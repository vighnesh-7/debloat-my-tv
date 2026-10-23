#!/usr/bin/env bash
# Restore script generated on 2026-10-19 for MiTV-AXSO0 (dangal)
# Android 9 (SDK 28)
#
# This script restores all packages to their original backed-up state (all enabled),
# reinstalls any uninstalled user-0 packages via install-existing,
# re-enables the stock launcher, restores the HOME activity, and resets animation scales.
#
# Usage:
#   bash backups/backup_20261019_0025/restore.sh
# or with custom IP:
#   TV_HOST=192.168.0.101 bash backups/backup_20261019_0025/restore.sh

set -euo pipefail

TV_HOST="${TV_HOST:-192.168.0.101}"
TV_PORT="${TV_PORT:-5555}"
STOCK_LAUNCHER="com.google.android.tvlauncher"

echo "=== Restoring MiTV-AXSO0 to original backed-up state ==="
echo "Target TV: ${TV_HOST}:${TV_PORT}"

# Reinstall any packages that were uninstalled for user 0
REINSTALL_PKGS=(
  com.google.android.youtube.tvmusic
  com.amazon.amazonvideo.livingroom
  com.sonyliv
  com.google.android.videos
  com.mitv.gallery
  com.android.gallery3d
)

for pkg in "${REINSTALL_PKGS[@]}"; do
  echo "Restoring uninstalled package $pkg..."
  adb -s "${TV_HOST}:${TV_PORT}" shell cmd package install-existing "$pkg" || true
done

# Re-enable all packages that were originally enabled
DISABLED_BY_US=(
  com.miui.tv.analytics
  com.xiaomi.statistic
  com.mitv.tvhome.atv
  com.xm.webcontent
  com.xiaomi.floatingframe
  com.xiaomi.android.tvsetup.partnercustomizer
  com.xiaomo.tv.milegal
  com.xiaomi.mitv.handbook
  android.autoinstalls.config.xioami.dangal
  com.google.android.tvrecommendations
  com.google.android.leanbacklauncher.recommendations
  com.xiaomi.mitv.tvmanager
  com.xiaomi.mitv.smartshare
  com.mitv.milinkservice
  com.xiaomi.wfdsinkhelperservice
  com.google.android.apps.mediashell
  com.google.android.backdrop
  com.xiaomi.mitv.updateservice
  com.google.android.youtube.tvmusic
  com.sonyliv
  com.google.android.videos
  com.amazon.amazonvideo.livingroom
  com.google.android.play.games
  com.xiaomi.mimusic2
  com.google.android.feedback
  com.google.android.tv.bugreportsender
  com.google.android.tvlauncher
  com.android.tv
)

for pkg in "${DISABLED_BY_US[@]}"; do
  echo "Re-enabling $pkg..."
  adb -s "${TV_HOST}:${TV_PORT}" shell pm enable "$pkg" || true
done

echo "Restoring stock home activity to $STOCK_LAUNCHER..."
adb -s "${TV_HOST}:${TV_PORT}" shell cmd package set-home-activity --user 0 "$STOCK_LAUNCHER/.MainActivity" || true

echo "Restoring animation scales to 1.0..."
adb -s "${TV_HOST}:${TV_PORT}" shell settings put global window_animation_scale 1.0 || true
adb -s "${TV_HOST}:${TV_PORT}" shell settings put global transition_animation_scale 1.0 || true
adb -s "${TV_HOST}:${TV_PORT}" shell settings put global animator_duration_scale 1.0 || true

echo "Restoring screensaver and sleep timeout..."
adb -s "${TV_HOST}:${TV_PORT}" shell settings put secure screensaver_components com.mitv.dream/com.mitv.dream.MiDream || true
adb -s "${TV_HOST}:${TV_PORT}" shell settings put secure sleep_timeout -1 || true
adb -s "${TV_HOST}:${TV_PORT}" shell settings put system screen_off_timeout 2147483647 || true

echo "Switching to stock home UI..."
adb -s "${TV_HOST}:${TV_PORT}" shell am force-stop com.gathin.tvhome || true
adb -s "${TV_HOST}:${TV_PORT}" shell am start -n com.google.android.tvlauncher/.MainActivity || true
sleep 1
adb -s "${TV_HOST}:${TV_PORT}" shell input keyevent 3

echo "=== Verification ==="
echo "Current Home Activity:"
adb -s "${TV_HOST}:${TV_PORT}" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME

echo "Disabled packages remaining:"
adb -s "${TV_HOST}:${TV_PORT}" shell pm list packages -d

echo "Restore complete!"
