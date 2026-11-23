#!/usr/bin/env bash
set -euo pipefail
# Native host build of the QORVEX Android ARM64 QEMU runtime. No Docker.
ROOT="$(cd "$(dirname "\${BASH_SOURCE[0]}")/.." && pwd)"
WORK="\${QORVEX_QEMU_WORKDIR:-\${RUNNER_TEMP:-/tmp}/qorvex-qemu-build}"
PODROID_REPO="https://github.com/ExTV/Podroid.git"
PODROID_COMMIT="e3fc1befa3c135d28bf8d40e3c2264d93fea87ef"
QEMU_VERSION=11.1.2
NDK_VERSION=r27c
NDK_SHA1=090e8083a715fdb1a3e402d0763c388abb03fb4e
LIBUCONTEXT_COMMIT=49e671dd52ff6791295d8161ad3b6da7dc5f6f9d
OUT="$ROOT/out/qemu-runtime"
PODROID_DIR="$WORK/Podroid"
SRC="$WORK/sources"
PREFIX="$WORK/deps"
NDK_ROOT="\${ANDROID_NDK_HOME:-\${ANDROID_NDK_ROOT:-$WORK/android-ndk-$NDK_VERSION}}"
JOBS="\${QORVEX_BUILD_JOBS:-$(getconf _NPROCESSORS_ONLN)}"

for tool in git curl unzip sha1sum make ninja meson pkg-config flex bison autoconf automake libtoolize gcc g++ patchelf readelf python3; do
 command -v "$tool" >/dev/null || { echo "Missing build prerequisite: $tool" >&2; exit 1; }
done
mkdir -p "$WORK" "$SRC" "$PREFIX"/{lib,include,lib/pkgconfig} "$OUT/jniLibs/arm64-v8a" "$OUT/assets/firmware"
if [ ! -d "$PODROID_DIR/.git" ]; then git clone "$PODROID_REPO" "$PODROID_DIR"; fi
git -C "$PODROID_DIR" fetch --depth 1 origin "$PODROID_COMMIT"
git -C "$PODROID_DIR" checkout --detach "$PODROID_COMMIT"

if [ ! -x "$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang" ]; then
 archive="$WORK/android-ndk-$NDK_VERSION-linux.zip"
 curl -fL "https://dl.google.com/android/repository/android-ndk-$NDK_VERSION-linux.zip" -o "$archive"
 echo "$NDK_SHA1  $archive" | sha1sum -c -
 unzip -q "$archive" -d "$WORK"
 rm -f "$archive"
fi
export NDK="$NDK_ROOT" LLVM="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64" PREFIX
export CC="$LLVM/bin/aarch64-linux-android26-clang" AR="$LLVM/bin/llvm-ar" RANLIB="$LLVM/bin/llvm-ranlib" STRIP="$LLVM/bin/llvm-strip"
test -x "$CC" || { echo "Android NDK clang not found: $CC" >&2; exit 1; }

cat > "$WORK/pkg-config-android" <<'PKGCFG'
#!/bin/sh
export PKG_CONFIG_LIBDIR="\${PREFIX}/lib/pkgconfig"
export PKG_CONFIG_PATH=
exec pkg-config "$@"
PKGCFG
chmod +x "$WORK/pkg-config-android"
ln -sf "$WORK/pkg-config-android" "$LLVM/bin/llvm-pkg-config"
cat > "$WORK/cross.ini" <<EOF
[binaries]
c = '$CC'
cpp = '$LLVM/bin/aarch64-linux-android26-clang++'
ar = '$AR'
strip = '$STRIP'
ranlib = '$RANLIB'
nm = '$LLVM/bin/llvm-nm'
pkg-config = '$WORK/pkg-config-android'
[properties]
sys_root = '$LLVM/sysroot'
pkg_config_libdir = ['$PREFIX/lib/pkgconfig']
c_args = ['--sysroot=$LLVM/sysroot', '-target', 'aarch64-linux-android26', '-I$PREFIX/include', '-fPIC', '-O2', '-march=armv8-a']
cpp_args = ['--sysroot=$LLVM/sysroot', '-target', 'aarch64-linux-android26', '-I$PREFIX/include', '-fPIC', '-O2', '-march=armv8-a']
c_link_args = ['--sysroot=$LLVM/sysroot', '-target', 'aarch64-linux-android26', '-L$PREFIX/lib', '-Wl,-z,max-page-size=16384']
cpp_link_args = ['--sysroot=$LLVM/sysroot', '-target', 'aarch64-linux-android26', '-L$PREFIX/lib', '-Wl,-z,max-page-size=16384']
[host_machine]
system = 'linux'
cpu_family = 'aarch64'
cpu = 'aarch64'
endian = 'little'
EOF
fetch_tar() {
 local url="$1" archive="$2" dir="$3"
 if [ ! -d "$SRC/$dir" ]; then curl -fL "$url" -o "$SRC/$archive"; tar -xf "$SRC/$archive" -C "$SRC"; rm -f "$SRC/$archive"; fi
}

