package com.vaibhav.moneytracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    val limitPaise: Long,
    val period: String = "MONTHLY",
    val isActive: Boolean = true
)
