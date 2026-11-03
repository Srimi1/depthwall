package dev.srimi.depthwall.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.util.Log
import dev.srimi.depthwall.wallpaper.AmbientDepthWallpaperService
import dev.srimi.depthwall.wallpaper.DepthWallpaperService
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * crDroid keeps a live wallpaper on Always-On Display only when its metadata declares ambient
 * support and the app holds AMBIENT_WALLPAPER (signature|preinstalled). Binding an ambient wallpaper
 * without that permission throws, so the ambient component stays disabled until a system install
 * (the Magisk/KernelSU module) actually receives the permission.
 */
object AmbientWallpaper {
    const val PERMISSION = "android.permission.AMBIENT_WALLPAPER"
    /** SystemUI's usual AOD exit animation when the framework does not supply a duration. */
    const val DEFAULT_EXIT_MS = 600L
    /** Burn-in shift radius in scene units (2.0 = panel height): about 6 px on a 3120 px panel. */
    const val SHIFT_RADIUS = .004f

    fun isGranted(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * Android 17's SystemUI keeps the wallpaper visible through its AOD scrim only when this secure
     * setting, `doze_always_on` and the ROM flag are all on. Hidden settings are readable by system
     * apps; null when this install cannot read it.
     */
    const val AOD_WALLPAPER_SETTING = "doze_always_on_wallpaper_enabled"

    fun aodWallpaperSetting(context: Context): Boolean? = try {
        when (android.provider.Settings.Secure.getString(context.contentResolver, AOD_WALLPAPER_SETTING)) {
            "1" -> true; null, "0" -> false; else -> null
        }
    } catch (_: SecurityException) { null }

    fun canWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Turns crDroid's wallpaper-on-AOD setting on or off; false when the module did not grant access. */
    fun setAodWallpaper(context: Context, enabled: Boolean): Boolean = try {
        canWriteSecureSettings(context) &&
            android.provider.Settings.Secure.putInt(context.contentResolver, AOD_WALLPAPER_SETTING, if (enabled) 1 else 0)
    } catch (_: SecurityException) { false }

    fun isSystemInstall(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0

    const val SYSTEMUI = "com.android.systemui"

    /**
     * Whether SystemUI will show wallpapers on AOD, read the way crDroid 17's `WallpaperRepository`
     * does: SystemUI's `config_dozeSupportsAodWallpaperOverride` (set to 1 only by the module's
     * optional SystemUI overlay) wins when it is 0 or 1; otherwise the framework's
     * `config_dozeSupportsAodWallpaper`, which crDroid's vendor overlay sets to true.
     * Null when neither can be read.
     */
    fun romSupportsAodWallpaper(context: Context): Boolean? =
        effectiveAodWallpaperFlag(systemUiAodWallpaperOverride(context), frameworkAodWallpaperFlag())

    /** SystemUI's rule: 1 is true, 0 is false, anything else (default -1) defers to the framework. */
    fun effectiveAodWallpaperFlag(systemUiOverride: Int?, frameworkFlag: Boolean?): Boolean? = when (systemUiOverride) {
        1 -> true
        0 -> false
        else -> frameworkFlag
    }

    /** Includes SystemUI's enabled overlays; needs SystemUI in the manifest's `<queries>`. */
    private fun systemUiAodWallpaperOverride(context: Context): Int? = runCatching {
        val resources = context.packageManager.getResourcesForApplication(SYSTEMUI)
        @Suppress("DiscouragedApi") // SystemUI's private flag has no public constant.
        val id = resources.getIdentifier("config_dozeSupportsAodWallpaperOverride", "integer", SYSTEMUI)
        if (id == 0) null else resources.getInteger(id)
    }.getOrNull()

    /** The framework's private `config_dozeSupportsAodWallpaper`; null when this build does not define it. */
    private fun frameworkAodWallpaperFlag(): Boolean? = runCatching {
        val system = Resources.getSystem()
        @Suppress("DiscouragedApi") // A private framework flag has no public constant.
        val id = system.getIdentifier("config_dozeSupportsAodWallpaper", "bool", "android")
        if (id == 0) null else system.getBoolean(id)
    }.getOrNull()

    fun ordinaryComponent(context: Context) = ComponentName(context, DepthWallpaperService::class.java)
    fun ambientComponent(context: Context) = ComponentName(context, AmbientDepthWallpaperService::class.java)

    fun isAmbientEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(ambientComponent(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** The component every new apply should use: ambient when it can bind, the ordinary one otherwise. */
    fun component(context: Context): ComponentName =
        if (isGranted(context)) ambientComponent(context) else ordinaryComponent(context)

    fun isOwn(context: Context, component: String?): Boolean = component != null &&
        (component == ordinaryComponent(context).flattenToString() || component == ambientComponent(context).flattenToString())

    /** Called at process start. Disabling also unbinds an ambient wallpaper that lost its permission. */
    fun syncComponent(context: Context) {
        try {
            // DEFAULT falls back to the manifest's enabled="false", so a normal install writes nothing.
            val wanted = if (isGranted(context)) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            val component = ambientComponent(context)
            if (context.packageManager.getComponentEnabledSetting(component) != wanted)
                context.packageManager.setComponentEnabledSetting(component, wanted, PackageManager.DONT_KILL_APP)
        } catch (error: RuntimeException) { Log.w("DepthWall", "Ambient component", error) }
    }

    /** One of eight points on a small circle, advancing each time the panel enters AOD. */
    fun burnInShift(entry: Int, radius: Float = SHIFT_RADIUS): FloatArray {
        val angle = Math.floorMod(entry, 8) * PI / 4
        return floatArrayOf((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat())
    }

    /** AOD pose weight while waking: 1 at the moment the panel leaves AOD, eased to 0. */
    fun exitLevel(elapsedMs: Long, durationMs: Long): Float {
        if (elapsedMs < 0) return 1f
        val t = (elapsedMs.toFloat() / durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        return 1f - t * t * (3f - 2f * t)
    }
}
