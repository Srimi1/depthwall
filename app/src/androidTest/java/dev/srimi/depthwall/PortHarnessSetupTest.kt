package dev.srimi.depthwall

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Destructive emulator fixture, never enabled by the ordinary test suite. */
@RunWith(AndroidJUnit4::class)
class PortHarnessSetupTest {
    @Test fun resetToNeutralWallpaper() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("portHarness") == "true")
        assertTrue("Port harness reset is restricted to an Android emulator",
            Build.PRODUCT.startsWith("sdk_") &&
                (Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = WallpaperManager.getInstance(context)
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(24, 24, 32))
            val id = manager.setBitmap(bitmap, null, false,
                WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            assertTrue("Neutral wallpaper was not accepted", id > 0)
            assertTrue(manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM) > 0)
            // A shared static wallpaper can legitimately have no separate lock ID.
            if (Build.VERSION.SDK_INT >= 34) {
                assertNull(manager.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM))
                assertNull(manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK))
            } else {
                assertNull(manager.wallpaperInfo)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
