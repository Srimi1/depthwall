package dev.srimi.depthwall

import dev.srimi.depthwall.data.RendererRecovery
import dev.srimi.depthwall.data.RotationPolicy
import dev.srimi.depthwall.data.WallpaperApplyState
import dev.srimi.depthwall.data.WallpaperRestart
import org.junit.Assert.*
import org.junit.Test

class RestartRecoveryTest {
    private val earth = "com.miui.miwallpaper.earth/com.miui.miwallpaper.earth.superwallpaper.EarthSuperWallpaper"
    private val longji = "com.miui.miwallpaper.earth/com.miui.miwallpaper.earth.superwallpaper.EarthSuperWallpaper1"
    private val mars = "com.miui.miwallpaper.mars/com.miui.miwallpaper.mars.superwallpaper.MarsSuperWallpaper"

    @Test fun lostSurfacesRebuildAndOtherFailuresRetryWithinABudget() {
        assertEquals(RendererRecovery.Action.REBUILD, RendererRecovery.onFailure(true, 0))
        assertEquals(RendererRecovery.Action.RETRY_FRAME, RendererRecovery.onFailure(false, 0))
        assertEquals(RendererRecovery.Action.GIVE_UP, RendererRecovery.onFailure(true, RendererRecovery.MAX_ATTEMPTS))
        assertEquals(RendererRecovery.Action.GIVE_UP, RendererRecovery.onFailure(false, RendererRecovery.MAX_ATTEMPTS))
    }
    @Test fun retryDelaysBackOffAndStayBounded() {
        assertEquals(500L, RendererRecovery.retryDelayMs(0))
        assertEquals(1000L, RendererRecovery.retryDelayMs(1))
        assertEquals(8000L, RendererRecovery.retryDelayMs(RendererRecovery.MAX_ATTEMPTS - 1))
        assertEquals(8000L, RendererRecovery.retryDelayMs(99))
        assertEquals(500L, RendererRecovery.retryDelayMs(-3))
    }
    @Test fun reapplyCommandMatchesTheFrameworkAction() {
        assertEquals("android.wallpaper.reapply", RendererRecovery.COMMAND_REAPPLY)
    }

    @Test fun sharedLockFollowsHomeWhenFindingRestartTargets() {
        assertEquals(3, WallpaperRestart.destinations(WallpaperApplyState(10, -1, earth, null), earth))
        assertEquals(1, WallpaperRestart.destinations(WallpaperApplyState(10, 20, earth, mars), earth))
        assertEquals(2, WallpaperRestart.destinations(WallpaperApplyState(10, 20, mars, earth), earth))
        assertEquals(3, WallpaperRestart.destinations(WallpaperApplyState(10, 20, earth, earth), earth))
        assertEquals(0, WallpaperRestart.destinations(WallpaperApplyState(10, -1, null, null), earth))
    }
    @Test fun onlyDifferentScenesOfOnePortPackageShareAProcess() {
        assertTrue(WallpaperRestart.sharesPortProcess(WallpaperApplyState(10, 20, earth, longji)))
        assertFalse(WallpaperRestart.sharesPortProcess(WallpaperApplyState(10, 20, earth, earth)))
        assertFalse(WallpaperRestart.sharesPortProcess(WallpaperApplyState(10, 20, earth, mars)))
        assertFalse(WallpaperRestart.sharesPortProcess(WallpaperApplyState(10, -1, earth, null)))
        val own = "dev.srimi.depthwall/dev.srimi.depthwall.wallpaper.DepthWallpaperService"
        assertFalse(WallpaperRestart.sharesPortProcess(WallpaperApplyState(10, 20, own, own)))
    }

    @Test fun lockOnlyRotationRespectsALiveWallpaperSharedWithHome() {
        assertTrue(RotationPolicy.liveActive(2, homeLive = true, lockLive = false, lockShared = true))
        assertFalse(RotationPolicy.liveActive(2, homeLive = true, lockLive = false, lockShared = false))
        assertTrue(RotationPolicy.liveActive(2, homeLive = false, lockLive = true, lockShared = false))
        assertTrue(RotationPolicy.liveActive(1, homeLive = true, lockLive = false, lockShared = false))
        assertFalse(RotationPolicy.liveActive(1, homeLive = false, lockLive = true, lockShared = false))
        assertTrue(RotationPolicy.liveActive(3, homeLive = false, lockLive = true, lockShared = false))
        assertFalse(RotationPolicy.liveActive(3, homeLive = false, lockLive = false, lockShared = true))
    }
}
