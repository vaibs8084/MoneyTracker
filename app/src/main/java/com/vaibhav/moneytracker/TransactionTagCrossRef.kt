package com.vaibhav.moneytracker

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "transaction_tag_cross_ref",
    primaryKeys = ["transactionId", "tagId"],
    indices = [Index(value = ["tagId"])]
)
data class TransactionTagCrossRef(
    val transactionId: Long,
    val tagId: Long
)
