# QORVEX

**QORVEX** is AtDev's Android PC emulation platform, designed around QEMU.

> **Project status:** Early development. VM profiles, local sparse RAW disks, and ISO import into app-private storage are implemented. The ISO importer streams files, checks the reported size when available, and records a SHA-256 digest. The QEMU engine is not yet bundled, so guest boot is not available.

## Current capabilities

- Create, save, and delete VM profiles.
- Configure ARM64 or x86_64 guest profiles, memory, CPU cores, and disk capacity.
- Create sparse RAW disk containers without overwriting existing disks.
- Inspect logical disk capacity and actual allocated storage blocks.
- Select an installation image and copy it into QORVEX's private storage.
- Record installation-image name and SHA-256 metadata.

## Architecture

- **Android app:** Kotlin, Jetpack Compose, Android lifecycle and storage APIs.
- **VM profiles:** JSON-backed local profile persistence.
- **Storage:** App-private sparse RAW disk images and imported installation media.
- **Native boundary:** NDK/CMake JNI status library; this is not QEMU.
- **QEMU integration:** Upstream QEMU still needs to be pinned, built for Android, linked, and tested.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/ROADMAP.md](docs/ROADMAP.md).

## Build

The Android project uses Gradle and the Android Gradle Plugin:

    gradle assembleDebug

Open the repository in Android Studio to use IDE-managed Gradle tooling.

## Licensing

QORVEX branding and original AtDev application code are maintained by AtDev. QEMU and bundled third-party components remain subject to their respective licenses. QEMU is licensed under the GNU GPL; distribution must preserve applicable source-code, notice, and corresponding-license obligations. No proprietary relicensing of QEMU is implied.

A complete third-party notices and compliance process is required before distributing builds containing QEMU.

## Release gate

A successful Android build does not mean QORVEX can boot a guest. The project must not be described as a working PC emulator until a guest has been booted and tested on a supported physical Android device.
