#!/bin/bash
# Double-click diagnostic for macOS: says in plain words what works and what is missing.
# Author: Tiago Martins
HERE="$(cd "$(dirname "$0")" && pwd)"
LIB="$(cd "$HERE/.." && pwd)"
clear
echo "============================================"
echo "   Leap Motion for Processing - check"
echo "============================================"
echo
ok=0; bad=0
say_ok()  { echo "  [OK]      $1"; ok=$((ok+1)); }
say_bad() { echo "  [PROBLEM] $1"; echo "            -> $2"; bad=$((bad+1)); }

if [ "$(sw_vers -productVersion | cut -d. -f1)" -ge 11 ]; then say_ok "macOS $(sw_vers -productVersion)"; else say_bad "macOS $(sw_vers -productVersion) is too old" "macOS 11 or newer is required"; fi
if [ -d /Applications/Processing.app ]; then say_ok "Processing is installed"; else say_bad "Processing not found in /Applications" "download it from https://processing.org/download"; fi
if [ -d "/Applications/Ultraleap Hand Tracking.app" ] || [ -d "$HOME/Applications/Ultraleap Hand Tracking.app" ]; then say_ok "Ultraleap Hand Tracking (driver) is installed"; else say_bad "the Leap Motion driver is not installed" "run 'Install Leap Motion for Processing (macOS).command'"; fi
if pgrep -x libtrack_server >/dev/null; then say_ok "the Leap Motion service is running"; else say_bad "the Leap Motion service is not running" "open 'Ultraleap Hand Tracking' from Applications, or restart your Mac"; fi
if ioreg -p IOUSB -l 2>/dev/null | grep -q '"idVendor" = 61826'; then say_ok "Leap Motion Controller found on USB"; else say_bad "no Leap Motion Controller on USB" "plug it in (try another port or cable)"; fi
SKETCHBOOK="$HOME/Documents/Processing"
PREFS="$HOME/Library/Processing/preferences.txt"
if [ -f "$PREFS" ]; then P="$(sed -n 's/^sketchbook.path.four=//p' "$PREFS" | head -1)"; [ -n "$P" ] && SKETCHBOOK="$P"; fi
if [ -f "$SKETCHBOOK/libraries/LeapMotionForProcessing/library/LeapJava.jar" ]; then say_ok "library installed in $SKETCHBOOK/libraries"; else say_bad "library not installed in the Processing sketchbook" "run 'Install Leap Motion for Processing (macOS).command'"; fi
if [ -d "$SKETCHBOOK/libraries/LeapJava" ]; then say_bad "an old 'LeapJava' library is still in $SKETCHBOOK/libraries" "delete that folder (it conflicts with the new library)"; fi
if [ -x "$LIB/driver/bin/leapcheck" ] && pgrep -x libtrack_server >/dev/null; then
  echo
  echo "  Talking to the service for 4 seconds (move a hand over the device)..."
  "$LIB/driver/bin/leapcheck" 4 2>&1 | grep -E "RESULT|device:" | sed 's/^/     /'
fi
echo
if [ "$bad" -eq 0 ]; then echo "Everything looks fine. Open Processing and run the LM_0_Test example."; else echo "$bad problem(s) found, see the arrows above."; fi
echo
read -r -p "Press Enter to close."
