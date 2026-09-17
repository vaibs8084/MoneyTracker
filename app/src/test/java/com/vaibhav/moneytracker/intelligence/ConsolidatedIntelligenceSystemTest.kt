package com.vaibhav.moneytracker.intelligence

import com.vaibhav.moneytracker.*
import org.junit.Assert.*
import org.junit.Test

class ConsolidatedIntelligenceSystemTest {

    @Test
    fun testPatternDetectorUnusualMerchantSpending() {
        val now = System.currentTimeMillis()
        val history = listOf(
            TransactionUiModel(id = 1, title = "Swiggy", category = "Food", account = "Bank", type = "Expense", amountPaise = 30000L, note = "", createdAt = now - 10000),
            TransactionUiModel(id = 2, title = "UPI/SWIGGY/1", category = "Food", account = "Bank", type = "Expense", amountPaise = 30000L, note = "", createdAt = now - 5000)
        )
        // Average Swiggy spending = Rs. 300.00 (30000L paise)
        val unusualTx = TransactionUiModel(id = 3, title = "SWIGGY*ORDER", category = "Food", account = "Bank", type = "Expense", amountPaise = 90000L, note = "", createdAt = now) // Rs. 900.00 (3x average)

        val pattern = PatternDetector.detectUnusualMerchantSpending(history, unusualTx)

        assertNotNull("Unusual spending >= 2x average must be detected", pattern)
        assertEquals("Swiggy", pattern?.merchantName)
        assertEquals(3.0, pattern?.multiplier ?: 0.0, 0.01)
        assertTrue(pattern?.explanation?.contains("3.0x higher") == true)
    }

    @Test
    fun testFinancialHealthEngineEvaluation() {
        val cashFlow = CashFlowSummary(incomePaise = 10000000L, expensePaise = 3000000L, netFlowPaise = 7000000L, inflowCount = 1, outflowCount = 2)
        val savingsRate = SavingsRate(percentage = 70.0, netSavingsPaise = 7000000L)
        val recurringSummary = RecurringCommitmentSummary(monthlyCommitmentPaise = 500000L, activeSubscriptionCount = 1, recurringIncomeSharePercentage = 5.0)

        val budgetSignals = listOf(
            BudgetPressureSignal(
                budget = BudgetEntity(id = 1, categoryId = 10L, limitPaise = 2000000L),
                categoryName = "Food",
                usagePaise = 2500000L,
                limitPaise = 2000000L,
                remainingPaise = -500000L,
                percentageUsed = 125.0,
                pressureState = PressureState.OVER_BUDGET
            )
        )

        val healthReport = FinancialHealthEngine.evaluateFinancialHealth(
            cashFlow = cashFlow,
            savingsRate = savingsRate,
            recurringSummary = recurringSummary,
            budgetSignals = budgetSignals,
            goalSignals = emptyList()
        )

        assertEquals(CashFlowHealth.POSITIVE, healthReport.cashFlowHealth)
        assertEquals(SavingsHealth.EXCELLENT, healthReport.savingsHealth)
        assertEquals(RecurringBurdenHealth.LOW, healthReport.recurringBurdenHealth)
        assertEquals(1, healthReport.overBudgetCount)
    }

    @Test
    fun testFinancialAlertEngineGeneration() {
        val healthReport = FinancialHealthReport(
            cashFlowHealth = CashFlowHealth.NEGATIVE,
            savingsHealth = SavingsHealth.NEEDS_ATTENTION,
            recurringBurdenHealth = RecurringBurdenHealth.HIGH,
            overBudgetCount = 1,
            approachingBudgetCount = 0,
            totalActiveGoalsCount = 0,
            completedGoalsCount = 0
        )

        val budgetSignals = listOf(
            BudgetPressureSignal(
                budget = BudgetEntity(id = 1, categoryId = 10L, limitPaise = 2000000L),
                categoryName = "Food",
                usagePaise = 2500000L,
                limitPaise = 2000000L,
                remainingPaise = -500000L,
                percentageUsed = 125.0,
                pressureState = PressureState.OVER_BUDGET
            )
        )

        val alerts = FinancialAlertEngine.generateAlerts(healthReport, budgetSignals)

        assertTrue(alerts.isNotEmpty())
        assertTrue("Must generate budget over-limit alert", alerts.any { it.severity == AlertSeverity.HIGH })
        assertTrue("Must generate negative cash flow alert", alerts.any { it.id == "negative_cash_flow" })
        assertTrue("Must generate high recurring burden alert", alerts.any { it.id == "high_recurring_burden" })
    }

    @Test
    fun testMoneyTrackerIntelligenceSystemOrchestration() {
        val now = System.currentTimeMillis()
        val txs = listOf(
            TransactionUiModel(id = 1, title = "Salary", category = "General", account = "Bank", type = "Income", amountPaise = 10000000L, note = "", createdAt = now),
            TransactionUiModel(id = 2, title = "Swiggy Food", category = "Food", account = "Bank", type = "Expense", amountPaise = 50000L, note = "", categoryId = 1L, createdAt = now)
        )
        val categories = listOf(CategoryEntity(id = 1L, name = "Food"))
        val budgets = listOf(BudgetEntity(id = 10, categoryId = 1L, limitPaise = 100000L))

        val output = MoneyTrackerIntelligenceSystem.computeConsolidatedIntelligence(
            transactions = txs,
            accounts = emptyList(),
            categories = categories,
            budgets = budgets,
            goals = emptyList(),
            subscriptions = emptyList(),
            period = DashboardPeriod.THIS_MONTH
        )

        assertNotNull(output.cashFlow)
        assertNotNull(output.savingsRate)
        assertNotNull(output.spendingAnalysis)
        assertNotNull(output.healthReport)
        assertTrue(output.structuredInsights.isNotEmpty())
    }
}
