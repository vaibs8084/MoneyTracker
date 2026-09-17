package com.vaibhav.moneytracker.intelligence

import com.vaibhav.moneytracker.*
import java.util.Calendar
import java.util.Locale

data class CashFlowSummary(
    val incomePaise: Long,
    val expensePaise: Long,
    val netFlowPaise: Long,
    val inflowCount: Int,
    val outflowCount: Int
)

data class SavingsRate(
    val percentage: Double,
    val netSavingsPaise: Long,
    val isIncomeZero: Boolean = false
)

data class SpendingCategoryShare(
    val categoryName: String,
    val amountPaise: Long,
    val percentage: Double
)

data class SpendingAnalysis(
    val topCategories: List<SpendingCategoryShare>,
    val topMerchants: List<Pair<String, Long>>,
    val totalExpensePaise: Long
)

data class PeriodComparison(
    val currentExpensePaise: Long,
    val previousExpensePaise: Long,
    val absoluteChangePaise: Long,
    val percentageChange: Double
)

data class RecurringCommitmentSummary(
    val monthlyCommitmentPaise: Long,
    val activeSubscriptionCount: Int,
    val recurringIncomeSharePercentage: Double
)

enum class PressureState {
    HEALTHY,
    APPROACHING_LIMIT,
    OVER_BUDGET
}

data class BudgetPressureSignal(
    val budget: BudgetEntity,
    val categoryName: String,
    val usagePaise: Long,
    val limitPaise: Long,
    val remainingPaise: Long,
    val percentageUsed: Double,
    val pressureState: PressureState
)

data class GoalProgressSignal(
    val goal: GoalEntity,
    val currentProgressPaise: Long,
    val targetPaise: Long,
    val percentageCompleted: Double,
    val remainingPaise: Long,
    val requiredMonthlySavingsPaise: Long? = null
)

enum class InsightType {
    SAVINGS,
    SPENDING_TREND,
    BUDGET_ALERT,
    GOAL_PROGRESS,
    RECURRING_BILL,
    CASH_FLOW
}

data class StructuredFinancialInsight(
    val type: InsightType,
    val icon: String,
    val title: String,
    val description: String,
    val priority: Int = 0,
    val valuePaise: Long? = null
)

object FinancialIntelligenceEngine {

    fun calculateCashFlow(
        transactions: List<TransactionUiModel>,
        startMs: Long,
        endMs: Long
    ): CashFlowSummary {
        val periodTxs = transactions.filter { it.createdAt in startMs..endMs }

        val incomes = periodTxs.filter { it.type == "Income" }
        val expenses = periodTxs.filter { it.type == "Expense" }

        val totalIncome = incomes.sumOf { it.amountPaise }
        val totalExpense = expenses.sumOf { it.amountPaise }

        return CashFlowSummary(
            incomePaise = totalIncome,
            expensePaise = totalExpense,
            netFlowPaise = totalIncome - totalExpense,
            inflowCount = incomes.size,
            outflowCount = expenses.size
        )
    }

    fun calculateSavingsRate(cashFlow: CashFlowSummary): SavingsRate {
        if (cashFlow.incomePaise <= 0L) {
            return SavingsRate(
                percentage = 0.0,
                netSavingsPaise = cashFlow.netFlowPaise,
                isIncomeZero = true
            )
        }

        val savings = cashFlow.incomePaise - cashFlow.expensePaise
        val rate = (savings.toDouble() / cashFlow.incomePaise.toDouble()) * 100.0

        return SavingsRate(
            percentage = rate.coerceIn(-1000.0, 100.0),
            netSavingsPaise = savings,
            isIncomeZero = false
        )
    }

