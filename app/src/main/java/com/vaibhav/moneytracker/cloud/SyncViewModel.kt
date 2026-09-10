package com.vaibhav.moneytracker.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaibhav.moneytracker.MoneyTrackerDatabase
import com.vaibhav.moneytracker.auth.AuthPreferenceManager
import com.vaibhav.moneytracker.auth.UserIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyncViewModel(
    private val database: MoneyTrackerDatabase,
    private val syncEngine: SyncEngine,
    private val preferenceManager: AuthPreferenceManager
) : ViewModel() {

    private val _syncState = MutableStateFlow(SyncUiState())
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    @Volatile
    private var isSyncInFlight = false

    init {
        refreshSyncStatusState()
        observePendingSyncLogs()
    }

    fun isSyncInFlight(): Boolean = isSyncInFlight

    @OptIn(FlowPreview::class)
    private fun observePendingSyncLogs() {
        viewModelScope.launch {
            database.syncLogDao().getAllFlow()
                .debounce(1500L)
                .collect { logs ->
                    val user = preferenceManager.getUserSession()
                    val isGuest = preferenceManager.isGuestMode()
                    if (logs.isNotEmpty() && !isGuest && user != null && !isSyncInFlight) {
                        performSyncNow(user)
                    }
                }
        }
    }

    fun refreshSyncStatusState() {
        viewModelScope.launch(Dispatchers.IO) {
            val lastSyncMs = preferenceManager.getLastSyncTimestamp()
            val pendingLogs = database.syncLogDao().getAll()
            val formatted = SyncUiState.formatRelativeSyncTime(lastSyncMs)

            val initialStatus = when {
                lastSyncMs <= 0L -> SyncStatus.IDLE_NEVER_SYNCED
                pendingLogs.isNotEmpty() -> SyncStatus.PENDING_CHANGES
                else -> SyncStatus.SYNC_SUCCESS
            }

            _syncState.value = SyncUiState(
                status = initialStatus,
                lastSyncFormatted = formatted,
                lastSyncTimestampMs = lastSyncMs,
                pendingChangesCount = pendingLogs.size
            )
        }
    }

    fun performSyncNow(user: UserIdentity) {
        if (isSyncInFlight) return
        isSyncInFlight = true

        _syncState.value = _syncState.value.copy(
            status = SyncStatus.SYNCING,
            errorMessage = null
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = syncEngine.performSync(user)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        val nowMs = System.currentTimeMillis()
                        preferenceManager.setLastSyncTimestamp(nowMs)
                        val formatted = SyncUiState.formatRelativeSyncTime(nowMs)

                        _syncState.value = SyncUiState(
                            status = SyncStatus.SYNC_SUCCESS,
                            lastSyncFormatted = formatted,
                            lastSyncTimestampMs = nowMs,
                            pendingChangesCount = 0,
                            errorMessage = null
                        )
                    } else {
                        val userMsg = mapErrorMessage(result.errorMessage)
                        val errorStatus = mapErrorStatus(result.errorMessage)
                        _syncState.value = _syncState.value.copy(
                            status = errorStatus,
                            errorMessage = userMsg
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _syncState.value = _syncState.value.copy(
                        status = SyncStatus.SYNC_ERROR,
                        errorMessage = "Backup couldn't be completed. Please try again."
                    )
                }
            } finally {
                isSyncInFlight = false
            }
        }
    }

    fun performStartFresh(user: UserIdentity, onComplete: () -> Unit) {
        if (isSyncInFlight) return
        isSyncInFlight = true

        _syncState.value = _syncState.value.copy(
            status = SyncStatus.SYNCING,
            errorMessage = null
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = syncEngine.performStartFresh(user)
                withContext(Dispatchers.Main) {
                    _syncState.value = SyncUiState(
                        status = SyncStatus.IDLE_NEVER_SYNCED,
                        lastSyncFormatted = "Never",
                        lastSyncTimestampMs = 0L,
                        pendingChangesCount = 0,
                        errorMessage = null
                    )
                    onComplete()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _syncState.value = _syncState.value.copy(
                        status = SyncStatus.SYNC_ERROR,
                        errorMessage = "Start Fresh reset failed. Please try again."
                    )
                }
            } finally {
                isSyncInFlight = false
            }
        }
    }

    private fun mapErrorMessage(rawError: String?): String {
        if (rawError.isNullOrBlank()) return "Backup couldn't be completed. Please try again."
        val lower = rawError.lowercase()
        return when {
            lower.contains("offline") || lower.contains("connect") || lower.contains("network") ->
                "You're offline. We'll try again when you're connected."
            lower.contains("401") || lower.contains("session") || lower.contains("unauthenticated") ->
                "Google account access is required."
            lower.contains("403") || lower.contains("denied") || lower.contains("permission") ->
                "Google Sheets access was not granted."
            lower.contains("404") || lower.contains("not found") ->
                "Your backup spreadsheet couldn't be found. We can create a new backup from this device."
            lower.contains("429") || lower.contains("rate limit") ->
                "Google is temporarily limiting requests. Please try again later."
            else -> "Backup couldn't be completed. Please try again."
        }
    }

    private fun mapErrorStatus(rawError: String?): SyncStatus {
        if (rawError.isNullOrBlank()) return SyncStatus.SYNC_ERROR
        val lower = rawError.lowercase()
        return when {
            lower.contains("offline") || lower.contains("connect") || lower.contains("network") -> SyncStatus.OFFLINE
            lower.contains("401") || lower.contains("session") -> SyncStatus.AUTH_REQUIRED
            lower.contains("403") || lower.contains("denied") -> SyncStatus.AUTHORIZATION_ERROR
            lower.contains("404") || lower.contains("not found") -> SyncStatus.SPREADSHEET_NOT_FOUND
            else -> SyncStatus.SYNC_ERROR
        }
    }
}
