package com.vaibhav.moneytracker

/**
 * UI-friendly representation of a transaction.
 */
data class TransactionUiModel(
    val id: Long,
    val title: String,
    val category: String,
    val account: String,
    val type: String,
    val amountPaise: Long,
    val note: String,
    val createdAt: Long,
    val accountId: Long? = null,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val externalMoneyKind: String? = null,
    val tags: List<TagEntity> = emptyList()
)
