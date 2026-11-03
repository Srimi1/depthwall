package dev.srimi.depthwall

import dev.srimi.depthwall.clock.ClockFaces
import dev.srimi.depthwall.clock.ClockMath
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ClockMathTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(hour: Int, minute: Int, second: Int = 0, nanos: Int = 0) = ZonedDateTime.of(2026, 11, 2, hour, minute, second, nanos, zone)

    @Test fun twelveAndTwentyFourHourText() {
        assertEquals("12", ClockMath.hours(at(0, 5), false))
        assertEquals("00", ClockMath.hours(at(0, 5), true))
        assertEquals("9", ClockMath.hours(at(21, 41), false))
        assertEquals("21", ClockMath.hours(at(21, 41), true))
        assertEquals("05", ClockMath.minutes(at(7, 5)))
        assertEquals("PM", ClockMath.meridiem(at(12, 0)))
        assertEquals("0941", ClockMath.digits(at(21, 41), false))
        assertEquals("2141", ClockMath.digits(at(21, 41), true))
    }

    @Test fun redrawWaitsForTheNextVisibleChange() {
        assertEquals(500L, ClockMath.untilNext(at(9, 0, 10, 500_000_000), true))
        assertEquals(49_500L, ClockMath.untilNext(at(9, 0, 10, 500_000_000), false))
    }

    @Test fun flipAnimatesOnlyAtTheStartOfAMinute() {
        assertEquals(0f, ClockMath.flipProgress(at(9, 1, 0, 0), true), 0f)
        assertEquals(.5f, ClockMath.flipProgress(at(9, 1, 0, 300_000_000), true), .001f)
        assertEquals(1f, ClockMath.flipProgress(at(9, 1, 0, 900_000_000), true), 0f)
        assertEquals(1f, ClockMath.flipProgress(at(9, 1, 1, 0), true), 0f)
        // Minute textures never catch a flip half-way.
        assertEquals(1f, ClockMath.flipProgress(at(9, 1, 0, 0), false), 0f)
    }

    @Test fun wordClockRoundsDownToFiveMinutesAndCountsTheRest() {
        assertEquals(listOf("IT IS", "HALF", "PAST", "TEN"), ClockMath.words(at(10, 30)).lines)
        assertEquals(listOf("IT IS", "TWENTY FIVE", "TO", "ELEVEN"), ClockMath.words(at(10, 35)).lines)
        assertEquals(listOf("IT IS", "QUARTER", "PAST", "TWO"), ClockMath.words(at(14, 17)).lines)
        assertEquals(2, ClockMath.words(at(14, 17)).extraMinutes)
        val noon = ClockMath.words(at(11, 58))
        assertEquals(listOf("IT IS", "FIVE", "TO", "TWELVE"), noon.lines)
        assertEquals(3, noon.extraMinutes)
        assertEquals(listOf("IT IS", "ELEVEN", "O’CLOCK"), ClockMath.words(at(23, 0)).lines)
        assertEquals(listOf("IT IS", "TWELVE", "O’CLOCK"), ClockMath.words(at(0, 0)).lines)
    }

    @Test fun sunCrossesTheDayArcAndTheMoonTheNight() {
        assertEquals(ClockMath.Sun(0f, true), ClockMath.sun(at(6, 0)))
        assertEquals(.5f, ClockMath.sun(at(12, 0)).fraction, .001f)
        assertTrue(ClockMath.sun(at(17, 59)).day)
        assertEquals(ClockMath.Sun(0f, false), ClockMath.sun(at(18, 0)))
        assertEquals(.5f, ClockMath.sun(at(0, 0)).fraction, .001f)
        assertFalse(ClockMath.sun(at(5, 59)).day)
    }

    @Test fun gaugeSweepsTwoHundredSeventyDegreesOverTwelveHours() {
        assertEquals(135f, ClockMath.gaugeAngle(at(0, 0)), 0f)
        assertEquals(135f, ClockMath.gaugeAngle(at(12, 0)), 0f)
        assertEquals(270f, ClockMath.gaugeAngle(at(6, 0)), .001f)
        assertEquals(135f + 270f * 10.5f / 12, ClockMath.gaugeAngle(at(22, 30)), .001f)
    }

    @Test fun worldClockNamesCitiesAndDayOffsets() {
        val late = at(23, 30)
        val world = ClockMath.world(late, listOf("Asia/Tokyo", "America/New_York", "Not/AZone", "Asia/Kolkata"))
        assertEquals(listOf("Tokyo", "New York", "Kolkata"), world.map { it.city })
        assertEquals(1, world[0].dayOffset)
        assertEquals("+3h30", world[0].offset)
        assertEquals(0, world[1].dayOffset)
        assertEquals("Same time", world[2].offset)
        assertEquals("−4h", ClockMath.offsetLabel(-240))
        assertEquals("Tomorrow", ClockMath.dayLabel(1))
        assertEquals("Los Angeles", ClockMath.cityName("America/Los_Angeles"))
    }

    @Test fun ledGlyphsAreFiveBySevenDots() {
        for (digit in '0'..'9') {
            val glyph = ClockMath.GLYPHS.getValue(digit)
            assertEquals(7, glyph.size)
            assertTrue(glyph.all { it.length == 5 && it.all { c -> c == '#' || c == '.' } })
        }
        assertEquals(7, ClockMath.GLYPHS.getValue(':').size)
    }

    @Test fun everyFaceIsReachableAndTheWallpaperSkipsSceneOnlyFaces() {
        assertEquals(12, ClockFaces.all.map { it.id }.toSet().size)
        assertEquals("Off", ClockFaces.wallpaperChoices.first())
        assertTrue(listOf("Digital", "Minimal", "Analogue").all { it in ClockFaces.wallpaperChoices })
        assertFalse("Scene" in ClockFaces.wallpaperChoices)
        assertTrue("Scene" in ClockFaces.standByIds)
    }
}
