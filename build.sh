#!/bin/bash
# Builds the Processing library "Leap Motion for Processing" (Apple Silicon / Windows / Linux fork):
#   - compiles com.leapmotion.leap (LeapC based re-implementation of the V2 Java API)
#   - compiles de.voidplus.leapmotion (the Processing wrapper, forked from nok/leap-motion-processing)
#   - builds the JNI bridge for the current platform (unless SKIP_NATIVE=1)
#   - assembles dist/LeapMotionForProcessing (Processing 4 library layout) with every native
#     variant found in native/build/, and zips it
# Environment: PROCESSING_APP (default /Applications/Processing.app), JAVA_HOME, SKIP_NATIVE=1, CORE_JAR
# Author: Tiago Martins
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

PROCESSING_VERSION="4.4.10"
CORE_JAR="${CORE_JAR:-}"
if [ -z "$CORE_JAR" ]; then
  PROCESSING_APP="${PROCESSING_APP:-/Applications/Processing.app}"
  CORE_JAR="$(ls "$PROCESSING_APP"/Contents/app/resources/core/library/core-*.jar 2>/dev/null | head -1 || true)"
fi
if [ -z "$CORE_JAR" ] || [ ! -f "$CORE_JAR" ]; then
  # no Processing installed (e.g. CI): use the core jar published on Maven Central
  mkdir -p build/deps
  CORE_JAR="build/deps/core-$PROCESSING_VERSION.jar"
  if [ ! -f "$CORE_JAR" ]; then
    echo "== downloading Processing core $PROCESSING_VERSION from Maven Central"
    curl -fsSL -o "$CORE_JAR" "https://repo1.maven.org/maven2/org/processing/core/$PROCESSING_VERSION/core-$PROCESSING_VERSION.jar"
  fi
fi

JDK="${JAVA_HOME:-}"
if [ -z "$JDK" ] || [ ! -x "$JDK/bin/javac" ]; then JDK="$(/usr/libexec/java_home 2>/dev/null || true)"; fi
if [ -z "$JDK" ] || [ ! -x "$JDK/bin/javac" ]; then JDK="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"; fi
if [ ! -x "$JDK/bin/javac" ]; then
  echo "ERROR: javac not found. Install a JDK 17+ or set JAVA_HOME" >&2
  exit 1
fi
JAVAC="$JDK/bin/javac"; JAR="$JDK/bin/jar"
# classpath separator: ';' on Windows (Git Bash / MSYS), ':' elsewhere
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; *) SEP=":" ;; esac

VERSION="$(sed -n 's/^prettyVersion=//p' library.properties)"
echo "== Leap Motion for Processing $VERSION"
echo "   core:  $CORE_JAR"
echo "   javac: $JAVAC"

rm -rf build/classes build/test-classes dist
mkdir -p build/classes build/test-classes native/build

echo "== compiling Java"
$JAVAC --release 17 -encoding UTF-8 -nowarn -cp "$CORE_JAR" -d build/classes $(find src -name "*.java")
$JAVAC --release 17 -encoding UTF-8 -nowarn -cp "$CORE_JAR${SEP}build/classes" -d build/test-classes $(find test/java -name "*.java")

echo "== packaging jars"
$JAR --create --file build/LeapJava.jar -C build/classes com
$JAR --create --file build/LeapMotionForProcessing.jar -C build/classes de

if [ "${SKIP_NATIVE:-0}" != "1" ]; then
  echo "== building native bridge"
  native/build_native.sh "$ROOT/native/build"
fi

echo "== assembling dist/LeapMotionForProcessing"
D="dist/LeapMotionForProcessing"
mkdir -p "$D/library" "$D/examples" "$D/src" "$D/reference"
cp library.properties "$D/"
cp build/LeapJava.jar build/LeapMotionForProcessing.jar "$D/library/"
FOUND=0
for v in macos-aarch64 macos-x86_64 windows-amd64 linux-amd64 linux-aarch64 linux-armv6hf; do
  if [ -d "native/build/$v" ]; then
    mkdir -p "$D/library/$v"; cp native/build/$v/*.dylib native/build/$v/*.so native/build/$v/*.dll "$D/library/$v/" 2>/dev/null || true; FOUND=$((FOUND+1))
    echo "   native: $v"
  fi
done
[ "$FOUND" -gt 0 ] || echo "   WARNING: no native binaries found in native/build/"
cp -R examples/. "$D/examples/"
cp -R src/. "$D/src/"
cp README.md LICENSE.txt "$D/"
cp README.md "$D/reference/index.md"
mkdir -p "$D/reference/images" && cp resources/* "$D/reference/images/" 2>/dev/null || true
mkdir -p "$D/driver" && cp driver/*.sh driver/*.plist driver/*.c "$D/driver/" 2>/dev/null || true
if [ "$(uname -s)" = "Darwin" ] && [ "${SKIP_NATIVE:-0}" != "1" ]; then
  driver/build_leapcheck.sh >/dev/null && mkdir -p "$D/driver/bin" && cp driver/bin/leapcheck "$D/driver/bin/"
fi
mkdir -p "$D/install" && cp install/* "$D/install/" 2>/dev/null || true
find "$D" -name ".DS_Store" -delete
rm -f dist/LeapMotionForProcessing.zip
if command -v zip >/dev/null 2>&1; then
  ( cd dist && zip -qr LeapMotionForProcessing.zip LeapMotionForProcessing )
else
  # no zip command (Git Bash on Windows): the JDK's jar tool writes a plain zip
  "$JAR" --create --no-manifest --file dist/LeapMotionForProcessing.zip -C dist LeapMotionForProcessing
fi
echo "== done: $D  and  dist/LeapMotionForProcessing.zip"
