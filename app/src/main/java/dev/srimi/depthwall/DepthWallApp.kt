package dev.srimi.depthwall

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import dev.srimi.depthwall.data.AmbientWallpaper
import dev.srimi.depthwall.data.Repository
import dev.srimi.depthwall.data.RemoteImageAccess
import dev.srimi.depthwall.data.StandByAutoStart
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DepthWallApp : Application(), ImageLoaderFactory {
    val repository by lazy { Repository(this) }
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO +
        CoroutineExceptionHandler { _, error -> Log.w("DepthWall", "Startup task", error) })

    override fun onCreate() {
        super.onCreate()
        // Follows the AOD permission: granted on a system install, lost if the module is removed.
        AmbientWallpaper.syncComponent(this)
        // StandBy is on by default once DepthWall may switch Android's screen saver.
        background.launch { StandByAutoStart.applyDefault(this@DepthWallApp, repository.settings) }
    }
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { RemoteImageAccess(repository.http).safeClient() }
        .memoryCache { MemoryCache.Builder(this).maxSizeBytes(32 * 1024 * 1024).build() }
        .build()
}
