package com.github.op88.smartcopy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.op88.smartcopy.overlay.EdgeBubbleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.github.op88.smartcopy.settings.Preferences

/**
 * BootReceiver
 *
 * Listens for [Intent.ACTION_BOOT_COMPLETED] to re-launch the [EdgeBubbleService]
 * if the user had it enabled before the device rebooted.
 *
 * Resource note: This receiver is invoked ONCE per boot, checks a DataStore flag,
 * and either starts the edge bubble service or does nothing. It does not stay
 * resident in memory after returning from [onReceive].
 *
 * Requires RECEIVE_BOOT_COMPLETED permission in manifest.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Use goAsync to safely read DataStore off the main thread
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = Preferences(context)
                val bubbleEnabled = prefs.edgeBubbleEnabledFlow.first()
                if (bubbleEnabled) {
                    EdgeBubbleService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
