#!/bin/bash
# Double-click installer for macOS. It:
#   1. installs the Ultraleap Hand Tracking software (the Leap Motion driver), if missing
#   2. copies the "Leap Motion for Processing" library into your Processing sketchbook
#   3. opens the test example in Processing
# Author: Tiago Martins
HERE="$(cd "$(dirname "$0")" && pwd)"
LIB="$(cd "$HERE/.." && pwd)"          # the LeapMotionForProcessing folder (this file lives in install/)
clear
echo "======================================================"
echo "   Leap Motion for Processing - installer (macOS)"
echo "======================================================"
echo
if [ "$(uname -s)" != "Darwin" ]; then echo "This installer is for macOS."; exit 1; fi
MAJOR="$(sw_vers -productVersion | cut -d. -f1)"
if [ "$MAJOR" -lt 11 ]; then
  echo "Your macOS is too old. macOS 11 (Big Sur) or newer is required."
  read -r -p "Press Enter to close."; exit 1
fi
if [ ! -f "$LIB/library/LeapJava.jar" ]; then
  echo "I cannot find the library files next to this installer."
  echo "Please unzip LeapMotionForProcessing.zip first and run this file from inside the unzipped folder."
  read -r -p "Press Enter to close."; exit 1
fi

echo "Step 1 of 3 - Leap Motion driver (Ultraleap Hand Tracking)"
if [ -d "/Applications/Ultraleap Hand Tracking.app" ] || [ -d "$HOME/Applications/Ultraleap Hand Tracking.app" ]; then
  echo "   already installed."
else
  echo "   The official Ultraleap installer will be downloaded (about 480 MB) and opened."
  echo "   Follow it to the end: it asks for your Mac password once."
  read -r -p "   Press Enter to continue (or close this window to skip)..."
  bash "$LIB/driver/install_driver.sh" || { echo "   The driver installation did not finish. You can run it again later."; }
fi
echo

echo "Step 2 of 3 - Processing library"
SKETCHBOOK="$HOME/Documents/Processing"
PREFS="$HOME/Library/Processing/preferences.txt"
if [ -f "$PREFS" ]; then P="$(sed -n 's/^sketchbook.path.four=//p' "$PREFS" | head -1)"; [ -n "$P" ] && SKETCHBOOK="$P"; fi
DEST="$SKETCHBOOK/libraries/LeapMotionForProcessing"
mkdir -p "$SKETCHBOOK/libraries/old"
for old in LeapJava LeapMotionForProcessing; do
  if [ -d "$SKETCHBOOK/libraries/$old" ] && [ ! -f "$SKETCHBOOK/libraries/$old/library/macos-aarch64/libLeapJava.dylib" ]; then
    mv "$SKETCHBOOK/libraries/$old" "$SKETCHBOOK/libraries/old/${old}_$(date +%Y%m%d-%H%M%S)"
    echo "   moved the old $old library to libraries/old/"
  fi
done
rm -rf "$DEST"
mkdir -p "$SKETCHBOOK/libraries"
cp -R "$LIB" "$DEST"
xattr -dr com.apple.quarantine "$DEST" 2>/dev/null || true
echo "   installed in $DEST"
echo

echo "Step 3 of 3 - test"
if pgrep -x libtrack_server >/dev/null; then
  echo "   the Leap Motion service is running."
else
  echo "   the Leap Motion service is NOT running yet. If you just installed the driver, it may take a few seconds;"
  echo "   otherwise open 'Ultraleap Hand Tracking' from your Applications folder."
fi
if [ -d /Applications/Processing.app ]; then
  echo "   opening the test example in Processing..."
  open -a Processing "$DEST/examples/LM_0_Test/LM_0_Test.pde"
  echo "   press the Run button (the triangle) in Processing. A green dot means the Leap Motion works."
else
  echo "   Processing is not installed in /Applications. Download it from https://processing.org/download"
  echo "   then open File > Examples > Contributed Libraries > Leap Motion for Processing > LM_0_Test."
fi
echo
echo "Done. You can close this window."
read -r -p "Press Enter to close."
