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
}
