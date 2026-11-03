package dev.srimi.depthwall.clock

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/** Time arithmetic shared by every clock face. Pure, so faces stay consistent and testable. */
object ClockMath {
    const val FLIP_MS = 600L
    private val HOURS = listOf("TWELVE", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN", "ELEVEN")
    private val MINUTES = listOf("", "FIVE", "TEN", "QUARTER", "TWENTY", "TWENTY FIVE", "HALF",
        "TWENTY FIVE", "TWENTY", "QUARTER", "TEN", "FIVE")

    /** Milliseconds until the displayed second (or minute) changes. */
    fun untilNext(time: ZonedDateTime, seconds: Boolean): Long {
        val ms = time.nano / 1_000_000L
        return if (seconds) 1000L - ms else 60_000L - (time.second * 1000L + ms)
    }

    fun hours(time: ZonedDateTime, is24h: Boolean): String =
        if (is24h) "%02d".format(time.hour) else ((time.hour + 11) % 12 + 1).toString()

    fun minutes(time: ZonedDateTime): String = "%02d".format(time.minute)

    fun meridiem(time: ZonedDateTime): String = if (time.hour < 12) "AM" else "PM"

    /** Four flip-card digits: always two-digit hours, like a mechanical clock. */
    fun digits(time: ZonedDateTime, is24h: Boolean): String =
        "%02d".format(if (is24h) time.hour else (time.hour + 11) % 12 + 1) + minutes(time)

    /** Flip animation progress at the start of each minute; 1 when settled or animation is off. */
    fun flipProgress(time: ZonedDateTime, animate: Boolean): Float {
        if (!animate || time.second > 0) return 1f
        return ((time.nano / 1_000_000f) / FLIP_MS).coerceIn(0f, 1f)
    }

    data class WordTime(val lines: List<String>, val extraMinutes: Int)

    /** Five-minute word clock ("IT IS / HALF / PAST / TEN"); leftover minutes show as dots. */
    fun words(time: ZonedDateTime): WordTime {
        val five = time.minute / 5
        val hour = HOURS[(if (five >= 7) time.hour + 1 else time.hour) % 12]
        val lines = if (five == 0) listOf("IT IS", hour, "O’CLOCK")
            else listOf("IT IS", MINUTES[five], if (five <= 6) "PAST" else "TO", hour)
        return WordTime(lines, time.minute % 5)
    }

    data class Sun(val fraction: Float, val day: Boolean)

    /**
     * Position along the day arc (sunrise → sunset) or, at night, the moon's arc. A stylised
     * 06:00–18:00 day: DepthWall has no location permission to compute real sunrise.
     */
    fun sun(time: ZonedDateTime, sunrise: Int = 6 * 60, sunset: Int = 18 * 60): Sun {
        val minute = time.hour * 60 + time.minute + time.second / 60f
        return if (minute >= sunrise && minute < sunset) Sun((minute - sunrise) / (sunset - sunrise), true)
        else Sun(((minute - sunset + 1440) % 1440) / (1440f - (sunset - sunrise)), false)
    }

    /** Needle angle on a 270° tachometer dial that reads 0–12 hours, starting at 135°. */
    fun gaugeAngle(time: ZonedDateTime): Float =
        135f + 270f * ((time.hour % 12) + time.minute / 60f) / 12f

    fun cityName(zone: String): String = zone.substringAfterLast('/').replace('_', ' ')

    data class ZoneTime(val city: String, val time: ZonedDateTime, val dayOffset: Int, val offset: String)

    fun world(now: ZonedDateTime, zones: List<String>): List<ZoneTime> = zones.mapNotNull { id ->
        val zone = runCatching { ZoneId.of(id) }.getOrNull() ?: return@mapNotNull null
        val there = now.withZoneSameInstant(zone)
        ZoneTime(cityName(id), there, (there.toLocalDate().toEpochDay() - now.toLocalDate().toEpochDay()).toInt(),
            offsetLabel((there.offset.totalSeconds - now.offset.totalSeconds) / 60))
    }

    /** "+5h30", "−4h" or "Same time", relative to the phone's zone. */
    fun offsetLabel(minutes: Int): String {
        if (minutes == 0) return "Same time"
        val sign = if (minutes > 0) "+" else "−"
        val h = abs(minutes) / 60
        val m = abs(minutes) % 60
        return sign + h + "h" + if (m == 0) "" else "%02d".format(m)
    }

    fun dayLabel(offset: Int): String = when (offset) { 0 -> "Today"; 1 -> "Tomorrow"; -1 -> "Yesterday"; else -> "%+d days".format(offset) }

    /** 5 × 7 LED glyphs for the dot-matrix face. */
    val GLYPHS: Map<Char, List<String>> = mapOf(
        '0' to listOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
        '1' to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
        '2' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to listOf("#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."),
        '4' to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        '5' to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        '6' to listOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to listOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        '8' to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to listOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
        ':' to listOf(".", ".", "#", ".", "#", ".", "."),
        ' ' to listOf(".....", ".....", ".....", ".....", ".....", ".....", "....."),
    )
}
