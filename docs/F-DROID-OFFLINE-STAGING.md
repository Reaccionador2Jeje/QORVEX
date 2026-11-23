# Offline source staging layout

The native builders now support an explicit offline mode. This is a build-system capability, not proof that all inputs have already been staged or checksummed.

## Required environment

Set:

- `QORVEX_OFFLINE=1`
- `QORVEX_SOURCE_ROOT=/absolute/path/to/sources`
- `QORVEX_PODROID_SOURCE=/absolute/path/to/Podroid`
- `QORVEX_EDK2_SOURCE=/absolute/path/to/edk2`
- `QORVEX_EDK2_WORKDIR=/absolute/path/to/edk2-work`
- `ANDROID_NDK_HOME=/absolute/path/to/android-ndk-r27c`

The EDK II work directory must contain a prepared `venv/bin/python` environment. The EDK II checkout must have all recursive submodules initialized at the exact revisions recorded by its superproject.

## Expected staged source trees

Under `QORVEX_SOURCE_ROOT`:

- `pcre2-10.44/`
- `libffi-3.4.6/`
- `glib-2.84.4/`
- `pixman-0.44.2/`
- `attr-2.5.2/`
- `libusb-1.0.27/`
- `qemu-11.1.2/`
- `libucontext/.git/` checked out at `49e671dd52ff6791295d8161ad3b6da7dc5f6f9d`

The Podroid checkout must be at `e3fc1befa3c135d28bf8d40e3c2264d93fea87ef`; EDK II must be at `fff6d81270b57ee786ea18ad74f43149b9f03494`.

## Behavior

In offline mode, the native scripts do not clone, fetch, download, or install Python requirements from the network. They fail immediately if a required staged checkout or source tree is absent or has the wrong Git revision.

In normal mode, the existing developer/CI download behavior remains available.

## Still required before F-Droid

- Generate and review SHA-256 hashes for all staged release archives.
- Ensure the source-staging process itself is reproducible and its archive origins are recorded.
- Ensure the NDK and Python build dependencies are staged through a policy-compliant mechanism.
- Execute the complete native build with network disabled, not merely shell syntax checks.
- Run the actual F-Droid buildserver recipe and validate the resulting APK.

The offline switch alone is not a substitute for checksum verification or a successful network-isolated build.
