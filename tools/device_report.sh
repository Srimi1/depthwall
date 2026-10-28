#!/bin/sh
# Read-only device report; explicitly select a device to avoid mixing emulator/phone evidence.
set -eu
: "${ANDROID_HOME:=$HOME/Library/Android/sdk}"
if [ "$#" -ne 2 ]; then echo 'Usage: tools/device_report.sh SERIAL OUTPUT_DIRECTORY' >&2; exit 2; fi
serial=$1
destination=$2
mkdir -p "$destination"
adb="$ANDROID_HOME/platform-tools/adb"
"$adb" -s "$serial" shell getprop ro.product.model > "$destination/model.txt"
"$adb" -s "$serial" shell getprop ro.product.device > "$destination/device.txt"
"$adb" -s "$serial" shell getprop ro.build.version.release > "$destination/android.txt"
"$adb" -s "$serial" shell getprop ro.build.version.sdk > "$destination/sdk.txt"
"$adb" -s "$serial" shell getprop ro.product.cpu.abilist > "$destination/abis.txt"
"$adb" -s "$serial" shell getprop ro.crdroid.version > "$destination/crdroid.txt"
"$adb" -s "$serial" shell getprop ro.build.display.id > "$destination/build-id.txt"
"$adb" -s "$serial" shell getprop ro.build.date.utc > "$destination/build-date.txt"
"$adb" -s "$serial" shell getprop ro.kernel.qemu > "$destination/emulator-indicator.txt"
"$adb" -s "$serial" shell wm size > "$destination/display-size.txt"
"$adb" -s "$serial" shell dumpsys wallpaper > "$destination/wallpaper.txt"
"$adb" -s "$serial" shell dumpsys meminfo dev.srimi.depthwall > "$destination/memory.txt"
"$adb" -s "$serial" shell dumpsys gfxinfo dev.srimi.depthwall > "$destination/gfxinfo.txt"
"$adb" -s "$serial" shell dumpsys thermalservice > "$destination/thermal.txt"
"$adb" -s "$serial" shell dumpsys battery > "$destination/battery.txt"
# Keep these observations separate from a claim that the phone actually displayed AOD.
"$adb" -s "$serial" shell dumpsys display > "$destination/display-state.txt"
"$adb" -s "$serial" shell dumpsys power > "$destination/power-state.txt"
"$adb" -s "$serial" shell dumpsys activity service dev.srimi.depthwall/.wallpaper.DepthWallpaperService > "$destination/wallpaper-engine.txt" 2>&1 || true
{
    for key in doze_always_on suppress_doze accessibility_display_inversion_enabled doze_always_on_auto_mode doze_always_on_auto_time doze_on_charge; do
        printf '%s: ' "$key"
        "$adb" -s "$serial" shell settings get secure "$key" || printf 'unavailable\n'
    done
} > "$destination/aod-preferences.txt" 2>&1
# Some ROMs omit overlay lookup. Retain its error as unknown instead of assuming support.
"$adb" -s "$serial" shell cmd overlay lookup --verbose android android:bool/config_dozeAlwaysOnDisplayAvailable > "$destination/aod-availability.txt" 2>&1 || true
"$adb" -s "$serial" shell cmd overlay lookup --verbose android android:bool/config_dozeSupportsAodWallpaper > "$destination/aod-wallpaper-overlay.txt" 2>&1 || true
echo "Read-only report saved in $destination"
