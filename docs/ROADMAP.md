# QORVEX Roadmap

## Phase 0 — Repository foundation
- [x] Establish project identity and repository.
- [x] Document architecture and licensing boundary.
- [ ] Establish Android build pipeline.
- [ ] Add contribution and third-party notice policies.

## Phase 1 — Android application shell
- [ ] Main dashboard and navigation.
- [ ] VM profile data model.
- [ ] Create/edit VM configuration screens.
- [ ] Empty states and error handling.
- [ ] Basic UI tests.

## Phase 2 — Runtime abstraction
- [ ] Define runtime and capability interfaces.
- [ ] Implement a mock runtime for UI development.
- [ ] Add lifecycle state machine.
- [ ] Add serial console event model.

## Phase 3 — QEMU integration
- [ ] Select and pin an upstream QEMU version.
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
