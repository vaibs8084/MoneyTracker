package com.vaibhav.moneytracker

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction
import com.vaibhav.moneytracker.capture.CapturedTransactionEntity

class MoneyRepository(private val db: MoneyTrackerDatabase) {

    fun getTransactions(): Flow<List<TransactionUiModel>> = 
        db.transactionDao().getAllWithTagsFlow().map { list ->
            list.map { it.transaction.toUiModel(it.tags) }
        }

    fun getAccounts(): Flow<List<AccountEntity>> = db.accountDao().getAllFlow()

    fun getAllAccounts(): Flow<List<AccountEntity>> = db.accountDao().getAllIncludingInactiveFlow()

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

            db.transactionDao().insert(
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
        }
    }

    suspend fun rejectCapturedTransaction(captured: CapturedTransactionEntity) {
        db.capturedTransactionDao().updateStatus(captured.id, "REJECTED")
    }

    suspend fun updateCapturedTransaction(captured: CapturedTransactionEntity) {
        db.capturedTransactionDao().update(captured)
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

    fun getSmartRules(): Flow<List<RuleWithTags>> = db.categorizationRuleDao().getAllActiveWithTagsFlow()

    suspend fun insertTransaction(transaction: TransactionEntity, tags: List<TagEntity>): Long {
        return db.withTransaction {
            val id = db.transactionDao().insert(transaction)
            tags.distinctBy { it.id }.forEach { tag ->
                db.transactionDao().insertTagRef(TransactionTagCrossRef(id, tag.id))
            }
            id
        }
    }

    /**
     * Atomically checks for a potential duplicate and, if none exists, inserts
     * the transaction — all within a single SQLite write transaction.
     *
     * Room's [db.withTransaction] acquires the database write lock for the entire
     * block. Concurrent callers are serialised by SQLite: the second caller's
     * duplicate-check read sees the first caller's committed row, preventing a
     * double-insert race condition.
     *
     * Returns [InsertTransactionResult.Inserted] on success, or
     * [InsertTransactionResult.DuplicateDetected] when a matching transaction
     * already exists on the same calendar day.
     *
     * Transfer transactions (where [TransactionEntity.accountId] is null) bypass
     * the duplicate check and are always inserted.
     */
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
            }
            InsertTransactionResult.Inserted(id)
        }
    }

    suspend fun updateTransaction(transaction: TransactionEntity, tags: List<TagEntity>) {
        db.withTransaction {
            db.transactionDao().update(transaction)
            db.transactionDao().deleteTagsForTransaction(transaction.id)
            tags.distinctBy { it.id }.forEach { tag ->
                db.transactionDao().insertTagRef(TransactionTagCrossRef(transaction.id, tag.id))
            }
        }
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        db.transactionDao().delete(transaction)
    }

    /**
     * Checks whether a near-identical transaction already exists for the same
     * calendar day as [transaction].
     *
     * Matching criteria: same title, same amountPaise, same account (by accountId
     * FK or by legacy account name), recorded on the same calendar day.
     *
     * Returns the matching [TransactionEntity] when a potential duplicate is found,
     * or null when no match exists.
     *
     * Transfer transactions are excluded because their accountId is always null;
     * the from/to accounts are tracked separately for them.
     */
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
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestampMs
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    suspend fun deleteTransactions(ids: List<Long>) {
        if (ids.isEmpty()) return
        db.withTransaction {
            db.transactionDao().deleteTagsForTransactions(ids)
            db.transactionDao().deleteByIds(ids)
        }
    }

    suspend fun insertAccount(account: AccountEntity) = db.accountDao().insert(account)
    suspend fun updateAccount(account: AccountEntity) {
        db.withTransaction {
            val previous = db.accountDao().getById(account.id)
            db.accountDao().update(account)
            if (previous != null && previous.name != account.name) {
                db.transactionDao().renameLegacyAccountName(previous.name, account.name)
            }
        }
    }
    suspend fun deactivateAccount(id: Long) = db.accountDao().deactivate(id)

    suspend fun insertCategory(category: CategoryEntity) = db.categoryDao().insert(category)
    suspend fun updateCategory(category: CategoryEntity) = db.categoryDao().update(category)

    suspend fun insertTag(tag: TagEntity) = db.tagDao().insert(tag)
    suspend fun updateTag(tag: TagEntity) = db.tagDao().update(tag)

    suspend fun insertBudget(budget: BudgetEntity) = db.budgetDao().insert(budget)
    suspend fun updateBudget(budget: BudgetEntity) = db.budgetDao().update(budget)

    suspend fun insertGoal(goal: GoalEntity) = db.goalDao().insert(goal)
    suspend fun updateGoal(goal: GoalEntity) = db.goalDao().update(goal)

    suspend fun confirmSubscription(suggestion: SubscriptionEntity): Long =
        db.subscriptionDao().insert(suggestion.copy(id = 0, isConfirmed = true, isActive = true))

    suspend fun updateSubscription(subscription: SubscriptionEntity) = db.subscriptionDao().update(subscription)

    suspend fun deactivateSubscription(subscription: SubscriptionEntity) {
        db.subscriptionDao().update(subscription.copy(isActive = false))
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
            transactionId
        }
    }

    suspend fun insertSmartRule(rule: CategorizationRuleEntity, tagIds: List<Long>): Long {
        return db.withTransaction {
            val ruleId = db.categorizationRuleDao().insert(rule)
            tagIds.distinct().forEach { tagId ->
                db.categorizationRuleDao().insertTagRef(RuleTagCrossRef(ruleId, tagId))
            }
            ruleId
        }
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

            val calendar = java.util.Calendar.getInstance()
            fun getDayStart(timestamp: Long): Long {
                calendar.timeInMillis = timestamp
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
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
                }
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
