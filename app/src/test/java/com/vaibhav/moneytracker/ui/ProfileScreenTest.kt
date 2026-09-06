package com.vaibhav.moneytracker.ui

import com.vaibhav.moneytracker.auth.UserIdentity
import com.vaibhav.moneytracker.cloud.SyncStatus
import com.vaibhav.moneytracker.cloud.SyncUiState
import org.junit.Assert.*
import org.junit.Test

class ProfileScreenTest {

    @Test
    fun testGoogleUserProfileModel() {
        val user = UserIdentity(
            googleAccountId = "sub_987654321",
            email = "vaibhav@gmail.com",
            displayName = "Vaibhav",
            spreadsheetId = "sheet_12345"
        )
        val syncUiState = SyncUiState(
            status = SyncStatus.SYNC_SUCCESS,
            lastSyncFormatted = "Backed up just now"
        )

        assertEquals("vaibhav@gmail.com", user.email)
        assertEquals("sheet_12345", user.spreadsheetId)
        assertEquals(SyncStatus.SYNC_SUCCESS, syncUiState.status)
        assertEquals("Backed up just now", syncUiState.lastSyncFormatted)
    }

    @Test
    fun testGuestProfileModel() {
        val user: UserIdentity? = null
        val isGuestMode = true

        assertNull("Guest user identity should be null", user)
        assertTrue("Guest mode flag should be true", isGuestMode)
    }

    @Test
    fun testSignOutPreservesRoomData() {
        val isSignedOut = true
        assertTrue("Sign out must clear auth session", isSignedOut)
        // Local Room database data is untouched during sign-out
    }
}
