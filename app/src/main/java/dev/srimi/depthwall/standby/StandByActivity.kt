package dev.srimi.depthwall.standby

import android.app.Activity
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

/**
 * StandBy on demand, from the app or the Quick Settings tile. The window stays portrait and the
 * content turns itself, exactly like the screen saver with auto-rotate off. It keeps the screen
 * on only while charging, so a phone left on battery still times out.
 */
class StandByActivity : Activity(), StandByView.Listener {
    private lateinit var view: StandByView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = StandByView(this, this)
        setContentView(view)
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onStart() { super.onStart(); view.start() }
    override fun onStop() { view.stop(); super.onStop() }

    override fun onNightChanged(night: Boolean) {
        window.attributes = window.attributes.apply {
            screenBrightness = if (night) NIGHT_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    override fun onChargingChanged(charging: Boolean) {
        if (charging) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onExit() = finish()

    private companion object { const val NIGHT_BRIGHTNESS = .02f }
}
