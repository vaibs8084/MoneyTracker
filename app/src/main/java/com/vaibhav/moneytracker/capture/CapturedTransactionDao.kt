package com.vaibhav.moneytracker.capture

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedTransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(captured: CapturedTransactionEntity): Long

    @Update
    suspend fun update(captured: CapturedTransactionEntity)

    @Delete
    suspend fun delete(captured: CapturedTransactionEntity)

    @Query("SELECT * FROM captured_transactions WHERE status = 'PENDING' ORDER BY createdAt DESC")
    fun getPendingCapturesFlow(): Flow<List<CapturedTransactionEntity>>

    @Query("SELECT * FROM captured_transactions WHERE status = 'PENDING' ORDER BY createdAt DESC")
    suspend fun getPendingCaptures(): List<CapturedTransactionEntity>

    @Query("SELECT COUNT(*) FROM captured_transactions WHERE status = 'PENDING'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT * FROM captured_transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CapturedTransactionEntity?

    @Query("UPDATE captured_transactions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("SELECT * FROM captured_transactions WHERE referenceNumber = :refNumber AND status = 'PENDING' LIMIT 1")
    suspend fun getByReferenceNumber(refNumber: String): CapturedTransactionEntity?

    @Query("DELETE FROM captured_transactions")
    suspend fun deleteAll()
}
