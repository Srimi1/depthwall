package dev.srimi.depthwall.data

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Decisions behind StandBy's layout, night mode and AMOLED care. Pure, so they are unit tested. */
object StandByPolicy {
    enum class Layout { LANDSCAPE, PORTRAIT }
    enum class Page { CLOCK, SCENE, WIDGETS }
    enum class Widget { CALENDAR, BATTERY, ALARM, WORLD }
    /** How long the light level must hold before night mode switches. */
    const val NIGHT_HOLD_MS = 10_000L

    /**
     * Snaps an OrientationEventListener reading to 0/90/180/270. Readings more than 30° from a
     * quarter turn, or flat on a table (negative), keep [previous] so the layout does not flicker.
     */
    fun snap(degrees: Int, previous: Int): Int {
        if (degrees < 0) return previous
        val nearest = (degrees + 45) / 90 % 4 * 90
        val distance = abs(degrees - nearest).let { minOf(it, 360 - it) }
        return if (distance <= 30) nearest else previous
    }

    fun layout(snapped: Int): Layout = if (snapped == 90 || snapped == 270) Layout.LANDSCAPE else Layout.PORTRAIT

    /**
     * Clockwise quarter turns that keep content upright. The listener reports 90 when the left side
     * is up; a window the system already rotated (auto-rotate on) cancels the turn out.
     */
    fun turns(snapped: Int, displayRotationDegrees: Int): Int = Math.floorMod(-snapped - displayRotationDegrees, 360) / 90

    /** Maps a swipe on the panel into the rotated content's own axes. */
    fun toContent(dx: Float, dy: Float, turns: Int): Pair<Float, Float> {
        var x = dx; var y = dy
        repeat(Math.floorMod(turns, 4)) { val t = x; x = y; y = -t }
        return x to y
    }

    /** Moves everything a few pixels each minute around a small circle, against OLED burn-in. */
    fun burnInShift(minute: Long, radiusPx: Float): FloatArray {
        val angle = Math.floorMod(minute, 8L) * PI / 4
        return floatArrayOf((cos(angle) * radiusPx).toFloat(), (sin(angle) * radiusPx).toFloat())
    }

    fun dimmed(idleMs: Long, dimAfterMinutes: Int): Boolean = dimAfterMinutes > 0 && idleMs >= dimAfterMinutes * 60_000L

    fun night(mode: String, detected: Boolean): Boolean = when (mode) { "Always" -> true; "Off" -> false; else -> detected }

    /**
     * Charging power from BatteryManager's CURRENT_NOW and the battery intent's voltage. Some
     * kernels report mA rather than µA and either sign; implausible values are dropped.
     */
    fun chargingWatts(currentNow: Int, voltageMv: Int): Float? {
        if (currentNow == Int.MIN_VALUE || currentNow == 0 || voltageMv <= 0) return null
        val amps = abs(currentNow.toLong()).let { if (it > 20_000) it / 1_000_000f else it / 1_000f }
        val watts = amps * voltageMv / 1000f
        return watts.takeIf { it in .5f..150f }
    }

    /** Low light keeps night mode only once it is steady, and leaves it only when clearly brighter. */
    class NightDetector(private val enterLux: Float = 3f, private val exitLux: Float = 8f, private val holdMs: Long = NIGHT_HOLD_MS) {
        var night = false
            private set
        private var candidateSince = -1L

        fun update(lux: Float, nowMs: Long): Boolean {
            val wanted = if (night) lux < exitLux else lux < enterLux
            if (wanted == night) { candidateSince = -1; return night }
            if (candidateSince < 0) candidateSince = nowMs
            if (nowMs - candidateSince >= holdMs) { night = wanted; candidateSince = -1 }
            return night
        }
    }
}
