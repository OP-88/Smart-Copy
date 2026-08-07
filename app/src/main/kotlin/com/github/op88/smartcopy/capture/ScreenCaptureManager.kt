package com.github.op88.smartcopy.capture

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * ScreenCaptureManager
 *
 * Manages the [MediaProjection] lifecycle and captures a single screen frame
 * as a [Bitmap]. The frame is read via [ImageReader] with
 * [ImageReader.ACQUIRE_LATEST_IMAGE] semantics to guarantee a fresh snapshot.
 *
 * Usage:
 * ```
 * val manager = ScreenCaptureManager(projection, metrics)
 * val bitmap = manager.captureFrame()
 * manager.release()
 * ```
 */
class ScreenCaptureManager(
    private val mediaProjection: MediaProjection,
    private val metrics: DisplayMetrics,
) {
    private val width: Int  = metrics.widthPixels
    private val height: Int = metrics.heightPixels
    private val density: Int = metrics.densityDpi

    private val imageReader: ImageReader = ImageReader.newInstance(
        width, height, PixelFormat.RGBA_8888, /* maxImages= */ 2
    )
    private var virtualDisplay: VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())

    init {
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "SmartCopyCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            /* callback= */ null,
            handler,
        )
    }

    /**
     * Captures the current screen frame and returns it as a [Bitmap].
     * Suspends until a valid image is available (≤ 300 ms typical on idle UI).
     *
     * Must be called from a coroutine context — switches to [Dispatchers.IO]
     * internally for image acquisition.
     */
    suspend fun captureFrame(): Bitmap = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride  = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    // Crop to exact display dimensions (strips row padding)
                    val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    bitmap.recycle()

                    if (cont.isActive) cont.resume(cropped)
                } finally {
                    image.close()
                    // Remove listener to avoid firing again
                    imageReader.setOnImageAvailableListener(null, null)
                }
            }, handler)
        }
    }

    /**
     * Releases the [VirtualDisplay] and [MediaProjection].
     * Call this when the overlay is dismissed.
     */
    fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader.close()
        mediaProjection.stop()
    }
}
