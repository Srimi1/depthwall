package dev.srimi.depthwall.standby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.OrientationEventListener
import androidx.core.content.ContextCompat
import dev.srimi.depthwall.data.StandbyPolicy

data class StandbyDeviceState(
    val powered: Boolean = false,
    val batteryPercent: Int? = null,
    val landscape: Boolean? = null,
)

/** Only owned by a visible activity or an active dream; never a background service. */
class StandbyDeviceMonitor(
    private val context: Context,
    private val onChange: (StandbyDeviceState) -> Unit,
    private val onScreenOff: () -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var state = StandbyDeviceState()
    private var candidate: Boolean? = null
    private var pending: Runnable? = null
    private var sensorRunning = false
    private val naturalLandscape: Boolean
        get() {
            // A service's resource configuration can differ from its dream window's rotation.
            // The physical display mode stays in the panel's natural orientation in either host.
            val mode = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.mode
            return mode != null && mode.physicalWidth > mode.physicalHeight
        }
    private val orientation = object : OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
        override fun onOrientationChanged(degrees: Int) {
            if (started && state.powered) propose(StandbyPolicy.landscape(degrees, naturalLandscape, state.landscape))
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val powered = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    state = state.copy(powered = powered,
                        batteryPercent = if (level >= 0 && scale > 0) (100L * level / scale).toInt().coerceIn(0, 100) else null)
                    if (powered && !sensorRunning) {
                        sensorRunning = true
                        if (orientation.canDetectOrientation()) orientation.enable() else configurationOrientation()
                    } else if (!powered) {
                        orientation.disable(); sensorRunning = false
                        pending?.let(handler::removeCallbacks); pending = null; candidate = null
                        state = state.copy(landscape = null)
                    }
                    onChange(state)
                }
                Intent.ACTION_CONFIGURATION_CHANGED -> if (!orientation.canDetectOrientation()) configurationOrientation()
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        val sticky = ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(Intent.ACTION_SCREEN_OFF)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (sticky?.action == Intent.ACTION_BATTERY_CHANGED) receiver.onReceive(context, sticky)
    }

    private fun configurationOrientation() {
        propose(context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }

    private fun propose(landscape: Boolean) {
        if (candidate == landscape) return
        candidate = landscape
        pending?.let(handler::removeCallbacks)
        pending = Runnable {
            if (started && state.powered) {
                state = state.copy(landscape = landscape)
                onChange(state)
            }
        }.also { handler.postDelayed(it, 500) }
    }

    fun stop() {
        if (!started) return
        started = false
        orientation.disable(); sensorRunning = false
        pending?.let(handler::removeCallbacks); pending = null; candidate = null
        context.unregisterReceiver(receiver)
    }
}
