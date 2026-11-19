#!/usr/bin/env bash
# Regenerates the images in docs/ from a running emulator.
#
# Needs: the Medium_Phone_API_37.0 emulator (1080 x 2400), a debug build installed,
# and one I-5 widget at its default 3 x 2 size in the top left of the second home page.
# Crop boxes below assume that layout.
set -euo pipefail

cd "$(dirname "$0")/.."
SDK="${SDK:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
ADB="${ADB:-$SDK/platform-tools/adb}"
JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
JAVA="$JAVA_HOME/bin/java"
OUT=docs
TMP="$(mktemp -d)"
PKG=com.andrewmacheret.i5
mkdir -p "$OUT"
"$JAVA" scripts/Icon.java "$OUT/app-icon.png"
"$ADB" shell svc power stayon true
"$ADB" shell input keyevent KEYCODE_WAKEUP
"$ADB" shell wm dismiss-keyguard

scenario() {
  "$ADB" shell am broadcast -n "$PKG/.DebugReceiver" --es scenario "$1" > /dev/null
  sleep 2
}

shot() {
  "$ADB" exec-out screencap -p > "$TMP/$1.png"
}

stitch() {
  "$JAVA" scripts/Stitch.java "$@"
}

# The widget lives on the second home page
home() {
  "$ADB" shell input keyevent KEYCODE_HOME
  sleep 1
  "$ADB" shell input keyevent KEYCODE_HOME
  sleep 1
  "$ADB" shell input swipe 900 1500 150 1500 200
  sleep 2
}

app() {
  "$ADB" shell am force-stop "$PKG"
  "$ADB" shell am start -n "$PKG/.ui.MainActivity" > /dev/null
  # Debug builds of Compose start slowly on the emulator
  sleep 8
}

scroll() {
  "$ADB" shell input swipe 540 1900 540 "$1" 600
  sleep 1
}

# Widget states on a real home screen
home
for s in north south closed closure checking holiday notice stale; do
  scenario "$s"
  shot "home-$s"
done
stitch "$OUT/widget-states.png" 2 0.5 20,60,786,620 \
  "$TMP/home-north.png" "$TMP/home-south.png" \
  "$TMP/home-closed.png" "$TMP/home-closure.png" \
  "$TMP/home-checking.png" "$TMP/home-holiday.png"
stitch "$OUT/widget-warnings.png" 2 0.5 20,60,786,620 \
  "$TMP/home-notice.png" "$TMP/home-stale.png"
stitch "$OUT/home.png" 1 0.4 full "$TMP/home-north.png"

# Widget sizes, drawn by the debug gallery from the real widget code
scenario north
"$ADB" shell am force-stop "$PKG"
"$ADB" shell am start -n "$PKG/.WidgetGalleryActivity" > /dev/null
# Each size is drawn in turn, which is slow in debug builds
sleep 30
shot gallery
stitch "$OUT/widget-sizes.png" 1 0.5 0,110,1080,1760 "$TMP/gallery.png"

# Detail screen
scenario north
app
shot app-north
scroll 700
shot app-north-2
# Slow, so it doesn't fling, and stops with all of Settings in view
"$ADB" shell input swipe 540 2150 540 260 2000
sleep 1
shot app-north-3
scenario holiday
app
shot app-holiday
stitch "$OUT/app.png" 2 0.4 full "$TMP/app-north.png" "$TMP/app-holiday.png"
stitch "$OUT/app-more.png" 2 0.4 full "$TMP/app-north-2.png" "$TMP/app-north-3.png"

# Dark mode
"$ADB" shell cmd uimode night yes > /dev/null
sleep 2
scenario evening
app
shot dark-app
"$ADB" shell am force-stop "$PKG"
"$ADB" shell am start -n "$PKG/.WidgetGalleryActivity" > /dev/null
sleep 30
shot dark-gallery
"$ADB" shell cmd uimode night no > /dev/null
stitch "$OUT/dark-mode.png" 2 0.4 full "$TMP/dark-app.png" "$TMP/dark-gallery.png"

# Notifications
"$ADB" shell input keyevent KEYCODE_HOME
for n in change cantconfirm; do
  "$ADB" shell am broadcast -n "$PKG/.DebugReceiver" --es notify "$n" > /dev/null
  sleep 1
  "$ADB" shell cmd statusbar expand-notifications
  sleep 2
  shot "notify-$n"
  "$ADB" shell cmd statusbar collapse
  sleep 1
done
"$ADB" shell cmd notification cancel_all > /dev/null 2>&1 || true
stitch "$OUT/notifications.png" 1 0.5 30,555,1020,290 "$TMP/notify-change.png" "$TMP/notify-cantconfirm.png"

scenario live
rm -rf "$TMP"
echo "Wrote images to $OUT/"
