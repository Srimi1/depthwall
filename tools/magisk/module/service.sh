#!/system/bin/sh
# Runs as root after boot (Magisk and KernelSU). Android 17's SystemUI shows the wallpaper on AOD
# only while doze_always_on_wallpaper_enabled is 1. Let DepthWall manage that setting itself, and
# turn it on once at first boot; afterwards DepthWall's switch decides.
MODDIR=${0%/*}

until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done

for pkg in dev.srimi.depthwall dev.srimi.depthwall.preview; do
  pm path "$pkg" >/dev/null 2>&1 && pm grant "$pkg" android.permission.WRITE_SECURE_SETTINGS 2>/dev/null
done

if [ ! -f "$MODDIR/.aod_wallpaper_initialised" ]; then
  settings put secure doze_always_on_wallpaper_enabled 1
  touch "$MODDIR/.aod_wallpaper_initialised"
fi
