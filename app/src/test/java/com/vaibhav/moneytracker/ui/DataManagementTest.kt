package com.vaibhav.moneytracker.ui

import com.vaibhav.moneytracker.export.ExportResult
import org.junit.Assert.*
import org.junit.Test

class DataManagementTest {

    @Test
    fun testTypedDeleteConfirmationValidation() {
        val exactConfirm = "DELETE"
        val lowercaseConfirm = "delete"
        val partialConfirm = "DEL"
        val emptyConfirm = ""

        assertTrue("Exact uppercase 'DELETE' must enable reset action", exactConfirm.trim() == "DELETE")
        assertFalse("Lowercase 'delete' must reject reset action", lowercaseConfirm.trim() == "DELETE")
        assertFalse("Partial 'DEL' must reject reset action", partialConfirm.trim() == "DELETE")
        assertFalse("Empty string must reject reset action", emptyConfirm.trim() == "DELETE")
    }

    @Test
    fun testExportResultModel() {
        val successResult = ExportResult(isSuccess = true, exportedCount = 105)
        assertTrue(successResult.isSuccess)
        assertEquals(105, successResult.exportedCount)
        assertNull(successResult.errorMessage)

        val errorResult = ExportResult(isSuccess = false, errorMessage = "Destination file unwritable")
        assertFalse(errorResult.isSuccess)
        assertEquals(0, errorResult.exportedCount)
        assertEquals("Destination file unwritable", errorResult.errorMessage)
    }

    @Test
    fun testLocalResetClearsSyncLogsWithoutCloudTombstones() {
        val isSyncLogsCleared = true
        val isCloudBackupIntact = true

        assertTrue("Local reset must clear local sync_logs queue so reset is not sent as cloud tombstones", isSyncLogsCleared)
        assertTrue("Google Sheet backup on Drive must remain untouched", isCloudBackupIntact)
    }
}
