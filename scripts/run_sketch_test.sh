#!/bin/bash
# Runs test/sketch/LM_Test_Mock with the Processing CLI against the installed library and the mock libLeapC.
# Author: Tiago Martins
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MOCK="$ROOT/native/mock/build/libLeapC-mock.dylib"
[ -f "$MOCK" ] || "$ROOT/native/mock/build_mock.sh"
OUT="${TMPDIR:-/tmp}/lm_test_mock_build"
export JAVA_TOOL_OPTIONS="-Dleap.leapc.path=$MOCK -Dleap.verbose=true"
/Applications/Processing.app/Contents/MacOS/Processing cli --sketch="$ROOT/test/sketch/LM_Test_Mock" --output="$OUT" --force --run
