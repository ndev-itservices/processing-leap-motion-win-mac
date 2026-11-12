#!/bin/bash
# Builds the fake libLeapC used by the bridge tests (macOS: libLeapC-mock.dylib, Linux: libLeapC-mock.so).
# Author: Tiago Martins
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$HERE/build"
if [ "$(uname -s)" = "Darwin" ]; then
  SDK="/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk"
  SYS=""; [ -d "$SDK" ] && SYS="-isysroot $SDK"
  OUT="$HERE/build/libLeapC-mock.dylib"
  clang $SYS -O1 -Wall -dynamiclib -install_name @rpath/libLeapC-mock.dylib -I"$HERE/.." "$HERE/mock_leapc.c" -o "$OUT" -lm
  codesign --force --sign - "$OUT" >/dev/null 2>&1 || true
else
  OUT="$HERE/build/libLeapC-mock.so"
  cc -O1 -Wall -fPIC -shared -fvisibility=hidden -I"$HERE/.." "$HERE/mock_leapc.c" -o "$OUT" -lm
fi
echo "built $OUT"
