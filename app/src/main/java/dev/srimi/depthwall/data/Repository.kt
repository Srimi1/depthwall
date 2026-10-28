package dev.srimi.depthwall.data

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class Repository(val context: Context) {
    val db = Room.databaseBuilder(context,DepthDatabase::class.java,"depthwall.db").build()
    val dao = db.wallpapers()
    val settings = SettingsStore(context)
    val scenes = SceneStore(context)
    val http = OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(40,TimeUnit.SECONDS).callTimeout(120,TimeUnit.SECONDS).build()
    val downloads = File(context.filesDir,"wallpapers").apply { mkdirs() }

    suspend fun seed() = withContext(Dispatchers.IO) {
        val array=JSONArray(context.assets.open("catalog.json").bufferedReader().use { it.readText() })
        dao.seed((0 until array.length()).map { i ->
            val j=array.getJSONObject(i)
            WallpaperItem(j.getString("id"),j.getString("title"),j.getString("category"),j.optString("color"),j.getString("kind"),j.optString("asset"),j.optString("thumbnail"),j.optString("foreground"),j.optString("scene"),j.optString("author"),j.optString("license"),j.optString("sourceUrl"))
        })
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
            val item=WallpaperItem("import_${UUID.randomUUID()}",if(foreground) "Imported foreground" else "My wallpaper","Imports",author="Personal import",license="Your file",localPath=file.path,bytes=file.length())
            if (!foreground) dao.save(item)
            item
        } catch(e:Exception) { file.delete();throw e }
    }
    suspend fun download(item: WallpaperItem): WallpaperItem = withContext(Dispatchers.IO) {
        dao.find(item.id)?.takeIf { it.localPath.isNotBlank() && File(it.localPath).exists() }?.let { return@withContext it }
        val connection=context.getSystemService(android.net.ConnectivityManager::class.java)
        require(!settings.flow.first().wifiOnly || !connection.isActiveNetworkMetered) { "Wi-Fi-only downloads are enabled. Connect to Wi-Fi or change this in Library → Settings." }
        val url=Uri.parse(item.remoteUrl)
        require(url.scheme=="https" && allowedImageHost(url.host.orEmpty())) { "Image host is not in this collection's source list." }
        val dest=File(downloads,"${item.id.replace(Regex("[^a-zA-Z0-9_-]"),"_")}.img")
        val temp=File(downloads,"${dest.name}.${UUID.randomUUID()}.part")
        try {
            http.newCall(Request.Builder().url(item.remoteUrl).header("User-Agent","DepthWall/1.0 (personal wallpaper app; github.com/Srimi1/depthwall)").build()).execute().use { response ->
                check(response.isSuccessful) { "Download failed (${response.code}). Try again later." }
                require(allowedImageHost(response.request.url.host)) { "Unexpected download redirect." }
                val body=response.body ?: error("Empty download.")
                require(body.contentLength()<=40L*1024*1024) { "Image is larger than 40 MB." }
                body.byteStream().use { copyLimited(it,temp,40L*1024*1024) }
            }
            decode(temp.path).recycle()
            check(temp.renameTo(dest)) { "Unable to save the download." }
            item.copy(localPath=dest.path,bytes=dest.length(),favorite=dao.find(item.id)?.favorite ?: item.favorite).also { dao.save(it) }
        } finally { temp.delete() }
    }
    private fun allowedImageHost(host:String)=host=="images-assets.nasa.gov" || host.endsWith(".nasa.gov") || host=="upload.wikimedia.org" || host=="thumb.wikimedia.org" || host=="commons.wikimedia.org"
    private fun json(url:String):JSONObject = http.newCall(Request.Builder().url(url).header("User-Agent","DepthWall/1.0 (https://github.com/Srimi1/depthwall)").build()).execute().use {
        check(it.isSuccessful) { "Collection is unavailable (${it.code}). Your offline library is still available." }
        JSONObject(it.body?.string() ?: error("Empty response"))
    }
    suspend fun online(provider:String, query:String):List<WallpaperItem> = withContext(Dispatchers.IO) {
        if(provider=="NASA") {
            val data=json("https://images-api.nasa.gov/search?media_type=image&page_size=30&q=${Uri.encode(query.ifBlank { "Blue Marble" })}").getJSONObject("collection").getJSONArray("items")
            (0 until data.length()).mapNotNull { i -> runCatching {
                val row=data.getJSONObject(i);val info=row.getJSONArray("data").getJSONObject(0);val id=info.getString("nasa_id")
                val links=row.getJSONArray("links")
                val images=(0 until links.length()).map { links.getJSONObject(it) }.filter { it.optString("render")=="image" && it.optLong("size")<=40L*1024*1024 }
                val best=images.maxByOrNull { it.optInt("width") } ?: return@runCatching null
                val thumb=images.minByOrNull { kotlin.math.abs(it.optInt("width")-500) }!!.getString("href")
                WallpaperItem("nasa_$id",info.getString("title"),"NASA",author=info.optString("photographer",info.optString("secondary_creator",info.optString("center","NASA"))),license="NASA media usage guidelines; check source credits",sourceUrl="https://images.nasa.gov/details/$id",remoteUrl=best.getString("href"),thumbnail=thumb,bytes=best.optLong("size"))
            }.getOrNull() }
        } else {
            val url="https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrsearch=${Uri.encode(query.ifBlank { "night landscape" }+" filetype:bitmap")}&gsrnamespace=6&gsrlimit=30&prop=imageinfo&iiprop=url%7Cextmetadata%7Csize&iiurlwidth=1800"
            val pages=json(url).optJSONObject("query")?.optJSONObject("pages") ?: return@withContext emptyList()
            pages.keys().asSequence().mapNotNull { key -> runCatching {
                val p=pages.getJSONObject(key);val info=p.getJSONArray("imageinfo").getJSONObject(0);val m=info.optJSONObject("extmetadata") ?: JSONObject()
                fun meta(k:String)=m.optJSONObject(k)?.optString("value").orEmpty().replace(Regex("<[^>]*>"),"").take(300)
                val license=meta("LicenseShortName")
                if(!license.startsWith("CC") && !license.contains("public domain",true)) return@runCatching null
                WallpaperItem("commons_$key",p.getString("title").removePrefix("File:").substringBeforeLast('.'),"Wikimedia",author=meta("Artist"),license=license,sourceUrl=info.getString("descriptionurl"),remoteUrl=info.optString("thumburl",info.getString("url")),thumbnail=info.optString("thumburl",info.getString("url")))
            }.getOrNull() }.toList()
        }
    }
    suspend fun applyStatic(item:WallpaperItem,which:Int,config:SceneConfig,w:Int,h:Int) = withContext(Dispatchers.IO) {
        val local=if(item.isLocal)item else download(item)
        val bitmap=decode(local.localPath.ifBlank { local.asset })
        val crop=CropMath.calculate(bitmap.width,bitmap.height,w,h,config.zoom,config.offsetX,config.offsetY)
        val cropped=Bitmap.createBitmap(bitmap,crop.left,crop.top,crop.width,crop.height)
        val scale=minOf(1f,1440f/w,3120f/h)
        val scaled=Bitmap.createScaledBitmap(cropped,(w*scale).toInt().coerceAtLeast(1),(h*scale).toInt().coerceAtLeast(1),true)
        try { WallpaperManager.getInstance(context).setBitmap(scaled,null,false,which) }
        finally { if(scaled!==cropped)scaled.recycle();if(cropped!==bitmap)cropped.recycle();bitmap.recycle() }
    }
    suspend fun export(item:WallpaperItem) = withContext(Dispatchers.IO) {
        val local=if(item.isLocal)item else download(item)
        val values=ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"DepthWall_${item.title.replace(Regex("[^a-zA-Z0-9_-]"),"_").take(100)}.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/DepthWall");put(MediaStore.Images.Media.IS_PENDING,1) }
        val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: error("Cannot create image in Pictures.")
        try {
            val decoded=decode(local.localPath.ifBlank { local.asset })
            val bitmap=decoded.copy(Bitmap.Config.ARGB_8888,true)
            decoded.recycle()
            if(local.kind=="LAYERED_DEPTH" && local.foreground.isNotBlank()) {
                val fg=decode(local.foreground)
                android.graphics.Canvas(bitmap).drawBitmap(fg,null,android.graphics.Rect(0,0,bitmap.width,bitmap.height),android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));fg.recycle()
            }
            try { context.contentResolver.openOutputStream(uri)?.use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) } ?: error("Cannot write image.") } finally { bitmap.recycle() }
            context.contentResolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
        } catch(e:Exception) { context.contentResolver.delete(uri,null,null);throw e }
    }
    fun config(item:WallpaperItem)=SceneConfig(item.id,item.scene.ifBlank { "depth" },item.localPath.ifBlank { item.asset },item.foreground)
    suspend fun saveDepth(base:WallpaperItem, foreground:String, title:String):WallpaperItem {
        return base.copy(id="depth_custom_${UUID.randomUUID()}",title=title,kind="LAYERED_DEPTH",foreground=foreground,category="My depth",thumbnail="").also { dao.save(it) }
    }
    companion object {
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
