package com.vaibhav.moneytracker.auth

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

class AuthManager(private val context: Context) {

    val preferenceManager = AuthPreferenceManager(context)

    val driveFileScope = Scope("https://www.googleapis.com/auth/drive.file")
    val spreadsheetsScope = Scope("https://www.googleapis.com/auth/spreadsheets")

    fun getGoogleSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(driveFileScope, spreadsheetsScope)
            .build()
    }

    /**
     * Checks if user is currently signed in and preferences exist.
     */
    fun getSignedInUser(): UserIdentity? {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        val savedSession = preferenceManager.getUserSession()

        if (lastAccount != null && savedSession != null && lastAccount.email.equals(savedSession.email, ignoreCase = true)) {
            return savedSession
        } else if (savedSession != null) {
            return savedSession
        }
        return null
    }

    /**
     * Checks if Google Sheets / Drive authorization scopes are granted.
     */
    fun hasSheetsScopePermission(): Boolean {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(lastAccount, driveFileScope, spreadsheetsScope)
    }

    /**
     * Returns the Android Account object if signed in and authorized.
     */
    fun getAccountCredentialAccount(): Account? {
        return GoogleSignIn.getLastSignedInAccount(context)?.account
    }

    /**
     * Returns GoogleSignInAccount if signed in and scopes are granted.
     */
    fun getAuthorizedAccount(): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return if (GoogleSignIn.hasPermissions(account, driveFileScope, spreadsheetsScope)) account else null
    }

    /**
     * Saves identity from GoogleSignInAccount into AuthPreferenceManager.
     */
    fun saveAccountIdentity(account: GoogleSignInAccount): UserIdentity {
        val email = account.email ?: "user@gmail.com"
        val googleAccountId = account.id ?: account.idToken ?: email
        val displayName = account.displayName
        val photoUrl = account.photoUrl?.toString()

        val saved = preferenceManager.getUserSession()
        val user = UserIdentity(
            googleAccountId = googleAccountId,
            email = email,
            displayName = displayName ?: saved?.displayName,
            photoUrl = photoUrl ?: saved?.photoUrl,
            spreadsheetId = saved?.spreadsheetId
        )

        val isComplete = preferenceManager.isOnboardingComplete()
        preferenceManager.saveUserSession(user, isOnboardingComplete = isComplete)
        return user
    }

    /**
     * Revokes session and clears local preferences.
     */
    fun signOut(onComplete: () -> Unit) {
        preferenceManager.clearSession()
        val client = GoogleSignIn.getClient(context, getGoogleSignInOptions())
        client.signOut().addOnCompleteListener {
            onComplete()
        }
    }
}
