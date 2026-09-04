package com.vaibhav.moneytracker.csv

import org.junit.Assert.*
import org.junit.Test

class StatementFormatDetectorTest {

    @Test
    fun testColumnDetectionAndRunningBalanceIgnored() {
        val headers = listOf("Txn Date", "Narration", "Withdrawal Amount", "Deposit Amount", "Closing Balance")
        val columns = StatementFormatDetector.detectColumns(headers)

        assertEquals(0, columns.dateIndex)
        assertEquals(1, columns.titleIndex)
        assertEquals(2, columns.debitIndex)
        assertEquals(3, columns.creditIndex)
        assertTrue("Closing Balance index should be in ignored set", columns.ignoredIndices.contains(4))
    }

    @Test
    fun testIsNonTransactionRow() {
        assertTrue(StatementFormatDetector.isNonTransactionRow("Brought Forward"))
        assertTrue(StatementFormatDetector.isNonTransactionRow("Opening Balance"))
        assertTrue(StatementFormatDetector.isNonTransactionRow("Closing Balance"))
        assertTrue(StatementFormatDetector.isNonTransactionRow("Running Balance"))
        assertTrue(StatementFormatDetector.isNonTransactionRow("Ledger Balance"))
        assertTrue(StatementFormatDetector.isNonTransactionRow("B/F"))
        assertFalse(StatementFormatDetector.isNonTransactionRow("UPI/657924401450/DR/AJAY"))
        assertFalse(StatementFormatDetector.isNonTransactionRow("Total Oil"))
    }

    @Test
    fun testNarrationDRCRIndicators() {
        // Single Amount with DR in narration
        val debitPaise = StatementFormatDetector.normalizeAmount(
            debitVal = null,
            creditVal = null,
            amountVal = "500.00",
            typeVal = null,
            title = "UPI/657924401450/DR/AJAY"
        )
        assertEquals(-50000L, debitPaise)

        // Single Amount with CR in narration
        val creditPaise = StatementFormatDetector.normalizeAmount(
            debitVal = null,
            creditVal = null,
            amountVal = "1500.00",
            typeVal = null,
            title = "UPI/658015506835/CR/VAIB/KKBK"
        )
        assertEquals(150000L, creditPaise)
    }

    @Test
    fun testSeparateDebitAndCreditNormalization() {
        // Debit row -> Negative paise (Expense)
        val debitPaise = StatementFormatDetector.normalizeAmount(debitVal = "1,500.00", creditVal = "", amountVal = null, typeVal = null)
        assertEquals(-150000L, debitPaise)

        // Credit row -> Positive paise (Income)
        val creditPaise = StatementFormatDetector.normalizeAmount(debitVal = "", creditVal = "5,000.00", amountVal = null, typeVal = null)
        assertEquals(500000L, creditPaise)
    }
}
