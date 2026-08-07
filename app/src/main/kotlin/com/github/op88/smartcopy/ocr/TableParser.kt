package com.github.op88.smartcopy.ocr

import com.google.mlkit.vision.text.Text

/**
 * TableParser
 *
 * Detects grid/table structures in a list of [Text.TextBlock]s and converts
 * them into Tab-Separated Values (TSV) suitable for direct paste into
 * spreadsheet apps (Google Sheets, Microsoft Excel).
 *
 * Algorithm:
 *  1. Collect all [Text.Element]s with their bounding [android.graphics.Rect]s.
 *  2. Cluster elements into ROWS by grouping those whose vertical centres
 *     fall within [rowTolerance] pixels of each other.
 *  3. Within each row, sort elements left-to-right by X position.
 *  4. Cluster columns by snapping each element's X centre to the nearest
 *     column bucket (within [colTolerance] pixels).
 *  5. Produce a 2D grid, filling empty cells with empty strings.
 *  6. Serialize to TSV.
 */
class TableParser(
    private val rowTolerance: Int = 12,
    private val colTolerance: Int = 20,
) {
    data class Cell(val text: String, val cx: Int, val cy: Int)

    /**
     * Parses OCR text blocks into a TSV string.
     *
     * @param blocks The [Text.TextBlock] list from [OcrEngine.recognize].
     * @return TSV string, or null if no tabular structure is detected
     *         (fewer than 2 logical columns).
     */
    fun parse(blocks: List<Text.TextBlock>): String? {
        val cells = extractCells(blocks)
        if (cells.isEmpty()) return null

        val rows = clusterRows(cells)
        if (rows.size < 2) return null  // Not a table

        val colBuckets = buildColumnBuckets(rows)
        if (colBuckets.size < 2) return null  // Single column = plain text

        val grid = buildGrid(rows, colBuckets)
        return serializeTsv(grid)
    }

    /**
     * Parses blocks into a [List]<[List]<[String]>> grid for programmatic use.
     */
    fun parseToGrid(blocks: List<Text.TextBlock>): List<List<String>> {
        val cells = extractCells(blocks)
        val rows = clusterRows(cells)
        val colBuckets = buildColumnBuckets(rows)
        return buildGrid(rows, colBuckets)
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun extractCells(blocks: List<Text.TextBlock>): List<Cell> =
        blocks.flatMap { block ->
            block.lines.flatMap { line ->
                line.elements.mapNotNull { element ->
                    val rect = element.boundingBox ?: return@mapNotNull null
                    Cell(
                        text = element.text,
                        cx   = rect.centerX(),
                        cy   = rect.centerY(),
                    )
                }
            }
        }

    private fun clusterRows(cells: List<Cell>): List<List<Cell>> {
        val sorted = cells.sortedBy { it.cy }
        val rows   = mutableListOf<MutableList<Cell>>()
        for (cell in sorted) {
            val existing = rows.lastOrNull()
            if (existing == null || kotlin.math.abs(cell.cy - existing.first().cy) > rowTolerance) {
                rows.add(mutableListOf(cell))
            } else {
                existing.add(cell)
            }
        }
        return rows.map { row -> row.sortedBy { it.cx } }
    }

    private fun buildColumnBuckets(rows: List<List<Cell>>): List<Int> {
        val allCx = rows.flatten().map { it.cx }.sorted()
        val buckets = mutableListOf<Int>()
        for (cx in allCx) {
            if (buckets.none { kotlin.math.abs(it - cx) <= colTolerance }) {
                buckets.add(cx)
            }
        }
        return buckets.sorted()
    }

    private fun buildGrid(
        rows: List<List<Cell>>,
        colBuckets: List<Int>,
    ): List<List<String>> {
        return rows.map { row ->
            val rowMap = mutableMapOf<Int, String>()
            for (cell in row) {
                val bucketIdx = colBuckets.indexOfFirst {
                    kotlin.math.abs(it - cell.cx) <= colTolerance
                }
                if (bucketIdx >= 0) {
                    rowMap[bucketIdx] = (rowMap[bucketIdx]?.let { "$it ${cell.text}" } ?: cell.text)
                }
            }
            List(colBuckets.size) { idx -> rowMap[idx] ?: "" }
        }
    }

    private fun serializeTsv(grid: List<List<String>>): String =
        grid.joinToString("\n") { row -> row.joinToString("\t") }
}
