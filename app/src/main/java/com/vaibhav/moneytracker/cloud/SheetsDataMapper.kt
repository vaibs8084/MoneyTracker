package com.vaibhav.moneytracker.cloud

import com.vaibhav.moneytracker.*
import com.vaibhav.moneytracker.capture.CapturedTransactionEntity

object SheetsDataMapper {

    // Helper functions for safe type conversion
    fun parseLong(value: String?, default: Long = 0L): Long {
        if (value.isNullOrBlank()) return default
        return value.trim().toLongOrNull() ?: default
    }

    fun parseNullableLong(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return value.trim().toLongOrNull()
    }

    fun parseBoolean(value: String?, default: Boolean = false): Boolean {
        if (value.isNullOrBlank()) return default
        return value.trim().equals("TRUE", ignoreCase = true) || value.trim() == "1"
    }

    fun formatBoolean(value: Boolean): String = if (value) "TRUE" else "FALSE"

    fun formatNullable(value: Any?): String = value?.toString() ?: ""

    // Transactions Mapping
    fun transactionToRow(tx: TransactionEntity, isDeleted: Boolean = false, updatedAtMs: Long = System.currentTimeMillis()): List<Any> {
        return listOf(
            tx.id.toString(),
            tx.title,
            tx.category,
            tx.account,
            tx.type,
            tx.amountPaise.toString(),
            tx.note,
            tx.createdAt.toString(),
            formatNullable(tx.accountId),
            formatNullable(tx.categoryId),
            formatNullable(tx.externalMoneyKind),
            formatBoolean(isDeleted),
            updatedAtMs.toString()
        )
    }

    fun rowToTransaction(row: List<String>): TransactionEntity? {
        if (row.size < 8) return null
        val id = parseLong(row.getOrNull(0))
        if (id <= 0L) return null

        return TransactionEntity(
            id = id,
            title = row.getOrNull(1)?.trim() ?: "Imported Transaction",
            category = row.getOrNull(2)?.trim() ?: "General",
            account = row.getOrNull(3)?.trim() ?: "Main",
            type = row.getOrNull(4)?.trim() ?: "Expense",
            amountPaise = parseLong(row.getOrNull(5)),
            note = row.getOrNull(6) ?: "",
            createdAt = parseLong(row.getOrNull(7)),
            accountId = parseNullableLong(row.getOrNull(8)),
            categoryId = parseNullableLong(row.getOrNull(9)),
            externalMoneyKind = row.getOrNull(10)?.takeIf { it.isNotBlank() }
        )
    }

    // Accounts Mapping
    fun accountToRow(acc: AccountEntity, isDeleted: Boolean = false, updatedAtMs: Long = System.currentTimeMillis()): List<Any> {
        return listOf(
            acc.id.toString(),
            acc.name,
            acc.type,
            acc.openingBalancePaise.toString(),
            formatBoolean(acc.isActive),
            acc.createdAt.toString(),
            formatBoolean(isDeleted),
            updatedAtMs.toString()
        )
    }

    fun rowToAccount(row: List<String>): AccountEntity? {
        if (row.size < 6) return null
        val id = parseLong(row.getOrNull(0))
        if (id <= 0L) return null

        return AccountEntity(
            id = id,
            name = row.getOrNull(1)?.trim() ?: "Main",
            type = row.getOrNull(2)?.trim() ?: "Cash",
            openingBalancePaise = parseLong(row.getOrNull(3)),
            isActive = parseBoolean(row.getOrNull(4), default = true),
            createdAt = parseLong(row.getOrNull(5))
        )
    }

    // Categories Mapping
    fun categoryToRow(cat: CategoryEntity, isDeleted: Boolean = false, updatedAtMs: Long = System.currentTimeMillis()): List<Any> {
        return listOf(
            cat.id.toString(),
            cat.name,
            formatNullable(cat.icon),
            formatNullable(cat.color),
            formatBoolean(cat.isActive),
            formatBoolean(isDeleted),
            updatedAtMs.toString()
        )
    }

