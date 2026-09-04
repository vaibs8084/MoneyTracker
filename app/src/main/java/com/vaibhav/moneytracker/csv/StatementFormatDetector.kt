package com.vaibhav.moneytracker.csv

import java.util.Locale

data class StatementColumnIndices(
    val dateIndex: Int? = null,
    val titleIndex: Int? = null,
    val debitIndex: Int? = null,
    val creditIndex: Int? = null,
    val amountIndex: Int? = null,
    val typeIndex: Int? = null,
    val categoryIndex: Int? = null,
    val ignoredIndices: Set<Int> = emptySet()
)

object StatementFormatDetector {

    private val balanceHeaders = listOf(
        "balance", "running balance", "closing balance", "available balance",
        "ledger balance", "bal", "curr bal", "closing bal", "clear balance"
    )

    // Robust Opening Balance Terminology Variants
    val openingBalanceTitles = listOf(
        "opening balance", "opening bal", "opening ledger balance", "opening available balance",
        "beginning balance", "beginning bal", "starting balance", "start balance",
        "brought forward", "b/f", "balance b/f", "balance bf", "balance brought forward",
        "previous balance", "previous bal", "balance forward"
    )

    // Robust Closing Balance Terminology Variants
    val closingBalanceTitles = listOf(
        "closing balance", "closing bal", "closing ledger balance", "closing available balance",
        "ending balance", "ending bal", "final balance", "final bal",
        "carried forward", "c/f", "balance c/f", "balance cf", "balance carried forward",
        "ending available balance", "running balance", "ledger balance", "available balance"
    )

    private val dateHeaders = listOf(
        "date", "txn date", "transaction date", "value date", "post date", "booking date"
    )

    private val titleHeaders = listOf(
        "narration", "description", "particulars", "details", "title",
        "memo", "transaction details", "remarks", "payee"
    )

    private val debitHeaders = listOf(
        "debit", "withdrawal", "dr", "debit amount", "paid out", "out", "withdrawal amount"
    )

    private val creditHeaders = listOf(
        "credit", "deposit", "cr", "credit amount", "paid in", "in", "deposit amount"
    )

    private val amountHeaders = listOf(
        "amount", "txn amount", "transaction amount", "val", "sum"
    )

    private val typeHeaders = listOf(
        "type", "cr/dr", "transaction kind", "d/c", "dc", "indicator"
    )

    private val categoryHeaders = listOf(
        "category", "cat"
    )

    /**
     * Checks if a column header represents a running/closing balance column.
     */
    fun isBalanceColumn(header: String): Boolean {
        val h = header.trim().lowercase(Locale.getDefault())
        return balanceHeaders.any { h == it || h.contains(it) || h.contains("balance") }
    }

    /**
     * Checks if a row title/narration represents an explicit opening balance.
     */
    fun isOpeningBalanceRow(title: String): Boolean {
        val t = title.trim().lowercase(Locale.getDefault())
        if (t == "b/f") return true
        return openingBalanceTitles.any { t == it || t.startsWith(it) }
    }

    /**
     * Checks if a row title/narration represents an explicit closing balance.
     */
    fun isClosingBalanceRow(title: String): Boolean {
        val t = title.trim().lowercase(Locale.getDefault())
        if (t == "c/f") return true
        return closingBalanceTitles.any { t == it || t.startsWith(it) }
    }

    /**
     * Checks if a row title/narration represents an explicit non-transaction summary row.
     */
    fun isNonTransactionRow(title: String): Boolean {
        return isOpeningBalanceRow(title) || isClosingBalanceRow(title)
    }

    /**
     * Automatically detects column indices from a list of header strings.
     */
    fun detectColumns(headers: List<String>): StatementColumnIndices {
        var dateIdx: Int? = null
        var titleIdx: Int? = null
        var debitIdx: Int? = null
        var creditIdx: Int? = null
        var amountIdx: Int? = null
        var typeIdx: Int? = null
        var categoryIdx: Int? = null
        val ignored = mutableSetOf<Int>()

        headers.forEachIndexed { index, header ->
            val h = header.trim().lowercase(Locale.getDefault())
            when {
                isBalanceColumn(h) -> ignored.add(index)
                dateIdx == null && dateHeaders.any { h.contains(it) } -> dateIdx = index
                titleIdx == null && titleHeaders.any { h.contains(it) } -> titleIdx = index
                debitIdx == null && debitHeaders.any { h == it || h.contains("debit") || h.contains("withdrawal") } -> debitIdx = index
                creditIdx == null && creditHeaders.any { h == it || h.contains("credit") || h.contains("deposit") } -> creditIdx = index
                typeIdx == null && typeHeaders.any { h == it || h.contains("cr/dr") } -> typeIdx = index
                categoryIdx == null && categoryHeaders.any { h == it } -> categoryIdx = index
                amountIdx == null && amountHeaders.any { h.contains(it) } -> amountIdx = index
            }
        }

        return StatementColumnIndices(
            dateIndex = dateIdx,
            titleIndex = titleIdx,
            debitIndex = debitIdx,
            creditIndex = creditIdx,
            amountIndex = amountIdx,
            typeIndex = typeIdx,
            categoryIndex = categoryIdx,
            ignoredIndices = ignored
        )
    }

    /**
     * Normalizes transaction amount and direction (Income vs Expense).
     * Also inspects narration text for /DR/ or /CR/ or "Interest Cr." / "Int.Cr" indicators.
     */
    fun normalizeAmount(
        debitVal: String?,
        creditVal: String?,
        amountVal: String?,
        typeVal: String?,
        title: String? = null
    ): Long? {
        val cleanDebit = debitVal?.trim()?.takeIf { it.isNotBlank() }
        val cleanCredit = creditVal?.trim()?.takeIf { it.isNotBlank() }

        val debitPaise = cleanDebit?.let { CSVParser.parseAmountPaise(it) } ?: 0L
        val creditPaise = cleanCredit?.let { CSVParser.parseAmountPaise(it) } ?: 0L

        // Case 1: Separate Debit & Credit columns
        if (Math.abs(debitPaise) > 0L && Math.abs(creditPaise) == 0L) {
            return -Math.abs(debitPaise)
        }
        if (Math.abs(creditPaise) > 0L && Math.abs(debitPaise) == 0L) {
            return Math.abs(creditPaise)
        }
        if (Math.abs(debitPaise) > 0L && Math.abs(creditPaise) > 0L) {
            return -Math.abs(debitPaise)
        }

        // Case 2: Single Amount column with optional Type indicator or Title /DR/ /CR/ marker
        val cleanAmount = amountVal?.trim()?.takeIf { it.isNotBlank() }
        if (cleanAmount != null) {
            var paise = CSVParser.parseAmountPaise(cleanAmount) ?: return null
            if (paise == 0L) return null

            val t = (typeVal ?: title ?: "").trim().lowercase(Locale.getDefault())

            if (t.contains("/dr/") || t.contains(" dr") || t.contains("[dr]") || t.contains("debit") || t.contains("out") || t.contains("interest dr")) {
                paise = -Math.abs(paise)
            } else if (t.contains("/cr/") || t.contains(" cr") || t.contains("[cr]") || t.contains("credit") || t.contains("in") || t.contains("interest cr")) {
                paise = Math.abs(paise)
            }
            return paise
        }

        return null
    }
}
