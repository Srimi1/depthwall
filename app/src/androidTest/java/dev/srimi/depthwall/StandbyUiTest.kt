package dev.srimi.depthwall

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.srimi.depthwall.data.*
import dev.srimi.depthwall.standby.StandbySettingsActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StandbyUiTest {
    @get:Rule val compose = createAndroidComposeRule<StandbySettingsActivity>()
    private val store get() = StandbySettingsStore(InstrumentationRegistry.getInstrumentation().targetContext)
    private lateinit var original: StandbySettings

    @Before fun setup() = runBlocking {
        original = store.flow.first()
        store.setEnabled(false)
        store.setDesign(StandbyDesign.DIGITAL)
        store.setNightMode(StandbyNightMode.OFF)
    }

    @After fun restore() = runBlocking {
        store.setEnabled(original.enabled)
        store.setDesign(original.design)
        store.setNightMode(original.nightMode)
        store.setPhoto(original.photoWallpaperId)
    }

    private fun preview() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("standby_preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("standby_preview").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("standby_screen").fetchSemanticsNodes().isNotEmpty() }
        dismissImmersivePrompt()
    }

    /** Fresh devices show Android's full-screen tutorial above the app and steal window focus. */
    private fun dismissImmersivePrompt() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            val button = automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("android:id/ok")?.firstOrNull()
            if (button?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
            SystemClock.sleep(100)
        }
    }

    private fun face(design: StandbyDesign) = compose.onNode(hasTestTag("standby_face_${design.name}") and
        hasAnyAncestor(hasTestTag("standby_screen")))

    private fun currentBrightness(): Float {
        var brightness = Float.NaN
        compose.runOnIdle {
            brightness = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .single().window.attributes.screenBrightness
        }
        return brightness
    }

    @Test fun enableSwitchPersistsWithoutChangingTheSelectedDesign() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("standby_enabled").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("standby_enabled").performClick()
        compose.waitUntil(10_000) { runBlocking { store.flow.first().enabled } }
        compose.onNodeWithTag("standby_enabled").assertIsOn()
        compose.onNodeWithTag("standby_enabled").performClick()
        compose.waitUntil(10_000) { runBlocking { !store.flow.first().enabled } }
        compose.onNodeWithTag("standby_enabled").assertIsOff()
        assertEquals(StandbyDesign.DIGITAL, runBlocking { store.flow.first().design })
    }

    @Test fun unrestrictedPreviewSwipesAllDesignsAndRemembersSelection() {
        preview()
        face(StandbyDesign.DIGITAL).assertIsDisplayed()
        for (design in listOf(StandbyDesign.FLIP, StandbyDesign.ANALOGUE, StandbyDesign.CALENDAR, StandbyDesign.PHOTO)) {
            compose.onNodeWithTag("standby_screen").performTouchInput { swipeLeft(durationMillis = 300) }
            compose.waitUntil(10_000) { runBlocking { store.flow.first().design == design } }
            face(design).assertIsDisplayed()
        }
        compose.onNode(hasTestTag("standby_photo") and hasAnyAncestor(hasTestTag("standby_screen"))).assertIsDisplayed()
        Espresso.pressBack()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("standby_screen").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("standby_designs").performScrollToNode(hasTestTag("standby_design_PHOTO"))
        compose.onNodeWithTag("standby_design_PHOTO").assertIsSelected()
    }

    @Test fun nightModeHidesPhotoAndStaysRedUntilDisabled() {
        runBlocking { store.setDesign(StandbyDesign.PHOTO); store.setNightMode(StandbyNightMode.ON) }
        preview()
        face(StandbyDesign.PHOTO).assertIsDisplayed()
        compose.onNode(hasTestTag("standby_photo") and hasAnyAncestor(hasTestTag("standby_screen"))).assertDoesNotExist()
        assertEquals(.03f, currentBrightness(), .001f)
        runBlocking { store.setNightMode(StandbyNightMode.OFF) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("standby_photo") and
            hasAnyAncestor(hasTestTag("standby_screen"))).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(.2f, currentBrightness(), .001f)
        Espresso.pressBack()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("standby_screen").fetchSemanticsNodes().isEmpty() }
        assertEquals(-1f, currentBrightness(), .001f)
    }
}
