package dev.srimi.depthwall.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Read-only observations: a stored AOD preference does not prove a working ambient display. */
data class AodStatus(
    val model: String,
    val device: String,
    val androidVersion: String,
    val api: Int,
    val build: String,
    val alwaysOn: Boolean?,
    val suppressed: Boolean?,
    val colorInversion: Boolean?,
    val batterySaver: Boolean,
    val ambientPermission: Boolean,
    val systemApp: Boolean = false,
    val ambientComponent: Boolean = false,
    val romAodWallpaper: Boolean? = null,
    /** crDroid's `doze_always_on_wallpaper_enabled`; null when it cannot be read. */
    val aodWallpaperSetting: Boolean? = null,
    val canWriteSecure: Boolean = false,
    /** True when the lock screen (which AOD shows) runs the AOD-capable DepthWall component. */
    val lockUsesAmbient: Boolean? = null,
) {
    val isHotdog: Boolean get() = device.equals("hotdog", ignoreCase = true)
    /** Every prerequisite this app can observe; the panel itself still needs checking on the phone. */
    val aodReady: Boolean get() = ambientPermission && ambientComponent && lockUsesAmbient == true &&
        romAodWallpaper != false && alwaysOn != false && aodWallpaperSetting != false

    fun report(): String = buildString {
        appendLine("DepthWall ${dev.srimi.depthwall.BuildConfig.VERSION_NAME} AOD compatibility observations")
        appendLine("Target: OnePlus 7T Pro McLaren 4G (hotdog), crDroid Android 17")
        appendLine("Reported device: $model ($device)")
        appendLine("Android: $androidVersion (API $api)")
        appendLine("Build: $build")
        appendLine("Always-on preference: ${label(alwaysOn)}")
        appendLine("Doze suppression: ${label(suppressed)}")
        appendLine("Colour inversion: ${label(colorInversion)}")
        appendLine("Battery Saver: ${label(batterySaver)}")
        appendLine("Installed as system app: ${if (systemApp) "yes" else "no"}")
        appendLine("Ambient wallpaper permission: ${if (ambientPermission) "granted" else "not granted"}")
        appendLine("AOD wallpaper component: ${if (ambientComponent) "enabled" else "disabled"}")
        appendLine("ROM config_dozeSupportsAodWallpaper: ${label(romAodWallpaper)}")
        appendLine("Secure doze_always_on_wallpaper_enabled: ${label(aodWallpaperSetting)} (writable: ${if (canWriteSecure) "yes" else "no"})")
        appendLine("Lock screen uses DepthWall AOD wallpaper: ${label(lockUsesAmbient)}")
        appendLine("Physical AOD / sleep / wake validation: not established by these observations")
    }

    companion object {
        fun label(value: Boolean?): String = when (value) { true -> "On"; false -> "Off"; null -> "Unknown" }
    }
}

object AodSupport {
    /** Shared with the Magisk/KernelSU module in tools/magisk; keep both in step. */
    const val MODULE_STEPS = """DepthWall AOD module (rooted crDroid)
1. Build: python3 tools/build_aod_module.py app/build/outputs/apk/release/app-release.apk
2. Flash artifacts/DepthWall-AOD-module.zip in Magisk or KernelSU, then reboot. It turns on crDroid's wallpaper-on-AOD setting once.
3. Keep the same signing key for DepthWall updates; a differently signed copy cannot update the system app.
4. In DepthWall: Settings → Always-on display → Apply AOD wallpaper, choose Lock screen or Home and lock screens.
5. Turn on crDroid's Always-on display, then lock the phone."""

    fun read(context: Context): AodStatus {
        // These three keys are @Readable in crDroid 17. A missing/denied value stays unknown.
        fun preference(key: String): Boolean? = try {
            when (Settings.Secure.getString(context.contentResolver, key)) { "1" -> true; "0" -> false; else -> null }
        } catch (_: SecurityException) { null }
        return AodStatus(
            Build.MODEL, Build.DEVICE, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.DISPLAY,
            preference("doze_always_on"), preference("suppress_doze"),
            preference("accessibility_display_inversion_enabled"),
            context.getSystemService(PowerManager::class.java).isPowerSaveMode,
            AmbientWallpaper.isGranted(context),
            AmbientWallpaper.isSystemInstall(context),
            AmbientWallpaper.isAmbientEnabled(context),
            AmbientWallpaper.romSupportsAodWallpaper(),
            AmbientWallpaper.aodWallpaperSetting(context),
            AmbientWallpaper.canWriteSecureSettings(context),
            WallpaperApplyState.capture(context)?.let { state ->
                // Without a separate lock wallpaper, the lock screen shows the home wallpaper.
                val lock = if (state.lockId < 0) state.homeComponent else state.lockComponent
                lock == AmbientWallpaper.ambientComponent(context).flattenToString()
            },
        )
    }

    enum class Destination { AOD, DISPLAY, SETTINGS, UNAVAILABLE }

    fun openSettings(context: Context): Destination {
        // crDroid 17 exports this activity, but does not register an AMBIENT_DISPLAY_SETTINGS action.
        val destinations = listOf(
            Destination.AOD to Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.display.AmbientDisplayAlwaysOnActivity")),
            Destination.DISPLAY to Intent(Settings.ACTION_DISPLAY_SETTINGS),
            Destination.SETTINGS to Intent(Settings.ACTION_SETTINGS),
        )
        for ((destination, intent) in destinations) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return destination
            } catch (_: android.content.ActivityNotFoundException) { /* Try the next public settings screen. */ }
            catch (_: SecurityException) { /* Some ROMs restrict the direct activity. */ }
        }
        return Destination.UNAVAILABLE
    }
}
