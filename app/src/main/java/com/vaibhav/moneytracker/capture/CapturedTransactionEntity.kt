package com.vaibhav.moneytracker.capture

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "captured_transactions")
data class CapturedTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amountPaise: Long,
    val type: String, // "Expense", "Income", "Transfer"
    val createdAt: Long,
    val sourceApp: String, // Package name or app label e.g., "PhonePe", "Kotak Bank"
    val referenceNumber: String? = null,
    val suggestedAccountId: Long? = null,
    val suggestedCategoryId: Long? = null,
    val status: String = "PENDING" // "PENDING", "APPROVED", "REJECTED"
)
