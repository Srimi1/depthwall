package dev.srimi.depthwall.data

import kotlin.math.roundToInt

/** Portrait export sizes. Small source crops retain their detail instead of being upscaled. */
object WallpaperSizing {
    data class Size(val width: Int, val height: Int)

    fun target(profile: String, screenWidth: Int, screenHeight: Int, fourK: Boolean = false): Size {
        val w = if (profile == "OnePlus 7T Pro") 1440 else screenWidth.coerceAtLeast(1)
        val h = if (profile == "OnePlus 7T Pro") 3120 else screenHeight.coerceAtLeast(1)
        val scale = if (fourK) minOf(2160f / w, 4680f / h) else minOf(1f, 2160f / w, 4680f / h)
        return Size((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
    }

    fun fitSource(cropWidth: Int, cropHeight: Int, target: Size): Size {
        require(cropWidth > 0 && cropHeight > 0)
        val scale = minOf(1f, target.width.toFloat() / cropWidth, target.height.toFloat() / cropHeight)
        return Size((cropWidth * scale).roundToInt().coerceAtLeast(1), (cropHeight * scale).roundToInt().coerceAtLeast(1))
    }
}
