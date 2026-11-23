# QORVEX release gates for F-Droid

This checklist separates repository work from evidence that must come from real builds and device testing.

## Gate 1 — source integrity

- [ ] All Git inputs use full immutable commit IDs.
- [ ] All source archives have independently verified SHA-256 checksums.
- [ ] EDK II submodules and Python dependencies are pinned.
- [ ] Third-party licenses and notices are complete.

## Gate 2 — offline build

- [ ] Source staging is completed before the isolated build.
- [ ] Native build scripts perform no network downloads.
- [ ] Gradle and native builds succeed with network access disabled.
- [ ] A clean F-Droid buildserver run succeeds.

## Gate 3 — release identity

- [ ] Android versionName is stable (not a development suffix).
- [ ] versionCode is unique and monotonically increasing.
- [ ] A signed Git tag points to the exact source commit.
- [ ] Fastlane metadata, icon, screenshots, and changelog describe that exact release.

## Gate 4 — product validation

- [ ] Unit tests pass.
- [ ] APK installs on a physical ARM64 Android device.
- [ ] VM creation, disk handling, serial console, networking, and graceful shutdown are tested.
- [ ] Foreground-service and notification behavior are tested on supported Android versions.
- [ ] Known limitations are documented accurately.

## Gate 5 — F-Droid review

- [ ] Current inclusion policy and maintenance expectations are met.
- [ ] fdroiddata metadata is prepared against the immutable release commit.
- [ ] F-Droid lint/build checks pass.
- [ ] A merge request is opened against the official fdroiddata repository.

## Current state

The native build is still being validated in GitHub Actions. No stable release has been created, no physical-device validation has been recorded, and no fdroiddata merge request has been submitted. Do not describe QORVEX as published on F-Droid until the repository maintainers accept it and it appears in the official index.
