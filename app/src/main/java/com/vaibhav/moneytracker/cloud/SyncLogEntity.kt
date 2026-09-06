package com.vaibhav.moneytracker.cloud

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entityType: String, // "TRANSACTION", "ACCOUNT", "CATEGORY", "TAG", "TRANSACTION_TAG", "SUBSCRIPTION", "BUDGET", "GOAL", "RULE", "RULE_TAG", "CAPTURED"
    val entityId: Long,
    val secondaryId: Long? = null,
    val action: String, // "CREATE", "UPDATE", "DELETE"
    val timestampMs: Long = System.currentTimeMillis()
)
