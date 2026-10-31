package dev.srimi.depthwall.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import org.json.JSONObject

// A file damaged by a power loss must not crash the process that also hosts the live wallpaper.
private val Context.dataStore by preferencesDataStore("settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })
class SettingsStore(private val context: Context) {
    private val quality = stringPreferencesKey("quality")
    private val hours = intPreferencesKey("rotation_hours")
    private val target = intPreferencesKey("rotation_target")
    private val favorites = booleanPreferencesKey("favorites_only")
    private val wifi = booleanPreferencesKey("wifi_only")
    private val theme = stringPreferencesKey("app_theme")
    private val profile = stringPreferencesKey("device_profile")
    private val exportSize = stringPreferencesKey("export_size")
    private val depth = booleanPreferencesKey("depth_enabled")
    private val parallax = floatPreferencesKey("depth_parallax")
    private val clock = stringPreferencesKey("depth_clock")
    private val clockY = floatPreferencesKey("depth_clock_y")
    val flow = context.dataStore.data.map { values ->
        AppSettings(
            quality = values[quality] ?: "Balanced",
            rotationHours = values[hours] ?: 0,
            rotationTarget = values[target] ?: 1,
            favoritesOnly = values[favorites] ?: true,
            wifiOnly = values[wifi] ?: true,
            appTheme = values[theme] ?: "Papaya",
            deviceProfile = values[profile] ?: "OnePlus 7T Pro",
            downloadSize = values[exportSize] ?: "Original",
            depthEnabled = values[depth] ?: false,
            depthParallax = values[parallax]?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: .35f,
            depthClock = values[clock]?.takeIf { it in listOf("Off", "Digital", "Minimal", "Analogue") } ?: "Off",
            depthClockY = values[clockY]?.takeIf { it.isFinite() }?.coerceIn(.18f, .72f) ?: .33f,
        )
    }
    suspend fun save(s: AppSettings) = context.dataStore.edit {
        it[quality]=s.quality; it[hours]=s.rotationHours; it[target]=s.rotationTarget; it[favorites]=s.favoritesOnly; it[wifi]=s.wifiOnly
        it[theme]=s.appTheme; it[profile]=s.deviceProfile; it[exportSize]=s.downloadSize
        it[depth]=s.depthEnabled; it[parallax]=s.depthParallax; it[clock]=s.depthClock; it[clockY]=s.depthClockY
    }
}

/** Synchronous apply boundary: a preview never writes the active scene. */
@android.annotation.SuppressLint("ApplySharedPref") // Commit preview/apply state before Android creates another engine.
class SceneStore(context: Context) {
    private val prefs = context.getSharedPreferences("scenes", Context.MODE_PRIVATE)
    fun stage(config: SceneConfig, before: WallpaperApplyState? = null) {
        prefs.edit().putString("pending", encode(config))
            .putString("pending_before", before?.let { encodeState(it) })
            .putString("pending_previous_home", encode(active(1)))
            .remove("pending_applied_flags").commit()
    }
    fun pending(): SceneConfig = decode(prefs.getString("pending", null))
    fun active(flags: Int = 1): SceneConfig = decode(prefs.getString(if (flags == 2) "lock" else "home", null))
    fun commit(config: SceneConfig, flags: Int) {
        if (flags and 3 == 0) return
        val editor=prefs.edit(); val encoded=encode(config)
        if (flags and 1 != 0) editor.putString("home",encoded)
        if (flags and 2 != 0) editor.putString("lock",encoded)
        editor.putLong("revision",System.nanoTime()).commit()
    }
    /** The newer picker callback supplies authoritative destinations when it is supported. */
    fun confirmApplied(config: SceneConfig, flags: Int) {
        val destinations = flags and 3
        if (destinations == 0) return
        if (prefs.contains("pending") && pending() == config)
            prefs.edit().putInt("pending_applied_flags", destinations).commit()
        commit(config, destinations)
    }
    /** Complete an app-launched preview, including pickers that never call onApplyWallpaper. */
    fun finishPreview(accepted: Boolean, after: WallpaperApplyState?, component: String): Int {
        try {
            if (!prefs.contains("pending")) return 0
            val callbackFlags = prefs.getInt("pending_applied_flags", 0) and 3
            val before = decodeState(prefs.getString("pending_before", null))
            val flags = if (callbackFlags != 0) callbackFlags
                else if (accepted && before != null && after != null)
                    WallpaperApplyDestination.resolve(before, after, component)
                else 0
            // When Android splits a shared live wallpaper, lock inherits the old HOME scene.
            // Its dormant stored lock scene may differ. Stage the old home before any callback
            // can overwrite it, then copy only after the actual ID migration is confirmed.
            if (flags == 1 && before != null && after != null && before.lockId < 0 &&
                before.homeComponent == component && after.lockId == before.homeId &&
                after.lockComponent == component) {
                prefs.getString("pending_previous_home", null)?.let { commit(decode(it), 2) }
            }
            if (flags != 0 && callbackFlags == 0) commit(pending(), flags)
            return flags
        } finally { clearPending() }
    }
    fun clearPending() {
        prefs.edit().remove("pending").remove("pending_before").remove("pending_previous_home")
            .remove("pending_applied_flags").commit()
    }
    private fun encodeState(s: WallpaperApplyState) = JSONObject().apply {
        put("homeId", s.homeId); put("lockId", s.lockId)
        put("homeComponent", s.homeComponent); put("lockComponent", s.lockComponent)
    }.toString()
    private fun decodeState(value: String?): WallpaperApplyState? = runCatching {
        val j = JSONObject(value ?: return@runCatching null)
        WallpaperApplyState(j.getInt("homeId"), j.getInt("lockId"),
            if (j.isNull("homeComponent")) null else j.getString("homeComponent"),
            if (j.isNull("lockComponent")) null else j.getString("lockComponent"))
    }.getOrNull()
    fun revision() = prefs.getLong("revision",0)
    fun listenForAppliedChanges(onChange: () -> Unit): () -> Unit {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "revision") onChange()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun encode(c: SceneConfig): String = JSONObject().apply {
        put("id",c.wallpaperId);put("scene",c.scene);put("background",c.background);put("foreground",c.foreground)
        put("clock",c.clock);put("parallax",c.parallax);put("clockY",c.clockY)
        put("zoom",c.zoom);put("x",c.offsetX);put("y",c.offsetY)
    }.toString()
    fun decode(s: String?): SceneConfig = runCatching {
        val j=JSONObject(s ?: "{}")
        SceneConfig(j.optString("id","scene_earth"),j.optString("scene","earth"),j.optString("background"),j.optString("foreground"),j.optString("clock","Off"),j.optDouble("parallax",.35).toFloat(),j.optDouble("clockY",.33).toFloat(),j.optDouble("zoom",1.0).toFloat(),j.optDouble("x",.5).toFloat(),j.optDouble("y",.5).toFloat())
    }.getOrDefault(SceneConfig())
}
