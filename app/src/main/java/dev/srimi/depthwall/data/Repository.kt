package dev.srimi.depthwall.data

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class Repository(val context: Context) {
    val db = Room.databaseBuilder(context,DepthDatabase::class.java,"depthwall.db")
        .addMigrations(MIGRATION_1_2).build()
    val dao = db.wallpapers()
    val settings = SettingsStore(context)
    val scenes = SceneStore(context)
    val http = OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(40,TimeUnit.SECONDS).callTimeout(120,TimeUnit.SECONDS).build()
    private val catalog = OnlineCatalog(http)
    private val imageAccess = RemoteImageAccess(http)
    private val webImporter = WebImageImporter(http)
    val downloads = File(context.filesDir,"wallpapers").apply { mkdirs() }

    suspend fun seed() = withContext(Dispatchers.IO) {
        for (catalogFile in listOf("catalog.json", "extra_catalog.json", "device_catalog.json")) {
            val array=JSONArray(context.assets.open(catalogFile).bufferedReader().use { it.readText() })
            dao.seed((0 until array.length()).map { i ->
                val j=array.getJSONObject(i)
                WallpaperItem(j.getString("id"),j.getString("title"),j.getString("category"),j.optString("color"),j.getString("kind"),j.optString("asset"),j.optString("thumbnail"),j.optString("foreground"),j.optString("scene"),j.optString("author"),j.optString("license"),j.optString("sourceUrl"),
                    width=j.optInt("width"),height=j.optInt("height"))
            })
        }
    }

    val devicePicks: List<String> by lazy {
        runCatching { DevicePicks.parse(context.assets.open(DevicePicks.ASSET).bufferedReader().use { it.readText() }) }
            .getOrDefault(emptyList())
    }

    fun open(path: String): InputStream = if (path.startsWith("/")) File(path).inputStream() else context.assets.open(path.removePrefix("file:///android_asset/"))
    fun decode(path: String, maxEdge: Int = 4096): Bitmap {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        open(path).use { BitmapFactory.decodeStream(it,null,bounds) }
        require(bounds.outWidth>0 && bounds.outHeight>0) { "This file is not a supported image." }
        require(bounds.outWidth.toLong()*bounds.outHeight<=100_000_000) { "Image exceeds the 100 megapixel limit." }
        var sample=1
        while (maxOf(bounds.outWidth,bounds.outHeight)/sample>maxEdge) sample*=2
        return open(path).use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample; inPreferredConfig=Bitmap.Config.ARGB_8888 }) } ?: error("Unable to decode image.")
    }
    suspend fun importImage(uri: Uri, foreground: Boolean = false): WallpaperItem = withContext(Dispatchers.IO) {
        val file=File(downloads,"import_${UUID.randomUUID()}.img")
        try {
            context.contentResolver.openInputStream(uri)?.use { copyLimited(it,file,40L*1024*1024) } ?: error("Cannot read selected file.")
            val bitmap=decode(file.path)
            if (foreground) {
                val row=IntArray(bitmap.width)
                val transparent=bitmap.hasAlpha() && (0 until bitmap.height).any { y -> bitmap.getPixels(row,0,bitmap.width,0,y,bitmap.width,1);row.any { it ushr 24 < 255 } }
                if(!transparent) { bitmap.recycle();error("Choose a foreground with transparent pixels, or use Edit mask.") }
            }
            bitmap.recycle()
            val item=WallpaperItem("import_${UUID.randomUUID()}",if(foreground) "Imported foreground" else "My wallpaper","Imports",author="Personal import",license="Your file",localPath=file.path,bytes=file.length(),width=imageBounds(file.path).outWidth,height=imageBounds(file.path).outHeight)
            if (!foreground) dao.save(item)
            item
        } catch(e:Exception) { file.delete();throw e }
    }
    suspend fun download(item: WallpaperItem): WallpaperItem = withContext(Dispatchers.IO) {
        dao.find(item.id)?.takeIf { it.localPath.isNotBlank() && File(it.localPath).exists() }?.let { return@withContext it }
        val connection=context.getSystemService(android.net.ConnectivityManager::class.java)
        require(!settings.flow.first().wifiOnly || !connection.isActiveNetworkMetered) { "Wi-Fi-only downloads are enabled. Connect to Wi-Fi or change this in Settings." }
        val resolved=catalog.resolveDownload(item)
        val dest=File(downloads,"${item.id.replace(Regex("[^a-zA-Z0-9_-]"),"_")}.img")
        val temp=File(downloads,"${dest.name}.${UUID.randomUUID()}.part")
        try {
            require(downloads.usableSpace > MAX_IMAGE_BYTES + 8*1024*1024) { "Not enough free storage." }
            imageAccess.open(resolved.remoteUrl).use { response ->
                check(response.isSuccessful) { if(response.code==429) "This source is busy. Please retry later; DepthWall has no download quota." else "Download failed (${response.code}). Try again later." }
                imageAccess.copyImage(response,temp,MAX_IMAGE_BYTES)
            }
            val bounds=imageBounds(temp.path)
            decode(temp.path,1024).recycle()
            check(temp.renameTo(dest)) { "Unable to save the download." }
            resolved.copy(localPath=dest.path,bytes=dest.length(),width=bounds.outWidth,height=bounds.outHeight,
                favorite=dao.find(item.id)?.favorite ?: item.favorite).also { dao.save(it) }
        } finally { temp.delete() }
    }
    suspend fun onlinePage(provider:String,query:String,cursor:String?=null,only4k:Boolean=false):OnlinePage =
        catalog.search(provider,query,cursor,only4k)
    suspend fun online(provider:String,query:String):List<WallpaperItem> = onlinePage(provider,query).items
    suspend fun importWebImage(url:String):List<WallpaperItem> = webImporter.extract(url)

    private fun imageBounds(path:String):BitmapFactory.Options = BitmapFactory.Options().apply {
        inJustDecodeBounds=true
        open(path).use { BitmapFactory.decodeStream(it,null,this) }
        require(outWidth>0 && outHeight>0) { "This file is not a supported image." }
        require(outWidth.toLong()*outHeight<=100_000_000) { "Image exceeds the 100 megapixel safety limit. Choose a smaller source image." }
    }

    /** Decode only the selected crop, preserving portrait detail in large landscape originals. */
    private fun croppedBitmap(path:String,config:SceneConfig,target:WallpaperSizing.Size):Bitmap {
        val bounds=imageBounds(path)
        val crop=CropMath.calculate(bounds.outWidth,bounds.outHeight,target.width,target.height,config.zoom,config.offsetX,config.offsetY)
        val output=WallpaperSizing.fitSource(crop.width,crop.height,target)
        var sample=1
        while(crop.width/(sample*2)>=output.width && crop.height/(sample*2)>=output.height) sample*=2
        val region=try { open(path).use { input ->
            val decoder=BitmapRegionDecoder.newInstance(input,false) ?: throw java.io.IOException("Region decoding unavailable")
            try { decoder.decodeRegion(Rect(crop.left,crop.top,crop.left+crop.width,crop.top+crop.height),
                BitmapFactory.Options().apply { inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888 })
                ?: error("Unable to decode image crop.") }
            finally { decoder.recycle() }
        } } catch(e:java.io.IOException) { return fallbackCrop(path,config,target) }
        catch(e:IllegalArgumentException) { return fallbackCrop(path,config,target) }
        return try {
            Bitmap.createScaledBitmap(region,output.width,output.height,true).also { if(it!==region)region.recycle() }
        } catch(e:Exception) { region.recycle();throw e }
    }
    // Some Android decoders (e.g. GIF/BMP) support whole images but not regions.
    private fun fallbackCrop(path:String,config:SceneConfig,target:WallpaperSizing.Size):Bitmap {
        val decoded=decode(path, maxOf(target.width,target.height))
        try {
            val crop=CropMath.calculate(decoded.width,decoded.height,target.width,target.height,config.zoom,config.offsetX,config.offsetY)
            val cropped=Bitmap.createBitmap(decoded,crop.left,crop.top,crop.width,crop.height)
            val size=WallpaperSizing.fitSource(crop.width,crop.height,target)
            try {
                val result=Bitmap.createScaledBitmap(cropped,size.width,size.height,true)
                // Own a separate bitmap so the decoded source can always be released below.
                return if(result===decoded) result.copy(Bitmap.Config.ARGB_8888,false) else result
            } finally { if(cropped!==decoded && (cropped.width!=size.width || cropped.height!=size.height))cropped.recycle() }
        } finally { decoded.recycle() }
    }
    suspend fun applyStatic(item:WallpaperItem,which:Int,config:SceneConfig,w:Int,h:Int) = withContext(Dispatchers.IO) {
        val local=if(item.isLocal)item else download(item)
        val prefs=settings.flow.first()
        val bitmap=croppedBitmap(local.localPath.ifBlank { local.asset },config,WallpaperSizing.target(prefs.deviceProfile,w,h))
        try { WallpaperManager.getInstance(context).setBitmap(bitmap,null,false,which) }
        finally { bitmap.recycle() }
    }
    suspend fun export(item:WallpaperItem,config:SceneConfig=SceneConfig()) = withContext(Dispatchers.IO) {
        val local=if(item.isLocal)item else download(item)
        val prefs=settings.flow.first()
        val path=local.localPath.ifBlank { local.asset }
        val original=prefs.downloadSize=="Original" && local.kind=="STATIC"
        val sourceMime=imageBounds(path).outMimeType
        val mime=if(original) sourceMime ?: "image/png" else "image/png"
        val extension=when(mime) { "image/jpeg"->"jpg";"image/webp"->"webp";"image/gif"->"gif";"image/avif"->"avif";"image/heif"->"heif";"image/heic"->"heic";"image/bmp","image/x-ms-bmp"->"bmp";else->"png" }
        val values=ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"DepthWall_${item.title.replace(Regex("[^a-zA-Z0-9_-]"),"_").take(100)}.$extension")
            put(MediaStore.Images.Media.MIME_TYPE,mime)
            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/DepthWall")
            put(MediaStore.Images.Media.IS_PENDING,1)
        }
        val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: error("Cannot create image in Pictures.")
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                if(original) open(path).use { it.copyTo(output) }
                else {
                    val metrics=context.resources.displayMetrics
                    val target=WallpaperSizing.target(prefs.deviceProfile,metrics.widthPixels,metrics.heightPixels,prefs.downloadSize=="4K portrait")
                    val decoded=if(prefs.downloadSize=="Original")decode(path) else croppedBitmap(path,config,target)
                    val bitmap=if(local.kind=="LAYERED_DEPTH" && local.foreground.isNotBlank()) {
                        val mutable=decoded.copy(Bitmap.Config.ARGB_8888,true);decoded.recycle()
                        try {
                            val fg=if(prefs.downloadSize=="Original")decode(local.foreground) else croppedBitmap(local.foreground,config,target)
                            try { android.graphics.Canvas(mutable).drawBitmap(fg,null,Rect(0,0,mutable.width,mutable.height),android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)) }
                            finally { fg.recycle() }
                            mutable
                        } catch(e:Exception) { mutable.recycle();throw e }
                    } else decoded
                    try { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output)) { "Unable to encode image." } }
                    finally { bitmap.recycle() }
                }
            } ?: error("Cannot write image.")
            context.contentResolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
        } catch(e:Exception) { context.contentResolver.delete(uri,null,null);throw e }
    }
    fun config(item:WallpaperItem)=SceneConfig(item.id,item.scene.ifBlank { "depth" },item.localPath.ifBlank { item.asset },item.foreground)
    suspend fun saveDepth(base:WallpaperItem, foreground:String, title:String):WallpaperItem {
        return base.copy(id="depth_custom_${UUID.randomUUID()}",title=title,kind="LAYERED_DEPTH",foreground=foreground,category="My depth",thumbnail="").also { dao.save(it) }
    }
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wallpapers ADD COLUMN width INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE wallpapers ADD COLUMN height INTEGER NOT NULL DEFAULT 0")
            }
        }
        const val MAX_IMAGE_BYTES=64L*1024*1024
        fun copyLimited(input:InputStream,file:File,limit:Long) {
            require(file.parentFile!!.usableSpace>limit+8*1024*1024) { "Not enough free storage." }
            file.outputStream().use { output ->
                val buffer=ByteArray(16384);var total=0L
                while(true) { val n=input.read(buffer);if(n<0)break;total+=n;require(total<=limit){"File exceeds the size limit."};output.write(buffer,0,n) }
                require(total>0) { "The selected file is empty." }
            }
        }
    }
}
