# QORVEX Roadmap

## Phase 0 — Repository foundation
- [x] Establish project identity and repository.
- [x] Document architecture and licensing boundary.
- [x] Establish Android Gradle project and CI build workflow.
- [ ] Add contribution and third-party notice policies.

## Phase 1 — Android application and storage
- [x] Main dashboard and VM profile editor.
- [x] Local persistence for VM profiles.
- [x] Delete saved VM profiles.
- [x] Create sparse RAW disk files in app-private storage.
- [x] Inspect logical disk capacity and physical block allocation.
- [x] Import installation media from the Android document picker.
- [x] Copy imported media into app-private storage.
- [x] Record SHA-256 digest and imported media metadata.
- [ ] Add import cancellation and progress reporting.
- [ ] Add automated storage-manager tests.

## Phase 2 — Runtime abstraction
- [x] Define runtime and capability interfaces.
- [x] Report the engine as unavailable when QEMU is absent.
- [ ] Implement an observable VM lifecycle state machine.
- [ ] Add serial console event model.
- [ ] Add process supervision and foreground-service lifecycle.

## Phase 3 — QEMU integration
- [x] Configure NDK/CMake for Android arm64-v8a.
- [x] Add JNI load/status probe.
- [ ] Select and pin an upstream QEMU version.
- [ ] Audit Android-compatible build dependencies and licenses.
- [ ] Build QEMU system emulators for supported Android ABIs.
- [ ] Package required native libraries and firmware.
- [ ] Connect QEMU's block layer to imported ISO and RAW disk files.
- [ ] Implement guest display and input transport.
- [ ] Verify launch and shutdown on a physical ARM64 Android device.

## Phase 4 — First guest boot
- [ ] Add validated ARM64 machine and firmware configuration.
- [ ] Boot a minimal ARM64 Linux guest on a physical device.
- [ ] Verify disk persistence across app restarts.
- [ ] Verify graceful stop and recovery after runtime failure.

## Phase 5 — Usable virtual PC
- [ ] Touch keyboard and pointer controls.
- [ ] Display scaling and rotation.
- [ ] VM pause/resume where supported.
- [ ] Import/export and backup flows.
- [ ] Multiple VM lifecycle management.

## Release gate

A release must not be described as a working PC emulator until a guest has been booted and tested on a real supported Android device. CI compilation and host-side smoke tests are necessary but not sufficient.
