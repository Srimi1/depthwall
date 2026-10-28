package dev.srimi.depthwall.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
) {
    val isHotdog: Boolean get() = device.equals("hotdog", ignoreCase = true)

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
        appendLine("Ambient wallpaper permission: ${if (ambientPermission) "granted" else "not granted"}")
        appendLine("Wallpaper ambient integration: not implemented by this ordinary APK")
        appendLine("Physical AOD / sleep / wake validation: not established by these observations")
    }

    companion object {
        fun label(value: Boolean?): String = when (value) { true -> "On"; false -> "Off"; null -> "Unknown" }
    }
}

object AodSupport {
    private const val AMBIENT_PERMISSION = "android.permission.AMBIENT_WALLPAPER"

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
            context.checkSelfPermission(AMBIENT_PERMISSION) == PackageManager.PERMISSION_GRANTED,
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
