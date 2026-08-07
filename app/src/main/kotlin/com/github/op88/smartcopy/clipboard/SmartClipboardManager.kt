package com.github.op88.smartcopy.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * SmartClipboardManager
 *
 * Handles copying text to the system clipboard and scheduling an automatic
 * TTL (Time-To-Live) wipe via [WorkManager].
 *
 * Security model:
 *  - Clipboard data is written with a plaintext label only.
 *  - A [ClipboardWipeWorker] is enqueued immediately after any copy operation
 *    with an initial delay equal to the configured TTL.
 *  - The work is tagged [WIPE_WORK_TAG] so any new copy cancels the previous
 *    pending wipe and reschedules it with the fresh TTL.
 *  - TTL of 0 means "never auto-wipe".
 *
 * Usage:
 * ```kotlin
 * val sc = SmartClipboardManager(context)
 * sc.copy("extracted text", ttlSeconds = 30L)
 * ```
 */
class SmartClipboardManager(private val context: Context) {

    companion object {
        const val WIPE_WORK_TAG = "clipboard_wipe"
    }

    private val systemClipboard: ClipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    /**
     * Copies [text] to the system clipboard and schedules an auto-wipe
     * after [ttlSeconds]. Pass 0 to disable auto-wipe.
     */
    fun copy(text: String, ttlSeconds: Long) {
        val clip = ClipData.newPlainText("SmartCopy", text)
        systemClipboard.setPrimaryClip(clip)

        // Cancel any previously scheduled wipe first
        WorkManager.getInstance(context).cancelAllWorkByTag(WIPE_WORK_TAG)

        if (ttlSeconds > 0L) {
            scheduleWipe(ttlSeconds)
        }
    }

    /** Immediately clears the clipboard regardless of any pending TTL. */
    fun wipeNow() {
        WorkManager.getInstance(context).cancelAllWorkByTag(WIPE_WORK_TAG)
        val emptyClip = ClipData.newPlainText("", "")
        systemClipboard.setPrimaryClip(emptyClip)
    }

    private fun scheduleWipe(delaySeconds: Long) {
        val request = OneTimeWorkRequestBuilder<ClipboardWipeWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .addTag(WIPE_WORK_TAG)
            .setConstraints(Constraints.NONE)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                WIPE_WORK_TAG,
                ExistingWorkPolicy.REPLACE,
                request,
            )
    }
}
