package com.vaibhav.moneytracker

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
        val id = db.transactionDao().insert(transaction)
        tags.forEach { tag ->
            db.transactionDao().insertTagRef(TransactionTagCrossRef(id, tag.id))
        }
        return id
    }

    suspend fun updateTransaction(transaction: TransactionEntity, tags: List<TagEntity>) {
        db.transactionDao().update(transaction)
        db.transactionDao().deleteTagsForTransaction(transaction.id)
        tags.forEach { tag ->
            db.transactionDao().insertTagRef(TransactionTagCrossRef(transaction.id, tag.id))
        }
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        db.transactionDao().delete(transaction)
    }

    suspend fun insertAccount(account: AccountEntity) = db.accountDao().insert(account)
    suspend fun updateAccount(account: AccountEntity) = db.accountDao().update(account)
    suspend fun deactivateAccount(id: Long) = db.accountDao().deactivate(id)

    suspend fun insertCategory(category: CategoryEntity) = db.categoryDao().insert(category)
    suspend fun updateCategory(category: CategoryEntity) = db.categoryDao().update(category)

    suspend fun insertTag(tag: TagEntity) = db.tagDao().insert(tag)
    suspend fun updateTag(tag: TagEntity) = db.tagDao().update(tag)

    suspend fun insertBudget(budget: BudgetEntity) = db.budgetDao().insert(budget)
    suspend fun updateBudget(budget: BudgetEntity) = db.budgetDao().update(budget)

    suspend fun insertGoal(goal: GoalEntity) = db.goalDao().insert(goal)
    suspend fun updateGoal(goal: GoalEntity) = db.goalDao().update(goal)

    suspend fun updateSubscription(subscription: SubscriptionEntity) = db.subscriptionDao().update(subscription)

    suspend fun insertSmartRule(rule: CategorizationRuleEntity, tagIds: List<Long>): Long {
        val ruleId = db.categorizationRuleDao().insert(rule)
        tagIds.forEach { tagId ->
            db.categorizationRuleDao().insertTagRef(RuleTagCrossRef(ruleId, tagId))
        }
        return ruleId
    }
}
