#!/usr/bin/env bash
set -euo pipefail

# Build AAVMF directly from the EDK II source tree.
# The commit is pinned so firmware inputs do not silently change.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${QORVEX_EDK2_WORKDIR:-${RUNNER_TEMP:-/tmp}/qorvex-edk2-build}"
OFFLINE="${QORVEX_OFFLINE:-0}"
EDK2_REPO="https://github.com/tianocore/edk2.git"
EDK2_COMMIT="fff6d81270b57ee786ea18ad74f43149b9f03494"
EDK2_DIR="${QORVEX_EDK2_SOURCE:-$WORK/edk2}"
OUT="$ROOT/out/qemu-runtime/assets/firmware"

for tool in make gcc python3 iasl nasm; do
    command -v "$tool" >/dev/null || {
        echo "Missing required EDK II build tool: $tool" >&2
        exit 1
    }
done

mkdir -p "$WORK" "$OUT"

if [ "$OFFLINE" = "1" ]; then
    [ -d "$EDK2_DIR/.git" ] || { echo "Offline EDK II source missing: $EDK2_DIR" >&2; exit 1; }
    [ "$(git -C "$EDK2_DIR" rev-parse HEAD)" = "$EDK2_COMMIT" ] || { echo "EDK II revision mismatch" >&2; exit 1; }
    if git -C "$EDK2_DIR" submodule status --recursive | grep -Eq '^[+-U]'; then
        echo "EDK II submodules are not fully initialized at their recorded revisions" >&2
        exit 1
    fi
    [ -x "$WORK/venv/bin/python" ] || { echo "Offline EDK II Python environment missing: $WORK/venv" >&2; exit 1; }
else
    if [ ! -d "$EDK2_DIR/.git" ]; then
        git clone https://github.com/tianocore/edk2.git "$EDK2_DIR"
    fi
    git -C "$EDK2_DIR" fetch --depth 1 origin "$EDK2_COMMIT"
    git -C "$EDK2_DIR" checkout --detach "$EDK2_COMMIT"
    git -C "$EDK2_DIR" submodule sync --recursive
    git -C "$EDK2_DIR" submodule update --init --recursive
    python3 -m venv "$WORK/venv"
    "$WORK/venv/bin/python" -m pip install --disable-pip-version-check -r "$EDK2_DIR/pip-requirements.txt"
fi
export PATH="$WORK/venv/bin:$PATH"

# PCCTS 1.33 relies on pre-C23 empty-parameter-list semantics. Newer GCC
# defaults may interpret () as a zero-argument prototype, breaking its
# generic callback calls. Keep this compatibility scoped to PCCTS tools.
for pccts_makefile in \
    "$EDK2_DIR/BaseTools/Source/C/VfrCompile/Pccts/dlg/makefile" \
    "$EDK2_DIR/BaseTools/Source/C/VfrCompile/Pccts/antlr/makefile"; do
    [ -f "$pccts_makefile" ] || {
        echo "Missing expected PCCTS makefile: $pccts_makefile" >&2
        exit 1
    }
    if ! grep -q -- "-std=gnu17" "$pccts_makefile"; then
        sed -i '/^BUILD_CFLAGS=/ s/$/ -std=gnu17/' "$pccts_makefile"
    fi
done

# Newer GCC diagnoses EDK II's mutable pointer receiving strstr's const result.
# Ptr0 is only used for this read-only extension check, so make it const-correct.
EFIROM_SOURCE="$EDK2_DIR/BaseTools/Source/C/EfiRom/EfiRom.c"
if grep -q '^  CHAR8     \*Ptr0;' "$EFIROM_SOURCE"; then
    sed -i 's/^  CHAR8     \*Ptr0;/  CONST CHAR8 *Ptr0;/' "$EFIROM_SOURCE"
elif ! grep -q '^  CONST CHAR8 \*Ptr0;' "$EFIROM_SOURCE"; then
    echo "Unexpected EfiRom Ptr0 declaration; refusing an unsafe patch" >&2
    exit 1
fi

echo "Building AAVMF from EDK II commit $EDK2_COMMIT..."
make -C "$EDK2_DIR/BaseTools" -j"$(getconf _NPROCESSORS_ONLN)"
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
