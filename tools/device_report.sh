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
"$adb" -s "$serial" shell getprop ro.kernel.qemu > "$destination/emulator-indicator.txt"
"$adb" -s "$serial" shell wm size > "$destination/display-size.txt"
"$adb" -s "$serial" shell dumpsys wallpaper > "$destination/wallpaper.txt"
"$adb" -s "$serial" shell dumpsys meminfo dev.srimi.depthwall > "$destination/memory.txt"
"$adb" -s "$serial" shell dumpsys gfxinfo dev.srimi.depthwall > "$destination/gfxinfo.txt"
"$adb" -s "$serial" shell dumpsys thermalservice > "$destination/thermal.txt"
"$adb" -s "$serial" shell dumpsys battery > "$destination/battery.txt"
echo "Read-only report saved in $destination"
