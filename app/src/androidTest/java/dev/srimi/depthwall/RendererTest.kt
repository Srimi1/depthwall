package dev.srimi.depthwall

import android.opengl.EGL14
import android.opengl.GLES20
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.srimi.depthwall.data.SceneConfig
import dev.srimi.depthwall.wallpaper.GlPainter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class RendererTest {
    @Test fun allSevenWorldsAndDepthRenderRealPixels() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version=IntArray(2);assertTrue(EGL14.eglInitialize(display,version,0,version,1))
        val configs=arrayOfNulls<android.opengl.EGLConfig>(1);val count=IntArray(1)
        assertTrue(EGL14.eglChooseConfig(display,intArrayOf(EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_NONE),0,configs,0,1,count,0))
        val gl=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
        val surface=EGL14.eglCreatePbufferSurface(display,configs[0],intArrayOf(EGL14.EGL_WIDTH,96,EGL14.EGL_HEIGHT,208,EGL14.EGL_NONE),0)
        assertTrue(EGL14.eglMakeCurrent(display,surface,surface,gl))
        val painter=GlPainter(context)
        try {
            val hashes=mutableSetOf<Int>()
            for(scene in listOf("earth","mars","saturn","moon","snow","geometry","sun","depth")) {
                val config=SceneConfig(scene=scene,background="wallpapers/depth_0_bg.webp",foreground=if(scene=="depth")"wallpapers/depth_0_fg.webp" else "",clock=if(scene=="depth")"Digital" else "Off")
                painter.draw(config,96,208,10f,0f,0f,0f)
                assertEquals("GL error in $scene",GLES20.GL_NO_ERROR,GLES20.glGetError())
                val pixels=ByteBuffer.allocateDirect(96*208*4)
                GLES20.glReadPixels(0,0,96,208,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels)
                val bytes=ByteArray(pixels.capacity());pixels.get(bytes)
                assertTrue("Blank $scene",bytes.indices.any { it%4!=3 && (bytes[it].toInt() and 255)>45 })
                hashes.add(bytes.contentHashCode())
            }
            assertEquals("Each scene must produce distinct pixels",8,hashes.size)
        } finally {
            painter.close();EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,gl);EGL14.eglTerminate(display)
        }
    }
}
