package dev.srimi.depthwall.clock

import android.graphics.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Colours and options for one draw. Faces always draw onto true black. */
data class ClockStyle(
    val ink: Int = IVORY,
    val accent: Int = PAPAYA,
    val dim: Int = DIM,
    val is24h: Boolean = false,
    val showSeconds: Boolean = false,
    /** Off for minute-by-minute textures (the wallpaper clock), which must never catch a flip mid-way. */
    val animate: Boolean = true,
    val night: Boolean = false,
    val zones: List<String> = DEFAULT_ZONES,
) {
    /** A darkroom-red monochrome for a dark bedroom: nothing blue, nothing bright. */
    fun toNight(): ClockStyle = copy(ink = NIGHT_INK, accent = NIGHT_ACCENT, dim = NIGHT_DIM, night = true)

    companion object {
        const val IVORY = 0xFFF4EEE5.toInt()
        const val PAPAYA = 0xFFFF870A.toInt()
        const val DIM = 0xFF6E6A64.toInt()
        const val NIGHT_INK = 0xFFB3180F.toInt()
        const val NIGHT_ACCENT = 0xFF8A120B.toInt()
        const val NIGHT_DIM = 0xFF3F0804.toInt()
        val DEFAULT_ZONES = listOf("Europe/London", "America/New_York", "Asia/Tokyo")
    }
}

interface ClockFace {
    val id: String
    /** Faces that need a scene behind them are offered only in StandBy. */
    val standByOnly: Boolean get() = false
    fun draw(canvas: Canvas, bounds: RectF, time: ZonedDateTime, style: ClockStyle)
    /** Milliseconds until the face next looks different. */
    fun nextFrameMs(time: ZonedDateTime, style: ClockStyle): Long = ClockMath.untilNext(time, style.showSeconds)
}

/**
 * Every DepthWall clock. Faces allocate their paints per draw: they run on the wallpaper's render
 * thread and StandBy's UI thread at once, at most a few times a second outside a flip.
 */
object ClockFaces {
    val all: List<ClockFace> = listOf(Digital, Minimal, Analogue, Bold, FloatFace, Solar, World, Flip, Word, DotMatrix, Gauge, Scene)
    /** Choices for the wallpaper clock layer, including Off. */
    val wallpaperChoices: List<String> = listOf("Off") + all.filterNot { it.standByOnly }.map { it.id }
    val standByIds: List<String> = all.map { it.id }

    fun byId(id: String?): ClockFace? = all.firstOrNull { it.id == id }

    // --- Shared drawing helpers (also used by StandBy's widgets) ---------------------------

    internal val thin by lazy { Typeface.create("sans-serif-thin", Typeface.NORMAL) }
    internal val light by lazy { Typeface.create("sans-serif-light", Typeface.NORMAL) }
    internal val medium by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    internal val heavy by lazy { Typeface.create("sans-serif-black", Typeface.NORMAL) }
    internal val condensed by lazy { Typeface.create("sans-serif-condensed", Typeface.BOLD) }

