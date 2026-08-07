package com.github.op88.smartcopy.ocr

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import kotlin.math.abs

/**
 * SelectionInferencer
 *
 * Expands a user's loose, imprecise selection [Rect] to snap cleanly to
 * complete logical sentences / paragraphs from the OCR result.
 *
 * Behaviour:
 *  - A horizontal swipe that crosses a text baseline automatically expands
 *    to include all characters on that line.
 *  - A vertical oval spanning multiple lines expands to capture full
 *    paragraphs without cutting leading/trailing letters.
 *  - Returns both the expanded [Rect] and the concatenated plain text.
 */
class SelectionInferencer(
    /** Pixels of vertical slack when matching a user gesture to a text line. */
    private val baselineTolerance: Int = 16,
) {
    data class InferenceResult(
        val expandedRect: Rect,
        val plainText: String,
    )

    /**
     * Given a user-drawn [selectionRect] and the [blocks] from [OcrEngine],
     * returns an [InferenceResult] with the snapped bounding box and
     * the extracted text.
     *
     * Returns null if no text blocks intersect the selection.
     */
    fun infer(
        selectionRect: Rect,
        blocks: List<Text.TextBlock>,
    ): InferenceResult? {
        val matchedLines = mutableListOf<Text.Line>()

        for (block in blocks) {
            for (line in block.lines) {
                val lineRect = line.boundingBox ?: continue
                // A line is "selected" if the user's gesture vertically overlaps
                // the line's centre within the tolerance window.
                val lineCy = lineRect.centerY()
                if (lineCy in (selectionRect.top - baselineTolerance)
                    ..(selectionRect.bottom + baselineTolerance)
                ) {
                    // Also require horizontal overlap for multi-column layouts
                    if (Rect.intersects(selectionRect, lineRect)) {
                        matchedLines.add(line)
                    }
                }
            }
        }

        if (matchedLines.isEmpty()) return null

        // Compute the union bounding rect of all matched lines
        val union = matchedLines
            .mapNotNull { it.boundingBox }
            .fold(Rect()) { acc, rect ->
                if (acc.isEmpty) Rect(rect) else acc.apply { union(rect) }
            }

        val text = matchedLines.joinToString(" ") { it.text }
        return InferenceResult(expandedRect = union, plainText = text)
    }
}
