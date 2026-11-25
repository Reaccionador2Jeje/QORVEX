# QORVEX Architecture

## 1. Runtime layout

    Jetpack Compose dashboard
             |
             v
    VM profiles and storage managers
             |
             v
    QorvexRuntimeService (Android foreground service)
             |
             v
    Shared QemuProcessRuntime (Kotlin)
             |
             | ProcessBuilder + argv (no shell)
             v
    libqemu-system-aarch64.so (Android/Bionic executable)
             |
             +---- libslirp.so (user-mode NAT, DHCP and DNS proxy)
             +---- AAVMF UEFI pflash code + per-VM vars
             +---- app-private RAW disk
             +---- imported installation ISO
             +---- serial stdio -> Compose console
             +---- QMP Unix socket -> graceful shutdown

The file named `libqemu-system-aarch64.so` is a native executable packaged in `jniLibs/arm64-v8a` so Android extracts it into `nativeLibraryDir`. It is executed as a child process; it is not passed to `System.loadLibrary()`.

## 2. Build provenance

- QEMU upstream version: **11.1.2**.
- Android host ABI: **arm64-v8a**, Android API 26 minimum.
- QEMU target: **aarch64-softmmu**.
- Accelerator: **TCG**, with multithreaded translation enabled.
- Build recipe: Podroid's Android ARM64 QEMU Docker build pinned to commit `e3fc1befa3c135d28bf8d40e3c2264d93fea87ef`. The exact upstream build recipe and Android compatibility patches are fetched at that immutable commit by `tools/build-qemu-android.sh`.
- Native dependency: QEMU SLIRP library `libslirp.so`.
- Firmware: Debian Bookworm `qemu-efi-aarch64` package, AAVMF_CODE and AAVMF_VARS.

The CI workflow builds the native artifacts first, validates the QEMU ELF architecture and 16 KiB LOAD-segment alignment, then packages them into the APK.

## 3. VM launch configuration

The first runtime launches only ARM64 guests using QEMU's generic `virt` machine:

- `-machine virt,gic-version=3`
- `-accel tcg,thread=multi`
- `-cpu max`
- configured `-smp` and `-m`
- read-only AAVMF_CODE pflash plus a per-VM writable AAVMF_VARS copy
- app-private sparse RAW disk attached through virtio
- optional ISO attached as a read-only virtio CD-ROM with CD-ROM boot priority
- SLIRP user-mode NAT through virtio-net (ARM64 `virtio-net-device`; x86_64 `virtio-net-pci`)
- Guest IPv4 network `10.0.2.0/24`, gateway `10.0.2.2`, DHCP pool beginning at `10.0.2.15`, and DNS proxy `10.0.2.3`
- serial console on stdio; graphical display disabled in this first backend
- optional QMP Unix-domain socket for monitor commands and graceful shutdown

All command arguments are passed as an argument vector to ProcessBuilder. No shell command string is assembled from profile data.

## 4. Storage

- RAW disk images live in app-private `files/virtual-disks`.
- Imported installation media lives in app-private `files/installation-media`.
- UEFI variable stores are copied per VM to `files/firmware`; guest NVRAM is not shared between VMs.
- Imported ISO files are streamed into a temporary file and hashed before the final rename.

## 5. Runtime limits

The foreground service owns the runtime lifetime while a guest is active, so moving the dashboard to the background or recreating the Activity does not discard the QEMU process or its observable runtime state. The service uses an ongoing notification with a Stop VM action. This does not promise recovery after Android kills the entire app process, a force-stop, or a device reboot. It exposes serial output only; there is no framebuffer surface, touch input, audio, pause/resume, or interactive QMP management UI yet. The stop action attempts a QMP `quit` request and falls back to process termination if the monitor is unavailable.

Only ARM64 guests are supported. x86_64 profiles are rejected at runtime until an x86_64-softmmu build is included.

## 6. Security and reliability

- Imported files are never executed on the Android host.
- VM files stay inside app-private storage.
- QEMU arguments are passed without a shell.
- The ISO is attached read-only.
- The UEFI variable store is unique per VM.
- The runtime reports missing binaries, firmware, unsupported ABI, and unsupported guest architecture rather than claiming a boot succeeded.
- QEMU runs under an Android foreground service with a persistent notification and user-accessible stop action. The service removes its notification after the guest process exits.
- A durable session journal detects a previous unclean app-process exit. QORVEX does not automatically restart the guest; it validates the disk container structure and reports the result before the user decides what to do.
- Disk validation checks regular-file status, private-directory containment, expected logical capacity, and read/write access. It does not inspect or repair the guest filesystem and does not prove guest data consistency.
- QMP graceful shutdown is available, with forced process termination retained as a fallback.

## 7. Licensing

QEMU is GPL-2.0. Build patches and corresponding source must be available when redistributing QEMU binaries. EDK II firmware and the external Android build recipe have separate notices; see [THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md).
