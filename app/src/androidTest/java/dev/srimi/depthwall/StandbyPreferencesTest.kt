package dev.srimi.depthwall

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.srimi.depthwall.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StandbyPreferencesTest {
    @Test fun preferencesSurviveFreshReadersAndWallpaperDraftSaves() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = StandbySettingsStore(context)
        val original = store.flow.first()
        val wallpapers = SettingsStore(context)
        val originalWallpaper = wallpapers.flow.first()
        val scenes = SceneStore(context)
        val home = scenes.active(1)
        val lock = scenes.active(2)
        try {
            store.setEnabled(true)
            store.setDesign(StandbyDesign.CALENDAR)
            store.setNightMode(StandbyNightMode.ON)
            store.setPhoto("wall_0_1")
            val expected = StandbySettings(true, StandbyDesign.CALENDAR, StandbyNightMode.ON, "wall_0_1")
            assertEquals(expected, StandbySettingsStore(context).flow.first())
            // A previously opened wallpaper settings dialog cannot overwrite the new preferences.
            wallpapers.save(originalWallpaper.copy(appTheme = "Space blue"))
            assertEquals(expected, StandbySettingsStore(context).flow.first())
            // A gesture changes only the design, preserving concurrent/night/photo preferences.
            store.setDesign(StandbyDesign.FLIP)
            assertEquals(expected.copy(design = StandbyDesign.FLIP), store.flow.first())
            assertEquals(home, scenes.active(1))
            assertEquals(lock, scenes.active(2))
        } finally {
            store.setEnabled(original.enabled)
            store.setDesign(original.design)
            store.setNightMode(original.nightMode)
            store.setPhoto(original.photoWallpaperId)
            wallpapers.save(originalWallpaper)
        }
    }
}
