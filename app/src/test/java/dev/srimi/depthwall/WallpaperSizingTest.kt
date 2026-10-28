package dev.srimi.depthwall

import dev.srimi.depthwall.data.CropMath
import dev.srimi.depthwall.data.WallpaperSizing
import org.junit.Assert.*
import org.junit.Test

class WallpaperSizingTest {
    @Test fun phoneProfileUsesNativePanelEvenWhenAndroidRunsAtLowerResolution() {
        assertEquals(WallpaperSizing.Size(1440, 3120), WallpaperSizing.target("OnePlus 7T Pro", 1080, 2340))
        assertEquals(WallpaperSizing.Size(2160, 4680), WallpaperSizing.target("OnePlus 7T Pro", 1080, 2340, true))
    }
    @Test fun currentScreenKeepsItsAspectRatio() {
        assertEquals(WallpaperSizing.Size(1080, 2400), WallpaperSizing.target("Current screen", 1080, 2400))
        assertEquals(WallpaperSizing.Size(2106, 4680), WallpaperSizing.target("Current screen", 1080, 2400, true))
    }
    @Test fun landscapeSourceIsNeverFalselyUpscaledTo4kPortrait() {
        val target = WallpaperSizing.target("OnePlus 7T Pro", 1440, 3120, true)
        val crop = CropMath.calculate(3840, 2160, target.width, target.height, 1f, .5f, .5f)
        val result = WallpaperSizing.fitSource(crop.width, crop.height, target)
        assertEquals(crop.width, result.width)
        assertEquals(crop.height, result.height)
        assertTrue(result.width < target.width)
    }
    @Test fun largePortraitIsReducedToSelectedTarget() {
        assertEquals(WallpaperSizing.Size(1440, 3120), WallpaperSizing.fitSource(2880, 6240, WallpaperSizing.Size(1440, 3120)))
    }
}
