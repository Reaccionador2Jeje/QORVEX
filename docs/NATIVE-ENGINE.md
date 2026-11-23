# Native engine integration boundary

## What this stage provides

- Android NDK/CMake configuration for the `arm64-v8a` host ABI.
- A small JNI shared library proving that the Kotlin-to-native boundary can load.
- A native status probe that explicitly reports that QEMU is not bundled.
- Storage Access Framework selection and persisted access to installation media.
- App-private sparse RAW disk containers managed independently from the emulator.

## What it does not provide

The native library in this stage is a bridge scaffold, **not QEMU**. It does not emulate a CPU, execute guest code, parse QCOW2, provide firmware, or boot an operating system. The UI must keep VM start disabled until a licensed and audited QEMU build is linked and verified.

## QEMU integration checklist

1. Pin an upstream QEMU release/tag and record its exact source commit.
2. Audit the selected release's Android/NDK build requirements and every bundled dependency.
3. Build the required AArch64 system emulator components for Android arm64-v8a.
4. Preserve QEMU's GPL obligations, source availability, notices and dependency licenses.
5. Add the QEMU entry point behind the JNI boundary; do not expose raw shell command construction to UI input.
6. Add lifecycle-safe start/stop, cancellation, log forwarding and resource cleanup.
7. Test an ARM64 guest on physical Android hardware before advertising boot support.

## Disk format

The initial disk manager creates app-private sparse RAW containers. It does not convert disks or implement QCOW2. Disk files are persistent app data and are removed when the user deletes them through an explicit disk-management action (not merely by deleting a profile).
