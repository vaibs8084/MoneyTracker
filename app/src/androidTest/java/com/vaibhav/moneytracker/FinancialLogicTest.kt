package com.vaibhav.moneytracker

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinancialLogicTest {
    private lateinit var db: MoneyTrackerDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var accountDao: AccountDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MoneyTrackerDatabase::class.java).build()
        transactionDao = db.transactionDao()
        accountDao = db.accountDao()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun accountBalance_includesPhysicalExternalMoney_butNetWorthDoesNotTreatItAsPersonal() = runBlocking {
        val account = createAccount("Test Bank", 10_000L)
        insertTransaction("Income", 5_000L, account = account)
        insertTransaction("Expense", 2_000L, account = account)
        insertTransaction("ExternalIn", 1_000L, account = account)
        insertTransaction("ExternalOut", 500L, account = account)

        assertEquals(13_500L, FinancialEngine.calculateAccountBalance(db, account))
        assertEquals(13_000L, FinancialEngine.calculateNetWorth(db))
        assertEquals(1_000L, transactionDao.getExternalInForAccount(account.id, account.name))
        assertEquals(500L, transactionDao.getExternalOutForAccount(account.id, account.name))
    }

    @Test
    fun twoAccountTransfer_updatesBothBalances_andIsNeutralToNetWorth() = runBlocking {
        val kotak = createAccount("Kotak", 10_000L)
        val cash = createAccount("Cash")
        insertTransaction("Transfer", 3_000L, fromAccountId = kotak.id, toAccountId = cash.id)

        assertEquals(7_000L, FinancialEngine.calculateAccountBalance(db, kotak))
        assertEquals(3_000L, FinancialEngine.calculateAccountBalance(db, cash))
        assertEquals(10_000L, FinancialEngine.calculateNetWorth(db))
    }

    @Test
    fun selfTransfer_isRejectedByUiBoundary_butDirectDaoInsertHasNoBalanceEffect() = runBlocking {
        val account = createAccount("Kotak", 10_000L)

        // The add and edit screens reject equal source/destination IDs. Room has no equivalent constraint.
        insertTransaction("Transfer", 3_000L, fromAccountId = account.id, toAccountId = account.id)

        assertEquals(10_000L, FinancialEngine.calculateAccountBalance(db, account))
        assertEquals(10_000L, FinancialEngine.calculateNetWorth(db))
    }

    @Test
    fun legacyNullAccountId_usesAccountNameFallback_forIncomeAndExpense() = runBlocking {
        val account = createAccount("Cash", 1_000L)
        insertTransaction("Income", 700L, accountName = "Cash")
        insertTransaction("Expense", 250L, accountName = "Cash")

        assertEquals(1_450L, FinancialEngine.calculateAccountBalance(db, account))
        val legacy = transactionDao.getAll().first { it.type == "Income" }
        assertNull(legacy.accountId)
        assertNull(legacy.fromAccountId)
        assertNull(legacy.toAccountId)
    }

    @Test
    fun deactivatedAccount_isHiddenFromActiveAccounts_butRemainsInPersonalNetWorth() = runBlocking {
        val active = createAccount("Cash", 1_000L)
        val inactive = createAccount("Old Bank", 2_000L)
        accountDao.deactivate(inactive.id)

        assertEquals(listOf(active.id), accountDao.getAll().map { it.id })
        // Deactivation is not disposal: historical owned balances remain part of net worth.
        assertEquals(3_000L, FinancialEngine.calculateNetWorth(db))
    }

    @Test
    fun totals_includeOnlyPersonalIncomeAndExpense_notExternalOrTransfers() = runBlocking {
        val account = createAccount("Cash")
        insertTransaction("Income", 8_000L, account = account)
        insertTransaction("Expense", 3_000L, account = account)
        insertTransaction("ExternalIn", 900L, account = account)
        insertTransaction("ExternalOut", 400L, account = account)
        insertTransaction("Transfer", 500L, fromAccountId = account.id, toAccountId = account.id)

        assertEquals(8_000L, transactionDao.getTotalIncome())
        assertEquals(3_000L, transactionDao.getTotalExpense())
        assertEquals(5_000L, FinancialEngine.calculateNetWorth(db))
    }

    @Test
    fun classifiedExternalMoney_tracksHeldReceivablesAndLiabilities_withoutChangingPersonalNetWorth() = runBlocking {
        val account = createAccount("Cash", 10_000L)

        insertTransaction("ExternalIn", 2_000L, account = account, externalMoneyKind = ExternalMoneyKind.HELD)
        insertTransaction("ExternalOut", 500L, account = account, externalMoneyKind = ExternalMoneyKind.HELD)
        insertTransaction("ExternalOut", 3_000L, account = account, externalMoneyKind = ExternalMoneyKind.RECEIVABLE)
        insertTransaction("ExternalIn", 1_000L, account = account, externalMoneyKind = ExternalMoneyKind.RECEIVABLE)
        insertTransaction("ExternalIn", 5_000L, account = account, externalMoneyKind = ExternalMoneyKind.LIABILITY)
        insertTransaction("ExternalOut", 2_000L, account = account, externalMoneyKind = ExternalMoneyKind.LIABILITY)

        val position = FinancialEngine.calculateFinancialPosition(db)

        assertEquals(12_500L, FinancialEngine.calculateAccountBalance(db, account))
        assertEquals(12_500L, position.physicalAccountBalancesPaise)
        assertEquals(1_500L, position.moneyHeldForOthersPaise)
        assertEquals(2_000L, position.receivablesPaise)
        assertEquals(3_000L, position.liabilitiesPaise)
        assertEquals(0L, position.unclassifiedExternalPaise)
        assertEquals(10_000L, position.personalNetWorthPaise)
    }

    @Test
    fun legacyUnclassifiedExternalMoney_isNotReclassified_orCountedAsPersonal() = runBlocking {
        val account = createAccount("Cash", 1_000L)
        val id = insertTransaction("ExternalIn", 900L, account = account)

        val stored = transactionDao.getById(id)!!
        val position = FinancialEngine.calculateFinancialPosition(db)

        assertNull(stored.externalMoneyKind)
        assertEquals(1_900L, FinancialEngine.calculateAccountBalance(db, account))
        assertEquals(900L, position.unclassifiedExternalPaise)
        assertEquals(1_000L, position.personalNetWorthPaise)
    }

    @Test
    fun externalMoneyClassificationValidation_requiresNewEntriesToBeClassified_butPreservesLegacyNull() {
        assertFalse(isExternalMoneyClassificationValid("ExternalIn", null))
        assertFalse(isExternalMoneyClassificationValid("ExternalOut", "Unknown"))
        assertTrue(isExternalMoneyClassificationValid("ExternalIn", ExternalMoneyKind.HELD))
        assertTrue(isExternalMoneyClassificationValid("ExternalOut", ExternalMoneyKind.RECEIVABLE))
        assertTrue(isExternalMoneyClassificationValid("ExternalIn", null, allowsLegacyUnclassified = true))
        assertTrue(isExternalMoneyClassificationValid("Income", null))
    }

    @Test
    fun migration5To6_preservesLegacyTransaction_andLeavesClassificationNull() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "financial-migration-${System.nanoTime()}.db"
        val factory = FrameworkSQLiteOpenHelperFactory()
        val v5 = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL(
                            "CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, category TEXT NOT NULL, account TEXT NOT NULL, type TEXT NOT NULL, amountPaise INTEGER NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL, fromAccountId INTEGER, toAccountId INTEGER, accountId INTEGER, categoryId INTEGER)"
                        )
                    }

                    override fun onUpgrade(
                        database: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        v5.writableDatabase.execSQL(
            "INSERT INTO transactions (title, category, account, type, amountPaise, note, createdAt) VALUES ('Legacy', 'General', 'Cash', 'ExternalIn', 900, '', 1)"
        )
        v5.close()

        val v6 = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(database: androidx.sqlite.db.SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(
                        database: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {
                        MoneyTrackerDatabase.MIGRATION_5_6.migrate(database)
                    }
                })
                .build()
        )
        val cursor = v6.writableDatabase.query("SELECT title, amountPaise, externalMoneyKind FROM transactions")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("Legacy", it.getString(0))
            assertEquals(900L, it.getLong(1))
            assertTrue(it.isNull(2))
        }
        v6.close()
        context.deleteDatabase(name)
    }

    @Test
    fun periodFiltering_countsOnlyIncomeAndExpenseWithinSelectedPeriod() = runBlocking {
        val account = createAccount("Cash")
        val start = getPeriodStartTimestamp(DashboardPeriod.THIS_MONTH)
        val end = getPeriodEndTimestamp(DashboardPeriod.THIS_MONTH)
        insertTransaction("Income", 1_500L, account = account, createdAt = start)
        insertTransaction("Expense", 600L, account = account, createdAt = end)
        insertTransaction("Income", 9_000L, account = account, createdAt = start - 1L)
        insertTransaction("ExternalIn", 2_000L, account = account, createdAt = start)

        val periodTransactions = transactionDao.getAll().filter { it.createdAt in start..end }
        assertEquals(1_500L, periodTransactions.filter { it.type == "Income" }.sumOf { it.amountPaise })
        assertEquals(600L, periodTransactions.filter { it.type == "Expense" }.sumOf { it.amountPaise })
    }

    @Test
    fun editingTransaction_recalculatesAccountBalanceFromUpdatedStoredValues() = runBlocking {
        val account = createAccount("Cash", 1_000L)
        val id = insertTransaction("Expense", 200L, account = account)
        val original = transactionDao.getById(id)!!

        transactionDao.update(original.copy(type = "Income", amountPaise = 500L))

        assertEquals(1_500L, FinancialEngine.calculateAccountBalance(db, account))
        assertEquals(1_500L, FinancialEngine.calculateNetWorth(db))
    }

    @Test
    fun duplicateTransactions_areBothStoredAndBothCounted_currentBehavior() = runBlocking {
        val account = createAccount("Cash")
        val duplicate = TransactionEntity(
            title = "Salary", category = "Work", account = account.name, type = "Income",
            amountPaise = 1_000L, note = "", createdAt = 1234L, accountId = account.id
        )

        transactionDao.insert(duplicate)
        transactionDao.insert(duplicate)

        assertEquals(2, transactionDao.getAll().size)
        assertEquals(2_000L, FinancialEngine.calculateAccountBalance(db, account))
    }

    @Test
    fun roomPersistsNullableLegacyRelationshipColumns_withoutDroppingTransactions() = runBlocking {
        val id = insertTransaction("Expense", 321L, accountName = "Pre-account Cash")

        val stored = transactionDao.getById(id)!!
        assertEquals("Pre-account Cash", stored.account)
        assertNull(stored.accountId)
        assertNull(stored.categoryId)
        assertNull(stored.fromAccountId)
        assertNull(stored.toAccountId)
        assertTrue(transactionDao.getAll().any { it.id == id })
    }

    private suspend fun createAccount(name: String, openingBalancePaise: Long = 0L): AccountEntity {
        val id = accountDao.insert(AccountEntity(name = name, openingBalancePaise = openingBalancePaise))
        return accountDao.getById(id)!!
    }

    private suspend fun insertTransaction(
        type: String,
        amountPaise: Long,
        account: AccountEntity? = null,
        accountName: String = account?.name.orEmpty(),
        fromAccountId: Long? = null,
        toAccountId: Long? = null,
        externalMoneyKind: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ): Long = transactionDao.insert(
        TransactionEntity(
            title = type,
            category = "General",
            account = accountName,
            type = type,
            amountPaise = amountPaise,
            note = "",
            createdAt = createdAt,
            accountId = account?.id,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            externalMoneyKind = externalMoneyKind
        )
    )
}
