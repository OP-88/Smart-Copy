package com.github.op88.smartcopy.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.*
import android.widget.FrameLayout
import android.widget.Magnifier
import com.github.op88.smartcopy.clipboard.SmartClipboardManager
import com.github.op88.smartcopy.ocr.SelectionInferencer
import com.github.op88.smartcopy.ocr.TableParser
import com.github.op88.smartcopy.settings.Preferences
import com.github.op88.smartcopy.snap.MagneticSnapHelper
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.*
import android.widget.Button
import android.widget.FrameLayout.LayoutParams

/**
 * FreezeOverlayView
 *
 * A full-screen [FrameLayout] that:
 *  1. Renders the [frozenBitmap] as the background (screen freeze effect).
 *  2. Draws a semi-transparent darkening scrim over it.
 *  3. Tracks finger movements to define a selection [Rect].
 *  4. Punches a clear "window" in the scrim over the selection.
 *  5. Snaps the selection via [MagneticSnapHelper] and infers text via [SelectionInferencer].
 *  6. Shows a sub-pixel [Magnifier] loupe at the touch point.
 *  7. On finger-up, adds an [ActionBarView] as a child view (Copy / TSV / Dismiss).
 *
 * Extends [FrameLayout] (not [View]) so it can host [ActionBarView] as a proper child
 * while still overriding [onDraw] for the custom canvas rendering underneath.
 */
@SuppressLint("ClickableViewAccessibility")
class FreezeOverlayView(
    context: Context,
    private val frozenBitmap: Bitmap,
    private val textBlocks: List<Text.TextBlock>,
    private val snapHelper: MagneticSnapHelper,
    private val inferencer: SelectionInferencer,
    private val tableParser: TableParser,
    private val preferences: Preferences,
    private val onDismiss: () -> Unit,
) : FrameLayout(context) {

    private val viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val clipboardManager = SmartClipboardManager(context)

    // Magnifier — Builder requires API 29; fall back to deprecated constructor on API 28
    private val magnifier: Magnifier by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Magnifier.Builder(this)
                .setSize(200, 200)
                .setInitialZoom(3.0f)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Magnifier(this)
        }
    }

    // ── Drawing paints ──────────────────────────────────────────────────────
    private val bitmapPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scrimPaint    = Paint().apply { color = Color.argb(120, 0, 0, 0) }
    private val selFillPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 56, 189, 248)
    }
    private val selStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#38BDF8")
        strokeWidth = 3f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.FILL
    }

    // ── Selection state ──────────────────────────────────────────────────────
    private var startX = 0f; private var startY = 0f
    private var endX   = 0f; private var endY   = 0f
    private var isDrawing = false
    private val selectionRect = Rect()
    private var lastInferenceResult: SelectionInferencer.InferenceResult? = null

    // ── Action bar child ─────────────────────────────────────────────────────
    private var actionBar: ActionBarView? = null

    init {
        // Required: FrameLayout sets willNotDraw=true by default,
        // which would skip our onDraw entirely.
        setWillNotDraw(false)

        // Add a Cancel button to the top-right corner
        val cancelButton = Button(context).apply {
            text = "Cancel"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(180, 0, 0, 0))
            setOnClickListener { onDismiss() }
        }
        val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = 100 // Leave space for status bar
            rightMargin = 40
        }
        addView(cancelButton, params)
    }

    // ── Drawing ──────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        // 1. Frozen screen background
        canvas.drawBitmap(
            frozenBitmap, null,
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            bitmapPaint
        )

        // 2. Dark scrim
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        if (!selectionRect.isEmpty) {
            val rf = selectionRect.toRectF()

            // 3. Punch clear window in scrim over selection area
            canvas.save()
            canvas.clipRect(rf)
            canvas.drawBitmap(
                frozenBitmap, null,
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                bitmapPaint
            )
            canvas.restore()

            // 4. Selection fill + stroke
            canvas.drawRect(rf, selFillPaint)
            canvas.drawRect(rf, selStrokePaint)

            // 5. Corner handles
            val r = 8f
            listOf(rf.left to rf.top, rf.right to rf.top,
                   rf.left to rf.bottom, rf.right to rf.bottom)
                .forEach { (hx, hy) -> canvas.drawCircle(hx, hy, r, handlePaint) }
        }
        // Note: super.onDraw not needed — FrameLayout draws children via dispatchDraw
    }

    // ── Touch ────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // If the user touches the very bottom edge (e.g. bottom 150px),
        // let the system handle it for navigation gestures/buttons.
        if (event.y > height - 150) {
            return false
        }
        handleTouch(event)
        return true
    }

    /**
     * Overrides touch dispatch so that [ActionBarView] children receive clicks
     * normally, while unclaimed touches fall through to [onTouchEvent] for
     * drawing the selection.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Give children first chance (ActionBarView buttons)
        if (actionBar != null && super.dispatchTouchEvent(event)) return true
        return onTouchEvent(event)
    }

    private fun handleTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                hideActionBar()
                isDrawing = true
                startX = event.x; startY = event.y
                endX   = event.x; endY   = event.y
                updateSelectionRect()
                magnifier.show(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                endX = event.x; endY = event.y
                updateSelectionRect()
                magnifier.show(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDrawing = false
                magnifier.dismiss()
                endX = event.x; endY = event.y
                updateSelectionRect()
                if (event.action == MotionEvent.ACTION_UP) {
                    runSnapAndInfer()
                }
                invalidate()
            }
        }
    }

    private fun updateSelectionRect() {
        selectionRect.set(
            minOf(startX, endX).toInt(), minOf(startY, endY).toInt(),
            maxOf(startX, endX).toInt(), maxOf(startY, endY).toInt(),
        )
    }

    private fun runSnapAndInfer() {
        viewScope.launch {
            val snapped = withContext(Dispatchers.Default) {
                snapHelper.snapRect(selectionRect, textBlocks)
            }
            selectionRect.set(snapped)

            lastInferenceResult = withContext(Dispatchers.Default) {
                inferencer.infer(snapped, textBlocks)
            }

            invalidate()
            showActionBar()
        }
    }

    // ── Action bar ───────────────────────────────────────────────────────────

    private fun showActionBar() {
        val result = lastInferenceResult ?: return
        val hasTable = tableParser.parse(textBlocks) != null

        val bar = ActionBarView(
            context    = context,
            anchorRect = selectionRect,
            hasTable   = hasTable,
            onCopy     = {
                viewScope.launch {
                    clipboardManager.copy(result.plainText, preferences.clipboardTtlSeconds())
                    onDismiss()
                }
            },
            onCopyTsv  = {
                viewScope.launch {
                    val tsv = tableParser.parse(textBlocks) ?: result.plainText
                    clipboardManager.copy(tsv, preferences.clipboardTtlSeconds())
                    onDismiss()
                }
            },
            onDismiss  = {
                selectionRect.setEmpty()
                lastInferenceResult = null
                hideActionBar()
                invalidate()
            },
        ).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
        actionBar = bar
        addView(bar)   // FrameLayout.addView
    }

    private fun hideActionBar() {
        actionBar?.let { removeView(it) }  // FrameLayout.removeView
        actionBar = null
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onDetachedFromWindow() {
        magnifier.dismiss()
        viewScope.cancel()
        super.onDetachedFromWindow()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun Rect.toRectF() = RectF(
        left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()
    )
}
