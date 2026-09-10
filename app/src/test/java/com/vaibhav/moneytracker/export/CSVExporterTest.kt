package com.vaibhav.moneytracker.export

import org.junit.Assert.*
import org.junit.Test

class CSVExporterTest {

    @Test
    fun testCSVHeaderColumns() {
        val expectedHeader = listOf("ID", "Date", "Title", "Type", "Amount", "Category", "Account", "Note", "Tags")
        assertEquals(expectedHeader, CSVExporter.CSV_HEADER)
    }

    @Test
    fun testFormatPaiseToDecimalString() {
        assertEquals("500.00", CSVExporter.formatPaiseToDecimalString(50000L))
        assertEquals("0.00", CSVExporter.formatPaiseToDecimalString(0L))
        assertEquals("-1250.50", CSVExporter.formatPaiseToDecimalString(-125050L))
        assertEquals("0.05", CSVExporter.formatPaiseToDecimalString(5L))
        assertEquals("1000000.00", CSVExporter.formatPaiseToDecimalString(100000000L))
    }

    @Test
    fun testFormatCSVFieldEscaping() {
        // Plain string
        assertEquals("Swiggy Lunch", CSVExporter.formatCSVField("Swiggy Lunch"))

        // String with comma
        assertEquals("\"Swiggy, Lunch\"", CSVExporter.formatCSVField("Swiggy, Lunch"))

        // String with quotes
        assertEquals("\"Swiggy \"\"Special\"\" Lunch\"", CSVExporter.formatCSVField("Swiggy \"Special\" Lunch"))

        // String with newline
        assertEquals("\"Swiggy\nLunch\"", CSVExporter.formatCSVField("Swiggy\nLunch"))

        // Empty / Null
        assertEquals("", CSVExporter.formatCSVField(""))
        assertEquals("", CSVExporter.formatCSVField(null))
    }
}
