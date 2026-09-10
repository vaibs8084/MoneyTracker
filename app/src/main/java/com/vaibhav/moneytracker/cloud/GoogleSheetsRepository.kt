package com.vaibhav.moneytracker.cloud

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.ClearValuesRequest
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.ValueRange
import com.vaibhav.moneytracker.auth.AuthPreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SheetsErrorType {
    AUTHENTICATION_ERROR,
    AUTHORIZATION_ERROR,
    SPREADSHEET_NOT_FOUND,
    TAB_NOT_FOUND,
    NETWORK_ERROR,
    API_RATE_LIMIT,
    MALFORMED_DATA,
    UNKNOWN_ERROR
}

sealed class SheetsResult<out T> {
    data class Success<out T>(val data: T) : SheetsResult<T>()
    data class Error(val errorType: SheetsErrorType, val message: String, val cause: Throwable? = null) : SheetsResult<Nothing>()
}

class GoogleSheetsRepository(private val context: Context) {

    private val preferenceManager = AuthPreferenceManager(context)

    companion object {
        const val BATCH_SIZE = 500
    }

    private fun getSheetsService(email: String): Sheets {
        val credential = GoogleAccountCredential.usingOAuth2(
            context.applicationContext,
            listOf(DriveScopes.DRIVE_FILE, SheetsScopes.SPREADSHEETS)
        ).apply {
            selectedAccountName = email
        }

        return Sheets.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Money Tracker").build()
    }

    private fun getAuthorizedUserEmail(): String? {
        return preferenceManager.getUserSession()?.email
    }

    suspend fun readTabRows(spreadsheetId: String, tabName: String): SheetsResult<List<List<String>>> = withContext(Dispatchers.IO) {
        val email = getAuthorizedUserEmail()
            ?: return@withContext SheetsResult.Error(SheetsErrorType.AUTHENTICATION_ERROR, "No authenticated user session found.")

        try {
            val sheetsService = getSheetsService(email)
            val range = "'$tabName'!A2:Z" // A2:Z skips header row
            val response = sheetsService.spreadsheets().values().get(spreadsheetId, range).execute()
            val rawValues = response.getValues() ?: emptyList()

            val stringRows = rawValues.map { row ->
                (row as? List<*>)?.map { it?.toString() ?: "" } ?: emptyList()
            }

            SheetsResult.Success(stringRows)
        } catch (e: GoogleJsonResponseException) {
            handleGoogleJsonException(e)
        } catch (e: Exception) {
            SheetsResult.Error(SheetsErrorType.NETWORK_ERROR, "Network error reading tab '$tabName': ${e.localizedMessage}", e)
        }
    }

    suspend fun batchReadTabs(spreadsheetId: String, tabNames: List<String>): SheetsResult<Map<String, List<List<String>>>> = withContext(Dispatchers.IO) {
        val email = getAuthorizedUserEmail()
            ?: return@withContext SheetsResult.Error(SheetsErrorType.AUTHENTICATION_ERROR, "No authenticated user session found.")

        try {
            val sheetsService = getSheetsService(email)
            val ranges = tabNames.map { "'$it'!A2:Z" }

            val batchResponse = sheetsService.spreadsheets().values()
                .batchGet(spreadsheetId)
                .setRanges(ranges)
                .execute()

            val resultMap = mutableMapOf<String, List<List<String>>>()
            val valueRanges = batchResponse.valueRanges ?: emptyList()

            tabNames.forEachIndexed { index, tabName ->
                val vRange = valueRanges.getOrNull(index)
                val rawValues = vRange?.getValues() ?: emptyList()
                val stringRows = rawValues.map { row ->
                    (row as? List<*>)?.map { it?.toString() ?: "" } ?: emptyList()
                }
                resultMap[tabName] = stringRows
            }

            SheetsResult.Success(resultMap)
        } catch (e: GoogleJsonResponseException) {
            handleGoogleJsonException(e)
        } catch (e: Exception) {
            SheetsResult.Error(SheetsErrorType.NETWORK_ERROR, "Network error batch reading tabs: ${e.localizedMessage}", e)
        }
    }

