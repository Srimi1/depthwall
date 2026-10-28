package dev.srimi.depthwall

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.srimi.depthwall.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImportAndApplyTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val repo=(context.applicationContext as DepthWallApp).repository
    @Test fun localImportExportAndHomeApplyWork():Unit=runBlocking {
        val file=File(context.cacheDir,"qa-import.png")
        val bitmap=Bitmap.createBitmap(240,520,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(255,135,10)) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        val item=repo.importImage(Uri.fromFile(file)).copy(title="QA export")
        try {
            assertTrue(File(item.localPath).exists())
            val before=WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
            repo.applyStatic(item,WallpaperManager.FLAG_SYSTEM,SceneConfig(),240,520)
            val after=WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
            assertTrue(after!=before && after>0)
            repo.export(item)
            val collection=MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(collection,arrayOf(MediaStore.Images.Media._ID),"${MediaStore.Images.Media.DISPLAY_NAME}=?",arrayOf("DepthWall_QA_export.png"),null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                context.contentResolver.delete(android.content.ContentUris.withAppendedId(collection,cursor.getLong(0)),null,null)
            }
        } finally { repo.dao.delete(item.id);File(item.localPath).delete();file.delete() }
    }
    @Test fun invalidImagesAndOpaqueForegroundAreRejected():Unit=runBlocking {
        val bad=File(context.cacheDir,"bad.img").apply { writeText("not an image") }
        assertTrue(runCatching { repo.importImage(Uri.fromFile(bad)) }.isFailure);bad.delete()
        val file=File(context.cacheDir,"opaque.png")
        val bitmap=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        assertTrue(runCatching { repo.importImage(Uri.fromFile(file),true) }.isFailure);file.delete()
    }
    @Test fun apkInspectionFindsIdentityAndRejectsWrongPackage():Unit=runBlocking {
        val result=PackManager(context).inspect(Uri.fromFile(File(context.applicationInfo.sourceDir)))
        assertEquals("dev.srimi.depthwall",result.packageName)
        assertTrue(result.signatures.isNotEmpty())
        assertEquals(64,result.sha256.length)
        assertTrue(result.problem!!.contains("not one of the six"))
        result.file.delete()
    }
}
