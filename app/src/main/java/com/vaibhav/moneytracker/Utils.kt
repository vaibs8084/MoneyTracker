package com.vaibhav.moneytracker

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Formats a paise amount into a rupee string (e.g., ₹500.00).
 */
fun formatRupees(amountPaise: Long): String {
    val rupees = amountPaise / 100.0
    return String.format(Locale.getDefault(), "₹%.2f", rupees)
}

/**
 * Formats a transaction for display with sign.
 */
fun formatSignedAmount(transaction: TransactionUiModel): String {
    val prefix = when (transaction.type) {
        "Income", "ExternalIn" -> "+"
        "Transfer" -> "↔"
        else -> "-"
    }
    return "$prefix ${formatRupees(transaction.amountPaise)}"
}

/**
 * Formats a timestamp into a date string.
 */
fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Returns the start timestamp for a given DashboardPeriod.
 */
fun getPeriodStartTimestamp(period: DashboardPeriod): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)

    return when (period) {
        DashboardPeriod.THIS_MONTH -> {
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.timeInMillis
        }
        DashboardPeriod.LAST_MONTH -> {
            calendar.add(Calendar.MONTH, -1)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.timeInMillis
        }
        DashboardPeriod.LAST_3_MONTHS -> {
            calendar.add(Calendar.MONTH, -2)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.timeInMillis
        }
        DashboardPeriod.LAST_6_MONTHS -> {
            calendar.add(Calendar.MONTH, -5)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.timeInMillis
        }
        DashboardPeriod.THIS_YEAR -> {
            calendar.set(Calendar.DAY_OF_YEAR, 1)
            calendar.timeInMillis
        }
        DashboardPeriod.ALL_TIME -> 0L
    }
}

/**
 * Returns the end timestamp for a given DashboardPeriod.
 */
fun getPeriodEndTimestamp(period: DashboardPeriod): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 999)

    return when (period) {
        DashboardPeriod.LAST_MONTH -> {
            calendar.add(Calendar.MONTH, -1)
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            calendar.timeInMillis
        }
        else -> Long.MAX_VALUE
    }
}

/**
 * Maps a internal transaction type to a user-friendly label.
 */
fun transactionTypeLabel(type: String): String {
    return when (type) {
        "ExternalIn" -> "Money In"
        "ExternalOut" -> "Money Out"
        else -> type
    }
}

/**
 * Returns a user-facing label for an ExternalMoneyKind.
 */
fun externalMoneyKindLabel(kind: String?): String {
    return when (kind) {
        ExternalMoneyKind.HELD -> "Money Held for Someone"
        ExternalMoneyKind.RECEIVABLE -> "Money Lent / Receivable"
        ExternalMoneyKind.LIABILITY -> "Money Borrowed / Liability"
        else -> "Unclassified"
    }
}

/**
 * Returns a user-facing explanation for an ExternalMoneyKind.
 */
fun externalMoneyKindDescription(kind: String?): String {
    return when (kind) {
        ExternalMoneyKind.HELD -> "This money belongs to someone else."
        ExternalMoneyKind.RECEIVABLE -> "Someone owes this money to me."
        ExternalMoneyKind.LIABILITY -> "This money belongs to someone else and I owe it back."
        else -> "Historical/unclassified external money."
    }
}

/**
 * Ensures default accounts exist in the database.
 */
suspend fun ensureDefaultAccounts(
    database: MoneyTrackerDatabase
) {
    val dao = database.accountDao()
    if (dao.getCount() == 0) {
        dao.insert(
            AccountEntity(
                name = "Kotak",
                type = "Bank",
                openingBalancePaise = 0L
            )
        )
        dao.insert(
            AccountEntity(
                name = "Cash",
                type = "Cash",
                openingBalancePaise = 0L
            )
        )
    }
}

/**
 * Ensures categories exist based on transaction history and defaults.
 */
suspend fun ensureCategories(
    database: MoneyTrackerDatabase
) {
    val transactionDao = database.transactionDao()
    val categoryDao = database.categoryDao()

    val uniqueCategoryNames = transactionDao.getAll().map { it.category }.distinct()
    val defaults = listOf("Food", "Transport", "Shopping")
    val allToEnsure = (uniqueCategoryNames + defaults).distinct()

    for (name in allToEnsure) {
        if (categoryDao.getByName(name) == null) {
            categoryDao.insert(CategoryEntity(name = name))
        }
    }
}

/**
 * Extension to convert TransactionEntity to UiModel.
 */
fun TransactionEntity.toUiModel(
    tags: List<TagEntity> = emptyList()
): TransactionUiModel {
    return TransactionUiModel(
        id = id,
        title = title,
        category = category,
        account = account,
        type = type,
        amountPaise = amountPaise,
        note = note,
        createdAt = createdAt,
        accountId = accountId,
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        externalMoneyKind = externalMoneyKind,
        tags = tags
    )
}

/**
 * Extension to convert UiModel to Entity.
 */
fun TransactionUiModel.toEntity(): TransactionEntity {
    return TransactionEntity(
        id = id,
        title = title,
        category = category,
        account = account,
        type = type,
        amountPaise = amountPaise,
        note = note,
        createdAt = createdAt,
        accountId = accountId,
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        externalMoneyKind = externalMoneyKind
    )
}
