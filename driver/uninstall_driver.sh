#!/bin/bash
# Removes the Ultraleap Hand Tracking software installed by install_driver.sh.
# Author: Tiago Martins
set -uo pipefail
LABEL="com.ultraleap.tracking.service"
APP_SYS="/Applications/Ultraleap Hand Tracking.app"
APP_USER="$HOME/Applications/Ultraleap Hand Tracking.app"
AGENT_PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
if [ -f "$AGENT_PLIST" ]; then
  launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null || true
  rm -f "$AGENT_PLIST"; echo "removed user agent"
fi
pkill -x libtrack_server 2>/dev/null || true
[ -d "$APP_USER" ] && rm -rf "$APP_USER" && echo "removed $APP_USER"
if [ -d "$APP_SYS" ]; then
  echo "Removing the system installation (administrator password required)"
  sudo launchctl bootout "system/$LABEL" 2>/dev/null || true
  sudo rm -f "/Library/LaunchDaemons/$LABEL.plist"
  sudo rm -rf "$APP_SYS"
  sudo pkgutil --forget com.ultraleap.tracking.pkg 2>/dev/null || true
  echo "removed $APP_SYS"
fi
echo "done"
