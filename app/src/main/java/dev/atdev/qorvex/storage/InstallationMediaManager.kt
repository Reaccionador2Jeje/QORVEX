package dev.atdev.qorvex.storage

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import dev.atdev.qorvex.vm.VirtualMachine
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class ImportedInstallationMedia(
    val file: File,
    val displayName: String,
    val sizeBytes: Long,
    val sha256: String
)

/**
 * Copies a document-provider URI into app-private storage.
 * The resulting absolute path can be passed to a future QEMU process without
 * depending on a temporary Storage Access Framework permission.
 *
 * Call importMedia() from a background thread.
 */
class InstallationMediaManager(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "installation-media").apply {
        check(isDirectory || mkdirs()) { "Could not create the installation media directory." }
    }

    fun getMediaFile(machine: VirtualMachine): File =
        File(directory, safeId(machine.id) + ".iso")

    fun inspect(machine: VirtualMachine): ImportedInstallationMedia? {
        val file = getMediaFile(machine)
        if (!file.isFile || file.length() <= 0L) return null
        return ImportedInstallationMedia(
            file = file,
            displayName = machine.installationMediaName ?: file.name,
            sizeBytes = file.length(),
            sha256 = machine.installationMediaSha256.orEmpty()
        )
    }

    fun importMedia(machine: VirtualMachine, uri: Uri): ImportedInstallationMedia {
        val resolver = appContext.contentResolver
        var displayName = "installation-media.iso"
        var expectedSize: Long? = null

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) {
                        displayName = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: displayName
                    }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        expectedSize = cursor.getLong(sizeIndex).takeIf { it >= 0L }
                    }
                }
            }

        val available = StatFs(directory.absolutePath).availableBytes
        val byteLimit = available - RESERVED_FREE_BYTES
        require(byteLimit > 0L) { "Not enough free storage to import installation media." }
        if (expectedSize != null) {
            require(expectedSize!! in 1..byteLimit) {
                "The selected file is larger than the available storage allowance."
            }
        }

        val target = getMediaFile(machine)
        val temporary = File(directory, safeId(machine.id) + ".importing")
        val backup = File(directory, safeId(machine.id) + ".previous")
        require(!temporary.exists() && !backup.exists()) {
            "An interrupted media import exists. Remove its temporary file before retrying."
        }

        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        try {
            val input = resolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Android could not open the selected file.")
            input.use { source ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        copied += count
                        require(copied <= byteLimit) {
                            "Import stopped because the device is running low on free storage."
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }

            require(copied > 0L) { "The selected installation media is empty." }
            if (expectedSize != null) {
                require(copied == expectedSize) {
                    "The imported file size differs from the document provider's reported size."
                }
            }

            if (target.exists()) {
                check(target.isFile && target.renameTo(backup)) {
                    "Could not preserve the previous installation media."
                }
            }
            if (!temporary.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                error("Could not finalize the imported installation media.")
            }
            backup.delete()

            return ImportedInstallationMedia(
                file = target,
                displayName = displayName,
                sizeBytes = copied,
                sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            )
        } catch (error: Throwable) {
            temporary.delete()
            if (backup.exists() && !target.exists()) backup.renameTo(target)
            throw error
        }
    }

    fun delete(machine: VirtualMachine): Boolean {
        val file = getMediaFile(machine)
        return !file.exists() || (file.isFile && file.delete())
    }

    private fun safeId(id: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val COPY_BUFFER_SIZE = 1024 * 1024
        const val RESERVED_FREE_BYTES = 64L * 1024L * 1024L
    }
}
