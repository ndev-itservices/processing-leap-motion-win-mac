#!/bin/bash
# Runs the test suite on macOS or Linux: pure Java API tests + JNI bridge test against the mock LeapC.
# (Windows: scripts\run_tests.bat)
# Author: Tiago Martins
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
JDK="${JAVA_HOME:-}"
if [ -z "$JDK" ] || [ ! -x "$JDK/bin/java" ]; then JDK="$(/usr/libexec/java_home 2>/dev/null || true)"; fi
if [ -z "$JDK" ] || [ ! -x "$JDK/bin/java" ]; then JDK="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"; fi
[ -d build/classes ] || ./build.sh
[ -d build/test-classes ] || ./build.sh
if [ ! -f native/build/libLeapJava.dylib ] && [ ! -f native/build/libLeapJava.so ]; then native/build_native.sh "$ROOT/native/build"; fi
native/mock/build_mock.sh >/dev/null
MOCK="$ROOT/native/mock/build/libLeapC-mock.dylib"; [ -f "$MOCK" ] || MOCK="$ROOT/native/mock/build/libLeapC-mock.so"
echo "### ApiTest (synthetic frames, no native code)"
"$JDK/bin/java" -cp "build/classes:build/test-classes" com.leapmotion.leap.ApiTest
echo
echo "### BridgeTest (LeapJava native bridge + mock LeapC)"
"$JDK/bin/java" -Dleap.leapc.path="$MOCK" -Djava.library.path="$ROOT/native/build" -cp "build/classes:build/test-classes" BridgeTest
