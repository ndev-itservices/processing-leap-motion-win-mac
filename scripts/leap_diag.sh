#!/bin/bash
# End-to-end diagnostic with the REAL Ultraleap service: JNI bridge -> libLeapC -> libtrack_server -> device.
# Usage: scripts/leap_diag.sh [seconds]
# Author: Tiago Martins
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
JDK="${JAVA_HOME:-$(/usr/libexec/java_home)}"
LIB="dist/LeapMotionForProcessing/library"
[ -f "$LIB/LeapJava.jar" ] || ./build.sh
[ -d build/test-classes ] || scripts/run_tests.sh >/dev/null
"$JDK/bin/java" -Djava.library.path="$ROOT/$LIB/macos-aarch64" -cp "$ROOT/$LIB/LeapJava.jar:$ROOT/build/test-classes" LeapDiag "${1:-10}"
