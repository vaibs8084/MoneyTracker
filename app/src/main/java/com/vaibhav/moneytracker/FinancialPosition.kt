package com.vaibhav.moneytracker

/**
 * Canonical classifications for ExternalIn and ExternalOut transactions.
 *
 * Values are stored as strings to keep Room migrations and existing records
 * simple. A null transaction value means the external movement predates this
 * distinction (or has intentionally not been classified) and must not be
 * guessed to be any of these kinds.
 */
object ExternalMoneyKind {
    const val HELD = "Held"
    const val RECEIVABLE = "Receivable"
    const val LIABILITY = "Liability"

    fun isKnown(value: String?): Boolean = value == HELD ||
        value == RECEIVABLE || value == LIABILITY
}

/**
 * New external-money entries must use one of the supported classifications.
 * A legacy record whose stored value is null may be edited without silently
 * reclassifying it, but a null selection is not valid for a new entry.
 */
fun isExternalMoneyClassificationValid(
    transactionType: String,
    externalMoneyKind: String?,
    allowsLegacyUnclassified: Boolean = false
): Boolean {
    val isExternal = transactionType == "ExternalIn" || transactionType == "ExternalOut"
    return !isExternal || ExternalMoneyKind.isKnown(externalMoneyKind) ||
        (allowsLegacyUnclassified && externalMoneyKind == null)
}

/**
 * A balance-sheet view in paise. Physical account cash can include money that
 * belongs to somebody else; personalNetWorth is the ownership-adjusted value.
 */
data class FinancialPosition(
    val physicalAccountBalancesPaise: Long,
    val moneyHeldForOthersPaise: Long,
    val receivablesPaise: Long,
    val liabilitiesPaise: Long,
    val unclassifiedExternalPaise: Long,
    val personalNetWorthPaise: Long
)
