package dev.srimi.depthwall.data

import android.app.WallpaperManager
import android.content.Context
import android.os.Build

/** A system snapshot, never an inferred destination based only on component equality. */
data class WallpaperApplyState(
    val homeId: Int,
    val lockId: Int,
    val homeComponent: String?,
    val lockComponent: String?
) {
    companion object {
        fun capture(context: Context): WallpaperApplyState? = runCatching {
            val manager = WallpaperManager.getInstance(context)
            // Recheck IDs so a concurrent wallpaper change cannot combine two different states.
            repeat(3) {
                val homeId = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
                val lockId = manager.getWallpaperId(WallpaperManager.FLAG_LOCK)
                val home = manager.wallpaperInfo?.component?.flattenToString()
                val lock = if (Build.VERSION.SDK_INT >= 34)
                    manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.component?.flattenToString()
                else null
                if (homeId == manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM) &&
                    lockId == manager.getWallpaperId(WallpaperManager.FLAG_LOCK))
                    return@runCatching WallpaperApplyState(homeId, lockId, home, lock)
            }
            null
        }.getOrNull()
    }
}

object WallpaperApplyDestination {
    /**
     * LivePicker returns RESULT_OK without destination extras or onApplyWallpaper(). AOSP assigns
     * a fresh wallpaper ID on apply. Home-only may move the previous home ID to lock, so compare
     * against BOTH old IDs. A missing separate lock ID means lock shares the home wallpaper.
     */
    fun resolve(before: WallpaperApplyState, after: WallpaperApplyState, component: String): Int {
        val previousIds = setOf(before.homeId, before.lockId)
        val homeApplied = after.homeId > 0 && after.homeId !in previousIds && after.homeComponent == component
        val lockApplied = after.lockId > 0 && after.lockId !in previousIds && after.lockComponent == component
        var flags = if (homeApplied) 1 else 0
        if (lockApplied || (homeApplied && after.lockId < 0)) flags = flags or 2
        return flags
    }
}
