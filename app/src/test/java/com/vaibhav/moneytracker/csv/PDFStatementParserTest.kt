package com.vaibhav.moneytracker.csv

import com.vaibhav.moneytracker.formatDate
import org.junit.Assert.*
import org.junit.Test

class PDFStatementParserTest {

    @Test
    fun testInitialEncryptedPDFDoesNotShowIncorrectPasswordError() {
        val initialResult = PDFExtractResult(isEncrypted = true, errorMessage = null)
        assertTrue(initialResult.isEncrypted)
        assertNull("Initial encrypted PDF check must NOT produce an incorrect password error message", initialResult.errorMessage)
    }

    @Test
    fun testFailedPasswordAttemptShowsIncorrectPasswordError() {
        val wrongPasswordResult = PDFExtractResult(isEncrypted = true, errorMessage = "Incorrect PDF password. Please try again.")
        assertTrue(wrongPasswordResult.isEncrypted)
        assertEquals("Incorrect PDF password. Please try again.", wrongPasswordResult.errorMessage)
    }

    @Test
    fun testScannedPDFDetectionResult() {
        val scannedResult = PDFExtractResult(
            isScanned = true,
            errorMessage = "This PDF appears to be scanned/image-based and does not contain selectable transaction text. OCR is required to import it reliably."
        )
        assertTrue(scannedResult.isScanned)
        assertTrue(scannedResult.errorMessage!!.contains("OCR is required"))
    }

    @Test
    fun testParsePDFDebitTransactionLineIgnoringBalance() {
        val line = "04-Sep-26 UPI/657924401450/DR/AJAY 500.00 24500.00"
        val candidate = PDFStatementParser.parseBlockToCandidate(line)

        assertNotNull("Candidate should not be null", candidate)
        assertEquals("04-Sep-26", candidate!!.rawDate)
        assertEquals("UPI/657924401450/DR/AJAY", candidate.narration)
        assertEquals(50000L, candidate.amountPaise)
        assertTrue(candidate.isExpense)
    }

    @Test
    fun testParsePDFCreditTransactionLineIgnoringBalance() {
        val line = "04-Sep-26 UPI/658015506835/CR/VAIB/KKBK 2000.00 26500.00"
        val candidate = PDFStatementParser.parseBlockToCandidate(line)

        assertNotNull("Candidate should not be null", candidate)
        assertEquals("04-Sep-26", candidate!!.rawDate)
        assertEquals("UPI/658015506835/CR/VAIB/KKBK", candidate.narration)
        assertEquals(200000L, candidate.amountPaise)
        assertFalse(candidate.isExpense)
    }

    @Test
    fun testInterestCreditEmbeddedDateAndSmallAmountParsing() {
        // Line 1: Interest Cr. for 28-Aug-2026 + ₹0.01
        val line1 = "Interest Cr. for 28-Aug-2026 0.01 6318.32"
        val parsedDate1 = CSVParser.parseDate(line1)
        assertNotNull("Embedded date 28-Aug-2026 must be parsed successfully", parsedDate1)
        assertEquals("28 Aug 2026", formatDate(parsedDate1!!))

        val candidate1 = PDFStatementParser.parseBlockToCandidate(line1)
        assertNotNull("Interest Cr block must be parsed as candidate", candidate1)
        assertEquals(1L, candidate1!!.amountPaise) // 0.01 = 1 paise
        assertFalse("Interest Cr must be Income (not expense)", candidate1.isExpense)

        // Line 2: Interest Cr. for 29-AUG-2026 + ₹0.58
        val line2 = "Interest Cr. for 29-AUG-2026 0.58 6318.90"
        val parsedDate2 = CSVParser.parseDate(line2)
        assertNotNull("Uppercase embedded date 29-AUG-2026 must be parsed", parsedDate2)
        assertEquals("29 Aug 2026", formatDate(parsedDate2!!))

        val candidate2 = PDFStatementParser.parseBlockToCandidate(line2)
        assertNotNull("Interest Cr line 2 must be parsed", candidate2)
        assertEquals(58L, candidate2!!.amountPaise) // 0.58 = 58 paise
        assertFalse("Interest Cr must be Income", candidate2.isExpense)

        // Line 3: Interest Cr. for 30-aug-2026 + ₹0.58
        val line3 = "Interest Cr. for 30-aug-2026 0.58 6319.48"
        val parsedDate3 = CSVParser.parseDate(line3)
        assertNotNull("Lowercase embedded date 30-aug-2026 must be parsed", parsedDate3)
        assertEquals("30 Aug 2026", formatDate(parsedDate3!!))

        val candidate3 = PDFStatementParser.parseBlockToCandidate(line3)
        assertNotNull("Interest Cr line 3 must be parsed", candidate3)
        assertEquals(58L, candidate3!!.amountPaise)
        assertFalse(candidate3.isExpense)
    }

    @Test
    fun testBroughtForwardIgnoredInPDFLine() {
        val line = "01-Aug-2026 Brought Forward 25000.00"
        val candidate = PDFStatementParser.parseBlockToCandidate(line)

        assertNotNull("Candidate should be generated for summary block with isSummaryRow=true", candidate)
        assertTrue("Brought Forward summary row must be flagged as summary row", candidate!!.isSummaryRow)
        assertNull("Summary row must have null amountPaise so it is not imported as a transaction", candidate.amountPaise)
    }
}
