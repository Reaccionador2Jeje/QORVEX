package dev.atdev.qorvex.runtime

import dev.atdev.qorvex.vm.VirtualMachine
import java.io.File

/**
 * Builds an argument vector for the Android ARM64 QEMU system emulator.
 * No shell is involved, so paths and VM names are passed as individual arguments.
 */
object QemuCommandBuilder {
    fun build(
        executable: File,
        machine: VirtualMachine,
        disk: File,
        installationMedia: File?,
        firmwareCode: File,
        firmwareVars: File,
        qmpSocket: File? = null
    ): List<String> {
        require(machine.architecture.qemuTarget == "aarch64") {
            "This QEMU build currently supports ARM64 guests only."
        }
        require(disk.isFile) { "The virtual disk does not exist." }
        require(firmwareCode.isFile) { "ARM64 UEFI firmware code is missing." }
        require(firmwareVars.isFile) { "The VM's UEFI variables file is missing." }

        return buildList {
            add(executable.absolutePath)
            addAll(listOf("-name", machine.name))
            addAll(listOf("-machine", "virt,gic-version=3"))
            addAll(listOf("-accel", "tcg,thread=multi"))
            addAll(listOf("-cpu", "max"))
            addAll(listOf("-smp", machine.cpuCores.toString()))
            addAll(listOf("-m", machine.memoryMiB.toString()))
            addAll(listOf("-drive", "if=pflash,format=raw,readonly=on,file=${firmwareCode.absolutePath}"))
            addAll(listOf("-drive", "if=pflash,format=raw,file=${firmwareVars.absolutePath}"))
            addAll(listOf("-drive", "if=none,id=vdisk,format=raw,file=${disk.absolutePath}"))
            addAll(listOf("-device", "virtio-blk-device,drive=vdisk"))
            if (installationMedia != null) {
                require(installationMedia.isFile) { "The imported installation image is missing." }
                addAll(listOf("-drive", "if=none,id=cdrom,media=cdrom,format=raw,readonly=on,file=${installationMedia.absolutePath}"))
                addAll(listOf("-device", "virtio-blk-device,drive=cdrom"))
                addAll(listOf("-boot", "order=d"))
            }
            addAll(listOf("-netdev", "user,id=net0"))
            addAll(listOf("-device", "virtio-net-device,netdev=net0"))
            addAll(listOf("-display", "none"))
            addAll(listOf("-serial", "stdio"))
            addAll(listOf("-monitor", "none"))
            if (qmpSocket != null) {
                addAll(listOf("-qmp", "unix:${qmpSocket.absolutePath},server=on,wait=off"))
            }
            add("-no-reboot")
        }
    }
}
