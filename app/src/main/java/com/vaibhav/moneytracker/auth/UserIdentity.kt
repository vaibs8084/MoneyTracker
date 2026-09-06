package com.vaibhav.moneytracker.auth

data class UserIdentity(
    val googleAccountId: String, // Google 'sub' claim / stable account ID
    val email: String,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val spreadsheetId: String? = null
)
