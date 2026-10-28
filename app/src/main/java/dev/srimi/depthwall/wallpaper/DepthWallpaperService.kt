package dev.srimi.depthwall.wallpaper

import android.app.KeyguardManager
import android.app.wallpaper.WallpaperDescription
import android.content.*
import android.graphics.Color
import android.hardware.*
import android.hardware.display.DisplayManager
import android.os.*
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Display
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import dev.srimi.depthwall.DepthWallApp
import dev.srimi.depthwall.data.*
import kotlinx.coroutines.*
import java.io.FileDescriptor
import java.io.PrintWriter
import kotlin.math.abs

class DepthWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = DepthEngine()

    inner class DepthEngine : Engine(), SensorEventListener {
        private val thread = HandlerThread("DepthWall-render").apply { start() }
        private val handler = Handler(thread.looper)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val repo = (application as DepthWallApp).repository
        private val power = getSystemService(PowerManager::class.java)
        private val keyguard = getSystemService(KeyguardManager::class.java)
        private val sensors = getSystemService(SensorManager::class.java)
        private val displays = getSystemService(DisplayManager::class.java)
        @Volatile private var rendering = false
        @Volatile private var interactive = false
        @Volatile private var displayId = Display.INVALID_DISPLAY
        @Volatile private var displayState = Display.STATE_UNKNOWN
        @Volatile private var surfaceReady = false
        @Volatile private var width = 1
        @Volatile private var height = 1
        @Volatile private var quality = "Balanced"
        @Volatile private var config = SceneConfig()
        @Volatile private var saver = false
        @Volatile private var hot = false
        @Volatile private var unlocked = false
        @Volatile private var userPresentEvents = 0
        @Volatile private var refresh = 60f
        @Volatile private var mx = 0f
        @Volatile private var my = 0f
        @Volatile private var drawnX = 0f
        @Volatile private var drawnY = 0f
        @Volatile private var motionWakesFrame = false
        @Volatile private var lastFrameAt = 0L
        @Volatile private var sensorListening = false
        @Volatile private var frames = 0L
        @Volatile private var lastError = "none"
        private var renderer: WindowRenderer? = null
        private val start = SystemClock.elapsedRealtime()
        private var transitionAt = start
        private var lastUnlocked = false
        @Volatile private var progress = 0f
        private var transitionFrom = 0f
        private var stopObservingScene: () -> Unit = {}

        private val events = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    interactive = false
                    rendering = false
                    unlocked = false
                    handler.removeCallbacks(frame)
                    stopSensors()
                    handler.post { progress = 0f; transitionFrom = 0f; lastUnlocked = false }
                } else {
                    if (intent?.action == Intent.ACTION_TIME_CHANGED || intent?.action == Intent.ACTION_TIMEZONE_CHANGED) {
                        handler.post { renderer?.invalidateClock() }
                    }
                    updateDeviceState()
                    updateSensors()
                    schedule()
                }
            }
        }
        // SystemUI uses its own UID. NOT_EXPORTED drops its unlock broadcast, leaving the
        // cached state locked indefinitely. USER_PRESENT is a framework protected broadcast;
        // ordinary applications cannot send it, and this receiver accepts only that action.
        private val userPresent = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_USER_PRESENT) return
                userPresentEvents++
                updateDeviceState()
                unlocked = true
                updateSensors(); schedule()
            }
        }
        private val thermal = PowerManager.OnThermalStatusChangedListener {
            updateDeviceState(); updateSensors(); schedule()
        }
        private val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) = onTargetDisplayChanged(id)
            override fun onDisplayRemoved(id: Int) = onTargetDisplayChanged(id)
            override fun onDisplayChanged(id: Int) = onTargetDisplayChanged(id)
        }
        private fun onTargetDisplayChanged(id: Int) {
            if (id != displayId) return
            // A display can enter AOD while a different display keeps the device interactive.
            updateDeviceState(); updateSensors(); schedule()
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(false)
            displayId = displayContext?.display?.displayId ?: Display.INVALID_DISPLAY
            ContextCompat.registerReceiver(this@DepthWallpaperService, events, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            ContextCompat.registerReceiver(this@DepthWallpaperService, userPresent,
                IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_EXPORTED)
            power.addThermalStatusListener(thermal)
            displays.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
            updateDeviceState()
            stopObservingScene = repo.scenes.listenForAppliedChanges {
                if (!isPreview) { readConfig(); updateSensors(); schedule() }
            }
            scope.launch { repo.settings.flow.collect { quality = it.quality; updateSensors(); schedule() } }
        }
        private fun updateDeviceState(visible: Boolean = isVisible) {
            val targetDisplay = displays.getDisplay(displayId)
            displayState = targetDisplay?.state ?: Display.STATE_UNKNOWN
            refresh = targetDisplay?.refreshRate ?: 60f
            interactive = power.isInteractive
            saver = power.isPowerSaveMode
            hot = power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
            unlocked = !keyguard.isKeyguardLocked
            rendering = DisplayRenderPolicy.canRender(visible, interactive, displayState)
        }
        private fun readConfig() {
            val flags = if (Build.VERSION.SDK_INT >= 34) wallpaperFlags else 1
            config = if (isPreview) repo.scenes.pending() else repo.scenes.active(flags)
        }
        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            readConfig(); surfaceReady = true; updateDeviceState(); updateSensors()
            handler.post {
                runCatching { renderer?.close(); renderer = WindowRenderer(this@DepthWallpaperService, holder.surface) }
                    .onFailure { lastError = it.message.orEmpty(); Log.e("DepthWall", "Renderer init", it) }
                schedule()
            }
        }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w; height = h; schedule()
        }
        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) { super.onSurfaceRedrawNeeded(holder); schedule() }
        override fun onVisibilityChanged(visible: Boolean) {
            updateDeviceState(visible)
            if (rendering) readConfig()
            updateSensors(); schedule()
        }
        override fun onOffsetsChanged(x: Float, y: Float, xStep: Float, yStep: Float, xPixels: Int, yPixels: Int) {
            mx = (x - .5f) * .6f
            requestMotionFrame()
        }
        override fun onWallpaperFlagsChanged(which: Int) {
            super.onWallpaperFlagsChanged(which); readConfig(); updateSensors(); schedule()
        }
        override fun onApplyWallpaper(which: Int): WallpaperDescription? {
            repo.scenes.confirmApplied(config, which)
            return super.onApplyWallpaper(which)
        }
        private fun stopSensors() { sensors.unregisterListener(this); sensorListening = false }
        private fun updateSensors() {
            stopSensors()
            if (rendering && surfaceReady && quality != "Battery" && !saver && !hot && config.parallax > 0f) {
                sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                    sensorListening = sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                }
            }
        }
        override fun onSensorChanged(event: SensorEvent) {
            mx = mx * .9f + (-event.values[0] / 9.8f).coerceIn(-1f, 1f) * .1f
            my = my * .9f + (event.values[1] / 9.8f - .5f).coerceIn(-1f, 1f) * .1f
            if (motionWakesFrame && abs(mx - drawnX) + abs(my - drawnY) > .004f) requestMotionFrame()
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        private fun requestMotionFrame() {
            if (!rendering || !surfaceReady) return
            handler.removeCallbacks(frame)
            handler.postDelayed(frame, (66 - (SystemClock.elapsedRealtime() - lastFrameAt)).coerceAtLeast(0))
        }
        private fun schedule() {
            handler.removeCallbacks(frame)
            if (rendering && surfaceReady) handler.post(frame)
        }
        private val frame = object : Runnable {
            override fun run() {
                if (!rendering || !surfaceReady) return
                handler.removeCallbacks(this)
                val now = SystemClock.elapsedRealtime()
                lastFrameAt = now
                val current = config
                if (unlocked != lastUnlocked) {
                    lastUnlocked = unlocked; transitionAt = now; transitionFrom = progress
                }
                val target = if (unlocked) 1f else 0f
                progress = SceneTransition.progress(transitionFrom, target, now - transitionAt)
                val changing = current.scene != "depth" && progress != target
                val continuous = RenderBudget.hasContinuousMotion(current.scene, progress)
                val fps = if (!continuous && !changing) 0 else RenderBudget.fps(quality, changing, true, saver, hot, refresh)
                motionWakesFrame = fps == 0
                drawnX = mx; drawnY = my
                try {
                    renderer?.draw(current, width, height, (now - start) / 1000f, progress,
                        drawnX, drawnY, RenderBudget.renderWidth(quality, saver, hot))
                    frames++
                } catch (error: Exception) {
                    lastError = error.message.orEmpty(); Log.e("DepthWall", "Wallpaper frame failed", error)
                    if (rendering) handler.postDelayed(this, 2000)
                    return
                }
                val delay = if (fps > 0) (1000L / fps - (SystemClock.elapsedRealtime() - now)).coerceAtLeast(1)
                    else if (current.clock != "Off") 60000 - System.currentTimeMillis() % 60000 else 0
                if (rendering && delay > 0) handler.postDelayed(this, delay)
            }
        }
        override fun onComputeColors() = android.app.WallpaperColors(Color.valueOf(Color.BLACK), Color.valueOf(0xFFFF870A.toInt()), null)
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false; handler.removeCallbacks(frame); stopSensors()
            handler.post { renderer?.close(); renderer = null }
            super.onSurfaceDestroyed(holder)
        }
        override fun onDestroy() {
            rendering = false; surfaceReady = false; handler.removeCallbacks(frame); stopSensors()
            stopObservingScene(); runCatching { unregisterReceiver(events) }
            runCatching { unregisterReceiver(userPresent) }
            power.removeThermalStatusListener(thermal); displays.unregisterDisplayListener(displayListener); scope.cancel()
            handler.post { renderer?.close(); renderer = null; thread.quitSafely() }
            super.onDestroy()
        }
        override fun dump(prefix: String, fd: FileDescriptor, out: PrintWriter, args: Array<out String>?) {
            super.dump(prefix, fd, out, args)
            out.println("${prefix}DepthWall visible=$rendering engineVisible=$isVisible interactive=$interactive displayId=$displayId displayState=${DisplayRenderPolicy.stateLabel(displayState)} frames=$frames quality=$quality scene=${config.scene} surface=$surfaceReady sensors=$sensorListening idleOnDemand=$motionWakesFrame unlocked=$unlocked progress=$progress userPresentEvents=$userPresentEvents error=$lastError")
        }
    }
}
