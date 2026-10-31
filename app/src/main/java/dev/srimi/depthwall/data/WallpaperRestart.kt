package dev.srimi.depthwall.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Applying the active live wallpaper again does not restart it: Android only sends the engine a
 * reapply command. A stuck third-party engine is restarted by replacing it first, which unbinds
 * the service, so the next apply binds a fresh engine.
 */
object WallpaperRestart {
    private const val PORT_PREFIX = "com.miui.miwallpaper."

    /** Screens that currently show [component]. A shared lock (no separate lock ID) follows home. */
    fun destinations(state: WallpaperApplyState, component: String): Int {
        var flags = 0
        if (state.homeComponent == component) flags = flags or WallpaperManager.FLAG_SYSTEM
        if (state.lockComponent == component || (state.lockId < 0 && state.homeComponent == component))
            flags = flags or WallpaperManager.FLAG_LOCK
        return flags
    }

    /** Two scenes of one port package share a process; starting both together crashes Unity. */
    fun sharesPortProcess(state: WallpaperApplyState): Boolean {
        val home = state.homeComponent?.substringBefore('/') ?: return false
        val lock = state.lockComponent?.substringBefore('/') ?: return false
        return state.lockId >= 0 && home == lock && home.startsWith(PORT_PREFIX) &&
            state.homeComponent != state.lockComponent
    }

    /** Replaces [component] with plain black on the screens it occupies and waits for Android to unbind it. */
    suspend fun release(context: Context, component: String): Boolean = withContext(Dispatchers.IO) {
        val before = WallpaperApplyState.capture(context) ?: error("Cannot read the current wallpaper.")
        val which = destinations(before, component)
        if (which == 0) return@withContext true
        val black = Bitmap.createBitmap(108, 234, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        try { WallpaperManager.getInstance(context).setBitmap(black, null, true, which) }
        finally { black.recycle() }
        repeat(15) {
            val after = WallpaperApplyState.capture(context)
            if (after != null && destinations(after, component) == 0) return@withContext true
            delay(200)
        }
        false
    }
}
