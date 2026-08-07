package com.github.op88.smartcopy.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OcrEngine
 *
 * Wraps Google ML Kit's [TextRecognizer] (bundled, offline variant).
 * Exposes a suspending [recognize] function that processes a [Bitmap] and
 * returns a structured [Text] result including per-word bounding boxes.
 *
 * The recognizer is a process-scoped singleton managed by ML Kit internally;
 * no manual lifecycle management is required. Call [close] only when the
 * application is shutting down.
 *
 * All work is dispatched to [Dispatchers.Default] to avoid blocking the
 * main thread during inference.
 */
class OcrEngine {

    // TextRecognizerOptions.DEFAULT_OPTIONS uses the BUNDLED model.
    // This is the key distinction from the thin Play Services client.
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Runs offline OCR on the given [bitmap] and returns a [Text] object
     * containing all recognized [Text.TextBlock], [Text.Line], and
     * [Text.Element] entries with their bounding [android.graphics.Rect]s.
     *
     * @throws RuntimeException if ML Kit recognition fails internally.
     */
    suspend fun recognize(bitmap: Bitmap): Text = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result)
                }
                .addOnFailureListener { exception ->
                    if (cont.isActive) cont.resumeWithException(exception)
                }
        }
    }

    /**
     * Convenience overload: runs OCR on a [Bitmap] cropped to the user's
     * selection [android.graphics.Rect] before full-image inference.
     *
     * Cropping first reduces inference time significantly on dense screens.
     */
    suspend fun recognizeCropped(
        bitmap: Bitmap,
        cropRect: android.graphics.Rect,
    ): Text {
        val safe = android.graphics.Rect(cropRect).apply {
            left   = left.coerceAtLeast(0)
            top    = top.coerceAtLeast(0)
            right  = right.coerceAtMost(bitmap.width)
            bottom = bottom.coerceAtMost(bitmap.height)
        }
        val cropped = Bitmap.createBitmap(
            bitmap,
            safe.left, safe.top,
            safe.width(), safe.height()
        )
        return recognize(cropped)
    }

    fun close() = recognizer.close()
}
