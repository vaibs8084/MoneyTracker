package com.vaibhav.moneytracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface CategorizationRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: CategorizationRuleEntity): Long

    @Update
    suspend fun update(rule: CategorizationRuleEntity)

    @Delete
    suspend fun delete(rule: CategorizationRuleEntity)

    @Query("SELECT * FROM categorization_rules WHERE isActive = 1")
    suspend fun getAllActive(): List<CategorizationRuleEntity>

    @Transaction
    @Query("SELECT * FROM categorization_rules WHERE isActive = 1")
    fun getAllActiveWithTagsFlow(): kotlinx.coroutines.flow.Flow<List<RuleWithTags>>

    @Transaction
    @Query("SELECT * FROM categorization_rules WHERE isActive = 1")
    suspend fun getAllActiveWithTags(): List<RuleWithTags>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTagRef(ref: RuleTagCrossRef)

    @Query("DELETE FROM rule_tag_cross_ref WHERE ruleId = :ruleId")
    suspend fun deleteTagsForRule(ruleId: Long)

    @Query("DELETE FROM categorization_rules")
    suspend fun deleteAll()

    @Query("DELETE FROM rule_tag_cross_ref")
    suspend fun deleteAllTagRefs()
}
