package dev.srimi.depthwall

import dev.srimi.depthwall.data.WallpaperItem
import dev.srimi.depthwall.ui.Tabs
import dev.srimi.depthwall.ui.cardBadge
import org.junit.Assert.*
import org.junit.Test

class HomeLogicTest {
    @Test fun badgesMarkLiveDepthAndThePhoneCollection() {
        assertEquals("LIVE", cardBadge(WallpaperItem("scene_sun", "Sun", "Native scenes", kind = "NATIVE_SCENE")))
        assertEquals("DEPTH", cardBadge(WallpaperItem("depth_1", "Dunes", "Depth", kind = "LAYERED_DEPTH")))
        assertEquals("NEW", cardBadge(WallpaperItem("device_rise", "Rise", "Papaya Edition")))
        assertNull(cardBadge(WallpaperItem("wall_0_0", "Blue Hour", "Space")))
    }
    @Test fun everyTabHasItsOwnIndex() {
        val tabs = listOf(Tabs.HOME, Tabs.LIVE, Tabs.BROWSE, Tabs.DEPTH, Tabs.LIBRARY)
        assertEquals(listOf(0, 1, 2, 3, 4), tabs)
    }
}
