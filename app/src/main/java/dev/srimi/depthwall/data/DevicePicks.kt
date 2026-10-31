package dev.srimi.depthwall.data

import org.json.JSONArray

/**
 * Explore's "Best for your phone" row. The ordered ids live in an asset rather than Room:
 * seeding never updates existing rows, and the row mixes still, depth and live wallpapers.
 */
object DevicePicks {
    const val ASSET = "device_picks.json"

    fun parse(json: String): List<String> {
        val array = JSONArray(json)
        return (0 until array.length()).map { array.getString(it) }.distinct()
    }

    /** Keeps the curated order and skips ids that are missing from this install. */
    fun resolve(ids: List<String>, items: List<WallpaperItem>): List<WallpaperItem> {
        val byId = items.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }
}
