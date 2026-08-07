package com.github.op88.smartcopy.ocr

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TableParser].
 *
 * These run on the JVM without a device — no Android framework required.
 * ML Kit types are mocked with MockK.
 */
class TableParserTest {

    private lateinit var parser: TableParser

    @Before
    fun setUp() {
        parser = TableParser(rowTolerance = 12, colTolerance = 20)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun makeElement(text: String, cx: Int, cy: Int): Text.Element {
        val rect = Rect(cx - 20, cy - 8, cx + 20, cy + 8)
        return mockk {
            every { this@mockk.text } returns text
            every { boundingBox } returns rect
        }
    }

    private fun makeLine(vararg elements: Text.Element): Text.Line {
        return mockk {
            every { this@mockk.elements } returns elements.toList()
            every { text } returns elements.joinToString(" ") { it.text }
            every { boundingBox } returns elements.mapNotNull { it.boundingBox }
                .fold(Rect()) { acc, r -> if (acc.isEmpty) Rect(r) else acc.apply { union(r) } }
        }
    }

    private fun makeBlock(vararg lines: Text.Line): Text.TextBlock {
        return mockk {
            every { this@mockk.lines } returns lines.toList()
            every { boundingBox } returns lines.mapNotNull { it.boundingBox }
                .fold(Rect()) { acc, r -> if (acc.isEmpty) Rect(r) else acc.apply { union(r) } }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `parse returns null for single-column content`() {
        // All elements in one column → not a table
        val block = makeBlock(
            makeLine(makeElement("Item",  50, 50)),
            makeLine(makeElement("Value", 50, 80)),
        )
        val result = parser.parse(listOf(block))
        assertNull("Single column should not be parsed as table", result)
    }

    @Test
    fun `parse returns TSV for 2x2 table`() {
        // Row 1: (Name, 50, 50) | (Age, 200, 50)
        // Row 2: (Alice, 50, 90) | (30, 200, 90)
        val block = makeBlock(
            makeLine(makeElement("Name", 50, 50), makeElement("Age", 200, 50)),
            makeLine(makeElement("Alice", 50, 90), makeElement("30", 200, 90)),
        )
        val result = parser.parse(listOf(block))
        assertNotNull("Two columns should produce TSV", result)
        val rows = result!!.split("\n")
        assertEquals(2, rows.size)
        assertTrue("Header row contains Name", rows[0].contains("Name"))
        assertTrue("Header row contains Age", rows[0].contains("Age"))
        assertTrue("Data row contains Alice", rows[1].contains("Alice"))
        assertTrue("Data row contains 30", rows[1].contains("30"))
    }

    @Test
    fun `parseToGrid returns correct dimensions for 3-column table`() {
        val row1 = makeLine(
            makeElement("A", 50, 50),
            makeElement("B", 200, 50),
            makeElement("C", 350, 50),
        )
        val row2 = makeLine(
            makeElement("1", 50, 90),
            makeElement("2", 200, 90),
            makeElement("3", 350, 90),
        )
        val block = makeBlock(row1, row2)
        val grid = parser.parseToGrid(listOf(block))
        assertEquals("Should have 2 rows", 2, grid.size)
        assertEquals("Should have 3 columns", 3, grid[0].size)
    }

    @Test
    fun `parse handles empty block list`() {
        val result = parser.parse(emptyList())
        assertNull("Empty input should return null", result)
    }
}
