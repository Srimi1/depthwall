package dev.srimi.depthwall.data

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import dev.srimi.depthwall.standby.StandByDreamService
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * StandBy starts through Android's screen saver. crDroid 17's PowerManagerService starts the chosen
 * screen saver when the screen times out while the phone charges, if "Use screen saver" is on and
 * "When to start" includes "While charging" (`shouldNapAtBedTimeLocked`, `canDreamLocked`). The
 * power button still just sleeps the phone. "While charging" is off by default and only a holder of
 * WRITE_SECURE_SETTINGS may switch it for the user: the AOD module grants that, or once over adb.
 * The screen saver itself shows StandBy only while the phone is on its side ([StandByDreamService]).
 */
object StandByAutoStart {
    const val ENABLED = "screensaver_enabled"
    const val COMPONENTS = "screensaver_components"
    const val ON_CHARGE = "screensaver_activate_on_sleep"
    private const val BACKUP = "standby_screensaver_backup"
    private const val SAVED = "saved"
    private val KEYS = listOf(ENABLED, COMPONENTS, ON_CHARGE)

    fun component(context: Context) = ComponentName(context, StandByDreamService::class.java)

    fun canSwitch(context: Context): Boolean = AmbientWallpaper.canWriteSecureSettings(context)

    /** One-time grant for a phone without the AOD module, run from a computer. */
    fun adbGrant(context: Context): String = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    /** True when Android will start DepthWall StandBy on the charger; null when the settings cannot be read. */
    fun isOn(context: Context): Boolean? = try {
        val resolver = context.contentResolver
        val ours = component(context)
        StandByPolicy.screenSaverStartsStandBy(Settings.Secure.getString(resolver, ENABLED),
            Settings.Secure.getString(resolver, COMPONENTS), Settings.Secure.getString(resolver, ON_CHARGE),
            ours.packageName, ours.className)
    } catch (_: SecurityException) { null }

    /** Makes DepthWall StandBy the screen saver while charging, remembering what it replaces. */
    fun turnOn(context: Context): Boolean {
        if (!canSwitch(context)) return false
        return try {
            val resolver = context.contentResolver
            // Whatever is set now is what gets replaced, so a choice made later in Android is what comes back.
            if (isOn(context) != true) {
                val editor = context.getSharedPreferences(BACKUP, Context.MODE_PRIVATE).edit().clear()
                KEYS.forEach { editor.putString(it, Settings.Secure.getString(resolver, it)) }
                editor.putBoolean(SAVED, true).apply()
            }
            Settings.Secure.putString(resolver, COMPONENTS, component(context).flattenToString()) &&
                Settings.Secure.putInt(resolver, ENABLED, 1) &&
                Settings.Secure.putInt(resolver, ON_CHARGE, 1)
        } catch (error: SecurityException) { Log.w("DepthWall", "StandBy screen saver", error); false }
    }

    /**
     * Puts back the screen saver DepthWall replaced, if StandBy is still the one Android starts.
     * A StandBy screen saver set up by hand (no backup) is left alone: only the on-its-side rule lifts.
     */
    fun turnOff(context: Context): Boolean {
        val backup = context.getSharedPreferences(BACKUP, Context.MODE_PRIVATE)
        if (!backup.getBoolean(SAVED, false)) return true
        // Another screen saver chosen in Android since then: nothing of DepthWall's to undo.
        if (isOn(context) != true) { backup.edit().clear().apply(); return true }
        if (!canSwitch(context)) return false
        return try {
            val resolver = context.contentResolver
            val restored = KEYS.all { Settings.Secure.putString(resolver, it, backup.getString(it, null)) }
            backup.edit().clear().apply()
            restored
        } catch (error: SecurityException) { Log.w("DepthWall", "StandBy screen saver", error); false }
    }

    /**
     * StandBy is on by default: the first time DepthWall may switch the screen saver, it does so
     * once. Afterwards only the StandBy setting changes it, so a later choice in Android stands.
     * Called at process start and whenever the app comes to the front, because a `pm grant` (from
     * the module after boot, or over adb) does not restart a process that is already running.
     */
    suspend fun applyDefault(context: Context, store: SettingsStore) {
        // The side-by-side preview build must not take the screen saver from the real app.
        if (context.packageName.endsWith(".preview") || !canSwitch(context)) return
        try {
            if (store.standByAutoDefaultApplied.first()) return
            if (store.standBy.first().autoStart && isOn(context) != true && !turnOn(context)) return
            store.markStandByAutoDefaultApplied()
        } catch (error: IOException) { Log.w("DepthWall", "StandBy default", error) }
    }
}
