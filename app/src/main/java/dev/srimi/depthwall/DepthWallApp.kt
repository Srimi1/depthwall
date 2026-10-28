package dev.srimi.depthwall

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import dev.srimi.depthwall.data.Repository

class DepthWallApp : Application(), ImageLoaderFactory {
    val repository by lazy { Repository(this) }
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizeBytes(32 * 1024 * 1024).build() }
        .build()
}
