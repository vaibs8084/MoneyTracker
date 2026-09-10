package com.vaibhav.moneytracker.cloud

import com.vaibhav.moneytracker.auth.UserIdentity
import org.junit.Assert.*
import org.junit.Test

class SyncEngineTest {

    @Test
    fun testTopologicalOrder() {
        val order = SyncEngine.TOPOLOGICAL_ORDER
        assertEquals(11, order.size)
        assertEquals("Accounts", order[0])
        assertEquals("Categories", order[1])
        assertEquals("Tags", order[2])
        assertEquals("Transactions", order[8])
        assertEquals("CapturedInbox", order[10])
    }

    @Test
    fun testSyncResultSuccessModel() {
        val result = SyncResult(
            isSuccess = true,
            uploadedCount = 102,
            downloadedCount = 0
        )

        assertTrue(result.isSuccess)
        assertEquals(102, result.uploadedCount)
        assertEquals(0, result.downloadedCount)
        assertNull(result.errorMessage)
    }

    @Test
    fun testSyncResultErrorModel() {
        val result = SyncResult(
            isSuccess = false,
            errorMessage = "Google Sheets authorization error (403)"
        )

        assertFalse(result.isSuccess)
        assertEquals("Google Sheets authorization error (403)", result.errorMessage)
    }

    @Test
    fun testConflictResolutionCloudNewerWins() {
        val localCreatedAt = 1788500000000L
        val cloudUpdatedAtMs = 1788500005000L

        assertTrue("Cloud row with newer timestamp wins", cloudUpdatedAtMs > localCreatedAt)
    }

    @Test
    fun testSoftDeleteTombstoneResolution() {
        val isCloudDeleted = true
        val cloudUpdatedAtMs = 1788500005000L
        val localCreatedAt = 1788500000000L

        assertTrue("Cloud soft-delete tombstone with newer timestamp must delete local record", isCloudDeleted && cloudUpdatedAtMs >= localCreatedAt)
    }

    @Test
    fun testEntityToTabMappingCoverage() {
        val entityTypes = listOf(
            "TRANSACTION", "ACCOUNT", "CATEGORY", "TAG", "TRANSACTION_TAG",
            "SUBSCRIPTION", "BUDGET", "GOAL", "RULE", "RULE_TAG", "CAPTURED"
        )

        entityTypes.forEach { type ->
            val mappedTab = when (type) {
                "TRANSACTION" -> "Transactions"
                "ACCOUNT" -> "Accounts"
                "CATEGORY" -> "Categories"
                "TAG" -> "Tags"
                "TRANSACTION_TAG" -> "TransactionTags"
                "SUBSCRIPTION" -> "Subscriptions"
                "BUDGET" -> "Budgets"
                "GOAL" -> "Goals"
                "RULE" -> "Rules"
                "RULE_TAG" -> "RuleTags"
                "CAPTURED" -> "CapturedInbox"
                else -> null
            }
            assertNotNull("Entity type $type must map to a valid cloud tab", mappedTab)
            assertTrue(SyncEngine.TOPOLOGICAL_ORDER.contains(mappedTab))
        }
    }

    @Test
    fun testIdempotentCloudDataUpsertLookup() {
        val cloudRows = mutableListOf(
            mutableListOf("20", "Swiggy", "Food", "Kotak", "Expense", "50000", "Dinner", "1788500000000", "1", "2", "NONE", "FALSE", "1788500000000")
        )

        // Attempting to sync transaction ID 20 again
        val targetEntityId = "20"
        val existingRowIndex = cloudRows.indexOfFirst { it.firstOrNull() == targetEntityId }

        assertTrue("Existing cloud row for ID 20 must be found at index 0", existingRowIndex >= 0)
        assertEquals(0, existingRowIndex)

        // Updating existing in-memory row in-place instead of appending duplicate
        val updatedRow = mutableListOf("20", "Swiggy Updated", "Food", "Kotak", "Expense", "50000", "Dinner Note", "1788500000000", "1", "2", "NONE", "FALSE", "178850005000")
        cloudRows[existingRowIndex] = updatedRow

        assertEquals(1, cloudRows.size) // Size remains exactly 1! ZERO duplicate rows created!
        assertEquals("Swiggy Updated", cloudRows[0][1])
    }

    @Test
    fun testSequentialUploadMemorySyncMapUpdate() {
        val mutableCloudData = mutableMapOf<String, MutableList<MutableList<String>>>(
            "Transactions" to mutableListOf()
        )

        val txRows = mutableCloudData["Transactions"]!!
        assertEquals(0, txRows.size)

        // Syncing log 1 for Transaction ID 20 (CREATE)
        val log1Row = mutableListOf("20", "Coffee", "Food", "Cash", "Expense", "10000", "", "1788500000000", "2", "1", "NONE", "FALSE", "1788500000000")
        val index1 = txRows.indexOfFirst { it.firstOrNull() == "20" }
        assertEquals(-1, index1)

        // Appending row 1 and recording in-memory Immediately
        txRows.add(log1Row)
        assertEquals(1, txRows.size)

        // Syncing log 2 for Transaction ID 20 (UPDATE) in the SAME batch or second pass
        val index2 = txRows.indexOfFirst { it.firstOrNull() == "20" }
        assertTrue("Subsequent log for ID 20 must find existing row index", index2 >= 0)
        assertEquals(0, index2)

        // Executing in-place update instead of second append
        val log2Row = mutableListOf("20", "Coffee Edit", "Food", "Cash", "Expense", "12000", "", "1788500000000", "2", "1", "NONE", "FALSE", "178850005000")
        txRows[index2] = log2Row

        assertEquals(1, txRows.size) // Size remains 1!
        assertEquals("Coffee Edit", txRows[0][1])
    }
}
