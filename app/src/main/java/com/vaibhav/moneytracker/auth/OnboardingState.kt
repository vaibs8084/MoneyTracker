package com.vaibhav.moneytracker.auth

enum class OnboardingStatus {
    IDLE_CHECKING,
    SIGNED_OUT,
    SIGNING_IN,
    AUTHENTICATED_PENDING_SCOPES,
    AUTHENTICATED_AUTHORIZED,
    CLOUD_SETUP_IN_PROGRESS,
    CLOUD_READY,
    GUEST,
    COMPLETE,
    ERROR
}

data class OnboardingState(
    val status: OnboardingStatus = OnboardingStatus.IDLE_CHECKING,
    val user: UserIdentity? = null,
    val isReturningUser: Boolean = false,
    val errorMessage: String? = null
)
