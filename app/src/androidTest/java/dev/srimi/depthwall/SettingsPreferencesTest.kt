package dev.srimi.depthwall

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.srimi.depthwall.data.SettingsStore
import dev.srimi.depthwall.data.WallpaperItem
import dev.srimi.depthwall.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPreferencesTest {
    @Test fun savedPreferencesReachFreshPreviewsWithoutChangingActiveScenes() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DepthWallApp
        val settings = SettingsStore(app)
        val original = settings.flow.first()
        val home = app.repository.scenes.active(1)
        val lock = app.repository.scenes.active(2)
        val store = ViewModelStore()
        try {
            val chosen = original.copy(
                appTheme = "Space blue",
                deviceProfile = "Current screen",
                downloadSize = "4K portrait",
                depthEnabled = true,
                depthParallax = .72f,
                depthClock = "Minimal",
                depthClockY = .51f,
            )
            settings.save(chosen)
            // A newly constructed reader sees all settings, including values previously absent.
            assertEquals(chosen, SettingsStore(app).flow.first())
            val vm = withContext(Dispatchers.Main) {
                ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[MainViewModel::class.java]
            }
            withTimeout(10_000) { vm.settings.first { it == chosen } }
            val item = WallpaperItem("settings_preview_test", "Settings preview", "Imports", localPath = "/test/image.jpg")
            val draft = withContext(Dispatchers.Main) { vm.draft(item).value }
            assertEquals(chosen.depthParallax, draft.parallax, .0001f)
            assertEquals(chosen.depthClock, draft.clock)
            assertEquals(chosen.depthClockY, draft.clockY, .0001f)
            assertEquals(item.localPath, draft.background)
            assertTrue(vm.settings.value.depthEnabled)
            assertEquals(home, app.repository.scenes.active(1))
            assertEquals(lock, app.repository.scenes.active(2))
        } finally {
            settings.save(original)
            withContext(Dispatchers.Main) { store.clear() }
        }
    }
}
