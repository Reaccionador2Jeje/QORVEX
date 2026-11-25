#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE="$ROOT/app/build.gradle.kts"
BUILD_SCRIPT="$ROOT/tools/build-qemu-android.sh"
ERRORS=0
WARNINGS=0

fail() { echo "FAIL: $*" >&2; ERRORS=$((ERRORS + 1)); }
warn() { echo "WARN: $*" >&2; WARNINGS=$((WARNINGS + 1)); }
pass() { echo "PASS: $*"; }

[ -f "$GRADLE" ] || { echo "Missing $GRADLE" >&2; exit 2; }
[ -f "$BUILD_SCRIPT" ] || { echo "Missing $BUILD_SCRIPT" >&2; exit 2; }

VERSION_NAME="$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)".*/\1/p' "$GRADLE" | head -n1)"
VERSION_CODE="$(sed -n 's/^[[:space:]]*versionCode = \([0-9][0-9]*\).*/\1/p' "$GRADLE" | head -n1)"

if [ -z "$VERSION_NAME" ]; then
  fail "Could not read versionName from app/build.gradle.kts"
elif [[ "$VERSION_NAME" == *dev* || "$VERSION_NAME" == *SNAPSHOT* || "$VERSION_NAME" == *alpha* || "$VERSION_NAME" == *beta* ]]; then
  fail "Development version '$VERSION_NAME' is not a stable release"
else
  pass "Stable-looking versionName: $VERSION_NAME"
fi

if [[ ! "$VERSION_CODE" =~ ^[1-9][0-9]*$ ]]; then
  fail "versionCode is missing or invalid"
else
  pass "versionCode: $VERSION_CODE"
fi

for locale in en-US es-ES; do
  for file in \
    "fastlane/metadata/android/$locale/short_description.txt" \
    "fastlane/metadata/android/$locale/full_description.txt" \
    "fastlane/metadata/android/$locale/changelogs/$VERSION_CODE.txt"; do
    if [ -s "$ROOT/$file" ]; then
      pass "Metadata exists: $file"
    else
      fail "Missing release metadata: $file"
    fi
  done
done

if grep -q 'OFFLINE="${QORVEX_OFFLINE:-0}"' "$BUILD_SCRIPT" &&
   grep -q 'Offline source tree missing' "$BUILD_SCRIPT" &&
   grep -q 'Offline EDK II source missing' "$ROOT/tools/build-aavmf-from-source.sh"; then
  pass "Native scripts expose explicit offline source mode and fail when staged inputs are missing"
else
  fail "Native scripts do not enforce the offline source mode"
fi

if [ -s "$ROOT/tools/source-checksums.sha256" ]; then
  if (cd "$ROOT" && sha256sum --check tools/source-checksums.sha256); then
    pass "Source archive checksums verify"
  else
    fail "Source archive checksum verification failed"
  fi
else
  fail "tools/source-checksums.sha256 is missing; source archive SHA-256 lock is required"
fi

if [ -d "$ROOT/app/src/main/jniLibs/arm64-v8a" ]; then
  warn "Native binaries are present in the repository; verify they are generated from source and not used as F-Droid build inputs"
else
  pass "No checked-in ARM64 native binary directory detected"
fi

echo
echo "F-Droid preflight: $ERRORS blocking issue(s), $WARNINGS warning(s)."
if [ "$ERRORS" -ne 0 ]; then
  exit 1
fi
