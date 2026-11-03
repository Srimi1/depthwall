package dev.srimi.depthwall.wallpaper

/**
 * The same scenes, declared with supportsAmbientMode. The manifest ships it disabled: crDroid
 * rejects ambient wallpapers from apps without AMBIENT_WALLPAPER, which only a system install
 * receives. [dev.srimi.depthwall.data.AmbientWallpaper.syncComponent] enables it once granted.
 */
class AmbientDepthWallpaperService : DepthWallpaperService() {
    override fun onCreateEngine(): Engine = DepthEngine(ambientCapable = true)
}
