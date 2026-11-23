#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${QORVEX_QEMU_WORKDIR:-${RUNNER_TEMP:-/tmp}/qorvex-qemu-build}"
PODROID_REPO="https://github.com/ExTV/Podroid.git"
PODROID_COMMIT="e3fc1befa3c135d28bf8d40e3c2264d93fea87ef"
QEMU_VERSION="11.1.2"
OUT="$ROOT/out/qemu-runtime"
PODROID_DIR="$WORK/Podroid"

command -v git >/dev/null || { echo "git is required" >&2; exit 1; }
command -v docker >/dev/null || { echo "Docker is required" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required" >&2; exit 1; }
command -v readelf >/dev/null || { echo "readelf is required" >&2; exit 1; }

mkdir -p "$WORK" "$OUT/jniLibs/arm64-v8a" "$OUT/assets/firmware"
if [ ! -d "$PODROID_DIR/.git" ]; then
    git clone "$PODROID_REPO" "$PODROID_DIR"
fi
git -C "$PODROID_DIR" fetch --depth 1 origin "$PODROID_COMMIT"
git -C "$PODROID_DIR" checkout --detach "$PODROID_COMMIT"

echo "Building upstream QEMU $QEMU_VERSION with the pinned Android ARM64 build recipe..."
docker build \
    --target qemu-builder \
    --build-arg "QEMU_VERSION=$QEMU_VERSION" \
    --tag qorvex-qemu-builder \
    "$PODROID_DIR"

container_id="$(docker create qorvex-qemu-builder)"
cleanup() { docker rm -f "$container_id" >/dev/null 2>&1 || true; }
trap cleanup EXIT

docker cp "$container_id:/opt/qemu-out/libqemu-system-aarch64.so" \
    "$OUT/jniLibs/arm64-v8a/libqemu-system-aarch64.so"
docker cp "$container_id:/opt/qemu-out/libslirp.so" \
    "$OUT/jniLibs/arm64-v8a/libslirp.so"
chmod 755 "$OUT/jniLibs/arm64-v8a/libqemu-system-aarch64.so"

echo "Extracting Debian ARM64 UEFI firmware and its copyright notice..."
docker run --rm \
    -v "$OUT/assets/firmware:/out" \
    debian:bookworm-slim \
    bash -euxc '
      apt-get update
      apt-get install -y --no-install-recommends qemu-efi-aarch64 ca-certificates
      cp -L /usr/share/AAVMF/AAVMF_CODE.fd /out/AAVMF_CODE.fd
      cp -L /usr/share/AAVMF/AAVMF_VARS.fd /out/AAVMF_VARS.fd
      cp /usr/share/doc/qemu-efi-aarch64/copyright /out/EDK2-COPYRIGHT
    '

echo "Checking QEMU ELF architecture and Android 16 KiB page alignment..."
python3 - "$OUT/jniLibs/arm64-v8a/libqemu-system-aarch64.so" <<'PY'
import struct
import sys

path = sys.argv[1]
data = open(path, "rb").read()
if data[:4] != b"\x7fELF" or data[4] != 2 or data[5] != 1:
    raise SystemExit("QEMU artifact is not a little-endian ELF64 binary")
machine = struct.unpack_from("<H", data, 18)[0]
if machine != 183:
    raise SystemExit(f"Expected ELF AArch64 (183), got e_machine={machine}")
phoff = struct.unpack_from("<Q", data, 32)[0]
phentsize = struct.unpack_from("<H", data, 54)[0]
phnum = struct.unpack_from("<H", data, 56)[0]
alignments = []
for index in range(phnum):
    offset = phoff + index * phentsize
    p_type = struct.unpack_from("<I", data, offset)[0]
    if p_type == 1:
        alignments.append(struct.unpack_from("<Q", data, offset + 48)[0])
if not alignments or any(value < 16384 for value in alignments):
    raise SystemExit(f"QEMU ELF LOAD segments are not 16 KiB aligned: {alignments}")
print(f"Verified AArch64 ELF; LOAD alignments: {alignments}")
PY

test -s "$OUT/jniLibs/arm64-v8a/libslirp.so"
test -s "$OUT/assets/firmware/AAVMF_CODE.fd"
test -s "$OUT/assets/firmware/AAVMF_VARS.fd"
echo "QEMU runtime artifacts are ready in $OUT"
