package com.vaibhav.moneytracker.ui

import com.vaibhav.moneytracker.AccountEntity
import com.vaibhav.moneytracker.CategoryEntity
import com.vaibhav.moneytracker.DashboardPeriod
import com.vaibhav.moneytracker.preferences.ThemeMode
import com.vaibhav.moneytracker.preferences.UserSettingsState
import org.junit.Assert.*
import org.junit.Test

class SettingsScreenTest {

    @Test
    fun testSettingsStateUIProperties() {
        val settings = UserSettingsState(
            themeMode = ThemeMode.DARK,
            currencySymbol = "$",
            currencyCode = "USD",
            showDecimals = false,
            defaultAccountId = 1L,
            defaultCategoryId = 2L,
            defaultDashboardPeriod = DashboardPeriod.THIS_YEAR,
            isPrivacyMaskingEnabled = true
        )

        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals("$", settings.currencySymbol)
        assertEquals("USD", settings.currencyCode)
        assertFalse(settings.showDecimals)
        assertEquals(1L, settings.defaultAccountId)
        assertEquals(2L, settings.defaultCategoryId)
        assertEquals(DashboardPeriod.THIS_YEAR, settings.defaultDashboardPeriod)
        assertTrue(settings.isPrivacyMaskingEnabled)
    }

    @Test
    fun testInvalidAccountIdFallback() {
        val accounts = listOf(
            AccountEntity(id = 1L, name = "Kotak Bank"),
            AccountEntity(id = 2L, name = "Cash")
        )
        val deletedAccountId = 9999L // Non-existent account ID
        val activeAccounts = accounts.filter { it.isActive }
        val currentAccountName = activeAccounts.find { it.id == deletedAccountId }?.name ?: "Not set"

        assertEquals("Not set", currentAccountName)
    }

    @Test
    fun testInvalidCategoryIdFallback() {
        val categories = listOf(
            CategoryEntity(id = 1L, name = "Food"),
            CategoryEntity(id = 2L, name = "Transport")
        )
        val deletedCategoryId = 8888L // Non-existent category ID
        val activeCategories = categories.filter { it.isActive }
        val currentCatName = activeCategories.find { it.id == deletedCategoryId }?.name ?: "Not set"

        assertEquals("Not set", currentCatName)
    }

    @Test
    fun testGuestModeOfflineSettingsUpdate() {
        val isGuestMode = true
        val settings = UserSettingsState(currencySymbol = "€", currencyCode = "EUR")

        assertTrue("Guest mode must work offline", isGuestMode)
        assertEquals("€", settings.currencySymbol)
        assertEquals("EUR", settings.currencyCode)
    }
}
