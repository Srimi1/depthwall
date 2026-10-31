package dev.srimi.depthwall.standby

import android.content.pm.ActivityInfo
import android.service.dreams.DreamService
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.srimi.depthwall.DepthWallApp

/** The system owns activation and the keyguard. This is a normal dream, not an AOD renderer. */
class StandbyDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore = ViewModelStore()
    private var monitor: StandbyDeviceMonitor? = null
    private var composition: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = true
        window?.let { host ->
            host.decorView.setViewTreeLifecycleOwner(this)
            host.decorView.setViewTreeSavedStateRegistryOwner(this)
            host.decorView.setViewTreeViewModelStoreOwner(this)
            // StandBy rotates independently from the user's normal rotation-lock preference.
            host.attributes = host.attributes.apply { screenOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
            prepareStandbyWindow(host)
        }
        composition = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                StandbySession((application as DepthWallApp).repository, preview = false,
                    onExit = { finish() }, onNightChanged = { night -> window?.let { applyStandbyBrightness(it, night) } })
            }
        }.also { setContentView(it) }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        registry.currentState = Lifecycle.State.RESUMED
        monitor = StandbyDeviceMonitor(this, { state ->
            if (!state.powered || state.landscape == false) finish()
        }, onScreenOff = { finish() }).also { it.start() }
    }

    override fun onDreamingStopped() {
        stopSession()
        super.onDreamingStopped()
    }

    private fun stopSession() {
        monitor?.stop(); monitor = null
        if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = Lifecycle.State.CREATED
        window?.let(::releaseStandbyWindow)
    }

    override fun onDetachedFromWindow() {
        stopSession()
        composition?.disposeComposition(); composition = null
        super.onDetachedFromWindow()
    }

    override fun onDestroy() {
        stopSession()
        composition?.disposeComposition(); composition = null
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        super.onDestroy()
    }
}
