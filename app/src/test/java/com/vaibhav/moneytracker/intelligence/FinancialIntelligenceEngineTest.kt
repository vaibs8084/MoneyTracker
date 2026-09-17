package com.vaibhav.moneytracker.intelligence

import com.vaibhav.moneytracker.BudgetEntity
import com.vaibhav.moneytracker.CategoryEntity
import com.vaibhav.moneytracker.DashboardPeriod
import com.vaibhav.moneytracker.SubscriptionEntity
import com.vaibhav.moneytracker.TransactionUiModel
import org.junit.Assert.*
import org.junit.Test

class FinancialIntelligenceEngineTest {

    @Test
    fun testCashFlowSummaryCalculation() {
        val now = System.currentTimeMillis()
        val txs = listOf(
            TransactionUiModel(id = 1, title = "Salary", category = "General", account = "Bank", type = "Income", amountPaise = 10000000L, note = "", createdAt = now),
            TransactionUiModel(id = 2, title = "Rent", category = "Housing", account = "Bank", type = "Expense", amountPaise = 3000000L, note = "", createdAt = now),
            TransactionUiModel(id = 3, title = "Food", category = "Food", account = "Bank", type = "Expense", amountPaise = 1000000L, note = "", createdAt = now),
            TransactionUiModel(id = 4, title = "Internal Transfer", category = "General", account = "Bank", type = "Transfer", amountPaise = 500000L, note = "", createdAt = now)
        )

        val cashFlow = FinancialIntelligenceEngine.calculateCashFlow(txs, now - 1000, now + 1000)

        assertEquals(10000000L, cashFlow.incomePaise)
        assertEquals(4000000L, cashFlow.expensePaise)
        assertEquals(6000000L, cashFlow.netFlowPaise)
        assertEquals(1, cashFlow.inflowCount)
        assertEquals(2, cashFlow.outflowCount) // Transfers excluded from inflow/outflow count
    }

    @Test
    fun testSavingsRateCalculation() {
        val positiveFlow = CashFlowSummary(incomePaise = 10000000L, expensePaise = 4000000L, netFlowPaise = 6000000L, inflowCount = 1, outflowCount = 2)
        val ratePositive = FinancialIntelligenceEngine.calculateSavingsRate(positiveFlow)

        assertFalse(ratePositive.isIncomeZero)
        assertEquals(60.0, ratePositive.percentage, 0.01)
        assertEquals(6000000L, ratePositive.netSavingsPaise)

        // Zero Income Case (Must not throw NaN or Infinity)
        val zeroIncomeFlow = CashFlowSummary(incomePaise = 0L, expensePaise = 500000L, netFlowPaise = -500000L, inflowCount = 0, outflowCount = 1)
        val rateZero = FinancialIntelligenceEngine.calculateSavingsRate(zeroIncomeFlow)

        assertTrue(rateZero.isIncomeZero)
        assertEquals(0.0, rateZero.percentage, 0.01)
        assertFalse(rateZero.percentage.isNaN())
        assertFalse(rateZero.percentage.isInfinite())
    }

    @Test
    fun testSpendingAnalysisCategorizationAndMerchants() {
        val now = System.currentTimeMillis()
        val txs = listOf(
            TransactionUiModel(id = 1, title = "SWIGGY*ORDER1", category = "Food", account = "Bank", type = "Expense", amountPaise = 50000L, note = "", createdAt = now),
            TransactionUiModel(id = 2, title = "UPI/SWIGGY/PAYTM", category = "Food", account = "Bank", type = "Expense", amountPaise = 30000L, note = "", createdAt = now),
            TransactionUiModel(id = 3, title = "Uber Ride", category = "Transport", account = "Bank", type = "Expense", amountPaise = 20000L, note = "", createdAt = now)
        )

        val analysis = FinancialIntelligenceEngine.analyzeSpending(txs, now - 1000, now + 1000)

        assertEquals(100000L, analysis.totalExpensePaise)
        assertEquals("Food", analysis.topCategories.first().categoryName)
        assertEquals(80000L, analysis.topCategories.first().amountPaise)

        assertEquals("Swiggy", analysis.topMerchants.first().first)
        assertEquals(80000L, analysis.topMerchants.first().second)
    }

