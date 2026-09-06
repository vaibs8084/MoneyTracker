package com.vaibhav.moneytracker.preferences

import android.content.Context
import android.content.SharedPreferences
import com.vaibhav.moneytracker.DashboardPeriod
import com.vaibhav.moneytracker.defaultCurrencySymbol
import com.vaibhav.moneytracker.defaultShowDecimals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settingsState = MutableStateFlow(loadSettingsFromPrefs())
    val settingsState: StateFlow<UserSettingsState> = _settingsState.asStateFlow()

    private fun loadSettingsFromPrefs(): UserSettingsState {
        val themeStr = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val themeMode = try {
            ThemeMode.valueOf(themeStr)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }

        val symbol = prefs.getString(KEY_CURRENCY_SYMBOL, "₹") ?: "₹"
        val code = prefs.getString(KEY_CURRENCY_CODE, "INR") ?: "INR"
        val showDecimals = prefs.getBoolean(KEY_SHOW_DECIMALS, true)

        val defaultAcc = if (prefs.contains(KEY_DEFAULT_ACCOUNT_ID)) {
            prefs.getLong(KEY_DEFAULT_ACCOUNT_ID, -1L).takeIf { it > 0L }
        } else null

        val defaultCat = if (prefs.contains(KEY_DEFAULT_CATEGORY_ID)) {
            prefs.getLong(KEY_DEFAULT_CATEGORY_ID, -1L).takeIf { it > 0L }
        } else null

        val periodStr = prefs.getString(KEY_DEFAULT_PERIOD, DashboardPeriod.THIS_MONTH.name) ?: DashboardPeriod.THIS_MONTH.name
        val dashboardPeriod = try {
            DashboardPeriod.valueOf(periodStr)
        } catch (e: Exception) {
            DashboardPeriod.THIS_MONTH
        }

        val isPrivacyMasked = prefs.getBoolean(KEY_PRIVACY_MASKING, false)

        defaultCurrencySymbol = symbol
        defaultShowDecimals = showDecimals

        return UserSettingsState(
            themeMode = themeMode,
            currencySymbol = symbol,
            currencyCode = code,
            showDecimals = showDecimals,
            defaultAccountId = defaultAcc,
            defaultCategoryId = defaultCat,
            defaultDashboardPeriod = dashboardPeriod,
            isPrivacyMaskingEnabled = isPrivacyMasked
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _settingsState.value = _settingsState.value.copy(themeMode = mode)
    }

    fun setCurrency(symbol: String, code: String) {
        val validSymbol = if (symbol.isNotBlank()) symbol.trim() else "₹"
        val validCode = if (code.isNotBlank()) code.trim().uppercase() else "INR"
        prefs.edit()
            .putString(KEY_CURRENCY_SYMBOL, validSymbol)
            .putString(KEY_CURRENCY_CODE, validCode)
            .apply()
        com.vaibhav.moneytracker.defaultCurrencySymbol = validSymbol
        _settingsState.value = _settingsState.value.copy(
            currencySymbol = validSymbol,
            currencyCode = validCode
        )
    }

    fun setDecimalVisibility(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_DECIMALS, show).apply()
        com.vaibhav.moneytracker.defaultShowDecimals = show
        _settingsState.value = _settingsState.value.copy(showDecimals = show)
    }

    fun setDefaultAccount(accountId: Long?) {
        if (accountId != null && accountId > 0L) {
            prefs.edit().putLong(KEY_DEFAULT_ACCOUNT_ID, accountId).apply()
        } else {
            prefs.edit().remove(KEY_DEFAULT_ACCOUNT_ID).apply()
        }
        _settingsState.value = _settingsState.value.copy(defaultAccountId = accountId?.takeIf { it > 0L })
    }

    fun setDefaultCategory(categoryId: Long?) {
        if (categoryId != null && categoryId > 0L) {
            prefs.edit().putLong(KEY_DEFAULT_CATEGORY_ID, categoryId).apply()
        } else {
            prefs.edit().remove(KEY_DEFAULT_CATEGORY_ID).apply()
        }
        _settingsState.value = _settingsState.value.copy(defaultCategoryId = categoryId?.takeIf { it > 0L })
    }

    fun setDefaultDashboardPeriod(period: DashboardPeriod) {
        prefs.edit().putString(KEY_DEFAULT_PERIOD, period.name).apply()
        _settingsState.value = _settingsState.value.copy(defaultDashboardPeriod = period)
    }

    fun setPrivacyMasking(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVACY_MASKING, enabled).apply()
        _settingsState.value = _settingsState.value.copy(isPrivacyMaskingEnabled = enabled)
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
        defaultCurrencySymbol = "₹"
        defaultShowDecimals = true
        _settingsState.value = UserSettingsState()
    }

    companion object {
        private const val PREFS_NAME = "money_tracker_user_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_CURRENCY_SYMBOL = "currency_symbol"
        private const val KEY_CURRENCY_CODE = "currency_code"
        private const val KEY_SHOW_DECIMALS = "show_decimals"
        private const val KEY_DEFAULT_ACCOUNT_ID = "default_account_id"
        private const val KEY_DEFAULT_CATEGORY_ID = "default_category_id"
        private const val KEY_DEFAULT_PERIOD = "default_dashboard_period"
        private const val KEY_PRIVACY_MASKING = "is_privacy_masking_enabled"
    }
}
