package com.vaibhav.moneytracker.cloud

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SyncLogEntity): Long

    @Query("SELECT * FROM sync_logs ORDER BY id ASC")
    suspend fun getAll(): List<SyncLogEntity>

    @Query("SELECT * FROM sync_logs ORDER BY id ASC")
    fun getAllFlow(): Flow<List<SyncLogEntity>>

    @Query("SELECT * FROM sync_logs WHERE entityType = :entityType AND entityId = :entityId LIMIT 1")
    suspend fun getByEntity(entityType: String, entityId: Long): SyncLogEntity?

    @Query("DELETE FROM sync_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_logs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM sync_logs")
    suspend fun deleteAll()

    @Transaction
    suspend fun insertOrCoalesce(log: SyncLogEntity): Long {
        val existing = getByEntity(log.entityType, log.entityId)
        if (existing == null) {
            return insert(log)
        }

        return when {
            // CREATE + UPDATE -> Keep CREATE with latest timestamp
            existing.action == "CREATE" && log.action == "UPDATE" -> {
                insert(existing.copy(timestampMs = log.timestampMs))
            }
            // CREATE + DELETE -> Remove pending queue entry (item never reached cloud)
            existing.action == "CREATE" && log.action == "DELETE" -> {
                deleteById(existing.id)
                0L
            }
            // UPDATE + UPDATE -> Keep UPDATE with latest timestamp
            existing.action == "UPDATE" && log.action == "UPDATE" -> {
                insert(existing.copy(timestampMs = log.timestampMs))
            }
            // UPDATE + DELETE -> Replace UPDATE with DELETE tombstone
            existing.action == "UPDATE" && log.action == "DELETE" -> {
                insert(existing.copy(action = "DELETE", timestampMs = log.timestampMs))
            }
            else -> insert(log)
        }
    }
}
