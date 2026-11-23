# QORVEX Roadmap

## Phase 0 — Repository foundation
- [x] Establish project identity and repository.
- [x] Document architecture and licensing boundary.
- [x] Establish Android Gradle project and CI build workflow.
- [ ] Add contribution and third-party notice policies.

## Phase 1 — Android application shell
- [x] Main dashboard.
- [x] VM profile data model.
- [x] Create VM configuration screen.
- [x] Local persistence for VM profiles.
- [x] Delete saved VM profiles.
- [ ] Import installation media through Android's Storage Access Framework.
- [ ] Basic UI tests.

## Phase 2 — Runtime abstraction
- [x] Define runtime and capability interfaces.
- [x] Add explicit not-installed runtime implementation.
- [ ] Implement a mock runtime for UI lifecycle testing.
- [ ] Add observable VM lifecycle state machine.
- [ ] Add serial console event model.

## Phase 3 — QEMU integration
- [ ] Select and pin an upstream QEMU version.
- [ ] Audit Android-compatible build dependencies and licenses.
- [ ] Build QEMU for supported Android ABIs.
- [ ] Package required native libraries and firmware.
- [ ] Implement JNI boundary.
- [ ] Verify launch and shutdown on a physical ARM64 Android device.

## Phase 4 — First guest boot
- [ ] Add ARM64 machine configuration.
- [ ] Add firmware management.
- [ ] Add virtual disk creation and attachment.
- [ ] Add installation media selection.
- [ ] Boot a minimal ARM64 Linux guest on a physical device.

## Phase 5 — Usable virtual PC
- [ ] Touch keyboard and pointer controls.
- [ ] Display scaling and rotation.
- [ ] VM pause/resume where supported.
- [ ] Persistent VM profiles.
- [ ] Import/export and backup flows.

## Release gate

A release must not be described as a working PC emulator until a guest has been booted and tested on a real supported Android device. CI compilation and emulator-host smoke tests are necessary but not sufficient.
