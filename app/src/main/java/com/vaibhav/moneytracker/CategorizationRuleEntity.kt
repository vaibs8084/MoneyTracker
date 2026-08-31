package com.vaibhav.moneytracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categorization_rules")
data class CategorizationRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val titlePattern: String, // Contains-match, case-insensitive
    val targetCategoryId: Long? = null,
    val isActive: Boolean = true
)
