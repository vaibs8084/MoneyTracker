package com.vaibhav.moneytracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.moneytracker.AccountEntity
import com.vaibhav.moneytracker.CategoryEntity
import com.vaibhav.moneytracker.DashboardPeriod
import com.vaibhav.moneytracker.preferences.ThemeMode
import com.vaibhav.moneytracker.preferences.UserPreferencesManager

@Composable
fun SettingsScreen(
    modifier: Modifier,
    userPreferencesManager: UserPreferencesManager,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onNavigateToProfile: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToTags: () -> Unit,
    onBack: () -> Unit
) {
    val settingsState by userPreferencesManager.settingsState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "‹",
                    fontSize = 38.sp,
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(end = 12.dp)
                )
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. APPEARANCE
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "🎨 Appearance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("App Theme", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = settingsState.themeMode == ThemeMode.SYSTEM,
                                onClick = { userPreferencesManager.setThemeMode(ThemeMode.SYSTEM) },
                                label = { Text("System") }
                            )
                            FilterChip(
                                selected = settingsState.themeMode == ThemeMode.LIGHT,
                                onClick = { userPreferencesManager.setThemeMode(ThemeMode.LIGHT) },
                                label = { Text("Light") }
                            )
                            FilterChip(
                                selected = settingsState.themeMode == ThemeMode.DARK,
                                onClick = { userPreferencesManager.setThemeMode(ThemeMode.DARK) },
                                label = { Text("Dark") }
                            )
                        }
                    }
                }
            }

            // 2. MONEY & DISPLAY
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "💱 Money & Display",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Currency Symbol", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = settingsState.currencySymbol == "₹",
                                    onClick = { userPreferencesManager.setCurrency("₹", "INR") },
                                    label = { Text("INR — ₹") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = settingsState.currencySymbol == "$",
                                    onClick = { userPreferencesManager.setCurrency("$", "USD") },
                                    label = { Text("USD — $") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = settingsState.currencySymbol == "€",
                                    onClick = { userPreferencesManager.setCurrency("€", "EUR") },
                                    label = { Text("EUR — €") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = settingsState.currencySymbol == "£",
                                    onClick = { userPreferencesManager.setCurrency("£", "GBP") },
                                    label = { Text("GBP — £") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Show Decimals", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Display .00 in amounts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settingsState.showDecimals,
                                onCheckedChange = { userPreferencesManager.setDecimalVisibility(it) }
                            )
                        }
                    }
                }
            }

            // 3. FINANCIAL DEFAULTS
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "⚙️ Financial Defaults",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Default Transaction Account", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(6.dp))

                        val activeAccounts = accounts.filter { it.isActive }
                        val currentAccountName = activeAccounts.find { it.id == settingsState.defaultAccountId }?.name ?: "Not set"

                        Text(
                            text = "Current: $currentAccountName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = settingsState.defaultAccountId == null,
                                    onClick = { userPreferencesManager.setDefaultAccount(null) },
                                    label = { Text("None") }
                                )
                            }
                            items(activeAccounts) { acc ->
                                FilterChip(
                                    selected = settingsState.defaultAccountId == acc.id,
                                    onClick = { userPreferencesManager.setDefaultAccount(acc.id) },
                                    label = { Text(acc.name) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("Default Transaction Category", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(6.dp))

                        val activeCategories = categories.filter { it.isActive }
                        val currentCatName = activeCategories.find { it.id == settingsState.defaultCategoryId }?.name ?: "Not set"

                        Text(
                            text = "Current: $currentCatName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = settingsState.defaultCategoryId == null,
                                    onClick = { userPreferencesManager.setDefaultCategory(null) },
                                    label = { Text("None") }
                                )
                            }
                            items(activeCategories) { cat ->
                                FilterChip(
                                    selected = settingsState.defaultCategoryId == cat.id,
                                    onClick = { userPreferencesManager.setDefaultCategory(cat.id) },
                                    label = { Text(cat.name) }
                                )
                            }
                        }
                    }
                }
            }

            // 4. DASHBOARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "📊 Dashboard Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Default Dashboard Time Period", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DashboardPeriod.entries) { period ->
                                val label = when (period) {
                                    DashboardPeriod.THIS_MONTH -> "This Month"
                                    DashboardPeriod.LAST_MONTH -> "Last Month"
                                    DashboardPeriod.LAST_3_MONTHS -> "3 Months"
                                    DashboardPeriod.LAST_6_MONTHS -> "6 Months"
                                    DashboardPeriod.THIS_YEAR -> "This Year"
                                    DashboardPeriod.ALL_TIME -> "All Time"
                                }
                                FilterChip(
                                    selected = settingsState.defaultDashboardPeriod == period,
                                    onClick = { userPreferencesManager.setDefaultDashboardPeriod(period) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }

            // 5. PRIVACY & SECURITY
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "🔒 Privacy & Security",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Privacy Amount Masking", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("App-wide sensitive amount masking (Coming soon)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "Coming soon",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 6. SHORTCUTS & ABOUT
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "ℹ️ Shortcuts & About",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(onClick = onNavigateToProfile, modifier = Modifier.fillMaxWidth()) {
                            Text("👤 Profile & Cloud Backup Controls")
                        }

                        TextButton(onClick = onNavigateToCategories, modifier = Modifier.fillMaxWidth()) {
                            Text("🏷️ Category Management")
                        }

                        TextButton(onClick = onNavigateToTags, modifier = Modifier.fillMaxWidth()) {
                            Text("🔖 Tag Management")
                        }

                        TextButton(onClick = onNavigateToImport, modifier = Modifier.fillMaxWidth()) {
                            Text("📄 Statement Import (CSV / PDF)")
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Text(
                            text = "Money Tracker v1.0.0",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Offline-First Personal Finance with Private Cloud Backup",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { userPreferencesManager.resetToDefaults() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset Settings to Defaults")
                }
            }
        }
    }
}
