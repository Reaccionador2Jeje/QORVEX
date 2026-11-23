package dev.atdev.qorvex.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualDiskIntegrityValidatorTest {
    @Test
    fun acceptsMatchingPrivateWritableDisk() {
        val issues = VirtualDiskIntegrityValidator.validate(
            DiskIntegrityFacts(16L, 16L, true, true, true, true)
        )
        assertTrue(issues.isEmpty())
    }

    @Test
    fun reportsCapacityMismatchWithoutRepairingDisk() {
        val issues = VirtualDiskIntegrityValidator.validate(
            DiskIntegrityFacts(16L, 8L, true, true, true, true)
        )
        assertEquals(1, issues.size)
        assertTrue(issues.single().contains("capacity mismatch"))
    }

    @Test
    fun rejectsPathOutsidePrivateDiskDirectory() {
        val issues = VirtualDiskIntegrityValidator.validate(
            DiskIntegrityFacts(16L, 16L, true, true, true, false)
        )
        assertTrue(issues.any { it.contains("outside its private storage directory") })
    }

    @Test
    fun rejectsUnreadableOrReadOnlyDisk() {
        val issues = VirtualDiskIntegrityValidator.validate(
            DiskIntegrityFacts(16L, 16L, true, false, false, true)
        )
        assertTrue(issues.any { it.contains("not readable") })
        assertTrue(issues.any { it.contains("not writable") })
    }

    @Test
    fun rejectsNonRegularDiskFile() {
        val issues = VirtualDiskIntegrityValidator.validate(
            DiskIntegrityFacts(16L, 16L, false, true, true, true)
        )
        assertTrue(issues.any { it.contains("not a regular file") })
    }
}