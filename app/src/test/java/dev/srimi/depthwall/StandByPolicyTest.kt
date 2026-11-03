package dev.srimi.depthwall

import dev.srimi.depthwall.data.StandByPolicy
import dev.srimi.depthwall.data.StandByPolicy.Layout
import dev.srimi.depthwall.data.StandBySettings
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class StandByPolicyTest {
    @Test fun orientationSnapsWithHysteresisAndIgnoresFlat() {
        assertEquals(90, StandByPolicy.snap(95, 0))
        assertEquals(270, StandByPolicy.snap(262, 0))
        assertEquals(0, StandByPolicy.snap(355, 90))
        // 45° is between quarter turns: keep what the phone was.
        assertEquals(90, StandByPolicy.snap(45, 90))
        assertEquals(0, StandByPolicy.snap(45, 0))
        assertEquals(270, StandByPolicy.snap(-1, 270))
        assertEquals(Layout.LANDSCAPE, StandByPolicy.layout(90))
        assertEquals(Layout.LANDSCAPE, StandByPolicy.layout(270))
        assertEquals(Layout.PORTRAIT, StandByPolicy.layout(180))
    }

    @Test fun contentTurnsUprightOnALockedOrRotatedWindow() {
        // Rotation locked: the window stays at 0, so the content turns.
        assertEquals(3, StandByPolicy.turns(90, 0))
        assertEquals(1, StandByPolicy.turns(270, 0))
        assertEquals(0, StandByPolicy.turns(0, 0))
        // Auto-rotate: the system picks ROTATION_270 for 90° and ROTATION_90 for 270°.
        assertEquals(0, StandByPolicy.turns(90, 270))
        assertEquals(0, StandByPolicy.turns(270, 90))
    }

    @Test fun swipesMapIntoTheTurnedContent() {
        assertEquals(10f to -5f, StandByPolicy.toContent(10f, -5f, 0))
        assertEquals(-5f to -10f, StandByPolicy.toContent(10f, -5f, 1))
        assertEquals(-10f to 5f, StandByPolicy.toContent(10f, -5f, 2))
        assertEquals(StandByPolicy.toContent(10f, -5f, 3), StandByPolicy.toContent(10f, -5f, -1))
    }

    @Test fun burnInShiftStaysWithinItsRadius() {
        val points = (0L until 8L).map { StandByPolicy.burnInShift(it, 8f).toList() }.toSet()
        assertEquals(8, points.size)
        for (minute in 0L..30L) {
            val (x, y) = StandByPolicy.burnInShift(minute, 8f).toList()
            assertEquals(8.0, hypot(x.toDouble(), y.toDouble()), 1e-4)
        }
    }

    @Test fun dimmingAndNightModes() {
        assertFalse(StandByPolicy.dimmed(29 * 60_000L, 30))
        assertTrue(StandByPolicy.dimmed(30 * 60_000L, 30))
        assertFalse(StandByPolicy.dimmed(Long.MAX_VALUE, 0))
        assertTrue(StandByPolicy.night("Always", false))
        assertFalse(StandByPolicy.night("Off", true))
        assertTrue(StandByPolicy.night("Auto", true))
    }

    @Test fun nightNeedsSteadyDarknessAndClearlyBrighterLightToLeave() {
        val detector = StandByPolicy.NightDetector()
        assertFalse(detector.update(1f, 0))
        assertFalse(detector.update(1f, 9_000))
        assertTrue(detector.update(1f, 10_000))
        // Between thresholds stays night; a brief bright flash does not end it.
        assertTrue(detector.update(5f, 11_000))
        assertTrue(detector.update(50f, 12_000))
        assertTrue(detector.update(1f, 13_000))
        assertTrue(detector.update(50f, 14_000))
        assertFalse(detector.update(50f, 24_000))
    }

    @Test fun chargingPowerHandlesMicroAndMilliampKernels() {
        assertEquals(19.2f, StandByPolicy.chargingWatts(4_800_000, 4000)!!, .01f)
        assertEquals(19.2f, StandByPolicy.chargingWatts(-4_800, 4000)!!, .01f)
        assertNull(StandByPolicy.chargingWatts(0, 4000))
        assertNull(StandByPolicy.chargingWatts(Int.MIN_VALUE, 4000))
        assertNull(StandByPolicy.chargingWatts(1_000_000, 0))
        assertNull(StandByPolicy.chargingWatts(50, 4000))
    }

    @Test fun standBySettingsRejectUnknownValues() {
        val clean = StandBySettings.of("Gauge", "Always", "mars", "Asia/Tokyo, Nowhere/City, Asia/Tokyo", true, false, 10)
        assertEquals(StandBySettings("Gauge", "Always", "mars", listOf("Asia/Tokyo"), true, false, 10), clean)
        val fallback = StandBySettings.of("Sundial", "Sometimes", "pluto", "", null, null, 7)
        assertEquals(StandBySettings(), fallback)
        val many = StandBySettings.of(null, null, null, StandBySettings.CITIES.joinToString(","), null, null, null)
        assertEquals(4, many.zones.size)
    }
}
