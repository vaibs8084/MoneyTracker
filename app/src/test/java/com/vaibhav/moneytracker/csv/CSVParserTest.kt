package com.vaibhav.moneytracker.csv

import org.junit.Assert.*
import org.junit.Test

class CSVParserTest {

    @Test
    fun testRealBankCSVHeaderRowAutoDetection() {
        // Representative bank statement CSV with bank metadata headers at the top
        val content = """
            KOTAK MAHINDRA BANK ACCOUNT STATEMENT
            Account Number: 123456789
            Statement Period: 01-Aug-2026 to 31-Aug-2026

            Date,Narration,Chq/Ref No,Value Date,Withdrawal Amount,Deposit Amount,Closing Balance
            01/08/2026,Brought Forward,,01/08/2026,,,25000.00
            02/08/2026,UPI/657924401450/DR/AJAY,,02/08/2026,500.00,,24500.00
            03/08/2026,UPI/658015506835/CR/VAIB/KKBK,,03/08/2026,,1500.00,26000.00
        """.trimIndent()

        val parsed = CSVParser.parse(content)

        // Verified header row index detection
        assertEquals(3, parsed.headerRowIndex)
        assertEquals(listOf("Date", "Narration", "Chq/Ref No", "Value Date", "Withdrawal Amount", "Deposit Amount", "Closing Balance"), parsed.headers)
        assertEquals(3, parsed.rows.size)
    }

    @Test
    fun testTwoDigitYearDateParsing() {
        val date1 = CSVParser.parseDate("01/08/26")
        assertNotNull("Failed on 01/08/26", date1)

        val date2 = CSVParser.parseDate("01-Aug-26")
        assertNotNull("Failed on 01-Aug-26", date2)

        val date3 = CSVParser.parseDate("04 Sep 2026")
        assertNotNull("Failed on 04 Sep 2026", date3)

        val date4 = CSVParser.parseDate("04-Sep-2026")
        assertNotNull("Failed on 04-Sep-2026", date4)

        val date5 = CSVParser.parseDate("04/09/2026")
        assertNotNull("Failed on 04/09/2026", date5)

        val date6 = CSVParser.parseDate("4/9/2026")
        assertNotNull("Failed on 4/9/2026", date6)

        val date7 = CSVParser.parseDate("04-Sep-2026 14:30:00")
        assertNotNull("Failed on 04-Sep-2026 14:30:00", date7)
    }

    @Test
    fun testParseAmount() {
        assertEquals(-50000L, CSVParser.parseAmountPaise("-500"))
        assertEquals(123456L, CSVParser.parseAmountPaise("1,234.56"))
        assertEquals(100000L, CSVParser.parseAmountPaise("₹ 1,000.00"))
        assertEquals(-250000L, CSVParser.parseAmountPaise("$ (2,500.00)"))
    }

    @Test
    fun testIndianNumberFormatting() {
        assertEquals(12345678L, CSVParser.parseAmountPaise("₹ 1,23,456.78"))
    }

    @Test
    fun testEuropeanAmount() {
        assertEquals(123456L, CSVParser.parseAmountPaise("1.234,56"))
    }

    @Test
    fun testInvalidAmountReturnsNull() {
        assertNull(CSVParser.parseAmountPaise(""))
        assertNull(CSVParser.parseAmountPaise("-"))
        assertNull(CSVParser.parseAmountPaise("N/A"))
    }
}
