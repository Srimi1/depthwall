package dev.srimi.depthwall.wallpaper

import android.content.Context
import android.os.*
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import dev.srimi.depthwall.data.SceneConfig

class SceneView(context:Context):SurfaceView(context),SurfaceHolder.Callback {
    @Volatile var config=SceneConfig()
    @Volatile private var active=true
    @Volatile private var ready=false
    @Volatile var showSurface=false
    private val thread=HandlerThread("DepthWall-preview").apply { start() }
    private val handler=Handler(thread.looper)
    private var renderer:WindowRenderer?=null
    private var start=SystemClock.elapsedRealtime()
    private var progress=0f
    var onError:((String)->Unit)?=null
    init { holder.addCallback(this) }
    fun setActive(value:Boolean) { active=value;handler.removeCallbacks(frame);if(value && ready)handler.post(frame) }
    override fun surfaceCreated(holder:SurfaceHolder) {
        ready=true
        handler.post {
            runCatching { renderer=WindowRenderer(context,holder.surface);frame.run() }.onFailure { post { onError?.invoke(it.message ?: "Preview could not start") } }
        }
    }
    override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int) {}
    private val frame=object:Runnable {
        override fun run() {
            if(!ready || !active)return
            progress+=((if(showSurface)1f else 0f)-progress)*.07f
            try { renderer?.draw(config,width.coerceAtLeast(1),height.coerceAtLeast(1),(SystemClock.elapsedRealtime()-start)/1000f,progress,0f,0f) }
            catch(e:Exception) { Log.e("DepthWall","Preview failed",e);post { onError?.invoke(e.message ?: "Preview failed") };return }
            handler.postDelayed(this,33)
        }
    }
    override fun surfaceDestroyed(holder:SurfaceHolder) {
        ready=false;handler.removeCallbacks(frame)
        handler.post { renderer?.close();renderer=null }
    }
    override fun onDetachedFromWindow() {
        active=false;ready=false;handler.removeCallbacks(frame)
        handler.post { renderer?.close();renderer=null;thread.quitSafely() }
        super.onDetachedFromWindow()
    }
}
