#!/bin/sh
# Read-only root-hiding and Play Integrity report for one phone, written to OUTPUT_DIRECTORY.
# It shows whether a DepthWall overlay (or any /data/adb file) is mapped into apps that check for
# root, and records the boot, ROM, Magisk and app state those apps and Play Integrity look at.
# Nothing on the phone is changed. The keybox is only checked for presence, never read.
# Root checks use `su -c`; Magisk asks once to allow root for "Shell". Without root they are skipped.
set -eu
: "${ANDROID_HOME:=$HOME/Library/Android/sdk}"
if [ "$#" -ne 2 ]; then echo 'Usage: tools/integrity_report.sh SERIAL OUTPUT_DIRECTORY' >&2; exit 2; fi
serial=$1
destination=$2
mkdir -p "$destination"
adb="$ANDROID_HOME/platform-tools/adb"
APPS="org.digiyatra.org com.anthropic.claude com.digilocker.android com.google.android.gms com.android.vending"

device() { "$adb" -s "$serial" shell "$1" 2>&1 | tr -d '\r' || true; }
# Arguments must not contain double quotes.
root() { "$adb" -s "$serial" shell "su -c \"$1\"" 2>&1 | tr -d '\r' || true; }

{
    for key in ro.product.model ro.product.device ro.build.fingerprint ro.build.display.id ro.crdroid.version \
        ro.build.date ro.build.type ro.build.tags ro.debuggable ro.secure ro.adb.secure \
        ro.boot.verifiedbootstate ro.boot.flash.locked ro.boot.vbmeta.device_state \
        ro.build.version.release ro.build.version.sdk ro.build.version.security_patch ro.vendor.build.security_patch; do
        printf '%s: %s\n' "$key" "$(device "getprop $key")"
    done
    printf 'selinux: %s\n' "$(device getenforce)"
} > "$destination/boot-and-build.txt"

{
    for key in development_settings_enabled adb_enabled adb_wifi_enabled private_dns_mode private_dns_specifier; do
        printf '%s: %s\n' "$key" "$(device "settings get global $key")"
    done
    printf 'accessibility services: %s\n' "$(device 'settings get secure enabled_accessibility_services')"
    echo '--- VPN networks'
    device 'dumpsys connectivity' | grep -iE 'VPN|IKEv2|WireGuard' | head -n 20 || true
} > "$destination/environment.txt"

{
    for package in $APPS; do
        echo "=== $package"
        device "pm path $package"
        device "dumpsys package $package" | grep -E 'versionName|installerPackageName|installInitiatingPackageName|codePath|lastUpdateTime' | head -n 8 || true
    done
} > "$destination/packages.txt"

{
    echo '--- DepthWall overlays targeting android (must be empty: zygote would load them into every app)'
    device 'cmd overlay list android' | grep -i depthwall || true
    echo '--- DepthWall overlays targeting SystemUI (1.5.1 module)'
    device 'cmd overlay list com.android.systemui' | grep -i depthwall || true
    echo '--- SystemUI AOD wallpaper override (1 from the module)'
    device 'cmd overlay lookup com.android.systemui com.android.systemui:integer/config_dozeSupportsAodWallpaperOverride'
    echo '--- framework AOD wallpaper flag'
    device 'cmd overlay lookup android android:bool/config_dozeSupportsAodWallpaper'
} > "$destination/overlays.txt"

{
    echo '--- root'
    root 'magisk -v; magisk -V'
    echo '--- modules (disable/remove markers listed under each)'
    root 'for m in /data/adb/modules/*; do echo \$m; ls \$m | grep -E disable\|remove\|skip_mount; done'
    echo '--- denylist'
    root 'magisk --denylist status; magisk --denylist ls'
    echo '--- tricky store (file names only; keybox contents are not read)'
    root 'ls /data/adb/tricky_store'
    root 'test -f /data/adb/tricky_store/keybox.xml && echo keybox.xml present || echo no keybox.xml'
} > "$destination/root.txt"

# The decisive check. A line here means the app's process can see a module file, whatever the
# DenyList says. zygote64 shows what every new app inherits.
{
    for process in zygote64 zygote $APPS; do
        pid=$(device "pidof $process" | awk '{print $1}')
        echo "=== $process (pid ${pid:-not running})"
        [ -n "$pid" ] || continue
        root "grep -iE 'depthwall|/data/adb|magisk' /proc/$pid/maps | sort -u"
        root "ls -l /proc/$pid/fd | grep -iE 'depthwall|/data/adb|magisk'"
    done
} > "$destination/app-mappings.txt"

echo "Read-only report saved in $destination. Open DigiYatra and Claude before running it so their processes are checked."
