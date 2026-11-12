#!/bin/bash
# Builds driver/bin/leapcheck (LeapC diagnostic). Author: Tiago Martins
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$HERE/bin"
SDK="/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk"
SYS=""; [ -d "$SDK" ] && SYS="-isysroot $SDK"
clang $SYS -O1 -mmacosx-version-min=11.0 -arch arm64 "$HERE/leapcheck.c" -o "$HERE/bin/leapcheck-arm64" -ldl
if clang $SYS -O1 -mmacosx-version-min=11.0 -arch x86_64 "$HERE/leapcheck.c" -o "$HERE/bin/leapcheck-x86_64" -ldl 2>/dev/null; then
  lipo -create "$HERE/bin/leapcheck-arm64" "$HERE/bin/leapcheck-x86_64" -output "$HERE/bin/leapcheck"
  rm -f "$HERE/bin/leapcheck-arm64" "$HERE/bin/leapcheck-x86_64"
else
  mv "$HERE/bin/leapcheck-arm64" "$HERE/bin/leapcheck"
fi
codesign --force --sign - "$HERE/bin/leapcheck" >/dev/null 2>&1 || true
echo "built $HERE/bin/leapcheck"
