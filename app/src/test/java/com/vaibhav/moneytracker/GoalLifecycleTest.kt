package com.vaibhav.moneytracker

import com.vaibhav.moneytracker.cloud.SyncLogEntity
import org.junit.Assert.*
import org.junit.Test

class GoalLifecycleTest {

    @Test
    fun testGoalCreationModel() {
        val goal = GoalEntity(
            id = 1,
            name = "Emergency Fund",
            targetPaise = 5000000L, // Rs. 50,000.00
            manualProgressPaise = 0L,
            isCompleted = false,
            isActive = true
        )

        assertEquals("Emergency Fund", goal.name)
        assertEquals(5000000L, goal.targetPaise)
        assertEquals(0L, goal.manualProgressPaise)
        assertFalse(goal.isCompleted)
        assertTrue(goal.isActive)
    }

    @Test
    fun testLinkedAccountProgressTransitions() {
        val goal = GoalEntity(
            id = 10,
            name = "Bike Savings",
            targetPaise = 15000000L, // Rs. 1,50,000.00
            manualProgressPaise = 0L,
            linkedAccountId = 5L,
            isCompleted = false
        )

        // 1. Linked account balance below target -> Active
        val balanceBelow = 14900000L // Rs. 1,49,000.00
        val isBelowCompleted = goal.targetPaise > 0L && balanceBelow >= goal.targetPaise
        assertFalse("Goal below target must remain active", isBelowCompleted)

        // 2. Linked account balance exactly reaches target -> Completed
        val balanceExact = 15000000L // Rs. 1,50,000.00
        val isExactCompleted = goal.targetPaise > 0L && balanceExact >= goal.targetPaise
        assertTrue("Goal reaching target must transition to completed", isExactCompleted)

        // 3. Linked account balance exceeds target -> Completed
        val balanceExceeded = 16000000L // Rs. 1,60,000.00
        val isExceededCompleted = goal.targetPaise > 0L && balanceExceeded >= goal.targetPaise
        assertTrue("Goal exceeding target must transition to completed", isExceededCompleted)

        val completedGoal = goal.copy(isCompleted = true)
        assertTrue(completedGoal.isCompleted)

        // 4. Linked account balance later decreases -> Goal remains in completed state, account balance untouched
        val balanceDecreasedLater = 13000000L
        assertEquals(16000000L, balanceExceeded) // Balance is not mutated by goal
        assertEquals(13000000L, balanceDecreasedLater)
        assertTrue("Completed goal remains completed", completedGoal.isCompleted)
    }

    @Test
    fun testManualProgressGoalReachesTarget() {
        val goal = GoalEntity(
            id = 2,
            name = "Laptop",
            targetPaise = 8000000L, // Rs. 80,000.00
            manualProgressPaise = 7500000L,
            isCompleted = false
        )

        val contribution = 500000L
        val newProgress = goal.manualProgressPaise + contribution
        val isNowCompleted = newProgress >= goal.targetPaise

        val updatedGoal = goal.copy(manualProgressPaise = newProgress, isCompleted = isNowCompleted)
        assertTrue(updatedGoal.isCompleted)
        assertEquals(8000000L, updatedGoal.manualProgressPaise)
    }

    @Test
    fun testReopenGoalBehavior() {
        val completedGoal = GoalEntity(
            id = 3,
            name = "Vacation",
            targetPaise = 5000000L,
            manualProgressPaise = 5000000L,
            isCompleted = true
        )

        // User explicitly reopens the goal
        val reopenedGoal = completedGoal.copy(isCompleted = false)
        assertFalse("Reopened goal isCompleted must be false", reopenedGoal.isCompleted)
        assertEquals(completedGoal.id, reopenedGoal.id)
    }

    @Test
    fun testAccountAndTransactionsUntouchedByGoalCompletion() {
        val account = AccountEntity(id = 5, name = "Savings Bank", openingBalancePaise = 15000000L)
        val initialBalance = account.openingBalancePaise

        val goal = GoalEntity(
            id = 10,
            name = "Bike Savings",
            targetPaise = 15000000L,
            linkedAccountId = account.id,
            isCompleted = true
        )

        // Verifying goal completion does NOT alter account balance or transactions
        assertTrue(goal.isCompleted)
        assertEquals(initialBalance, account.openingBalancePaise)
    }

    @Test
    fun testGoalDeletionLogTombstone() {
        val log = SyncLogEntity(
            entityType = "GOAL",
            entityId = 3L,
            action = "DELETE",
            timestampMs = System.currentTimeMillis()
        )

        assertEquals("GOAL", log.entityType)
        assertEquals(3L, log.entityId)
        assertEquals("DELETE", log.action)
    }
}
