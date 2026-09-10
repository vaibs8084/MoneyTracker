package com.vaibhav.moneytracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AccountDao {

    @Insert
    suspend fun insert(
        account: AccountEntity
    ): Long

    @Update
    suspend fun update(
        account: AccountEntity
    )

    @Delete
    suspend fun delete(
        account: AccountEntity
    )

    @Query(
        "SELECT * FROM accounts " +
                "WHERE isActive = 1 " +
                "ORDER BY id ASC"
    )
    fun getAllFlow(): kotlinx.coroutines.flow.Flow<List<AccountEntity>>

    @Query(
        "SELECT * FROM accounts " +
                "WHERE isActive = 1 " +
                "ORDER BY id ASC"
    )
    suspend fun getAll(): List<AccountEntity>

    /** Historical/deactivated accounts remain part of ownership calculations. */
    @Query("SELECT * FROM accounts ORDER BY id ASC")
    fun getAllIncludingInactiveFlow(): kotlinx.coroutines.flow.Flow<List<AccountEntity>>

    /** Historical/deactivated accounts remain part of ownership calculations. */
    @Query("SELECT * FROM accounts ORDER BY id ASC")
    suspend fun getAllIncludingInactive(): List<AccountEntity>

    @Query(
        "SELECT * FROM accounts " +
                "WHERE id = :id " +
                "LIMIT 1"
    )
    suspend fun getById(
        id: Long
    ): AccountEntity?

    @Query(
        "SELECT COUNT(*) FROM accounts"
    )
    suspend fun getCount(): Int

    @Query(
        "SELECT * FROM accounts " +
                "WHERE name = :name " +
                "LIMIT 1"
    )
    suspend fun getByName(
        name: String
    ): AccountEntity?

    @Query(
        "UPDATE accounts " +
                "SET isActive = 0 " +
                "WHERE id = :id"
    )
    suspend fun deactivate(
        id: Long
    )

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}
