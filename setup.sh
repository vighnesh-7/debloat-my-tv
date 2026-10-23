#!/usr/bin/env bash
# setup.sh — one-step install of the current tvOS-style home on an Android TV.
#
# New TV, same Wi-Fi as this computer:
#
#   TV_HOST=192.168.0.101 bash <(curl -fsSL https://raw.githubusercontent.com/Gathin23/tv-vibehack/main/setup.sh)
#
# Or from a clone of this repo:
#
#   TV_HOST=192.168.0.101 ./setup.sh
#
# Replicates the live setup: Outfit 600 launcher, dock, glass hover, stats, USB
# pills, screensaver, Inputs order, Wi-Fi glyph, debloat, HOME, sleep off.
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/Gathin23/tv-vibehack.git}"
TV_HOST="${TV_HOST:-}"
TV_PORT="${TV_PORT:-5555}"

if [ -z "$TV_HOST" ]; then
  echo "Set TV_HOST to the TV IP, e.g.  TV_HOST=192.168.0.101 $0"
  exit 1
fi
export TV_HOST TV_PORT

# When piped from curl, BASH_SOURCE is a /dev/fd path — clone then re-exec.
SRC="${BASH_SOURCE[0]:-}"
if [ -z "$SRC" ] || [ ! -f "$SRC" ] || [[ "$SRC" == /dev/fd/* ]]; then
  WORK="${TMPDIR:-/tmp}/tv-vibehack-setup"
  rm -rf "$WORK"
  git clone --depth 1 "$REPO_URL" "$WORK"
  exec bash "$WORK/setup.sh"
fi

ROOT="$(cd "$(dirname "$SRC")" && pwd)"
cd "$ROOT"

echo "=== tv-vibehack setup ==="
echo "Repo: $ROOT"
echo "TV:   ${TV_HOST}:${TV_PORT}"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$JAVA_HOME/bin:${ANDROID_HOME:-}/cmdline-tools/latest/bin:${PATH:-}"

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "Need JDK 17. On macOS: brew install openjdk@17"
  exit 1
fi
if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "$ANDROID_HOME" ]; then
  echo "Need ANDROID_HOME (platform 28+/34, build-tools). On macOS: brew install android-commandlinetools"
  exit 1
fi

echo "[1/6] Python ADB client"
python3 -m venv tools/venv
tools/venv/bin/pip install -q -r tools/requirements.txt
PY=tools/venv/bin/python
"$PY" -I tools/tv.py --probe

echo "[2/6] Build launcher"
cd "$ROOT/launcher"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug -q --console=plain
cd "$ROOT"
APK="$ROOT/launcher/app/build/outputs/apk/debug/app-debug.apk"
test -f "$APK"

echo "[3/6] Install launcher and set HOME"
"$PY" -I tools/b64push.py "$APK" /data/local/tmp/tvhome.apk
{
  echo "pm install -r /data/local/tmp/tvhome.apk"
  echo "rm -f /data/local/tmp/tvhome.apk"
  echo "pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION 2>/dev/null; echo location-granted"
  echo "cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity"
  echo "pm disable-user --user 0 com.google.android.tvlauncher"
  echo "pm enable --user 0 com.xiaomi.floatingframe"
  echo "am startservice -a com.xiaomi.action.FloatingFrame_Service -n com.xiaomi.floatingframe/.FloatingFrameService"
} | "$PY" -I tools/tv.py --batch

echo "[4/6] Debloat (disable-user; Live TV / HDMI / Play / keyboard / BT / Assistant / Cast / updates kept)"
DEBLOAT="$ROOT/examples/xiaomi-mitv-axso0/debloat.txt"
if [ -f "$DEBLOAT" ]; then
  bash "$ROOT/tools/disable.sh" "$DEBLOAT" || true
fi
{
  echo "am force-stop com.mitv.tvhome.atv"
  echo "am force-stop com.xm.webcontent"
  echo "pm clear com.mitv.tvhome.atv"
  echo "pm clear com.xm.webcontent"
  echo "pm uninstall --user 0 com.mitv.tvhome.atv"
  echo "pm uninstall com.mitv.tvhome.atv"
  echo "pm uninstall --user 0 com.xm.webcontent"
  echo "pm uninstall com.xm.webcontent"
  echo "pm disable-user --user 0 com.mitv.tvhome.atv"
  echo "pm disable-user --user 0 com.xm.webcontent"
  echo "pm uninstall --user 0 com.sonyliv"
  echo "rm -f /data/local/tmp/*.apk /sdcard/*.apk /sdcard/Download/*.apk /cache/*.apk"
  echo "pm enable --user 0 com.xiaomi.floatingframe"
  echo "pm enable --user 0 com.android.tv"
  echo "pm enable --user 0 com.droidlogic.tvinput"
  echo "pm enable --user 0 com.droidlogic.droidlivetv"
  echo "pm enable --user 0 com.google.android.apps.mediashell"
  echo "pm enable --user 0 com.xiaomi.mitv.updateservice"
} | "$PY" -I tools/tv.py --batch || true

echo "[5/6] Timeouts, screensaver, PatchWall off"
{
  echo "settings put system screen_off_timeout 2147483647"
  echo "settings put secure sleep_timeout -1"
  echo "settings put global tv_device_sleep_time 0"
  echo "settings put secure screensaver_enabled 1"
  echo "settings put secure screensaver_timeout 300000"
  echo "settings put secure screensaver_components com.gathin.tvhome/.TvDreamService"
  echo "settings put secure screensaver_activate_on_sleep 1"
  echo "settings put secure screensaver_activate_on_dock 1"
  echo "settings put global patchwall_enable 0"
  echo "settings put global set_patchwall_default 0"
  echo "cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity"
  echo "am start -n com.gathin.tvhome/.MainActivity"
  echo "input keyevent 3"
} | "$PY" -I tools/tv.py --batch

echo "[6/6] Verify"
"$PY" -I tools/tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
echo "=== done ==="
echo "Home is com.gathin.tvhome. Sleep is off. Screensaver after 5 minutes idle."
echo "Dock, Outfit, glass hover, stats, USB pills and Inputs live in the installed APK."