    fun analyzeSpending(
        transactions: List<TransactionUiModel>,
        startMs: Long,
        endMs: Long
    ): SpendingAnalysis {
        val expenses = transactions.filter { it.type == "Expense" && it.createdAt in startMs..endMs }
        val totalExpense = expenses.sumOf { it.amountPaise }

        if (totalExpense <= 0L) {
            return SpendingAnalysis(emptyList(), emptyList(), 0L)
        }

        val catShares = expenses.groupBy { it.category }
            .map { (catName, txList) ->
                val amt = txList.sumOf { it.amountPaise }
                val pct = (amt.toDouble() / totalExpense.toDouble()) * 100.0
                SpendingCategoryShare(catName, amt, pct)
            }
            .sortedByDescending { it.amountPaise }

        val merchantShares = expenses.groupBy { MerchantNormalizer.normalize(it.title) }
            .map { (merchant, txList) ->
                merchant to txList.sumOf { it.amountPaise }
            }
            .sortedByDescending { it.second }

        return SpendingAnalysis(
            topCategories = catShares,
            topMerchants = merchantShares,
            totalExpensePaise = totalExpense
        )
    }

    fun comparePeriodSpending(
        transactions: List<TransactionUiModel>,
        currentStartMs: Long,
        currentEndMs: Long,
        previousStartMs: Long,
        previousEndMs: Long
    ): PeriodComparison {
        val currentExpense = transactions
            .filter { it.type == "Expense" && it.createdAt in currentStartMs..currentEndMs }
            .sumOf { it.amountPaise }

        val previousExpense = transactions
            .filter { it.type == "Expense" && it.createdAt in previousStartMs..previousEndMs }
            .sumOf { it.amountPaise }

        val absChange = currentExpense - previousExpense
        val pctChange = if (previousExpense > 0L) {
            ((absChange.toDouble()) / previousExpense.toDouble()) * 100.0
        } else if (currentExpense > 0L) {
            100.0
        } else {
            0.0
        }

        return PeriodComparison(
            currentExpensePaise = currentExpense,
            previousExpensePaise = previousExpense,
            absoluteChangePaise = absChange,
            percentageChange = pctChange
        )
    }

    fun analyzeRecurringCommitments(
        subscriptions: List<SubscriptionEntity>,
        totalIncomePaise: Long
    ): RecurringCommitmentSummary {
        val active = subscriptions.filter { it.isConfirmed && it.isActive }
        var monthlyTotal = 0L

        active.forEach { sub ->
            when (sub.cadence) {
                "WEEKLY" -> monthlyTotal += sub.amountPaise * 4L
                "MONTHLY" -> monthlyTotal += sub.amountPaise
                "YEARLY" -> monthlyTotal += sub.amountPaise / 12L
            }
        }

        val sharePct = if (totalIncomePaise > 0L) {
            (monthlyTotal.toDouble() / totalIncomePaise.toDouble()) * 100.0
        } else {
            0.0
        }

        return RecurringCommitmentSummary(
            monthlyCommitmentPaise = monthlyTotal,
            activeSubscriptionCount = active.size,
            recurringIncomeSharePercentage = sharePct
        )
    }

    fun evaluateBudgetPressure(
        transactions: List<TransactionUiModel>,
        budgets: List<BudgetEntity>,
        categories: List<CategoryEntity>
    ): List<BudgetPressureSignal> {
        return budgets.filter { it.isActive }.map { budget ->
            val categoryName = categories.find { it.id == budget.categoryId }?.name ?: "Category"
            val usage = IntelligenceEngine.calculateBudgetUsage(transactions, budget)
            val pct = if (budget.limitPaise > 0L) (usage.toDouble() / budget.limitPaise.toDouble()) * 100.0 else 0.0

            val state = when {
                pct >= 100.0 -> PressureState.OVER_BUDGET
                pct >= 80.0 -> PressureState.APPROACHING_LIMIT
                else -> PressureState.HEALTHY
            }

            BudgetPressureSignal(
                budget = budget,
                categoryName = categoryName,
                usagePaise = usage,
                limitPaise = budget.limitPaise,
                remainingPaise = budget.limitPaise - usage,
                percentageUsed = pct,
                pressureState = state
            )
        }
    }

