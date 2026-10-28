package dev.srimi.depthwall

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.srimi.depthwall.data.SceneConfig
import dev.srimi.depthwall.data.SceneStore
import dev.srimi.depthwall.data.WallpaperApplyState
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses isolated preferences, leaving the user's applied wallpaper untouched. */
@RunWith(AndroidJUnit4::class)
class SceneApplyTest {
    private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences("scene_apply_regression_$name", mode)
    }
    private lateinit var store: SceneStore
    private val own = "dev.srimi.depthwall/dev.srimi.depthwall.wallpaper.DepthWallpaperService"
    private val home = SceneConfig(scene = "saturn")
    private val lock = SceneConfig(scene = "moon")
    private val depth = SceneConfig(wallpaperId = "depth_0", scene = "depth", background = "background.png")
    private fun state(home: Int, lock: Int) = WallpaperApplyState(home, lock, own, if (lock < 0) null else own)

    @Before fun setup() {
        context.getSharedPreferences("scenes", Context.MODE_PRIVATE).edit().clear().commit()
        store = SceneStore(context)
        store.commit(home, 1); store.commit(lock, 2)
    }
    @After fun cleanup() {
        context.getSharedPreferences("scenes", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun legacyPickerResultCommitsDepthWithoutEngineApplyCallback() {
        store.stage(depth, state(10, 20))
        // Recreate the store to cover activity/process recreation while the picker is open.
        store = SceneStore(context)
        assertEquals(3, store.finishPreview(true, state(21, -1), own))
        assertEquals(depth, store.active(1)); assertEquals(depth, store.active(2))
        assertEquals(0, store.finishPreview(true, state(21, -1), own))
    }
    @Test fun homeOnlyMigratesThePreviouslySharedHomeSceneToLock() {
        store.stage(depth, state(10, -1))
        assertEquals(1, store.finishPreview(true, state(11, 10), own))
        assertEquals(depth, store.active(1)); assertEquals(home, store.active(2))
    }
    @Test fun separateHomeOnlyAndLockOnlyKeepTheOtherDestination() {
        store.stage(depth, state(10, 20))
        assertEquals(1, store.finishPreview(true, state(21, 20), own))
        assertEquals(depth, store.active(1)); assertEquals(lock, store.active(2))
        store.stage(home, state(21, 20))
        assertEquals(2, store.finishPreview(true, state(21, 22), own))
        assertEquals(depth, store.active(1)); assertEquals(home, store.active(2))
    }
    @Test fun migrationRetainsOldHomeEvenAfterCallbackHasCommittedTheNewHome() {
        store.stage(depth, state(10, -1))
        store.confirmApplied(depth, 1)
        assertEquals(1, store.finishPreview(true, state(11, 10), own))
        assertEquals(depth, store.active(1)); assertEquals(home, store.active(2))
    }
    @Test fun cancelledAndUnconfirmedPreviewsPreserveBothScenes() {
        store.stage(depth, state(10, 20))
        assertEquals(0, store.finishPreview(false, state(21, -1), own))
        assertEquals(home, store.active(1)); assertEquals(lock, store.active(2))
        store.stage(depth, state(10, 20))
        assertEquals(0, store.finishPreview(true, state(10, 20), own))
        assertEquals(home, store.active(1)); assertEquals(lock, store.active(2))
        store.stage(depth, state(10, 20))
        assertEquals(0, store.finishPreview(true, null, own))
        assertEquals(home, store.active(1)); assertEquals(lock, store.active(2))
    }
    @Test fun explicitEngineDestinationRemainsAuthoritative() {
        store.stage(depth, state(10, 20))
        store.confirmApplied(depth, 2)
        val revision = store.revision()
        assertEquals(2, store.finishPreview(true, null, own))
        assertEquals(home, store.active(1)); assertEquals(depth, store.active(2))
        assertEquals(revision, store.revision())
    }
}
