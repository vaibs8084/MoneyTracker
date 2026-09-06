package com.vaibhav.moneytracker.cloud

import org.junit.Assert.*
import org.junit.Test

class SyncLogDaoTest {

    @Test
    fun testSyncLogEntityFields() {
        val log = SyncLogEntity(
            id = 1,
            entityType = "TRANSACTION",
            entityId = 101,
            secondaryId = null,
            action = "CREATE",
            timestampMs = 1788500000000L
        )

        assertEquals(1L, log.id)
        assertEquals("TRANSACTION", log.entityType)
        assertEquals(101L, log.entityId)
        assertEquals("CREATE", log.action)
        assertEquals(1788500000000L, log.timestampMs)
    }

    @Test
    fun testRuleTagsTabPresentInRequiredTabs() {
        assertTrue("RuleTags tab must be present in REQUIRED_TABS", CloudSpreadsheetManager.REQUIRED_TABS.contains("RuleTags"))
        val ruleTagHeaders = CloudSpreadsheetManager.TAB_HEADERS["RuleTags"]
        assertNotNull("RuleTags headers must be defined", ruleTagHeaders)
        assertEquals(2, ruleTagHeaders!!.size)
        assertEquals("RuleID", ruleTagHeaders[0])
        assertEquals("TagId", ruleTagHeaders[1])
    }

    @Test
    fun testRequiredTabsCountAndOrder() {
        assertEquals(12, CloudSpreadsheetManager.REQUIRED_TABS.size)
        assertEquals("Sync Metadata", CloudSpreadsheetManager.REQUIRED_TABS[0])
        assertEquals("RuleTags", CloudSpreadsheetManager.REQUIRED_TABS[10])
        assertEquals("CapturedInbox", CloudSpreadsheetManager.REQUIRED_TABS[11])
    }
}
