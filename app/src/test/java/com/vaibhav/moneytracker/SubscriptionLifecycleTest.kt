package com.vaibhav.moneytracker

import com.vaibhav.moneytracker.cloud.SyncLogEntity
import org.junit.Assert.*
import org.junit.Test

class SubscriptionLifecycleTest {

    @Test
    fun testManualSubscriptionCreationModel() {
        val sub = SubscriptionEntity(
            id = 10,
            name = "Netflix Premium",
            amountPaise = 64900L, // Rs. 649.00
            cadence = "MONTHLY",
            nextDate = 1788500000000L,
            categoryId = 1L,
            accountId = 2L,
            isConfirmed = true,
            isActive = true
        )

        assertEquals("Netflix Premium", sub.name)
        assertEquals(64900L, sub.amountPaise)
        assertEquals("MONTHLY", sub.cadence)
        assertTrue(sub.isConfirmed)
        assertTrue(sub.isActive)
    }

    @Test
    fun testSubscriptionAmountValidation() {
        val validAmountText = "649"
        val zeroAmountText = "0"
        val negativeAmountText = "-100"
        val invalidText = "abc"

        assertTrue((validAmountText.trim().toLongOrNull() ?: 0L) > 0L)
        assertFalse((zeroAmountText.trim().toLongOrNull() ?: 0L) > 0L)
        assertFalse((negativeAmountText.trim().toLongOrNull() ?: 0L) > 0L)
        assertFalse((invalidText.trim().toLongOrNull() ?: 0L) > 0L)
    }

    @Test
    fun testAdvanceSubscriptionDateCadence() {
        val baseDate = 1788500000000L

        val nextMonthly = IntelligenceEngine.advanceSubscriptionDate(baseDate, "MONTHLY")
        assertTrue("Monthly advance must increase nextDate", nextMonthly > baseDate)

        val nextWeekly = IntelligenceEngine.advanceSubscriptionDate(baseDate, "WEEKLY")
        assertTrue("Weekly advance must increase nextDate", nextWeekly > baseDate)
        assertTrue("Weekly advance must be earlier than monthly advance", nextWeekly < nextMonthly)

        val nextYearly = IntelligenceEngine.advanceSubscriptionDate(baseDate, "YEARLY")
        assertTrue("Yearly advance must be further than monthly advance", nextYearly > nextMonthly)
    }

    @Test
    fun testSubscriptionPauseAndReactivate() {
        val sub = SubscriptionEntity(
            id = 5,
            name = "Spotify",
            amountPaise = 11900L,
            cadence = "MONTHLY",
            nextDate = 1788500000000L,
            isActive = true
        )

        val paused = sub.copy(isActive = false)
        assertFalse(paused.isActive)

        val reactivated = paused.copy(isActive = true)
        assertTrue(reactivated.isActive)
        assertEquals(sub.id, reactivated.id)
    }

    @Test
    fun testSubscriptionDeletionLogTombstone() {
        val log = SyncLogEntity(
            entityType = "SUBSCRIPTION",
            entityId = 5,
            action = "DELETE",
            timestampMs = System.currentTimeMillis()
        )

        assertEquals("SUBSCRIPTION", log.entityType)
        assertEquals(5L, log.entityId)
        assertEquals("DELETE", log.action)
    }
}
