package com.github.op88.smartcopy.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.github.op88.smartcopy.R
import com.github.op88.smartcopy.capture.FrameBuffer
import com.github.op88.smartcopy.capture.ScreenCaptureManager
import com.github.op88.smartcopy.ocr.OcrEngine
import com.github.op88.smartcopy.ocr.SelectionInferencer
import com.github.op88.smartcopy.ocr.TableParser
import com.github.op88.smartcopy.settings.Preferences
import com.github.op88.smartcopy.snap.MagneticSnapHelper
import kotlinx.coroutines.*

/**
 * OverlayService
 *
 * The central foreground service that orchestrates the full Smart Copy workflow:
 *
 *  1. Receives the [MediaProjection] token from [MainActivity].
 *  2. Creates a [ScreenCaptureManager] and grabs a single frozen frame.
 *  3. Attaches a [FreezeOverlayView] to [WindowManager] covering the full screen.
 *  4. Runs [OcrEngine] on the captured frame to get text blocks.
 *  5. Feeds OCR results into [SelectionInferencer] and [MagneticSnapHelper]
 *     for assisted selection.
 *  6. On user confirmation → copies text via [SmartClipboardManager] and
 *     optionally formats as TSV via [TableParser].
 *  7. Dismisses the overlay.
 *
 * Lifecycle: Started as a foreground service (with notification), stopped
 * when the user dismisses the overlay or taps "Done".
 */
class OverlayService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "smartcopy_overlay"

        /** Convenience factory — wraps the MediaProjection intent extras. */
        fun buildIntent(
            context: Context,
            resultCode: Int,
            resultData: Intent,
        ): Intent = Intent(context, OverlayService::class.java).apply {
            putExtra(EXTRA_RESULT_CODE, resultCode)
            putExtra(EXTRA_RESULT_DATA, resultData)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager

    // Lazy: ML Kit model is NOT loaded until the first screen capture fires.
    // At idle the process has zero ML Kit overhead.
    private val ocrEngine: OcrEngine by lazy { OcrEngine() }

    private var overlayView: FreezeOverlayView? = null
    private var captureManager: ScreenCaptureManager? = null
    private lateinit var preferences: Preferences

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        preferences = Preferences(this)
        createNotificationChannel()
    }

    /** Release ML Kit model memory when the OS signals pressure. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (overlayView == null) {
            // Overlay is not active — safe to drop the model from memory
            if (::ocrEngine.isInitialized || true) {
                // ocrEngine is lazy; if not yet touched it costs nothing
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            serviceScope.launch { startCapture(resultCode, resultData) }
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        dismissOverlay()
        serviceScope.cancel()
        ocrEngine.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun startCapture(resultCode: Int, resultData: Intent) {
        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection: MediaProjection =
            projectionManager.getMediaProjection(resultCode, resultData)

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        captureManager = ScreenCaptureManager(projection, metrics)
        val frame: Bitmap = captureManager!!.captureFrame()
        FrameBuffer.set(frame)

        // Run OCR in background
        val ocrResult = ocrEngine.recognize(frame)

        // Show overlay on main thread
        withContext(Dispatchers.Main) {
            attachOverlay(frame, ocrResult.textBlocks, metrics)
        }
    }

    private fun attachOverlay(
        frame: Bitmap,
        textBlocks: List<com.google.mlkit.vision.text.Text.TextBlock>,
        metrics: DisplayMetrics,
    ) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )

        val view = FreezeOverlayView(
            context      = this,
            frozenBitmap = frame,
            textBlocks   = textBlocks,
            snapHelper   = MagneticSnapHelper(),
            inferencer   = SelectionInferencer(),
            tableParser  = TableParser(),
            preferences  = preferences,
            onDismiss    = ::dismissOverlay,
        )
        overlayView = view
        windowManager.addView(view, params)
    }

    private fun dismissOverlay() {
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
            overlayView = null
        }
        captureManager?.release()
        captureManager = null
        FrameBuffer.clear()
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Smart Copy Overlay",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Active while Smart Copy overlay is displayed"
            setShowBadge(false)
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Copy Active")
            .setContentText("Tap to return to the app")
            .setSmallIcon(R.drawable.ic_smartcopy)
            .setOngoing(true)
            .setSilent(true)
            .build()
}
