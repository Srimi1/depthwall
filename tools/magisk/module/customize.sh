# Sourced by the Magisk or KernelSU module installer.
# The builder records which DepthWall build this module carries (release or .preview).
PKG=$(cat "$MODPATH/depthwall.package" 2>/dev/null || echo dev.srimi.depthwall)

ui_print "- DepthWall AOD: system install of $PKG for Always-On Display"
[ "$API" -lt 31 ] && abort "! Android 12 or newer is required."

# Android cannot update a system app from a copy signed with another key, and it wipes the
# user-installed copy's data when the keys differ. dumpsys prints Signature.hashCode() values.
EXPECTED=$(cat "$MODPATH/depthwall.sighash" 2>/dev/null)
INSTALLED=$(dumpsys package "$PKG" 2>/dev/null | sed -n 's/.*version:[0-9]*, signatures:\[\([0-9a-f, ]*\)\].*/\1/p' | head -n 1)
if [ -n "$INSTALLED" ] && [ -n "$EXPECTED" ]; then
  case ",$(echo "$INSTALLED" | tr -d ' ')," in
    *",$EXPECTED,"*) ui_print "- Installed DepthWall uses the same signing key. Library and settings are kept." ;;
    *) abort "! Installed DepthWall is signed with a different key. Back up, uninstall it, then flash again." ;;
  esac
elif [ -n "$INSTALLED" ]; then
  ui_print "- Could not compare signing keys. Keep using the same key for DepthWall updates."
fi
rm -f "$MODPATH/depthwall.sighash" "$MODPATH/depthwall.package"

set_perm_recursive "$MODPATH/system" 0 0 0755 0644

# Magisk and KernelSU replace the module folder on update. Carry service.sh's "turned on once"
# marker over, so an update does not switch wallpaper-on-AOD back on after the user turned it off.
OLD="/data/adb/modules/${MODID:-depthwall_aod}"
[ -f "$OLD/.aod_wallpaper_initialised" ] && touch "$MODPATH/.aod_wallpaper_initialised"
ui_print "- Reboot, then open DepthWall > Settings > Always-on display."
