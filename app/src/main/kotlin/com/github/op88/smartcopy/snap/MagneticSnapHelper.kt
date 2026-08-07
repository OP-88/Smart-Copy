package com.github.op88.smartcopy.snap

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import kotlin.math.abs
import kotlin.math.hypot

/**
 * MagneticSnapHelper
 *
 * Implements "Aim Assist" — snapping selection handles to the nearest
 * detected text bounding box edge when the user drags within [snapThresholdPx].
 *
 * Instead of perfectly manual dragging, the user draws an approximate box
 * and this helper snaps it to the nearest text/cell boundaries detected by OCR.
 *
 * Snap targets are extracted from [Text.TextBlock] bounding rects (words,
 * lines, or blocks depending on granularity setting).
 */
class MagneticSnapHelper(
    /** Distance in pixels within which a handle snaps to a text edge. */
    private val snapThresholdPx: Float = 48f,
) {
    enum class Granularity { ELEMENT, LINE, BLOCK }

    /**
     * Given the current drag position [touchX], [touchY] and the OCR [blocks],
     * returns the nearest snapped point (x, y) if within threshold, or the
     * original touch point if no snap candidate is found.
     */
    fun snapPoint(
        touchX: Float,
        touchY: Float,
        blocks: List<Text.TextBlock>,
        granularity: Granularity = Granularity.LINE,
    ): Pair<Float, Float> {
        val candidates = collectCandidateEdges(blocks, granularity)
        if (candidates.isEmpty()) return touchX to touchY

        // Find nearest edge point
        var bestDist = Float.MAX_VALUE
        var bestX = touchX
        var bestY = touchY

        for ((ex, ey) in candidates) {
            val dist = hypot((touchX - ex).toDouble(), (touchY - ey).toDouble()).toFloat()
            if (dist < bestDist && dist <= snapThresholdPx) {
                bestDist = dist
                bestX = ex.toFloat()
                bestY = ey.toFloat()
            }
        }

        return bestX to bestY
    }

    /**
     * Snaps an entire selection [Rect] by independently snapping each of its
     * four edge midpoints to the nearest OCR boundary.
     */
    fun snapRect(
        rect: Rect,
        blocks: List<Text.TextBlock>,
        granularity: Granularity = Granularity.LINE,
    ): Rect {
        val candidates = collectCandidateEdges(blocks, granularity)
        return Rect(
            snapCoordinate(rect.left.toFloat(),   candidates.map { it.first  }).toInt(),
            snapCoordinate(rect.top.toFloat(),    candidates.map { it.second }).toInt(),
            snapCoordinate(rect.right.toFloat(),  candidates.map { it.first  }).toInt(),
            snapCoordinate(rect.bottom.toFloat(), candidates.map { it.second }).toInt(),
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extracts corner and midpoint coordinates from all bounding rects at
     * the requested [granularity].
     */
    private fun collectCandidateEdges(
        blocks: List<Text.TextBlock>,
        granularity: Granularity,
    ): List<Pair<Int, Int>> {
        val rects = when (granularity) {
            Granularity.BLOCK   -> blocks.mapNotNull { it.boundingBox }
            Granularity.LINE    -> blocks.flatMap { b -> b.lines.mapNotNull { it.boundingBox } }
            Granularity.ELEMENT -> blocks.flatMap { b ->
                b.lines.flatMap { l -> l.elements.mapNotNull { it.boundingBox } }
            }
        }
        return rects.flatMap { r ->
            listOf(
                r.left  to r.top,
                r.right to r.top,
                r.left  to r.bottom,
                r.right to r.bottom,
                r.centerX() to r.top,
                r.centerX() to r.bottom,
                r.left  to r.centerY(),
                r.right to r.centerY(),
            )
        }
    }

    private fun snapCoordinate(value: Float, candidates: List<Int>): Float {
        val nearest = candidates.minByOrNull { abs(it - value) } ?: return value
        return if (abs(nearest - value) <= snapThresholdPx) nearest.toFloat() else value
    }
}
