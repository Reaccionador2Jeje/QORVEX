# F-Droid reproducible native build

## Why the current build is not yet suitable

`tools/build-qemu-android.sh` currently performs network operations during the build:

- Clones Podroid at a pinned commit.
- Downloads Android NDK r27c.
- Downloads PCRE2, libffi, GLib, Pixman, attr, libusb, and QEMU source archives.
- Clones libucontext at a pinned commit.
- Calls the EDK II firmware builder, which clones EDK II, initializes submodules, and installs Python requirements.

Pinning a Git revision or checking the NDK SHA-1 is useful, but it does not make the whole build offline or independently reproducible.

## Required architecture

The F-Droid build recipe must stage all inputs before the isolated build phase:

1. Use immutable source revisions for Git dependencies and recursively record submodule revisions.
2. Vendor or otherwise stage all source archives in a way accepted by F-Droid's build process.
3. Record SHA-256 for each archive and verify it before extraction.
4. Stage the exact NDK/toolchain version through an F-Droid-supported mechanism; do not download it from the build script.
5. Stage EDK II and all required submodules and Python build requirements. Do not run pip against the public internet during the build.
6. Make the native build script consume only those staged inputs.
7. Add a network-disabled test proving the build does not attempt outbound access after source staging.
8. Keep the Android Gradle build dependencies pinned and available through repositories accepted by F-Droid.

## Source pins currently present upstream

| Component | Pin |
|---|---|
| Podroid | `e3fc1befa3c135d28bf8d40e3c2264d93fea87ef` |
| QEMU | `11.1.2` |
| Android NDK | `r27c` (current script verifies published SHA-1) |
| libucontext | `49e671dd52ff6791295d8161ad3b6da7dc5f6f9d` |
| EDK II | `fff6d81270b57ee786ea18ad74f43149b9f03494` |
| PCRE2 | `10.44` |
| libffi | `3.4.6` |
| GLib | `2.84.4` |
| Pixman | `0.44.2` |
| attr | `2.5.2` |
| libusb | `1.0.27` |

These version/revision pins are an inventory, not a complete checksum lock. SHA-256 values for all archive inputs and a complete EDK II submodule/Python dependency lock are still required.

## Acceptance criteria

- The build works with network access disabled after the source-staging phase.
- Every archive checksum is verified before extraction.
- Every Git dependency and submodule resolves to an immutable revision.
- The build output contains QEMU, libslirp, Podroid bridge/launcher, AAVMF firmware, and all required license notices.
- ELF architecture, interpreter, and 16 KiB alignment checks pass.
- Unit tests and APK packaging pass from the same staged inputs.
