package dev.srimi.depthwall.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import java.io.IOException
import java.time.LocalTime
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class StandbyDesign(val label: String, val description: String) {
    DIGITAL("Digital", "Big numbers. Nothing in the way."),
    FLIP("Flip", "A little movement, once a minute."),
    ANALOGUE("Analogue", "A classic face with a modern glow."),
    CALENDAR("Calendar", "The time and the month at a glance."),
    PHOTO("Photo", "Your favourite view, around the clock.");

    fun next(direction: Int): StandbyDesign = entries[Math.floorMod(ordinal + direction, entries.size)]
    companion object {
        fun decode(value: String?) = entries.firstOrNull { it.name == value } ?: DIGITAL
    }
}

enum class StandbyNightMode(val label: String) {
    AUTO("Auto"), ON("On"), OFF("Off");
    companion object {
        fun decode(value: String?) = entries.firstOrNull { it.name == value } ?: AUTO
    }
}

data class StandbySettings(
    val enabled: Boolean = false,
    val design: StandbyDesign = StandbyDesign.DIGITAL,
    val nightMode: StandbyNightMode = StandbyNightMode.AUTO,
    val photoWallpaperId: String? = null,
)

private val Context.standbyDataStore by preferencesDataStore("standby",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })

/** Separate from wallpaper preference drafts. Each edit changes only the requested field. */
class StandbySettingsStore(context: Context) {
    private val store = context.applicationContext.standbyDataStore
    private val enabled = booleanPreferencesKey("enabled")
    private val design = stringPreferencesKey("design")
    private val night = stringPreferencesKey("night_mode")
    private val photo = stringPreferencesKey("photo_wallpaper_id")
    val flow = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { values ->
        StandbySettings(values[enabled] ?: false, StandbyDesign.decode(values[design]),
            StandbyNightMode.decode(values[night]), values[photo])
    }
    suspend fun setEnabled(value: Boolean) { store.edit { it[enabled] = value } }
    suspend fun setDesign(value: StandbyDesign) { store.edit { it[design] = value.name } }
    suspend fun setNightMode(value: StandbyNightMode) { store.edit { it[night] = value.name } }
    suspend fun setPhoto(value: String?) { store.edit {
        if (value == null) it.remove(photo) else it[photo] = value
    } }
}

object StandbyPolicy {
    fun isNight(mode: StandbyNightMode, time: LocalTime): Boolean = when (mode) {
        StandbyNightMode.AUTO -> time.hour >= 22 || time.hour < 7
        StandbyNightMode.ON -> true
        StandbyNightMode.OFF -> false
    }

    /** Hysteresis avoids entering/exiting repeatedly near a diagonal. Unknown means flat. */
    fun landscape(degrees: Int, naturalLandscape: Boolean, previous: Boolean?): Boolean {
        if (degrees < 0) return false
        val side = when (Math.floorMod(degrees, 360)) {
            in 60..120, in 240..300 -> true
            in 0..30, in 150..210, in 330..359 -> false
            else -> return previous ?: false
        }
        return if (naturalLandscape) !side else side
    }
}

/** Unknown orientation during sensor startup must not clear an existing dismissal. */
class StandbyEntryGate {
    var suppressed: Boolean = false
        private set
    fun dismiss() { suppressed = true }
    fun update(enabled: Boolean, powered: Boolean, landscape: Boolean?): Boolean {
        if (!enabled || !powered || landscape == false) suppressed = false
        return enabled && powered && landscape == true && !suppressed
    }
}

object StandbyPhoto {
    const val DEFAULT_MODEL = "file:///android_asset/wallpapers/wall_0_0.webp"
    fun model(item: WallpaperItem?, exists: (String) -> Boolean = { File(it).isFile }): String {
        if (item == null || item.kind != WallpaperKind.STATIC.name) return DEFAULT_MODEL
        if (item.localPath.isNotBlank() && exists(item.localPath)) return item.localPath
        if (item.asset.isNotBlank()) return "file:///android_asset/${item.asset}"
        return DEFAULT_MODEL
    }
}
