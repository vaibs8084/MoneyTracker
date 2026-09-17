package com.vaibhav.moneytracker.intelligence

import com.vaibhav.moneytracker.*

data class ConsolidatedIntelligenceOutput(
    val cashFlow: CashFlowSummary,
    val savingsRate: SavingsRate,
    val spendingAnalysis: SpendingAnalysis,
    val periodComparison: PeriodComparison,
    val recurringSummary: RecurringCommitmentSummary,
    val budgetSignals: List<BudgetPressureSignal>,
    val healthReport: FinancialHealthReport,
    val structuredInsights: List<StructuredFinancialInsight>,
    val alerts: List<FinancialAlert>
)

object MoneyTrackerIntelligenceSystem {

    fun computeConsolidatedIntelligence(
        transactions: List<TransactionUiModel>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity>,
        goals: List<GoalEntity>,
        subscriptions: List<SubscriptionEntity>,
        period: DashboardPeriod
    ): ConsolidatedIntelligenceOutput {
        val startMs = getPeriodStartTimestamp(period)
        val endMs = getPeriodEndTimestamp(period)

        // Previous period timestamps for comparison
        val periodDurationMs = Math.max(1000L, endMs - startMs)
        val prevStartMs = startMs - periodDurationMs
        val prevEndMs = startMs - 1L

        // 1. Aggregations
        val cashFlow = FinancialIntelligenceEngine.calculateCashFlow(transactions, startMs, endMs)
        val savingsRate = FinancialIntelligenceEngine.calculateSavingsRate(cashFlow)
        val spendingAnalysis = FinancialIntelligenceEngine.analyzeSpending(transactions, startMs, endMs)
        val periodComparison = FinancialIntelligenceEngine.comparePeriodSpending(transactions, startMs, endMs, prevStartMs, prevEndMs)
        val recurringSummary = FinancialIntelligenceEngine.analyzeRecurringCommitments(subscriptions, cashFlow.incomePaise)

        // 2. Health & Pressure Signals
        val budgetSignals = FinancialIntelligenceEngine.evaluateBudgetPressure(transactions, budgets, categories)
        val goalSignals = goals.map { goal ->
            val progressPaise = if (goal.linkedAccountId != null) {
                accounts.find { it.id == goal.linkedAccountId }?.openingBalancePaise ?: goal.manualProgressPaise
            } else {
                goal.manualProgressPaise
            }
            val remainingPaise = Math.max(0L, goal.targetPaise - progressPaise)
            val pct = if (goal.targetPaise > 0L) (progressPaise.toDouble() / goal.targetPaise.toDouble()) * 100.0 else 0.0
            GoalProgressSignal(goal, progressPaise, goal.targetPaise, pct, remainingPaise)
        }

        val healthReport = FinancialHealthEngine.evaluateFinancialHealth(
            cashFlow, savingsRate, recurringSummary, budgetSignals, goalSignals
        )

        // 3. Insights & Alerts
        val structuredInsights = FinancialIntelligenceEngine.generateStructuredInsights(
            transactions, accounts, categories, budgets, goals, subscriptions, period
        )
        val alerts = FinancialAlertEngine.generateAlerts(healthReport, budgetSignals)

        return ConsolidatedIntelligenceOutput(
            cashFlow = cashFlow,
            savingsRate = savingsRate,
            spendingAnalysis = spendingAnalysis,
            periodComparison = periodComparison,
            recurringSummary = recurringSummary,
            budgetSignals = budgetSignals,
            healthReport = healthReport,
            structuredInsights = structuredInsights,
            alerts = alerts
        )
    }
}
