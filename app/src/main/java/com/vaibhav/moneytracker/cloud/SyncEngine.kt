package com.vaibhav.moneytracker.cloud

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
     * Evaluates local vs cloud state and chooses First Backup, New Device Restore, or Bidirectional Sync.
     */
    suspend fun performSync(user: UserIdentity): SyncResult = withContext(Dispatchers.IO) {
        try {
            val provision = cloudManager.discoverOrCreateSpreadsheet(user)
            val spreadsheetId = provision.spreadsheetId
                ?: return@withContext SyncResult(isSuccess = false, errorMessage = provision.errorMessage ?: "Cloud setup failed")

            // Read cloud data for initial evaluation
            val batchResult = sheetsRepository.batchReadTabs(spreadsheetId, TOPOLOGICAL_ORDER)
            val cloudData = when (batchResult) {
                is SheetsResult.Success -> batchResult.data
                is SheetsResult.Error -> return@withContext SyncResult(isSuccess = false, errorMessage = batchResult.message)
            }

            val localTxCount = database.transactionDao().getAll().size
            val cloudTxCount = cloudData["Transactions"]?.size ?: 0

            // Scenario 1: New Device Restore (Empty Room + Populated Cloud)
            if (localTxCount == 0 && cloudTxCount > 0) {
                return@withContext performNewDeviceRestore(cloudData)
            }

            // Scenario 2: First Backup (Populated Room + Empty Cloud)
            if (localTxCount > 0 && cloudTxCount == 0) {
                return@withContext performFirstBackup(spreadsheetId)
            }

            // Scenario 3: Bidirectional Incremental Sync
            return@withContext performIncrementalSync(spreadsheetId, cloudData)

        } catch (e: Exception) {
            return@withContext SyncResult(isSuccess = false, errorMessage = "Sync failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    suspend fun performFirstBackup(spreadsheetId: String): SyncResult = withContext(Dispatchers.IO) {
        try {
            var uploadedCount = 0

            // 1. Accounts
            val accounts = database.accountDao().getAllIncludingInactive()
            val accountRows = accounts.map { SheetsDataMapper.accountToRow(it) }
            sheetsRepository.appendRows(spreadsheetId, "Accounts", accountRows)
            uploadedCount += accounts.size

            // 2. Categories
            val categories = database.categoryDao().getAll()
            val categoryRows = categories.map { SheetsDataMapper.categoryToRow(it) }
            sheetsRepository.appendRows(spreadsheetId, "Categories", categoryRows)

            // 3. Tags
            val tags = database.tagDao().getAll()
            val tagRows = tags.map { SheetsDataMapper.tagToRow(it) }
            sheetsRepository.appendRows(spreadsheetId, "Tags", tagRows)

            // 4. Subscriptions
            val subscriptions = database.subscriptionDao().getAllActive()
            val subRows = subscriptions.map { SheetsDataMapper.subscriptionToRow(it) }
            sheetsRepository.appendRows(spreadsheetId, "Subscriptions", subRows)

            // 5. Budgets
            val budgets = database.budgetDao().getAllActive()
            val budgetRows = budgets.map { SheetsDataMapper.budgetToRow(it) }
            sheetsRepository.appendRows(spreadsheetId, "Budgets", budgetRows)

            // 6. Goals
            val goals = database.goalDao().getAllActive()
            val goalRows = goals.map { SheetsDataMapper.goalToRow(it) }
            sheetsRepository.appendRows(spreadsheetId, "Goals", goalRows)

            // 7. Rules
            val rules = database.categorizationRuleDao().getAllActive()
            val ruleRows = rules.map { SheetsDataMapper.ruleToRow(it) }
            sheetsRepository.appendRows(spreadsheetId, "Rules", ruleRows)

            // 8. Transactions
            val transactions = database.transactionDao().getAll()
            val txRows = transactions.map { SheetsDataMapper.transactionToRow(it) }
            sheetsRepository.appendRows(spreadsheetId, "Transactions", txRows)
            uploadedCount += transactions.size

            // 9. Captured Inbox
            val capturedList = database.capturedTransactionDao().getPendingCaptures()
            val capturedRows = capturedList.map { SheetsDataMapper.capturedToRow(it) }
            sheetsRepository.appendRows(spreadsheetId, "CapturedInbox", capturedRows)

            // Drain local sync logs
            database.syncLogDao().deleteAll()

            SyncResult(isSuccess = true, uploadedCount = uploadedCount, downloadedCount = 0)
        } catch (e: Exception) {
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

            SyncResult(isSuccess = true, uploadedCount = 0, downloadedCount = downloadedCount)
        } catch (e: Exception) {
            SyncResult(isSuccess = false, errorMessage = "Restore error: ${e.localizedMessage}")
        }
    }

    suspend fun performIncrementalSync(spreadsheetId: String, cloudData: Map<String, List<List<String>>>): SyncResult = withContext(Dispatchers.IO) {
        try {
            var uploadedCount = 0
            var downloadedCount = 0

            // 1. Upload local changes queued in sync_logs
            val logs = database.syncLogDao().getAll()
            if (logs.isNotEmpty()) {
                val processedLogIds = mutableListOf<Long>()

                for (log in logs) {
                    when (log.entityType) {
                        "TRANSACTION" -> {
                            val tx = database.transactionDao().getById(log.entityId)
                            if (tx != null && log.action != "DELETE") {
                                val row = SheetsDataMapper.transactionToRow(tx, isDeleted = false, updatedAtMs = log.timestampMs)
                                sheetsRepository.appendRows(spreadsheetId, "Transactions", listOf(row))
                                uploadedCount++
                            }
                            processedLogIds.add(log.id)
                        }
                        "ACCOUNT" -> {
                            val acc = database.accountDao().getById(log.entityId)
                            if (acc != null && log.action != "DELETE") {
                                val row = SheetsDataMapper.accountToRow(acc, isDeleted = false, updatedAtMs = log.timestampMs)
                                sheetsRepository.appendRows(spreadsheetId, "Accounts", listOf(row))
                            }
                            processedLogIds.add(log.id)
                        }
                        else -> {
                            processedLogIds.add(log.id)
                        }
                    }
                }

                if (processedLogIds.isNotEmpty()) {
                    database.syncLogDao().deleteByIds(processedLogIds)
                }
            }

            // 2. Download cloud updates
            repository.withSyncSuppressed {
                val cloudTxRows = cloudData["Transactions"] ?: emptyList()
                val existingLocalTxs = database.transactionDao().getAll().associateBy { it.id }

                cloudTxRows.forEach { row ->
                    val cloudTx = SheetsDataMapper.rowToTransaction(row)
                    if (cloudTx != null) {
                        val localTx = existingLocalTxs[cloudTx.id]
                        if (localTx == null) {
                            database.transactionDao().insert(cloudTx)
                            downloadedCount++
                        }
                    }
                }
            }

            SyncResult(isSuccess = true, uploadedCount = uploadedCount, downloadedCount = downloadedCount)
        } catch (e: Exception) {
            SyncResult(isSuccess = false, errorMessage = "Incremental sync error: ${e.localizedMessage}")
        }
    }
}
