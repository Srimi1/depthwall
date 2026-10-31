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
# Reboot evidence. Android resets a live wallpaper whose service dies twice within 10 s or
# reconnects too slowly, and shows its default image until first unlock for services that are
# not direct-boot aware. Capture right after reboot + unlock to see which of these happened.
"$adb" -s "$serial" logcat -d -b main,system 2>&1 | grep -E 'WallpaperManagerService|Reverting to built-in|reconnect timed out|direct boot aware|Wallpaper service gone|exitReason|due to lmk|Rebind fail|DepthWall' > "$destination/wallpaper-log.txt" || true
"$adb" -s "$serial" logcat -d -b events 2>&1 | grep -E 'wp_wallpaper_crashed|am_proc_died|am_crash|am_kill' > "$destination/wallpaper-events.txt" || true
"$adb" -s "$serial" logcat -d -b crash > "$destination/crash-buffer.txt" 2>&1 || true
"$adb" -s "$serial" shell ps -A -o PID,STIME,NAME 2>&1 | grep -E 'miwallpaper|depthwall' > "$destination/wallpaper-processes.txt" || true
"$adb" -s "$serial" shell settings get global boot_count > "$destination/boot-count.txt" 2>&1 || true
for package in $("$adb" -s "$serial" shell pm list packages com.miui.miwallpaper | tr -d '\r' | sed 's/^package://'); do
    "$adb" -s "$serial" shell dumpsys activity exit-info "$package" > "$destination/exit-info-$package.txt" 2>&1 || true
    "$adb" -s "$serial" shell dumpsys package "$package" 2>&1 | grep -iE 'versionName|DIRECT_BOOT|codePath|pkgFlags|privateFlags' > "$destination/package-$package.txt" || true
done
echo "Read-only report saved in $destination"