    internal fun pen(color: Int, typeface: Typeface? = null, align: Paint.Align = Paint.Align.CENTER) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; this.typeface = typeface; textAlign = align }

    internal fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND
    }

    internal fun Int.alpha(fraction: Float): Int = Color.argb((Color.alpha(this) * fraction).toInt(), Color.red(this), Color.green(this), Color.blue(this))

    /** Sizes [paint] so [text]'s glyphs fit inside maxW × maxH. */
    internal fun fit(paint: Paint, text: String, maxW: Float, maxH: Float) {
        paint.textSize = 100f
        val box = Rect().also { paint.getTextBounds(text, 0, text.length, it) }
        val scale = min(maxW / paint.measureText(text).coerceAtLeast(1f), maxH / box.height().coerceAtLeast(1))
        paint.textSize = 100f * scale
    }

    /** Draws [text] with its glyphs (not its line box) centred vertically on [cy]. */
    internal fun Canvas.centered(text: String, x: Float, cy: Float, paint: Paint) {
        val box = Rect().also { paint.getTextBounds(text, 0, text.length, it) }
        drawText(text, x, cy - box.exactCenterY(), paint)
    }

    internal fun date(time: ZonedDateTime, pattern: String = "EEE d MMM") =
        time.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

    internal fun clockText(time: ZonedDateTime, style: ClockStyle) = ClockMath.hours(time, style.is24h) + ":" + ClockMath.minutes(time)

    private fun hand(canvas: Canvas, cx: Float, cy: Float, angle: Double, length: Float, paint: Paint, tail: Float = 0f) {
        val dx = sin(angle).toFloat(); val dy = -cos(angle).toFloat()
        canvas.drawLine(cx - dx * tail, cy - dy * tail, cx + dx * length, cy + dy * length, paint)
    }

    // --- Faces ------------------------------------------------------------------------------

    object Digital : ClockFace {
        override val id = "Digital"
        override fun draw(canvas: Canvas, bounds: RectF, time: ZonedDateTime, style: ClockStyle) {
            val text = clockText(time, style)
            val p = pen(style.ink, light)
            fit(p, text, bounds.width() * .9f, bounds.height() * .5f)
            val cy = bounds.top + bounds.height() * .4f
            canvas.centered(text, bounds.centerX(), cy, p)
            if (style.showSeconds) {
                val s = pen(style.accent, medium, Paint.Align.LEFT).apply { textSize = p.textSize * .22f }
                canvas.drawText(":" + "%02d".format(time.second), bounds.centerX() + p.measureText(text) / 2 + p.textSize * .04f,
                    cy - p.textSize * .2f, s)
            }
            val d = pen(style.ink.alpha(.7f), medium)
            fit(d, date(time), bounds.width() * .7f, bounds.height() * .08f)
            canvas.centered(date(time), bounds.centerX(), bounds.top + bounds.height() * .82f, d)
        }
    }

    object Minimal : ClockFace {
        override val id = "Minimal"
        override fun draw(canvas: Canvas, bounds: RectF, time: ZonedDateTime, style: ClockStyle) {
            val p = pen(style.ink, thin)
            val text = clockText(time, style)
            fit(p, text, bounds.width() * .7f, bounds.height() * .34f)
            canvas.centered(text, bounds.centerX(), bounds.top + bounds.height() * .42f, p)
            val d = pen(style.dim, medium).apply { letterSpacing = .25f }
            val label = date(time, "EEEE · d MMMM").uppercase(Locale.getDefault())
            fit(d, label, bounds.width() * .8f, bounds.height() * .05f)
            canvas.centered(label, bounds.centerX(), bounds.top + bounds.height() * .72f, d)
        }
    }

    object Analogue : ClockFace {
        override val id = "Analogue"
        override fun draw(canvas: Canvas, bounds: RectF, time: ZonedDateTime, style: ClockStyle) {
            val r = min(bounds.width(), bounds.height()) * .46f
            val cx = bounds.centerX(); val cy = bounds.centerY()
            for (i in 0 until 60) {
                val major = i % 5 == 0
                val a = i * Math.PI / 30
                val tick = stroke(if (major) style.ink else style.dim, r * if (major) .022f else .01f)
                val inner = r * if (major) .9f else .94f
                canvas.drawLine(cx + sin(a).toFloat() * inner, cy - cos(a).toFloat() * inner, cx + sin(a).toFloat() * r, cy - cos(a).toFloat() * r, tick)
            }
            val numerals = pen(style.ink.alpha(.85f), light).apply { textSize = r * .15f }
            for (n in 1..12) {
                val a = n * Math.PI / 6
                canvas.centered(n.toString(), cx + sin(a).toFloat() * r * .74f, cy - cos(a).toFloat() * r * .74f, numerals)
            }
            val minute = time.minute + time.second / 60.0
            hand(canvas, cx, cy, ((time.hour % 12) + minute / 60) * Math.PI / 6, r * .5f, stroke(style.ink, r * .055f))
            hand(canvas, cx, cy, minute * Math.PI / 30, r * .78f, stroke(style.ink, r * .035f))
            if (style.showSeconds) hand(canvas, cx, cy, time.second * Math.PI / 30, r * .86f, stroke(style.accent, r * .012f), r * .14f)
            canvas.drawCircle(cx, cy, r * .045f, pen(style.accent))
        }
    }

    /** StandBy's hero face: hours and minutes as tall as the panel allows. */
    object Bold : ClockFace {
        override val id = "Bold"
        override fun draw(canvas: Canvas, bounds: RectF, time: ZonedDateTime, style: ClockStyle) {
            pair(canvas, bounds, ClockMath.hours(time, style.is24h), ClockMath.minutes(time), pen(style.ink, heavy), pen(style.accent, heavy))
        }
        fun pair(canvas: Canvas, bounds: RectF, left: String, right: String, a: Paint, b: Paint, height: Float = .78f) {
            fit(a, left + " " + right, bounds.width() * .94f, bounds.height() * height)
            b.textSize = a.textSize
            val gap = a.textSize * .12f
            val total = a.measureText(left) + gap + b.measureText(right)
            val x = bounds.centerX() - total / 2
            a.textAlign = Paint.Align.LEFT; b.textAlign = Paint.Align.LEFT
            canvas.centered(left, x, bounds.centerY(), a)
            canvas.centered(right, x + a.measureText(left) + gap, bounds.centerY(), b)
        }
    }

    /** Soft, rounded numerals in warm McLaren tones that drift a little each minute. */
    object FloatFace : ClockFace {
        override val id = "Float"
        private val palette = intArrayOf(0xFFFF870A.toInt(), 0xFFFFB547.toInt(), 0xFFFF6B5A.toInt(), 0xFFFFD08A.toInt())
        override fun draw(canvas: Canvas, bounds: RectF, time: ZonedDateTime, style: ClockStyle) {
            val digits = ClockMath.hours(time, style.is24h) + ClockMath.minutes(time)
            val p = pen(style.ink, heavy, Paint.Align.LEFT).apply {
                this.style = Paint.Style.FILL_AND_STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
            }
            fit(p, "$digits ", bounds.width() * .86f, bounds.height() * .66f)
            p.strokeWidth = p.textSize * .07f
            val gap = p.textSize * .22f
            val split = digits.length - 2
            var x = bounds.centerX() - (p.measureText(digits) + gap) / 2
            digits.forEachIndexed { i, c ->
                if (i == split) x += gap
                p.color = if (style.night) style.ink else palette[i % palette.size]
                val drift = sin(i * 1.7 + time.minute * .45).toFloat() * bounds.height() * .035f
                canvas.centered(c.toString(), x, bounds.centerY() + drift, p)
                x += p.measureText(c.toString())
            }
        }
    }

    /** The sun's place on its day arc, or the moon's at night, above a horizon line. */
    object Solar : ClockFace {
        override val id = "Solar"
        override fun draw(canvas: Canvas, bounds: RectF, time: ZonedDateTime, style: ClockStyle) {
            val horizon = bounds.top + bounds.height() * .66f
            val r = min(bounds.width() * .4f, bounds.height() * .54f)
            val cx = bounds.centerX()
            canvas.drawLine(bounds.left + bounds.width() * .04f, horizon, bounds.right - bounds.width() * .04f, horizon, stroke(style.dim, 2f))
            val arc = stroke(style.dim, 2f).apply { pathEffect = DashPathEffect(floatArrayOf(r * .02f, r * .04f), 0f) }
            canvas.drawArc(cx - r, horizon - r, cx + r, horizon + r, 180f, 180f, false, arc)
            val sun = ClockMath.sun(time)
            val angle = Math.toRadians(180.0 + 180.0 * sun.fraction)
            val x = cx + cos(angle).toFloat() * r
            val y = horizon + sin(angle).toFloat() * r
            val size = bounds.height() * .05f
            if (sun.day) {
                val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(x, y, size * 3.2f, style.accent.alpha(.45f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                }
                canvas.drawCircle(x, y, size * 3.2f, glow)
                canvas.drawCircle(x, y, size, pen(style.accent))
            } else {
                // A crescent cut from the disc, so nothing dark is painted over a scene behind it.
                val moon = Path().apply { addCircle(x, y, size, Path.Direction.CW) }
                moon.op(Path().apply { addCircle(x + size * .45f, y - size * .2f, size * .9f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
                canvas.drawPath(moon, pen(style.ink.alpha(.8f)))
            }
            val t = pen(style.ink, light, Paint.Align.LEFT)
            fit(t, clockText(time, style), bounds.width() * .4f, bounds.height() * .14f)
            canvas.centered(clockText(time, style), bounds.left + bounds.width() * .05f, horizon + bounds.height() * .17f, t)
            val d = pen(style.dim, medium, Paint.Align.RIGHT)
            fit(d, date(time), bounds.width() * .3f, bounds.height() * .05f)
            canvas.centered(date(time), bounds.right - bounds.width() * .05f, horizon + bounds.height() * .17f, d)
        }
    }

    object World : ClockFace {
        override val id = "World"
        override fun draw(canvas: Canvas, bounds: RectF, time: ZonedDateTime, style: ClockStyle) {
            val zones = ClockMath.world(time, style.zones.take(4)).ifEmpty { ClockMath.world(time, ClockStyle.DEFAULT_ZONES) }
            val column = bounds.width() / zones.size
            zones.forEachIndexed { i, zone ->
                val cx = bounds.left + column * (i + .5f)
                val r = min(column * .3f, bounds.height() * .2f)
                val cy = bounds.top + bounds.height() * .3f
                val day = zone.time.hour in 6..17
                // Daytime dials are softly filled; night dials stay as outlines on black.
                if (day) canvas.drawCircle(cx, cy, r, pen(style.ink.alpha(.14f)))
                canvas.drawCircle(cx, cy, r, stroke(style.dim, r * .04f))
                val minute = zone.time.minute.toDouble()
                hand(canvas, cx, cy, ((zone.time.hour % 12) + minute / 60) * Math.PI / 6, r * .5f, stroke(style.ink, r * .08f))
                hand(canvas, cx, cy, minute * Math.PI / 30, r * .78f, stroke(style.ink, r * .05f))
                canvas.drawCircle(cx, cy, r * .07f, pen(style.accent))
                val name = pen(style.ink, medium)
                fit(name, zone.city, column * .86f, bounds.height() * .07f)
                canvas.centered(zone.city, cx, bounds.top + bounds.height() * .62f, name)
                val t = pen(style.ink, light)
                val label = ClockMath.hours(zone.time, style.is24h) + ":" + ClockMath.minutes(zone.time)
                fit(t, label, column * .8f, bounds.height() * .11f)
                canvas.centered(label, cx, bounds.top + bounds.height() * .76f, t)
                val sub = pen(style.dim, medium)
                val note = ClockMath.dayLabel(zone.dayOffset) + " · " + zone.offset
                fit(sub, note, column * .9f, bounds.height() * .045f)
                canvas.centered(note, cx, bounds.top + bounds.height() * .88f, sub)
            }
        }
    }

    /** Split-flap cards; the top flap falls over the first 600 ms of each minute. */
    object Flip : ClockFace {
        override val id = "Flip"
        override fun draw(canvas: Canvas, bounds: RectF, time: ZonedDateTime, style: ClockStyle) {
            val now = ClockMath.digits(time, style.is24h)
            val before = ClockMath.digits(time.minusMinutes(1), style.is24h)
            val progress = ClockMath.flipProgress(time, style.animate)
            val gap = bounds.width() * .04f
            val w = min((bounds.width() * .92f - gap) / 2, bounds.height() * .82f / 1.12f)
            val h = w * 1.12f
            val left = bounds.centerX() - w - gap / 2
            val top = bounds.centerY() - h / 2
            for (card in 0..1) {
                val rect = RectF(left + card * (w + gap), top, left + card * (w + gap) + w, top + h)
                val old = before.substring(card * 2, card * 2 + 2)
                val new = now.substring(card * 2, card * 2 + 2)
                if (progress >= 1f || old == new) { face(canvas, rect, new, style); continue }
                val hinge = rect.centerY()
                // Settled halves: the new top is revealed behind the falling flap, the old bottom waits.
                half(canvas, rect, hinge, true) { face(canvas, rect, new, style) }
                half(canvas, rect, hinge, false) { face(canvas, rect, old, style) }
                val fold = if (progress < .5f) 1f - progress * 2 else progress * 2 - 1f
                half(canvas, rect, hinge, progress < .5f) {
                    canvas.scale(1f, fold.coerceAtLeast(.001f), rect.centerX(), hinge)
                    face(canvas, rect, if (progress < .5f) old else new, style)
                    canvas.drawRect(rect, pen(Color.BLACK.alpha((1f - fold) * .55f)))
                }
            }
        }
        private inline fun half(canvas: Canvas, rect: RectF, hinge: Float, top: Boolean, block: () -> Unit) {
            canvas.save()
            canvas.clipRect(rect.left, if (top) rect.top else hinge, rect.right, if (top) hinge else rect.bottom)
            block()
            canvas.restore()
        }
        private fun face(canvas: Canvas, rect: RectF, digits: String, style: ClockStyle) {
            canvas.drawRoundRect(rect, rect.width() * .08f, rect.width() * .08f, pen(if (style.night) 0xFF1A0503.toInt() else 0xFF151515.toInt()))
            val p = pen(style.ink, condensed)
            fit(p, digits, rect.width() * .84f, rect.height() * .6f)
            canvas.centered(digits, rect.centerX(), rect.centerY(), p)
            canvas.drawRect(rect.left, rect.centerY() - rect.height() * .006f, rect.right, rect.centerY() + rect.height() * .006f, pen(Color.BLACK))
        }
        override fun nextFrameMs(time: ZonedDateTime, style: ClockStyle): Long =
            if (style.animate && ClockMath.flipProgress(time, true) < 1f) 16L else super.nextFrameMs(time, style)
    }

    object Word : ClockFace {
        override val id = "Word"
        override fun draw(canvas: Canvas, bounds: RectF, time: ZonedDateTime, style: ClockStyle) {
            val words = ClockMath.words(time)
            val p = pen(style.ink, heavy).apply { letterSpacing = .04f }
            val widest = words.lines.maxBy { it.length }
            val lineHeight = bounds.height() * .8f / words.lines.size
            fit(p, widest, bounds.width() * .88f, lineHeight * .72f)
            val start = bounds.centerY() - lineHeight * (words.lines.size - 1) / 2 - bounds.height() * .04f
            words.lines.forEachIndexed { i, line ->
                p.color = when {
                    i == words.lines.lastIndex && line != "O’CLOCK" -> style.accent
                    i == 0 || line == "PAST" || line == "TO" || line == "O’CLOCK" -> style.dim
                    else -> style.ink
                }
                // Measure caps only, so lines without descenders keep an even rhythm.
                canvas.centered(line, bounds.centerX(), start + i * lineHeight, p)
            }
            val dot = lineHeight * .06f
            val y = start + words.lines.size * lineHeight - lineHeight * .35f
            for (i in 0 until words.extraMinutes)
                canvas.drawCircle(bounds.centerX() + (i - (words.extraMinutes - 1) / 2f) * dot * 4, y, dot, pen(style.accent))
        }
    }

    /** LED-board digits; unlit dots are a faint trace so the grid reads without lighting the panel. */
    object DotMatrix : ClockFace {
        override val id = "Dot matrix"
        override fun draw(canvas: Canvas, bounds: RectF, time: ZonedDateTime, style: ClockStyle) {
            val text = clockText(time, style)
            val glyphs = text.map { ClockMath.GLYPHS.getValue(it) }
            val columns = glyphs.sumOf { it[0].length } + glyphs.size - 1
            val pitch = min(bounds.width() * .92f / columns, bounds.height() * .56f / 7)
            val radius = pitch * .38f
            var x = bounds.centerX() - columns * pitch / 2 + pitch / 2
            val y = bounds.top + bounds.height() * .42f - 3 * pitch
            val lit = pen(style.accent)
            val unlit = pen(style.accent.alpha(.09f))
            val blinkOff = style.showSeconds && time.second % 2 == 1
            glyphs.forEachIndexed { index, glyph ->
                for (row in 0 until 7) for (col in glyph[row].indices) {
                    val on = glyph[row][col] == '#' && !(text[index] == ':' && blinkOff)
                    canvas.drawCircle(x + col * pitch, y + row * pitch, radius, if (on) lit else unlit)
                }
                x += (glyph[0].length + 1) * pitch
            }
            val d = pen(style.dim, medium).apply { letterSpacing = .3f }
            val label = date(time, "EEE d MMM").uppercase(Locale.getDefault())
            fit(d, label, bounds.width() * .6f, bounds.height() * .055f)
            canvas.centered(label, bounds.centerX(), bounds.top + bounds.height() * .86f, d)
        }
    }

    /** A McLaren-style tachometer: the needle reads the hour, papaya redline from 10 to 12. */
    object Gauge : ClockFace {
        override val id = "Gauge"
        override fun draw(canvas: Canvas, bounds: RectF, time: ZonedDateTime, style: ClockStyle) {
            val r = min(bounds.width(), bounds.height()) * .46f
            val cx = bounds.centerX(); val cy = bounds.centerY() + r * .06f
            val oval = RectF(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(oval, 135f, 270f, false, stroke(style.dim, r * .035f))
            canvas.drawArc(oval, 135f + 270f * 10 / 12, 270f / 6, false, stroke(style.accent, r * .06f).apply { strokeCap = Paint.Cap.BUTT })
            val numerals = pen(style.ink, condensed).apply { textSize = r * .13f }
            for (step in 0..24) {
                val a = Math.toRadians(135.0 + 270.0 * step / 24)
                val major = step % 2 == 0
                val inner = r * if (major) .84f else .9f
                canvas.drawLine(cx + cos(a).toFloat() * inner, cy + sin(a).toFloat() * inner,
                    cx + cos(a).toFloat() * r * .96f, cy + sin(a).toFloat() * r * .96f,
                    stroke(if (step >= 20) style.accent else if (major) style.ink else style.dim, r * if (major) .022f else .012f))
                if (major) canvas.centered((step / 2).toString(), cx + cos(a).toFloat() * r * .68f, cy + sin(a).toFloat() * r * .68f, numerals)
            }
            if (style.showSeconds) canvas.drawArc(RectF(cx - r * 1.06f, cy - r * 1.06f, cx + r * 1.06f, cy + r * 1.06f),
                -90f, 6f * time.second, false, stroke(style.accent.alpha(.6f), r * .012f))
            val needle = Math.toRadians(ClockMath.gaugeAngle(time).toDouble())
            val tip = stroke(style.accent, r * .03f)
            canvas.drawLine(cx - cos(needle).toFloat() * r * .14f, cy - sin(needle).toFloat() * r * .14f,
                cx + cos(needle).toFloat() * r * .86f, cy + sin(needle).toFloat() * r * .86f, tip)
            canvas.drawCircle(cx, cy, r * .09f, pen(Color.BLACK))
            canvas.drawCircle(cx, cy, r * .09f, stroke(style.ink, r * .02f))
            val readout = pen(style.ink, condensed)
            fit(readout, clockText(time, style), r * .7f, r * .2f)
            canvas.centered(clockText(time, style), cx, cy + r * .45f, readout)
            val unit = pen(style.dim, medium).apply { letterSpacing = .3f; textSize = r * .07f }
            canvas.centered(if (style.is24h) "HRS" else ClockMath.meridiem(time), cx, cy + r * .66f, unit)
        }
    }

    /** Photo-style corner clock over a DepthWall scene (StandBy draws the scene behind it). */
    object Scene : ClockFace {
        override val id = "Scene"
        override val standByOnly = true
        override fun draw(canvas: Canvas, bounds: RectF, time: ZonedDateTime, style: ClockStyle) {
            val t = pen(style.ink, medium, Paint.Align.LEFT).apply { setShadowLayer(18f, 0f, 2f, Color.BLACK) }
            val text = clockText(time, style)
            fit(t, text, bounds.width() * .45f, bounds.height() * .2f)
            val x = bounds.left + bounds.width() * .05f
            val y = bounds.bottom - bounds.height() * .16f
            canvas.centered(text, x, y, t)
            val d = pen(style.ink.alpha(.8f), medium, Paint.Align.LEFT).apply { setShadowLayer(12f, 0f, 1f, Color.BLACK); letterSpacing = .2f }
            val label = date(time, "EEEE d MMMM").uppercase(Locale.getDefault())
            fit(d, label, bounds.width() * .45f, bounds.height() * .04f)
            canvas.centered(label, x, y - bounds.height() * .16f, d)
        }
    }
}