    suspend fun appendRows(spreadsheetId: String, tabName: String, rows: List<List<Any>>): SheetsResult<Int> = withContext(Dispatchers.IO) {
        if (rows.isEmpty()) return@withContext SheetsResult.Success(0)
        val email = getAuthorizedUserEmail()
            ?: return@withContext SheetsResult.Error(SheetsErrorType.AUTHENTICATION_ERROR, "No authenticated user session found.")

        try {
            val sheetsService = getSheetsService(email)
            var totalAppended = 0

            // Execute in 500-row batches for memory safety and rate limit management
            rows.chunked(BATCH_SIZE).forEach { chunk ->
                val valueRange = ValueRange().setValues(chunk as List<List<Any>>)
                val range = "'$tabName'!A1"

                val appendResponse = sheetsService.spreadsheets().values()
                    .append(spreadsheetId, range, valueRange)
                    .setValueInputOption("RAW")
                    .setInsertDataOption("INSERT_ROWS")
                    .execute()

                val updatedRows = appendResponse.updates?.updatedRows ?: chunk.size
                totalAppended += updatedRows
            }

            SheetsResult.Success(totalAppended)
        } catch (e: GoogleJsonResponseException) {
            handleGoogleJsonException(e)
        } catch (e: Exception) {
            SheetsResult.Error(SheetsErrorType.NETWORK_ERROR, "Network error appending rows to '$tabName': ${e.localizedMessage}", e)
        }
    }

    suspend fun updateRange(spreadsheetId: String, range: String, rows: List<List<Any>>): SheetsResult<Int> = withContext(Dispatchers.IO) {
        if (rows.isEmpty()) return@withContext SheetsResult.Success(0)
        val email = getAuthorizedUserEmail()
            ?: return@withContext SheetsResult.Error(SheetsErrorType.AUTHENTICATION_ERROR, "No authenticated user session found.")

        try {
            val sheetsService = getSheetsService(email)
            val valueRange = ValueRange().setValues(rows as List<List<Any>>)

            val updateResponse = sheetsService.spreadsheets().values()
                .update(spreadsheetId, range, valueRange)
                .setValueInputOption("RAW")
                .execute()

            val updatedRows = updateResponse.updatedRows ?: rows.size
            SheetsResult.Success(updatedRows)
        } catch (e: GoogleJsonResponseException) {
            handleGoogleJsonException(e)
        } catch (e: Exception) {
            SheetsResult.Error(SheetsErrorType.NETWORK_ERROR, "Network error updating range '$range': ${e.localizedMessage}", e)
        }
    }

    suspend fun batchUpdateSpreadsheet(spreadsheetId: String, requests: List<Request>): SheetsResult<Boolean> = withContext(Dispatchers.IO) {
        if (requests.isEmpty()) return@withContext SheetsResult.Success(true)
        val email = getAuthorizedUserEmail()
            ?: return@withContext SheetsResult.Error(SheetsErrorType.AUTHENTICATION_ERROR, "No authenticated user session found.")

        try {
            val sheetsService = getSheetsService(email)
            val batchRequest = BatchUpdateSpreadsheetRequest().setRequests(requests)

            sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute()
            SheetsResult.Success(true)
        } catch (e: GoogleJsonResponseException) {
            handleGoogleJsonException(e)
        } catch (e: Exception) {
            SheetsResult.Error(SheetsErrorType.NETWORK_ERROR, "Network error updating spreadsheet: ${e.localizedMessage}", e)
        }
    }

    suspend fun clearTabRows(spreadsheetId: String, tabName: String): SheetsResult<Boolean> = withContext(Dispatchers.IO) {
        val email = getAuthorizedUserEmail()
            ?: return@withContext SheetsResult.Error(SheetsErrorType.AUTHENTICATION_ERROR, "No authenticated user session found.")

        try {
            val sheetsService = getSheetsService(email)
            val range = "'$tabName'!A2:Z10000"
            sheetsService.spreadsheets().values().clear(spreadsheetId, range, ClearValuesRequest()).execute()
            SheetsResult.Success(true)
        } catch (e: GoogleJsonResponseException) {
            handleGoogleJsonException(e)
        } catch (e: Exception) {
            SheetsResult.Error(SheetsErrorType.NETWORK_ERROR, "Network error clearing tab '$tabName': ${e.localizedMessage}", e)
        }
    }

    private fun <T> handleGoogleJsonException(e: GoogleJsonResponseException): SheetsResult<T> {
        val statusCode = e.statusCode
        return when (statusCode) {
            401 -> SheetsResult.Error(SheetsErrorType.AUTHENTICATION_ERROR, "Google session expired. Re-authentication required.", e)
            403 -> SheetsResult.Error(SheetsErrorType.AUTHORIZATION_ERROR, "Google Sheets authorization scope denied or rate limit exceeded.", e)
            404 -> SheetsResult.Error(SheetsErrorType.SPREADSHEET_NOT_FOUND, "Target Google Spreadsheet not found on Drive.", e)
            429 -> SheetsResult.Error(SheetsErrorType.API_RATE_LIMIT, "Google Sheets API rate limit reached. Retrying shorty.", e)
            else -> SheetsResult.Error(SheetsErrorType.UNKNOWN_ERROR, "Google Sheets API error ($statusCode): ${e.message}", e)
        }
    }
}
