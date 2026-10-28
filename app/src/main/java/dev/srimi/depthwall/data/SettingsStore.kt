package dev.srimi.depthwall.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore("settings")
class SettingsStore(private val context: Context) {
    private val quality = stringPreferencesKey("quality")
    private val hours = intPreferencesKey("rotation_hours")
    private val target = intPreferencesKey("rotation_target")
    private val favorites = booleanPreferencesKey("favorites_only")
    private val wifi = booleanPreferencesKey("wifi_only")
    val flow = context.dataStore.data.map { AppSettings(it[quality] ?: "Balanced", it[hours] ?: 0, it[target] ?: 1, it[favorites] ?: true, it[wifi] ?: true) }
    suspend fun save(s: AppSettings) = context.dataStore.edit {
        it[quality]=s.quality; it[hours]=s.rotationHours; it[target]=s.rotationTarget; it[favorites]=s.favoritesOnly; it[wifi]=s.wifiOnly
    }
}

/** Synchronous apply boundary: a preview never writes the active scene. */
@android.annotation.SuppressLint("ApplySharedPref") // Commit preview/apply state before Android creates another engine.
class SceneStore(context: Context) {
    private val prefs = context.getSharedPreferences("scenes", Context.MODE_PRIVATE)
    fun stage(config: SceneConfig) { prefs.edit().putString("pending", encode(config)).commit() }
    fun pending(): SceneConfig = decode(prefs.getString("pending", null))
    fun active(flags: Int = 1): SceneConfig = decode(prefs.getString(if (flags == 2) "lock" else "home", null))
    fun commit(config: SceneConfig, flags: Int) {
        val editor=prefs.edit(); val encoded=encode(config)
        if (flags and 1 != 0) editor.putString("home",encoded)
        if (flags and 2 != 0) editor.putString("lock",encoded)
        editor.putLong("revision",System.nanoTime()).commit()
    }
    fun clearPending() { prefs.edit().remove("pending").apply() }
    fun revision() = prefs.getLong("revision",0)
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
