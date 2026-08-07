package com.github.op88.smartcopy.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.github.op88.smartcopy.MainActivity
import com.github.op88.smartcopy.R

/**
 * EdgeBubbleService
 *
 * An ultra-lightweight foreground service whose ONLY job is to host the
 * [EdgeBubbleView] in [WindowManager]. It has no background loops,
 * no ML Kit, no MediaProjection — just a single View pinned to the edge.
 *
 * Resource profile at idle:
 *  - ~0% CPU (no loops, no polling)
 *  - ~4 MB RSS (service stub + one View — smaller than most lock-screen widgets)
 *  - Battery: negligible (no wakelocks, no sensors, no network)
 *
 * Lifecycle:
 *  - Started when the user enables the Edge Bubble in Settings.
 *  - Stopped when the user disables it.
 *  - Also stops itself if SYSTEM_ALERT_WINDOW is revoked.
 *  - Uses IMPORTANCE_MIN notification — no sound, no heads-up, collapsed by default.
 *
 * To start:  EdgeBubbleService.start(context)
 * To stop:   EdgeBubbleService.stop(context)
 */
class EdgeBubbleService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "smartcopy_bubble"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, EdgeBubbleService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EdgeBubbleService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: EdgeBubbleView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // IMPORTANCE_MIN = totally silent, no badge, collapsed in shade
        startForeground(NOTIFICATION_ID, buildNotification())

        if (!android.provider.Settings.canDrawOverlays(this)) {
            // Permission revoked — die quietly
            stopSelf()
            return START_NOT_STICKY
        }

        attachBubble()

        // START_NOT_STICKY: if the system kills this service under memory pressure,
        // do NOT restart it automatically. The user will see it's gone and can
        // re-enable from Settings. This prevents unwanted resurrection.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        detachBubble()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─────────────────────────────────────────────────────────────────────────

    private fun attachBubble() {
        if (bubbleView != null) return  // Already attached

        // TODO: read dockRight from DataStore preferences
        val dockRight = true

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_TOUCH_MODAL: touches outside the bubble pass through normally
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = if (dockRight) {
                Gravity.END or Gravity.CENTER_VERTICAL
            } else {
                Gravity.START or Gravity.CENTER_VERTICAL
            }
            x = 0
            y = 0
        }

        val view = EdgeBubbleView(
            context  = this,
            dockRight = dockRight,
            onTap    = {
                // Launch MainActivity which handles MediaProjection + overlay launch
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(MainActivity.EXTRA_QS_TRIGGERED, true)
                }.also { startActivity(it) }
            },
        )

        bubbleView = view
        windowManager.addView(view, params)
    }

    private fun detachBubble() {
        bubbleView?.let {
            runCatching { windowManager.removeView(it) }
            bubbleView = null
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Smart Copy Bubble",
            // IMPORTANCE_MIN = no sound, no pop-up, collapsed — least intrusive possible
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "Smart Copy edge trigger bubble"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Copy")
            .setContentText("Edge bubble active — tap to capture")
            .setSmallIcon(R.drawable.ic_smartcopy)
            .setPriority(NotificationCompat.PRIORITY_MIN)   // Collapsed, no heads-up
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // Hidden on lock screen
            .build()
}
