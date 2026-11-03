package dev.srimi.depthwall

import dev.srimi.depthwall.data.AmbientWallpaper
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class AmbientWallpaperTest {
    @Test fun burnInShiftVisitsEightPointsOnASmallCircle() {
        val points = (0 until 8).map { AmbientWallpaper.burnInShift(it).toList() }.toSet()
        assertEquals(8, points.size)
        for (entry in -3..20) {
            val (x, y) = AmbientWallpaper.burnInShift(entry).toList()
            assertEquals(AmbientWallpaper.SHIFT_RADIUS.toDouble(), hypot(x.toDouble(), y.toDouble()), 1e-6)
        }
        assertArrayEquals(AmbientWallpaper.burnInShift(1), AmbientWallpaper.burnInShift(9), 0f)
    }

    @Test fun wakingEasesFromTheAodPoseToTheLockOrbit() {
        assertEquals(1f, AmbientWallpaper.exitLevel(0, 600), 0f)
        assertEquals(.5f, AmbientWallpaper.exitLevel(300, 600), .001f)
        assertEquals(0f, AmbientWallpaper.exitLevel(600, 600), 0f)
        assertEquals(0f, AmbientWallpaper.exitLevel(60_000, 600), 0f)
        // An engine that never left AOD measures from 0 and is long settled.
        assertEquals(0f, AmbientWallpaper.exitLevel(5_000_000, AmbientWallpaper.DEFAULT_EXIT_MS), 0f)
        assertEquals(0f, AmbientWallpaper.exitLevel(10, 0), 0f)
        val samples = (0..600 step 50).map { AmbientWallpaper.exitLevel(it.toLong(), 600) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b <= a })
    }

    @Test fun systemUiOverrideDecidesBeforeTheFrameworkFlag() {
        // The optional SystemUI overlay sets 1, for ROMs whose framework flag is false.
        assertEquals(true, AmbientWallpaper.effectiveAodWallpaperFlag(1, false))
        assertEquals(false, AmbientWallpaper.effectiveAodWallpaperFlag(0, true))
        // -1 (SystemUI's default), other values or an unreadable override defer to the framework.
        assertEquals(false, AmbientWallpaper.effectiveAodWallpaperFlag(-1, false))
        assertEquals(true, AmbientWallpaper.effectiveAodWallpaperFlag(-1, true))
        assertEquals(true, AmbientWallpaper.effectiveAodWallpaperFlag(2, true))
        assertEquals(false, AmbientWallpaper.effectiveAodWallpaperFlag(null, false))
        assertNull(AmbientWallpaper.effectiveAodWallpaperFlag(null, null))
    }
}
