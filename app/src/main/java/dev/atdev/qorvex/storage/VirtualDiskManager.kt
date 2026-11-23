package dev.atdev.qorvex.storage

import android.content.Context
import android.os.StatFs
import dev.atdev.qorvex.vm.VirtualMachine
import java.io.File
import java.io.RandomAccessFile

data class VirtualDiskInfo(
    val file: File,
    val capacityBytes: Long,
    val allocatedBytes: Long
)

/**
 * Creates a sparse RAW disk container in app-private storage.
 * QEMU attachment is not enabled until the QEMU block layer is integrated.
 */
class VirtualDiskManager(context: Context) {
    private val diskDirectory = File(context.filesDir, "virtual-disks").apply { mkdirs() }

    fun getDiskFile(machine: VirtualMachine): File =
        File(diskDirectory, "${machine.id}.raw")

    fun createSparseDisk(machine: VirtualMachine): VirtualDiskInfo {
        val target = getDiskFile(machine)
        val capacity = machine.diskSizeGiB.toLong() * GIB
        val available = StatFs(diskDirectory.absolutePath).availableBytes
        // Keep a safety margin; sparse files can still consume physical storage as blocks are written.
        require(available >= 64L * MIB) { "At least 64 MiB of free storage is required." }
        RandomAccessFile(target, "rw").use { disk ->
            if (disk.length() == 0L) disk.setLength(capacity)
            else require(disk.length() == capacity) { "Existing disk size does not match this VM profile." }
        }
        return VirtualDiskInfo(target, capacity, target.length())
    }

    fun inspect(machine: VirtualMachine): VirtualDiskInfo? {
        val file = getDiskFile(machine)
        if (!file.isFile) return null
        return VirtualDiskInfo(file, file.length(), file.length())
    }

    fun delete(machine: VirtualMachine): Boolean {
        val file = getDiskFile(machine)
        return !file.exists() || file.delete()
    }

    private companion object {
        const val GIB = 1024L * 1024L * 1024L
        const val MIB = 1024L * 1024L
    }
}
