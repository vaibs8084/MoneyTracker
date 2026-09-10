package com.vaibhav.moneytracker

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction
import com.vaibhav.moneytracker.capture.CapturedTransactionEntity
import com.vaibhav.moneytracker.cloud.SyncLogEntity
import kotlinx.coroutines.flow.flow
import java.util.Calendar

class MoneyRepository(private val db: MoneyTrackerDatabase) {

    fun getTransactions(): Flow<List<TransactionUiModel>> = 
        db.transactionDao().getAllWithTagsFlow().map { list ->
            list.map { it.transaction.toUiModel(it.tags) }
        }

    fun getAccounts(): Flow<List<AccountEntity>> = db.accountDao().getAllFlow()

    fun getAllAccounts(): Flow<List<AccountEntity>> = db.accountDao().getAllIncludingInactiveFlow()

    suspend fun getAccountBalance(account: AccountEntity): Long {
        return FinancialEngine.calculateAccountBalance(db, account)
    }

    @Volatile
    var isSyncSuppressed: Boolean = false

    suspend fun <T> withSyncSuppressed(block: suspend () -> T): T {
        isSyncSuppressed = true
        return try {
            block()
        } finally {
            isSyncSuppressed = false
        }
    }

    suspend fun <T> executeInTransaction(block: suspend () -> T): T {
        return db.withTransaction {
            block()
        }
    }

    private suspend fun logSync(entityType: String, entityId: Long, action: String, secondaryId: Long? = null) {
        if (isSyncSuppressed) return
        db.syncLogDao().insertOrCoalesce(
            SyncLogEntity(
                entityType = entityType,
                entityId = entityId,
                secondaryId = secondaryId,
                action = action,
                timestampMs = System.currentTimeMillis()
            )
        )
    }

    // Captured Transactions (Capture Inbox)
    fun getPendingCapturesFlow(): Flow<List<CapturedTransactionEntity>> =
        db.capturedTransactionDao().getPendingCapturesFlow()

    fun getPendingCaptureCountFlow(): Flow<Int> =
        db.capturedTransactionDao().getPendingCountFlow()

    suspend fun approveCapturedTransaction(captured: CapturedTransactionEntity) {
        db.withTransaction {
            val account = captured.suggestedAccountId?.let { db.accountDao().getById(it) }
                ?: db.accountDao().getAllIncludingInactive().firstOrNull()
                ?: error("An account is required to approve a transaction")

            val category = captured.suggestedCategoryId?.let { id ->
                db.categoryDao().getAll().firstOrNull { it.id == id }
            }

            val newTxId = db.transactionDao().insert(
                TransactionEntity(
                    title = captured.title,
                    category = category?.name ?: "General",
                    account = account.name,
                    type = captured.type,
                    amountPaise = captured.amountPaise,
                    note = "Captured from ${captured.sourceApp}",
                    createdAt = captured.createdAt,
                    accountId = account.id,
                    categoryId = captured.suggestedCategoryId
                )
            )

            db.capturedTransactionDao().updateStatus(captured.id, "APPROVED")
            logSync("TRANSACTION", newTxId, "CREATE")
            logSync("CAPTURED", captured.id, "UPDATE")
        }
    }

    suspend fun rejectCapturedTransaction(captured: CapturedTransactionEntity) {
        db.withTransaction {
            db.capturedTransactionDao().updateStatus(captured.id, "REJECTED")
            logSync("CAPTURED", captured.id, "UPDATE")
        }
    }

    suspend fun updateCapturedTransaction(captured: CapturedTransactionEntity) {
        db.withTransaction {
            db.capturedTransactionDao().update(captured)
            logSync("CAPTURED", captured.id, "UPDATE")
        }
    }

    suspend fun getTransactionsInRange(accountId: Long?, startDate: Long, endDate: Long): List<TransactionEntity> {
        return if (accountId != null && accountId > 0L) {
            db.transactionDao().getTransactionsInRange(accountId, startDate, endDate)
        } else {
            db.transactionDao().getTransactionsInDateRange(startDate, endDate)
        }
    }

    fun getCategories(): Flow<List<CategoryEntity>> = db.categoryDao().getAllActiveFlow()

    fun getTags(): Flow<List<TagEntity>> = db.tagDao().getAllActiveFlow()

    fun getSubscriptions(): Flow<List<SubscriptionEntity>> = db.subscriptionDao().getConfirmedSubscriptionsFlow()

    fun getBudgets(): Flow<List<BudgetEntity>> = db.budgetDao().getAllActiveFlow()

