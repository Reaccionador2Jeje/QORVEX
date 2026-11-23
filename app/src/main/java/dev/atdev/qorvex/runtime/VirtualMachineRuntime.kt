package dev.atdev.qorvex.runtime

import dev.atdev.qorvex.vm.VirtualMachine

enum class RuntimeAvailability {
    NOT_INSTALLED,
    READY,
    UNSUPPORTED
}

data class RuntimeCapabilities(
    val availability: RuntimeAvailability,
    val supportedArchitectures: Set<String> = emptySet(),
    val hardwareAccelerationAvailable: Boolean = false,
    val detail: String
)

interface VirtualMachineRuntime {
    fun getCapabilities(): RuntimeCapabilities
    suspend fun start(machine: VirtualMachine): RuntimeResult
    suspend fun stop(machineId: String): RuntimeResult
}

sealed interface RuntimeResult {
    data object Success : RuntimeResult
    data class Failure(val reason: String) : RuntimeResult
}

/** Placeholder until QEMU is built, packaged and connected through JNI. */
class QemuRuntimeNotInstalled : VirtualMachineRuntime {
    override fun getCapabilities() = RuntimeCapabilities(
        availability = RuntimeAvailability.NOT_INSTALLED,
        detail = NativeQemuBridge.status + " QEMU itself is not linked; guest boot remains disabled."
    )

    override suspend fun start(machine: VirtualMachine) = RuntimeResult.Failure(
        "QEMU runtime is not installed. This profile is saved, but cannot boot yet."
    )

    override suspend fun stop(machineId: String) = RuntimeResult.Failure(
        "No QEMU runtime process is available in this build."
    )
}
