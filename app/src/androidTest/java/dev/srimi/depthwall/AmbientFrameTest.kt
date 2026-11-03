package dev.srimi.depthwall

import android.opengl.EGL14
import android.opengl.GLES20
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.srimi.depthwall.data.AmbientWallpaper
import dev.srimi.depthwall.data.SceneConfig
import dev.srimi.depthwall.wallpaper.GlPainter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

/** The AOD pose must stay visible yet light few OLED pixels, and never show the wallpaper clock. */
@RunWith(AndroidJUnit4::class)
class AmbientFrameTest {
    private val w = 96
    private val h = 208

    @Test fun aodPoseIsDimSparseAndClockFree() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2); assertTrue(EGL14.eglInitialize(display, version, 0, version, 1))
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1); val count = IntArray(1)
        assertTrue(EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_NONE), 0, configs, 0, 1, count, 0))
        val gl = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, w, EGL14.EGL_HEIGHT, h, EGL14.EGL_NONE), 0)
        assertTrue(EGL14.eglMakeCurrent(display, surface, surface, gl))
        val painter = GlPainter(context)
        try {
            fun pixels(): ByteArray {
                val buffer = ByteBuffer.allocateDirect(w * h * 4)
                GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
                return ByteArray(buffer.capacity()).also { buffer.get(it) }
            }
            fun brightest(bytes: ByteArray, i: Int) = maxOf(bytes[i].toInt() and 255, bytes[i + 1].toInt() and 255, bytes[i + 2].toInt() and 255)
            fun lit(bytes: ByteArray) = (bytes.indices step 4).count { brightest(bytes, it) > 24 } / (w * h).toFloat()
            fun mean(bytes: ByteArray) = (bytes.indices step 4).sumOf { brightest(bytes, it) } / (w * h).toDouble()
            val shift = AmbientWallpaper.burnInShift(3)
            for (scene in listOf("earth", "mars", "saturn", "moon", "snow", "geometry", "sun", "depth")) {
                val config = SceneConfig(scene = scene, background = "wallpapers/depth_0_bg.webp",
                    foreground = if (scene == "depth") "wallpapers/depth_0_fg.webp" else "")
                painter.draw(config, w, h, 10f, 0f, 0f, 0f)
                val awake = pixels()
                painter.draw(config, w, h, 10f, 0f, 0f, 0f, 1f, shift[0], shift[1])
                assertEquals("GL error in $scene AOD", GLES20.GL_NO_ERROR, GLES20.glGetError())
                val aod = pixels()
                Log.i("AmbientFrameTest", "$scene lit=%.3f awakeLit=%.3f mean=%.1f awakeMean=%.1f".format(lit(aod), lit(awake), mean(aod), mean(awake)))
                assertTrue("AOD $scene is blank", aod.indices.any { it % 4 != 3 && (aod[it].toInt() and 255) > 8 })
                assertTrue("AOD $scene lights too much of the panel: ${lit(aod)}", lit(aod) <= .15f)
                assertTrue("AOD $scene is not dimmer than awake", mean(aod) < mean(awake) * .6)
            }
            // SystemUI owns the AOD clock: DepthWall's clock layer fades out completely.
            val base = SceneConfig(scene = "depth", background = "wallpapers/depth_0_bg.webp", foreground = "wallpapers/depth_0_fg.webp")
            painter.draw(base, w, h, 10f, 0f, 0f, 0f, 1f)
            val withoutClock = pixels()
            painter.draw(base.copy(clock = "Bold"), w, h, 10f, 0f, 0f, 0f, 1f)
            assertArrayEquals(withoutClock, pixels())
            // Halfway through waking, the pose sits between AOD and the lock orbit.
            val earth = SceneConfig(scene = "earth")
            painter.draw(earth, w, h, 10f, 0f, 0f, 0f, .5f)
            val waking = mean(pixels())
            painter.draw(earth, w, h, 10f, 0f, 0f, 0f, 1f)
            val dozing = mean(pixels())
            assertTrue("Waking frame must brighten from the AOD pose", waking > dozing)
        } finally {
            painter.close()
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface); EGL14.eglDestroyContext(display, gl); EGL14.eglTerminate(display)
        }
    }
}
