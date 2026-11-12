#!/bin/bash
# Controls / inspects the Ultraleap tracking service (libtrack_server).
#   leap_service.sh status | start | stop | restart | log | panel
# Works with the official system daemon (needs sudo for start/stop) and with the user
# LaunchAgent installed by install_driver.sh --user.
# Author: Tiago Martins
set -uo pipefail
LABEL="com.ultraleap.tracking.service"
APP_SYS="/Applications/Ultraleap Hand Tracking.app"
APP_USER="$HOME/Applications/Ultraleap Hand Tracking.app"
AGENT_PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
DAEMON_PLIST="/Library/LaunchDaemons/$LABEL.plist"
HERE="$(cd "$(dirname "$0")" && pwd)"

app_path() {
  if [ -d "$APP_SYS" ]; then echo "$APP_SYS"; elif [ -d "$APP_USER" ]; then echo "$APP_USER"; else echo ""; fi
}

status() {
  local app; app="$(app_path)"
  echo "Ultraleap Hand Tracking app : ${app:-NOT INSTALLED}"
  if [ -n "$app" ]; then
    echo "Version                     : $(defaults read "$app/Contents/Info.plist" CFBundleShortVersionString 2>/dev/null)"
    echo "LeapC library               : $(ls "$app"/Contents/LeapSDK/lib/libLeapC.*.dylib 2>/dev/null | head -1)"
  fi
  [ -f "$DAEMON_PLIST" ] && echo "System daemon plist         : $DAEMON_PLIST"
  [ -f "$AGENT_PLIST" ] && echo "User agent plist            : $AGENT_PLIST"
  local pid; pid="$(pgrep -x libtrack_server | head -1 || true)"
  if [ -n "$pid" ]; then
    echo "Tracking service            : RUNNING (pid $pid, user $(ps -o user= -p "$pid" | tr -d ' '))"
  else
    echo "Tracking service            : NOT RUNNING"
  fi
  if system_profiler SPUSBDataType 2>/dev/null | grep -qi "Leap Motion"; then
    echo "USB device                  : Leap Motion Controller detected"
  elif ioreg -p IOUSB -l 2>/dev/null | grep -q '"idVendor" = 61826'; then
    echo "USB device                  : Leap Motion Controller detected (vendor 0xF182)"
  else
    echo "USB device                  : no Leap Motion controller found on USB"
  fi
  if [ -x "$HERE/bin/leapcheck" ] && [ -n "$pid" ]; then
    echo
    echo "LeapC connection test (3 s):"
    "$HERE/bin/leapcheck" 3 2>&1 | sed 's/^/    /'
  fi
  [ -n "$pid" ]
}

start() {
  if [ -f "$AGENT_PLIST" ]; then
    launchctl bootstrap "gui/$(id -u)" "$AGENT_PLIST" 2>/dev/null || launchctl kickstart -k "gui/$(id -u)/$LABEL"
  elif [ -f "$DAEMON_PLIST" ]; then
    echo "System daemon: running 'sudo launchctl kickstart -k system/$LABEL' (password required)"
    sudo launchctl bootstrap system "$DAEMON_PLIST" 2>/dev/null || sudo launchctl kickstart -k "system/$LABEL"
  else
    echo "No service installed. Run install_driver.sh first." >&2; return 1
  fi
  sleep 3; status
}

stop() {
  if [ -f "$AGENT_PLIST" ]; then launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null || true; fi
  if [ -f "$DAEMON_PLIST" ] && pgrep -x libtrack_server >/dev/null; then
    echo "System daemon: running 'sudo launchctl bootout system/$LABEL' (password required)"
    sudo launchctl bootout "system/$LABEL" 2>/dev/null || true
  fi
  pkill -x libtrack_server 2>/dev/null || true
  sleep 1; status || true
}

case "${1:-status}" in
  status) status ;;
  start) start ;;
  stop) stop ;;
  restart) stop; start ;;
  log)
    if [ -f "$HOME/Library/Logs/Ultraleap/libtrack_server.log" ]; then tail -n 50 "$HOME/Library/Logs/Ultraleap/libtrack_server.log"; fi
    ls -t /var/log/ultraleap/*.log 2>/dev/null | head -1 | xargs -I{} tail -n 50 {} ;;
  panel)
    app="$(app_path)"; [ -n "$app" ] && open "$app/Contents/MacOS/Ultraleap Control Panel.app" ;;
  *) echo "usage: $0 status|start|stop|restart|log|panel"; exit 2 ;;
esac
