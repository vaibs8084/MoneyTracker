package com.vaibhav.moneytracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FinancialEngine {

    /**
     * Calculates the current balance of an account based on its opening balance
     * and all related transactions.
     *
     * Formula:
     * Balance = Opening + Income + ExternalIn + TransfersIn
     *           - Expense - ExternalOut - TransfersOut
     *
     * This is a physical account balance. External money is physically in (or
     * out of) an account even though it is not necessarily personal money.
     */
    suspend fun calculateAccountBalance(
        database: MoneyTrackerDatabase,
        account: AccountEntity
    ): Long = withContext(Dispatchers.IO) {
        val dao = database.transactionDao()
        
        val income = dao.getIncomeForAccount(account.id, account.name)
        val expense = dao.getExpenseForAccount(account.id, account.name)
        val transfersIn = dao.getTransfersInForAccount(account.id)
        val transfersOut = dao.getTransfersOutForAccount(account.id)

        val externalIn = dao.getExternalInForAccount(account.id, account.name)
        val externalOut = dao.getExternalOutForAccount(account.id, account.name)

        account.openingBalancePaise + income + externalIn + transfersIn -
            expense - externalOut - transfersOut
    }

    /**
     * Calculates ownership-adjusted personal net worth. Deactivated accounts
     * remain included because deactivation hides an account from selection; it
     * does not dispose of its money or historical transactions.
     */
    suspend fun calculateNetWorth(
        database: MoneyTrackerDatabase
    ): Long = withContext(Dispatchers.IO) {
        calculateFinancialPosition(database).personalNetWorthPaise
    }

    /**
     * Canonical balance-sheet calculation.
     *
     * External movements always affect physical cash. Their classification
     * supplies the offset: held money is excluded, lending creates a
     * receivable, and borrowing creates a liability. Unclassified legacy
     * external movements are also offset rather than being guessed.
     */
    suspend fun calculateFinancialPosition(
        database: MoneyTrackerDatabase
    ): FinancialPosition = withContext(Dispatchers.IO) {
        val transactions = database.transactionDao().getAll()
        val accounts = database.accountDao().getAllIncludingInactive()
        calculateFinancialPosition(transactions, accounts)
    }

    /**
     * Pure function to calculate financial position from lists.
     */
    fun calculateFinancialPosition(
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>
    ): FinancialPosition {
        val totalOpening = accounts.sumOf { it.openingBalancePaise }

        var physical = totalOpening
        var held = 0L
        var receivables = 0L
        var liabilities = 0L
        var unclassifiedExternal = 0L

        transactions.forEach { transaction ->
            when (transaction.type) {
                "Income" -> physical += transaction.amountPaise
                "Expense" -> physical -= transaction.amountPaise
                "Transfer" -> Unit // Transfers are neutral across owned accounts.
                "ExternalIn" -> {
                    physical += transaction.amountPaise
                    when (transaction.externalMoneyKind) {
                        ExternalMoneyKind.HELD -> held += transaction.amountPaise
                        ExternalMoneyKind.RECEIVABLE -> receivables -= transaction.amountPaise
                        ExternalMoneyKind.LIABILITY -> liabilities += transaction.amountPaise
                        else -> unclassifiedExternal += transaction.amountPaise
                    }
                }
                "ExternalOut" -> {
                    physical -= transaction.amountPaise
                    when (transaction.externalMoneyKind) {
                        ExternalMoneyKind.HELD -> held -= transaction.amountPaise
                        ExternalMoneyKind.RECEIVABLE -> receivables += transaction.amountPaise
                        ExternalMoneyKind.LIABILITY -> liabilities -= transaction.amountPaise
                        else -> unclassifiedExternal -= transaction.amountPaise
                    }
                }
            }
        }

        return FinancialPosition(
            physicalAccountBalancesPaise = physical,
            moneyHeldForOthersPaise = held,
            receivablesPaise = receivables,
            liabilitiesPaise = liabilities,
            unclassifiedExternalPaise = unclassifiedExternal,
            personalNetWorthPaise = physical - held + receivables - liabilities - unclassifiedExternal
        )
    }
}
