package com.vaibhav.moneytracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.vaibhav.moneytracker.csv.CSVParser
import com.vaibhav.moneytracker.csv.PDFStatementParser
import com.vaibhav.moneytracker.csv.StatementFormatDetector
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class BulkImportTest {

    private lateinit var db: MoneyTrackerDatabase
    private lateinit var repository: MoneyRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MoneyTrackerDatabase::class.java).build()
        repository = MoneyRepository(db)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testBulkImportPreservesRepeatedLegitimateTransactions() = runBlocking {
        val accountId = db.accountDao().insert(AccountEntity(name = "Kotak Bank", openingBalancePaise = 500000L))
        val date = System.currentTimeMillis()

        // User confirmed 3 transactions on the same day, including 2 repeated ₹500 transactions
        val items = listOf(
            Pair(TransactionEntity(title = "Coffee Shop", category = "Food", account = "Kotak Bank", type = "Expense", amountPaise = 50000L, createdAt = date, accountId = accountId, note = "Morning"), emptyList<TagEntity>()),
            Pair(TransactionEntity(title = "Coffee Shop", category = "Food", account = "Kotak Bank", type = "Expense", amountPaise = 50000L, createdAt = date, accountId = accountId, note = "Evening"), emptyList()),
            Pair(TransactionEntity(title = "Salary", category = "Income", account = "Kotak Bank", type = "Income", amountPaise = 1000000L, createdAt = date, accountId = accountId, note = "Monthly"), emptyList())
        )

        val result = repository.bulkImportTransactions(items)

        // Verify all 3 transactions were imported (no silent drops of valid repeats)
        assertEquals(3, result.imported)

        val all = db.transactionDao().getAll()
        assertEquals(3, all.size)

        // Verify account balance
        val account = db.accountDao().getById(accountId)
        assertNotNull(account)
        val balance = FinancialEngine.calculateAccountBalance(db, account!!)
        assertEquals(500000L + 1000000L - 50000L - 50000L, balance)
    }

    @Test
    fun testSmartRulesApplication() = runBlocking {
        // Setup Smart Rule
        val foodCatId = db.categoryDao().insert(CategoryEntity(name = "Food & Dining"))
        val ruleId = db.categorizationRuleDao().insert(CategorizationRuleEntity(titlePattern = "Swiggy", targetCategoryId = foodCatId))

        val rules = db.categorizationRuleDao().getAllActiveWithTags()
        val (matchedCategory, matchedTags) = IntelligenceEngine.matchRules("SWIGGY INDIA ONLINE ORDER", rules)

        assertEquals(foodCatId, matchedCategory)
    }

    private fun createEncryptedPdf(password: String): ByteArray {
        val out = ByteArrayOutputStream()
        val doc = PDDocument()
        val page = PDPage()
        doc.addPage(page)

        val contentStream = PDPageContentStream(doc, page)
        contentStream.beginText()
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12f)
        contentStream.newLineAtOffset(50f, 700f)
        contentStream.showText("04/09/2026 UPI/657924401450/DR/AJAY 500.00 24500.00")
        contentStream.newLineAtOffset(0f, -20f)
        contentStream.showText("04/09/2026 UPI/658015506835/CR/VAIB/KKBK 2000.00 26500.00")
        contentStream.endText()
        contentStream.close()

        val ap = AccessPermission()
        val spp = StandardProtectionPolicy("ownerSecretKey123", password, ap)
        spp.encryptionKeyLength = 128
        doc.protect(spp)
        doc.save(out)
        doc.close()

        return out.toByteArray()
    }

    @Test
    fun testRealDevicePdfPasswordFlow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)

        val correctPassword = "KotakPassword@2026"
        val pdfBytes = createEncryptedPdf(correctPassword)

        // Step 1: Initial PDF opening with no password
        val initialResult = PDFStatementParser.parsePdf(context, ByteArrayInputStream(pdfBytes), password = null)
        assertTrue("PDF must be detected as encrypted", initialResult.isEncrypted)
        assertNull("Initial encrypted check must NOT show wrong password error", initialResult.errorMessage)
        assertTrue("Raw lines should be empty until unlocked", initialResult.rawLines.isEmpty())

        // Step 2: Empty/blank password attempt
        val blankResult = PDFStatementParser.parsePdf(context, ByteArrayInputStream(pdfBytes), password = "   ")
        assertTrue("Blank password should still report encrypted", blankResult.isEncrypted)
        assertNull("Blank password should not be treated as wrong password attempt", blankResult.errorMessage)

        // Step 3: Wrong password attempt
        val wrongResult = PDFStatementParser.parsePdf(context, ByteArrayInputStream(pdfBytes), password = "WrongPassword999")
        assertTrue("PDF should remain encrypted on wrong password", wrongResult.isEncrypted)
        assertEquals("Incorrect PDF password. Please try again.", wrongResult.errorMessage)
        assertTrue(wrongResult.rawLines.isEmpty())

        // Step 4: Retry with correct password after failed attempt
        val retryCorrectResult = PDFStatementParser.parsePdf(context, ByteArrayInputStream(pdfBytes), password = correctPassword)
        assertFalse("Correct password must unlock PDF (isEncrypted=false)", retryCorrectResult.isEncrypted)
        assertNull("No error on correct password", retryCorrectResult.errorMessage)
        assertFalse("Scanned flag should be false for digital text", retryCorrectResult.isScanned)
        assertTrue("Lines should be extracted", retryCorrectResult.rawLines.isNotEmpty())
        assertTrue(
            "Extracted lines should contain UPI DR transaction",
            retryCorrectResult.rawLines.any { it.contains("UPI/657924401450/DR/AJAY") }
        )
        assertTrue(
            "Extracted lines should contain UPI CR transaction",
            retryCorrectResult.rawLines.any { it.contains("UPI/658015506835/CR/VAIB/KKBK") }
        )
    }

    @Test
    fun testRealDeviceCsvParsingAndBalanceFiltering() {
        val csvContent = """
            Txn Date,Narration,Withdrawal Amount,Deposit Amount,Closing Balance
            01/09/2026,Brought Forward,,,25000.00
            02/09/2026,Opening Balance,,,25000.00
            03/09/2026,Running Balance,,,25000.00
            03/09/2026,Ledger Balance,,,25000.00
            04/09/2026,UPI/657924401450/DR/AJAY,500.00,,24500.00
            04/09/2026,UPI/658015506835/CR/VAIB/KKBK,,"2,000.00",26500.00
            05/09/2026,Closing Balance,,,26500.00
            05/09/2026,Available Balance,,,26500.00
        """.trimIndent()

        val parsed = CSVParser.parse(csvContent)
        val columns = StatementFormatDetector.detectColumns(parsed.headers)

        assertEquals(0, columns.dateIndex)
        assertEquals(1, columns.titleIndex)
        assertEquals(2, columns.debitIndex)
        assertEquals(3, columns.creditIndex)
        assertTrue(columns.ignoredIndices.contains(4))

        val nonTransactionRows = parsed.rows.filter { row ->
            val title = row[1]
            StatementFormatDetector.isNonTransactionRow(title)
        }

        // Verify that Brought Forward, Opening Balance, Running Balance, Ledger Balance, Closing Balance, Available Balance are ignored
        assertEquals(6, nonTransactionRows.size)

        val transactionRows = parsed.rows.filterNot { row ->
            val title = row[1]
            StatementFormatDetector.isNonTransactionRow(title)
        }

        assertEquals(2, transactionRows.size)

        // Row 1: UPI DR
        val row1 = transactionRows[0]
        val date1 = CSVParser.parseDate(row1[0])
        assertNotNull(date1)
        assertEquals("04 Sep 2026", formatDate(date1!!))
        val amount1 = StatementFormatDetector.normalizeAmount(row1[2], row1[3], null, null, row1[1])
        assertEquals(-50000L, amount1)
        assertEquals("₹500.00", formatRupees(Math.abs(amount1!!)))

        // Row 2: UPI CR
        val row2 = transactionRows[1]
        val date2 = CSVParser.parseDate(row2[0])
        assertNotNull(date2)
        assertEquals("04 Sep 2026", formatDate(date2!!))
        val amount2 = StatementFormatDetector.normalizeAmount(row2[2], row2[3], null, null, row2[1])
        assertEquals(200000L, amount2)
        assertEquals("₹2,000.00", formatRupees(Math.abs(amount2!!)))
    }

    @Test
    fun testAtomicBulkTransactionDeletion() = runBlocking {
        val accountId = db.accountDao().insert(AccountEntity(name = "Kotak Bank", openingBalancePaise = 500000L))
        val foodCatId = db.categoryDao().insert(CategoryEntity(name = "Food"))
        val tagId = db.tagDao().insert(TagEntity(name = "Snacks"))

        val tx1Id = db.transactionDao().insert(TransactionEntity(title = "Tx 1", category = "Food", account = "Kotak Bank", type = "Expense", amountPaise = 10000L, createdAt = System.currentTimeMillis(), accountId = accountId, categoryId = foodCatId, note = ""))
        val tx2Id = db.transactionDao().insert(TransactionEntity(title = "Tx 2", category = "Food", account = "Kotak Bank", type = "Expense", amountPaise = 20000L, createdAt = System.currentTimeMillis(), accountId = accountId, categoryId = foodCatId, note = ""))
        val tx3Id = db.transactionDao().insert(TransactionEntity(title = "Tx 3", category = "Food", account = "Kotak Bank", type = "Expense", amountPaise = 30000L, createdAt = System.currentTimeMillis(), accountId = accountId, categoryId = foodCatId, note = ""))

        db.transactionDao().insertTagRef(TransactionTagCrossRef(tx1Id, tagId))
        db.transactionDao().insertTagRef(TransactionTagCrossRef(tx2Id, tagId))
        db.transactionDao().insertTagRef(TransactionTagCrossRef(tx3Id, tagId))

        // Perform bulk deletion of tx1 and tx2
        repository.deleteTransactions(listOf(tx1Id, tx2Id))

        val remaining = db.transactionDao().getAll()
        assertEquals(1, remaining.size)
        assertEquals(tx3Id, remaining[0].id)

        // Verify remaining tx3 still has its tag relationship intact
        val tx3WithTags = db.transactionDao().getByIdWithTags(tx3Id)
        assertNotNull(tx3WithTags)
        assertEquals(1, tx3WithTags!!.tags.size)
        assertEquals(tagId, tx3WithTags.tags[0].id)
    }
}
