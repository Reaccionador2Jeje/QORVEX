package dev.atdev.qorvex.storage

import android.content.Context
import android.os.StatFs
import android.system.Os
import dev.atdev.qorvex.vm.VirtualMachine
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

data class VirtualDiskSnapshot(
    val id: String,
    val displayName: String,
    val createdAtEpochMs: Long,
    val capacityBytes: Long,
    val file: File
)

/**
 * Offline, sparse RAW disk snapshots.
 *
 * The caller MUST ensure the VM is stopped before createSnapshot or restoreSnapshot.
 * Snapshots are independent disk copies; they are not live QEMU/VM-state snapshots.
 */
class VirtualDiskSnapshotManager(context: Context) {
    private val appContext = context.applicationContext
    private val diskManager = VirtualDiskManager(appContext)
    private val root = File(appContext.filesDir, SNAPSHOT_DIRECTORY).apply {
        check(isDirectory || mkdirs()) { "Could not create the snapshot directory." }
    }

    fun listSnapshots(machine: VirtualMachine): List<VirtualDiskSnapshot> {
        val directory = machineDirectory(machine)
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .mapNotNull { metadata ->
                runCatching {
                    val json = JSONObject(metadata.readText())
                    val id = json.getString("id")
                    require(id.matches(SNAPSHOT_ID.toRegex())) 
                    val image = File(directory, "$id.raw")
                    require(image.isFile && image.length() == json.getLong("capacityBytes"))
                    VirtualDiskSnapshot(
                        id = id,
                        displayName = json.optString("displayName", "Snapshot"),
                        createdAtEpochMs = json.getLong("createdAtEpochMs"),
                        capacityBytes = json.getLong("capacityBytes"),
                        file = image
                    )
                }.getOrNull()
            }
            .sortedByDescending { it.createdAtEpochMs }
    }

    /** Create a sparse offline copy without modifying the source disk. */
    fun createSnapshot(machine: VirtualMachine, displayName: String = "Snapshot"): VirtualDiskSnapshot {
        require(displayName.isNotBlank()) { "Snapshot name cannot be empty." }
        val source = diskManager.getDiskFile(machine)
        require(source.isFile && source.length() == machine.diskSizeGiB.toLong() * GIB) {
            "The VM disk is missing or its capacity does not match the profile."
        }
        val directory = machineDirectory(machine).apply {
            check(isDirectory || mkdirs()) { "Could not create this VM's snapshot directory." }
        }
        val allocated = Os.stat(source.absolutePath).st_blocks * 512L
        val available = StatFs(directory.absolutePath).availableBytes
        require(available >= allocated + MINIMUM_FREE_BYTES) {
            "Not enough free storage for a safe snapshot. Free up space and retry."
        }

        val id = "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        val image = File(directory, "$id.raw")
        val temporary = File(directory, "$id.creating")
        try {
            copySparse(source, temporary)
            check(temporary.length() == source.length()) { "Snapshot capacity verification failed." }
            check(temporary.renameTo(image)) { "Could not finalize the snapshot image." }
            val created = System.currentTimeMillis()
            val metadata = JSONObject()
                .put("id", id)
                .put("displayName", displayName.trim().take(64))
                .put("createdAtEpochMs", created)
                .put("capacityBytes", source.length())
            val metadataTemp = File(directory, "$id.json.creating")
            metadataTemp.writeText(metadata.toString())
            RandomAccessFile(metadataTemp, "rw").use { it.fd.sync() }
            check(metadataTemp.renameTo(File(directory, "$id.json"))) {
                "Could not save snapshot metadata."
            }
            return VirtualDiskSnapshot(id, displayName.trim().take(64), created, source.length(), image)
        } catch (error: Throwable) {
            temporary.delete()
            image.delete()
            File(directory, "$id.json.creating").delete()
            throw error
        }
    }

