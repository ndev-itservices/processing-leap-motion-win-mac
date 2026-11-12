#!/bin/bash
# Installs the Ultraleap Hand Tracking software (the "driver" for the Leap Motion Controller)
# on macOS Apple Silicon (or Intel), version 6.2.0 (Hyperion).
#
#   ./install_driver.sh            official installer (asks for the administrator password,
#                                  installs /Applications/Ultraleap Hand Tracking.app + system daemon)
#   ./install_driver.sh --user     no administrator password: installs into ~/Applications and
#                                  runs the tracking service as a user LaunchAgent
#   ./install_driver.sh --download-only
#
# The package is downloaded from the official Ultraleap CDN and its Apple signature and
# notarization are verified before anything is installed.
# Author: Tiago Martins
set -euo pipefail

VERSION="6.2.0"
BASE="https://s3.eu-west-1.amazonaws.com/downloads.ultraleap.com/software/tracking-software/$VERSION"
ARCH="$(uname -m)"
if [ "$ARCH" = "arm64" ]; then PKG_NAME="tracking-software-apple-silicon-$VERSION.pkg"; else PKG_NAME="tracking-software-apple-intel-$VERSION.pkg"; fi
URL="$BASE/$PKG_NAME"
DL="$HOME/Downloads/$PKG_NAME"
APP_SYS="/Applications/Ultraleap Hand Tracking.app"
APP_USER="$HOME/Applications/Ultraleap Hand Tracking.app"
AGENT_PLIST="$HOME/Library/LaunchAgents/com.ultraleap.tracking.service.plist"
HERE="$(cd "$(dirname "$0")" && pwd)"
MODE="official"
case "${1:-}" in
  --user) MODE="user" ;;
  --download-only) MODE="download" ;;
  -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
esac

say() { printf '\n\033[1m%s\033[0m\n' "$*"; }

if [ "$(sw_vers -productVersion | cut -d. -f1)" -lt 11 ]; then
  echo "macOS 11 or newer is required." >&2; exit 1
fi

if [ -d "$APP_SYS" ] && [ "$MODE" = "official" ]; then
  say "Ultraleap Hand Tracking is already installed in /Applications."
  "$HERE/leap_service.sh" status || true
  exit 0
fi

say "1/4 Downloading $PKG_NAME ($ARCH)"
if [ -f "$DL" ] && [ "$(stat -f%z "$DL")" -gt 100000000 ]; then
  echo "    already downloaded: $DL"
else
  curl -L --fail --progress-bar -o "$DL.part" "$URL"
  mv "$DL.part" "$DL"
fi

say "2/4 Verifying signature and notarization"
SIG="$(pkgutil --check-signature "$DL")"
echo "$SIG" | grep -q "ULTRALEAP LIMITED" || { echo "ERROR: package is not signed by ULTRALEAP LIMITED" >&2; exit 1; }
echo "$SIG" | grep -q "Notarization: trusted" || { echo "ERROR: package is not notarized by Apple" >&2; exit 1; }
echo "    OK: Developer ID Installer: ULTRALEAP LIMITED, notarized"

if [ "$MODE" = "download" ]; then echo "Package at $DL"; exit 0; fi

if [ "$MODE" = "official" ]; then
  say "3/4 Running the official installer (administrator password required)"
  if sudo -n true 2>/dev/null; then
    sudo installer -pkg "$DL" -target /
  else
    echo "    The macOS Installer will open. Complete it, then come back here."
    open -W "$DL" || open "$DL"
    for i in $(seq 1 120); do [ -d "$APP_SYS" ] && break; sleep 2; done
  fi
  if [ ! -d "$APP_SYS" ]; then
    echo "ERROR: $APP_SYS not found after installation." >&2; exit 1
  fi
  say "4/4 Checking the tracking service"
  sleep 3
  "$HERE/leap_service.sh" status || true
  echo
  echo "Done. The tracking service (libtrack_server) starts automatically at boot."
  echo "Open 'Ultraleap Hand Tracking' from /Applications to see the control panel and camera view."
  exit 0
fi

# ---------------------------------------------------------------- user mode
say "3/4 Installing into ~/Applications (no administrator rights)"
WORK="$(mktemp -d)"
pkgutil --expand "$DL" "$WORK/pkg"
mkdir -p "$WORK/payload"
( cd "$WORK/payload" && gunzip -dc "$WORK/pkg/Ultraleap-Hand-Tracking.pkg/Payload" | cpio -idm 2>/dev/null )
mkdir -p "$HOME/Applications"
rm -rf "$APP_USER"
ditto "$WORK/payload/Applications/Ultraleap Hand Tracking.app" "$APP_USER"
rm -rf "$WORK"
codesign --verify --strict "$APP_USER/Contents/bin/libtrack_server" || { echo "ERROR: libtrack_server signature invalid" >&2; exit 1; }
echo "    installed: $APP_USER"

say "4/4 Installing the user LaunchAgent"
mkdir -p "$HOME/Library/LaunchAgents" "$HOME/Library/Logs/Ultraleap"
sed -e "s|__APP__|$APP_USER|g" -e "s|__HOME__|$HOME|g" "$HERE/com.ultraleap.tracking.service.user.plist" > "$AGENT_PLIST"
launchctl bootout "gui/$(id -u)/com.ultraleap.tracking.service" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$AGENT_PLIST"
sleep 4
"$HERE/leap_service.sh" status || true
echo
echo "Done. The tracking service runs as your user and starts at login."
echo "Control panel: open \"$APP_USER/Contents/MacOS/Ultraleap Control Panel.app\""
