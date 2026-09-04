package com.vaibhav.moneytracker.csv

import com.vaibhav.moneytracker.TransactionEntity
import org.junit.Assert.*
import org.junit.Test

class DuplicateDetectorTest {

    @Test
    fun testExtractReferenceNumber() {
        val ref1 = DuplicateDetector.extractReferenceNumber("UPI/623493409656/Paid to Swiggy")
        assertEquals("623493409656", ref1)

        val ref2 = DuplicateDetector.extractReferenceNumber("RRN: 987654321012 Transfer")
        assertEquals("987654321012", ref2)

        val ref3 = DuplicateDetector.extractReferenceNumber("Normal Coffee Shop Expense")
        assertNull(ref3)
    }

    @Test
    fun testNormalizeTitle() {
        val title1 = DuplicateDetector.normalizeTitle("UPI/623493409656/Paid to Swiggy!!")
        assertTrue("Normalized title should contain swiggy", title1.contains("swiggy"))
        assertFalse("Normalized title should not contain reference digits", title1.contains("623493409656"))
    }

    @Test
    fun testFingerprintCalculation() {
        val dateMs = 1788500000000L
        val tx1 = TransactionEntity(title = "UPI/623493409656/Swiggy", category = "Food", account = "Kotak Bank", type = "Expense", amountPaise = 35000L, createdAt = dateMs, accountId = 1L, note = "")
        val tx2 = TransactionEntity(title = "UPI/623493409656/Swiggy", category = "Food", account = "Kotak Bank", type = "Expense", amountPaise = 35000L, createdAt = dateMs, accountId = 1L, note = "")

        val fp1 = DuplicateDetector.calculateFingerprint(tx1)
        val fp2 = DuplicateDetector.calculateFingerprint(tx2)

        assertEquals("Same transaction details must produce identical fingerprint", fp1, fp2)
    }

    @Test
    fun testHighConfidenceDuplicateReferenceNumberMatch() {
        val dateMs = 1788500000000L
        val existing = listOf(
            TransactionEntity(id = 1, title = "UPI/623493409656/Swiggy", category = "Food", account = "Kotak Bank", type = "Expense", amountPaise = 35000L, createdAt = dateMs, accountId = 1L, note = "")
        )

        val candidate = TransactionEntity(id = 0, title = "UPI/623493409656/Swiggy Online", category = "General", account = "Kotak Bank", type = "Expense", amountPaise = 35000L, createdAt = dateMs, accountId = 1L, note = "")

        val result = DuplicateDetector.detectDuplicate(candidate, existing)

        assertEquals(DuplicateConfidence.HIGH_CONFIDENCE_DUPLICATE, result.confidence)
        assertNotNull(result.matchedTransaction)
        assertEquals(1L, result.matchedTransaction!!.id)
    }

    @Test
    fun testPossibleDuplicatePostingDelayOneDayWindow() {
        val baseDateMs = 1788500000000L
        val nextDayMs = baseDateMs + 24 * 3600 * 1000L

        val existing = listOf(
            TransactionEntity(id = 10, title = "Swiggy Food", category = "Food", account = "Kotak Bank", type = "Expense", amountPaise = 35000L, createdAt = baseDateMs, accountId = 1L, note = "Manual Entry")
        )

        val candidate = TransactionEntity(id = 0, title = "UPI/SWIGGY/659610094486", category = "General", account = "Kotak Bank", type = "Expense", amountPaise = 35000L, createdAt = nextDayMs, accountId = 1L, note = "Import")

        val result = DuplicateDetector.detectDuplicate(candidate, existing)

        assertEquals(DuplicateConfidence.POSSIBLE_DUPLICATE, result.confidence)
        assertNotNull(result.matchedTransaction)
        assertEquals(10L, result.matchedTransaction!!.id)
    }

    @Test
    fun testSameFileReimportFlagsAllAsHighConfidenceDuplicates() {
        val date1 = 1788500000000L
        val date2 = 1788586400000L

        val existingDbTxs = listOf(
            TransactionEntity(id = 1, title = "UPI/623493409656/Swiggy", category = "Food", account = "Kotak Bank", type = "Expense", amountPaise = 35000L, createdAt = date1, accountId = 1L, note = ""),
            TransactionEntity(id = 2, title = "Salary September", category = "Income", account = "Kotak Bank", type = "Income", amountPaise = 1000000L, createdAt = date2, accountId = 1L, note = "")
        )

        val statementCandidates = listOf(
            TransactionEntity(id = 0, title = "UPI/623493409656/Swiggy", category = "General", account = "Kotak Bank", type = "Expense", amountPaise = 35000L, createdAt = date1, accountId = 1L, note = ""),
            TransactionEntity(id = 0, title = "Salary September", category = "General", account = "Kotak Bank", type = "Income", amountPaise = 1000000L, createdAt = date2, accountId = 1L, note = "")
        )

        for (candidate in statementCandidates) {
            val dupResult = DuplicateDetector.detectDuplicate(candidate, existingDbTxs)
            assertEquals("All rows in a re-imported statement must be flagged as High-Confidence Duplicates", DuplicateConfidence.HIGH_CONFIDENCE_DUPLICATE, dupResult.confidence)
        }
    }

    @Test
    fun testPartialReimportCorrectlySeparatesNewFromExisting() {
        val date1 = 1788500000000L
        val date2 = 1788586400000L

        val existingDbTxs = listOf(
            TransactionEntity(id = 1, title = "UPI/623493409656/Swiggy", category = "Food", account = "Kotak Bank", type = "Expense", amountPaise = 35000L, createdAt = date1, accountId = 1L, note = "")
        )

        val statementCandidates = listOf(
            TransactionEntity(id = 0, title = "UPI/623493409656/Swiggy", category = "General", account = "Kotak Bank", type = "Expense", amountPaise = 35000L, createdAt = date1, accountId = 1L, note = ""), // Existing
            TransactionEntity(id = 0, title = "New Coffee Expense", category = "General", account = "Kotak Bank", type = "Expense", amountPaise = 2500L, createdAt = date2, accountId = 1L, note = "") // New
        )

        val dupResult1 = DuplicateDetector.detectDuplicate(statementCandidates[0], existingDbTxs)
        val dupResult2 = DuplicateDetector.detectDuplicate(statementCandidates[1], existingDbTxs)

        assertEquals(DuplicateConfidence.HIGH_CONFIDENCE_DUPLICATE, dupResult1.confidence)
        assertEquals(DuplicateConfidence.UNIQUE, dupResult2.confidence)
    }

    @Test
    fun testUniqueTransactionDifferentAmount() {
        val dateMs = 1788500000000L
        val existing = listOf(
            TransactionEntity(id = 1, title = "Coffee Shop", category = "Food", account = "Kotak Bank", type = "Expense", amountPaise = 20000L, createdAt = dateMs, accountId = 1L, note = "")
        )

        val candidate = TransactionEntity(id = 0, title = "Coffee Shop", category = "Food", account = "Kotak Bank", type = "Expense", amountPaise = 50000L, createdAt = dateMs, accountId = 1L, note = "")

        val result = DuplicateDetector.detectDuplicate(candidate, existing)

        assertEquals(DuplicateConfidence.UNIQUE, result.confidence)
        assertNull(result.matchedTransaction)
    }
}
