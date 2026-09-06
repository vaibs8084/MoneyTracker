package com.vaibhav.moneytracker.auth

import android.content.Context
import android.content.SharedPreferences

class AuthPreferenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveUserSession(user: UserIdentity, isOnboardingComplete: Boolean = false) {
        prefs.edit()
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_GOOGLE_ACCOUNT_ID, user.googleAccountId)
            .putString(KEY_DISPLAY_NAME, user.displayName)
            .putString(KEY_PHOTO_URL, user.photoUrl)
            .putString(KEY_SPREADSHEET_ID, user.spreadsheetId)
            .putBoolean(KEY_ONBOARDING_COMPLETE, isOnboardingComplete)
            .putBoolean(KEY_IS_GUEST, false)
            .apply()
    }

    fun getUserSession(): UserIdentity? {
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val googleAccountId = prefs.getString(KEY_GOOGLE_ACCOUNT_ID, null) ?: return null
        val displayName = prefs.getString(KEY_DISPLAY_NAME, null)
        val photoUrl = prefs.getString(KEY_PHOTO_URL, null)
        val spreadsheetId = prefs.getString(KEY_SPREADSHEET_ID, null)

        return UserIdentity(
            googleAccountId = googleAccountId,
            email = email,
            displayName = displayName,
            photoUrl = photoUrl,
            spreadsheetId = spreadsheetId
        )
    }

    fun setSpreadsheetId(spreadsheetId: String) {
        prefs.edit().putString(KEY_SPREADSHEET_ID, spreadsheetId).apply()
    }

    fun setOnboardingComplete(complete: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply()
    }

    fun isOnboardingComplete(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    }

    fun setGuestMode(isGuest: Boolean) {
        prefs.edit().putBoolean(KEY_IS_GUEST, isGuest).apply()
    }

    fun isGuestMode(): Boolean {
        return prefs.getBoolean(KEY_IS_GUEST, false)
    }

    fun setLastSyncTimestamp(timestampMs: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, timestampMs).apply()
    }

    fun getLastSyncTimestamp(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "money_tracker_auth_prefs"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_GOOGLE_ACCOUNT_ID = "google_account_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_PHOTO_URL = "photo_url"
        private const val KEY_SPREADSHEET_ID = "spreadsheet_id"
        private const val KEY_ONBOARDING_COMPLETE = "is_onboarding_complete"
        private const val KEY_IS_GUEST = "is_guest_mode"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp_ms"
    }
}
