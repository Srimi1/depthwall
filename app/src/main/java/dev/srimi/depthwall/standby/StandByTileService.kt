package dev.srimi.depthwall.standby

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** A Quick Settings shortcut that opens StandBy straight away. */
class StandByTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            subtitle = "Charging clock"
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // The Intent overload is the only one before API 34.
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, StandByActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34)
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        else @Suppress("DEPRECATION") startActivityAndCollapse(intent)
    }
}
