package dev.srimi.depthwall.data

import android.view.Display

/** A normal installed wallpaper pauses on ambient panels, even if another display is awake. */
object DisplayRenderPolicy {
    fun canRender(visible: Boolean, interactive: Boolean, displayState: Int): Boolean =
        visible && interactive && displayState == Display.STATE_ON

    fun stateLabel(state: Int): String = when (state) {
        Display.STATE_ON -> "ON"
        Display.STATE_OFF -> "OFF"
        Display.STATE_DOZE -> "DOZE"
        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
        Display.STATE_ON_SUSPEND -> "ON_SUSPEND"
        Display.STATE_VR -> "VR"
        Display.STATE_UNKNOWN -> "UNKNOWN"
        else -> "UNKNOWN($state)"
    }
}
