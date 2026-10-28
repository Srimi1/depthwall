package dev.srimi.depthwall.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class WallpaperKind { STATIC, LAYERED_DEPTH, NATIVE_SCENE, EXTERNAL_SERVICE }

@Entity(tableName = "wallpapers")
data class WallpaperItem(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val color: String = "Mixed",
    val kind: String = WallpaperKind.STATIC.name,
    val asset: String = "",
    val thumbnail: String = "",
    val foreground: String = "",
    val scene: String = "",
    val author: String = "",
    val license: String = "",
    val sourceUrl: String = "",
    val remoteUrl: String = "",
    val localPath: String = "",
    val favorite: Boolean = false,
    val bytes: Long = 0,
    @ColumnInfo(defaultValue = "0") val width: Int = 0,
    @ColumnInfo(defaultValue = "0") val height: Int = 0,
) {
    val isLocal: Boolean get() = asset.isNotBlank() || localPath.isNotBlank()
    val imageModel: String get() = localPath.ifBlank { if (asset.isNotBlank()) "file:///android_asset/$asset" else remoteUrl }
    val thumbnailModel: String get() = if(localPath.isNotBlank()) localPath else if (thumbnail.isNotBlank()) {
        if (thumbnail.startsWith("https:")) thumbnail else "file:///android_asset/$thumbnail"
    } else imageModel
}

@Dao
interface WallpaperDao {
    @Query("SELECT * FROM wallpapers ORDER BY rowid") fun observe(): Flow<List<WallpaperItem>>
    @Query("SELECT * FROM wallpapers WHERE id=:id") suspend fun find(id: String): WallpaperItem?
    @Query("SELECT * FROM wallpapers WHERE kind='STATIC' AND (localPath != '' OR asset != '')") suspend fun rotationCandidates(): List<WallpaperItem>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun seed(items: List<WallpaperItem>)
    @Upsert suspend fun save(item: WallpaperItem)
    @Query("UPDATE wallpapers SET favorite = NOT favorite WHERE id=:id") suspend fun toggleFavorite(id: String)
    @Query("DELETE FROM wallpapers WHERE id=:id") suspend fun delete(id: String)
}
@Database(entities = [WallpaperItem::class], version = 2, exportSchema = true)
abstract class DepthDatabase : RoomDatabase() { abstract fun wallpapers(): WallpaperDao }

data class SceneConfig(
    val wallpaperId: String = "scene_earth",
    val scene: String = "earth",
    val background: String = "",
    val foreground: String = "",
    val clock: String = "Off",
    val parallax: Float = .35f,
    val clockY: Float = .33f,
    val zoom: Float = 1f,
    val offsetX: Float = .5f,
    val offsetY: Float = .5f,
)

data class AppSettings(
    val quality: String = "Balanced",
    val rotationHours: Int = 0,
    val rotationTarget: Int = 1,
    val favoritesOnly: Boolean = true,
    val wifiOnly: Boolean = true,
    val appTheme: String = "Papaya",
    val deviceProfile: String = "OnePlus 7T Pro",
    val downloadSize: String = "Original",
    val depthEnabled: Boolean = false,
    val depthParallax: Float = .35f,
    val depthClock: String = "Off",
    val depthClockY: Float = .33f,
)

object CropMath {
    data class Crop(val left: Int, val top: Int, val width: Int, val height: Int)
    fun calculate(sw: Int, sh: Int, dw: Int, dh: Int, zoom: Float, x: Float, y: Float): Crop {
        require(sw > 0 && sh > 0 && dw > 0 && dh > 0)
        val ratio = dw.toFloat() / dh
        val baseW = minOf(sw.toFloat(), sh * ratio)
        val w = (baseW / zoom.coerceIn(1f, 3f)).toInt().coerceIn(1, sw)
        val h = (w / ratio).toInt().coerceIn(1, sh)
        return Crop(((sw-w)*x.coerceIn(0f,1f)).toInt(), ((sh-h)*y.coerceIn(0f,1f)).toInt(), w, h)
    }
}

object RenderBudget {
    // Settled terrain and layered images have no time-driven shader effects.
    // Motion/clock events can still request a frame without an idle frame loop.
    fun hasContinuousMotion(scene: String, progress: Float): Boolean = when(scene) {
        "depth" -> false
        "earth", "mars", "moon" -> progress < 1f
        else -> true
    }
    fun renderWidth(quality: String, saver: Boolean, hot: Boolean): Int =
        if(saver || hot || quality == "Battery") 720 else if(quality == "High Quality") 1440 else 960

    fun fps(quality: String, transitioning: Boolean, visible: Boolean, saver: Boolean, hot: Boolean, refresh: Float = 90f): Int {
        if (!visible) return 0
        if (saver || hot) return if (transitioning) 15 else 0
        return when (quality) {
            "Battery" -> if (transitioning) 30 else 0
            "High Quality" -> if (transitioning) refresh.toInt().coerceIn(30,90) else 30
            else -> if (transitioning) 60 else 15
        }
    }
}

object SceneTransition {
    const val DURATION_MS=1800L
    fun progress(from:Float,to:Float,elapsedMs:Long):Float {
        val t=(elapsedMs.toFloat()/DURATION_MS).coerceIn(0f,1f)
        return from+(to-from)*t*t*(3f-2f*t)
    }
}
