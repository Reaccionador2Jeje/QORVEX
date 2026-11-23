# QORVEX Architecture

## 1. Design principles

1. Keep the Android interface independent from QEMU implementation details.
2. Never report a VM as running until the runtime confirms it.
3. Keep guest files in app-private storage by default.
4. Make architecture, machine type, accelerator availability, and runtime limitations visible.
5. Treat QEMU as a separately licensed upstream dependency.

## 2. High-level components

    Android UI (Jetpack Compose)
            |
            v
    Application / VM management
            |
            v
    VirtualMachineRuntime interface
            |
            +---- Runtime capability discovery
            |
            +---- VM lifecycle and console events
            |
            v
    Native bridge (JNI) [planned]
            |
            v
    QEMU system emulator [planned]
            |
            +---- Guest firmware
            +---- Virtual disks
            +---- Installation media
            +---- Virtual devices

## 3. Runtime boundary

The UI must depend on an application-level runtime contract rather than directly invoking native QEMU functions.

The contract should eventually support:
- Discovering supported architectures and machine types.
- Validating a VM configuration before launch.
- Starting, stopping, and observing a virtual machine.
- Receiving serial-console output and runtime errors.
- Reporting acceleration support and fallback behavior.

## 4. Initial target

The first engineering target is **ARM64 Android hosts running ARM64 guests**. This is the most practical initial compatibility target and does not imply that every Android device supports hardware virtualization.

QEMU TCG may be used where appropriate, subject to performance and Android platform constraints. Hardware acceleration must only be enabled after capability detection and validation.

## 5. Storage model

Each VM profile should reference:
- A machine configuration.
- One or more virtual disk images.
- Optional installation media.
- Firmware appropriate to the selected machine.
- A small metadata record containing display name and timestamps.

Use app-private storage initially. Import/export through Android's Storage Access Framework should be added explicitly.

## 6. Security and reliability

- Validate imported file sizes and formats.
- Avoid executing imported content on the Android host.
- Keep VM process lifecycle tied to an explicit foreground execution model.
- Do not silently delete or overwrite virtual disks.
- Surface crashes and unsupported configurations as actionable errors.
- Do not request broad storage access when a document picker is sufficient.

## 7. Licensing boundary

QEMU is an upstream open-source project with GPL licensing obligations. QORVEX must track exact QEMU versions, patches, build options, bundled libraries, notices, and corresponding source obligations for every distributable build.
