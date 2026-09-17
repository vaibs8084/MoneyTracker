package com.vaibhav.moneytracker.intelligence

import com.vaibhav.moneytracker.TransactionUiModel
import java.util.Locale

data class UnusualSpendingPattern(
    val transaction: TransactionUiModel,
    val merchantName: String,
    val transactionAmountPaise: Long,
    val historicalAveragePaise: Long,
    val multiplier: Double,
    val explanation: String
)

object PatternDetector {

    /**
     * Detects transactions that deviate significantly (>= 2x) from the user's historical average for that merchant.
     * Requires at least 2 prior transactions for that merchant to establish a baseline.
     */
    fun detectUnusualMerchantSpending(
        transactions: List<TransactionUiModel>,
        currentTx: TransactionUiModel
    ): UnusualSpendingPattern? {
        if (currentTx.type != "Expense" || currentTx.amountPaise <= 0L) return null

        val merchant = MerchantNormalizer.normalize(currentTx.title)
        if (merchant.isBlank() || merchant == "Unclassified") return null

        val merchantHistory = transactions.filter {
            it.id != currentTx.id &&
                    it.type == "Expense" &&
                    MerchantNormalizer.normalize(it.title).equals(merchant, ignoreCase = true)
        }

        if (merchantHistory.size < 2) return null

        val avgPaise = merchantHistory.sumOf { it.amountPaise } / merchantHistory.size.toLong()
        if (avgPaise <= 0L) return null

        val multiplier = currentTx.amountPaise.toDouble() / avgPaise.toDouble()
        if (multiplier >= 2.0) {
            return UnusualSpendingPattern(
                transaction = currentTx,
                merchantName = merchant,
                transactionAmountPaise = currentTx.amountPaise,
                historicalAveragePaise = avgPaise,
                multiplier = multiplier,
                explanation = "Amount is ${String.format(Locale.US, "%.1f", multiplier)}x higher than your average $merchant expense"
            )
        }

        return null
    }

    /**
     * Detects high category concentration (> 40% of total expenses in a single category).
     */
    fun detectCategoryConcentration(spendingAnalysis: SpendingAnalysis): SpendingCategoryShare? {
        if (spendingAnalysis.totalExpensePaise <= 0L) return null
        val top = spendingAnalysis.topCategories.firstOrNull() ?: return null
        return if (top.percentage >= 40.0) top else null
    }
}
