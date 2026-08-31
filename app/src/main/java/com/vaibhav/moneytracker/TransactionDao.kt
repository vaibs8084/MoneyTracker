package com.vaibhav.moneytracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(
        transaction: TransactionEntity
    ): Long

    @Update
    suspend fun update(
        transaction: TransactionEntity
    )

    @Delete
    suspend fun delete(
        transaction: TransactionEntity
    )

    @Query(
        "SELECT * FROM transactions ORDER BY createdAt DESC"
    )
    suspend fun getAll(): List<TransactionEntity>

    @Query(
        "SELECT * FROM transactions WHERE id = :id LIMIT 1"
    )
    suspend fun getById(
        id: Long
    ): TransactionEntity?

    @Query(
        "DELETE FROM transactions"
    )
    suspend fun deleteAll()

    /*
     * Only real personal income.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0)
        FROM transactions
        WHERE type = 'Income'
        """
    )
    suspend fun getTotalIncome(): Long

    /*
     * Only real personal expenses.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0)
        FROM transactions
        WHERE type = 'Expense'
        """
    )
    suspend fun getTotalExpense(): Long

    /*
     * External money received.
     * NOT personal income.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0)
        FROM transactions
        WHERE type = 'ExternalIn'
        """
    )
    suspend fun getTotalExternalIn(): Long

    /*
     * External money paid out.
     * NOT personal expense.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0)
        FROM transactions
        WHERE type = 'ExternalOut'
        """
    )
    suspend fun getTotalExternalOut(): Long


    /* =====================================================
       ACCOUNT SPECIFIC QUERIES
       (Includes legacy support via name matching)
    ===================================================== */

    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0)
        FROM transactions
        WHERE type = 'Income'
        AND (accountId = :accountId OR (accountId IS NULL AND account = :accountName))
        """
    )
    suspend fun getIncomeForAccount(
        accountId: Long,
        accountName: String
    ): Long

    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0)
        FROM transactions
        WHERE type = 'Expense'
        AND (accountId = :accountId OR (accountId IS NULL AND account = :accountName))
        """
    )
    suspend fun getExpenseForAccount(
        accountId: Long,
        accountName: String
    ): Long

    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0)
        FROM transactions
        WHERE type = 'Transfer'
        AND toAccountId = :accountId
        """
    )
    suspend fun getTransfersInForAccount(
        accountId: Long
    ): Long

    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0)
        FROM transactions
        WHERE type = 'Transfer'
        AND fromAccountId = :accountId
        """
    )
    suspend fun getTransfersOutForAccount(
        accountId: Long
    ): Long

    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0)
        FROM transactions
        WHERE type = 'ExternalIn'
        AND (accountId = :accountId OR (accountId IS NULL AND account = :accountName))
        """
    )
    suspend fun getExternalInForAccount(
        accountId: Long,
        accountName: String
    ): Long

    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0)
        FROM transactions
        WHERE type = 'ExternalOut'
        AND (accountId = :accountId OR (accountId IS NULL AND account = :accountName))
        """
    )
    suspend fun getExternalOutForAccount(
        accountId: Long,
        accountName: String
    ): Long

    /* =====================================================
       TAGS & RELATIONSHIPS
    ===================================================== */

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun getAllWithTagsFlow(): kotlinx.coroutines.flow.Flow<List<TransactionWithTags>>

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    suspend fun getAllWithTags(): List<TransactionWithTags>

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getByIdWithTags(id: Long): TransactionWithTags?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertTagRef(ref: TransactionTagCrossRef)

    @Query("DELETE FROM transaction_tag_cross_ref WHERE transactionId = :transactionId")
    suspend fun deleteTagsForTransaction(transactionId: Long)
}
