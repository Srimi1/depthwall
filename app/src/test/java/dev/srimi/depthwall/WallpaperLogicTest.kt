package dev.srimi.depthwall

import dev.srimi.depthwall.data.CropMath
import dev.srimi.depthwall.data.RenderBudget
import dev.srimi.depthwall.data.WallpaperItem
import dev.srimi.depthwall.data.SceneTransition
import org.junit.Assert.*
import org.junit.Test

class WallpaperLogicTest {
    @Test fun settledLandscapesAndDepthNeedNoIdleAnimation() {
        assertFalse(RenderBudget.hasContinuousMotion("depth",0f))
        for(scene in listOf("earth","mars","moon")) {
            assertTrue(RenderBudget.hasContinuousMotion(scene,.99f))
            assertFalse(RenderBudget.hasContinuousMotion(scene,1f))
        }
        for(scene in listOf("sun","snow","saturn","geometry"))
            assertTrue(RenderBudget.hasContinuousMotion(scene,1f))
    }
    @Test fun pressureLowersRenderResolutionRegardlessOfQuality() {
        assertEquals(960,RenderBudget.renderWidth("Balanced",false,false))
        assertEquals(1440,RenderBudget.renderWidth("High Quality",false,false))
        assertEquals(720,RenderBudget.renderWidth("High Quality",true,false))
        assertEquals(720,RenderBudget.renderWidth("High Quality",false,true))
    }

    @Test fun lowFrameRateTransitionsStillReachTheirDestination() {
        assertEquals(0f,SceneTransition.progress(0f,1f,0),0f)
        assertEquals(.5f,SceneTransition.progress(0f,1f,900),.001f)
        for(fps in listOf(15,30,60,90)) {
            val elapsed=(SceneTransition.DURATION_MS/(1000L/fps)+1)*(1000L/fps)
            assertEquals(1f,SceneTransition.progress(0f,1f,elapsed),0f)
            assertEquals(0f,SceneTransition.progress(.7f,0f,elapsed),.00001f)
        }
    }
    @Test fun downloadedLibraryImagesDoNotNeedRemoteThumbnailCache() {
        val item=WallpaperItem("remote","Moon","NASA",thumbnail="https://images-assets.nasa.gov/thumb.jpg",localPath="/saved/moon.img")
        assertEquals("/saved/moon.img",item.thumbnailModel)
    }
    @Test fun wideImageHasPortraitCropAndReachesBothEdges() {
        val left=CropMath.calculate(4000,2000,1440,3120,1f,0f,0f)
        val right=CropMath.calculate(4000,2000,1440,3120,1f,1f,1f)
        assertEquals(0,left.left)
        assertEquals(4000,right.left+right.width)
        assertEquals(2000,right.top+right.height)
        assertEquals(1440f/3120,left.width.toFloat()/left.height,.002f)
    }
    @Test fun zoomAndPositionsCannotEscapeImageBounds() {
        for(z in listOf(-10f,1f,2f,9f))for(x in listOf(-1f,.5f,2f)) {
            val c=CropMath.calculate(300,900,1440,3120,z,x,x)
            assertTrue(c.left>=0 && c.top>=0 && c.left+c.width<=300 && c.top+c.height<=900)
        }
    }
    @Test fun hiddenWallpaperNeverSchedulesFrames() {
        for(q in listOf("Battery","Balanced","High Quality"))for(t in listOf(false,true))
            assertEquals(0,RenderBudget.fps(q,t,false,false,false))
    }
    @Test fun powerAndThermalLimitsOverrideQuality() {
        assertEquals(15,RenderBudget.fps("High Quality",true,true,true,false))
        assertEquals(0,RenderBudget.fps("High Quality",false,true,false,true))
        assertEquals(60,RenderBudget.fps("Balanced",true,true,false,false))
        assertEquals(15,RenderBudget.fps("Balanced",false,true,false,false))
        assertEquals(60,RenderBudget.fps("High Quality",true,true,false,false,60f))
    }
}
