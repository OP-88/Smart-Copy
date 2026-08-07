package com.github.op88.smartcopy.capture

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicReference

/**
 * FrameBuffer — Thread-safe single-frame bitmap store.
 *
 * Holds the most recently captured screen bitmap during an active Smart Copy
 * session. Old frames are recycled immediately when a new one is stored to
 * prevent memory leaks.
 *
 * The [AtomicReference] provides lock-free read access from the render thread
 * while [set] is called from the IO/capture thread.
 */
object FrameBuffer {

    private val _frame = AtomicReference<Bitmap?>(null)

    /** The current frozen screen frame, or null if no capture has occurred. */
    val current: Bitmap? get() = _frame.get()

    /**
     * Stores [bitmap] as the active frame. The previous frame (if any) is
     * recycled to free native memory immediately.
     */
    fun set(bitmap: Bitmap) {
        val old = _frame.getAndSet(bitmap)
        old?.recycle()
    }

    /**
     * Clears the stored frame and recycles native memory.
     * Call when the overlay is dismissed.
     */
    fun clear() {
        val old = _frame.getAndSet(null)
        old?.recycle()
    }
}
