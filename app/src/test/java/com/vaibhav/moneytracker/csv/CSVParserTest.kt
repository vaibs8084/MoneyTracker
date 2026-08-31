package com.vaibhav.moneytracker.csv

import org.junit.Assert.*
import org.junit.Test
import java.util.*

class CSVParserTest {

    @Test
    fun testBasicCSVParsing() {
        val content = """
            Date,Title,Amount
            01/01/2026,Lunch,-500
            02/01/2026,Salary,50000
        """.trimIndent()
        val parsed = CSVParser.parse(content)
        assertEquals(listOf("Date", "Title", "Amount"), parsed.headers)
        assertEquals(2, parsed.rows.size)
        assertEquals(listOf("01/01/2026", "Lunch", "-500"), parsed.rows[0])
    }

    @Test
    fun testQuotedFields() {
        val content = """
            "Date","Title","Amount"
            "01/01/2026","Pizza, and Beer","-12.50"
        """.trimIndent()
        val parsed = CSVParser.parse(content)
        assertEquals(listOf("Date", "Title", "Amount"), parsed.headers)
        assertEquals("Pizza, and Beer", parsed.rows[0][1])
    }

    @Test
    fun testDetectDelimiter() {
        val content = "Date;Title;Amount\n01/01/2026;Lunch;-500"
        val parsed = CSVParser.parse(content)
        assertEquals(';', parsed.delimiter)
        assertEquals(listOf("Date", "Title", "Amount"), parsed.headers)
    }

    @Test
    fun testParseDate() {
        val date = CSVParser.parseDate("01/05/2026")
        assertNotNull(date)
        
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date!!
        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, calendar.get(Calendar.MONTH))
        assertEquals(1, calendar.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testParseAmount() {
        // Simple
        assertEquals(-50000L, CSVParser.parseAmountPaise("-500"))
        assertEquals(123456L, CSVParser.parseAmountPaise("1,234.56"))
        
        // With currency symbols
        assertEquals(100000L, CSVParser.parseAmountPaise("₹ 1,000.00"))
        assertEquals(-250000L, CSVParser.parseAmountPaise("$ (2,500.00)")) // Handled by regex removal of () if I update it? 
        // Wait, my regex removes everything except digits, dots, commas, and minus.
        // So (2,500.00) becomes 2,500.00. I should handle negative parens if common.
    }

    @Test
    fun testEuropeanAmount() {
        // European style: 1.234,56
        assertEquals(123456L, CSVParser.parseAmountPaise("1.234,56"))
    }
}