    fun rowToCategory(row: List<String>): CategoryEntity? {
        if (row.size < 5) return null
        val id = parseLong(row.getOrNull(0))
        if (id <= 0L) return null

        return CategoryEntity(
            id = id,
            name = row.getOrNull(1)?.trim() ?: "General",
            icon = row.getOrNull(2)?.takeIf { it.isNotBlank() },
            color = row.getOrNull(3)?.toIntOrNull(),
            isActive = parseBoolean(row.getOrNull(4), default = true)
        )
    }

    // Tags Mapping
    fun tagToRow(tag: TagEntity, isDeleted: Boolean = false, updatedAtMs: Long = System.currentTimeMillis()): List<Any> {
        return listOf(
            tag.id.toString(),
            tag.name,
            formatBoolean(tag.isActive),
            formatBoolean(isDeleted),
            updatedAtMs.toString()
        )
    }

    fun rowToTag(row: List<String>): TagEntity? {
        if (row.size < 3) return null
        val id = parseLong(row.getOrNull(0))
        if (id <= 0L) return null

        return TagEntity(
            id = id,
            name = row.getOrNull(1)?.trim() ?: "Tag",
            isActive = parseBoolean(row.getOrNull(2), default = true)
        )
    }

    // TransactionTags CrossRef Mapping
    fun transactionTagToRow(crossRef: TransactionTagCrossRef): List<Any> {
        return listOf(
            crossRef.transactionId.toString(),
            crossRef.tagId.toString()
        )
    }

    fun rowToTransactionTag(row: List<String>): TransactionTagCrossRef? {
        if (row.size < 2) return null
        val txId = parseLong(row.getOrNull(0))
        val tagId = parseLong(row.getOrNull(1))
        if (txId <= 0L || tagId <= 0L) return null
        return TransactionTagCrossRef(txId, tagId)
    }

    // Subscriptions Mapping
    fun subscriptionToRow(sub: SubscriptionEntity, isDeleted: Boolean = false, updatedAtMs: Long = System.currentTimeMillis()): List<Any> {
        return listOf(
            sub.id.toString(),
            sub.name,
            sub.amountPaise.toString(),
            sub.cadence,
            sub.nextDate.toString(),
            formatNullable(sub.categoryId),
            formatNullable(sub.accountId),
            formatBoolean(sub.isConfirmed),
            formatBoolean(sub.isActive),
            formatBoolean(isDeleted),
            updatedAtMs.toString()
        )
    }

    fun rowToSubscription(row: List<String>): SubscriptionEntity? {
        if (row.size < 9) return null
        val id = parseLong(row.getOrNull(0))
        if (id <= 0L) return null

        return SubscriptionEntity(
            id = id,
            name = row.getOrNull(1)?.trim() ?: "Subscription",
            amountPaise = parseLong(row.getOrNull(2)),
            cadence = row.getOrNull(3)?.trim() ?: "MONTHLY",
            nextDate = parseLong(row.getOrNull(4)),
            categoryId = parseNullableLong(row.getOrNull(5)),
            accountId = parseNullableLong(row.getOrNull(6)),
            isConfirmed = parseBoolean(row.getOrNull(7), default = false),
            isActive = parseBoolean(row.getOrNull(8), default = true)
        )
    }

    // Budgets Mapping
    fun budgetToRow(budget: BudgetEntity, isDeleted: Boolean = false, updatedAtMs: Long = System.currentTimeMillis()): List<Any> {
        return listOf(
            budget.id.toString(),
            budget.categoryId.toString(),
            budget.limitPaise.toString(),
            budget.period,
            formatBoolean(budget.isActive),
            formatBoolean(isDeleted),
            updatedAtMs.toString()
        )
    }

    fun rowToBudget(row: List<String>): BudgetEntity? {
        if (row.size < 5) return null
        val id = parseLong(row.getOrNull(0))
        val catId = parseLong(row.getOrNull(1))
        if (id <= 0L || catId <= 0L) return null

        return BudgetEntity(
            id = id,
            categoryId = catId,
            limitPaise = parseLong(row.getOrNull(2)),
            period = row.getOrNull(3)?.trim() ?: "MONTHLY",
            isActive = parseBoolean(row.getOrNull(4), default = true)
        )
    }

