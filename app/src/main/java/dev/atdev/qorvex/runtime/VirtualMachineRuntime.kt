package dev.atdev.qorvex.runtime

import android.content.Context
import android.os.Build
import dev.atdev.qorvex.storage.InstallationMediaManager
import dev.atdev.qorvex.storage.VirtualDiskManager
import dev.atdev.qorvex.vm.GuestArchitecture
import dev.atdev.qorvex.vm.VirtualMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

enum class RuntimeAvailability {
    NOT_INSTALLED,
    READY,
    UNSUPPORTED
}

enum class RuntimeState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    EXITED,
    FAILED
}

data class RuntimeCapabilities(
    val availability: RuntimeAvailability,
    val supportedArchitectures: Set<String> = emptySet(),
    val hardwareAccelerationAvailable: Boolean = false,
    val detail: String
)

interface VirtualMachineRuntime {
    val state: StateFlow<RuntimeState>
    val consoleText: StateFlow<String>
    val activeMachineId: StateFlow<String?>
    fun getCapabilities(): RuntimeCapabilities
    suspend fun start(machine: VirtualMachine): RuntimeResult
    suspend fun stop(machineId: String): RuntimeResult
}

sealed interface RuntimeResult {
    data object Success : RuntimeResult
    data class Failure(val reason: String) : RuntimeResult
}

/**
 * Runs the packaged QEMU ARM64 system-emulator executable as a child process.
 * The executable is built for Android/Bionic and packaged in jniLibs; it is
 * deliberately not loaded with System.loadLibrary().
 *
 * This first runtime exposes the serial console. A graphical display frontend,
 * pause/resume and foreground-service supervision are separate follow-up work.
 */
class QemuProcessRuntime(context: Context) : VirtualMachineRuntime {
    private val appContext = context.applicationContext
    private val qemuExecutable = File(appContext.applicationInfo.nativeLibraryDir, QEMU_LIBRARY)
    private val diskManager = VirtualDiskManager(appContext)
    private val mediaManager = InstallationMediaManager(appContext)
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startMutex = Mutex()

    private val mutableState = MutableStateFlow(RuntimeState.STOPPED)
    override val state: StateFlow<RuntimeState> = mutableState.asStateFlow()

    private val mutableConsole = MutableStateFlow("")
    override val consoleText: StateFlow<String> = mutableConsole.asStateFlow()

    private val mutableActiveMachineId = MutableStateFlow<String?>(null)
    override val activeMachineId: StateFlow<String?> = mutableActiveMachineId.asStateFlow()

    @Volatile
    private var process: Process? = null

