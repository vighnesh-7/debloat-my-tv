#!/usr/bin/env bash
# ==============================================================================
# vignesh.sh - One-Click Play Setup Script for Custom MiTV tvOS Launcher
# ==============================================================================
# This script sets up and mirrors the complete custom implementation:
#  - Builds and installs the custom Apple TV-style fluid launcher (com.gathin.tvhome)
#  - Configures dock apps (YouTube, Netflix, Hotstar, FX File Explorer)
#  - Physics-driven spring animations and specular glass reflection highlight
#  - Typography font weight set to 800 ('wght' 800 bold) for Greeting, Clock, AM/PM, Date
#  - Backgroundless status display showing RAM % and available TV Memory (Storage)
#  - Input switcher with passthrough channel handling for HDMI 1, HDMI 2, HDMI 3, AV, and Live TV
#  - Dynamic Wi-Fi connection detection pill with connection feedback
#  - Long-press dock context menu (Move Left, Move Right, Remove from Dock)
#  - Custom Screensaver (TvDreamService) with live clock, greeting, date and stats
#  - 10-minute sleep timeout
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

TV_HOST="${TV_HOST:-192.168.0.101}"
TV_PORT="${TV_PORT:-5555}"
DEVICE="${TV_HOST}:${TV_PORT}"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$JAVA_HOME/bin:$PATH"

echo "========================================================"
echo " Starting Setup: Custom MiTV tvOS Implementation"
echo " Target Device: $DEVICE"
echo "========================================================"

# 1. Connect to TV over ADB
echo "[1/7] Connecting to TV..."
adb connect "$DEVICE" || true
adb -s "$DEVICE" wait-for-device

# 2. Build Launcher APK
echo "[2/7] Building launcher APK with Gradle..."
cd "$SCRIPT_DIR/launcher"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug -q --console=plain
cd "$SCRIPT_DIR"

APK="$SCRIPT_DIR/launcher/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
    echo "Error: APK not found at $APK"
    exit 1
fi

# 3. Install Launcher APK on TV
echo "[3/7] Installing com.gathin.tvhome onto TV..."
adb -s "$DEVICE" push "$APK" /data/local/tmp/tvhome.apk
adb -s "$DEVICE" shell pm install -r /data/local/tmp/tvhome.apk
adb -s "$DEVICE" shell rm -f /data/local/tmp/tvhome.apk
adb -s "$DEVICE" shell pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true

# 4. Set Custom Launcher as Default Home
echo "[4/7] Setting com.gathin.tvhome as default Home activity..."
adb -s "$DEVICE" shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
adb -s "$DEVICE" shell pm disable-user --user 0 com.google.android.tvlauncher || true

# 5. Apply Bloatware Disable List (preserving TV/HDMI hardware services)
echo "[5/7] Applying optimized debloat configuration..."
DEBLOAT_PKGS=(
  com.miui.tv.analytics
  com.xiaomi.statistic
  com.mitv.tvhome.atv
  com.xm.webcontent
  com.xiaomi.android.tvsetup.partnercustomizer
  com.xiaomo.tv.milegal
  com.xiaomi.mitv.handbook
  android.autoinstalls.config.xioami.dangal
  com.google.android.tvrecommendations
  com.google.android.leanbacklauncher.recommendations
  com.xiaomi.mitv.smartshare
  com.mitv.milinkservice
  com.xiaomi.wfdsinkhelperservice
  com.google.android.apps.mediashell
  com.google.android.backdrop
  com.xiaomi.mitv.updateservice
  com.xiaomi.mimusic2
  com.google.android.feedback
  com.google.android.tv.bugreportsender
  com.google.android.play.games
)

for pkg in "${DEBLOAT_PKGS[@]}"; do
  adb -s "$DEVICE" shell pm disable-user --user 0 "$pkg" 2>/dev/null || true
done

# Ensure critical TV and HDMI video surface packages remain enabled
CRITICAL_TV_PKGS=(
  com.xiaomi.mitv.tvmanager
  com.xiaomi.floatingframe
  com.android.tv
  com.droidlogic.tvinput
  com.droidlogic.droidlivetv
)
for pkg in "${CRITICAL_TV_PKGS[@]}"; do
  adb -s "$DEVICE" shell pm enable "$pkg" 2>/dev/null || true
done

# 6. Configure System Settings (Sleep timeout, Animations, Screensaver)
echo "[6/7] Configuring system preferences..."
# 10 minute sleep timeout (600,000 ms)
adb -s "$DEVICE" shell settings put system screen_off_timeout 600000
adb -s "$DEVICE" shell settings put secure sleep_timeout 600000

# Fluid, snappy animation scales
adb -s "$DEVICE" shell settings put global window_animation_scale 0.5
adb -s "$DEVICE" shell settings put global transition_animation_scale 0.5
adb -s "$DEVICE" shell settings put global animator_duration_scale 0.5

# Register custom TvDreamService screensaver
adb -s "$DEVICE" shell settings put secure screensaver_components com.gathin.tvhome/.TvDreamService
adb -s "$DEVICE" shell settings put secure screensaver_enabled 1
adb -s "$DEVICE" shell settings put secure screensaver_activate_on_sleep 1
adb -s "$DEVICE" shell settings put secure screensaver_activate_on_dock 1

# 7. Start Launcher and Verify
echo "[7/7] Launching custom Home activity..."
adb -s "$DEVICE" shell am start -n com.gathin.tvhome/.MainActivity
sleep 1
adb -s "$DEVICE" shell input keyevent 3

echo "========================================================"
echo " Setup complete! The TV is now running the custom UI:"
echo "  - Launcher: com.gathin.tvhome/.MainActivity"
echo "  - Font weight: 800 bold"
echo "  - Stats: Clean RAM & Memory (available storage) overlay"
echo "  - Dock apps: YouTube, Netflix, Hotstar, FX"
echo "  - Sleep timeout: 10 minutes"
echo "  - Screensaver: com.gathin.tvhome/.TvDreamService"
echo "========================================================"
