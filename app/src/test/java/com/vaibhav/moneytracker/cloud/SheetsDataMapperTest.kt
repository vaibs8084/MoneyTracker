package com.vaibhav.moneytracker.cloud

import com.vaibhav.moneytracker.*
import org.junit.Assert.*
import org.junit.Test

class SheetsDataMapperTest {

    @Test
    fun testTransactionRoundTripMapping() {
        val tx = TransactionEntity(
            id = 101,
            title = "Swiggy Lunch",
            category = "Food",
            account = "Kotak Bank",
            type = "Expense",
            amountPaise = 35000L, // Rs. 350.00
            note = "Office Lunch",
            createdAt = 1788500000000L,
            accountId = 1L,
            categoryId = 2L,
            externalMoneyKind = null
        )

        val row = SheetsDataMapper.transactionToRow(tx, isDeleted = false, updatedAtMs = 1788500005000L)
        assertEquals(13, row.size)
        assertEquals("101", row[0])
        assertEquals("Swiggy Lunch", row[1])
        assertEquals("35000", row[5])
        assertEquals("FALSE", row[11])
        assertEquals("1788500005000", row[12])

        val stringRow = row.map { it.toString() }
        val restored = SheetsDataMapper.rowToTransaction(stringRow)

        assertNotNull("Restored transaction must not be null", restored)
        assertEquals(tx.id, restored!!.id)
        assertEquals(tx.title, restored.title)
        assertEquals(tx.amountPaise, restored.amountPaise)
        assertEquals(tx.accountId, restored.accountId)
        assertEquals(tx.categoryId, restored.categoryId)
    }

    @Test
    fun testAccountRoundTripMapping() {
        val acc = AccountEntity(
            id = 5,
            name = "HDFC Bank",
            type = "Bank",
            openingBalancePaise = 2500000L, // Rs 25,000.00
            isActive = true,
            createdAt = 1788500000000L
        )

        val row = SheetsDataMapper.accountToRow(acc, isDeleted = false, updatedAtMs = 1788500005000L)
        assertEquals("5", row[0])
        assertEquals("HDFC Bank", row[1])
        assertEquals("2500000", row[3])
        assertEquals("TRUE", row[4])

        val stringRow = row.map { it.toString() }
        val restored = SheetsDataMapper.rowToAccount(stringRow)

        assertNotNull(restored)
        assertEquals(acc.id, restored!!.id)
        assertEquals(acc.name, restored.name)
        assertEquals(acc.openingBalancePaise, restored.openingBalancePaise)
        assertTrue(restored.isActive)
    }

    @Test
    fun testRuleTagCrossRefRoundTripMapping() {
        val ref = RuleTagCrossRef(ruleId = 12, tagId = 34)
        val row = SheetsDataMapper.ruleTagToRow(ref)
        assertEquals("12", row[0])
        assertEquals("34", row[1])

        val stringRow = row.map { it.toString() }
        val restored = SheetsDataMapper.rowToRuleTag(stringRow)

        assertNotNull(restored)
        assertEquals(12L, restored!!.ruleId)
        assertEquals(34L, restored.tagId)
    }

    @Test
    fun testMalformedRowReturnsNullWithoutCrashing() {
        val malformedRow = listOf("INVALID_ID", "Incomplete Row")
        val restoredTx = SheetsDataMapper.rowToTransaction(malformedRow)
        assertNull("Malformed row must return null safely", restoredTx)

        val malformedAcc = SheetsDataMapper.rowToAccount(malformedRow)
        assertNull(malformedAcc)
    }
}
