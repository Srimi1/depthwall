package dev.srimi.depthwall

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.srimi.depthwall.data.SceneConfig
import dev.srimi.depthwall.data.SceneStore
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun libraryAndNavigationAreUsable() {
        compose.waitUntil(15000) { compose.onAllNodesWithTag("wall_wall_0_0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("explore_grid").performScrollToNode(hasTestTag("device_picks"))
        compose.onNodeWithTag("pick_device_carbon_weave").performClick()
        compose.onNodeWithTag("apply_wallpaper").assertExists()
        compose.onNodeWithTag("close_preview").performClick()
        compose.onNodeWithTag("search").performTextInput("Blue Hour")
        compose.onNodeWithTag("wall_wall_0_0").assert(hasText("Blue Hour"))
        compose.onNodeWithTag("wall_wall_0_0").performClick()
        compose.onNodeWithTag("apply_wallpaper").assertExists()
        compose.onNodeWithTag("close_preview").performClick()
        compose.onNodeWithTag("tab_1").performClick()
        compose.onNodeWithText("Beyond the still.").assertExists()
        compose.onNodeWithTag("tab_2").performClick()
        compose.onNodeWithText("Make it dimensional.").assertExists()
        compose.onNodeWithTag("tab_3").performClick()
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithText("Make it yours").assertExists()
    }
    @Test fun stagedPreviewCannotChangeActiveWallpaper() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=SceneStore(context)
        val home=SceneConfig(scene="saturn");val lock=SceneConfig(scene="moon")
        store.commit(home,1);store.commit(lock,2)
        store.stage(SceneConfig(scene="sun"));assertEquals(home,store.active(1));assertEquals(lock,store.active(2))
        store.clearPending();assertEquals(home,store.active(1))
    }
    @Test fun allBundledAssetsExistAndCountsMatch() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val counts=mutableMapOf<String,Int>()
        val ids=mutableSetOf<String>()
        for(file in listOf("catalog.json","extra_catalog.json","device_catalog.json")) {
            val catalog=JSONArray(context.assets.open(file).bufferedReader().use { it.readText() })
            for(i in 0 until catalog.length()) {
                val j=catalog.getJSONObject(i);val kind=j.getString("kind");counts[kind]=(counts[kind]?:0)+1
                assertTrue(ids.add(j.getString("id")))
                for(key in listOf("asset","thumbnail","foreground"))j.optString(key).takeIf { it.isNotEmpty() }?.let { path -> context.assets.open(path).use { assertTrue(it.read()!=-1) } }
            }
        }
        assertEquals(64,counts["STATIC"]);assertEquals(12,counts["LAYERED_DEPTH"]);assertEquals(7,counts["NATIVE_SCENE"])
        val picks=JSONArray(context.assets.open("device_picks.json").bufferedReader().use { it.readText() })
        for(i in 0 until picks.length())assertTrue(picks.getString(i) in ids)
    }
}
