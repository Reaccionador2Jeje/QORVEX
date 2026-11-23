# QORVEX Architecture

## 1. Design principles

1. Keep the Android interface independent from QEMU implementation details.
2. Never report a VM as running until the runtime confirms it.
3. Keep guest files in app-private storage by default.
4. Make architecture, machine type, acceleration availability, and runtime limitations visible.
5. Track QEMU and all third-party licenses separately from original AtDev code.

## 2. High-level components

    Android UI (Jetpack Compose)
            |
            v
    VM profiles and storage managers
            |
            v
    VirtualMachineRuntime interface
            |
            v
    Native bridge (JNI)
            |
            v
    QEMU system emulator [not yet integrated]
            |
            +---- Guest firmware
            +---- Virtual disks
            +---- Imported installation media
            +---- Virtual devices and display

## 3. Runtime boundary

The UI must depend on an application-level runtime contract rather than directly invoking native QEMU functions.

The runtime must eventually support:
- Discovering supported guest architectures and machine types.
- Validating a VM configuration before launch.
- Starting, stopping, and observing a virtual machine.
- Receiving serial-console output and runtime errors.
- Reporting acceleration support and fallback behavior.
- Managing the process lifecycle through an Android foreground execution model.

## 4. Initial target

The first target is ARM64 Android hosts running ARM64 guests. This does not imply that every Android device supports hardware virtualization. QEMU TCG may be used where appropriate, subject to performance and Android platform constraints. Hardware acceleration must only be enabled after capability detection and validation.

## 5. Storage model

Each VM profile has:
- A machine configuration.
- An app-private sparse RAW disk file, created on demand.
- Optional installation media copied into app-private storage.
- Display-name and SHA-256 metadata for imported media.

The installation-media importer streams data to a temporary file, checks available storage, validates the copied byte count when the document provider reports a size, calculates SHA-256, and only then replaces the previous image. The resulting file is addressable by the VM ID through `InstallationMediaManager.getMediaFile()`.

The disk and media managers currently manage host-side files only. They are not yet connected to QEMU's block layer.

## 6. Security and reliability

- Use the Android document picker instead of broad storage permissions.
- Never execute imported media on the Android host.
- Do not overwrite a disk image during disk creation.
- Keep VM process lifecycle tied to an explicit foreground execution model.
- Surface crashes and unsupported configurations as actionable errors.
- Do not report a VM as running before the QEMU runtime confirms it.

## 7. Licensing boundary

QEMU is an upstream open-source project with GPL obligations. QORVEX must track exact QEMU versions, patches, build options, bundled libraries, notices, and corresponding source obligations for every distributable build.
