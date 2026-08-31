package com.vaibhav.moneytracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val targetPaise: Long,
    val manualProgressPaise: Long = 0L,
    val targetDate: Long? = null,
    val linkedAccountId: Long? = null,
    val isCompleted: Boolean = false,
    val isActive: Boolean = true
)
