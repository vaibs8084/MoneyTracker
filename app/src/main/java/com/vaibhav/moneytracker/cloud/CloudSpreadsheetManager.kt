package com.vaibhav.moneytracker.cloud

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.*
import com.vaibhav.moneytracker.auth.AuthPreferenceManager
import com.vaibhav.moneytracker.auth.UserIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProvisionResult(
    val spreadsheetId: String? = null,
    val isNewlyCreated: Boolean = false,
    val errorMessage: String? = null
)

class CloudSpreadsheetManager(private val context: Context) {

    private val preferenceManager = AuthPreferenceManager(context)

    companion object {
        const val SPREADSHEET_TITLE = "Money Tracker Backup"

        val REQUIRED_TABS = listOf(
            "Sync Metadata",
            "Transactions",
            "Accounts",
            "Categories",
            "Tags",
            "TransactionTags",
            "Subscriptions",
            "Budgets",
            "Goals",
            "Rules",
            "RuleTags",
            "CapturedInbox"
        )

        val TAB_HEADERS = mapOf(
            "Sync Metadata" to listOf("PropertyKey", "PropertyValue", "LastUpdatedMs"),
            "Transactions" to listOf("ID", "Title", "Category", "Account", "Type", "AmountPaise", "Note", "CreatedAtMs", "AccountId", "CategoryId", "ExternalMoneyKind", "IsDeleted", "UpdatedAtMs"),
            "Accounts" to listOf("ID", "Name", "Type", "OpeningBalancePaise", "IsActive", "CreatedAtMs", "IsDeleted", "UpdatedAtMs"),
            "Categories" to listOf("ID", "Name", "Icon", "Color", "IsActive", "IsDeleted", "UpdatedAtMs"),
            "Tags" to listOf("ID", "Name", "IsActive", "IsDeleted", "UpdatedAtMs"),
            "TransactionTags" to listOf("TransactionID", "TagId"),
            "Subscriptions" to listOf("ID", "Name", "AmountPaise", "Cadence", "NextDateMs", "CategoryId", "AccountId", "IsConfirmed", "IsActive", "IsDeleted", "UpdatedAtMs"),
            "Budgets" to listOf("ID", "CategoryId", "LimitPaise", "Period", "IsActive", "IsDeleted", "UpdatedAtMs"),
            "Goals" to listOf("ID", "Name", "TargetPaise", "ManualProgressPaise", "TargetDateMs", "LinkedAccountId", "IsCompleted", "IsActive", "IsDeleted", "UpdatedAtMs"),
            "Rules" to listOf("ID", "TitlePattern", "TargetCategoryId", "IsActive", "IsDeleted", "UpdatedAtMs"),
            "RuleTags" to listOf("RuleID", "TagId"),
            "CapturedInbox" to listOf("ID", "Title", "AmountPaise", "Type", "CreatedAtMs", "SourceApp", "ReferenceNumber", "SuggestedAccountId", "SuggestedCategoryId", "Status", "IsDeleted", "UpdatedAtMs")
        )
    }

    private fun getCredential(email: String): GoogleAccountCredential {
        return GoogleAccountCredential.usingOAuth2(
            context.applicationContext,
            listOf(DriveScopes.DRIVE_FILE, SheetsScopes.SPREADSHEETS)
        ).apply {
            selectedAccountName = email
        }
    }

    private fun getDriveService(credential: GoogleAccountCredential): Drive {
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Money Tracker").build()
    }

    private fun getSheetsService(credential: GoogleAccountCredential): Sheets {
        return Sheets.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Money Tracker").build()
    }

