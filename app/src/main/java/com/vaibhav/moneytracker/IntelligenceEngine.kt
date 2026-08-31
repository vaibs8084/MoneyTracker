package com.vaibhav.moneytracker

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale

object IntelligenceEngine {

    fun calculateRecurringAveragePaise(transactions: List<TransactionEntity>): Long {
        require(transactions.isNotEmpty())
        return transactions.sumOf { it.amountPaise } / transactions.size.toLong()
    }

    /** UTC makes persisted schedule calculations independent of device timezone/DST. */
    fun advanceSubscriptionDate(nextDate: Long, cadence: String): Long {
        val dateTime = Instant.ofEpochMilli(nextDate).atZone(ZoneOffset.UTC)
        return when (cadence) {
            "WEEKLY" -> dateTime.plusWeeks(1)
            "MONTHLY" -> dateTime.plusMonths(1)
            "YEARLY" -> dateTime.plusYears(1)
            else -> error("Unsupported subscription cadence: $cadence")
        }.toInstant().toEpochMilli()
    }

    /* =====================================================
       RECURRING DETECTION
    ===================================================== */

    /**
     * Analyzes transaction history to find recurring patterns.
     * 
     * Strategy:
     * 1. Normalize title (trim, lowercase).
     * 2. Group by title.
     * 3. Min 3 occurrences.
     * 4. Check for monthly/weekly cadence.
     */
    suspend fun detectRecurring(
        transactions: List<TransactionEntity>,
        database: MoneyTrackerDatabase
    ): List<SubscriptionEntity> {
        
        val expenseTransactions = transactions.filter { it.type == "Expense" }
        if (expenseTransactions.size < 3) return emptyList()

        val suggestions = mutableListOf<SubscriptionEntity>()
        
        // Group by normalized title
        val groups = expenseTransactions.groupBy { 
            it.title.trim().lowercase(Locale.getDefault()) 
        }

        for ((normalizedTitle, group) in groups) {
            if (group.size < 3) continue

            val sorted = group.sortedBy { it.createdAt }
            
            // Check if this merchant is already a confirmed subscription
            val existing = database.subscriptionDao().getByName(group.first().title)
            if (existing != null) continue

            // Analyze cadence (Monthly is primary target)
            var isMonthly = true
            for (i in 1 until sorted.size) {
                val dayDiff = ChronoUnit.DAYS.between(
                    Instant.ofEpochMilli(sorted[i - 1].createdAt).atZone(ZoneOffset.UTC).toLocalDate(),
                    Instant.ofEpochMilli(sorted[i].createdAt).atZone(ZoneOffset.UTC).toLocalDate()
                )
                
                // Allow 27 to 33 days for monthly
                if (dayDiff < 27 || dayDiff > 33) {
                    isMonthly = false
                    break
                }
            }

            if (isMonthly) {
                // Average amount
                val avgAmount = calculateRecurringAveragePaise(sorted)
                
                // Prediction for next date
                suggestions.add(
                    SubscriptionEntity(
                        name = group.first().title, // Use original casing from first tx
                        amountPaise = avgAmount,
                        cadence = "MONTHLY",
                        nextDate = advanceSubscriptionDate(sorted.last().createdAt, "MONTHLY"),
                        categoryId = group.first().categoryId,
                        isConfirmed = false,
                        isActive = true
                    )
                )
            }
        }

        return suggestions
    }


    /* =====================================================
       RULE MATCHING
    ===================================================== */

    /**
     * Returns matching category and tags for a transaction title.
     */
    suspend fun matchRules(
        title: String,
        rules: List<RuleWithTags>
    ): Pair<Long?, List<TagEntity>> {
        val normalizedTitle = title.trim().lowercase(Locale.getDefault())
        
        val matchedRule = rules.find { 
            normalizedTitle.contains(it.rule.titlePattern.lowercase(Locale.getDefault())) 
        }

        return if (matchedRule != null) {
            matchedRule.rule.targetCategoryId to matchedRule.tags
        } else {
            null to emptyList()
        }
    }


    /* =====================================================
       BUDGET CALCULATIONS
    ===================================================== */

    fun calculateBudgetUsage(
        transactions: List<TransactionUiModel>,
        budget: BudgetEntity
    ): Long {
        val start = getMonthStartTimestamp()
        val end = getMonthEndTimestamp()

        return transactions
            .filter { 
                it.type == "Expense" && 
                it.categoryId == budget.categoryId &&
                it.createdAt in start..end
            }
            .sumOf { it.amountPaise }
    }


    /* =====================================================
       HELPERS
    ===================================================== */

    fun getMonthStartTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun getMonthEndTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    /* =====================================================
       INSIGHTS GENERATION
    ===================================================== */

    data class Insight(
        val icon: String,
        val text: String,
        val priority: Int = 0 // Higher is more important
    )

    fun generateInsights(
        transactions: List<TransactionUiModel>,
        period: DashboardPeriod
    ): List<Insight> {
        val insights = mutableListOf<Insight>()
        
        val start = getPeriodStartTimestamp(period)
        val end = getPeriodEndTimestamp(period)
        
        val periodTxs = transactions.filter { it.createdAt in start..end }
        val expenses = periodTxs.filter { it.type == "Expense" }
        val incomes = periodTxs.filter { it.type == "Income" }
        
        val totalExpense = expenses.sumOf { it.amountPaise }
        val totalIncome = incomes.sumOf { it.amountPaise }
        
        // 1. Savings Insight
        if (totalIncome > totalExpense && totalIncome > 0) {
            val savings = totalIncome - totalExpense
            insights.add(Insight("📈", "You've saved ${formatRupees(savings)} this period!", priority = 10))
        } else if (totalExpense > totalIncome && totalIncome > 0) {
            insights.add(Insight("⚠️", "Your spending exceeds your income by ${formatRupees(totalExpense - totalIncome)}.", priority = 10))
        }

        // 2. Top Category Insight
        val topCat = expenses.groupBy { it.category }
            .maxByOrNull { it.value.sumOf { tx -> tx.amountPaise } }
        if (topCat != null) {
            insights.add(Insight("🛍️", "${topCat.key} is your highest spending category at ${formatRupees(topCat.value.sumOf { it.amountPaise })}.", priority = 5))
        }

        // 3. Unusual Spending (Compared to previous period if available)
        // For simplicity, we'll just check for any single transaction > 50% of total expense
        val unusual = expenses.find { it.amountPaise * 2L > totalExpense && totalExpense > 100000L }
        if (unusual != null) {
            insights.add(Insight("🔎", "Unusual spending detected: ${unusual.title} was over 50% of your period's expenses.", priority = 8))
        }

        return insights.sortedByDescending { it.priority }
    }
}
