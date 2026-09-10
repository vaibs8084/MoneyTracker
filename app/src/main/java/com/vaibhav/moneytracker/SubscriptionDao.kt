package com.vaibhav.moneytracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SubscriptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: SubscriptionEntity): Long

    @Update
    suspend fun update(subscription: SubscriptionEntity)

    @Delete
    suspend fun delete(subscription: SubscriptionEntity)

    @Query("SELECT * FROM subscriptions WHERE isActive = 1")
    suspend fun getAllActive(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE isConfirmed = 1 AND isActive = 1")
    fun getConfirmedSubscriptionsFlow(): kotlinx.coroutines.flow.Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE isConfirmed = 1 AND isActive = 1")
    suspend fun getConfirmedSubscriptions(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE isConfirmed = 0 AND isActive = 1")
    suspend fun getSuggestions(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): SubscriptionEntity?

    @Query("DELETE FROM subscriptions")
    suspend fun deleteAll()
}
