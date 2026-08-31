package com.vaibhav.moneytracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    val type: String = "Cash",

    val openingBalancePaise: Long = 0L,

    val isActive: Boolean = true,

    val createdAt: Long = System.currentTimeMillis()
)