echo "Building PCRE2 10.44"
fetch_tar https://github.com/PCRE2Project/pcre2/releases/download/pcre2-10.44/pcre2-10.44.tar.gz pcre2.tar.gz pcre2-10.44
(cd "$SRC/pcre2-10.44" && ./configure --host=aarch64-linux-android --prefix="$PREFIX" --enable-static --disable-shared CC="$CC" && make -j"$JOBS" install)

echo "Building libffi 3.4.6"
fetch_tar https://github.com/libffi/libffi/releases/download/v3.4.6/libffi-3.4.6.tar.gz libffi.tar.gz libffi-3.4.6
(cd "$SRC/libffi-3.4.6" && ./configure --host=aarch64-linux-android --prefix="$PREFIX" --enable-static --disable-shared CC="$CC" && make -j"$JOBS" install)

cat > "$PREFIX/include/iconv.h" <<'ICONVH'
#ifndef PODROID_ICONV_H
#define PODROID_ICONV_H
#include <stddef.h>
typedef void *iconv_t;
iconv_t iconv_open(const char *, const char *);
size_t iconv(iconv_t, char **, size_t *, char **, size_t *);
int iconv_close(iconv_t);
#endif
ICONVH
cat > "$WORK/iconv.c" <<'ICONVC'
#include <errno.h>
#include <stddef.h>
#include <string.h>
#include <iconv.h>
iconv_t iconv_open(const char *to,const char *from){if(!to||!from){errno=EINVAL;return(iconv_t)-1;}return(iconv_t)1;}
size_t iconv(iconv_t cd,char **in,size_t *il,char **out,size_t *ol){
(void)cd;if(!in||!il||!out||!ol){errno=EINVAL;return(size_t)-1;}if(!*in||!*il)return 0;
if(!*out||!*ol){errno=E2BIG;return(size_t)-1;}size_t n=*il<*ol?*il:*ol;memcpy(*out,*in,n);
*in+=n;*out+=n;*il-=n;*ol-=n;if(*il){errno=E2BIG;return(size_t)-1;}return 0;}
int iconv_close(iconv_t cd){(void)cd;return 0;}
ICONVC
"$CC" --sysroot="$LLVM/sysroot" -target aarch64-linux-android26 -I"$PREFIX/include" -c "$WORK/iconv.c" -o "$WORK/iconv.o"
"$AR" rcs "$PREFIX/lib/libiconv.a" "$WORK/iconv.o"
cp "$PREFIX/lib/libiconv.a" "$LLVM/sysroot/usr/lib/aarch64-linux-android/26/libiconv.a"

echo "Building GLib 2.82.5"
fetch_tar https://download.gnome.org/sources/glib/2.82/glib-2.82.5.tar.xz glib.tar.xz glib-2.82.5
(cd "$SRC/glib-2.82.5" && meson setup _build --cross-file "$WORK/cross.ini" --prefix "$PREFIX" --default-library static -Dselinux=disabled -Dlibmount=disabled && ninja -C _build -j"$JOBS" install)

echo "Building Pixman 0.44.2"
fetch_tar https://cairographics.org/releases/pixman-0.44.2.tar.xz pixman.tar.xz pixman-0.44.2
(cd "$SRC/pixman-0.44.2" && meson setup _build --cross-file "$WORK/cross.ini" --prefix "$PREFIX" --default-library static -Da64-neon=disabled && ninja -C _build -j"$JOBS" install)