    fun generateStructuredInsights(
        transactions: List<TransactionUiModel>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity>,
        goals: List<GoalEntity>,
        subscriptions: List<SubscriptionEntity>,
        period: DashboardPeriod
    ): List<StructuredFinancialInsight> {
        val insights = mutableListOf<StructuredFinancialInsight>()

        val startMs = getPeriodStartTimestamp(period)
        val endMs = getPeriodEndTimestamp(period)

        // 1. Cash Flow & Savings Insight
        val cashFlow = calculateCashFlow(transactions, startMs, endMs)
        val savingsRate = calculateSavingsRate(cashFlow)

        if (!savingsRate.isIncomeZero && cashFlow.netFlowPaise > 0L) {
            insights.add(
                StructuredFinancialInsight(
                    type = InsightType.SAVINGS,
                    icon = "📈",
                    title = "Positive Cash Flow",
                    description = "You've saved ${formatRupees(cashFlow.netFlowPaise)} (${String.format(Locale.US, "%.1f", savingsRate.percentage)}% savings rate) this period.",
                    priority = 10,
                    valuePaise = cashFlow.netFlowPaise
                )
            )
        } else if (!savingsRate.isIncomeZero && cashFlow.netFlowPaise < 0L) {
            insights.add(
                StructuredFinancialInsight(
                    type = InsightType.CASH_FLOW,
                    icon = "⚠️",
                    title = "Negative Cash Flow",
                    description = "Your spending exceeds income by ${formatRupees(Math.abs(cashFlow.netFlowPaise))} this period.",
                    priority = 10,
                    valuePaise = cashFlow.netFlowPaise
                )
            )
        }

        // 2. Spending Analysis & Concentration
        val spending = analyzeSpending(transactions, startMs, endMs)
        val topCategory = spending.topCategories.firstOrNull()
        if (topCategory != null && topCategory.amountPaise > 0L) {
            insights.add(
                StructuredFinancialInsight(
                    type = InsightType.SPENDING_TREND,
                    icon = "🛍️",
                    title = "Highest Spending Category",
                    description = "${topCategory.categoryName} represents ${String.format(Locale.US, "%.0f", topCategory.percentage)}% of your expenses (${formatRupees(topCategory.amountPaise)}).",
                    priority = 6,
                    valuePaise = topCategory.amountPaise
                )
            )
        }

        // 3. Budget Pressure Alerts
        val budgetSignals = evaluateBudgetPressure(transactions, budgets, categories)
        val overBudget = budgetSignals.find { it.pressureState == PressureState.OVER_BUDGET }
        val nearBudget = budgetSignals.find { it.pressureState == PressureState.APPROACHING_LIMIT }

        if (overBudget != null) {
            insights.add(
                StructuredFinancialInsight(
                    type = InsightType.BUDGET_ALERT,
                    icon = "🚨",
                    title = "Budget Exceeded",
                    description = "${overBudget.categoryName} is over budget by ${formatRupees(Math.abs(overBudget.remainingPaise))}.",
                    priority = 9,
                    valuePaise = overBudget.usagePaise
                )
            )
        } else if (nearBudget != null) {
            insights.add(
                StructuredFinancialInsight(
                    type = InsightType.BUDGET_ALERT,
                    icon = "⚡",
                    title = "Approaching Budget Limit",
                    description = "${nearBudget.categoryName} budget is at ${String.format(Locale.US, "%.0f", nearBudget.percentageUsed)}% of limit (${formatRupees(nearBudget.remainingPaise)} remaining).",
                    priority = 8,
                    valuePaise = nearBudget.remainingPaise
                )
            )
        }

        // 4. Recurring Commitments
        val recurringSummary = analyzeRecurringCommitments(subscriptions, cashFlow.incomePaise)
        if (recurringSummary.activeSubscriptionCount > 0) {
            insights.add(
                StructuredFinancialInsight(
                    type = InsightType.RECURRING_BILL,
                    icon = "🔄",
                    title = "Recurring Commitments",
                    description = "${recurringSummary.activeSubscriptionCount} active subscriptions represent ${formatRupees(recurringSummary.monthlyCommitmentPaise)}/month.",
                    priority = 5,
                    valuePaise = recurringSummary.monthlyCommitmentPaise
                )
            )
        }

        return insights.sortedByDescending { it.priority }
    }
}
