#!/bin/bash
# Installs dist/LeapMotionForProcessing into the Processing sketchbook libraries folder.
# Moves the old V2 "LeapJava" and "LeapMotionForProcessing" folders to libraries/old/ (they
# contain Intel-only natives and would create duplicate com.leapmotion.leap classes).
# Author: Tiago Martins
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
SKETCHBOOK="${SKETCHBOOK:-}"
if [ -z "$SKETCHBOOK" ]; then
  PREFS="$HOME/Library/Processing/preferences.txt"
  if [ -f "$PREFS" ]; then SKETCHBOOK="$(sed -n 's/^sketchbook.path.four=//p' "$PREFS" | head -1)"; fi
fi
if [ -z "$SKETCHBOOK" ]; then SKETCHBOOK="$HOME/Documents/Processing"; fi
LIBS="$SKETCHBOOK/libraries"
mkdir -p "$LIBS/old"
if [ ! -d "$ROOT/dist/LeapMotionForProcessing" ]; then
  echo "dist/ not found, building first..."; "$ROOT/build.sh"
fi
STAMP="$(date +%Y%m%d-%H%M%S)"
for old in LeapJava LeapMotionForProcessing; do
  if [ -d "$LIBS/$old" ]; then
    if [ -f "$LIBS/$old/library/macos-aarch64/libLeapJava.dylib" ]; then
      rm -rf "$LIBS/$old"   # a previous install of this fork: just replace it
    else
      echo "moving old $LIBS/$old -> $LIBS/old/${old}_v2_$STAMP"
      mv "$LIBS/$old" "$LIBS/old/${old}_v2_$STAMP"
    fi
  fi
done
cp -R "$ROOT/dist/LeapMotionForProcessing" "$LIBS/LeapMotionForProcessing"
echo "installed: $LIBS/LeapMotionForProcessing"
echo "Restart Processing, then open File > Examples > Contributed Libraries > Leap Motion for Processing."
