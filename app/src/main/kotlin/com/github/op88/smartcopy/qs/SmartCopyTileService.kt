package com.github.op88.smartcopy.qs

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.github.op88.smartcopy.MainActivity

/**
 * SmartCopyTileService
 *
 * Android Quick Settings Tile that triggers Smart Copy with a single tap
 * from the notification shade — no need to open the app first.
 *
 * The tile indicates active state while the overlay is running.
 *
 * Permission flow:
 *  - If SYSTEM_ALERT_WINDOW is not granted, the tile opens [MainActivity]
 *    which guides the user through the permission grant.
 *  - If the permission is already granted, the tile immediately launches
 *    [OverlayService] via [MainActivity] with the capture intent.
 */
class SmartCopyTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Smart Copy"
            updateTile()
        }
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        // Collapse the shade and launch MainActivity in QS-trigger mode.
        // MainActivity handles permission checks and forwards to OverlayService.
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_QS_TRIGGERED, true)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = android.app.PendingIntent.getActivity(
                this, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }

        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
