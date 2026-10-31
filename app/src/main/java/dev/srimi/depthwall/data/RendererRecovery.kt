package dev.srimi.depthwall.data

/**
 * How the wallpaper engine reacts to a renderer failure. A lost EGL surface is rebuilt; other
 * frame errors are retried. Both are bounded so a permanent fault cannot spin the battery, and
 * the budget only resets after a frame actually reaches the screen.
 */
object RendererRecovery {
    /** Sent by Android when the wallpaper that is already active is applied again (hidden API constant). */
    const val COMMAND_REAPPLY = "android.wallpaper.reapply"
    const val MAX_ATTEMPTS = 5

    enum class Action { REBUILD, RETRY_FRAME, GIVE_UP }

    fun onFailure(surfaceLost: Boolean, failures: Int): Action = when {
        failures >= MAX_ATTEMPTS -> Action.GIVE_UP
        surfaceLost -> Action.REBUILD
        else -> Action.RETRY_FRAME
    }

    fun retryDelayMs(attempt: Int): Long = 500L shl attempt.coerceIn(0, MAX_ATTEMPTS - 1)
}
