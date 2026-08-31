package com.vaibhav.moneytracker

/**
 * Time periods for dashboard and history filtering.
 */
enum class DashboardPeriod(val label: String) {
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    LAST_3_MONTHS("Last 3 Months"),
    LAST_6_MONTHS("Last 6 Months"),
    THIS_YEAR("This Year"),
    ALL_TIME("All Time")
}