    // Goals Mapping
    fun goalToRow(goal: GoalEntity, isDeleted: Boolean = false, updatedAtMs: Long = System.currentTimeMillis()): List<Any> {
        return listOf(
            goal.id.toString(),
            goal.name,
            goal.targetPaise.toString(),
            goal.manualProgressPaise.toString(),
            formatNullable(goal.targetDate),
            formatNullable(goal.linkedAccountId),
            formatBoolean(goal.isCompleted),
            formatBoolean(goal.isActive),
            formatBoolean(isDeleted),
            updatedAtMs.toString()
        )
    }

    fun rowToGoal(row: List<String>): GoalEntity? {
        if (row.size < 8) return null
        val id = parseLong(row.getOrNull(0))
        if (id <= 0L) return null

        return GoalEntity(
            id = id,
            name = row.getOrNull(1)?.trim() ?: "Goal",
            targetPaise = parseLong(row.getOrNull(2)),
            manualProgressPaise = parseLong(row.getOrNull(3)),
            targetDate = parseNullableLong(row.getOrNull(4)),
            linkedAccountId = parseNullableLong(row.getOrNull(5)),
            isCompleted = parseBoolean(row.getOrNull(6), default = false),
            isActive = parseBoolean(row.getOrNull(7), default = true)
        )
    }

    // Categorization Rules Mapping
    fun ruleToRow(rule: CategorizationRuleEntity, isDeleted: Boolean = false, updatedAtMs: Long = System.currentTimeMillis()): List<Any> {
        return listOf(
            rule.id.toString(),
            rule.titlePattern,
            formatNullable(rule.targetCategoryId),
            formatBoolean(rule.isActive),
            formatBoolean(isDeleted),
            updatedAtMs.toString()
        )
    }

    fun rowToRule(row: List<String>): CategorizationRuleEntity? {
        if (row.size < 4) return null
        val id = parseLong(row.getOrNull(0))
        if (id <= 0L) return null

        return CategorizationRuleEntity(
            id = id,
            titlePattern = row.getOrNull(1)?.trim() ?: "",
            targetCategoryId = parseNullableLong(row.getOrNull(2)),
            isActive = parseBoolean(row.getOrNull(3), default = true)
        )
    }

    // RuleTags CrossRef Mapping
    fun ruleTagToRow(crossRef: RuleTagCrossRef): List<Any> {
        return listOf(
            crossRef.ruleId.toString(),
            crossRef.tagId.toString()
        )
    }

    fun rowToRuleTag(row: List<String>): RuleTagCrossRef? {
        if (row.size < 2) return null
        val ruleId = parseLong(row.getOrNull(0))
        val tagId = parseLong(row.getOrNull(1))
        if (ruleId <= 0L || tagId <= 0L) return null
        return RuleTagCrossRef(ruleId, tagId)
    }

    // CapturedInbox Mapping
    fun capturedToRow(captured: CapturedTransactionEntity, isDeleted: Boolean = false, updatedAtMs: Long = System.currentTimeMillis()): List<Any> {
        return listOf(
            captured.id.toString(),
            captured.title,
            captured.amountPaise.toString(),
            captured.type,
            captured.createdAt.toString(),
            captured.sourceApp,
            formatNullable(captured.referenceNumber),
            formatNullable(captured.suggestedAccountId),
            formatNullable(captured.suggestedCategoryId),
            captured.status,
            formatBoolean(isDeleted),
            updatedAtMs.toString()
        )
    }

    fun rowToCaptured(row: List<String>): CapturedTransactionEntity? {
        if (row.size < 10) return null
        val id = parseLong(row.getOrNull(0))
        if (id <= 0L) return null

        return CapturedTransactionEntity(
            id = id,
            title = row.getOrNull(1)?.trim() ?: "Captured Transaction",
            amountPaise = parseLong(row.getOrNull(2)),
            type = row.getOrNull(3)?.trim() ?: "Expense",
            createdAt = parseLong(row.getOrNull(4)),
            sourceApp = row.getOrNull(5)?.trim() ?: "Bank App",
            referenceNumber = row.getOrNull(6)?.takeIf { it.isNotBlank() },
            suggestedAccountId = parseNullableLong(row.getOrNull(7)),
            suggestedCategoryId = parseNullableLong(row.getOrNull(8)),
            status = row.getOrNull(9)?.trim() ?: "PENDING"
        )
    }
}
