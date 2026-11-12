#!/bin/bash
# Builds the fake libLeapC used by the bridge tests. Author: Tiago Martins
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$HERE/build"
SDK="/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk"
SYS=""; [ -d "$SDK" ] && SYS="-isysroot $SDK"
clang $SYS -O1 -Wall -dynamiclib -install_name @rpath/libLeapC-mock.dylib -I"$HERE/.." "$HERE/mock_leapc.c" -o "$HERE/build/libLeapC-mock.dylib" -lm
codesign --force --sign - "$HERE/build/libLeapC-mock.dylib" >/dev/null 2>&1 || true
echo "built $HERE/build/libLeapC-mock.dylib"