    /**
     * Restore through a staged sparse copy and same-directory rename.
     * The previous disk is retained as a temporary rollback file until commit succeeds.
     */
    fun restoreSnapshot(machine: VirtualMachine, snapshotId: String) {
        require(snapshotId.matches(SNAPSHOT_ID.toRegex())) { "Invalid snapshot identifier." }
        val snapshot = listSnapshots(machine).firstOrNull { it.id == snapshotId }
            ?: error("Snapshot not found or its metadata is invalid.")
        val target = diskManager.getDiskFile(machine)
        require(snapshot.capacityBytes == machine.diskSizeGiB.toLong() * GIB) {
            "Snapshot capacity does not match the VM profile."
        }
        val directory = target.parentFile ?: error("Virtual disk directory is unavailable.")
        val staged = File(directory, target.name + ".restore")
        val backup = File(directory, target.name + ".rollback")
        require(!staged.exists() && !backup.exists()) {
            "A previous interrupted restore needs review before another restore can run."
        }
        try {
            copySparse(snapshot.file, staged)
            val expectedCapacity = machine.diskSizeGiB.toLong() * GIB
            check(staged.length() == expectedCapacity) { "Restored disk capacity verification failed." }
            if (target.exists()) require(target.length() == expectedCapacity) {
                "Current disk capacity does not match the VM profile; restore was not applied."
            }
            if (target.exists()) {
                check(target.renameTo(backup)) { "Could not preserve the current disk for rollback." }
            }
            if (!staged.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                error("Could not activate the restored disk; the previous disk was preserved.")
            }
            backup.delete()
        } catch (error: Throwable) {
            staged.delete()
            if (!target.exists() && backup.exists()) backup.renameTo(target)
            throw error
        }
    }

    fun deleteSnapshot(machine: VirtualMachine, snapshotId: String): Boolean {
        require(snapshotId.matches(SNAPSHOT_ID.toRegex())) { "Invalid snapshot identifier." }
        val directory = machineDirectory(machine)
        val image = File(directory, "$snapshotId.raw")
        val metadata = File(directory, "$snapshotId.json")
        if (!image.exists() && !metadata.exists()) return true
        val imageDeleted = !image.exists() || (image.isFile && image.delete())
        val metadataDeleted = !metadata.exists() || (metadata.isFile && metadata.delete())
        return imageDeleted && metadataDeleted
    }

    private fun machineDirectory(machine: VirtualMachine): File {
        // Hashing avoids path traversal and keeps the directory name independent of profile text.
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(machine.id.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(root, digest)
    }

    /** Copy only non-zero 1 MiB blocks, preserving sparse allocation on Android filesystems. */
    private fun copySparse(source: File, destination: File) {
        require(source.isFile && source.canRead()) { "Snapshot source is not readable." }
        RandomAccessFile(source, "r").use { input ->
            RandomAccessFile(destination, "rw").use { output ->
                output.setLength(0L)
                output.setLength(input.length())
                val buffer = ByteArray(COPY_CHUNK_BYTES)
                var offset = 0L
                while (offset < input.length()) {
                    val count = minOf(buffer.size.toLong(), input.length() - offset).toInt()
                    input.seek(offset)
                    input.readFully(buffer, 0, count)
                    var hasData = false
                    for (index in 0 until count) {
                        if (buffer[index].toInt() != 0) { hasData = true; break }
                    }
                    if (hasData) {
                        output.seek(offset)
                        output.write(buffer, 0, count)
                    }
                    offset += count
                }
                output.fd.sync()
            }
        }
    }

    private companion object {
        const val SNAPSHOT_DIRECTORY = "vm-snapshots"
        const val GIB = 1024L * 1024L * 1024L
        const val MINIMUM_FREE_BYTES = 64L * 1024L * 1024L
        const val COPY_CHUNK_BYTES = 1024 * 1024
        const val SNAPSHOT_ID = "[0-9]{13}-[a-f0-9]{8}"
    }
}
