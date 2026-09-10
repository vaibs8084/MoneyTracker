package com.vaibhav.moneytracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface GoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Delete
    suspend fun delete(goal: GoalEntity)

    @Query("SELECT * FROM goals WHERE isActive = 1 AND isCompleted = 0")
    fun getAllActiveFlow(): kotlinx.coroutines.flow.Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE isActive = 1 AND isCompleted = 0")
    suspend fun getAllActive(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE isCompleted = 1")
    suspend fun getCompleted(): List<GoalEntity>

    @Query("DELETE FROM goals")
    suspend fun deleteAll()
}
