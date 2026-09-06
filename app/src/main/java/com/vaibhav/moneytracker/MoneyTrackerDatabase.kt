package com.vaibhav.moneytracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

import com.vaibhav.moneytracker.capture.CapturedTransactionDao
import com.vaibhav.moneytracker.capture.CapturedTransactionEntity
import com.vaibhav.moneytracker.cloud.SyncLogDao
import com.vaibhav.moneytracker.cloud.SyncLogEntity

@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        TransactionTagCrossRef::class,
        SubscriptionEntity::class,
        BudgetEntity::class,
        GoalEntity::class,
        CategorizationRuleEntity::class,
        RuleTagCrossRef::class,
        CapturedTransactionEntity::class,
        SyncLogEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class MoneyTrackerDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun accountDao(): AccountDao

    abstract fun categoryDao(): CategoryDao

    abstract fun tagDao(): TagDao

    abstract fun subscriptionDao(): SubscriptionDao

    abstract fun budgetDao(): BudgetDao

    abstract fun goalDao(): GoalDao

    abstract fun categorizationRuleDao(): CategorizationRuleDao

    abstract fun capturedTransactionDao(): CapturedTransactionDao

    abstract fun syncLogDao(): SyncLogDao


    companion object {

        @Volatile
        private var INSTANCE:
                MoneyTrackerDatabase? = null


        /*
         * Migration from database version 1 → 2.
         *
         * IMPORTANT:
         * This does NOT delete existing transactions.
         */
        private val MIGRATION_1_2 =
            object : Migration(1, 2) {

                override fun migrate(
                    database: SupportSQLiteDatabase
                ) {

                    /*
                     * Add transfer source.
                     */
                    database.execSQL(
                        """
                        ALTER TABLE transactions
                        ADD COLUMN fromAccountId INTEGER
                        """.trimIndent()
                    )


                    /*
                     * Add transfer destination.
                     */
                    database.execSQL(
                        """
                        ALTER TABLE transactions
                        ADD COLUMN toAccountId INTEGER
                        """.trimIndent()
                    )


                    /*
                     * Create accounts table.
                     */
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS accounts (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            type TEXT NOT NULL,
                            openingBalancePaise INTEGER NOT NULL,
                            isActive INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }
            }


        /*
         * Migration from database version 2 → 3.
         *
         * Adds accountId to transactions for 
         * proper account relationship.
         */
        private val MIGRATION_2_3 =
            object : Migration(2, 3) {

                override fun migrate(
                    database: SupportSQLiteDatabase
                ) {

                    database.execSQL(
                        """
                        ALTER TABLE transactions
                        ADD COLUMN accountId INTEGER
                        """.trimIndent()
                    )
                }
            }


        /*
         * Migration from database version 3 → 4.
         *
         * Adds categoryId and new tables for
         * Categories and Tags.
         */
        private val MIGRATION_3_4 =
            object : Migration(3, 4) {

                override fun migrate(
                    database: SupportSQLiteDatabase
                ) {

                    /* Add categoryId to transactions */
                    database.execSQL(
                        "ALTER TABLE transactions ADD COLUMN categoryId INTEGER"
                    )

                    /* Create categories table */
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS categories (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            icon TEXT,
                            color INTEGER,
                            isActive INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )

                    /* Create tags table */
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS tags (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            isActive INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )

                    /* Create cross ref table */
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS transaction_tag_cross_ref (
                            transactionId INTEGER NOT NULL,
                            tagId INTEGER NOT NULL,
                            PRIMARY KEY(transactionId, tagId)
                        )
                        """.trimIndent()
                    )
                    
                    /* Index for cross ref */
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_transaction_tag_cross_ref_tagId ON transaction_tag_cross_ref (tagId)"
                    )
                }
            }


        /*
         * Migration from database version 4 → 5.
         *
         * Adds Subscriptions, Budgets, Goals, and Rules.
         */
        private val MIGRATION_4_5 =
            object : Migration(4, 5) {

                override fun migrate(
                    database: SupportSQLiteDatabase
                ) {

                    /* Create subscriptions table */
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS subscriptions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            amountPaise INTEGER NOT NULL,
                            cadence TEXT NOT NULL,
                            nextDate INTEGER NOT NULL,
                            categoryId INTEGER,
                            accountId INTEGER,
                            isConfirmed INTEGER NOT NULL,
                            isActive INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )

                    /* Create budgets table */
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS budgets (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            categoryId INTEGER NOT NULL,
                            limitPaise INTEGER NOT NULL,
                            period TEXT NOT NULL,
                            isActive INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )

                    /* Create goals table */
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS goals (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            targetPaise INTEGER NOT NULL,
                            manualProgressPaise INTEGER NOT NULL,
                            targetDate INTEGER,
                            linkedAccountId INTEGER,
                            isCompleted INTEGER NOT NULL,
                            isActive INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )

                    /* Create categorization_rules table */
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS categorization_rules (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            titlePattern TEXT NOT NULL,
                            targetCategoryId INTEGER,
                            isActive INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )

                    /* Create rule_tag_cross_ref table */
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS rule_tag_cross_ref (
                            ruleId INTEGER NOT NULL,
                            tagId INTEGER NOT NULL,
                            PRIMARY KEY(ruleId, tagId)
                        )
                        """.trimIndent()
                    )
                    
                    /* Index for rule_tag_cross_ref */
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_rule_tag_cross_ref_tagId ON rule_tag_cross_ref (tagId)"
                    )
                }
            }

        /**
         * Adds an optional external-money classification without rewriting any
         * existing transaction. Null preserves the historical meaning.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE transactions ADD COLUMN externalMoneyKind TEXT"
                )
            }
        }


        /**
         * Migration from database version 6 → 7.
         * Creates captured_transactions table for Notification Capture Inbox.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS captured_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        amountPaise INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        sourceApp TEXT NOT NULL,
                        referenceNumber TEXT,
                        suggestedAccountId INTEGER,
                        suggestedCategoryId INTEGER,
                        status TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Migration from database version 7 → 8.
         * Creates sync_logs table for offline cloud sync tracking.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId INTEGER NOT NULL,
                        secondaryId INTEGER,
                        action TEXT NOT NULL,
                        timestampMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }


        fun getInstance(
            context: Context
        ): MoneyTrackerDatabase {

            return INSTANCE ?: synchronized(this) {

                INSTANCE ?: Room.databaseBuilder(

                    context.applicationContext,

                    MoneyTrackerDatabase::class.java,

                    "money_tracker_database"

                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8
                    )
                    .build()
                    .also {
                        INSTANCE = it
                    }
            }
        }
    }
}