    suspend fun discoverOrCreateSpreadsheet(user: UserIdentity): ProvisionResult = withContext(Dispatchers.IO) {
        try {
            val credential = getCredential(user.email)
            val driveService = getDriveService(credential)
            val sheetsService = getSheetsService(credential)

            // 1. Fast Path: Verify locally saved spreadsheet ID
            val savedSheetId = user.spreadsheetId ?: preferenceManager.getUserSession()?.spreadsheetId
            if (!savedSheetId.isNullOrBlank()) {
                try {
                    val file = driveService.files().get(savedSheetId).setFields("id, name, trashed").execute()
                    if (file != null && file.trashed != true) {
                        ensureTabsExist(sheetsService, savedSheetId)
                        return@withContext ProvisionResult(spreadsheetId = savedSheetId, isNewlyCreated = false)
                    }
                } catch (e: Exception) {
                    // Stored sheet ID deleted, trashed, or inaccessible -> Fall back to Drive search
                }
            }

            // 2. Search Drive for existing "Money Tracker Backup" spreadsheet
            val searchResults = driveService.files().list()
                .setQ("mimeType = 'application/vnd.google-apps.spreadsheet' and name contains 'Money Tracker' and trashed = false")
                .setFields("files(id, name, createdTime)")
                .setOrderBy("createdTime desc")
                .execute()

            val rawFiles = searchResults.files
            var foundSheetId: String? = null

            if (rawFiles != null) {
                val candidateList = rawFiles as? List<*> ?: emptyList<Any>()
                for (item in candidateList) {
                    val candidate = item as? com.google.api.services.drive.model.File ?: continue
                    val candidateId = candidate.id
                    if (!candidateId.isNullOrBlank()) {
                        try {
                            ensureTabsExist(sheetsService, candidateId)
                            preferenceManager.setSpreadsheetId(candidateId)
                            foundSheetId = candidateId
                            break
                        } catch (e: Exception) {
                            // Candidate inaccessible or invalid, try next
                        }
                    }
                }
            }

            if (foundSheetId != null) {
                return@withContext ProvisionResult(spreadsheetId = foundSheetId, isNewlyCreated = false)
            }

            // 3. Create new Spreadsheet if no valid existing spreadsheet found
            val spreadsheet = Spreadsheet().setProperties(
                SpreadsheetProperties().setTitle(SPREADSHEET_TITLE)
            )

            val created = sheetsService.spreadsheets().create(spreadsheet).execute()
            val newSheetId = created.spreadsheetId ?: throw IllegalStateException("Spreadsheet creation returned null ID")

            preferenceManager.setSpreadsheetId(newSheetId)
            ensureTabsExist(sheetsService, newSheetId)
            initializeMetadataTab(sheetsService, newSheetId, user)

            return@withContext ProvisionResult(spreadsheetId = newSheetId, isNewlyCreated = true)

        } catch (e: Exception) {
            return@withContext ProvisionResult(
                errorMessage = "Cloud setup error: ${e.localizedMessage ?: "Unable to connect to Google Drive/Sheets"}"
            )
        }
    }

    private fun ensureTabsExist(sheetsService: Sheets, spreadsheetId: String) {
        val spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute()
        val existingTitles = spreadsheet.sheets?.mapNotNull { it.properties?.title }?.toSet() ?: emptySet()

        val addSheetRequests = mutableListOf<Request>()

        REQUIRED_TABS.forEach { tabName ->
            if (!existingTitles.contains(tabName)) {
                addSheetRequests.add(
                    Request().setAddSheet(
                        AddSheetRequest().setProperties(SheetProperties().setTitle(tabName))
                    )
                )
            }
        }

        if (addSheetRequests.isNotEmpty()) {
            val batchRequest = BatchUpdateSpreadsheetRequest().setRequests(addSheetRequests)
            sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute()
        }

        // Initialize header rows for any newly created tabs without destroying existing data
        REQUIRED_TABS.forEach { tabName ->
            if (!existingTitles.contains(tabName)) {
                val headers = TAB_HEADERS[tabName] ?: emptyList()
                if (headers.isNotEmpty()) {
                    val valueRange = ValueRange().setValues(listOf(headers as List<Any>))
                    sheetsService.spreadsheets().values()
                        .update(spreadsheetId, "'$tabName'!A1", valueRange)
                        .setValueInputOption("RAW")
                        .execute()
                }
            }
        }
    }

    private fun initializeMetadataTab(sheetsService: Sheets, spreadsheetId: String, user: UserIdentity) {
        val metadataValues = listOf(
            listOf("app_id", "MoneyTracker", System.currentTimeMillis().toString()),
            listOf("version", "1.0", System.currentTimeMillis().toString()),
            listOf("owner_google_id", user.googleAccountId, System.currentTimeMillis().toString()),
            listOf("owner_email", user.email, System.currentTimeMillis().toString())
        )
        val valueRange = ValueRange().setValues(metadataValues as List<List<Any>>)
        sheetsService.spreadsheets().values()
            .update(spreadsheetId, "'Sync Metadata'!A2", valueRange)
            .setValueInputOption("RAW")
            .execute()
    }
}
