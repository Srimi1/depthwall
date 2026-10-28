package dev.srimi.depthwall.wallpaper

import android.app.KeyguardManager
import android.app.wallpaper.WallpaperDescription
import android.content.*
import android.graphics.Color
import android.hardware.*
import android.os.*
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import dev.srimi.depthwall.DepthWallApp
import dev.srimi.depthwall.data.*
import kotlinx.coroutines.*
import java.io.FileDescriptor
import java.io.PrintWriter
import kotlin.math.*

class DepthWallpaperService:WallpaperService() {
    override fun onCreateEngine():Engine=DepthEngine()
    inner class DepthEngine:Engine(),SensorEventListener {
        private val thread=HandlerThread("DepthWall-render").apply { start() }
        private val handler=Handler(thread.looper)
        private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        private val repo=(application as DepthWallApp).repository
        private val power=getSystemService(PowerManager::class.java)
        private val keyguard=getSystemService(KeyguardManager::class.java)
        private val sensors=getSystemService(SensorManager::class.java)
        @Volatile private var rendering=false
        @Volatile private var surfaceReady=false
        @Volatile private var width=1
        @Volatile private var height=1
        @Volatile private var quality="Balanced"
        @Volatile private var config=SceneConfig()
        @Volatile private var mx=0f
        @Volatile private var my=0f
        private var renderer:WindowRenderer?=null
        private var start=SystemClock.elapsedRealtime()
        private var transitionAt=start
        private var lastUnlocked=false
        private var progress=0f
        private var previousRevision=-1L
        @Volatile private var frames=0L
        @Volatile private var lastError="none"
        private val events=object:BroadcastReceiver() {
            override fun onReceive(c:Context?,i:Intent?) {
                if(i?.action==Intent.ACTION_SCREEN_OFF) { rendering=false;handler.removeCallbacks(frame);sensors.unregisterListener(this@DepthEngine) }
                else { rendering=isVisible && power.isInteractive;updateSensors();schedule() }
            }
        }
        private val thermal=PowerManager.OnThermalStatusChangedListener { updateSensors();schedule() }
        override fun onCreate(holder:SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(false)
            ContextCompat.registerReceiver(this@DepthWallpaperService,events,IntentFilter().apply { addAction(Intent.ACTION_USER_PRESENT);addAction(Intent.ACTION_SCREEN_ON);addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_TIME_CHANGED);addAction(Intent.ACTION_TIMEZONE_CHANGED);addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) },ContextCompat.RECEIVER_NOT_EXPORTED)
            power.addThermalStatusListener(thermal)
            scope.launch { repo.settings.flow.collect { quality=it.quality;updateSensors();schedule() } }
        }
        private fun readConfig() {
            val flags=if(Build.VERSION.SDK_INT>=34)wallpaperFlags else 1
            config=if(isPreview) repo.scenes.pending() else repo.scenes.active(flags)
        }
        override fun onSurfaceCreated(holder:SurfaceHolder) {
            super.onSurfaceCreated(holder);readConfig();surfaceReady=true
            handler.post {
                runCatching { renderer?.close();renderer=WindowRenderer(this@DepthWallpaperService,holder.surface) }
                    .onFailure { lastError=it.message.orEmpty();Log.e("DepthWall","Renderer init",it) }
                schedule()
            }
        }
        override fun onSurfaceChanged(holder:SurfaceHolder,format:Int,w:Int,h:Int) {
            super.onSurfaceChanged(holder,format,w,h);width=w;height=h;schedule()
        }
        override fun onSurfaceRedrawNeeded(holder:SurfaceHolder) { super.onSurfaceRedrawNeeded(holder);schedule() }
        override fun onVisibilityChanged(visible:Boolean) {
            rendering=visible && power.isInteractive
            if(rendering) { readConfig();transitionAt=SystemClock.elapsedRealtime() }
            updateSensors();schedule()
        }
        override fun onOffsetsChanged(xOffset:Float,yOffset:Float,xStep:Float,yStep:Float,xPixels:Int,yPixels:Int) { mx=(xOffset-.5f)*.6f;schedule() }
        override fun onWallpaperFlagsChanged(which:Int) { super.onWallpaperFlagsChanged(which);readConfig();schedule() }
        override fun onApplyWallpaper(which:Int):WallpaperDescription? {
            // Android's actual Apply callback is the commit boundary, not preview creation.
            repo.scenes.commit(config,which)
            return super.onApplyWallpaper(which)
        }
        private fun updateSensors() {
            sensors.unregisterListener(this)
            if(rendering && quality!="Battery" && !power.isPowerSaveMode && power.currentThermalStatus<PowerManager.THERMAL_STATUS_MODERATE && config.parallax>0f) sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(this,it,SensorManager.SENSOR_DELAY_UI) }
        }
        override fun onSensorChanged(event:SensorEvent) { mx=mx*.9f+(-event.values[0]/9.8f).coerceIn(-1f,1f)*.1f;my=my*.9f+(event.values[1]/9.8f-.5f).coerceIn(-1f,1f)*.1f }
        override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int) {}
        private fun schedule() { handler.removeCallbacks(frame);if(rendering && surfaceReady)handler.post(frame) }
        private val frame=object:Runnable {
            override fun run() {
                if(!rendering || !surfaceReady)return
                val now=SystemClock.elapsedRealtime()
                val revision=repo.scenes.revision()
                if(!isPreview && revision!=previousRevision) { previousRevision=revision;readConfig() }
                val unlocked=!keyguard.isKeyguardLocked
                if(unlocked!=lastUnlocked) { lastUnlocked=unlocked;transitionAt=now }
                val target=if(unlocked)1f else 0f
                progress+=(target-progress)*.065f
                if(abs(target-progress)<.001f)progress=target
                val changing=now-transitionAt<2200
                val refresh=getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY)?.refreshRate ?: 60f
                val still=config.scene=="depth" && config.parallax==0f && !changing
                val fps=if(still)0 else RenderBudget.fps(quality,changing,true,power.isPowerSaveMode,power.currentThermalStatus>=PowerManager.THERMAL_STATUS_MODERATE,refresh)
                try {
                    val budget=if(power.isPowerSaveMode || power.currentThermalStatus>=PowerManager.THERMAL_STATUS_MODERATE || quality=="Battery")720 else if(quality=="High Quality")1440 else 960
                    renderer?.draw(config,width,height,(now-start)/1000f,if(isPreview) ((sin((now-start)/2500.0)*.5+.5).toFloat()) else progress,mx,my,budget)
                    frames++
                } catch(e:Exception) { lastError=e.message.orEmpty();Log.e("DepthWall","Wallpaper frame failed",e);handler.postDelayed(this,2000);return }
                val delay=if(fps>0)(1000L/fps-(SystemClock.elapsedRealtime()-now)).coerceAtLeast(1) else if(config.clock!="Off")60000-System.currentTimeMillis()%60000 else 0
                if(rendering && delay>0)handler.postDelayed(this,delay)
            }
        }
        override fun onComputeColors()=android.app.WallpaperColors(Color.valueOf(Color.BLACK),Color.valueOf(0xFFFF870A.toInt()),null)
        override fun onSurfaceDestroyed(holder:SurfaceHolder) {
            surfaceReady=false;handler.removeCallbacks(frame)
            handler.post { renderer?.close();renderer=null }
            super.onSurfaceDestroyed(holder)
        }
        override fun onDestroy() {
            rendering=false;surfaceReady=false;handler.removeCallbacks(frame)
            sensors.unregisterListener(this);runCatching { unregisterReceiver(events) };power.removeThermalStatusListener(thermal);scope.cancel()
            handler.post { renderer?.close();renderer=null;thread.quitSafely() }
            super.onDestroy()
        }
        override fun dump(prefix:String,fd:FileDescriptor,out:PrintWriter,args:Array<out String>?) {
            super.dump(prefix,fd,out,args)
            out.println("${prefix}DepthWall visible=$rendering frames=$frames quality=$quality scene=${config.scene} surface=$surfaceReady error=$lastError")
        }
    }
}
