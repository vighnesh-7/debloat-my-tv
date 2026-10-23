#!/usr/bin/env bash
# Restore MiTV-AXSO0 (dangal, Android 9) to the exact package and settings
# state captured in this backup folder before any debloat / launcher switch.
#
# Every package that was enabled at backup time is re-enabled.
# Packages that were already disabled (none at backup time) stay disabled.
# Stock HOME, sleep timeout, screensaver, IME and animation scales are restored.
#
# Usage (from the repo root, same Wi-Fi as the TV):
#   TV_HOST=192.168.0.101 bash backups/backup_20261019_2318/restore.sh
#
# Uses tools/tv.py (Python ADB) because the stock adb binary is blocked on macOS.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BACKUP="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

TV_HOST="${TV_HOST:-192.168.0.101}"
TV_PORT="${TV_PORT:-5555}"
export TV_HOST TV_PORT

PY="$ROOT/tools/venv/bin/python"
STOCK_LAUNCHER="com.google.android.tvlauncher"
STOCK_HOME="$STOCK_LAUNCHER/.MainActivity"

echo "=== Restoring MiTV-AXSO0 from $BACKUP ==="
echo "Target: ${TV_HOST}:${TV_PORT}"

# Reinstall user-0 packages that were uninstalled (system image still has them).
{
  echo "echo === reinstall user-0 packages ==="
  echo "cmd package install-existing com.google.android.youtube.tvmusic"
  echo "cmd package install-existing com.amazon.amazonvideo.livingroom"
  echo "echo skipping com.sonyliv — permanently removed by request"
  echo "cmd package install-existing com.google.android.videos"
  echo "cmd package install-existing com.mitv.gallery"
  echo "cmd package install-existing com.android.gallery3d"
  echo "echo === re-enable packages that were originally enabled ==="
  awk -F: 'NF==2 {print "pm enable --user 0 " $2}' "$BACKUP/packages_enabled.txt"
  echo "echo === restore stock home ==="
  echo "cmd package set-home-activity --user 0 $STOCK_HOME"
  echo "pm enable --user 0 $STOCK_LAUNCHER"
  echo "echo === restore settings ==="
  echo "settings put system screen_off_timeout 2147483647"
  echo "settings put secure sleep_timeout -1"
  echo "settings put secure screensaver_components com.mitv.dream/com.mitv.dream.MiDream"
  echo "settings put secure screensaver_enabled 1"
  echo "settings put secure screensaver_activate_on_sleep 1"
  echo "settings put secure screensaver_activate_on_dock 1"
  echo "settings put global window_animation_scale 1.0"
  echo "settings put global transition_animation_scale 1.0"
  echo "settings put global animator_duration_scale 1.0"
  echo "settings put secure default_input_method com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
  echo "am force-stop com.gathin.tvhome"
  echo "am start -n $STOCK_HOME"
  echo "input keyevent 3"
  echo "echo === verify home ==="
  echo "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
  echo "echo === currently disabled ==="
  echo "pm list packages -d"
} | "$PY" -I "$ROOT/tools/tv.py" --batch

echo "=== Restore complete ==="
echo "Stock launcher should be HOME again. If the TV is blank, press Home on the remote."
