package dev.atdev.qorvex.runtime

import dev.atdev.qorvex.vm.GuestArchitecture
import dev.atdev.qorvex.vm.VirtualMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class QemuCommandBuilderTest {
    @Test
    fun buildsArm64CommandWithFirmwareDiskIsoAndSerialConsole() {
        val directory = Files.createTempDirectory("qorvex-qemu-test").toFile()
        val executable = File(directory, "qemu executable").apply { writeText("binary") }
        val disk = File(directory, "vm disk.raw").apply { writeText("disk") }
        val iso = File(directory, "installer image.iso").apply { writeText("iso") }
        val code = File(directory, "AAVMF_CODE.fd").apply { writeText("code") }
        val vars = File(directory, "AAVMF_VARS.fd").apply { writeText("vars") }
        val machine = VirtualMachine(
            name = "Test VM",
            architecture = GuestArchitecture.AARCH64,
            memoryMiB = 2048,
            cpuCores = 4,
            diskSizeGiB = 16
        )

        val args = QemuCommandBuilder.build(executable, machine, disk, iso, code, vars)

        assertEquals(executable.absolutePath, args.first())
        assertTrue(args.containsAll(listOf("-machine", "virt,gic-version=3")))
        assertTrue(args.containsAll(listOf("-accel", "tcg,thread=multi")))
        assertTrue(args.containsAll(listOf("-smp", "4")))
        assertTrue(args.containsAll(listOf("-m", "2048")))
        assertTrue(args.any { it.contains(disk.absolutePath) })
        assertTrue(args.any { it.contains(iso.absolutePath) })
        assertTrue(args.any { it.contains(code.absolutePath) })
        assertTrue(args.any { it.contains(vars.absolutePath) })
        assertTrue(args.containsAll(listOf("-serial", "stdio", "-monitor", "none")))
        assertFalse(args.any { it == "mon:stdio" })
    }

    @Test
    fun omitsCdromAndBootOrderWhenNoIsoWasSelected() {
        val directory = Files.createTempDirectory("qorvex-qemu-no-iso").toFile()
        val executable = File(directory, "qemu").apply { writeText("binary") }
        val disk = File(directory, "disk.raw").apply { writeText("disk") }
        val code = File(directory, "code.fd").apply { writeText("code") }
        val vars = File(directory, "vars.fd").apply { writeText("vars") }
        val machine = VirtualMachine(
            name = "Disk only",
            architecture = GuestArchitecture.AARCH64,
            memoryMiB = 1024,
            cpuCores = 1,
            diskSizeGiB = 4
        )

        val args = QemuCommandBuilder.build(executable, machine, disk, null, code, vars)

        assertFalse(args.contains("-boot"))
        assertFalse(args.any { it.contains("cdrom") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedX8664Guest() {
        val directory = Files.createTempDirectory("qorvex-qemu-x86").toFile()
        val machine = VirtualMachine(
            name = "x86 guest",
            architecture = GuestArchitecture.X86_64,
            memoryMiB = 1024,
            cpuCores = 1,
            diskSizeGiB = 4
        )
        QemuCommandBuilder.build(
            File(directory, "qemu"),
            machine,
            File(directory, "disk.raw"),
            null,
            File(directory, "code.fd"),
            File(directory, "vars.fd")
        )
    }
}
