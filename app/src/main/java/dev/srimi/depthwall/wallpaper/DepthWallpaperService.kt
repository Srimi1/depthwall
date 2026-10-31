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
import kotlinx.coroutines.flow.catch
import java.io.FileDescriptor
import java.io.IOException
import java.io.PrintWriter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class DepthWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = DepthEngine()

    inner class DepthEngine : Engine(), SensorEventListener {
        private val thread = HandlerThread("DepthWall-render").apply { start() }
        private val handler = Handler(thread.looper)
        private val main = Handler(Looper.getMainLooper())
        // A settings failure must degrade to defaults; a crash here counts toward Android's
        // two-deaths-in-10-seconds rule that permanently resets the wallpaper.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, error -> lastError = error.message.orEmpty(); Log.e("DepthWall", "Wallpaper settings", error) })
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
        @Volatile private var lastDrawnAt = 0L
        @Volatile private var surfaceGeneration = 0
        @Volatile private var stalled = false
        @Volatile private var rendererAttempts = 0
        // Render-thread state: the renderer and its failure budget are only touched on [handler].
        private var renderer: WindowRenderer? = null
        private var drawFailures = 0
        private var keyguardChecks = 0
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
        // After a reboot the engine binds while the keyguard is still going away. Re-read it a
        // few times instead of relying only on USER_PRESENT, so terrain does not stay in orbit.
        private val recheckKeyguard = object : Runnable {
            override fun run() {
                if (unlocked || !isVisible) return
                updateDeviceState()
                if (unlocked) { updateSensors(); schedule() }
                else if (++keyguardChecks < 3) main.postDelayed(this, 1000L * keyguardChecks)
            }
        }
        private fun watchKeyguard() {
            main.removeCallbacks(recheckKeyguard)
            if (unlocked) return
            keyguardChecks = 0
            main.postDelayed(recheckKeyguard, 1000)
        }
        /** Creates EGL for the current surface, retrying with backoff instead of staying black. */
        private val createRenderer = object : Runnable {
            override fun run() {
                val generation = surfaceGeneration
                if (!surfaceReady) return
                renderer?.close(); renderer = null
                try {
                    renderer = WindowRenderer(this@DepthWallpaperService, surfaceHolder.surface)
                    stalled = false
                    schedule()
                } catch (error: Throwable) {
                    lastError = error.message.orEmpty(); Log.e("DepthWall", "Renderer init", error)
                    if (!surfaceReady || generation != surfaceGeneration) return
                    if (rendererAttempts < RendererRecovery.MAX_ATTEMPTS)
                        handler.postDelayed(this, RendererRecovery.retryDelayMs(rendererAttempts++))
                    else stalled = true
                }
            }
        }
        private val rebuildRenderer = Runnable {
            rendererAttempts = 0; drawFailures = 0
            createRenderer.run()
        }
        private fun requestRebuild() {
            handler.removeCallbacks(createRenderer); handler.removeCallbacks(rebuildRenderer)
            handler.post(rebuildRenderer)
        }
        // Android only sends this command when the same wallpaper is applied again, so a
        // re-apply from the picker can revive an engine that lost its surface or gave up.
        private val reapply = Runnable {
            if (renderer == null || stalled) rebuildRenderer.run()
            else { drawFailures = 0; schedule() }
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
            watchKeyguard()
            stopObservingScene = repo.scenes.listenForAppliedChanges {
                if (!isPreview) { readConfig(); updateSensors(); schedule() }
            }
            scope.launch {
                repo.settings.flow.catch { if (it is IOException) emit(AppSettings()) else throw it }
                    .collect { quality = it.quality; updateSensors(); schedule() }
            }
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
            surfaceGeneration++
            readConfig(); surfaceReady = true; updateDeviceState(); updateSensors()
            requestRebuild()
        }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w; height = h; schedule()
        }
        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) { super.onSurfaceRedrawNeeded(holder); schedule() }
        override fun onVisibilityChanged(visible: Boolean) {
            updateDeviceState(visible)
            if (rendering) readConfig()
            if (visible) watchKeyguard()
            // A renderer that gave up gets a fresh budget each time the wallpaper is shown again.
            if (visible && stalled && surfaceReady) requestRebuild()
            updateSensors(); schedule()
        }
        override fun onOffsetsChanged(x: Float, y: Float, xStep: Float, yStep: Float, xPixels: Int, yPixels: Int) {
            mx = (x - .5f) * .6f
            requestMotionFrame()
        }
        override fun onWallpaperFlagsChanged(which: Int) {
            super.onWallpaperFlagsChanged(which); readConfig(); updateSensors(); schedule()
        }
        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (!isPreview && action == RendererRecovery.COMMAND_REAPPLY) {
                readConfig(); updateDeviceState(); updateSensors()
                handler.post(reapply)
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
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
                val active = renderer ?: return
                try {
                    active.draw(current, width, height, (now - start) / 1000f, progress,
                        drawnX, drawnY, RenderBudget.renderWidth(quality, saver, hot))
                    frames++; lastDrawnAt = SystemClock.elapsedRealtime()
                    drawFailures = 0; rendererAttempts = 0
                } catch (error: Throwable) {
                    lastError = error.message.orEmpty(); Log.e("DepthWall", "Wallpaper frame failed", error)
                    when (RendererRecovery.onFailure(error is SurfaceLostException, drawFailures++)) {
                        // Retrying the same dead EGL surface would never draw again.
                        RendererRecovery.Action.REBUILD -> {
                            active.close(); renderer = null
                            if (surfaceReady) handler.postDelayed(createRenderer, RendererRecovery.retryDelayMs(rendererAttempts++))
                        }
                        RendererRecovery.Action.RETRY_FRAME -> if (rendering) handler.postDelayed(this, 2000)
                        RendererRecovery.Action.GIVE_UP -> stalled = true
                    }
                    return
                }
                val delay = if (fps > 0) (1000L / fps - (SystemClock.elapsedRealtime() - now)).coerceAtLeast(1)
                    else if (current.clock != "Off") 60000 - System.currentTimeMillis() % 60000 else 0
                if (rendering && delay > 0) handler.postDelayed(this, delay)
            }
        }
        override fun onComputeColors() = android.app.WallpaperColors(Color.valueOf(Color.BLACK), Color.valueOf(0xFFFF870A.toInt()), null)
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false; surfaceGeneration++
            handler.removeCallbacks(frame); handler.removeCallbacks(createRenderer); handler.removeCallbacks(rebuildRenderer)
            stopSensors()
            // Release the EGL surface before Android releases the Surface behind it.
            val released = CountDownLatch(1)
            if (handler.post { try { renderer?.close(); renderer = null } finally { released.countDown() } })
                runCatching { released.await(500, TimeUnit.MILLISECONDS) }
            super.onSurfaceDestroyed(holder)
        }
        override fun onDestroy() {
            rendering = false; surfaceReady = false; handler.removeCallbacks(frame); stopSensors()
            handler.removeCallbacks(createRenderer); handler.removeCallbacks(rebuildRenderer); handler.removeCallbacks(reapply)
            main.removeCallbacks(recheckKeyguard)
            stopObservingScene(); runCatching { unregisterReceiver(events) }
            runCatching { unregisterReceiver(userPresent) }
            power.removeThermalStatusListener(thermal); displays.unregisterDisplayListener(displayListener); scope.cancel()
            handler.post { renderer?.close(); renderer = null; thread.quitSafely() }
            super.onDestroy()
        }
        override fun dump(prefix: String, fd: FileDescriptor, out: PrintWriter, args: Array<out String>?) {
            super.dump(prefix, fd, out, args)
            out.println("${prefix}DepthWall visible=$rendering engineVisible=$isVisible interactive=$interactive displayId=$displayId displayState=${DisplayRenderPolicy.stateLabel(displayState)} frames=$frames quality=$quality scene=${config.scene} surface=$surfaceReady sensors=$sensorListening idleOnDemand=$motionWakesFrame unlocked=$unlocked progress=$progress userPresentEvents=$userPresentEvents rendererAttempts=$rendererAttempts stalled=$stalled surfaceGeneration=$surfaceGeneration msSinceLastFrame=${if (lastDrawnAt == 0L) -1 else SystemClock.elapsedRealtime() - lastDrawnAt} error=$lastError")
        }
    }
}
