package com.vaibhav.moneytracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,

    val category: String,

    /*
     * Main/display account.
     *
     * Existing transactions continue using this field.
     */
    val account: String,

    /*
     * Transaction types:
     *
     * Income
     * Expense
     * Transfer
     * ExternalIn
     * ExternalOut
     */
    val type: String,

    /*
     * Amount stored in paise.
     */
    val amountPaise: Long,

    val note: String,

    val createdAt: Long,

    /*
     * Used only for transfers.
     *
     * Nullable so all existing transactions
     * remain valid.
     */
    val fromAccountId: Long? = null,

    val toAccountId: Long? = null,

    /*
     * Relationship to the account system.
     *
     * Nullable for legacy transactions.
     */
    val accountId: Long? = null,

    /*
     * Relationship to the category system.
     *
     * Nullable for legacy transactions.
     */
    val categoryId: Long? = null,
    /*
     * Optional meaning for an external cash movement. A null value is the
     * historical/unclassified external-money meaning and is deliberately not
     * inferred during migration.
     *
     * See ExternalMoneyKind for the supported values.
     */
    val externalMoneyKind: String? = null
)
