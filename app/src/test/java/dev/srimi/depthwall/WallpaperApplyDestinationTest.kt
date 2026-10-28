package dev.srimi.depthwall

import dev.srimi.depthwall.data.WallpaperApplyDestination
import dev.srimi.depthwall.data.WallpaperApplyState
import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperApplyDestinationTest {
    private val own = "dev.srimi.depthwall/dev.srimi.depthwall.wallpaper.DepthWallpaperService"
    private fun state(home: Int, lock: Int, homeComponent: String? = own, lockComponent: String? = own) =
        WallpaperApplyState(home, lock, homeComponent, if (lock < 0) null else lockComponent)

    @Test fun homeOnlyDoesNotTreatMigratedPreviousHomeAsANewLockApply() {
        assertEquals(1, WallpaperApplyDestination.resolve(state(10, -1), state(11, 10), own))
    }
    @Test fun lockOnlyKeepsTheExistingHomeScene() {
        assertEquals(2, WallpaperApplyDestination.resolve(state(10, -1), state(10, 11), own))
    }
    @Test fun bothRemovesSeparateLockAndSharesTheFreshHome() {
        assertEquals(3, WallpaperApplyDestination.resolve(state(10, 20), state(21, -1), own))
    }
    @Test fun reapplyingTheSameComponentOnlyChangesItsFreshDestination() {
        assertEquals(1, WallpaperApplyDestination.resolve(state(10, 20), state(21, 20), own))
        assertEquals(2, WallpaperApplyDestination.resolve(state(10, 20), state(10, 21), own))
    }
    @Test fun unchangedIdsAndForeignComponentsAreNotEvidenceOfApplication() {
        assertEquals(0, WallpaperApplyDestination.resolve(state(10, 20), state(10, 20), own))
        assertEquals(0, WallpaperApplyDestination.resolve(state(10, -1), state(11, -1, "other/service"), own))
        assertEquals(0, WallpaperApplyDestination.resolve(state(10, 20), state(10, 21, own, null), own))
    }
}
