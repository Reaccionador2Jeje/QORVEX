package dev.atdev.qorvex.storage

import android.content.Context
import android.os.StatFs
import android.system.Os
import dev.atdev.qorvex.vm.VirtualMachine
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

data class VirtualDiskInfo(
    val file: File,
    val capacityBytes: Long,
    val allocatedBytes: Long
)

/**
 * Creates and inspects sparse RAW disk containers in app-private storage.
 *
 * This manages host-side files only. It does not attach them to QEMU.
 */
class VirtualDiskManager(context: Context) {
    private val diskDirectory = File(context.filesDir, DISK_DIRECTORY).apply {
        check(isDirectory || mkdirs()) { "Could not create the virtual disk directory." }
    }

    /**
     * The VM ID is hashed before it becomes a filename. Profile data must never
     * be able to escape the app's virtual-disks directory through path segments.
     */
    fun getDiskFile(machine: VirtualMachine): File =
        File(diskDirectory, "${safeFileId(machine.id)}.raw")

    /**
     * Creates a sparse file without replacing an existing disk.
     * Existing disks are accepted only when their logical capacity matches.
     */
    fun createSparseDisk(machine: VirtualMachine): VirtualDiskInfo {
        val target = getDiskFile(machine)
        val capacity = machine.diskSizeGiB.toLong() * GIB

        if (target.exists()) {
            require(target.isFile) { "The virtual disk path is not a regular file." }
            require(target.length() == capacity) {
                "Existing disk size does not match this VM profile; the disk was not changed."
            }
            return inspectFile(target)
        }

        val available = StatFs(diskDirectory.absolutePath).availableBytes
        require(available >= MINIMUM_FREE_BYTES) {
            "At least 64 MiB of free storage is required to create a virtual disk."
        }

        val temporary = File(diskDirectory, "${target.name}.creating")
        require(!temporary.exists()) {
            "A previous interrupted disk creation exists. Remove ${temporary.name} before retrying."
        }

        try {
            RandomAccessFile(temporary, "rw").use { disk ->
                disk.setLength(capacity)
                disk.fd.sync()
            }
            check(temporary.length() == capacity) {
                "The filesystem did not create the requested virtual disk capacity."
            }
            check(temporary.renameTo(target)) {
                "Could not finalize the virtual disk file."
            }
            return inspectFile(target)
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    /** Returns null when this VM has no disk file. */
    fun inspect(machine: VirtualMachine): VirtualDiskInfo? {
        val file = getDiskFile(machine)
        if (!file.exists()) return null
        require(file.isFile) { "The virtual disk path is not a regular file." }
        return inspectFile(file)
    }

    /** Deletes only this VM's disk image. Profile deletion is intentionally separate. */
    fun delete(machine: VirtualMachine): Boolean {
        val file = getDiskFile(machine)
        return !file.exists() || (file.isFile && file.delete())
    }

    private fun inspectFile(file: File): VirtualDiskInfo {
        val stat = Os.stat(file.absolutePath)
        // st_blocks is reported in 512-byte units on Android/Linux. This is the
        // physical allocation, unlike File.length(), which is logical capacity.
        val allocated = stat.st_blocks * 512L
        return VirtualDiskInfo(
            file = file,
            capacityBytes = file.length(),
            allocatedBytes = allocated
        )
    }

    private fun safeFileId(id: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val DISK_DIRECTORY = "virtual-disks"
        const val GIB = 1024L * 1024L * 1024L
        const val MINIMUM_FREE_BYTES = 64L * 1024L * 1024L
    }
}