    override fun getCapabilities(): RuntimeCapabilities {
        if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" }) {
            return RuntimeCapabilities(
                availability = RuntimeAvailability.UNSUPPORTED,
                detail = "This build requires a 64-bit ARM Android device (arm64-v8a)."
            )
        }
        if (!qemuExecutable.isFile || !qemuExecutable.canExecute()) {
            return RuntimeCapabilities(
                availability = RuntimeAvailability.NOT_INSTALLED,
                detail = "QEMU ARM64 binary is not packaged in this APK. Build the QEMU runtime artifact first."
            )
        }
        val firmwareReady = runCatching {
            appContext.assets.open(FIRMWARE_CODE_ASSET).close()
            appContext.assets.open(FIRMWARE_VARS_ASSET).close()
        }.isSuccess
        if (!firmwareReady) {
            return RuntimeCapabilities(
                availability = RuntimeAvailability.NOT_INSTALLED,
                detail = "QEMU is packaged, but ARM64 UEFI firmware assets are missing."
            )
        }
        return RuntimeCapabilities(
            availability = RuntimeAvailability.READY,
            supportedArchitectures = setOf(GuestArchitecture.AARCH64.qemuTarget),
            hardwareAccelerationAvailable = false,
            detail = "QEMU ARM64 is ready. TCG software emulation is enabled; this build exposes the serial console."
        )
    }

    override suspend fun start(machine: VirtualMachine): RuntimeResult = startMutex.withLock {
        val capabilities = getCapabilities()
        if (capabilities.availability != RuntimeAvailability.READY) {
            return@withLock RuntimeResult.Failure(capabilities.detail)
        }
        if (machine.architecture != GuestArchitecture.AARCH64) {
            return@withLock RuntimeResult.Failure(
                "This QEMU build supports ARM64 guests only. x86_64 guest support is not included yet."
            )
        }
        if (process?.isAlive == true) {
            return@withLock RuntimeResult.Failure("Another virtual machine is already running.")
        }

        mutableState.value = RuntimeState.STARTING
        mutableConsole.value = ""
        try {
            withContext(Dispatchers.IO) {
                val disk = diskManager.createSparseDisk(machine).file
                val media = mediaManager.inspect(machine)?.file
                if (machine.installationMediaName != null && media == null) {
                    error("The imported installation image is missing. Select it again before booting.")
                }

                val firmware = prepareFirmware(machine)
                val command = QemuCommandBuilder.build(
                    executable = qemuExecutable,
                    machine = machine,
                    disk = disk,
                    installationMedia = media,
                    firmwareCode = firmware.first,
                    firmwareVars = firmware.second
                )
                val child = ProcessBuilder(command)
                    .directory(appContext.filesDir)
                    .redirectErrorStream(true)
                    .apply {
                        environment()["LD_LIBRARY_PATH"] =
                            "${appContext.applicationInfo.nativeLibraryDir}:${appContext.filesDir.absolutePath}"
                    }
                    .start()

                process = child
                mutableActiveMachineId.value = machine.id
                mutableState.value = RuntimeState.RUNNING

                runtimeScope.launch {
                    runCatching {
                        child.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { appendConsoleLine(it) }
                        }
                    }.onFailure { appendConsoleLine("Console stream ended: ${it.message}") }

                    val exitCode = runCatching { child.waitFor() }.getOrDefault(-1)
                    if (process === child) {
                        process = null
                        mutableActiveMachineId.value = null
                        mutableState.value = if (exitCode == 0) {
                            RuntimeState.EXITED
                        } else {
                            RuntimeState.FAILED
                        }
                        appendConsoleLine("QEMU exited with code $exitCode.")
                    }
                }
            }
            RuntimeResult.Success
        } catch (error: Throwable) {
            process = null
            mutableActiveMachineId.value = null
            mutableState.value = RuntimeState.FAILED
            RuntimeResult.Failure(error.message ?: "Could not start the QEMU process.")
        }
    }

    override suspend fun stop(machineId: String): RuntimeResult {
        val activeId = mutableActiveMachineId.value
            ?: return RuntimeResult.Failure("No virtual machine is running.")
        if (activeId != machineId) {
            return RuntimeResult.Failure("The requested VM is not the active machine.")
        }
        val child = process ?: return RuntimeResult.Failure("The QEMU process is unavailable.")

        mutableState.value = RuntimeState.STOPPING
        return withContext(Dispatchers.IO) {
            runCatching {
                child.destroy()
                if (!child.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    child.destroyForcibly()
                    child.waitFor()
                }
                if (process === child) process = null
                mutableActiveMachineId.value = null
                mutableState.value = RuntimeState.STOPPED
                appendConsoleLine("QEMU stopped.")
                RuntimeResult.Success
            }.getOrElse {
                mutableState.value = RuntimeState.FAILED
                RuntimeResult.Failure(it.message ?: "Could not stop QEMU.")
            }
        }
    }

    private fun prepareFirmware(machine: VirtualMachine): Pair<File, File> {
        val directory = File(appContext.filesDir, "firmware").apply {
            check(isDirectory || mkdirs()) { "Could not create the firmware directory." }
        }
        val code = File(directory, "AAVMF_CODE.fd")
        if (!code.isFile) {
            appContext.assets.open(FIRMWARE_CODE_ASSET).use { input ->
                code.outputStream().use { output -> input.copyTo(output) }
            }
        }

        val vars = File(directory, "AAVMF_VARS-${machine.id}.fd")
        if (!vars.isFile) {
            appContext.assets.open(FIRMWARE_VARS_ASSET).use { input ->
                vars.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return code to vars
    }

    @Synchronized
    private fun appendConsoleLine(line: String) {
        val current = mutableConsole.value
        mutableConsole.value = (current + line + "\n").takeLast(MAX_CONSOLE_CHARS)
    }

    private companion object {
        const val QEMU_LIBRARY = "libqemu-system-aarch64.so"
        const val FIRMWARE_CODE_ASSET = "firmware/AAVMF_CODE.fd"
        const val FIRMWARE_VARS_ASSET = "firmware/AAVMF_VARS.fd"
        const val STOP_TIMEOUT_SECONDS = 5L
        const val MAX_CONSOLE_CHARS = 48 * 1024
    }
}
