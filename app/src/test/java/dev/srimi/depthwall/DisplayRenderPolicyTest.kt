package dev.srimi.depthwall

import android.view.Display
import dev.srimi.depthwall.data.DisplayRenderPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayRenderPolicyTest {
    @Test fun ambientPanelPausesEvenWhenAnotherDisplayKeepsTheDeviceInteractive() {
        for (state in listOf(Display.STATE_OFF, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND, Display.STATE_ON_SUSPEND)) {
            assertFalse("State $state must not render", DisplayRenderPolicy.canRender(true, true, state))
        }
    }

    @Test fun awakePanelStillRequiresVisibilityAndAnInteractiveDevice() {
        assertTrue(DisplayRenderPolicy.canRender(true, true, Display.STATE_ON))
        assertFalse(DisplayRenderPolicy.canRender(false, true, Display.STATE_ON))
        assertFalse(DisplayRenderPolicy.canRender(true, false, Display.STATE_ON))
        assertFalse(DisplayRenderPolicy.canRender(false, false, Display.STATE_ON))
    }

    @Test fun sleepAodPulseAndWakeOnlyResumeRenderingWhenPanelIsFullyOn() {
        val states = listOf(Display.STATE_ON, Display.STATE_OFF, Display.STATE_DOZE,
            Display.STATE_DOZE_SUSPEND, Display.STATE_DOZE, Display.STATE_ON)
        val eligible = states.map { DisplayRenderPolicy.canRender(true, true, it) }
        assertTrue(eligible.first())
        assertTrue(eligible.last())
        assertTrue(eligible.subList(1, eligible.lastIndex).none { it })
    }

    @Test fun missingOrUnrecognizedDisplaysDoNotStartBackgroundRendering() {
        assertFalse(DisplayRenderPolicy.canRender(true, true, Display.STATE_UNKNOWN))
        assertFalse(DisplayRenderPolicy.canRender(true, true, -1))
        assertFalse(DisplayRenderPolicy.canRender(true, true, Int.MAX_VALUE))
    }
}
