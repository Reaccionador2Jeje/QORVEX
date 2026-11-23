# QORVEX

**QORVEX** is an open-source Android ARM64 virtual-machine manager powered by QEMU.

It lets you create VM profiles, prepare virtual disks, import installation media, and launch ARM64 guest systems on compatible ARM64 Android devices. QEMU uses TCG software emulation; root access and KVM are not required.

## Current capabilities

- Create, save, and delete VM profiles.
- Configure guest memory, CPU cores, and virtual disk capacity.
- Create sparse RAW disk images in app-private storage.
- Inspect disk capacity and allocated storage.
- Import ISO images into app-private storage and record SHA-256 metadata.
- Launch QEMU ARM64 guests using the generic `virt` machine and UEFI firmware.
- Attach a virtio virtual disk and optional read-only installation ISO.
- Provide SLIRP user-mode networking.
- Capture serial output in the in-app console.
- Keep an active QEMU process supervised by an Android foreground service with a persistent notification and a Stop VM action.
- Request graceful shutdown through QMP, with process termination as a fallback.
- Detect interrupted VM sessions and perform non-destructive virtual-disk container checks before a manual restart.

## Current limitations

- ARM64 guests only. x86_64 profiles cannot boot with the current runtime.
- The frontend is serial-console only. A graphical framebuffer, touch keyboard, and pointer integration are not implemented.
- Audio, pause/resume, snapshots, and interactive QMP management are not implemented.
- Disk checks validate the container file and its storage properties; they do not inspect or repair the guest filesystem.
- TCG is software emulation and can be slow, especially on entry-level devices.
- Guest boot depends on the selected ISO supporting the ARM64 QEMU `virt` machine and UEFI firmware.
- Physical-device compatibility testing is still required before calling this a stable release.

## Build

The Android app uses Kotlin, Jetpack Compose, and Gradle.

The QEMU runtime is built separately for Android/Bionic ARM64 by `tools/build-qemu-android.sh`. It pins the Podroid QEMU build recipe, builds QEMU 11.1.2 and SLIRP, and checks the resulting ELF architecture and 16 KiB page alignment. UEFI firmware is built separately from the pinned EDK II source revision by `tools/build-aavmf-from-source.sh`; no prebuilt Debian firmware package is copied into the APK.

Requirements for the current build scripts: a Linux x86_64 host, Git, curl, Python 3 with `venv` and `pip`, GCC/G++, Make, Ninja, Meson, Autoconf/Automake/Libtool, NASM, ACPICA (`iasl`), UUID development headers, `patchelf`, and `readelf`. The QEMU runtime build no longer requires Docker.

```bash
bash tools/build-qemu-android.sh
gradle testDebugUnitTest
gradle assembleDebug
```

A plain Gradle build without first producing and packaging the QEMU runtime does not create a complete emulator APK. The GitHub Actions workflow builds the runtime, runs the unit tests, and then packages the APK.

## Architecture and licensing

- Android application: Apache-2.0.
- QEMU 11.1.2: GPL-2.0.
- Firmware: EDK II/AAVMF, with the Debian package copyright notice.
- Native build recipe: ExTV/Podroid at the pinned commit documented in `docs/THIRD-PARTY-LICENSES.md`.

QORVEX does not claim ownership of QEMU, SLIRP, or EDK II. Corresponding source, build patches, and required notices must accompany redistribution of the relevant binaries. See [the third-party license record](docs/THIRD-PARTY-LICENSES.md).

See [the architecture](docs/ARCHITECTURE.md) and [the roadmap](docs/ROADMAP.md) for implementation details.