    fun getGoals(): Flow<List<GoalEntity>> = db.goalDao().getAllActiveFlow()

    fun getCompletedGoals(): Flow<List<GoalEntity>> = flow {
        emit(db.goalDao().getCompleted())
    }

    fun getSmartRules(): Flow<List<RuleWithTags>> = db.categorizationRuleDao().getAllActiveWithTagsFlow()

    suspend fun insertTransaction(transaction: TransactionEntity, tags: List<TagEntity>): Long {
        return db.withTransaction {
            val id = db.transactionDao().insert(transaction)
            tags.distinctBy { it.id }.forEach { tag ->
                db.transactionDao().insertTagRef(TransactionTagCrossRef(id, tag.id))
                logSync("TRANSACTION_TAG", tag.id, "CREATE", secondaryId = id)
            }
            logSync("TRANSACTION", id, "CREATE")
            id
        }
    }

    suspend fun insertTransactionChecked(
        transaction: TransactionEntity,
        tags: List<TagEntity>
    ): InsertTransactionResult = db.withTransaction {
        val accountId = transaction.accountId
        val existing = if (accountId != null) {
            val dayStart = calendarDayStart(transaction.createdAt)
            val dayEnd = dayStart + 24L * 60 * 60 * 1_000 - 1L
            db.transactionDao().findPotentialDuplicateOnDay(
                title = transaction.title,
                amountPaise = transaction.amountPaise,
                accountId = accountId,
                accountName = transaction.account,
                dayStart = dayStart,
                dayEnd = dayEnd
            )
        } else {
            null
        }
        if (existing != null) {
            InsertTransactionResult.DuplicateDetected(existing)
        } else {
            val id = db.transactionDao().insert(transaction)
            tags.distinctBy { it.id }.forEach { tag ->
                db.transactionDao().insertTagRef(TransactionTagCrossRef(id, tag.id))
                logSync("TRANSACTION_TAG", tag.id, "CREATE", secondaryId = id)
            }
            logSync("TRANSACTION", id, "CREATE")
            InsertTransactionResult.Inserted(id)
        }
    }

    suspend fun updateTransaction(transaction: TransactionEntity, tags: List<TagEntity>) {
        db.withTransaction {
            db.transactionDao().update(transaction)
            db.transactionDao().deleteTagsForTransaction(transaction.id)
            tags.distinctBy { it.id }.forEach { tag ->
                db.transactionDao().insertTagRef(TransactionTagCrossRef(transaction.id, tag.id))
                logSync("TRANSACTION_TAG", tag.id, "CREATE", secondaryId = transaction.id)
            }
            logSync("TRANSACTION", transaction.id, "UPDATE")
        }
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        db.withTransaction {
            db.transactionDao().deleteTagsForTransaction(transaction.id)
            db.transactionDao().delete(transaction)
            logSync("TRANSACTION", transaction.id, "DELETE")
        }
    }

    suspend fun findPotentialDuplicate(transaction: TransactionEntity): TransactionEntity? {
        val accountId = transaction.accountId ?: return null
        val dayStart = calendarDayStart(transaction.createdAt)
        val dayEnd = dayStart + 24L * 60 * 60 * 1_000 - 1L
        return db.transactionDao().findPotentialDuplicateOnDay(
            title = transaction.title,
            amountPaise = transaction.amountPaise,
            accountId = accountId,
            accountName = transaction.account,
            dayStart = dayStart,
            dayEnd = dayEnd
        )
    }

