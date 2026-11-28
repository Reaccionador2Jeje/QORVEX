#!/usr/bin/env bash
set -euo pipefail

# Build AAVMF directly from the EDK II source tree.
# The commit is pinned so firmware inputs do not silently change.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${QORVEX_EDK2_WORKDIR:-${RUNNER_TEMP:-/tmp}/qorvex-edk2-build}"
EDK2_REPO="https://github.com/tianocore/edk2.git"
EDK2_COMMIT="fff6d81270b57ee786ea18ad74f43149b9f03494"
EDK2_DIR="$WORK/edk2"
OUT="$ROOT/out/qemu-runtime/assets/firmware"

for tool in git make gcc python3 iasl nasm; do
    command -v "$tool" >/dev/null || {
        echo "Missing required EDK II build tool: $tool" >&2
        exit 1
    }
done

mkdir -p "$WORK" "$OUT"

if [ ! -d "$EDK2_DIR/.git" ]; then
    git clone https://github.com/tianocore/edk2.git "$EDK2_DIR"
fi

git -C "$EDK2_DIR" fetch --depth 1 origin "$EDK2_COMMIT"
git -C "$EDK2_DIR" checkout --detach "$EDK2_COMMIT"
git -C "$EDK2_DIR" submodule sync --recursive
git -C "$EDK2_DIR" submodule update --init --recursive

python3 -m venv "$WORK/venv"
"$WORK/venv/bin/python" -m pip install --disable-pip-version-check -r "$EDK2_DIR/pip-requirements.txt"
export PATH="$WORK/venv/bin:$PATH"

echo "Building AAVMF from EDK II commit $EDK2_COMMIT..."
make -C "$EDK2_DIR/BaseTools" CFLAGS="-std=gnu17" -j"$(getconf _NPROCESSORS_ONLN)"
(
    cd "$EDK2_DIR"
    set +u
    source edksetup.sh
    build -a AARCH64 -t GCC5 -p ArmVirtPkg/ArmVirtQemu.dsc -b RELEASE
)

FV="$EDK2_DIR/Build/ArmVirtQemu-AARCH64/RELEASE_GCC5/FV"
test -s "$FV/QEMU_EFI.fd"
test -s "$FV/QEMU_VARS.fd"

cp "$FV/QEMU_EFI.fd" "$OUT/AAVMF_CODE.fd"
cp "$FV/QEMU_VARS.fd" "$OUT/AAVMF_VARS.fd"
truncate -s 64M "$OUT/AAVMF_CODE.fd"
truncate -s 64M "$OUT/AAVMF_VARS.fd"
cp "$EDK2_DIR/License.txt" "$OUT/EDK2-LICENSE.txt"

cat > "$OUT/EDK2-BUILD-INFO.txt" <<EOF
Firmware: AAVMF for QEMU AArch64
Source: $EDK2_REPO
Commit: $EDK2_COMMIT
Platform: ArmVirtPkg/ArmVirtQemu.dsc
Architecture: AARCH64
Build: RELEASE_GCC5
Outputs: QEMU_EFI.fd -> AAVMF_CODE.fd; QEMU_VARS.fd -> AAVMF_VARS.fd
EOF

echo "Source-built AAVMF firmware is ready in $OUT"
