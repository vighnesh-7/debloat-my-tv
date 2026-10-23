#!/usr/bin/env bash
# Build the launcher APK and install it on the TV.
# By default the stock launcher stays enabled (safer). Pass SET_HOME=1 to switch HOME
# after you have verified the custom launcher, and DISABLE_STOCK=1 only after that.
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
STOCK_LAUNCHER="${STOCK_LAUNCHER:-com.google.android.tvlauncher}"
SET_HOME="${SET_HOME:-0}"
DISABLE_STOCK="${DISABLE_STOCK:-0}"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$JAVA_HOME/bin:$PATH"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug -q --console=plain
PY=../tools/venv/bin/python
APK=app/build/outputs/apk/debug/app-debug.apk
"$PY" -I ../tools/b64push.py "$APK" /data/local/tmp/tvhome.apk
CMDS=(
  "pm install -r /data/local/tmp/tvhome.apk"
  "rm -f /data/local/tmp/tvhome.apk"
  "pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION 2>/dev/null; echo location-granted"
)
if [ "$SET_HOME" = "1" ]; then
  CMDS+=("cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity")
fi
if [ "$DISABLE_STOCK" = "1" ]; then
  CMDS+=("pm disable-user --user 0 $STOCK_LAUNCHER")
fi
printf '%s\n' "${CMDS[@]}" | "$PY" -I ../tools/tv.py --batch
echo "Installed com.gathin.tvhome. SET_HOME=$SET_HOME DISABLE_STOCK=$DISABLE_STOCK"
