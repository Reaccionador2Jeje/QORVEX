package dev.atdev.qorvex.storage

/** File-system facts needed for a non-destructive virtual disk structure check. */
internal data class DiskIntegrityFacts(
    val expectedCapacityBytes: Long,
    val actualCapacityBytes: Long,
    val isRegularFile: Boolean,
    val isReadable: Boolean,
    val isWritable: Boolean,
    val parentDirectoryMatches: Boolean
)

/** Pure validation rules so the disk safety checks can be covered by JVM tests. */
internal object VirtualDiskIntegrityValidator {
    fun validate(facts: DiskIntegrityFacts): List<String> = buildList {
        if (!facts.isRegularFile) add("Virtual disk is not a regular file.")
        if (!facts.parentDirectoryMatches) add("Virtual disk resolves outside its private storage directory.")
        if (facts.actualCapacityBytes != facts.expectedCapacityBytes) {
            add("Virtual disk capacity mismatch: expected ${facts.expectedCapacityBytes} bytes, found ${facts.actualCapacityBytes} bytes.")
        }
        if (!facts.isReadable) add("Virtual disk is not readable.")
        if (!facts.isWritable) add("Virtual disk is not writable.")
    }
}