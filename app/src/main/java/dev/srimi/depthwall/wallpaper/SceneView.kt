package dev.srimi.depthwall.wallpaper

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.ContextCompat
import dev.srimi.depthwall.DepthWallApp
import dev.srimi.depthwall.data.RenderBudget
import dev.srimi.depthwall.data.DisplayRenderPolicy
import dev.srimi.depthwall.data.SceneConfig
import dev.srimi.depthwall.data.SceneTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Preview rendering follows the same quality and power budget as the wallpaper. */
class SceneView(context:Context):SurfaceView(context),SurfaceHolder.Callback {
    @Volatile var config=SceneConfig()
        set(value) {
            if(field==value)return
            field=value
            requestDraw()
        }
    @Volatile var showSurface=false
        set(value) {
            if(field==value)return
            field=value
            if(!closed)handler.post {
                transitionFrom=progress
                transitionAt=SystemClock.elapsedRealtime()
                requestDraw()
            }
        }
    @Volatile private var active=true
    @Volatile private var ready=false
    @Volatile private var closed=false
    @Volatile private var initialized=false
    @Volatile private var windowVisible=true
    @Volatile private var renderWidth=1
    @Volatile private var renderHeight=1
    @Volatile private var quality="Balanced"
    @Volatile private var refreshRate=60f
    private val power=context.getSystemService(PowerManager::class.java)
    private val displays=context.getSystemService(DisplayManager::class.java)
    @Volatile private var displayId=Display.INVALID_DISPLAY
    @Volatile private var displayState=Display.STATE_UNKNOWN
    @Volatile private var interactive=power.isInteractive
    @Volatile private var saver=power.isPowerSaveMode
    @Volatile private var hot=power.currentThermalStatus>=PowerManager.THERMAL_STATUS_MODERATE
    private val thread=HandlerThread("DepthWall-preview").apply { start() }
    private val handler=Handler(thread.looper)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var renderer:WindowRenderer?=null
    private val start=SystemClock.elapsedRealtime()
    private var progress=0f
    private var transitionFrom=0f
    private var transitionAt=start-SceneTransition.DURATION_MS
    var onError:((String)->Unit)?=null

    private val events=object:BroadcastReceiver() {
        override fun onReceive(context:Context?,intent:Intent?) {
            updatePolicy()
            if(intent?.action==Intent.ACTION_SCREEN_OFF)interactive=false
            if(intent?.action==Intent.ACTION_TIME_CHANGED || intent?.action==Intent.ACTION_TIMEZONE_CHANGED) {
                handler.post { renderer?.invalidateClock();requestDraw() }
            } else requestDraw()
        }
    }
    private val thermal=PowerManager.OnThermalStatusChangedListener { status ->
        hot=status>=PowerManager.THERMAL_STATUS_MODERATE
        requestDraw()
    }
    private val displayListener=object:DisplayManager.DisplayListener {
        override fun onDisplayAdded(id:Int)=onTargetDisplayChanged(id)
        override fun onDisplayRemoved(id:Int)=onTargetDisplayChanged(id)
        override fun onDisplayChanged(id:Int)=onTargetDisplayChanged(id)
    }
    private fun onTargetDisplayChanged(id:Int) {
        if(id!=displayId && id!=display?.displayId)return
        updatePolicy()
        requestDraw()
    }
    init {
        holder.addCallback(this)
        ContextCompat.registerReceiver(context,events,IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        },ContextCompat.RECEIVER_NOT_EXPORTED)
        power.addThermalStatusListener(thermal)
        displays.registerDisplayListener(displayListener,Handler(Looper.getMainLooper()))
        scope.launch {
            (context.applicationContext as DepthWallApp).repository.settings.flow.collect {
                if(quality!=it.quality) { quality=it.quality;requestDraw() }
            }
        }
        initialized=true
    }
    private fun updatePolicy() {
        displayId=display?.displayId ?: Display.INVALID_DISPLAY
        val targetDisplay=displays.getDisplay(displayId)
        displayState=targetDisplay?.state ?: Display.STATE_UNKNOWN
        interactive=power.isInteractive
        saver=power.isPowerSaveMode
        hot=power.currentThermalStatus>=PowerManager.THERMAL_STATUS_MODERATE
        refreshRate=targetDisplay?.refreshRate ?: 60f
    }
    private fun canDraw()=ready && !closed &&
        DisplayRenderPolicy.canRender(active && windowVisible,interactive,displayState)
    private fun requestDraw() {
        if(closed || !initialized)return
        handler.removeCallbacks(frame)
        if(canDraw())handler.post(frame)
    }
    fun setActive(value:Boolean) {
        if(active==value)return
        active=value
        updatePolicy()
        requestDraw()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if(initialized && !closed) { updatePolicy();requestDraw() }
    }
    override fun onWindowVisibilityChanged(visibility:Int) {
        super.onWindowVisibilityChanged(visibility)
        windowVisible=visibility==VISIBLE
        if(initialized && !closed)updatePolicy()
        requestDraw()
    }
    override fun surfaceCreated(holder:SurfaceHolder) {
        ready=true
        updatePolicy()
        handler.post {
            if(!ready || closed)return@post
            runCatching {
                renderer=WindowRenderer(context,holder.surface)
                requestDraw()
            }.onFailure { reportError(it) }
        }
    }
    override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int) {
        renderWidth=width.coerceAtLeast(1)
        renderHeight=height.coerceAtLeast(1)
        updatePolicy()
        requestDraw()
    }
    private fun reportError(error:Throwable) {
        Log.e("DepthWall","Preview failed",error)
        post { if(!closed)onError?.invoke(error.message ?: "Preview could not start") }
    }
    private val frame=object:Runnable {
        override fun run() {
            // An edit arriving during a draw must not create a second timer loop.
            handler.removeCallbacks(this)
            if(!canDraw())return
            val now=SystemClock.elapsedRealtime()
            val current=config
            progress=SceneTransition.progress(transitionFrom,if(showSurface)1f else 0f,now-transitionAt)
            val changing=now-transitionAt<SceneTransition.DURATION_MS
            // Layers have no preview sensor motion. Settled terrain also has no
            // time-driven effect, so it only needs edits and clock-minute draws.
            val still=!RenderBudget.hasContinuousMotion(current.scene,progress)
            val fps=if(still && !changing)0 else
                RenderBudget.fps(quality,changing,true,saver,hot,refreshRate)
            val maxWidth=RenderBudget.renderWidth(quality,saver,hot)
            try {
                renderer?.draw(current,renderWidth,renderHeight,(now-start)/1000f,
                    progress,0f,0f,maxWidth)
            } catch(error:Exception) { reportError(error);return }
            val delay=if(fps>0)(1000L/fps-(SystemClock.elapsedRealtime()-now)).coerceAtLeast(1)
                else if(current.clock!="Off")60000-System.currentTimeMillis()%60000 else 0
            if(canDraw() && delay>0)handler.postDelayed(this,delay)
        }
    }
    override fun surfaceDestroyed(holder:SurfaceHolder) {
        ready=false;handler.removeCallbacks(frame)
        handler.post { renderer?.close();renderer=null }
    }
    override fun onDetachedFromWindow() {
        active=false;ready=false;closed=true;handler.removeCallbacks(frame)
        scope.cancel()
        runCatching { context.unregisterReceiver(events) }
        power.removeThermalStatusListener(thermal)
        displays.unregisterDisplayListener(displayListener)
        handler.post { renderer?.close();renderer=null;thread.quitSafely() }
        super.onDetachedFromWindow()
    }
}
