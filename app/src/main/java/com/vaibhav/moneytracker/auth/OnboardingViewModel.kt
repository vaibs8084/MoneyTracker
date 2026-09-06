package com.vaibhav.moneytracker.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.vaibhav.moneytracker.cloud.CloudSpreadsheetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnboardingViewModel(
    private val authManager: AuthManager,
    private val cloudSpreadsheetManager: CloudSpreadsheetManager
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState(status = OnboardingStatus.IDLE_CHECKING))
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        checkAuthState()
    }

    /**
     * Checks current sign-in and authorization status on app startup.
     */
    fun checkAuthState() {
        viewModelScope.launch {
            val isGuest = authManager.preferenceManager.isGuestMode()
            val savedUser = authManager.getSignedInUser()
            val isComplete = authManager.preferenceManager.isOnboardingComplete()
            val hasScopes = authManager.hasSheetsScopePermission()

            if (isGuest) {
                _state.value = OnboardingState(status = OnboardingStatus.GUEST)
            } else if (savedUser != null && isComplete && hasScopes && !savedUser.spreadsheetId.isNullOrBlank()) {
                _state.value = OnboardingState(
                    status = OnboardingStatus.COMPLETE,
                    user = savedUser,
                    isReturningUser = true
                )
            } else if (savedUser != null && hasScopes) {
                runCloudSetup(savedUser)
            } else if (savedUser != null) {
                _state.value = OnboardingState(
                    status = OnboardingStatus.AUTHENTICATED_PENDING_SCOPES,
                    user = savedUser,
                    isReturningUser = true
                )
            } else {
                _state.value = OnboardingState(
                    status = OnboardingStatus.SIGNED_OUT
                )
            }
        }
    }

    fun continueAsGuest() {
        authManager.preferenceManager.setGuestMode(true)
        _state.value = OnboardingState(status = OnboardingStatus.GUEST)
    }

    fun startSignIn() {
        _state.value = _state.value.copy(
            status = OnboardingStatus.SIGNING_IN,
            errorMessage = null
        )
    }

    fun onGoogleSignInResult(account: GoogleSignInAccount?, error: String? = null) {
        if (account != null) {
            authManager.preferenceManager.setGuestMode(false)
            val user = authManager.saveAccountIdentity(account)
            val hasScope = authManager.hasSheetsScopePermission()

            if (hasScope) {
                runCloudSetup(user)
            } else {
                _state.value = OnboardingState(
                    status = OnboardingStatus.AUTHENTICATED_PENDING_SCOPES,
                    user = user
                )
            }
        } else {
            _state.value = OnboardingState(
                status = OnboardingStatus.ERROR,
                errorMessage = error ?: "Google Sign-In was cancelled or failed. Please try again."
            )
        }
    }

    fun onSheetsAuthorizationResult(success: Boolean, error: String? = null) {
        val currentUser = _state.value.user ?: authManager.getSignedInUser()
        if (success && currentUser != null) {
            runCloudSetup(currentUser)
        } else {
            _state.value = OnboardingState(
                status = OnboardingStatus.ERROR,
                user = currentUser,
                errorMessage = error ?: "Google Sheets cloud authorization was denied or failed. Access is required for backup."
            )
        }
    }

    fun runCloudSetup(user: UserIdentity) {
        _state.value = OnboardingState(
            status = OnboardingStatus.CLOUD_SETUP_IN_PROGRESS,
            user = user
        )
        viewModelScope.launch(Dispatchers.IO) {
            val provisionResult = cloudSpreadsheetManager.discoverOrCreateSpreadsheet(user)
            withContext(Dispatchers.Main) {
                if (!provisionResult.spreadsheetId.isNullOrBlank()) {
                    val updatedUser = user.copy(spreadsheetId = provisionResult.spreadsheetId)
                    authManager.preferenceManager.saveUserSession(updatedUser, isOnboardingComplete = true)
                    _state.value = OnboardingState(
                        status = OnboardingStatus.COMPLETE,
                        user = updatedUser
                    )
                } else {
                    _state.value = OnboardingState(
                        status = OnboardingStatus.ERROR,
                        user = user,
                        errorMessage = provisionResult.errorMessage ?: "Unable to set up your Google Sheets backup. Please check your internet connection."
                    )
                }
            }
        }
    }

    fun retryOnboarding() {
        val currentUser = _state.value.user ?: authManager.getSignedInUser()
        if (currentUser != null && authManager.hasSheetsScopePermission()) {
            runCloudSetup(currentUser)
        } else {
            checkAuthState()
        }
    }

    fun signOut() {
        authManager.preferenceManager.setGuestMode(false)
        authManager.signOut {
            _state.value = OnboardingState(status = OnboardingStatus.SIGNED_OUT)
        }
    }
}
