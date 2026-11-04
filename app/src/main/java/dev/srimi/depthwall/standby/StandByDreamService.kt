package dev.srimi.depthwall.standby

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.SensorManager
import android.os.SystemClock
import android.service.dreams.DreamService
import android.view.OrientationEventListener
import android.view.View
import android.view.WindowInsets
import dev.srimi.depthwall.DepthWallApp
import dev.srimi.depthwall.data.StandByPolicy
import dev.srimi.depthwall.data.StandByPolicy.Layout
import dev.srimi.depthwall.data.StandByPolicy.SideGate
import dev.srimi.depthwall.data.StandBySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * StandBy as Android's screen saver. With "While charging", crDroid starts it when the screen times
 * out on the charger and ends it when unplugged, so nothing of StandBy runs on battery.
 *
 * With StandBy's "Start by itself" setting (the default) it shows only while the phone is on its
 * side: the screen stays black for up to [StandByPolicy.SIDE_WAIT_MS] waiting for landscape, and
 * otherwise the screen saver ends so the phone sleeps as usual. Standing the phone upright again
 * closes StandBy after [StandByPolicy.UPRIGHT_LEAVE_MS]. With the setting off it behaves as before,
 * including the portrait clock.
 */
class StandByDreamService : DreamService(), StandByView.Listener {
    private var view: StandByView? = null
    private var scope: CoroutineScope? = null
    private var gate: OrientationEventListener? = null
    private var snapped = StandByPolicy.UNKNOWN_ORIENTATION
    private var sideOnly = true
    private var showing = false
    private var leavePending = false
    private var waitingSince = 0L

    private val sleepIfNotOnSide = Runnable {
        if (!showing && StandByPolicy.sideGate(snapped, SystemClock.elapsedRealtime() - waitingSince) == SideGate.SLEEP) finish()
    }
    private val leaveWhenUpright = Runnable {
        leavePending = false
        if (showing && sideOnly && StandByPolicy.layout(snapped) != Layout.LANDSCAPE) finish()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Until StandBy shows: dim, black, and a tap wakes the phone (a non-interactive dream does).
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        view = StandByView(this, this).apply { visibility = View.INVISIBLE }
        setContentView(view)
        window?.insetsController?.hide(WindowInsets.Type.systemBars())
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        val repo = (applicationContext as DepthWallApp).repository
        scope = MainScope().also { scope ->
            scope.launch {
                sideOnly = try { repo.settings.standBy.first().autoStart } catch (_: IOException) { StandBySettings().autoStart }
                if (sideOnly) waitForSide() else show()
            }
        }
    }

    private fun waitForSide() {
        val listener = object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(degrees: Int) = onOrientation(degrees)
        }
        // Without an accelerometer the side cannot be known: behave like an ordinary screen saver.
        if (!listener.canDetectOrientation()) { show(); return }
        gate = listener
        waitingSince = SystemClock.elapsedRealtime()
        listener.enable()
        view?.postDelayed(sleepIfNotOnSide, StandByPolicy.SIDE_WAIT_MS)
    }

    private fun onOrientation(degrees: Int) {
        snapped = StandByPolicy.snap(degrees, snapped)
        val view = view ?: return
        if (!showing) {
            if (StandByPolicy.sideGate(snapped, SystemClock.elapsedRealtime() - waitingSince) == SideGate.SHOW) show()
            return
        }
        if (StandByPolicy.layout(snapped) == Layout.LANDSCAPE) {
            if (leavePending) { view.removeCallbacks(leaveWhenUpright); leavePending = false }
        } else if (!leavePending) {
            leavePending = true
            view.postDelayed(leaveWhenUpright, StandByPolicy.UPRIGHT_LEAVE_MS)
        }
    }

    private fun show() {
        val view = view ?: return
        if (showing) return
        showing = true
        view.removeCallbacks(sleepIfNotOnSide)
        isInteractive = true
        isScreenBright = true
        view.visibility = View.VISIBLE
        view.start(snapped)
    }

    // Night mode lets the system drop to its dim dream brightness.
    override fun onNightChanged(night: Boolean) { if (showing) isScreenBright = !night }
    override fun onExit() = wakeUp()

    override fun onDreamingStopped() {
        release()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        release(); view = null
        super.onDetachedFromWindow()
    }

    private fun release() {
        scope?.cancel(); scope = null
        gate?.disable(); gate = null
        view?.apply { removeCallbacks(sleepIfNotOnSide); removeCallbacks(leaveWhenUpright); stop() }
        leavePending = false
        showing = false
        snapped = StandByPolicy.UNKNOWN_ORIENTATION
    }
}
