package dev.atdev.qorvex.runtime

import dev.atdev.qorvex.vm.GuestArchitecture
import dev.atdev.qorvex.vm.VirtualMachine
import java.io.File

/** Builds a safe argument vector for the Android ARM64-hosted QEMU system emulators. */
object QemuCommandBuilder {
    fun build(
        executable: File,
        machine: VirtualMachine,
        disk: File,
        installationMedia: File?,
        firmwareCode: File,
        firmwareVars: File,
        qmpSocket: File? = null,
        qemuDataDirectory: File? = null
    ): List<String> {
        require(disk.isFile) { "The virtual disk does not exist." }
        if (machine.architecture == GuestArchitecture.AARCH64) {
            require(firmwareCode.isFile) { "ARM64 UEFI firmware code is missing." }
            require(firmwareVars.isFile) { "The VM's UEFI variables file is missing." }
        }
        return buildList {
            add(executable.absolutePath)
            addAll(listOf("-name", machine.name))
            addAll(listOf("-accel", "tcg,thread=multi"))
            addAll(listOf("-smp", machine.cpuCores.toString()))
            addAll(listOf("-m", machine.memoryMiB.toString()))

            when (machine.architecture) {
                GuestArchitecture.AARCH64 -> {
                    addAll(listOf("-machine", "virt,gic-version=3"))
                    addAll(listOf("-cpu", "max"))
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
                    addAll(listOf("-device", "virtio-gpu-device"))
                    addAll(listOf("-device", "virtio-keyboard-device"))
                    addAll(listOf("-device", "virtio-tablet-device"))
                }
                GuestArchitecture.X86_64 -> {
                    addAll(listOf("-machine", "pc"))
                    addAll(listOf("-cpu", "qemu64"))
                    if (qemuDataDirectory != null) addAll(listOf("-L", qemuDataDirectory.absolutePath))
                    addAll(listOf("-drive", "if=ide,index=0,media=disk,format=raw,file=${disk.absolutePath}"))
                    if (installationMedia != null) {
                        require(installationMedia.isFile) { "The imported installation image is missing." }
                        addAll(listOf("-drive", "if=ide,index=1,media=cdrom,readonly=on,format=raw,file=${installationMedia.absolutePath}"))
                        addAll(listOf("-boot", "order=cd"))
                    }
                    addAll(listOf("-netdev", "user,id=net0"))
                    addAll(listOf("-device", "e1000,netdev=net0"))
                    addAll(listOf("-vga", "std"))
                    addAll(listOf("-device", "usb-kbd"))
                    addAll(listOf("-device", "usb-tablet"))
                }
            }

            addAll(listOf("-display", "none"))
            addAll(listOf("-vnc", "127.0.0.1:0"))
            addAll(listOf("-serial", "stdio"))
            addAll(listOf("-monitor", "none"))
            if (qmpSocket != null) addAll(listOf("-qmp", "unix:${qmpSocket.absolutePath},server=on,wait=off"))
            add("-no-reboot")
        }
    }
}