echo "Building attr 2.5.2"
fetch_tar https://download.savannah.gnu.org/releases/attr/attr-2.5.2.tar.gz attr.tar.gz attr-2.5.2
(cd "$SRC/attr-2.5.2" && ./configure --host=aarch64-linux-android --prefix="$PREFIX" --enable-static --disable-shared CC="$CC" && make -j"$JOBS" install)
cp "$PREFIX/lib/libattr.a" "$LLVM/sysroot/usr/lib/aarch64-linux-android/26/libattr.a"

echo "Building libucontext pinned revision"
if [ ! -d "$SRC/libucontext/.git" ]; then git clone https://github.com/kaniini/libucontext.git "$SRC/libucontext"; fi
git -C "$SRC/libucontext" fetch --depth 1 origin "$LIBUCONTEXT_COMMIT"
git -C "$SRC/libucontext" checkout --detach "$LIBUCONTEXT_COMMIT"
make -C "$SRC/libucontext" ARCH=aarch64 CC="$CC" EXPORT_UNPREFIXED=yes -j"$JOBS"
install -Dm644 "$SRC/libucontext/libucontext.a" "$PREFIX/lib/libucontext.a"
install -Dm644 "$SRC/libucontext/include/libucontext/libucontext.h" "$PREFIX/include/libucontext/libucontext.h"
install -Dm644 "$SRC/libucontext/arch/common/include/libucontext/bits.h" "$PREFIX/include/libucontext/bits.h"
cat > "$PREFIX/include/ucontext.h" <<'UCONTEXT'
#ifndef PODROID_UCONTEXT_SHIM_H
#define PODROID_UCONTEXT_SHIM_H
#include_next <ucontext.h>
#include <libucontext/libucontext.h>
#define getcontext libucontext_getcontext
#define makecontext libucontext_makecontext
#define setcontext libucontext_setcontext
#define swapcontext libucontext_swapcontext
#endif
UCONTEXT

echo "Building libusb 1.0.27"
fetch_tar https://github.com/libusb/libusb/releases/download/v1.0.27/libusb-1.0.27.tar.bz2 libusb.tar.bz2 libusb-1.0.27
(cd "$SRC/libusb-1.0.27" && ./configure --host=aarch64-linux-android --prefix="$PREFIX" --enable-static --disable-shared --disable-udev CC="$CC" && make -j"$JOBS" install)

echo "Preparing QEMU $QEMU_VERSION"
fetch_tar "https://download.qemu.org/qemu-$QEMU_VERSION.tar.xz" qemu.tar.xz "qemu-$QEMU_VERSION"
QEMU_DIR="$SRC/qemu-$QEMU_VERSION"
sed -i "s/rt = cc.find_library('rt', required: true)/rt = cc.find_library('rt', required: false)/" "$QEMU_DIR/meson.build"
printf '#undef st_atime_nsec\n#undef st_mtime_nsec\n#undef st_ctime_nsec\n' | cat - "$QEMU_DIR/fsdev/9p-marshal.h" > "$WORK/9p.h"
mv "$WORK/9p.h" "$QEMU_DIR/fsdev/9p-marshal.h"
printf '# disabled for Android Bionic\n' > "$QEMU_DIR/contrib/ivshmem-server/meson.build"
printf '# disabled for Android Bionic\n' > "$QEMU_DIR/contrib/ivshmem-client/meson.build"

cat > "$WORK/shm.h" <<'SHMH'
#ifndef PODROID_SHM_SHIM_H
#define PODROID_SHM_SHIM_H
extern int shm_open(const char *, int, unsigned);
extern int shm_unlink(const char *);
#endif
SHMH
cat > "$WORK/shm.c" <<'SHMC'
#include <sys/syscall.h>
#include <unistd.h>
#include <errno.h>
#ifndef SYS_memfd_create
#define SYS_memfd_create 279
#endif
int shm_open(const char *n,int f,unsigned m){(void)f;(void)m;while(*n=='/')n++;long fd=syscall(SYS_memfd_create,n,0);if(fd<0){errno=(int)(-fd);return -1;}return(int)fd;}
int shm_unlink(const char *n){(void)n;return 0;}
SHMC
"$CC" --sysroot="$LLVM/sysroot" -target aarch64-linux-android26 -c "$WORK/shm.c" -o "$WORK/shm.o"
"$AR" rcs "$PREFIX/lib/libshm.a" "$WORK/shm.o"

