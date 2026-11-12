#!/bin/bash
# Builds the JNI bridge for the current platform:
#   macOS  -> <out>/macos-aarch64/libLeapJava.dylib and <out>/macos-x86_64/libLeapJava.dylib (same universal binary)
#   Linux  -> <out>/linux-amd64/libLeapJava.so (or linux-aarch64)
# Usage: build_native.sh [out-dir]     (default: native/build)
# Author: Tiago Martins
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="${1:-$HERE/build}"
mkdir -p "$OUT_DIR"

JDK="${JAVA_HOME:-}"
if [ -z "$JDK" ] || [ ! -f "$JDK/include/jni.h" ]; then
  JDK="$(/usr/libexec/java_home 2>/dev/null || true)"
fi
if [ -z "$JDK" ] || [ ! -f "$JDK/include/jni.h" ]; then
  JDK="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi
if [ ! -f "$JDK/include/jni.h" ]; then
  echo "ERROR: jni.h not found. Set JAVA_HOME to a JDK (17+) with include/jni.h" >&2
  exit 1
fi
echo "Building the LeapJava JNI bridge (JDK headers: $JDK)"

OS="$(uname -s)"
if [ "$OS" = "Darwin" ]; then
  SDKROOT_X="/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk"
  SYSROOT=""; [ -d "$SDKROOT_X" ] && SYSROOT="-isysroot $SDKROOT_X"
  CFLAGS="-O2 -fPIC -Wall -Wno-unused-function -mmacosx-version-min=11.0 -I$HERE -I$JDK/include -I$JDK/include/darwin"
  LDFLAGS="-dynamiclib -install_name @rpath/libLeapJava.dylib"
  TMP="$OUT_DIR/.tmp"; mkdir -p "$TMP"
  clang $SYSROOT -arch arm64 $CFLAGS $LDFLAGS "$HERE/leapjni.c" -o "$TMP/arm64.dylib" -ldl -lpthread
  if clang $SYSROOT -arch x86_64 $CFLAGS $LDFLAGS "$HERE/leapjni.c" -o "$TMP/x86_64.dylib" -ldl -lpthread 2>/dev/null; then
    lipo -create "$TMP/arm64.dylib" "$TMP/x86_64.dylib" -output "$TMP/universal.dylib"
    echo "  universal binary (arm64 + x86_64)"
  else
    cp "$TMP/arm64.dylib" "$TMP/universal.dylib"
    echo "  arm64 only (x86_64 build not available)"
  fi
  codesign --force --sign - "$TMP/universal.dylib" >/dev/null 2>&1 || true
  for v in macos-aarch64 macos-x86_64; do
    mkdir -p "$OUT_DIR/$v"; cp "$TMP/universal.dylib" "$OUT_DIR/$v/libLeapJava.dylib"
  done
  cp "$TMP/universal.dylib" "$OUT_DIR/libLeapJava.dylib"   # convenience copy for local tests
  rm -rf "$TMP"
  file "$OUT_DIR/macos-aarch64/libLeapJava.dylib"
else
  ARCH="$(uname -m)"
  case "$ARCH" in x86_64|amd64) V="linux-amd64" ;; aarch64|arm64) V="linux-aarch64" ;; armv7l|armv6l) V="linux-armv6hf" ;; *) V="linux-$ARCH" ;; esac
  mkdir -p "$OUT_DIR/$V"
  cc -O2 -fPIC -Wall -Wno-unused-function -shared -I"$HERE" -I"$JDK/include" -I"$JDK/include/linux" \
     "$HERE/leapjni.c" -o "$OUT_DIR/$V/libLeapJava.so" -ldl -lpthread
  cp "$OUT_DIR/$V/libLeapJava.so" "$OUT_DIR/libLeapJava.so"
  file "$OUT_DIR/$V/libLeapJava.so"
fi
