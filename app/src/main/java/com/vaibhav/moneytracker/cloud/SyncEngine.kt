package com.vaibhav.moneytracker.cloud

import android.util.Log
import com.vaibhav.moneytracker.*
import com.vaibhav.moneytracker.auth.UserIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SyncResult(
    val isSuccess: Boolean,
    val uploadedCount: Int = 0,
    val downloadedCount: Int = 0,
    val errorMessage: String? = null
)

class SyncEngine(
    private val database: MoneyTrackerDatabase,
    private val repository: MoneyRepository,
    private val sheetsRepository: GoogleSheetsRepository,
    private val cloudManager: CloudSpreadsheetManager
) {

    companion object {
        private const val TAG = "MoneyTrackerSync"

        val TOPOLOGICAL_ORDER = listOf(
            "Accounts",
            "Categories",
            "Tags",
            "Subscriptions",
            "Budgets",
            "Goals",
            "Rules",
            "RuleTags",
            "Transactions",
            "TransactionTags",
            "CapturedInbox"
        )
    }

    /**
     * Entry point for synchronization.
     * Evaluates local vs cloud state and chooses First Backup, New Device Restore, or Incremental Sync.
     */
    suspend fun performSync(user: UserIdentity): SyncResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting sync for user: ${user.email}")
        try {
            val provision = cloudManager.discoverOrCreateSpreadsheet(user)
            val spreadsheetId = provision.spreadsheetId
            if (spreadsheetId.isNullOrBlank()) {
                Log.e(TAG, "Cloud setup failed: ${provision.errorMessage}")
                return@withContext SyncResult(isSuccess = false, errorMessage = provision.errorMessage ?: "Cloud setup failed")
            }

            Log.d(TAG, "Spreadsheet ID verified: $spreadsheetId")

            // Read cloud data for initial evaluation
            val batchResult = sheetsRepository.batchReadTabs(spreadsheetId, TOPOLOGICAL_ORDER)
            val cloudData = when (batchResult) {
                is SheetsResult.Success -> batchResult.data
                is SheetsResult.Error -> {
                    Log.e(TAG, "Batch read tabs failed: ${batchResult.message}")
                    return@withContext SyncResult(isSuccess = false, errorMessage = batchResult.message)
                }
            }

            val localTxCount = database.transactionDao().getAll().size
            val cloudTxCount = cloudData["Transactions"]?.size ?: 0

            Log.d(TAG, "Local transactions: $localTxCount, Cloud transactions: $cloudTxCount")

            // Scenario 1: New Device Restore (Empty Room + Populated Cloud)
            if (localTxCount == 0 && cloudTxCount > 0) {
                Log.d(TAG, "Executing New Device Restore flow")
                return@withContext performNewDeviceRestore(cloudData)
            }

            // Scenario 2: First Backup (Populated Room + Empty Cloud)
            if (localTxCount > 0 && cloudTxCount == 0) {
                Log.d(TAG, "Executing First Backup flow")
                return@withContext performFirstBackup(spreadsheetId)
            }

            // Scenario 3: Bidirectional Incremental Sync
            Log.d(TAG, "Executing Incremental Sync flow")
            return@withContext performIncrementalSync(spreadsheetId, cloudData)

        } catch (e: Exception) {
            Log.e(TAG, "Sync exception: ${e.localizedMessage}", e)
            return@withContext SyncResult(isSuccess = false, errorMessage = "Sync failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    suspend fun performFirstBackup(spreadsheetId: String): SyncResult = withContext(Dispatchers.IO) {
        try {
            var uploadedCount = 0

            // 1. Accounts
            val accounts = database.accountDao().getAllIncludingInactive()
            val accountRows = accounts.map { SheetsDataMapper.accountToRow(it) }
            val accRes = sheetsRepository.appendRows(spreadsheetId, "Accounts", accountRows)
            if (accRes !is SheetsResult.Success) return@withContext handleWriteError("Accounts", accRes)
            uploadedCount += accounts.size

            // 2. Categories
            val categories = database.categoryDao().getAll()
            val categoryRows = categories.map { SheetsDataMapper.categoryToRow(it) }
            val catRes = sheetsRepository.appendRows(spreadsheetId, "Categories", categoryRows)
            if (catRes !is SheetsResult.Success) return@withContext handleWriteError("Categories", catRes)

            // 3. Tags
            val tags = database.tagDao().getAll()
            val tagRows = tags.map { SheetsDataMapper.tagToRow(it) }
            val tagRes = sheetsRepository.appendRows(spreadsheetId, "Tags", tagRows)
            if (tagRes !is SheetsResult.Success) return@withContext handleWriteError("Tags", tagRes)

            // 4. Subscriptions
            val subscriptions = database.subscriptionDao().getAllActive()
            val subRows = subscriptions.map { SheetsDataMapper.subscriptionToRow(it) }
            val subRes = sheetsRepository.appendRows(spreadsheetId, "Subscriptions", subRows)
            if (subRes !is SheetsResult.Success) return@withContext handleWriteError("Subscriptions", subRes)

            // 5. Budgets
            val budgets = database.budgetDao().getAllActive()
            val budgetRows = budgets.map { SheetsDataMapper.budgetToRow(it) }
            val budgetRes = sheetsRepository.appendRows(spreadsheetId, "Budgets", budgetRows)
            if (budgetRes !is SheetsResult.Success) return@withContext handleWriteError("Budgets", budgetRes)

            // 6. Goals
            val goals = database.goalDao().getAllActive()
            val goalRows = goals.map { SheetsDataMapper.goalToRow(it) }
            val goalRes = sheetsRepository.appendRows(spreadsheetId, "Goals", goalRows)
            if (goalRes !is SheetsResult.Success) return@withContext handleWriteError("Goals", goalRes)

            // 7. Rules
            val rules = database.categorizationRuleDao().getAllActive()
            val ruleRows = rules.map { SheetsDataMapper.ruleToRow(it) }
            val ruleRes = sheetsRepository.appendRows(spreadsheetId, "Rules", ruleRows)
            if (ruleRes !is SheetsResult.Success) return@withContext handleWriteError("Rules", ruleRes)

            // 8. Transactions
            val transactions = database.transactionDao().getAll()
            val txRows = transactions.map { SheetsDataMapper.transactionToRow(it) }
            val txRes = sheetsRepository.appendRows(spreadsheetId, "Transactions", txRows)
            if (txRes !is SheetsResult.Success) return@withContext handleWriteError("Transactions", txRes)
            uploadedCount += transactions.size

            // 9. Captured Inbox
            val capturedList = database.capturedTransactionDao().getPendingCaptures()
            val capturedRows = capturedList.map { SheetsDataMapper.capturedToRow(it) }
            val capRes = sheetsRepository.appendRows(spreadsheetId, "CapturedInbox", capturedRows)
            if (capRes !is SheetsResult.Success) return@withContext handleWriteError("CapturedInbox", capRes)

            // Drain local sync logs ONLY after all cloud writes succeeded
            database.syncLogDao().deleteAll()
            Log.d(TAG, "First Backup completed successfully. Uploaded: $uploadedCount")

            SyncResult(isSuccess = true, uploadedCount = uploadedCount, downloadedCount = 0)
        } catch (e: Exception) {
            Log.e(TAG, "First backup exception: ${e.localizedMessage}", e)
            SyncResult(isSuccess = false, errorMessage = "First backup error: ${e.localizedMessage}")
        }
    }

    suspend fun performNewDeviceRestore(cloudData: Map<String, List<List<String>>>): SyncResult = withContext(Dispatchers.IO) {
        try {
            var downloadedCount = 0

            repository.withSyncSuppressed {
                repository.executeInTransaction {
                    // Accounts
                    cloudData["Accounts"]?.forEach { row ->
                        val acc = SheetsDataMapper.rowToAccount(row)
                        if (acc != null) database.accountDao().insert(acc)
                    }

                    // Categories
                    cloudData["Categories"]?.forEach { row ->
                        val cat = SheetsDataMapper.rowToCategory(row)
                        if (cat != null) database.categoryDao().insert(cat)
                    }

                    // Tags
                    cloudData["Tags"]?.forEach { row ->
                        val tag = SheetsDataMapper.rowToTag(row)
                        if (tag != null) database.tagDao().insert(tag)
                    }

                    // Subscriptions
                    cloudData["Subscriptions"]?.forEach { row ->
                        val sub = SheetsDataMapper.rowToSubscription(row)
                        if (sub != null) database.subscriptionDao().insert(sub)
                    }

                    // Budgets
                    cloudData["Budgets"]?.forEach { row ->
                        val b = SheetsDataMapper.rowToBudget(row)
                        if (b != null) database.budgetDao().insert(b)
                    }

                    // Goals
                    cloudData["Goals"]?.forEach { row ->
                        val g = SheetsDataMapper.rowToGoal(row)
                        if (g != null) database.goalDao().insert(g)
                    }

                    // Rules
                    cloudData["Rules"]?.forEach { row ->
                        val r = SheetsDataMapper.rowToRule(row)
                        if (r != null) database.categorizationRuleDao().insert(r)
                    }

                    // Transactions
                    val txRows = cloudData["Transactions"] ?: emptyList()
                    txRows.forEach { row ->
                        val tx = SheetsDataMapper.rowToTransaction(row)
                        if (tx != null) {
                            database.transactionDao().insert(tx)
                            downloadedCount++
                        }
                    }

                    // TransactionTags CrossRef
                    cloudData["TransactionTags"]?.forEach { row ->
                        val ref = SheetsDataMapper.rowToTransactionTag(row)
                        if (ref != null) database.transactionDao().insertTagRef(ref)
                    }

                    // RuleTags CrossRef
                    cloudData["RuleTags"]?.forEach { row ->
                        val ref = SheetsDataMapper.rowToRuleTag(row)
                        if (ref != null) database.categorizationRuleDao().insertTagRef(ref)
                    }

                    // CapturedInbox
                    cloudData["CapturedInbox"]?.forEach { row ->
                        val cap = SheetsDataMapper.rowToCaptured(row)
                        if (cap != null) database.capturedTransactionDao().insert(cap)
                    }
                }
            }

            Log.d(TAG, "New Device Restore completed successfully. Downloaded: $downloadedCount")
            SyncResult(isSuccess = true, uploadedCount = 0, downloadedCount = downloadedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Restore exception: ${e.localizedMessage}", e)
            SyncResult(isSuccess = false, errorMessage = "Restore error: ${e.localizedMessage}")
        }
    }

    suspend fun performIncrementalSync(spreadsheetId: String, cloudData: Map<String, List<List<String>>>): SyncResult = withContext(Dispatchers.IO) {
        try {
            var uploadedCount = 0
            var downloadedCount = 0

            // 1. Upload local changes queued in sync_logs
            val logs = database.syncLogDao().getAll()
            Log.d(TAG, "Draining ${logs.size} pending sync logs")

            if (logs.isNotEmpty()) {
                val processedLogIds = mutableListOf<Long>()

                for (log in logs) {
                    val tabName = mapEntityTypeToTab(log.entityType)
                    if (tabName == null) {
                        processedLogIds.add(log.id)
                        continue
                    }

                    val rowData = buildRowForLog(log)
                    val isDelete = log.action == "DELETE"

                    if (rowData != null || isDelete) {
                        val cloudRows = cloudData[tabName] ?: emptyList()
                        val rowIndex = cloudRows.indexOfFirst { it.firstOrNull() == log.entityId.toString() }

                        val writeResult = if (rowIndex >= 0) {
                            // Row exists in cloud -> Update existing row safely by stable ID
                            val rowNum = rowIndex + 2 // +2 for 1-based index and header row
                            val range = "'$tabName'!A$rowNum:Z$rowNum"
                            val payload = rowData ?: listOf(log.entityId.toString(), "", "", "", "", "", "", "", "", "", "", "TRUE", log.timestampMs.toString())
                            sheetsRepository.updateRange(spreadsheetId, range, listOf(payload))
                        } else if (rowData != null) {
                            // Row does not exist in cloud -> Append new row
                            sheetsRepository.appendRows(spreadsheetId, tabName, listOf(rowData))
                        } else {
                            SheetsResult.Success(0)
                        }

                        if (writeResult is SheetsResult.Success) {
                            processedLogIds.add(log.id)
                            uploadedCount++
                        } else {
                            Log.e(TAG, "Write failed for log ${log.id} (${log.entityType}): ${(writeResult as SheetsResult.Error).message}")
                            // Stop draining on first write error to preserve sync log ordering
                            break
                        }
                    } else {
                        processedLogIds.add(log.id)
                    }
                }

                if (processedLogIds.isNotEmpty()) {
                    database.syncLogDao().deleteByIds(processedLogIds)
                    Log.d(TAG, "Successfully processed and removed ${processedLogIds.size} sync logs")
                }
            }

            // 2. Download cloud updates
            repository.withSyncSuppressed {
                val cloudTxRows = cloudData["Transactions"] ?: emptyList()
                val existingLocalTxs = database.transactionDao().getAll().associateBy { it.id }

                cloudTxRows.forEach { row ->
                    val cloudTx = SheetsDataMapper.rowToTransaction(row)
                    val isDeletedInCloud = SheetsDataMapper.parseBoolean(row.getOrNull(11))
                    val cloudUpdatedAtMs = SheetsDataMapper.parseLong(row.getOrNull(12))

                    if (cloudTx != null) {
                        val localTx = existingLocalTxs[cloudTx.id]
                        if (localTx == null && !isDeletedInCloud) {
                            database.transactionDao().insert(cloudTx)
                            downloadedCount++
                        } else if (localTx != null) {
                            if (isDeletedInCloud) {
                                database.transactionDao().delete(localTx)
                                downloadedCount++
                            } else if (cloudUpdatedAtMs > localTx.createdAt) {
                                database.transactionDao().update(cloudTx)
                                downloadedCount++
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "Incremental sync completed. Uploaded: $uploadedCount, Downloaded: $downloadedCount")
            SyncResult(isSuccess = true, uploadedCount = uploadedCount, downloadedCount = downloadedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Incremental sync exception: ${e.localizedMessage}", e)
            SyncResult(isSuccess = false, errorMessage = "Incremental sync error: ${e.localizedMessage}")
        }
    }

    private suspend fun buildRowForLog(log: SyncLogEntity): List<Any>? {
        return when (log.entityType) {
            "TRANSACTION" -> {
                val tx = database.transactionDao().getById(log.entityId)
                tx?.let { SheetsDataMapper.transactionToRow(it, isDeleted = false, updatedAtMs = log.timestampMs) }
            }
            "ACCOUNT" -> {
                val acc = database.accountDao().getById(log.entityId)
                acc?.let { SheetsDataMapper.accountToRow(it, isDeleted = false, updatedAtMs = log.timestampMs) }
            }
            "CATEGORY" -> {
                val cat = database.categoryDao().getAll().firstOrNull { it.id == log.entityId }
                cat?.let { SheetsDataMapper.categoryToRow(it, isDeleted = false, updatedAtMs = log.timestampMs) }
            }
            "TAG" -> {
                val tag = database.tagDao().getAll().firstOrNull { it.id == log.entityId }
                tag?.let { SheetsDataMapper.tagToRow(it, isDeleted = false, updatedAtMs = log.timestampMs) }
            }
            "SUBSCRIPTION" -> {
                val sub = database.subscriptionDao().getAllActive().firstOrNull { it.id == log.entityId }
                sub?.let { SheetsDataMapper.subscriptionToRow(it, isDeleted = false, updatedAtMs = log.timestampMs) }
            }
            "BUDGET" -> {
                val b = database.budgetDao().getAllActive().firstOrNull { it.id == log.entityId }
                b?.let { SheetsDataMapper.budgetToRow(it, isDeleted = false, updatedAtMs = log.timestampMs) }
            }
            "GOAL" -> {
                val g = database.goalDao().getAllActive().firstOrNull { it.id == log.entityId }
                g?.let { SheetsDataMapper.goalToRow(it, isDeleted = false, updatedAtMs = log.timestampMs) }
            }
            "RULE" -> {
                val r = database.categorizationRuleDao().getAllActive().firstOrNull { it.id == log.entityId }
                r?.let { SheetsDataMapper.ruleToRow(it, isDeleted = false, updatedAtMs = log.timestampMs) }
            }
            "CAPTURED" -> {
                val cap = database.capturedTransactionDao().getById(log.entityId)
                cap?.let { SheetsDataMapper.capturedToRow(it, isDeleted = false, updatedAtMs = log.timestampMs) }
            }
            else -> null
        }
    }

    private fun mapEntityTypeToTab(entityType: String): String? {
        return when (entityType) {
            "TRANSACTION" -> "Transactions"
            "ACCOUNT" -> "Accounts"
            "CATEGORY" -> "Categories"
            "TAG" -> "Tags"
            "TRANSACTION_TAG" -> "TransactionTags"
            "SUBSCRIPTION" -> "Subscriptions"
            "BUDGET" -> "Budgets"
            "GOAL" -> "Goals"
            "RULE" -> "Rules"
            "RULE_TAG" -> "RuleTags"
            "CAPTURED" -> "CapturedInbox"
            else -> null
        }
    }

    private fun handleWriteError(tabName: String, res: SheetsResult<*>): SyncResult {
        val msg = if (res is SheetsResult.Error) res.message else "Write error for $tabName"
        Log.e(TAG, "First backup write error for $tabName: $msg")
        return SyncResult(isSuccess = false, errorMessage = msg)
    }
}
