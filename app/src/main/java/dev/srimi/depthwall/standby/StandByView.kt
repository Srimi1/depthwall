package dev.srimi.depthwall.standby

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import dev.srimi.depthwall.DepthWallApp
import dev.srimi.depthwall.clock.ClockFace
import dev.srimi.depthwall.clock.ClockFaces
import dev.srimi.depthwall.clock.ClockMath
import dev.srimi.depthwall.clock.ClockStyle
import dev.srimi.depthwall.data.SceneConfig
import dev.srimi.depthwall.data.StandByPolicy
import dev.srimi.depthwall.data.StandByPolicy.Layout
import dev.srimi.depthwall.data.StandByPolicy.Page
import dev.srimi.depthwall.data.StandByPolicy.Widget
import dev.srimi.depthwall.data.StandBySettings
import dev.srimi.depthwall.wallpaper.SceneView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.min

/**
 * StandBy, shared by the screen saver and the in-app activity. Landscape pages: clock, DepthWall
 * scene, widgets; portrait is a nightstand clock. Content draws on true black at most once a second
 * (16 ms only during a flip), rotates itself when the window stays portrait, and every sensor and
 * receiver lives strictly between [start] and [stop].
 */
@SuppressLint("ViewConstructor")
class StandByView(context: Context, private val listener: Listener) : FrameLayout(context) {
    interface Listener {
        fun onNightChanged(night: Boolean)
        fun onChargingChanged(charging: Boolean) = Unit
        /** Long press: leave StandBy. */
        fun onExit()
    }

