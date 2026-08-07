package com.github.op88.smartcopy.overlay

import android.content.Context
import android.graphics.Rect
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import com.github.op88.smartcopy.R

/**
 * ActionBarView
 *
 * A floating contextual action bar that appears anchored below (or above)
 * the user's selection rectangle after a Smart Copy selection is confirmed.
 *
 * Actions:
 *  - Copy      — plain text copy to clipboard
 *  - Copy TSV  — table-aware TSV copy (shown only when [hasTable] is true)
 *  - ✕         — dismiss selection
 */
class ActionBarView(
    context: Context,
    private val anchorRect: Rect,
    private val hasTable: Boolean,
    private val onCopy: () -> Unit,
    private val onCopyTsv: () -> Unit,
    private val onDismiss: () -> Unit,
) : FrameLayout(context) {

    init {
        setupLayout()
    }

    private fun setupLayout() {
        val dp = resources.displayMetrics.density

        // Container row
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.bg_action_bar)
            elevation = 16f * dp
            setPadding((8 * dp).toInt())
        }

        fun makeButton(label: String, action: () -> Unit): TextView {
            return TextView(context).apply {
                text = label
                textSize = 14f
                setTextColor(0xFFF1F5F9.toInt())
                setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
                setOnClickListener { action() }
            }
        }

        row.addView(makeButton("Copy", onCopy))

        if (hasTable) {
            // Divider
            View(context).apply {
                layoutParams = LinearLayout.LayoutParams((1 * dp).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
                setBackgroundColor(0xFF334155.toInt())
            }.also { row.addView(it) }
            row.addView(makeButton("Copy TSV", onCopyTsv))
        }

        // Divider
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams((1 * dp).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(0xFF334155.toInt())
        }.also { row.addView(it) }
        row.addView(makeButton("✕", onDismiss))

        addView(row)
        positionBar()
    }

    private fun positionBar() {
        val dp = resources.displayMetrics.density
        val screenHeight = resources.displayMetrics.heightPixels
        val barHeight = (44 * dp).toInt()
        val margin = (8 * dp).toInt()

        // Position below selection if space permits, otherwise above
        val yPos = if (anchorRect.bottom + barHeight + margin < screenHeight) {
            anchorRect.bottom + margin
        } else {
            anchorRect.top - barHeight - margin
        }

        translationX = anchorRect.left.toFloat()
        translationY = yPos.toFloat()
    }
}
