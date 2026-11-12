#!/bin/bash
# Builds libLeapJava.dylib (JNI bridge to Ultraleap LeapC) for macOS.
# Produces a universal binary (arm64 + x86_64) when the SDK allows it.
# Author: Tiago Martins
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="${1:-$HERE/build}"
mkdir -p "$OUT_DIR"

# JNI headers: prefer JAVA_HOME, then java_home, then Processing's bundled JDK (no headers there usually)
JDK="${JAVA_HOME:-}"
if [ -z "$JDK" ] || [ ! -f "$JDK/include/jni.h" ]; then
  JDK="$(/usr/libexec/java_home 2>/dev/null || true)"
fi
if [ -z "$JDK" ] || [ ! -f "$JDK/include/jni.h" ]; then
  echo "ERROR: jni.h not found. Set JAVA_HOME to a JDK (17+) with include/jni.h" >&2
  exit 1
fi

# SDK: the Xcode SDK links reliably; the CLT 27 SDK has a malformed tbd for older clang.
SDKROOT_X="/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk"
SYSROOT=""
if [ -d "$SDKROOT_X" ]; then SYSROOT="-isysroot $SDKROOT_X"; fi

CFLAGS="-O2 -fPIC -Wall -Wno-unused-function -mmacosx-version-min=11.0 -I$HERE -I$JDK/include -I$JDK/include/darwin"
LDFLAGS="-dynamiclib -install_name @rpath/libLeapJava.dylib -framework CoreFoundation"

build_arch() {
  local arch="$1" out="$2"
  echo "  clang -arch $arch -> $out"
  clang $SYSROOT -arch "$arch" $CFLAGS $LDFLAGS "$HERE/leapjni.c" -o "$out" -ldl -lpthread
}

echo "Building libLeapJava.dylib (JDK headers: $JDK)"
build_arch arm64 "$OUT_DIR/libLeapJava-arm64.dylib"
if build_arch x86_64 "$OUT_DIR/libLeapJava-x86_64.dylib" 2>/dev/null; then
  lipo -create "$OUT_DIR/libLeapJava-arm64.dylib" "$OUT_DIR/libLeapJava-x86_64.dylib" -output "$OUT_DIR/libLeapJava.dylib"
  echo "  universal binary created"
else
  echo "  x86_64 build failed; shipping arm64 only"
  cp "$OUT_DIR/libLeapJava-arm64.dylib" "$OUT_DIR/libLeapJava.dylib"
fi
# ad-hoc signature so macOS loads it without complaint
codesign --force --sign - "$OUT_DIR/libLeapJava.dylib" >/dev/null 2>&1 || true
file "$OUT_DIR/libLeapJava.dylib"
