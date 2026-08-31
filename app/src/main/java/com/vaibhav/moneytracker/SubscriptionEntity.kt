package com.vaibhav.moneytracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val amountPaise: Long,
    val cadence: String, // MONTHLY, WEEKLY, YEARLY
    val nextDate: Long,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val isConfirmed: Boolean = false,
    val isActive: Boolean = true
)
