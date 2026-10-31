package dev.srimi.depthwall.standby

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.srimi.depthwall.DepthWallApp

class StandbyActivity : ComponentActivity() {
    private var monitor: StandbyDeviceMonitor? = null
    private val preview: Boolean get() = intent.getBooleanExtra(EXTRA_PREVIEW, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prepareStandbyWindow(window)
        val repository = (application as DepthWallApp).repository
        setContent {
            StandbySession(repository, preview, onExit = { finish() },
                onNightChanged = { applyStandbyBrightness(window, it) })
        }
    }

    override fun onResume() {
        super.onResume()
        prepareStandbyWindow(window)
        if (!preview) {
            monitor = StandbyDeviceMonitor(this, { state ->
                if (!state.powered || state.landscape == false) finish()
            }, onScreenOff = { finish() }).also { it.start() }
        }
    }

    override fun onPause() {
        monitor?.stop(); monitor = null
        releaseStandbyWindow(window)
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // Returning from Home or another app must not leave an old charging session on top.
        if (!isChangingConfigurations) finish()
    }

    companion object {
        private const val EXTRA_PREVIEW = "standby_preview"
        fun intent(context: Context, preview: Boolean = false) = Intent(context, StandbyActivity::class.java)
            .putExtra(EXTRA_PREVIEW, preview)
    }
}

internal fun prepareStandbyWindow(window: Window) {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}

internal fun applyStandbyBrightness(window: Window, night: Boolean) {
    val brightness = if (night) .03f else .2f
    if (window.attributes.screenBrightness != brightness)
        window.attributes = window.attributes.apply { screenBrightness = brightness }
}

internal fun releaseStandbyWindow(window: Window) {
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
}
