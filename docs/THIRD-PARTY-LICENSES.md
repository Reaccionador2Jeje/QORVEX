# Third-party licenses and provenance

## QEMU 11.1.2

- Project: https://www.qemu.org/
- Source release: https://download.qemu.org/qemu-11.1.2.tar.xz
- License: GNU General Public License version 2 (GPL-2.0).
- Android build target: `aarch64-softmmu`, Android/Bionic arm64-v8a, TCG.
- Build recipe and Android compatibility patches: [ExTV/Podroid at pinned commit e3fc1befa3c135d28bf8d40e3c2264d93fea87ef](https://github.com/ExTV/Podroid/tree/e3fc1befa3c135d28bf8d40e3c2264d93fea87ef). QORVEX fetches this immutable revision in `tools/build-qemu-android.sh` and builds the QEMU stage from source.

QEMU is a separate executable component. QORVEX does not claim ownership of QEMU or relicense it under AtDev terms. When distributing a QORVEX APK containing QEMU, provide the exact corresponding QEMU source, the Android build patches/recipe, and all notices required by GPL-2.0. Do not distribute a binary-only QEMU APK.

## SLIRP

QEMU's user-mode networking dependency is built as `libslirp.so` by the pinned QEMU build environment. Its license and source are included in the upstream QEMU source/build dependency records. Preserve the upstream notices when redistributing.

## EDK II / AAVMF firmware

- Project: https://www.tianocore.org/ (EDK II).
- Source: https://github.com/tianocore/edk2 at commit `fff6d81270b57ee786ea18ad74f43149b9f03494` (`edk2-stable202211`).
- Platform: `ArmVirtPkg/ArmVirtQemu.dsc`, AARCH64, RELEASE_GCC5.
- Files: `QEMU_EFI.fd` and `QEMU_VARS.fd`, packaged as `AAVMF_CODE.fd` and `AAVMF_VARS.fd` and padded to 64 MiB.
- License: BSD-2-Clause-Patent; the EDK II `License.txt` is copied into the runtime artifact as `EDK2-LICENSE.txt`.
- Build record: `EDK2-BUILD-INFO.txt` is generated alongside the firmware.

The firmware is redistributed separately from QORVEX's original Kotlin application code. Preserve its copyright and license notice.

## Android build recipe

The Android-specific QEMU build recipe is maintained by ExTV/Podroid and is GPL-2.0. QORVEX fetches it at the immutable commit listed above rather than silently copying or modifying its source. The repository's own license and attribution requirements must be respected if the recipe is vendored in the future.
