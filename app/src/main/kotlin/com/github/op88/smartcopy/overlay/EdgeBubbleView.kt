package com.github.op88.smartcopy.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.*

/**
 * EdgeBubbleView
 *
 * A semi-transparent, draggable edge trigger bubble docked to the left or
 * right screen edge. Tapping it launches the Smart Copy overlay.
 *
 * The bubble stays docked to its configured side but can be dragged
 * vertically to any Y position. Snaps to the edge on release.
 *
 * Requires [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY].
 */
@SuppressLint("ClickableViewAccessibility")
class EdgeBubbleView(
    context: Context,
    private val onTap: () -> Unit,
    private val dockRight: Boolean = true,
) : View(context) {

    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC0F172A")  // near-black, 80% opacity
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private var lastY = 0f
    private var isDragging = false

    init {
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - lastY
                    if (Math.abs(dy) > 8f) isDragging = true
                    val lp = (v.layoutParams as? WindowManager.LayoutParams) ?: return@setOnTouchListener false
                    lp.y += dy.toInt()
                    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(v, lp)
                    lastY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) onTap()
                    true
                }
                else -> false
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Fixed 48×80dp pill shape
        val dp = resources.displayMetrics.density
        setMeasuredDimension((48 * dp).toInt(), (80 * dp).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Draw a half-pill on the appropriate edge
        val path = Path()
        val radius = w * 0.6f
        if (dockRight) {
            path.addRoundRect(RectF(-radius, 4f, w - 4f, h - 4f), radius, radius, Path.Direction.CW)
        } else {
            path.addRoundRect(RectF(4f, 4f, w + radius, h - 4f), radius, radius, Path.Direction.CW)
        }

        canvas.drawPath(path, bubblePaint)
        canvas.drawPath(path, strokePaint)

        // "S|C" monogram — matches brand logo
        val monoX = w / 2f
        val monoY = h / 2f + textPaint.textSize / 3f
        // Draw S and C smaller to fit the pipe between them
        val smallPaint = Paint(textPaint).apply { textSize = 20f }
        val pipePaint  = Paint(textPaint).apply { textSize = 16f; strokeWidth = 1.5f }

        canvas.drawText("S|C", monoX, monoY, smallPaint)
    }
}