cat > "$WORK/qjmp.h" <<'JMPH'
#ifndef PODROID_QEMU_JMP_H
#define PODROID_QEMU_JMP_H
#include <setjmp.h>
extern int _qemu_setjmp(sigjmp_buf);
__attribute__((noreturn)) extern void _qemu_longjmp(sigjmp_buf,int);
#endif
JMPH
cat > "$WORK/qjmp.S" <<'JMPS'
.text
.global _qemu_setjmp
.type _qemu_setjmp,%function
_qemu_setjmp:
stp x19,x20,[x0,#0]
stp x21,x22,[x0,#16]
stp x23,x24,[x0,#32]
stp x25,x26,[x0,#48]
stp x27,x28,[x0,#64]
stp x29,x30,[x0,#80]
mov x9,sp
str x9,[x0,#96]
stp d8,d9,[x0,#104]
stp d10,d11,[x0,#120]
stp d12,d13,[x0,#136]
stp d14,d15,[x0,#152]
mov w0,#0
ret
.global _qemu_longjmp
.type _qemu_longjmp,%function
_qemu_longjmp:
ldp x19,x20,[x0,#0]
ldp x21,x22,[x0,#16]
ldp x23,x24,[x0,#32]
ldp x25,x26,[x0,#48]
ldp x27,x28,[x0,#64]
ldp x29,x30,[x0,#80]
ldr x9,[x0,#96]
mov sp,x9
ldp d8,d9,[x0,#104]
ldp d10,d11,[x0,#120]
ldp d12,d13,[x0,#136]
ldp d14,d15,[x0,#152]
cmp w1,#0
csinc w0,w1,wzr,ne
br x30
.section .note.GNU-stack,"",%progbits
JMPS
"$CC" --sysroot="$LLVM/sysroot" -target aarch64-linux-android26 -c "$WORK/qjmp.S" -o "$WORK/qjmp.o"
"$AR" rcs "$PREFIX/lib/libqemujmp.a" "$WORK/qjmp.o"
sed -i '1i#include "'"$WORK"'/qjmp.h"' "$QEMU_DIR/util/coroutine-ucontext.c"
sed -i 's/\bsigsetjmp(\([^,]*\), *0)/_qemu_setjmp(\1)/g' "$QEMU_DIR/util/coroutine-ucontext.c"
sed -i 's/\bsiglongjmp(/_qemu_longjmp(/g' "$QEMU_DIR/util/coroutine-ucontext.c"

sed -i 's@^    rc = libusb_init(&ctx);@#if defined(__ANDROID__)\n    libusb_set_option(NULL, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);\n#endif\n    rc = libusb_init(\&ctx);@' "$QEMU_DIR/hw/usb/host-libusb.c"
grep -q LIBUSB_OPTION_NO_DEVICE_DISCOVERY "$QEMU_DIR/hw/usb/host-libusb.c"
sed -i 's@^    udev = speed_map\[libusb_speed\];@#if defined(__ANDROID__)\n    if (libusb_speed == LIBUSB_SPEED_UNKNOWN) { libusb_speed = s->ddesc.bcdUSB >= 0x0300 ? LIBUSB_SPEED_SUPER : s->ddesc.bMaxPacketSize0 == 64 ? LIBUSB_SPEED_HIGH : LIBUSB_SPEED_FULL; }\n#endif\n    udev = speed_map[libusb_speed];@' "$QEMU_DIR/hw/usb/host-libusb.c"

(
 cd "$QEMU_DIR"
 ./configure --cc="$CC" --cross-prefix="$LLVM/bin/llvm-" \
  --extra-cflags="-fPIC -DANDROID -include $WORK/shm.h -I$PREFIX/include -I$PREFIX/include/glib-2.0 -I$PREFIX/lib/glib-2.0/include" \
  --extra-ldflags="-L$PREFIX/lib -Wl,-z,max-page-size=16384 $PREFIX/lib/libucontext.a $PREFIX/lib/libshm.a $PREFIX/lib/libqemujmp.a" \
  --prefix="$WORK/qemu-out" --target-list=aarch64-softmmu --enable-tcg --enable-slirp --enable-virtfs --enable-libusb --enable-pie \
  --disable-docs --disable-gtk --disable-sdl --disable-vnc --disable-vhost-user --disable-plugins --with-coroutine=ucontext
 make -j"$JOBS" install
)
"$CC" --sysroot="$LLVM/sysroot" -target aarch64-linux-android26 -fPIE -pie -Wl,-z,max-page-size=16384 "$PODROID_DIR/podroid-bridge.c" -o "$WORK/qemu-out/libpodroid-bridge.so"
"$CC" --sysroot="$LLVM/sysroot" -target aarch64-linux-android26 -fPIE -pie -Wl,-z,max-page-size=16384 "$PODROID_DIR/podroid-launcher.c" -o "$WORK/qemu-out/libpodroid-launcher.so"
cp "$WORK/qemu-out/bin/qemu-system-aarch64" "$OUT/jniLibs/arm64-v8a/libqemu-system-aarch64.so"
cp "$WORK/qemu-out/lib/libslirp.so.0" "$OUT/jniLibs/arm64-v8a/libslirp.so"
patchelf --set-soname libslirp.so "$OUT/jniLibs/arm64-v8a/libslirp.so"
patchelf --replace-needed libslirp.so.0 libslirp.so "$OUT/jniLibs/arm64-v8a/libqemu-system-aarch64.so"
cp "$WORK/qemu-out/libpodroid-bridge.so" "$OUT/jniLibs/arm64-v8a/"
cp "$WORK/qemu-out/libpodroid-launcher.so" "$OUT/jniLibs/arm64-v8a/"
chmod 755 "$OUT/jniLibs/arm64-v8a/libqemu-system-aarch64.so"
bash "$ROOT/tools/build-aavmf-from-source.sh"

python3 - "$OUT/jniLibs/arm64-v8a/libqemu-system-aarch64.so" "$OUT/jniLibs/arm64-v8a/libslirp.so" <<'PY'
import struct,sys
for path in sys.argv[1:]:
 d=open(path,"rb").read()
 if d[:4]!=b"\x7fELF" or d[4]!=2 or d[5]!=1: raise SystemExit(f"{path}: invalid ELF64")
 if struct.unpack_from("<H",d,18)[0]!=183: raise SystemExit(f"{path}: not AArch64")
 if path.endswith("libqemu-system-aarch64.so") and struct.unpack_from("<H",d,16)[0]!=3: raise SystemExit("QEMU must be ET_DYN")
 po=struct.unpack_from("<Q",d,32)[0]; es=struct.unpack_from("<H",d,54)[0]; n=struct.unpack_from("<H",d,56)[0]; aligns=[]; interp=None
 for i in range(n):
  o=po+i*es; t=struct.unpack_from("<I",d,o)[0]
  if t==1: aligns.append(struct.unpack_from("<Q",d,o+48)[0])
  elif t==3:
   a=struct.unpack_from("<Q",d,o+8)[0]; z=struct.unpack_from("<Q",d,o+32)[0]; interp=d[a:a+z].rstrip(b"\0").decode()
 if not aligns or any(a<16384 for a in aligns): raise SystemExit(f"{path}: bad 16 KiB alignment {aligns}")
 if path.endswith("libqemu-system-aarch64.so") and interp!="/system/bin/linker64": raise SystemExit(f"wrong interpreter: {interp}")
 print("Verified",path,"AArch64 16 KiB")
PY
for f in "$OUT/jniLibs/arm64-v8a/libslirp.so" "$OUT/jniLibs/arm64-v8a/libpodroid-bridge.so" "$OUT/jniLibs/arm64-v8a/libpodroid-launcher.so" "$OUT/assets/firmware/AAVMF_CODE.fd" "$OUT/assets/firmware/AAVMF_VARS.fd"; do
 test -s "$f" || { echo "Missing output $f" >&2; exit 1; }
done
echo "Docker-free QEMU runtime artifacts ready: $OUT"
