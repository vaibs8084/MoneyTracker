package com.vaibhav.moneytracker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaibhav.moneytracker.AccountEntity
import com.vaibhav.moneytracker.DashboardPeriod
import com.vaibhav.moneytracker.TagEntity
import com.vaibhav.moneytracker.TransactionUiModel
import com.vaibhav.moneytracker.getPeriodEndTimestamp
import com.vaibhav.moneytracker.getPeriodStartTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    modifier: Modifier,
    transactions: List<TransactionUiModel>,
    accounts: List<AccountEntity>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filterType: String,
    onFilterTypeChange: (String) -> Unit,
    filterAccountId: Long?,
    onFilterAccountIdChange: (Long?) -> Unit,
    filterCategory: String,
    onFilterCategoryChange: (String) -> Unit,
    filterTagId: Long?,
    onFilterTagIdChange: (Long?) -> Unit,
    filterPeriod: DashboardPeriod,
    onFilterPeriodChange: (DashboardPeriod) -> Unit,
    filterExternalKind: String?,
    onFilterExternalKindChange: (String?) -> Unit,
    tags: List<TagEntity>,
    onTransactionClick: (TransactionUiModel) -> Unit
) {
    val filteredTransactions by remember {
        derivedStateOf {
            val start = getPeriodStartTimestamp(filterPeriod)
            val end = getPeriodEndTimestamp(filterPeriod)

            transactions.filter { tx ->
                // Period
                val inPeriod = tx.createdAt in start..end

                // Type
                val matchesType = when (filterType) {
                    "All" -> true
                    "Income" -> tx.type == "Income"
                    "Expense" -> tx.type == "Expense"
                    "Transfer" -> tx.type == "Transfer"
                    "External" -> tx.type == "ExternalIn" || tx.type == "ExternalOut"
                    else -> true
                }

                // External Kind
                val matchesExternalKind = if (filterExternalKind == null) true else {
                    tx.externalMoneyKind == filterExternalKind
                }

                // Account
                val matchesAccount = if (filterAccountId == null) true else {
                    val account = accounts.find { it.id == filterAccountId }
                    tx.accountId == filterAccountId || (tx.accountId == null && tx.account == account?.name)
                }

                // Category
                val matchesCategory = if (filterCategory == "All") true else tx.category == filterCategory

                // Tag
                val matchesTag = if (filterTagId == null) true else tx.tags.any { it.id == filterTagId }

                // Search
                val matchesSearch = if (searchQuery.isBlank()) true else {
                    tx.title.contains(searchQuery, ignoreCase = true) ||
                    tx.note.contains(searchQuery, ignoreCase = true) ||
                    tx.category.contains(searchQuery, ignoreCase = true) ||
                    tx.account.contains(searchQuery, ignoreCase = true) ||
                    tx.tags.any { it.name.contains(searchQuery, ignoreCase = true) }
                }

                inPeriod && matchesType && matchesExternalKind && matchesAccount && matchesCategory && matchesTag && matchesSearch
            }
        }
    }

    val groupedTransactions by remember {
        derivedStateOf {
            filteredTransactions.groupBy { tx ->
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                sdf.format(Date(tx.createdAt))
            }
        }
    }

    val availableCategories by remember {
        derivedStateOf {
            listOf("All") + transactions.map { it.category }.distinct().sorted()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Sticky Search & Filter Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("Search transactions...") },
                    leadingIcon = { Text("🔍", modifier = Modifier.padding(start = 8.dp)) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        { Text("✕", modifier = Modifier.clickable { onSearchQueryChange("") }.padding(end = 8.dp)) }
                    } else null,
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                // Filter Rows
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChipGroup(
                        title = "Type",
                        options = listOf("All", "Income", "Expense", "Transfer", "External"),
                        selectedOption = filterType,
                        onOptionSelected = onFilterTypeChange
                    )

                    FilterChipGroup(
                        title = "Account",
                        options = listOf("All") + accounts.map { it.name },
                        selectedOption = accounts.find { it.id == filterAccountId }?.name ?: "All",
                        onOptionSelected = { name ->
                            if (name == "All") onFilterAccountIdChange(null)
                            else onFilterAccountIdChange(accounts.find { it.name == name }?.id)
                        }
                    )

                    FilterChipGroup(
                        title = "Category",
                        options = availableCategories,
                        selectedOption = filterCategory,
                        onOptionSelected = onFilterCategoryChange
                    )

                    FilterChipGroup(
                        title = "Tag",
                        options = listOf("All") + tags.map { it.name },
                        selectedOption = tags.find { it.id == filterTagId }?.name ?: "All",
                        onOptionSelected = { name ->
                            if (name == "All") onFilterTagIdChange(null)
                            else onFilterTagIdChange(tags.find { it.name == name }?.id)
                        }
                    )

                    FilterChipGroup(
                        title = "Period",
                        options = DashboardPeriod.entries.map { it.label },
                        selectedOption = filterPeriod.label,
                        onOptionSelected = { label ->
                            onFilterPeriodChange(DashboardPeriod.entries.find { it.label == label } ?: DashboardPeriod.ALL_TIME)
                        }
                    )

                    FilterChipGroup(
                        title = "External",
                        options = listOf("All", "Held", "Receivable", "Liability"),
                        selectedOption = filterExternalKind ?: "All",
                        onOptionSelected = {
                            if (it == "All") onFilterExternalKindChange(null)
                            else onFilterExternalKindChange(it)
                        }
                    )
                }
            }
        }

        // Transaction List
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading History...")
            }
        } else if (filteredTransactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No transactions found", fontWeight = FontWeight.Bold)
                    if (searchQuery.isNotEmpty() || filterType != "All" || filterAccountId != null) {
                        Text("Try adjusting your filters", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                groupedTransactions.forEach { (date, txs) ->
                    stickyHeader {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                    items(txs, key = { it.id }) { transaction ->
                        TransactionRow(
                            transaction = transaction,
                            onClick = { onTransactionClick(transaction) }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}
