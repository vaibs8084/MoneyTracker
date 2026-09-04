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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onTransactionClick: (TransactionUiModel) -> Unit,
    onDeleteTransactions: (List<Long>, onComplete: () -> Unit) -> Unit = { _, onComplete -> onComplete() }
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedTransactionIds = remember { mutableStateListOf<Long>() }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    // Stable keys in remember ensure filtered list updates INSTANTLY when transactions list changes in ViewModel/Room
    val filteredTransactions by remember(
        transactions, filterPeriod, filterType, filterExternalKind,
        filterAccountId, filterCategory, filterTagId, searchQuery
    ) {
        derivedStateOf {
            val start = getPeriodStartTimestamp(filterPeriod)
            val end = getPeriodEndTimestamp(filterPeriod)

            transactions.filter { tx ->
                val inPeriod = tx.createdAt in start..end

                val matchesType = when (filterType) {
                    "All" -> true
                    "Income" -> tx.type == "Income"
                    "Expense" -> tx.type == "Expense"
                    "Transfer" -> tx.type == "Transfer"
                    "External" -> tx.type == "ExternalIn" || tx.type == "ExternalOut"
                    else -> true
                }

                val matchesExternalKind = if (filterExternalKind == null) true else {
                    tx.externalMoneyKind == filterExternalKind
                }

                val matchesAccount = if (filterAccountId == null) true else {
                    val account = accounts.find { it.id == filterAccountId }
                    tx.accountId == filterAccountId || (tx.accountId == null && tx.account == account?.name)
                }

                val matchesCategory = if (filterCategory == "All") true else tx.category == filterCategory

                val matchesTag = if (filterTagId == null) true else tx.tags.any { it.id == filterTagId }

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

    val groupedTransactions by remember(filteredTransactions) {
        derivedStateOf {
            filteredTransactions.groupBy { tx ->
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                sdf.format(Date(tx.createdAt))
            }
        }
    }

    val availableCategories by remember(transactions) {
        derivedStateOf {
            listOf("All") + transactions.map { it.category }.distinct().sorted()
        }
    }

    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirmationDialog = false },
            title = { Text("Delete Transactions?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete ${selectedTransactionIds.size} selected transaction(s)? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        val idsToDelete = selectedTransactionIds.toList()
                        onDeleteTransactions(idsToDelete) {
                            selectedTransactionIds.clear()
                            isSelectionMode = false
                            isDeleting = false
                            showDeleteConfirmationDialog = false
                        }
                    },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isDeleting) "Deleting..." else "Delete (${selectedTransactionIds.size})")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmationDialog = false },
                    enabled = !isDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
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
                if (isSelectionMode) {
                    // Bulk Selection Action Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Selected (${selectedTransactionIds.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (selectedTransactionIds.size == filteredTransactions.size) {
                                        selectedTransactionIds.clear()
                                    } else {
                                        selectedTransactionIds.clear()
                                        selectedTransactionIds.addAll(filteredTransactions.map { it.id })
                                    }
                                }
                            ) {
                                Text(if (selectedTransactionIds.size == filteredTransactions.size) "Deselect All" else "Select All")
                            }
                            Button(
                                onClick = { showDeleteConfirmationDialog = true },
                                enabled = selectedTransactionIds.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Delete (${selectedTransactionIds.size})")
                            }
                            TextButton(
                                onClick = {
                                    isSelectionMode = false
                                    selectedTransactionIds.clear()
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                } else {
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
                        val isSelected = selectedTransactionIds.contains(transaction.id)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedTransactionIds.add(transaction.id)
                                        else selectedTransactionIds.remove(transaction.id)
                                    }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                TransactionRow(
                                    transaction = transaction,
                                    onClick = {
                                        if (isSelectionMode) {
                                            if (isSelected) selectedTransactionIds.remove(transaction.id)
                                            else selectedTransactionIds.add(transaction.id)
                                        } else {
                                            onTransactionClick(transaction)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedTransactionIds.add(transaction.id)
                                        } else {
                                            if (isSelected) selectedTransactionIds.remove(transaction.id)
                                            else selectedTransactionIds.add(transaction.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}
