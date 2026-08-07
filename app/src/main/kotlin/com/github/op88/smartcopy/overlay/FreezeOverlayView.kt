package com.github.op88.smartcopy.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.widget.Magnifier
import com.github.op88.smartcopy.clipboard.SmartClipboardManager
import com.github.op88.smartcopy.ocr.SelectionInferencer
import com.github.op88.smartcopy.ocr.TableParser
import com.github.op88.smartcopy.settings.Preferences
import com.github.op88.smartcopy.snap.MagneticSnapHelper
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.*

/**
 * FreezeOverlayView
 *
 * A full-screen [View] that:
 *  1. Renders the [frozenBitmap] as the background (screen freeze effect).
 *  2. Draws a semi-transparent darkening scrim.
 *  3. Tracks finger movements to define a selection [Rect].
 *  4. Snaps the selection rect via [MagneticSnapHelper] and infers text
 *     using [SelectionInferencer].
 *  5. Shows a sub-pixel [Magnifier] loupe at the touch point.
 *  6. On finger-up, shows the contextual [ActionBarView].
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
) : View(context) {

    private val viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val clipboardManager = SmartClipboardManager(context)

    // Magnifier (API 28+)
    private val magnifier = Magnifier.Builder(this)
        .setSize(200, 200)
        .setInitialZoom(3.0f)
        .build()

    // Drawing paints
    private val bitmapPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scrimPaint   = Paint().apply { color = Color.argb(120, 0, 0, 0) }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#38BDF8")  // cyan-400
        strokeWidth = 3f
    }
    private val selectionFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 56, 189, 248)
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.FILL
    }

    // State
    private var startX = 0f
    private var startY = 0f
    private var endX   = 0f
    private var endY   = 0f
    private var isDrawing = false
    private val selectionRect = Rect()
    private var lastInferenceResult: SelectionInferencer.InferenceResult? = null

    // Action bar
    private var actionBar: ActionBarView? = null

    init {
        setOnTouchListener { _, event -> handleTouch(event); true }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw frozen screen
        canvas.drawBitmap(frozenBitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), bitmapPaint)

        // 2. Draw scrim
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        if (isDrawing || !selectionRect.isEmpty) {
            val r = selectionRect
            val rf = RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())

            // 3. Punch a clear "window" in the scrim over the selection
            canvas.save()
            canvas.clipRect(rf)
            canvas.drawBitmap(frozenBitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), bitmapPaint)
            canvas.restore()

            // 4. Draw selection overlay
            canvas.drawRect(rf, selectionFillPaint)
            canvas.drawRect(rf, selectionPaint)

            // 5. Draw corner handles
            val handleR = 8f
            listOf(
                rf.left to rf.top, rf.right to rf.top,
                rf.left to rf.bottom, rf.right to rf.bottom,
            ).forEach { (hx, hy) ->
                canvas.drawCircle(hx, hy, handleR, cornerPaint)
            }
        }
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
            MotionEvent.ACTION_UP -> {
                isDrawing = false
                magnifier.dismiss()
                endX = event.x; endY = event.y
                updateSelectionRect()

                // Snap & infer on background thread
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
        }
    }

    private fun updateSelectionRect() {
        selectionRect.set(
            minOf(startX, endX).toInt(),
            minOf(startY, endY).toInt(),
            maxOf(startX, endX).toInt(),
            maxOf(startY, endY).toInt(),
        )
    }

    private fun showActionBar() {
        val result = lastInferenceResult ?: return
        val hasTable = tableParser.parse(textBlocks) != null

        val bar = ActionBarView(
            context     = context,
            anchorRect  = selectionRect,
            hasTable    = hasTable,
            onCopy      = {
                viewScope.launch {
                    val ttl = preferences.clipboardTtlSeconds()
                    clipboardManager.copy(result.plainText, ttl)
                    onDismiss()
                }
            },
            onCopyTsv   = {
                viewScope.launch {
                    val tsv = tableParser.parse(textBlocks) ?: result.plainText
                    val ttl = preferences.clipboardTtlSeconds()
                    clipboardManager.copy(tsv, ttl)
                    onDismiss()
                }
            },
            onDismiss   = {
                selectionRect.setEmpty()
                lastInferenceResult = null
                hideActionBar()
                invalidate()
            },
        )
        actionBar = bar
        addView(bar)
    }

    private fun hideActionBar() {
        actionBar?.let { removeView(it) }
        actionBar = null
    }

    override fun onDetachedFromWindow() {
        magnifier.dismiss()
        viewScope.cancel()
        super.onDetachedFromWindow()
    }
}
