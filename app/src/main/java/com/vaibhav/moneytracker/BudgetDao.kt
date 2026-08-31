package com.vaibhav.moneytracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE isActive = 1")
    fun getAllActiveFlow(): kotlinx.coroutines.flow.Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE isActive = 1")
    suspend fun getAllActive(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId LIMIT 1")
    suspend fun getByCategoryId(categoryId: Long): BudgetEntity?
}
