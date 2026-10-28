package dev.srimi.depthwall

import android.content.ContentValues
import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.srimi.depthwall.data.DepthDatabase
import dev.srimi.depthwall.data.Repository
import dev.srimi.depthwall.data.WallpaperItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** A real v1 database fixture, isolated from the installed user's library. */
@RunWith(AndroidJUnit4::class)
class WallpaperMigrationTest {
    @Test fun upgradeRetainsDownloadedFavoritesAndAddsUnknownDimensions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration_${UUID.randomUUID()}.db"
        val download = File.createTempFile("prior_wallpaper_", ".jpg", context.cacheDir)
        val originalBytes = byteArrayOf(1, 2, 3, 4, 5)
        download.writeBytes(originalBytes)
        val before = WallpaperItem(
            id = "nasa_existing_download", title = "Existing favorite", category = "NASA", color = "Blue",
            asset = "", thumbnail = "https://images-assets.nasa.gov/existing~thumb.jpg", foreground = "",
            scene = "", author = "Original credit", license = "Original usage note",
            sourceUrl = "https://images.nasa.gov/details/existing",
            remoteUrl = "https://images-assets.nasa.gov/existing~orig.jpg",
            localPath = download.path, favorite = true, bytes = originalBytes.size.toLong(),
        )
        var migrated: DepthDatabase? = null
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { legacy ->
                // Exact schema and identity from app/schemas/.../1.json, before dimensions existed.
                legacy.execSQL("""
                    CREATE TABLE IF NOT EXISTS `wallpapers` (
                        `id` TEXT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL,
                        `color` TEXT NOT NULL, `kind` TEXT NOT NULL, `asset` TEXT NOT NULL,
                        `thumbnail` TEXT NOT NULL, `foreground` TEXT NOT NULL, `scene` TEXT NOT NULL,
                        `author` TEXT NOT NULL, `license` TEXT NOT NULL, `sourceUrl` TEXT NOT NULL,
                        `remoteUrl` TEXT NOT NULL, `localPath` TEXT NOT NULL, `favorite` INTEGER NOT NULL,
                        `bytes` INTEGER NOT NULL, PRIMARY KEY(`id`))
                """.trimIndent())
                legacy.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
                legacy.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '1e780e17bd9d46fa45e9d88e5c35a3f1')")
                legacy.insertOrThrow("wallpapers", null, ContentValues().apply {
                    put("id", before.id); put("title", before.title); put("category", before.category)
                    put("color", before.color); put("kind", before.kind); put("asset", before.asset)
                    put("thumbnail", before.thumbnail); put("foreground", before.foreground); put("scene", before.scene)
                    put("author", before.author); put("license", before.license); put("sourceUrl", before.sourceUrl)
                    put("remoteUrl", before.remoteUrl); put("localPath", before.localPath)
                    put("favorite", 1); put("bytes", before.bytes)
                })
                legacy.version = 1
            }
            migrated = Room.databaseBuilder(context, DepthDatabase::class.java, name)
                .addMigrations(Repository.MIGRATION_1_2).build()
            val after = migrated.wallpapers().find(before.id)
            assertEquals(before, after)
            assertEquals(0, after!!.width)
            assertEquals(0, after.height)
            assertEquals(listOf(before), migrated.wallpapers().observe().first())
            assertArrayEquals(originalBytes, download.readBytes())

            // New metadata is writable after migration, without disturbing the old favorite.
            val measured = after.copy(width = 4096, height = 2160)
            migrated.wallpapers().save(measured)
            migrated.close()
            migrated = Room.databaseBuilder(context, DepthDatabase::class.java, name)
                .addMigrations(Repository.MIGRATION_1_2).build()
            assertEquals(measured, migrated.wallpapers().find(before.id))
        } finally {
            migrated?.close()
            context.deleteDatabase(name)
            download.delete()
        }
    }
}
