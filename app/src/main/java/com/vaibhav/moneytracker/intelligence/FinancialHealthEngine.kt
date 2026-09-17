package com.vaibhav.moneytracker.intelligence

enum class CashFlowHealth {
    POSITIVE,
    BALANCED,
    NEGATIVE
}

enum class SavingsHealth {
    EXCELLENT,        // >= 30%
    HEALTHY,          // 15% - 29%
    LOW,              // 1% - 14%
    NEEDS_ATTENTION   // <= 0% or income zero
}

enum class RecurringBurdenHealth {
    LOW,        // < 15% income
    MODERATE,   // 15% - 30% income
    HIGH        // > 30% income
}

data class FinancialHealthReport(
    val cashFlowHealth: CashFlowHealth,
    val savingsHealth: SavingsHealth,
    val recurringBurdenHealth: RecurringBurdenHealth,
    val overBudgetCount: Int,
    val approachingBudgetCount: Int,
    val totalActiveGoalsCount: Int,
    val completedGoalsCount: Int
)

object FinancialHealthEngine {

    fun evaluateFinancialHealth(
        cashFlow: CashFlowSummary,
        savingsRate: SavingsRate,
        recurringSummary: RecurringCommitmentSummary,
        budgetSignals: List<BudgetPressureSignal>,
        goalSignals: List<GoalProgressSignal>
    ): FinancialHealthReport {
        val cfHealth = when {
            cashFlow.netFlowPaise > 0L -> CashFlowHealth.POSITIVE
            cashFlow.netFlowPaise == 0L -> CashFlowHealth.BALANCED
            else -> CashFlowHealth.NEGATIVE
        }

        val sHealth = when {
            savingsRate.isIncomeZero || savingsRate.percentage <= 0.0 -> SavingsHealth.NEEDS_ATTENTION
            savingsRate.percentage >= 30.0 -> SavingsHealth.EXCELLENT
            savingsRate.percentage >= 15.0 -> SavingsHealth.HEALTHY
            else -> SavingsHealth.LOW
        }

        val rHealth = when {
            recurringSummary.recurringIncomeSharePercentage >= 30.0 -> RecurringBurdenHealth.HIGH
            recurringSummary.recurringIncomeSharePercentage >= 15.0 -> RecurringBurdenHealth.MODERATE
            else -> RecurringBurdenHealth.LOW
        }

        val overBudget = budgetSignals.count { it.pressureState == PressureState.OVER_BUDGET }
        val nearBudget = budgetSignals.count { it.pressureState == PressureState.APPROACHING_LIMIT }
        val activeGoals = goalSignals.count { !it.goal.isCompleted }
        val completedGoals = goalSignals.count { it.goal.isCompleted }

        return FinancialHealthReport(
            cashFlowHealth = cfHealth,
            savingsHealth = sHealth,
            recurringBurdenHealth = rHealth,
            overBudgetCount = overBudget,
            approachingBudgetCount = nearBudget,
            totalActiveGoalsCount = activeGoals,
            completedGoalsCount = completedGoals
        )
    }
}
