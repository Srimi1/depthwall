package dev.srimi.depthwall.wallpaper

import android.content.Context
import android.graphics.*
import android.opengl.*
import android.util.Log
import android.view.Surface
import dev.srimi.depthwall.DepthWallApp
import dev.srimi.depthwall.data.SceneConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

/** All methods are called on the owner render thread with its EGL context current. */
class GlPainter(private val context:Context) {
    private val vertices=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f));position(0) }
    private val program:Int
    private val textures=IntArray(3)
    private var loaded:SceneConfig?=null
    private var textureEdge=0
    private var kind=0
    private var minute=-1L
    private var imageAspect=1440f/3120
    init {
        val vertex=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 position;varying vec2 uv;void main(){uv=vec2((position.x+1.0)*.5,(1.0-position.y)*.5);gl_Position=vec4(position,0.0,1.0);}")
        val fragment=shader(GLES20.GL_FRAGMENT_SHADER,context.assets.open("scene.frag").bufferedReader().use { it.readText() })
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program)
        val status=IntArray(1);GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0)
        check(status[0]!=0) { GLES20.glGetProgramInfoLog(program) }
        GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment)
        GLES20.glGenTextures(3,textures,0)
        for(i in 0..2) upload(i,Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888))
    }
    private val positionLocation=GLES20.glGetAttribLocation(program,"position")
    private val resolutionLocation=GLES20.glGetUniformLocation(program,"resolution")
    private val motionLocation=GLES20.glGetUniformLocation(program,"motion")
    private val cropLocation=GLES20.glGetUniformLocation(program,"cropOffset")
    private val zoomLocation=GLES20.glGetUniformLocation(program,"zoom")
    private val aspectLocation=GLES20.glGetUniformLocation(program,"imageAspect")
    private val timeLocation=GLES20.glGetUniformLocation(program,"time")
    private val progressLocation=GLES20.glGetUniformLocation(program,"progress")
    private val kindLocation=GLES20.glGetUniformLocation(program,"kind")
    private val samplerLocations=intArrayOf(
        GLES20.glGetUniformLocation(program,"backgroundTex"),
        GLES20.glGetUniformLocation(program,"foregroundTex"),
        GLES20.glGetUniformLocation(program,"clockTex"))
    private val maxTextureSize=IntArray(1).also { GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE,it,0) }[0]
    private fun shader(type:Int,text:String):Int {
        val id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,text);GLES20.glCompileShader(id)
        val status=IntArray(1);GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,status,0)
        check(status[0]!=0) { GLES20.glGetShaderInfoLog(id) };return id
    }
    private fun upload(index:Int,bitmap:Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[index])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        try { GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0) } finally { bitmap.recycle() }
    }
    /** A missing or unreadable layer draws as black instead of failing every frame. */
    private fun layer(path:String,maxEdge:Int):Bitmap {
        val repo=(context.applicationContext as DepthWallApp).repository
        return try { repo.decode(path,maxEdge) }
        catch(error:Exception) { Log.w("DepthWall","Layer unavailable",error);Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888) }
        catch(error:OutOfMemoryError) { Log.w("DepthWall","Layer too large",error);Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888) }
    }
    private fun load(config:SceneConfig,maxEdge:Int) {
        if(config.scene=="depth") {
            val changedBudget=textureEdge!=maxEdge
            if(loaded?.scene!="depth" || config.background!=loaded?.background || changedBudget) {
                val bitmap=layer(config.background,maxEdge)
                imageAspect=bitmap.width.toFloat()/bitmap.height
                upload(0,bitmap)
            }
            if(loaded?.scene!="depth" || config.foreground!=loaded?.foreground || changedBudget) {
                val bitmap=if(config.foreground.isBlank())Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
                    else layer(config.foreground,maxEdge)
                upload(1,bitmap)
            }
        } else if(loaded?.scene=="depth") {
            // Procedural scenes do not need to retain the previous layer textures.
            upload(0,Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888))
            upload(1,Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888))
        }
        if(config.clock!=loaded?.clock || config.clockY!=loaded?.clockY) minute=-1
        textureEdge=maxEdge
        kind=SCENE_KINDS[config.scene] ?: 0
        loaded=config
    }
    fun invalidateClock() { minute=-1 }
    private fun clock(config:SceneConfig) {
        val now=if(config.clock=="Off")Long.MIN_VALUE else System.currentTimeMillis()/60000
        if(now==minute)return
        minute=now
        if(config.clock=="Off") { upload(2,Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888));return }
        val bitmap=Bitmap.createBitmap(720,1560,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(244,238,229);textAlign=Paint.Align.CENTER;typeface=Typeface.create("sans-serif-light",Typeface.NORMAL) }
        val y=1560*config.clockY
        if(config.clock=="Analogue") {
            val c=Calendar.getInstance();val radius=110f;p.style=Paint.Style.STROKE;p.strokeWidth=2f;canvas.drawCircle(360f,y,radius,p)
            for(i in 0..11) { val a=i*Math.PI/6;canvas.drawLine(360f+Math.sin(a).toFloat()*(radius-10),y-Math.cos(a).toFloat()*(radius-10),360f+Math.sin(a).toFloat()*radius,y-Math.cos(a).toFloat()*radius,p) }
            fun hand(angle:Double,length:Float,width:Float){p.strokeWidth=width;p.strokeCap=Paint.Cap.ROUND;canvas.drawLine(360f,y,360f+Math.sin(angle).toFloat()*length,y-Math.cos(angle).toFloat()*length,p)}
            hand((c.get(Calendar.HOUR)+c.get(Calendar.MINUTE)/60.0)*Math.PI/6,66f,6f);hand(c.get(Calendar.MINUTE)*Math.PI/30,93f,3f)
        } else {
            p.textSize=if(config.clock=="Minimal")74f else 134f
            val pattern=if(android.text.format.DateFormat.is24HourFormat(context))"HH:mm" else "h:mm"
            canvas.drawText(SimpleDateFormat(pattern,Locale.getDefault()).format(java.util.Date()),360f,y,p)
            p.textSize=23f;p.letterSpacingCompat()
            canvas.drawText(SimpleDateFormat("EEE, MMM d",Locale.getDefault()).format(java.util.Date()).uppercase(),360f,y+48,p)
        }
        upload(2,bitmap)
    }
    private fun Paint.letterSpacingCompat() { /* Paint has no letter-spacing API; leave system shaping intact. */ }
    fun draw(config:SceneConfig,w:Int,h:Int,time:Float,progress:Float,mx:Float,my:Float) {
        val edge=maxOf(w,h).coerceIn(256,minOf(4096,maxTextureSize).coerceAtLeast(256))
        if(loaded!=config || (config.scene=="depth" && textureEdge!=edge))load(config,edge)
        clock(config)
        GLES20.glViewport(0,0,w,h);GLES20.glClearColor(0f,0f,0f,1f);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glVertexAttribPointer(positionLocation,2,GLES20.GL_FLOAT,false,0,vertices)
        GLES20.glUniform2f(resolutionLocation,w.toFloat(),h.toFloat())
        GLES20.glUniform2f(motionLocation,mx*config.parallax,my*config.parallax)
        GLES20.glUniform2f(cropLocation,config.offsetX,config.offsetY)
        GLES20.glUniform1f(zoomLocation,config.zoom)
        GLES20.glUniform1f(aspectLocation,imageAspect)
        GLES20.glUniform1f(timeLocation,time)
        GLES20.glUniform1f(progressLocation,progress)
        GLES20.glUniform1i(kindLocation,kind)
        for(i in 0..2) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0+i);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[i]);GLES20.glUniform1i(samplerLocations[i],i)
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
        GLES20.glDisableVertexAttribArray(positionLocation)
    }
    fun close() { GLES20.glDeleteTextures(3,textures,0);GLES20.glDeleteProgram(program) }
    companion object {
        private val SCENE_KINDS=mapOf("depth" to 0,"earth" to 1,"mars" to 2,"saturn" to 3,
            "moon" to 4,"snow" to 5,"geometry" to 6,"sun" to 7)
    }
}

