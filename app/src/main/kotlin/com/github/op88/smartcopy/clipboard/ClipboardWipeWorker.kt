package com.github.op88.smartcopy.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ClipboardWipeWorker
 *
 * A [CoroutineWorker] that runs after the configured Clipboard TTL expires
 * and overwrites the system clipboard with an empty string.
 *
 * This approach is resilient to:
 *  - App process being killed (WorkManager persists across restarts).
 *  - Device reboots (WorkManager re-schedules on boot if needed).
 *
 * The wipe writes an empty [ClipData] rather than clearing to null, because
 * [ClipboardManager.clearPrimaryClip] requires API 28 targeting with
 * additional flags on some manufacturers. An empty clip is universally safe.
 */
class ClipboardWipeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Main) {
        try {
            val clipboard = applicationContext
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            // Overwrite with empty clip — scrubs any previously extracted text
            val emptyClip = ClipData.newPlainText("", "")
            clipboard.setPrimaryClip(emptyClip)

            Result.success()
        } catch (e: Exception) {
            // Retry once on failure (e.g. clipboard service temporarily unavailable)
            if (runAttemptCount < 1) Result.retry() else Result.failure()
        }
    }
}
