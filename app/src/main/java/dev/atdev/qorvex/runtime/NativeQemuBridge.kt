package dev.atdev.qorvex.runtime

/**
 * Loads QORVEX's small native boundary. This is not the QEMU emulator:
 * the upstream engine must be built and linked before boot is enabled.
 */
object NativeQemuBridge {
    val isLoaded: Boolean
    val status: String

    init {
        val loaded = runCatching { System.loadLibrary("qorvex_native") }.isSuccess
        isLoaded = loaded
        status = if (loaded) {
            runCatching { nativeRuntimeStatus() }.getOrElse { "Native bridge loaded, status unavailable." }
        } else {
            "Native bridge library is unavailable for this device ABI."
        }
    }

    private external fun nativeRuntimeStatus(): String
}
