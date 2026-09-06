package com.vaibhav.moneytracker.preferences

import com.vaibhav.moneytracker.DashboardPeriod

data class UserSettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val currencySymbol: String = "₹",
    val currencyCode: String = "INR",
    val showDecimals: Boolean = true,
    val defaultAccountId: Long? = null,
    val defaultCategoryId: Long? = null,
    val defaultDashboardPeriod: DashboardPeriod = DashboardPeriod.THIS_MONTH,
    val isPrivacyMaskingEnabled: Boolean = false
)
