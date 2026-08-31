package com.vaibhav.moneytracker

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "rule_tag_cross_ref",
    primaryKeys = ["ruleId", "tagId"],
    indices = [Index(value = ["tagId"])]
)
data class RuleTagCrossRef(
    val ruleId: Long,
    val tagId: Long
)