    private val repo = (context.applicationContext as DepthWallApp).repository
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val batteries = context.getSystemService(BatteryManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val faces = ClockFaces.all.filterNot { it.standByOnly }
    private val nightDetector = StandByPolicy.NightDetector()
    private val board = Board(context)
    private var scope: CoroutineScope? = null
    private var started = false
    private var settings = StandBySettings()
    private var settingsLoaded = false
    private var face: ClockFace = ClockFaces.Bold
    private var page = Page.CLOCK
    private var widget = Widget.CALENDAR
    private var snapped = 0
    private var night = false
    private var lastTouch = SystemClock.elapsedRealtime()
    private var chromeUntil = 0L
    private var battery: StandByWidgets.Battery? = null
    private var nextAlarm: ZonedDateTime? = null
    private var scene: SceneView? = null

    init {
        setBackgroundColor(Color.BLACK)
        addView(board, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private val orientation = object : OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
        override fun onOrientationChanged(degrees: Int) {
            val next = StandByPolicy.snap(degrees, snapped)
            if (next != snapped) { snapped = next; syncScene(); board.invalidate() }
        }
    }
    private var lastLux = -1f
    // The light sensor reports only changes: a steady dark room sends one reading, so re-check
    // that reading once the hold time has passed instead of waiting for another event.
    private val recheckNight = Runnable {
        if (started && lastLux >= 0) { nightDetector.update(lastLux, SystemClock.elapsedRealtime()); applyNight() }
    }
    private val light = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            lastLux = event.values[0]
            nightDetector.update(lastLux, SystemClock.elapsedRealtime())
            applyNight()
            removeCallbacks(recheckNight)
            postDelayed(recheckNight, StandByPolicy.NIGHT_HOLD_MS + 250)
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    private val events = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> readBattery(intent)
                AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> readAlarm()
            }
            board.invalidate()
        }
    }

    /** [knownOrientation]: a snapped reading the caller already has (the screen saver's on-its-side check), if any. */
    fun start(knownOrientation: Int = StandByPolicy.UNKNOWN_ORIENTATION) {
        if (started) return
        started = true
        lastTouch = SystemClock.elapsedRealtime()
        // Until the sensor reports, use the caller's reading or assume the window's own rotation is upright.
        snapped = if (knownOrientation != StandByPolicy.UNKNOWN_ORIENTATION) knownOrientation else Math.floorMod(-(display?.rotation ?: 0) * 90, 360)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, events, filter, ContextCompat.RECEIVER_NOT_EXPORTED)?.let(::readBattery)
        readAlarm()
        if (orientation.canDetectOrientation()) orientation.enable()
        scope = MainScope().also { scope ->
            scope.launch {
                repo.settings.standBy.catch { if (it is IOException) emit(StandBySettings()) else throw it }.collect {
                    settings = it
                    // Later emissions are this view's own face saves; keep the face on screen.
                    if (!settingsLoaded) face = faces.firstOrNull { f -> f.id == it.face } ?: ClockFaces.Bold
                    settingsLoaded = true
                    syncLight(); applyNight(); syncScene(); board.invalidate()
                }
            }
        }
        syncScene(); board.invalidate()
    }

    fun stop() {
        if (!started) return
        started = false
        scope?.cancel(); scope = null
        runCatching { context.unregisterReceiver(events) }
        orientation.disable()
        sensors.unregisterListener(light)
        removeCallbacks(recheckNight)
        board.removeCallbacks(tick)
        syncScene()
    }

    private fun syncLight() {
        sensors.unregisterListener(light)
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (started && settings.nightMode == "Auto" && sensor != null)
            sensors.registerListener(light, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun applyNight() {
        val next = StandByPolicy.night(settings.nightMode, nightDetector.night)
        if (next == night) return
        night = next
        listener.onNightChanged(next)
        syncScene(); board.invalidate()
    }

    private fun readBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val charging = plugged && status != BatteryManager.BATTERY_STATUS_DISCHARGING
        val watts = if (charging) StandByPolicy.chargingWatts(
            batteries.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)) else null
        val previous = battery
        battery = if (level < 0) null else StandByWidgets.Battery(level * 100 / scale.coerceAtLeast(1), charging,
            status == BatteryManager.BATTERY_STATUS_FULL, watts)
        if (previous?.charging != charging) listener.onChargingChanged(charging)
    }

    private fun readAlarm() {
        nextAlarm = alarms.nextAlarmClock?.let { ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.triggerTime), ZoneId.systemDefault()) }
    }

    private fun layout(): Layout = StandByPolicy.layout(snapped)
    private fun turns(): Int = StandByPolicy.turns(snapped, (display?.rotation ?: 0) * 90)

    /** The scene page is landscape-only and steps aside for night mode's red monochrome. */
    private fun syncScene() {
        val wanted = started && layout() == Layout.LANDSCAPE && page == Page.SCENE && !night
        if (wanted && scene == null) {
            scene = SceneView(context).apply { maxFps = 30; maxRenderWidth = 720 }
                .also { addView(it, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
        } else if (!wanted && scene != null) {
            removeView(scene); scene = null
        }
        scene?.apply { config = SceneConfig(wallpaperId = "standby", scene = settings.scene); turns = turns() }
    }

    private fun style(): ClockStyle {
        val base = ClockStyle(is24h = DateFormat.is24HourFormat(context), showSeconds = settings.showSeconds, zones = settings.zones)
        return if (night) base.toNight() else base
    }

    private fun cycleFace(step: Int) {
        face = faces[Math.floorMod(faces.indexOf(face) + step, faces.size)]
        val id = face.id
        scope?.launch { runCatching { repo.settings.saveStandByFace(id) } }
    }

    private fun touched() {
        lastTouch = SystemClock.elapsedRealtime()
        chromeUntil = lastTouch + 3000
        board.invalidate()
    }

    private val tick = Runnable { board.invalidate() }

    private inner class Board(context: Context) : View(context) {
        private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean { touched(); return true }
            override fun onLongPress(e: MotionEvent) = listener.onExit()
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val (dx, dy) = StandByPolicy.toContent(vx, vy, turns())
                if (abs(dx) > abs(dy)) {
                    if (layout() == Layout.LANDSCAPE) {
                        page = Page.entries[Math.floorMod(page.ordinal + if (dx < 0) 1 else -1, Page.entries.size)]
                        syncScene()
                    } else cycleFace(if (dx < 0) 1 else -1)
                } else {
                    // On the widgets page the right half is the widget stack, the left half the clock.
                    val start = e1?.let { contentX(it) } ?: 0f
                    if (layout() == Layout.LANDSCAPE && page == Page.WIDGETS && start > logicalWidth() / 2)
                        widget = Widget.entries[Math.floorMod(widget.ordinal + if (dy < 0) 1 else -1, Widget.entries.size)]
                    else cycleFace(if (dy < 0) 1 else -1)
                }
                touched()
                return true
            }
        })

        private fun logicalWidth() = if (turns() % 2 == 1) height else width
        private fun logicalHeight() = if (turns() % 2 == 1) width else height
        private fun contentX(e: MotionEvent): Float =
            StandByPolicy.toContent(e.x - width / 2f, e.y - height / 2f, turns()).first + logicalWidth() / 2f

        @SuppressLint("ClickableViewAccessibility") // Swipes and long press only; there is nothing to click.
        override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event)

        override fun onDraw(canvas: Canvas) {
            val now = ZonedDateTime.now()
            val style = style()
            val turns = turns()
            val lw = logicalWidth().toFloat(); val lh = logicalHeight().toFloat()
            val showScene = scene != null
            if (!showScene) canvas.drawColor(Color.BLACK)
            canvas.save()
            canvas.translate(width / 2f, height / 2f)
            canvas.rotate(turns * 90f)
            canvas.translate(-lw / 2, -lh / 2)
            if (settings.burnInShift) {
                val shift = StandByPolicy.burnInShift(now.toEpochSecond() / 60, 8f * min(lw, lh) / 1440f)
                canvas.translate(shift[0], shift[1])
            }
            when {
                layout() == Layout.PORTRAIT -> portrait(canvas, lw, lh, now, style)
                page == Page.SCENE && showScene -> ClockFaces.Scene.draw(canvas, RectF(0f, 0f, lw, lh), now, style)
                page == Page.WIDGETS -> {
                    face.draw(canvas, RectF(lw * .02f, lh * .08f, lw * .5f, lh * .92f), now, style)
                    val card = RectF(lw * .53f, lh * .08f, lw * .97f, lh * .92f)
                    StandByWidgets.card(canvas, card, style)
                    when (widget) {
                        Widget.CALENDAR -> StandByWidgets.calendar(canvas, card, now, style)
                        Widget.BATTERY -> StandByWidgets.battery(canvas, card, battery, style)
                        Widget.ALARM -> StandByWidgets.alarm(canvas, card, nextAlarm, now, style)
                        Widget.WORLD -> StandByWidgets.world(canvas, card, now, style)
                    }
                }
                else -> face.draw(canvas, RectF(lw * .03f, lh * .06f, lw * .97f, lh * .94f), now, style)
            }
            if (SystemClock.elapsedRealtime() < chromeUntil && layout() == Layout.LANDSCAPE) dots(canvas, lw, lh, style)
            canvas.restore()
            // Dimming after a quiet spell covers the scene too, since it draws below this view.
            if (StandByPolicy.dimmed(SystemClock.elapsedRealtime() - lastTouch, settings.dimAfterMinutes))
                canvas.drawColor(Color.argb(153, 0, 0, 0))
            schedule(now, style)
        }

        private fun schedule(now: ZonedDateTime, style: ClockStyle) {
            removeCallbacks(tick)
            if (!started) return
            val untilMinute = 60_000L - (now.second * 1000L + now.nano / 1_000_000L)
            var delay = min(face.nextFrameMs(now, style), untilMinute)
            val chrome = chromeUntil - SystemClock.elapsedRealtime()
            if (chrome > 0) delay = min(delay, chrome)
            postDelayed(tick, delay.coerceAtLeast(16L))
        }

        private fun portrait(canvas: Canvas, lw: Float, lh: Float, now: ZonedDateTime, style: ClockStyle) {
            face.draw(canvas, RectF(0f, lh * .1f, lw, lh * .56f), now, style)
            val day = ClockFaces.pen(style.ink, ClockFaces.medium)
            val label = ClockFaces.date(now, "EEEE d MMMM")
            ClockFaces.fit(day, label, lw * .8f, lh * .028f)
            with(ClockFaces) { canvas.centered(label, lw / 2, lh * .66f, day) }
            val info = ClockFaces.pen(if (battery?.charging == true) style.accent else style.dim, ClockFaces.medium)
            val charge = battery?.let { b ->
                (if (b.full) "Charged" else if (b.charging) "Charging" else "Battery") + " · ${b.percent}%" +
                    (b.watts?.let { " · %.1f W".format(it) } ?: "")
            } ?: ""
            if (charge.isNotEmpty()) {
                ClockFaces.fit(info, charge, lw * .8f, lh * .02f)
                with(ClockFaces) { canvas.centered(charge, lw / 2, lh * .74f, info) }
            }
            nextAlarm?.let { alarm ->
                val text = "Alarm " + ClockMath.hours(alarm, style.is24h) + ":" + ClockMath.minutes(alarm) +
                    if (alarm.toLocalDate() == now.toLocalDate()) " today" else " · " + ClockFaces.date(alarm, "EEE")
                val pen = ClockFaces.pen(style.dim, ClockFaces.medium)
                ClockFaces.fit(pen, text, lw * .8f, lh * .02f)
                with(ClockFaces) { canvas.centered(text, lw / 2, lh * .8f, pen) }
            }
        }

        private fun dots(canvas: Canvas, lw: Float, lh: Float, style: ClockStyle) {
            val r = lh * .008f
            Page.entries.forEachIndexed { i, p ->
                canvas.drawCircle(lw / 2 + (i - 1) * r * 5, lh * .965f, r, ClockFaces.pen(if (p == page) style.accent else style.dim))
            }
        }
    }
}
