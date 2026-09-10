package com.vaibhav.moneytracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Delete
    suspend fun delete(tag: TagEntity)

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveFlow(): kotlinx.coroutines.flow.Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE isActive = 1 ORDER BY name ASC")
    suspend fun getAllActive(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): TagEntity?

    @Query("DELETE FROM tags")
    suspend fun deleteAll()
}
