package com.vaibhav.moneytracker.cloud

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class SyncStatus {
    IDLE_NEVER_SYNCED,
    SYNCING,
    SYNC_SUCCESS,
    OFFLINE,
    PENDING_CHANGES,
    AUTH_REQUIRED,
    AUTHORIZATION_ERROR,
    SPREADSHEET_NOT_FOUND,
    SYNC_ERROR,
    RESTORING,
    RESTORE_SUCCESS,
    RESTORE_ERROR
}

data class SyncUiState(
    val status: SyncStatus = SyncStatus.IDLE_NEVER_SYNCED,
    val lastSyncFormatted: String = "Never",
    val lastSyncTimestampMs: Long = 0L,
    val pendingChangesCount: Int = 0,
    val errorMessage: String? = null
) {
    companion object {
        fun formatRelativeSyncTime(timestampMs: Long): String {
            if (timestampMs <= 0L) return "Never"
            val now = System.currentTimeMillis()
            val diffMs = now - timestampMs

            if (diffMs < 0L) return "Just now" // Handles clock skew
            val diffSec = diffMs / 1000L
            val diffMin = diffSec / 60L
            val diffHours = diffMin / 60L

            return when {
                diffSec < 60 -> "Just now"
                diffMin < 60 -> if (diffMin == 1L) "1 minute ago" else "$diffMin minutes ago"
                diffHours < 24 -> if (diffHours == 1L) "1 hour ago" else "$diffHours hours ago"
                else -> {
                    val calNow = Calendar.getInstance().apply { timeInMillis = now }
                    val calSync = Calendar.getInstance().apply { timeInMillis = timestampMs }

                    if (calNow.get(Calendar.DAY_OF_YEAR) == calSync.get(Calendar.DAY_OF_YEAR) &&
                        calNow.get(Calendar.YEAR) == calSync.get(Calendar.YEAR)
                    ) {
                        "Today, " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestampMs))
                    } else if (calNow.get(Calendar.DAY_OF_YEAR) - calSync.get(Calendar.DAY_OF_YEAR) == 1 &&
                        calNow.get(Calendar.YEAR) == calSync.get(Calendar.YEAR)
                    ) {
                        "Yesterday, " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestampMs))
                    } else {
                        SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(Date(timestampMs))
                    }
                }
            }
        }
    }
}
