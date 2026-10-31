package dev.srimi.depthwall

import dev.srimi.depthwall.data.DevicePicks
import dev.srimi.depthwall.data.WallpaperItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BundledCatalogTest {
    private val assets = File("src/main/assets")
    private fun catalog(name: String): List<JSONObject> {
        val array = JSONArray(File(assets, name).readText())
        return (0 until array.length()).map { array.getJSONObject(it) }
    }
    private val all by lazy { listOf("catalog.json", "extra_catalog.json", "device_catalog.json").flatMap(::catalog) }
    private val picks by lazy { DevicePicks.parse(File(assets, DevicePicks.ASSET).readText()) }

    @Test fun idsAreUniqueAndEveryBundledFileExists() {
        val ids = all.map { it.getString("id") }
        assertEquals(ids.size, ids.toSet().size)
        for (entry in all) for (key in listOf("asset", "thumbnail", "foreground")) {
            val path = entry.optString(key)
            if (path.isNotEmpty()) assertTrue("$path is missing", File(assets, path).isFile)
        }
        assertEquals(64, all.count { it.getString("kind") == "STATIC" })
        assertEquals(12, all.count { it.getString("kind") == "LAYERED_DEPTH" })
        assertEquals(7, all.count { it.getString("kind") == "NATIVE_SCENE" })
    }

    @Test fun phoneCollectionUsesTheFullPanelRatioAtFourK() {
        val device = catalog("device_catalog.json")
        assertEquals(11, device.size)
        for (entry in device) {
            assertTrue(entry.getString("id").startsWith("device_"))
            assertEquals(2160, entry.getInt("width"))
            assertEquals(4680, entry.getInt("height"))
            assertEquals(1440f / 3120, entry.getInt("width").toFloat() / entry.getInt("height"), 1e-4f)
            assertEquals("Original artwork", entry.getString("license"))
        }
    }

    @Test fun picksResolveInOrderWithoutDuplicates() {
        assertTrue(picks.size in 8..12)
        val items = all.map { WallpaperItem(it.getString("id"), it.getString("title"), it.getString("category"), kind = it.getString("kind")) }
        assertEquals(picks, DevicePicks.resolve(picks, items).map { it.id })
        assertTrue(picks.any { id -> all.first { it.getString("id") == id }.getString("kind") == "NATIVE_SCENE" })
    }

    @Test fun resolveKeepsCuratedOrderAndSkipsUnknownIds() {
        val a = WallpaperItem("a", "A", "X"); val b = WallpaperItem("b", "B", "X")
        assertEquals(listOf(b, a), DevicePicks.resolve(listOf("b", "missing", "a"), listOf(a, b)))
        assertEquals(listOf("a", "b"), DevicePicks.parse("[\"a\",\"b\",\"a\"]"))
    }

    @Test fun bundledMetadataCarriesNoBrandNames() {
        val forbidden = listOf("mclaren", "oneplus", "xiaomi", "hyperos")
        for (entry in all) for (key in listOf("title", "category", "author", "license", "sourceUrl")) {
            val value = entry.optString(key).lowercase()
            assertTrue("$key of ${entry.getString("id")} names a brand", forbidden.none { it in value })
        }
    }
}
