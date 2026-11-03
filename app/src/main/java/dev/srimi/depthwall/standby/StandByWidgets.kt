package dev.srimi.depthwall.standby

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import dev.srimi.depthwall.clock.ClockFaces
import dev.srimi.depthwall.clock.ClockFaces.alpha
import dev.srimi.depthwall.clock.ClockFaces.centered
import dev.srimi.depthwall.clock.ClockMath
import dev.srimi.depthwall.clock.ClockStyle
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.min

/** StandBy's right-hand stack. Nothing here needs a permission: the data comes from the system. */
object StandByWidgets {
    data class Battery(val percent: Int, val charging: Boolean, val full: Boolean, val watts: Float?)

    fun calendar(canvas: Canvas, bounds: RectF, time: ZonedDateTime, style: ClockStyle) {
        val locale = Locale.getDefault()
        val title = ClockFaces.pen(style.accent, ClockFaces.medium).apply { letterSpacing = .2f }
        val month = time.month.getDisplayName(TextStyle.FULL, locale).uppercase(locale)
        ClockFaces.fit(title, month, bounds.width() * .6f, bounds.height() * .06f)
        canvas.centered(month, bounds.centerX(), bounds.top + bounds.height() * .1f, title)
        val first = WeekFields.of(locale).firstDayOfWeek
        val cellW = bounds.width() * .9f / 7
        val left = bounds.centerX() - cellW * 3.5f
        val days = ClockFaces.pen(style.dim, ClockFaces.medium).apply { textSize = min(cellW, bounds.height() * .1f) * .38f }
        for (i in 0 until 7) {
            val label = DayOfWeek.of(Math.floorMod(first.value - 1 + i, 7) + 1).getDisplayName(TextStyle.NARROW, locale)
            canvas.centered(label, left + cellW * (i + .5f), bounds.top + bounds.height() * .23f, days)
        }
        val start = time.withDayOfMonth(1).toLocalDate()
        val lead = Math.floorMod(start.dayOfWeek.value - first.value, 7)
        val count = start.lengthOfMonth()
        val rows = (lead + count + 6) / 7
        val cellH = bounds.height() * .66f / rows
        val numbers = ClockFaces.pen(style.ink, ClockFaces.light).apply { textSize = min(cellW, cellH) * .44f }
        val today = ClockFaces.pen(android.graphics.Color.BLACK, ClockFaces.medium).apply { textSize = numbers.textSize }
        for (day in 1..count) {
            val index = lead + day - 1
            val x = left + cellW * (index % 7 + .5f)
            val y = bounds.top + bounds.height() * .31f + cellH * (index / 7 + .5f)
            if (day == time.dayOfMonth) {
                canvas.drawCircle(x, y, min(cellW, cellH) * .42f, ClockFaces.pen(style.accent))
                canvas.centered(day.toString(), x, y, today)
            } else canvas.centered(day.toString(), x, y, numbers)
        }
    }

    fun battery(canvas: Canvas, bounds: RectF, battery: Battery?, style: ClockStyle) {
        val r = min(bounds.width(), bounds.height()) * .3f
        val cx = bounds.centerX(); val cy = bounds.top + bounds.height() * .42f
        canvas.drawCircle(cx, cy, r, ClockFaces.stroke(style.dim.alpha(.6f), r * .1f))
        val level = battery?.percent ?: 0
        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, -90f, 3.6f * level, false, ClockFaces.stroke(style.accent, r * .1f))
        val value = ClockFaces.pen(style.ink, ClockFaces.light)
        val label = if (battery == null) "—" else "$level%"
        ClockFaces.fit(value, label, r * 1.3f, r * .5f)
        canvas.centered(label, cx, cy, value)
        if (battery?.charging == true) {
            // A small lightning bolt above the percentage.
            val s = r * .16f
            val bolt = Path().apply {
                moveTo(cx + s * .2f, cy - r * .62f - s); lineTo(cx - s * .55f, cy - r * .62f + s * .15f); lineTo(cx, cy - r * .62f + s * .15f)
                lineTo(cx - s * .2f, cy - r * .62f + s); lineTo(cx + s * .55f, cy - r * .62f - s * .15f); lineTo(cx, cy - r * .62f - s * .15f); close()
            }
            canvas.drawPath(bolt, ClockFaces.pen(style.accent))
        }
        val status = when {
            battery == null -> "Battery unavailable"
            battery.full -> "Charged"
            battery.charging -> battery.watts?.let { "Charging · %.1f W".format(it) } ?: "Charging"
            else -> "On battery"
        }
        val text = ClockFaces.pen(style.dim, ClockFaces.medium)
        ClockFaces.fit(text, status, bounds.width() * .8f, bounds.height() * .06f)
        canvas.centered(status, cx, bounds.top + bounds.height() * .86f, text)
    }

    fun alarm(canvas: Canvas, bounds: RectF, next: ZonedDateTime?, now: ZonedDateTime, style: ClockStyle) {
        val heading = ClockFaces.pen(style.dim, ClockFaces.medium).apply { letterSpacing = .3f }
        ClockFaces.fit(heading, "NEXT ALARM", bounds.width() * .6f, bounds.height() * .05f)
        canvas.centered("NEXT ALARM", bounds.centerX(), bounds.top + bounds.height() * .2f, heading)
        val big = ClockFaces.pen(style.ink, ClockFaces.light)
        if (next == null) {
            ClockFaces.fit(big, "No alarm", bounds.width() * .7f, bounds.height() * .12f)
            canvas.centered("No alarm", bounds.centerX(), bounds.centerY(), big)
            return
        }
        val time = ClockMath.hours(next, style.is24h) + ":" + ClockMath.minutes(next)
        ClockFaces.fit(big, time, bounds.width() * .8f, bounds.height() * .26f)
        canvas.centered(time, bounds.centerX(), bounds.top + bounds.height() * .5f, big)
        val days = (next.toLocalDate().toEpochDay() - now.toLocalDate().toEpochDay()).toInt()
        val day = when (days) { 0 -> "Today"; 1 -> "Tomorrow"; else -> ClockFaces.date(next, "EEEE d MMM") } +
            if (style.is24h) "" else " · " + ClockMath.meridiem(next)
        val sub = ClockFaces.pen(style.accent, ClockFaces.medium)
        ClockFaces.fit(sub, day, bounds.width() * .7f, bounds.height() * .06f)
        canvas.centered(day, bounds.centerX(), bounds.top + bounds.height() * .76f, sub)
    }

    fun world(canvas: Canvas, bounds: RectF, time: ZonedDateTime, style: ClockStyle) {
        // Two cities fit a half-width card; the full World face shows up to four.
        ClockFaces.World.draw(canvas, bounds, time, style.copy(zones = style.zones.take(2)))
    }

    fun card(canvas: Canvas, bounds: RectF, style: ClockStyle) {
        if (!style.night) canvas.drawRoundRect(bounds, bounds.height() * .07f, bounds.height() * .07f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B0B0B.toInt() })
    }
}
