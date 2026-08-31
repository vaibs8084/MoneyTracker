package com.vaibhav.moneytracker

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction

class MoneyRepository(private val db: MoneyTrackerDatabase) {

    fun getTransactions(): Flow<List<TransactionUiModel>> = 
        db.transactionDao().getAllWithTagsFlow().map { list ->
            list.map { it.transaction.toUiModel(it.tags) }
        }

    fun getAccounts(): Flow<List<AccountEntity>> = db.accountDao().getAllFlow()

    fun getAllAccounts(): Flow<List<AccountEntity>> = db.accountDao().getAllIncludingInactiveFlow()

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
}
