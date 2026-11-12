#!/bin/bash
# Runs the test suite: pure Java API tests + JNI bridge test against the mock libLeapC.
# Author: Tiago Martins
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
JDK="${JAVA_HOME:-$(/usr/libexec/java_home)}"
CORE="$(ls /Applications/Processing.app/Contents/app/resources/core/library/core-*.jar | head -1)"
[ -d build/classes ] || ./build.sh
mkdir -p build/test-classes
"$JDK/bin/javac" --release 17 -encoding UTF-8 -nowarn -cp "$CORE:build/classes" -d build/test-classes $(find test/java -name "*.java")
[ -f native/build/libLeapJava.dylib ] || native/build_native.sh native/build
native/mock/build_mock.sh >/dev/null
echo "### ApiTest (synthetic frames, no native code)"
"$JDK/bin/java" -cp "build/classes:build/test-classes" com.leapmotion.leap.ApiTest
echo
echo "### BridgeTest (libLeapJava.dylib + mock libLeapC)"
"$JDK/bin/java" -Dleap.leapc.path="$ROOT/native/mock/build/libLeapC-mock.dylib" -Djava.library.path="$ROOT/build/native" \
     -cp "build/classes:build/test-classes" BridgeTest
