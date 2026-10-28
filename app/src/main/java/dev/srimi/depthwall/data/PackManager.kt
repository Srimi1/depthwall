package dev.srimi.depthwall.data

import android.app.WallpaperInfo
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.wallpaper.WallpaperService
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.json.JSONObject

data class PackDefinition(val title:String,val packageName:String,val url:String,val legacyUrl:String="")
data class InstalledPack(val definition:PackDefinition,val version:String?,val services:List<WallpaperInfo>,val signatures:List<String>,val error:String?=null)
data class ApkInspection(val file:File,val packageName:String,val version:String,val minSdk:Int,val targetSdk:Int,val abis:List<String>,val signatures:List<String>,val sha256:String,val services:List<String>,val activities:List<String>,val problem:String?)

class PackManager(private val context:Context) {
    private val pm=context.packageManager
    private val verified by lazy { JSONObject(context.assets.open("port-verification.json").bufferedReader().use { it.readText() }) }
    fun verifiedRelease(packageName:String):JSONObject?=verified.optJSONObject(packageName)
    val definitions=listOf(
        PackDefinition("Earth","com.miui.miwallpaper.earth","https://www.apkmirror.com/apk/ouxyl/superwallpaperearth-ouxyls-mod/superwallpaperearth-ouxyls-mod-alpha-2-6-561-11211547-ogl-64-fix1-release/","https://www.apkmirror.com/apk/linuxct/earth-super-wallpapers-default-linuxcts-port/earth-super-wallpapers-default-linuxcts-port-linuxct-2-6-260-08131844-211107-release/"),
        PackDefinition("Mars","com.miui.miwallpaper.mars","https://www.apkmirror.com/apk/ouxyl/superwallpapermars-ouxyls-mod/superwallpapermars-ouxyls-mod-alpha-2-6-561-11211547-ogl-64-fix1-release/","https://www.apkmirror.com/apk/linuxct/mars-super-wallpapers-default-linuxcts-port/mars-super-wallpapers-default-linuxcts-port-linuxct-2-6-260-08131855-211107-release/"),
        PackDefinition("Saturn","com.miui.miwallpaper.saturn","https://www.apkmirror.com/apk/ouxyl/superwallpapersaturn-ouxyls-mod/superwallpapersaturn-ouxyls-mod-alpha-2-6-561-11211547-ogl-64-fix1-release/"),
        PackDefinition("Moon","com.miui.miwallpaper.moon","https://www.apkmirror.com/apk/ouxyl/the-moon-super-wallpapers-ouxyls-mod/the-moon-super-wallpapers-ouxyls-mod-alpha-2-6-563-08071126-ogl-fix1-release/"),
        PackDefinition("Snow Mountain","com.miui.miwallpaper.snowmountain","https://www.apkmirror.com/apk/ouxyl/superwallpapersnowmountain-ouxyls-mod/superwallpapersnowmountain-ouxyls-mod-alpha-2-6-561-11211547-ogl-64-fix1-release/"),
        PackDefinition("Geometry","com.miui.miwallpaper.geometry","https://www.apkmirror.com/apk/ouxyl/superwallpapergeometry-ouxyls-mod/superwallpapergeometry-ouxyls-mod-alpha-2-7-561-11211547-vulkan-fix1-release/")
    )
    private fun signatures(p:PackageInfo)=p.signingInfo?.apkContentsSigners.orEmpty().map { sha(it.toByteArray()) }.sorted()
    suspend fun installed():List<InstalledPack> = withContext(Dispatchers.IO) { definitions.map { d ->
        val p=runCatching { pm.getPackageInfo(d.packageName,PackageManager.GET_SIGNING_CERTIFICATES) }.getOrNull()
        if(p==null) InstalledPack(d,null,emptyList(),emptyList()) else {
            val infos=pm.queryIntentServices(Intent(WallpaperService.SERVICE_INTERFACE).setPackage(d.packageName),PackageManager.GET_META_DATA)
            val services=infos.mapNotNull { runCatching { WallpaperInfo(context,it) }.getOrNull() }
            InstalledPack(d,p.versionName,services,signatures(p),if(services.isEmpty())"Installed, but no usable wallpaper service was discovered." else null)
        }
    } }
    suspend fun inspect(uri:Uri):ApkInspection = withContext(Dispatchers.IO) {
        val dir=File(context.filesDir,"packs").apply { mkdirs() }
        dir.listFiles()?.filter { it.name.endsWith(".apk") }?.forEach { it.delete() }
        val file=File(dir,"import.apk")
        try {
            context.contentResolver.openInputStream(uri)?.use { Repository.copyLimited(it,file,400L*1024*1024) } ?: error("Cannot read APK.")
            val p=pm.getPackageArchiveInfo(file.path,PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SERVICES or PackageManager.GET_ACTIVITIES or PackageManager.GET_PERMISSIONS) ?: error("Not a valid, signed APK archive.")
            val app=p.applicationInfo ?: error("Missing Android application metadata.")
            val abi=ZipFile(file).use { z -> z.entries().asSequence().map { it.name }.filter { it.startsWith("lib/") && it.endsWith(".so") }.map { it.split('/')[1] }.distinct().toList() }
            val certs=signatures(p)
            val existing=runCatching { pm.getPackageInfo(p.packageName,PackageManager.GET_SIGNING_CERTIFICATES) }.getOrNull()
            val errors=buildList {
                if(definitions.none { it.packageName==p.packageName }) add("This APK is not one of the six supported Super Wallpaper packages.")
                if(app.minSdkVersion>Build.VERSION.SDK_INT) add("This pack requires a newer Android version.")
                if(abi.isNotEmpty() && abi.none { it in Build.SUPPORTED_ABIS }) add("Native libraries do not match this phone's supported ABIs.")
                if(certs.isEmpty()) add("No APK signing certificate was found.")
                if(existing!=null && signatures(existing)!=certs) add("Signing conflict with installed ${existing.versionName}. DepthWall will not replace it.")
                if(existing!=null && p.longVersionCode<existing.longVersionCode) add("This is an older version than the installed pack.")
            }
            ApkInspection(file,p.packageName,p.versionName.orEmpty(),app.minSdkVersion,app.targetSdkVersion,abi,certs,MessageDigest.getInstance("SHA-256").let { digest -> file.inputStream().use { stream -> val bytes=ByteArray(16384);while(true){val n=stream.read(bytes);if(n<0)break;digest.update(bytes,0,n)} };hex(digest.digest()) },p.services.orEmpty().filter { it.exported && it.permission=="android.permission.BIND_WALLPAPER" }.map { it.name },p.activities.orEmpty().filter { it.exported }.map { it.name },errors.joinToString("\n").ifBlank { null })
        } catch(e:Exception) { file.delete();throw e }
    }
    fun installationIntent(report:ApkInspection):Intent {
        require(report.problem==null) { report.problem.orEmpty() }
        if(!pm.canRequestPackageInstalls()) return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}"))
        return Intent(Intent.ACTION_VIEW).setDataAndType(FileProvider.getUriForFile(context,"${context.packageName}.files",report.file),"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    fun preview(info:WallpaperInfo)=Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,info.component)
    fun configuration(info:WallpaperInfo):Intent? {
        val activity=info.settingsActivity ?: return null
        val component=ComponentName(info.packageName,activity)
        val resolved=runCatching { pm.getActivityInfo(component,0) }.getOrNull() ?: return null
        if(!resolved.exported || !resolved.enabled || resolved.permission!=null) return null
        return Intent().setComponent(component)
    }
    companion object {
        private fun hex(bytes:ByteArray)=bytes.joinToString("") { "%02x".format(it) }
        private fun sha(bytes:ByteArray)=hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    }
}
