# QORVEX

**QORVEX** is AtDev's Android PC emulation platform, powered by upstream QEMU.

## Runtime target

The first packaged runtime targets **ARM64 Android devices running ARM64 guests**. QEMU uses TCG software emulation, so it does not require root or KVM. Hardware acceleration is not enabled.

The Android CI pipeline builds QEMU 11.1.2 for Android/Bionic, packages the ARM64 executable and SLIRP networking library, adds AAVMF UEFI firmware, and then builds the APK. The executable is packaged in the app's native library directory and launched as a child process; it is not loaded as a JNI shared library.

## Implemented

- Create, save, and delete VM profiles.
- Configure guest memory, CPU cores, and virtual disk capacity.
- Create sparse RAW disk images in app-private storage.
- Inspect logical disk capacity and actual allocated storage blocks.
- Import ISO images into app-private storage and record SHA-256 metadata.
- Launch the QEMU ARM64 system emulator with a `virt` machine, TCG, UEFI pflash, virtio disk, optional installation CD-ROM, and SLIRP user networking.
- Start and stop one QEMU process from the dashboard.
- Capture QEMU's serial output in the in-app console.

## Current limitations

- ARM64 guests only. x86_64 profiles can be saved but cannot boot in this runtime.
- The first frontend is serial-console only. A graphical framebuffer, touch keyboard, and pointer integration are not implemented yet.
- QEMU runs in the app process lifecycle; a foreground service for long-running/background VMs is still needed.
- Guest boot success depends on the selected ISO being compatible with the ARM64 `virt` machine and UEFI firmware.
- CI must successfully build and package QEMU before the APK contains the engine. A local `gradle assembleDebug` without running the QEMU build script creates an APK without QEMU.

## Build the QEMU runtime locally

Requirements: Docker, Git, Python 3, and readelf.

    bash tools/build-qemu-android.sh
    gradle testDebugUnitTest
    gradle assembleDebug

The build script checks that the resulting executable is AArch64 ELF and that its load segments are aligned for 16 KiB Android pages.

## Architecture

- **Android app:** Kotlin, Jetpack Compose, Android storage and process APIs.
- **VM storage:** app-private sparse RAW disks and imported installation media.
- **QEMU:** upstream QEMU 11.1.2, built for Android ARM64 with TCG.
- **Firmware:** AAVMF_CODE and per-VM AAVMF_VARS from Debian's `qemu-efi-aarch64` package.
- **Networking:** QEMU SLIRP user-mode networking.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/ROADMAP.md](docs/ROADMAP.md), and [docs/THIRD-PARTY-LICENSES.md](docs/THIRD-PARTY-LICENSES.md).

## Licensing

QORVEX branding and original AtDev application code are maintained by AtDev. QEMU is GPL-2.0; its corresponding source and applicable build patches must accompany distributed QEMU binaries. EDK II firmware and the Android build recipe have their own notices. See the third-party license record before redistributing builds.

QORVEX must not be described as a complete graphical PC emulator until graphical display and input have been implemented and guest boot has been tested on a physical Android device.
