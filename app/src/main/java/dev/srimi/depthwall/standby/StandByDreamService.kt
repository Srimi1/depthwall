package dev.srimi.depthwall.standby

import android.service.dreams.DreamService
import android.view.WindowInsets

/**
 * StandBy as Android's screen saver. Choose it in Settings → Display → Screen saver with
 * "While charging": crDroid starts it on the charger and ends it when unplugged, so nothing
 * of StandBy runs on battery.
 */
class StandByDreamService : DreamService() {
    private var view: StandByView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = true
        view = StandByView(this, object : StandByView.Listener {
            // Night mode lets the system drop to its dim dream brightness.
            override fun onNightChanged(night: Boolean) { isScreenBright = !night }
            override fun onExit() = wakeUp()
        })
        setContentView(view)
        window?.insetsController?.hide(WindowInsets.Type.systemBars())
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        view?.start()
    }

    override fun onDreamingStopped() {
        view?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        view?.stop(); view = null
        super.onDetachedFromWindow()
    }
}
