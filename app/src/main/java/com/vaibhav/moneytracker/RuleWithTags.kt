package com.vaibhav.moneytracker

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class RuleWithTags(
    @Embedded
    val rule: CategorizationRuleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RuleTagCrossRef::class,
            parentColumn = "ruleId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)