    private fun calendarDayStart(timestampMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestampMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    suspend fun deleteTransactions(ids: List<Long>) {
        if (ids.isEmpty()) return
        db.withTransaction {
            db.transactionDao().deleteTagsForTransactions(ids)
            db.transactionDao().deleteByIds(ids)
            ids.forEach { id ->
                logSync("TRANSACTION", id, "DELETE")
            }
        }
    }

    suspend fun insertAccount(account: AccountEntity): Long {
        return db.withTransaction {
            val id = db.accountDao().insert(account)
            logSync("ACCOUNT", id, "CREATE")
            id
        }
    }

    suspend fun updateAccount(account: AccountEntity) {
        db.withTransaction {
            val previous = db.accountDao().getById(account.id)
            db.accountDao().update(account)
            if (previous != null && previous.name != account.name) {
                db.transactionDao().renameLegacyAccountName(previous.name, account.name)
            }
            logSync("ACCOUNT", account.id, "UPDATE")
        }
    }

    suspend fun deactivateAccount(id: Long) {
        db.withTransaction {
            db.accountDao().deactivate(id)
            logSync("ACCOUNT", id, "UPDATE")
        }
    }

    suspend fun insertCategory(category: CategoryEntity): Long {
        return db.withTransaction {
            val id = db.categoryDao().insert(category)
            logSync("CATEGORY", id, "CREATE")
            id
        }
    }

    suspend fun updateCategory(category: CategoryEntity) {
        db.withTransaction {
            db.categoryDao().update(category)
            logSync("CATEGORY", category.id, "UPDATE")
        }
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        db.withTransaction {
            db.categoryDao().delete(category)
            logSync("CATEGORY", category.id, "DELETE")
        }
    }

    suspend fun insertTag(tag: TagEntity): Long {
        return db.withTransaction {
            val id = db.tagDao().insert(tag)
            logSync("TAG", id, "CREATE")
            id
        }
    }

    suspend fun updateTag(tag: TagEntity) {
        db.withTransaction {
            db.tagDao().update(tag)
            logSync("TAG", tag.id, "UPDATE")
        }
    }

    suspend fun deleteTag(tag: TagEntity) {
        db.withTransaction {
            db.tagDao().delete(tag)
            logSync("TAG", tag.id, "DELETE")
        }
    }

    suspend fun insertBudget(budget: BudgetEntity): Long {
        return db.withTransaction {
            val id = db.budgetDao().insert(budget)
            logSync("BUDGET", id, "CREATE")
            id
        }
    }

    suspend fun updateBudget(budget: BudgetEntity) {
        db.withTransaction {
            db.budgetDao().update(budget)
            logSync("BUDGET", budget.id, "UPDATE")
        }
    }

    suspend fun deleteBudget(budget: BudgetEntity) {
        db.withTransaction {
            db.budgetDao().delete(budget)
            logSync("BUDGET", budget.id, "DELETE")
        }
    }

    suspend fun insertGoal(goal: GoalEntity): Long {
        return db.withTransaction {
            val id = db.goalDao().insert(goal)
            logSync("GOAL", id, "CREATE")
            id
        }
    }

    suspend fun updateGoal(goal: GoalEntity) {
        db.withTransaction {
            db.goalDao().update(goal)
            logSync("GOAL", goal.id, "UPDATE")
        }
    }

    suspend fun deleteGoal(goal: GoalEntity) {
        db.withTransaction {
            db.goalDao().delete(goal)
            logSync("GOAL", goal.id, "DELETE")
        }
    }

    suspend fun insertSubscription(subscription: SubscriptionEntity): Long {
        return db.withTransaction {
            val id = db.subscriptionDao().insert(subscription.copy(isConfirmed = true))
            logSync("SUBSCRIPTION", id, "CREATE")
            id
        }
    }

    suspend fun confirmSubscription(suggestion: SubscriptionEntity): Long {
        return db.withTransaction {
            val id = db.subscriptionDao().insert(suggestion.copy(id = 0, isConfirmed = true, isActive = true))
            logSync("SUBSCRIPTION", id, "CREATE")
            id
        }
    }

    suspend fun updateSubscription(subscription: SubscriptionEntity) {
        db.withTransaction {
            db.subscriptionDao().update(subscription)
            logSync("SUBSCRIPTION", subscription.id, "UPDATE")
        }
    }

    suspend fun deactivateSubscription(subscription: SubscriptionEntity) {
        db.withTransaction {
            db.subscriptionDao().update(subscription.copy(isActive = false))
            logSync("SUBSCRIPTION", subscription.id, "UPDATE")
        }
    }

    suspend fun deleteSubscription(subscription: SubscriptionEntity) {
        db.withTransaction {
            db.subscriptionDao().delete(subscription)
            logSync("SUBSCRIPTION", subscription.id, "DELETE")
        }
    }

    /** Records one real payment and advances its schedule atomically. */
    suspend fun recordSubscriptionPayment(subscription: SubscriptionEntity): Long {
        require(subscription.isConfirmed && subscription.isActive)
        return db.withTransaction {
            val account = subscription.accountId?.let { db.accountDao().getById(it) }
                ?: error("A subscription payment requires an existing account")
            val category = subscription.categoryId?.let { id ->
                db.categoryDao().getAll().firstOrNull { it.id == id }
            }
            val transactionId = db.transactionDao().insert(
                TransactionEntity(
                    title = subscription.name,
                    category = category?.name ?: "Subscription",
                    account = account.name,
                    type = "Expense",
                    amountPaise = subscription.amountPaise,
                    note = "Subscription payment",
                    createdAt = System.currentTimeMillis(),
                    accountId = account.id,
                    categoryId = subscription.categoryId
                )
            )
            db.subscriptionDao().update(
                subscription.copy(
                    nextDate = IntelligenceEngine.advanceSubscriptionDate(
                        subscription.nextDate,
                        subscription.cadence
                    )
                )
            )
            logSync("TRANSACTION", transactionId, "CREATE")
            logSync("SUBSCRIPTION", subscription.id, "UPDATE")
            transactionId
        }
    }

    suspend fun insertSmartRule(rule: CategorizationRuleEntity, tagIds: List<Long>): Long {
        return db.withTransaction {
            val ruleId = db.categorizationRuleDao().insert(rule)
            tagIds.distinct().forEach { tagId ->
                db.categorizationRuleDao().insertTagRef(RuleTagCrossRef(ruleId, tagId))
                logSync("RULE_TAG", tagId, "CREATE", secondaryId = ruleId)
            }
            logSync("RULE", ruleId, "CREATE")
            ruleId
        }
    }

    suspend fun updateRule(rule: CategorizationRuleEntity, tagIds: List<Long>) {
        db.withTransaction {
            db.categorizationRuleDao().update(rule)
            db.categorizationRuleDao().deleteTagsForRule(rule.id)
            tagIds.distinct().forEach { tagId ->
                db.categorizationRuleDao().insertTagRef(RuleTagCrossRef(rule.id, tagId))
                logSync("RULE_TAG", tagId, "CREATE", secondaryId = rule.id)
            }
            logSync("RULE", rule.id, "UPDATE")
        }
    }

    suspend fun deleteRule(rule: CategorizationRuleEntity) {
        db.withTransaction {
            db.categorizationRuleDao().deleteTagsForRule(rule.id)
            db.categorizationRuleDao().delete(rule)
            logSync("RULE", rule.id, "DELETE")
        }
    }

    suspend fun resetLocalDatabase() {
        db.withTransaction {
            db.transactionDao().deleteAll()
            db.capturedTransactionDao().deleteAll()
            db.subscriptionDao().deleteAll()
            db.budgetDao().deleteAll()
            db.goalDao().deleteAll()
            db.categorizationRuleDao().deleteAllTagRefs()
            db.categorizationRuleDao().deleteAll()
            db.tagDao().deleteAll()
            db.accountDao().deleteAll()
            db.categoryDao().deleteAll()
            db.syncLogDao().deleteAll()
        }
        ensureDefaultAccounts(db)
        ensureCategories(db)
    }

    /**
     * Imports a batch of confirmed candidate transactions atomically.
     * All transactions in [items] confirmed by the user are inserted in a single Room transaction.
     * Legitimate repeated transactions on the same date are preserved.
     */
    suspend fun bulkImportTransactions(
        items: List<Pair<TransactionEntity, List<TagEntity>>>
    ): ImportResult {
        return db.withTransaction {
            var importedCount = 0
            val existing = db.transactionDao().getAll()

            val calendar = Calendar.getInstance()
            fun getDayStart(timestamp: Long): Long {
                calendar.timeInMillis = timestamp
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                return calendar.timeInMillis
            }

            var duplicateCount = 0
            for ((transaction, tags) in items) {
                val hasMatchInDb = existing.any { ex ->
                    ex.title == transaction.title &&
                    ex.amountPaise == transaction.amountPaise &&
                    ex.accountId == transaction.accountId &&
                    getDayStart(ex.createdAt) == getDayStart(transaction.createdAt)
                }
                if (hasMatchInDb) {
                    duplicateCount++
                }

                val id = db.transactionDao().insert(transaction)
                tags.distinctBy { it.id }.forEach { tag ->
                    db.transactionDao().insertTagRef(TransactionTagCrossRef(id, tag.id))
                    logSync("TRANSACTION_TAG", tag.id, "CREATE", secondaryId = id)
                }
                logSync("TRANSACTION", id, "CREATE")
                importedCount++
            }

            ImportResult(importedCount, duplicateCount)
        }
    }

    data class ImportResult(val imported: Int, val duplicates: Int)

    /** Result of [insertTransactionChecked]. */
    sealed class InsertTransactionResult {
        data class Inserted(val id: Long) : InsertTransactionResult()
        data class DuplicateDetected(val existing: TransactionEntity) : InsertTransactionResult()
    }
}
