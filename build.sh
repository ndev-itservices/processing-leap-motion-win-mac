#!/bin/bash
# Builds the Processing library "Leap Motion for Processing" (ARM fork):
#   - compiles com.leapmotion.leap (LeapC based re-implementation of the V2 Java API)
#   - compiles de.voidplus.leapmotion (the Processing wrapper, forked from nok/leap-motion-processing)
#   - builds the JNI bridge libLeapJava.dylib (arm64 + x86_64)
#   - assembles dist/LeapMotionForProcessing (Processing 4 library layout) and a zip
# Author: Tiago Martins
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

PROCESSING_APP="${PROCESSING_APP:-/Applications/Processing.app}"
CORE_JAR="$(ls "$PROCESSING_APP"/Contents/app/resources/core/library/core-*.jar 2>/dev/null | head -1 || true)"
if [ -z "$CORE_JAR" ]; then
  CORE_JAR="$(ls "$PROCESSING_APP"/Contents/Java/core/library/core*.jar 2>/dev/null | head -1 || true)"
fi
if [ -z "$CORE_JAR" ]; then
  echo "ERROR: Processing core jar not found. Set PROCESSING_APP=/path/to/Processing.app" >&2
  exit 1
fi

JDK="${JAVA_HOME:-}"
if [ -z "$JDK" ] || [ ! -x "$JDK/bin/javac" ]; then JDK="$(/usr/libexec/java_home 2>/dev/null || true)"; fi
if [ -z "$JDK" ] || [ ! -x "$JDK/bin/javac" ]; then
  echo "ERROR: javac not found. Install a JDK 17+ or set JAVA_HOME" >&2
  exit 1
fi
JAVAC="$JDK/bin/javac"; JAR="$JDK/bin/jar"

VERSION="$(sed -n 's/^prettyVersion=//p' library.properties)"
echo "== Leap Motion for Processing $VERSION"
echo "   core:  $CORE_JAR"
echo "   javac: $JAVAC"

rm -rf build/classes build/test-classes dist
mkdir -p build/classes build/test-classes build/native

echo "== compiling Java"
$JAVAC --release 17 -encoding UTF-8 -nowarn -cp "$CORE_JAR" -d build/classes $(find src -name "*.java")
$JAVAC --release 17 -encoding UTF-8 -nowarn -cp "$CORE_JAR:build/classes" -d build/test-classes $(find test/java -name "*.java")

echo "== packaging jars"
$JAR --create --file build/LeapJava.jar -C build/classes com
$JAR --create --file build/LeapMotionForProcessing.jar -C build/classes de

echo "== building native bridge"
native/build_native.sh "$ROOT/build/native"

echo "== assembling dist/LeapMotionForProcessing"
D="dist/LeapMotionForProcessing"
mkdir -p "$D/library/macos-aarch64" "$D/library/macos-x86_64" "$D/examples" "$D/src" "$D/reference"
cp library.properties "$D/"
cp build/LeapJava.jar build/LeapMotionForProcessing.jar "$D/library/"
cp build/native/libLeapJava.dylib "$D/library/macos-aarch64/"
cp build/native/libLeapJava.dylib "$D/library/macos-x86_64/"
cp -R examples/. "$D/examples/"
cp -R src/. "$D/src/"
cp README.md LICENSE.txt "$D/"
cp README.md "$D/reference/index.md"
mkdir -p "$D/reference/images" && cp resources/* "$D/reference/images/" 2>/dev/null || true
mkdir -p "$D/driver" && cp driver/*.sh driver/*.plist driver/*.c "$D/driver/"
find "$D" -name ".DS_Store" -delete
( cd dist && rm -f LeapMotionForProcessing.zip && zip -qr LeapMotionForProcessing.zip LeapMotionForProcessing )
echo "== done: $D  and  dist/LeapMotionForProcessing.zip"
