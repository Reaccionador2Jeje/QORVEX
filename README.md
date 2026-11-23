# QORVEX

**QORVEX** is AtDev's Android PC virtualization and emulation platform, powered by QEMU.

Run virtual computers from Android with a purpose-built interface for machine configuration, virtual storage, installation media, input controls, and VM lifecycle management.

> **Project status:** Early development. The Android dashboard, VM profile configuration, local profile storage, and runtime boundary are in place. QEMU runtime integration and on-device validation are not yet complete.

## Project goals

- Android-first experience with touch-friendly controls.
- QEMU-backed virtual machines, starting with ARM64 guests on ARM64 Android devices.
- Configurable virtual CPU, memory, storage, and boot media.
- Persistent VM profiles and disk images.
- Clear separation between the Android UI and the virtualization runtime.
- Transparent reporting of device compatibility and runtime limitations.

## Architecture

- **Android app:** Kotlin, Jetpack Compose, Android lifecycle and storage APIs.
- **Runtime boundary:** Kotlin interface isolating UI from QEMU implementation.
- **QEMU integration:** Native QEMU build and JNI bridge, introduced in a later milestone.
- **Guest firmware:** Firmware assets selected per supported machine architecture.
- **VM storage:** App-managed virtual disk files with explicit import/export flows.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/ROADMAP.md](docs/ROADMAP.md).

## Build

The Android project uses Gradle and the Android Gradle Plugin.

    gradle assembleDebug

Open the repository in Android Studio to use the IDE-managed Gradle tooling.

## Licensing

QORVEX branding and original AtDev application code are maintained by AtDev. QEMU and any bundled third-party components remain subject to their respective licenses. In particular, QEMU is licensed under the GNU GPL; distribution must preserve applicable source-code, notice, and corresponding-license obligations. No proprietary relicensing of QEMU is implied.

A complete third-party notices and compliance process is required before distributing builds containing QEMU.

## Status

QORVEX is under active development. A successful Android UI build does not imply that a guest operating system can already boot on a physical device.