/** The EGL window surface or context is gone; the owner must rebuild the renderer. */
class SurfaceLostException(message:String):IllegalStateException(message)

class WindowRenderer(context:Context,surface:Surface) {
    private val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private var eglContext=EGL14.EGL_NO_CONTEXT
    private var eglSurface=EGL14.EGL_NO_SURFACE
    private lateinit var painter:GlPainter
    private val target=IntArray(1)
    private val color=IntArray(1)
    private var targetW=0
    private var targetH=0
    private var blit=0
    private var blitPosition=0
    private var blitTexture=0
    private val quad=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f));position(0) }
    init {
        val version=IntArray(2);check(EGL14.eglInitialize(display,version,0,version,1)) { "EGL initialisation failed (0x%x)".format(EGL14.eglGetError()) }
        try {
            val configs=arrayOfNulls<android.opengl.EGLConfig>(1);val count=IntArray(1)
            check(EGL14.eglChooseConfig(display,intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_NONE),0,configs,0,1,count,0) && count[0]>0) { "No EGL config" }
            eglContext=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
            eglSurface=EGL14.eglCreateWindowSurface(display,configs[0],surface,intArrayOf(EGL14.EGL_NONE),0)
            check(eglContext!=EGL14.EGL_NO_CONTEXT && eglSurface!=EGL14.EGL_NO_SURFACE) { "EGL surface unavailable (0x%x)".format(EGL14.eglGetError()) }
            check(EGL14.eglMakeCurrent(display,eglSurface,eglSurface,eglContext)) { "EGL context unavailable (0x%x)".format(EGL14.eglGetError()) }
            painter=GlPainter(context)
            fun compile(type:Int,source:String):Int { val s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);return s }
            val v=compile(GLES20.GL_VERTEX_SHADER,"attribute vec2 p;varying vec2 uv;void main(){uv=(p+1.0)*.5;gl_Position=vec4(p,0,1);}")
            val f=compile(GLES20.GL_FRAGMENT_SHADER,"precision mediump float;varying vec2 uv;uniform sampler2D tex;void main(){gl_FragColor=texture2D(tex,uv);}")
            blit=GLES20.glCreateProgram();GLES20.glAttachShader(blit,v);GLES20.glAttachShader(blit,f);GLES20.glLinkProgram(blit);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f)
            blitPosition=GLES20.glGetAttribLocation(blit,"p")
            blitTexture=GLES20.glGetUniformLocation(blit,"tex")
            GLES20.glGenFramebuffers(1,target,0);GLES20.glGenTextures(1,color,0)
        } catch(error:Throwable) {
            // A half-built renderer must not leak EGL objects into the next attempt.
            releaseEgl();throw error
        }
    }
    private fun releaseEgl() {
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
        if(eglSurface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,eglSurface)
        if(eglContext!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,eglContext)
        eglSurface=EGL14.EGL_NO_SURFACE;eglContext=EGL14.EGL_NO_CONTEXT
        EGL14.eglReleaseThread();EGL14.eglTerminate(display)
    }
    fun draw(c:SceneConfig,w:Int,h:Int,t:Float,p:Float,x:Float,y:Float,maxWidth:Int=960) {
        val rw=minOf(w,maxWidth);val rh=(h.toFloat()*rw/w).toInt().coerceAtLeast(1)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,target[0])
        if(rw!=targetW || rh!=targetH) {
            targetW=rw;targetH=rh;GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,color[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,rw,rh,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null)
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,color[0],0)
            check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)==GLES20.GL_FRAMEBUFFER_COMPLETE)
        }
        painter.draw(c,rw,rh,t,p,x,y)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);GLES20.glViewport(0,0,w,h);GLES20.glUseProgram(blit)
        GLES20.glEnableVertexAttribArray(blitPosition);GLES20.glVertexAttribPointer(blitPosition,2,GLES20.GL_FLOAT,false,0,quad)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,color[0]);GLES20.glUniform1i(blitTexture,0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(blitPosition)
        if(!EGL14.eglSwapBuffers(display,eglSurface))
            throw SurfaceLostException("Wallpaper surface was lost (EGL 0x%x).".format(EGL14.eglGetError()))
    }
    fun invalidateClock()=painter.invalidateClock()
    fun close() { painter.close();GLES20.glDeleteFramebuffers(1,target,0);GLES20.glDeleteTextures(1,color,0);GLES20.glDeleteProgram(blit);releaseEgl() }
}
