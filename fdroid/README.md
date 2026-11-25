# F-Droid submission preparation

This directory is a preparation area in the upstream QORVEX repository. It is **not** the official F-Droid metadata repository and does not mean QORVEX has been accepted or published.

## Current status

QORVEX is not ready for an official submission yet.

Blocking items:

1. The native QEMU/EDK II build still fetches source repositories and archives while the build runs. F-Droid needs a fully reviewable build recipe whose inputs are available to its build process without network access during compilation.
2. Every downloaded source archive needs a verified, committed checksum. Git revisions must remain full immutable commit IDs.
3. A stable release tag and matching Android version name/code have not been created.
4. A clean F-Droid build and APK comparison/review have not been completed.
5. Physical-device validation has not been completed, and the project must satisfy F-Droid's current maintenance and policy requirements.

## Intended submission

- App ID: `dev.atdev.qorvex`
- Upstream: https://github.com/Reaccionador2Jeje/QORVEX
- License of the Android application: Apache-2.0
- Native components: QEMU and third-party source components retain their own upstream licenses; firmware licensing and notices must be included with the source-built firmware.

## Before opening an fdroiddata merge request

- [ ] Make the native runtime build offline from pinned source inputs.
- [ ] Verify and record SHA-256 checksums for every source archive.
- [ ] Audit every native component and its license/notice.
- [ ] Run the complete build in a clean F-Droid buildserver.
- [ ] Run unit tests and install/test the resulting APK on a physical ARM64 Android device.
- [ ] Publish a stable, immutable Git tag.
- [ ] Complete the F-Droid metadata entry, screenshots, icon, and changelog.
- [ ] Confirm current F-Droid inclusion and maintenance policy requirements.
- [ ] Submit a merge request to the official fdroiddata repository.

Do not remove these gates merely to make a preflight script pass. A failed gate means the app is not ready for submission.
