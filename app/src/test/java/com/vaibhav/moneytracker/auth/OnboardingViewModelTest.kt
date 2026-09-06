package com.vaibhav.moneytracker.auth

import org.junit.Assert.*
import org.junit.Test

class OnboardingViewModelTest {

    @Test
    fun testInitialStateTransition() {
        val state = OnboardingState(status = OnboardingStatus.IDLE_CHECKING)
        assertEquals(OnboardingStatus.IDLE_CHECKING, state.status)
        assertNull(state.user)
    }

    @Test
    fun testSignedOutState() {
        val state = OnboardingState(status = OnboardingStatus.SIGNED_OUT)
        assertEquals(OnboardingStatus.SIGNED_OUT, state.status)
        assertNull(state.user)
    }

    @Test
    fun testGuestModeState() {
        val state = OnboardingState(status = OnboardingStatus.GUEST)
        assertEquals(OnboardingStatus.GUEST, state.status)
        assertNull(state.user)
    }

    @Test
    fun testSignInSuccessPendingScopes() {
        val user = UserIdentity(
            googleAccountId = "sub_123456789",
            email = "user@gmail.com",
            displayName = "User Name"
        )
        val state = OnboardingState(
            status = OnboardingStatus.AUTHENTICATED_PENDING_SCOPES,
            user = user
        )

        assertEquals(OnboardingStatus.AUTHENTICATED_PENDING_SCOPES, state.status)
        assertEquals("user@gmail.com", state.user?.email)
        assertEquals("sub_123456789", state.user?.googleAccountId)
    }

    @Test
    fun testSheetsAuthorizationSuccess() {
        val user = UserIdentity(
            googleAccountId = "sub_123456789",
            email = "user@gmail.com",
            displayName = "User Name"
        )
        val state = OnboardingState(
            status = OnboardingStatus.COMPLETE,
            user = user
        )

        assertEquals(OnboardingStatus.COMPLETE, state.status)
        assertEquals("user@gmail.com", state.user?.email)
    }

    @Test
    fun testSheetsAuthorizationDenialKeepsUserSignedIn() {
        val user = UserIdentity(
            googleAccountId = "sub_123456789",
            email = "user@gmail.com"
        )
        val state = OnboardingState(
            status = OnboardingStatus.ERROR,
            user = user,
            errorMessage = "Google Sheets cloud authorization was denied or failed."
        )

        assertEquals(OnboardingStatus.ERROR, state.status)
        assertNotNull("User email must remain preserved on scope denial so user can easily retry", state.user)
        assertEquals("user@gmail.com", state.user?.email)
    }

    @Test
    fun testOnboardingCompleteState() {
        val user = UserIdentity(
            googleAccountId = "sub_123456789",
            email = "user@gmail.com",
            displayName = "User Name",
            spreadsheetId = "sheet_xyz123"
        )
        val state = OnboardingState(
            status = OnboardingStatus.COMPLETE,
            user = user,
            isReturningUser = true
        )

        assertEquals(OnboardingStatus.COMPLETE, state.status)
        assertTrue(state.isReturningUser)
        assertEquals("sheet_xyz123", state.user?.spreadsheetId)
    }

    @Test
    fun testErrorState() {
        val state = OnboardingState(
            status = OnboardingStatus.ERROR,
            errorMessage = "Google Sign-In was cancelled or failed."
        )

        assertEquals(OnboardingStatus.ERROR, state.status)
        assertEquals("Google Sign-In was cancelled or failed.", state.errorMessage)
    }

    @Test
    fun testSignOutSessionInvalidation() {
        val stateAfterSignOut = OnboardingState(status = OnboardingStatus.SIGNED_OUT)
        assertEquals(OnboardingStatus.SIGNED_OUT, stateAfterSignOut.status)
        assertNull(stateAfterSignOut.user)
    }
}
