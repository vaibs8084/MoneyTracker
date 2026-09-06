package com.vaibhav.moneytracker.cloud

import org.junit.Assert.*
import org.junit.Test

class SyncViewModelTest {

    @Test
    fun testInitialSyncState() {
        val state = SyncUiState()
        assertEquals(SyncStatus.IDLE_NEVER_SYNCED, state.status)
        assertEquals("Never", state.lastSyncFormatted)
        assertEquals(0, state.pendingChangesCount)
        assertNull(state.errorMessage)
    }

    @Test
    fun testFormatRelativeSyncTimeJustNow() {
        val now = System.currentTimeMillis()
        val formatted = SyncUiState.formatRelativeSyncTime(now - 10000L) // 10 seconds ago
        assertEquals("Just now", formatted)
    }

    @Test
    fun testFormatRelativeSyncTimeMinutes() {
        val now = System.currentTimeMillis()
        val formatted = SyncUiState.formatRelativeSyncTime(now - 5 * 60 * 1000L) // 5 minutes ago
        assertEquals("5 minutes ago", formatted)
    }

    @Test
    fun testFormatRelativeSyncTimeHours() {
        val now = System.currentTimeMillis()
        val formatted = SyncUiState.formatRelativeSyncTime(now - 3 * 3600 * 1000L) // 3 hours ago
        assertEquals("3 hours ago", formatted)
    }

    @Test
    fun testFormatRelativeSyncTimeNever() {
        val formatted = SyncUiState.formatRelativeSyncTime(0L)
        assertEquals("Never", formatted)
    }

    @Test
    fun testSyncUiStateSuccessModel() {
        val now = System.currentTimeMillis()
        val state = SyncUiState(
            status = SyncStatus.SYNC_SUCCESS,
            lastSyncFormatted = "Just now",
            lastSyncTimestampMs = now,
            pendingChangesCount = 0
        )

        assertEquals(SyncStatus.SYNC_SUCCESS, state.status)
        assertEquals("Just now", state.lastSyncFormatted)
        assertEquals(0, state.pendingChangesCount)
    }

    @Test
    fun testSyncUiStateOfflineModel() {
        val state = SyncUiState(
            status = SyncStatus.OFFLINE,
            errorMessage = "You're offline. We'll try again when you're connected."
        )

        assertEquals(SyncStatus.OFFLINE, state.status)
        assertEquals("You're offline. We'll try again when you're connected.", state.errorMessage)
    }
}
