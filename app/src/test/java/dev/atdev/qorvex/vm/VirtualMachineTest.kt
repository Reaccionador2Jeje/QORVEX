package dev.atdev.qorvex.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VirtualMachineTest {
    @Test
    fun profileSurvivesJsonRoundTrip() {
        val original = VirtualMachine(
            name = "Debian ARM",
            architecture = GuestArchitecture.AARCH64,
            memoryMiB = 2048,
            cpuCores = 4,
            diskSizeGiB = 24
        )
        val restored = VirtualMachine.fromJson(original.toJson())
        assertEquals(original, restored)
    }

    @Test
    fun installationMediaUriSurvivesJsonRoundTrip() {
        val original = VirtualMachine(
            name = "Installer",
            architecture = GuestArchitecture.AARCH64,
            memoryMiB = 1024,
            cpuCores = 2,
            diskSizeGiB = 8,
            installationMediaUri = "content://provider/document/123"
        )
        assertEquals(original, VirtualMachine.fromJson(original.toJson()))
    }

    @Test
    fun blankNameIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualMachine("id", " ", GuestArchitecture.AARCH64, 1024, 2, 16)
        }
    }

    @Test
    fun invalidMemoryIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualMachine("id", "Test", GuestArchitecture.AARCH64, 128, 2, 16)
        }
    }
}
