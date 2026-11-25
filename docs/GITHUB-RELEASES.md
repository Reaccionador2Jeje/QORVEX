# GitHub Releases

GitHub Releases is the active distribution channel for QORVEX. F-Droid packaging and its CI workflow are paused.

## Creating a preview

1. Merge the tested changes into `main`.
2. Create and push a version tag, for example `v0.4.0-preview.1`.
3. The Android workflow builds the QEMU ARM64 runtime, runs JVM unit tests, packages the Android debug APK, and uploads it as a workflow artifact.
4. For a `v*` tag, the workflow publishes a GitHub **pre-release** containing:
   - `QORVEX-vX.Y.Z-preview.N-debug.apk`
   - `SHA256SUMS.txt`

A failed build or test prevents the release job from running.

## Why previews are debug-signed

The repository does not yet have a persistent release-signing key configured in GitHub Actions. The pipeline therefore uses Android's CI-generated debug signing key. This is acceptable only for clearly labelled preview builds. A later CI run may use a different debug key, so Android can reject an in-place update. Users may need to uninstall the previous preview first; this removes app-private VM data, so virtual disks must be backed up beforehand.

Do not call these APKs stable or production-ready. A stable release requires a persistent signing key stored as protected GitHub Actions secrets, a documented signing/rotation policy, successful physical-device testing, and verified upgrade behavior.

## Validation still required

- Install the APK on a real ARM64 Android device.
- Create and boot a supported ARM64 guest from user-supplied installation media.
- Verify NAT, DHCP, DNS, and outbound connectivity inside the guest.
- Verify VM disk persistence and backup/restore.
- Test app upgrade and uninstall behavior.
- Verify the published APK checksum against `SHA256SUMS.txt`.

The x86_64 guest path remains experimental and is not a release guarantee.
