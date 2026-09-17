package com.vaibhav.moneytracker.intelligence

import com.vaibhav.moneytracker.formatRupees
import java.util.Locale

enum class AlertSeverity {
    HIGH,
    MEDIUM,
    LOW
}

data class FinancialAlert(
    val id: String,
    val icon: String,
    val title: String,
    val message: String,
    val severity: AlertSeverity,
    val timestampMs: Long = System.currentTimeMillis()
)

object FinancialAlertEngine {

    fun generateAlerts(
        healthReport: FinancialHealthReport,
        budgetSignals: List<BudgetPressureSignal>
    ): List<FinancialAlert> {
        val alerts = mutableListOf<FinancialAlert>()

        // 1. Over-budget alerts
        budgetSignals.filter { it.pressureState == PressureState.OVER_BUDGET }.forEach { signal ->
            alerts.add(
                FinancialAlert(
                    id = "budget_over_${signal.budget.id}",
                    icon = "🚨",
                    title = "Over Budget: ${signal.categoryName}",
                    message = "Spending exceeded monthly budget by ${formatRupees(Math.abs(signal.remainingPaise))}.",
                    severity = AlertSeverity.HIGH
                )
            )
        }

        // 2. Approaching budget limit alerts
        budgetSignals.filter { it.pressureState == PressureState.APPROACHING_LIMIT }.forEach { signal ->
            alerts.add(
                FinancialAlert(
                    id = "budget_near_${signal.budget.id}",
                    icon = "⚡",
                    title = "Approaching Limit: ${signal.categoryName}",
                    message = "Used ${String.format(Locale.US, "%.0f", signal.percentageUsed)}% of budget. ${formatRupees(signal.remainingPaise)} remaining.",
                    severity = AlertSeverity.MEDIUM
                )
            )
        }

        // 3. Negative Cash Flow Alert
        if (healthReport.cashFlowHealth == CashFlowHealth.NEGATIVE) {
            alerts.add(
                FinancialAlert(
                    id = "negative_cash_flow",
                    icon = "⚠️",
                    title = "Negative Cash Flow",
                    message = "Expenses exceed income for this period.",
                    severity = AlertSeverity.HIGH
                )
            )
        }

        // 4. High Recurring Burden Alert
        if (healthReport.recurringBurdenHealth == RecurringBurdenHealth.HIGH) {
            alerts.add(
                FinancialAlert(
                    id = "high_recurring_burden",
                    icon = "🔄",
                    title = "High Recurring Obligations",
                    message = "Fixed recurring subscriptions take up > 30% of your income.",
                    severity = AlertSeverity.MEDIUM
                )
            )
        }

        return alerts.sortedBy { it.severity.ordinal }
    }
}
