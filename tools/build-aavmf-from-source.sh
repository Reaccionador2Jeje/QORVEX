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
AARCH64_CROSS_PREFIX="${GCC5_AARCH64_PREFIX:-aarch64-linux-gnu-}"

for tool in git make gcc python3 iasl nasm "${AARCH64_CROSS_PREFIX}gcc"; do
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
# The bundled legacy Pccts makefiles hard-code COPT=-O and ignore CFLAGS.
# Their old unspecified-argument callbacks require GNU17 with modern GCC.
PCCTS_MAKEFILES=(
    "$EDK2_DIR/BaseTools/Source/C/VfrCompile/Pccts/antlr/makefile"
    "$EDK2_DIR/BaseTools/Source/C/VfrCompile/Pccts/dlg/makefile"
)
for makefile in "${PCCTS_MAKEFILES[@]}"; do
    test -f "$makefile"
    # These pinned legacy makefiles use CRLF; preserve it while matching either
    # CRLF or LF so the patch remains effective on Linux checkouts.
    sed -i 's/^COPT=-O\(\r\?\)$/COPT=-O -std=gnu17\1/' "$makefile"
    grep -q '^COPT=-O -std=gnu17' "$makefile"
done

# EfiRom stores the result of strstr in a pointer to mutable CHAR8, which
# triggers GCC's -Werror=discarded-qualifiers. The pointer is only read.
EFIROM_SOURCE="$EDK2_DIR/BaseTools/Source/C/EfiRom/EfiRom.c"
test -f "$EFIROM_SOURCE"
sed -i 's/^[[:space:]]*CHAR8[[:space:]]*\*Ptr0;[[:space:]]*$/  CONST CHAR8 *Ptr0;/' "$EFIROM_SOURCE"
grep -q 'CONST CHAR8 \*Ptr0;' "$EFIROM_SOURCE"

make -C "$EDK2_DIR/BaseTools" -j"$(getconf _NPROCESSORS_ONLN)"
(
    cd "$EDK2_DIR"
    set +u
    source edksetup.sh
    # EDK II otherwise falls back to host gcc, which cannot emit AArch64 code.
    export GCC5_AARCH64_PREFIX="$AARCH64_CROSS_PREFIX"
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
AARCH64 cross compiler prefix: $AARCH64_CROSS_PREFIX
Outputs: QEMU_EFI.fd -> AAVMF_CODE.fd; QEMU_VARS.fd -> AAVMF_VARS.fd
EOF

echo "Source-built AAVMF firmware is ready in $OUT"
