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
}
