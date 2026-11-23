package dev.atdev.qorvex.vm

import org.json.JSONObject
import java.util.UUID

enum class GuestArchitecture(val label: String, val qemuTarget: String) {
    AARCH64("ARM64 (aarch64)", "aarch64"),
    X86_64("x86_64", "x86_64")
}

data class VirtualMachine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val architecture: GuestArchitecture,
    val memoryMiB: Int,
    val cpuCores: Int,
    val diskSizeGiB: Int,
    val installationMediaUri: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    init {
        require(name.isNotBlank()) { "VM name cannot be empty." }
        require(memoryMiB in 256..65536) { "Memory must be between 256 and 65536 MiB." }
        require(cpuCores in 1..32) { "CPU cores must be between 1 and 32." }
        require(diskSizeGiB in 1..2048) { "Disk size must be between 1 and 2048 GiB." }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("architecture", architecture.name)
        .put("memoryMiB", memoryMiB)
        .put("cpuCores", cpuCores)
        .put("diskSizeGiB", diskSizeGiB)
        .put("installationMediaUri", installationMediaUri)
        .put("createdAtEpochMs", createdAtEpochMs)

    companion object {
        fun fromJson(json: JSONObject): VirtualMachine = VirtualMachine(
            id = json.getString("id"),
            name = json.getString("name"),
            architecture = GuestArchitecture.valueOf(json.getString("architecture")),
            memoryMiB = json.getInt("memoryMiB"),
            cpuCores = json.getInt("cpuCores"),
            diskSizeGiB = json.getInt("diskSizeGiB"),
            installationMediaUri = json.optString("installationMediaUri").takeIf { it.isNotBlank() && it != "null" },
            createdAtEpochMs = json.getLong("createdAtEpochMs")
        )
    }
}
