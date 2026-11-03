package dev.srimi.depthwall

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import dev.srimi.depthwall.data.AmbientWallpaper
import dev.srimi.depthwall.data.Repository
import dev.srimi.depthwall.data.RemoteImageAccess

class DepthWallApp : Application(), ImageLoaderFactory {
    val repository by lazy { Repository(this) }
    override fun onCreate() {
        super.onCreate()
        // Follows the AOD permission: granted on a system install, lost if the module is removed.
        AmbientWallpaper.syncComponent(this)
    }
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { RemoteImageAccess(repository.http).safeClient() }
        .memoryCache { MemoryCache.Builder(this).maxSizeBytes(32 * 1024 * 1024).build() }
        .build()
}
