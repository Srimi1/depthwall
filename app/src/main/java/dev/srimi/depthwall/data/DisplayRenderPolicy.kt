package dev.srimi.depthwall.data

import android.view.Display

/** A normal installed wallpaper pauses on ambient panels, even if another display is awake. */
object DisplayRenderPolicy {
    enum class Mode { RUN, AMBIENT_FRAME, STOP }

    fun canRender(visible: Boolean, interactive: Boolean, displayState: Int): Boolean =
        visible && interactive && displayState == Display.STATE_ON

    /**
     * Only the ambient-capable engine (a system install holding AMBIENT_WALLPAPER) may draw while
     * the panel dozes, and then just one still frame. A fully awake panel always wins over a stale
     * ambient callback, because SystemUI turns the display on before it reports leaving AOD.
     */
    fun mode(visible: Boolean, interactive: Boolean, displayState: Int, ambientCapable: Boolean, ambientSignal: Boolean): Mode = when {
        canRender(visible, interactive, displayState) -> Mode.RUN
        ambientCapable && visible && (ambientSignal || isDozing(displayState)) -> Mode.AMBIENT_FRAME
        else -> Mode.STOP
    }

    fun isDozing(state: Int): Boolean = state == Display.STATE_DOZE || state == Display.STATE_DOZE_SUSPEND

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
