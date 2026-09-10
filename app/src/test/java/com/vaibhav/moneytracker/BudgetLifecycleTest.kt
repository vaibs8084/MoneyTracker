package com.vaibhav.moneytracker

import com.vaibhav.moneytracker.cloud.SyncLogEntity
import org.junit.Assert.*
import org.junit.Test

class BudgetLifecycleTest {

    @Test
    fun testBudgetCreationModel() {
        val budget = BudgetEntity(
            id = 1,
            categoryId = 2L,
            limitPaise = 500000L, // Rs. 5,000.00
            period = "MONTHLY",
            isActive = true
        )

        assertEquals(1L, budget.id)
        assertEquals(2L, budget.categoryId)
        assertEquals(500000L, budget.limitPaise)
        assertEquals("MONTHLY", budget.period)
        assertTrue(budget.isActive)
    }

    @Test
    fun testBudgetLimitValidation() {
        val validLimitText = "5000"
        val zeroLimitText = "0"
        val negativeLimitText = "-500"
        val invalidText = "abc"

        assertTrue((validLimitText.trim().toLongOrNull() ?: 0L) > 0L)
        assertFalse((zeroLimitText.trim().toLongOrNull() ?: 0L) > 0L)
        assertFalse((negativeLimitText.trim().toLongOrNull() ?: 0L) > 0L)
        assertFalse((invalidText.trim().toLongOrNull() ?: 0L) > 0L)
    }

    @Test
    fun testBudgetUsageProgressAndRemaining() {
        val limitPaise = 500000L // Rs. 5,000.00
        val usageHealthyPaise = 200000L // Rs. 2,000.00
        val usageOverPaise = 600000L // Rs. 6,000.00

        // Healthy case
        val pctHealthy = usageHealthyPaise.toFloat() / limitPaise.toFloat()
        val remainingPaise = limitPaise - usageHealthyPaise
        assertEquals(0.40f, pctHealthy, 0.01f)
        assertEquals(300000L, remainingPaise)

        // Over budget case
        val pctOver = usageOverPaise.toFloat() / limitPaise.toFloat()
        val exceededPaise = usageOverPaise - limitPaise
        assertEquals(1.20f, pctOver, 0.01f)
        assertEquals(100000L, exceededPaise)
    }

    @Test
    fun testBudgetEditing() {
        val initial = BudgetEntity(id = 2, categoryId = 1L, limitPaise = 500000L)
        val edited = initial.copy(categoryId = 3L, limitPaise = 800000L)

        assertEquals(2L, edited.id)
        assertEquals(3L, edited.categoryId)
        assertEquals(800000L, edited.limitPaise)
    }

    @Test
    fun testBudgetDeletionLogTombstone() {
        val log = SyncLogEntity(
            entityType = "BUDGET",
            entityId = 2L,
            action = "DELETE",
            timestampMs = System.currentTimeMillis()
        )

        assertEquals("BUDGET", log.entityType)
        assertEquals(2L, log.entityId)
        assertEquals("DELETE", log.action)
    }
}