    @Test
    fun testPeriodComparisonDeltas() {
        val now = System.currentTimeMillis()
        val currentStart = now - 10000
        val currentEnd = now
        val previousStart = now - 30000
        val previousEnd = now - 10001

        val txs = listOf(
            TransactionUiModel(id = 1, title = "Current Tx", category = "Food", account = "Bank", type = "Expense", amountPaise = 120000L, note = "", createdAt = now - 1000),
            TransactionUiModel(id = 2, title = "Previous Tx", category = "Food", account = "Bank", type = "Expense", amountPaise = 100000L, note = "", createdAt = now - 20000)
        )

        val comparison = FinancialIntelligenceEngine.comparePeriodSpending(txs, currentStart, currentEnd, previousStart, previousEnd)

        assertEquals(120000L, comparison.currentExpensePaise)
        assertEquals(100000L, comparison.previousExpensePaise)
        assertEquals(20000L, comparison.absoluteChangePaise)
        assertEquals(20.0, comparison.percentageChange, 0.01)
    }

    @Test
    fun testRecurringCommitmentSummary() {
        val subscriptions = listOf(
            SubscriptionEntity(id = 1, name = "Netflix", amountPaise = 64900L, cadence = "MONTHLY", nextDate = 1000L, isConfirmed = true, isActive = true),
            SubscriptionEntity(id = 2, name = "Gym", amountPaise = 100000L, cadence = "WEEKLY", nextDate = 1000L, isConfirmed = true, isActive = true)
        )

        val summary = FinancialIntelligenceEngine.analyzeRecurringCommitments(subscriptions, 10000000L)

        // 649.00 (Monthly) + 4,000.00 (4 x Weekly 1,000.00) = 4,649.00 -> 464900L
        assertEquals(464900L, summary.monthlyCommitmentPaise)
        assertEquals(2, summary.activeSubscriptionCount)
        assertEquals(4.649, summary.recurringIncomeSharePercentage, 0.001)
    }

    @Test
    fun testEvaluateBudgetPressureSignals() {
        val now = System.currentTimeMillis()
        val categories = listOf(CategoryEntity(id = 1L, name = "Food"), CategoryEntity(id = 2L, name = "Transport"))
        val budgets = listOf(
            BudgetEntity(id = 10, categoryId = 1L, limitPaise = 500000L), // Rs 5,000 limit
            BudgetEntity(id = 11, categoryId = 2L, limitPaise = 200000L)  // Rs 2,000 limit
        )
        val txs = listOf(
            TransactionUiModel(id = 1, title = "Food", category = "Food", account = "Bank", type = "Expense", amountPaise = 600000L, note = "", categoryId = 1L, createdAt = now),
            TransactionUiModel(id = 2, title = "Taxi", category = "Transport", account = "Bank", type = "Expense", amountPaise = 170000L, note = "", categoryId = 2L, createdAt = now)
        )

        val signals = FinancialIntelligenceEngine.evaluateBudgetPressure(txs, budgets, categories)

        val foodSignal = signals.find { it.budget.categoryId == 1L }!!
        assertEquals(PressureState.OVER_BUDGET, foodSignal.pressureState)
        assertEquals(-100000L, foodSignal.remainingPaise)

        val transportSignal = signals.find { it.budget.categoryId == 2L }!!
        assertEquals(PressureState.APPROACHING_LIMIT, transportSignal.pressureState)
        assertEquals(30000L, transportSignal.remainingPaise)
    }

    @Test
    fun testGenerateStructuredInsights() {
        val now = System.currentTimeMillis()
        val txs = listOf(
            TransactionUiModel(id = 1, title = "Salary", category = "General", account = "Bank", type = "Income", amountPaise = 10000000L, note = "", createdAt = now),
            TransactionUiModel(id = 2, title = "Food", category = "Food", account = "Bank", type = "Expense", amountPaise = 3000000L, note = "", categoryId = 1L, createdAt = now)
        )
        val categories = listOf(CategoryEntity(id = 1L, name = "Food"))
        val budgets = listOf(BudgetEntity(id = 10, categoryId = 1L, limitPaise = 2500000L)) // Over budget
        val subscriptions = listOf(SubscriptionEntity(id = 1, name = "Netflix", amountPaise = 64900L, cadence = "MONTHLY", nextDate = now, isConfirmed = true))

        val insights = FinancialIntelligenceEngine.generateStructuredInsights(
            transactions = txs,
            accounts = emptyList(),
            categories = categories,
            budgets = budgets,
            goals = emptyList(),
            subscriptions = subscriptions,
            period = DashboardPeriod.THIS_MONTH
        )

        assertTrue(insights.isNotEmpty())
        assertTrue("Must generate budget alert insight", insights.any { it.type == InsightType.BUDGET_ALERT })
        assertTrue("Must generate savings insight", insights.any { it.type == InsightType.SAVINGS })
        assertTrue("Must generate top category insight", insights.any { it.type == InsightType.SPENDING_TREND })
    }
}
