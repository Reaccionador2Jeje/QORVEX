#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
QEMU="$ROOT/tools/build-qemu-android.sh"
EDK2="$ROOT/tools/build-aavmf-from-source.sh"
PREFLIGHT="$ROOT/tools/fdroid-preflight.sh"

for file in "$QEMU" "$EDK2" "$PREFLIGHT"; do
  bash -n "$file"
  echo "PASS: shell syntax: ${file#$ROOT/}"
done

grep -Fq 'OFFLINE="${QORVEX_OFFLINE:-0}"' "$QEMU"
grep -Fq 'Offline source tree missing' "$QEMU"
grep -Fq 'Podroid revision mismatch' "$QEMU"
grep -Fq 'libucontext revision mismatch' "$QEMU"
grep -Fq 'Offline EDK II source missing' "$EDK2"
grep -Fq 'EDK II revision mismatch' "$EDK2"
grep -Fq 'submodule status --recursive' "$EDK2"
grep -Fq 'Offline EDK II Python environment missing' "$EDK2"
echo "PASS: offline mode checks staged source trees, immutable Git revisions, submodules, and Python environment"

if grep -Fq 'OFFLINE=1' "$QEMU" && grep -Fq 'OFFLINE=1' "$EDK2"; then
  echo "PASS: both native builders have offline-mode branches"
else
  echo "FAIL: offline mode branch missing" >&2
  exit 1
fi

echo "Offline-mode guard tests passed."
