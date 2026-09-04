package com.vaibhav.moneytracker.capture

data class ParsedNotificationCandidate(
    val title: String,
    val amountPaise: Long?,
    val type: String, // "Expense", "Income", "Transfer"
    val timestamp: Long,
    val sourceApp: String,
    val referenceNumber: String? = null,
    val isFinancial: Boolean = true,
    val isIgnoredOrFailed: Boolean = false,
    val ignoreReason: String? = null
)
