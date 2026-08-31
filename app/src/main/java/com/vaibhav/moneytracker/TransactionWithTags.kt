package com.vaibhav.moneytracker

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class TransactionWithTags(
    @Embedded
    val transaction: TransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TransactionTagCrossRef::class,
            parentColumn = "transactionId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)
