# QORVEX Roadmap

## Phase 0 — Repository foundation
- [x] Establish project identity and repository.
- [x] Document architecture and licensing boundary.
- [x] Establish Android Gradle project and CI workflow.
- [x] Record third-party QEMU and firmware provenance.

## Phase 1 — Android application and storage
- [x] Main dashboard and VM profile editor.
- [x] Local persistence for VM profiles.
- [x] Delete saved VM profiles.
- [x] Create sparse RAW disk files in app-private storage.
- [x] Inspect logical disk capacity and physical block allocation.
- [x] Import installation media into app-private storage.
- [x] Record SHA-256 metadata for imported media.

## Phase 2 — QEMU ARM64 runtime
- [x] Pin upstream QEMU 11.1.2 through the Android build recipe.
- [x] Build QEMU for Android/Bionic ARM64 with TCG (verified in GitHub Actions).
- [x] Package QEMU executable and SLIRP dependency in the APK (verified in GitHub Actions).
- [x] Package AAVMF UEFI code and per-VM variable-store template (verified in GitHub Actions).
- [x] Generate launch arguments for the ARM64 `virt` machine.
- [x] Attach app-private RAW disk and optional installation ISO.
- [x] Add SLIRP user-mode networking.
- [x] Start and stop a QEMU child process.
- [x] Capture QEMU serial output in the app.
- [x] Verify the complete QEMU build, unit tests, and APK pipeline in GitHub Actions.
- [ ] Boot and install an ARM64 guest on a physical Android device.

## Phase 3 — Runtime reliability
- [ ] Move VM ownership into an Android foreground service.
- [ ] Preserve VM state across activity recreation.
- [x] Add a QMP Unix-socket control channel and graceful shutdown request (fallback termination retained).
- [ ] Add crash recovery and disk consistency checks.
- [ ] Add observable boot/readiness states and actionable diagnostics.
- [ ] Add automated process lifecycle tests.

## Phase 4 — Graphical virtual PC
- [ ] Add a QEMU display backend that renders into an Android surface.
- [ ] Add touch keyboard, pointer, and gesture input.
- [ ] Add display scaling, rotation, and resolution controls.
- [ ] Add clipboard integration.
- [ ] Add audio output.

## Phase 5 — More guest architectures and formats
- [ ] Build and package x86_64 system emulation.
- [ ] Add QCOW2 support and disk conversion tools.
- [ ] Add import/export and backup flows.
- [ ] Add multiple concurrent VM support where device resources permit.

## Release gate

A release must not be described as a complete PC emulator until a guest has booted and been tested on a physical supported Android device. A graphical release additionally requires working display and input.
