package dev.srimi.depthwall

import dev.srimi.depthwall.data.*
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class StandbyPolicyTest {
    @Test fun autoNightHasExactLocalBoundariesAndCrossesMidnight() {
        for (time in listOf("22:00", "23:59", "00:00", "06:59"))
            assertTrue(time, StandbyPolicy.isNight(StandbyNightMode.AUTO, LocalTime.parse(time)))
        for (time in listOf("07:00", "12:00", "21:59"))
            assertFalse(time, StandbyPolicy.isNight(StandbyNightMode.AUTO, LocalTime.parse(time)))
    }

    @Test fun manualNightOverridesSchedule() {
        for (hour in 0..23) {
            assertTrue(StandbyPolicy.isNight(StandbyNightMode.ON, LocalTime.of(hour, 0)))
            assertFalse(StandbyPolicy.isNight(StandbyNightMode.OFF, LocalTime.of(hour, 0)))
        }
    }

    @Test fun nightUsesLocalTimeAfterTimeZoneChanges() {
        val instant = ZonedDateTime.parse("2026-10-31T23:00:00Z")
        assertTrue(StandbyPolicy.isNight(StandbyNightMode.AUTO, instant.toLocalTime()))
        assertFalse(StandbyPolicy.isNight(StandbyNightMode.AUTO,
            instant.withZoneSameInstant(ZoneId.of("America/Los_Angeles")).toLocalTime()))
    }

    @Test fun entryRequiresPowerEnabledAndKnownLandscape() {
        val gate = StandbyEntryGate()
        assertFalse(gate.update(false, true, true))
        assertFalse(gate.update(true, false, true))
        assertFalse(gate.update(true, true, null))
        assertFalse(gate.update(true, true, false))
        assertTrue(gate.update(true, true, true))
    }

    @Test fun dismissalSurvivesMonitorRestartAndBatteryChanges() {
        val gate = StandbyEntryGate()
        assertTrue(gate.update(true, true, true))
        gate.dismiss()
        assertFalse(gate.update(true, true, null))
        repeat(3) { assertFalse(gate.update(true, true, true)) }
        gate.update(true, false, null)
        assertTrue(gate.update(true, true, true))
    }

    @Test fun turningUprightRearmsEntryAfterExit() {
        val gate = StandbyEntryGate()
        gate.dismiss()
        assertFalse(gate.update(true, true, false))
        assertTrue(gate.update(true, true, true))
    }

    @Test fun physicalOrientationIncludesBothLandscapeDirectionsAndFlatExit() {
        for (degrees in listOf(60, 90, 120, 240, 270, 300))
            assertTrue(StandbyPolicy.landscape(degrees, false, null))
        for (degrees in listOf(-1, 0, 30, 150, 180, 210, 330, 359))
            assertFalse(StandbyPolicy.landscape(degrees, false, true))
        assertTrue(StandbyPolicy.landscape(45, false, true))
        assertFalse(StandbyPolicy.landscape(45, false, false))
        assertFalse(StandbyPolicy.landscape(45, false, null))
    }

    @Test fun naturallyLandscapeDisplaysInvertThePhysicalOrientation() {
        assertTrue(StandbyPolicy.landscape(0, true, null))
        assertFalse(StandbyPolicy.landscape(90, true, null))
    }

    @Test fun defaultsAndUnrecognizedStoredEnumsRemainUsable() {
        val defaults = StandbySettings()
        assertFalse(defaults.enabled)
        assertEquals(StandbyDesign.DIGITAL, defaults.design)
        assertEquals(StandbyNightMode.AUTO, defaults.nightMode)
        assertNull(defaults.photoWallpaperId)
        assertEquals(StandbyDesign.DIGITAL, StandbyDesign.decode("removed_design"))
        assertEquals(StandbyNightMode.AUTO, StandbyNightMode.decode(null))
    }

    @Test fun swipingCyclesAllFiveDesignsInBothDirections() {
        assertEquals(5, StandbyDesign.entries.size)
        assertEquals(StandbyDesign.PHOTO, StandbyDesign.DIGITAL.next(-1))
        assertEquals(StandbyDesign.DIGITAL, StandbyDesign.PHOTO.next(1))
        assertEquals(StandbyDesign.ANALOGUE, StandbyDesign.FLIP.next(1))
    }

    @Test fun photoNeverFetchesRemoteImagesAndFallsBackForMissingFiles() {
        val remote = WallpaperItem("remote", "Online", "Space", remoteUrl = "https://example.com/image.jpg")
        assertEquals(StandbyPhoto.DEFAULT_MODEL, StandbyPhoto.model(remote))
        assertEquals(StandbyPhoto.DEFAULT_MODEL, StandbyPhoto.model(remote.copy(localPath = "/missing")) { false })
        assertEquals("/present", StandbyPhoto.model(remote.copy(localPath = "/present")) { true })
        assertEquals("file:///android_asset/wallpapers/test.webp",
            StandbyPhoto.model(remote.copy(localPath = "/missing", asset = "wallpapers/test.webp")) { false })
        assertEquals(StandbyPhoto.DEFAULT_MODEL, StandbyPhoto.model(remote.copy(kind = "NATIVE_SCENE", asset = "scene.webp")))
        assertEquals(StandbyPhoto.DEFAULT_MODEL, StandbyPhoto.model(null))
    }
}
