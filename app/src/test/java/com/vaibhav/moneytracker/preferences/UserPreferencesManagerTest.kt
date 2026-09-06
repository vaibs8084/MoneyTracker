package com.vaibhav.moneytracker.preferences

import com.vaibhav.moneytracker.DashboardPeriod
import com.vaibhav.moneytracker.formatCurrency
import com.vaibhav.moneytracker.formatRupees
import org.junit.Assert.*
import org.junit.Test

class UserPreferencesManagerTest {

    @Test
    fun testDefaultSettingsStateValues() {
        val state = UserSettingsState()
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
        assertEquals("₹", state.currencySymbol)
        assertEquals("INR", state.currencyCode)
        assertTrue(state.showDecimals)
        assertNull(state.defaultAccountId)
        assertNull(state.defaultCategoryId)
        assertEquals(DashboardPeriod.THIS_MONTH, state.defaultDashboardPeriod)
        assertFalse(state.isPrivacyMaskingEnabled)
    }

    @Test
    fun testExactIntegerCurrencyFormatting() {
        val amountPaise = 50000L // Rs. 500.00 / $500.00

        // INR Formatting
        assertEquals("₹500.00", formatRupees(amountPaise))
        assertEquals("₹500.00", formatCurrency(amountPaise, "₹", true))
        assertEquals("₹500", formatCurrency(amountPaise, "₹", false))

        // USD Formatting
        assertEquals("$500.00", formatCurrency(amountPaise, "$", true))
        assertEquals("$500", formatCurrency(amountPaise, "$", false))

        // EUR Formatting
        assertEquals("€500.00", formatCurrency(amountPaise, "€", true))
        assertEquals("€500", formatCurrency(amountPaise, "€", false))

        // GBP Formatting
        assertEquals("£1,250.50", formatCurrency(125050L, "£", true))

        // INVARIANT: Underlying amount in paise is strictly preserved
        assertEquals(50000L, amountPaise)
    }

    @Test
    fun testCurrencyFormattingEdgeCases() {
        // Zero Amount
        assertEquals("₹0.00", formatCurrency(0L, "₹", true))
        assertEquals("₹0", formatCurrency(0L, "₹", false))

        // Negative Amount
        assertEquals("-₹500.00", formatCurrency(-50000L, "₹", true))
        assertEquals("-$500.00", formatCurrency(-50000L, "$", true))
        assertEquals("-$500", formatCurrency(-50000L, "$", false))

        // Small Paise Amount (< 1 Major Unit)
        assertEquals("₹0.05", formatCurrency(5L, "₹", true))

        // Large Amount (1 Billion Rupees / Dollars)
        assertEquals("₹1,000,000,000.00", formatCurrency(100000000000L, "₹", true))
        assertEquals("₹1,000,000,000", formatCurrency(100000000000L, "₹", false))
    }

    @Test
    fun testStateCopyAndUpdates() {
        val state = UserSettingsState()
        val updated = state.copy(
            themeMode = ThemeMode.DARK,
            currencySymbol = "$",
            currencyCode = "USD",
            showDecimals = false,
            defaultAccountId = 10L,
            isPrivacyMaskingEnabled = true
        )

        assertEquals(ThemeMode.DARK, updated.themeMode)
        assertEquals("$", updated.currencySymbol)
        assertEquals("USD", updated.currencyCode)
        assertFalse(updated.showDecimals)
        assertEquals(10L, updated.defaultAccountId)
        assertTrue(updated.isPrivacyMaskingEnabled)
    }
}
