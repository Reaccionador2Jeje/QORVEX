# GitHub Releases

GitHub Releases is the active distribution channel for QORVEX. F-Droid packaging and its CI workflow are paused.

## Automatic publishing

Every successful non-PR build pushed to `main` or `fdroid/preparation-five-developments` automatically creates a GitHub **pre-release**. A successful `workflow_dispatch` run does the same. Version tags matching `v*` also publish a pre-release using that exact tag.

Continuous builds receive unique tags in the form `continuous-RUN_NUMBER-RUN_ATTEMPT`, so every successful run can publish independently without replacing previous releases. Re-running a workflow creates a new release tag for that attempt. Pull-request builds still compile and test, but do not publish public releases.

Each release contains:
- `QORVEX-<release-tag>-debug.apk`
- `SHA256SUMS.txt`

The workflow first builds the QEMU ARM64 runtime, then runs JVM unit tests and packages the Android APK. Release publication runs only after the build and tests succeed. If the release already exists (for example, a rerun of a version-tag build), the workflow replaces its assets.

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
