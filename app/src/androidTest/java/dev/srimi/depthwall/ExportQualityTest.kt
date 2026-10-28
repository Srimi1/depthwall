package dev.srimi.depthwall

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.srimi.depthwall.data.SceneConfig
import dev.srimi.depthwall.data.WallpaperItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExportQualityTest {
    @Test fun originalPreservesJpegBytesWhilePhoneExportCropsWithoutUpscaling() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = (context.applicationContext as DepthWallApp).repository
        val preferences = repo.settings.flow.first()
        val token = UUID.randomUUID().toString().replace("-", "")
        val originalTitle = "ExportOriginal_$token"
        val optimizedTitle = "ExportPhone_$token"
        val originalName = "DepthWall_$originalTitle.jpg"
        val optimizedName = "DepthWall_$optimizedTitle.png"
        val source = File.createTempFile("export_quality_", ".jpg", context.cacheDir)
        try {
            val image = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
            try {
                image.eraseColor(Color.BLUE)
                Canvas(image).drawRect(0f, 0f, 600f, 800f, Paint().apply { color = Color.RED })
                source.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.JPEG, 93, it)) }
            } finally { image.recycle() }
            val sourceBytes = source.readBytes()
            val item = WallpaperItem("export_$token", originalTitle, "Imports", localPath = source.path)
            repo.settings.save(preferences.copy(downloadSize = "Original", deviceProfile = "OnePlus 7T Pro"))
            // Original means the actual source, even when the preview has been zoomed or moved.
            repo.export(item, SceneConfig(zoom = 2f, offsetX = 1f))
            val original = findExport(context, originalName)
            assertEquals("image/jpeg", context.contentResolver.getType(original))
            context.contentResolver.openInputStream(original)!!.use { assertArrayEquals(sourceBytes, it.readBytes()) }

            repo.settings.save(preferences.copy(downloadSize = "Phone optimized", deviceProfile = "OnePlus 7T Pro"))
            repo.export(item.copy(title = optimizedTitle), SceneConfig(offsetX = 1f))
            val optimized = findExport(context, optimizedName)
            assertEquals("image/png", context.contentResolver.getType(optimized))
            val crop = context.contentResolver.openInputStream(optimized)!!.use { BitmapFactory.decodeStream(it) }!!
            try {
                // A 1200x800 source cannot supply a 1440x3120 portrait. Keep its real detail.
                assertTrue(crop.width <= 370)
                assertTrue(crop.height in 790..800)
                assertEquals(1440f / 3120f, crop.width.toFloat() / crop.height, .002f)
                // Right-aligned crop must exclude the red half (a centered crop would include it).
                val pixel = crop.getPixel((crop.width * .1f).toInt(), crop.height / 2)
                assertTrue(Color.blue(pixel) > Color.red(pixel) + 100)
            } finally { crop.recycle() }
            assertArrayEquals(sourceBytes, source.readBytes())
        } finally {
            try { repo.settings.save(preferences) }
            finally {
                deleteExports(context, originalName)
                deleteExports(context, optimizedName)
                source.delete()
            }
        }
    }

    private fun findExport(context: Context, name: String): Uri {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.IS_PENDING),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?", arrayOf(name), null,
        )!!.use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(1))
            return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0))
        }
    }

    private fun deleteExports(context: Context, name: String) {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?", arrayOf(name), null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0))
                context.contentResolver.delete(uri, null, null)
            }
        }
    }
}
