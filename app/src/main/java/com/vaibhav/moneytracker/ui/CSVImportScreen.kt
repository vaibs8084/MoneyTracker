package com.vaibhav.moneytracker.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.moneytracker.*
import com.vaibhav.moneytracker.csv.ParsedCSV

@Composable
fun CSVImportScreen(
    modifier: Modifier,
    viewModel: CSVImportViewModel,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    rules: List<RuleWithTags>,
    onBack: () -> Unit,
    onImportSuccess: () -> Unit = onBack
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val columnMapping by viewModel.columnMapping.collectAsState()
    val targetAccountId by viewModel.targetAccountId.collectAsState()

    BackHandler {
        viewModel.reset()
        onBack()
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.loadStatement(context, it, password = null, rules = rules, accounts = accounts, categories = categories)
        }
    }

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
                    modifier = Modifier.clickable { onBack() }.padding(end = 12.dp)
                )
                Text(text = "Statement Import", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (val s = state) {
                is CSVImportViewModel.ImportState.Idle -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "📂", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Select a CSV or PDF Bank Statement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = "Supports auto-detection of columns & password PDFs", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { fileLauncher.launch("*/*") },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Choose CSV / PDF File")
                        }
                    }
                }

                is CSVImportViewModel.ImportState.PasswordRequired -> {
                    PasswordDialog(
                        uri = s.uri,
                        errorMessage = s.errorMessage,
                        onUnlock = { pwd ->
                            viewModel.loadStatement(context, s.uri, password = pwd, rules = rules, accounts = accounts, categories = categories)
                        },
                        onDismiss = { viewModel.reset() }
                    )
                }

                is CSVImportViewModel.ImportState.Mapping -> {
                    MappingStep(
                        parsed = ParsedCSV(s.headers, s.rows, ','),
                        mapping = columnMapping,
                        targetAccountId = targetAccountId,
                        accounts = accounts,
                        onMappingChange = { field, index -> viewModel.updateMapping(field, index) },
                        onAccountSelect = { viewModel.setTargetAccount(it, accounts) },
                        onContinue = { viewModel.generateManualPreview(rules, accounts, categories) }
                    )
                }

                is CSVImportViewModel.ImportState.Preview -> {
                    PreviewStep(
                        items = s.items,
                        counts = s.counts,
                        balances = s.balances,
                        accounts = accounts,
                        targetAccountId = targetAccountId,
                        categories = categories,
                        onAccountSelect = { viewModel.setTargetAccount(it, accounts) },
                        onItemChange = { index, tx, tags -> viewModel.updatePreviewItem(index, tx, tags) },
                        onReclassifyItem = { index, cat -> viewModel.reclassifyItem(index, cat) },
                        onRemoveItem = { index -> viewModel.removePreviewItem(index) },
                        onToggleImportAnyway = { index -> viewModel.toggleImportAnyway(index) },
                        onImport = { viewModel.performImport() },
                        onBack = { viewModel.reset() },
                        onSwitchToAdvanced = { viewModel.switchToAdvancedMapping() }
                    )
                }

                is CSVImportViewModel.ImportState.Success -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "✅", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Import Successful!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Imported: ${s.imported} transactions", style = MaterialTheme.typography.bodyLarge)
                        if (s.duplicates > 0) {
                            Text(text = "Potential matches flagged: ${s.duplicates}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                viewModel.reset()
                                onImportSuccess()
                            },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Done")
                        }
                    }
                }

                is CSVImportViewModel.ImportState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "❌", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Import Error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.reset() }, shape = RoundedCornerShape(16.dp)) {
                            Text("Try Again")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordDialog(
    uri: Uri,
    errorMessage: String?,
    onUnlock: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var passwordText by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            passwordText = ""
            onDismiss()
        },
        title = {
            Text(text = "Password Protected PDF", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "This PDF statement is encrypted. Please enter the password to extract transactions.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                OutlinedTextField(
                    value = passwordText,
                    onValueChange = { passwordText = it },
                    label = { Text("PDF Password") },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        Text(
                            text = if (isPasswordVisible) "Hide" else "Show",
                            modifier = Modifier
                                .clickable { isPasswordVisible = !isPasswordVisible }
                                .padding(8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pwd = passwordText
                    passwordText = ""
                    onUnlock(pwd)
                },
                enabled = passwordText.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Unlock PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                passwordText = ""
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MappingStep(
    parsed: ParsedCSV,
    mapping: Map<String, Int>,
    targetAccountId: Long?,
    accounts: List<AccountEntity>,
    onMappingChange: (String, Int) -> Unit,
    onAccountSelect: (Long) -> Unit,
    onContinue: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(text = "1. Select Destination Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            AccountSelectionList(
                accounts = accounts,
                selectedAccount = accounts.find { it.id == targetAccountId },
                onAccountSelected = { onAccountSelect(it.id) }
            )
        }

        item {
            Text(text = "2. Map Statement Columns", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = "Running balance columns are automatically ignored.", style = MaterialTheme.typography.bodySmall)
        }

        val fields = listOf("Date", "Title", "Amount", "Category", "Type")
        items(fields) { field ->
            Column {
                Text(text = field, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(parsed.headers.size) { index ->
                        val header = parsed.headers[index]
                        SelectionChip(
                            text = header,
                            selected = mapping[field] == index,
                            onClick = { onMappingChange(field, index) }
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                enabled = mapping.containsKey("Date") && mapping.containsKey("Title") && mapping.containsKey("Amount") && targetAccountId != null,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Generate Preview")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PreviewStep(
    items: List<CSVImportViewModel.ImportPreviewItem>,
    counts: CSVImportViewModel.ImportCounts,
    balances: CSVImportViewModel.StatementBalances,
    accounts: List<AccountEntity>,
    targetAccountId: Long?,
    categories: List<CategoryEntity>,
    onAccountSelect: (Long) -> Unit,
    onItemChange: (Int, TransactionEntity, List<TagEntity>) -> Unit,
    onReclassifyItem: (Int, String) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onToggleImportAnyway: (Int) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    onSwitchToAdvanced: () -> Unit
) {
    var selectedTab by remember { mutableStateOf("All") }

    val filteredItems = when (selectedTab) {
        "Ready" -> items.filter { it.isValid && !it.isAmbiguous && !it.isStatementInfo }
        "Review" -> items.filter { (!it.isValid || it.isAmbiguous) && !it.isStatementInfo }
        "Ignored" -> items.filter { !it.isValid && !it.isAmbiguous && !it.isStatementInfo }
        "Info" -> items.filter { it.isStatementInfo }
        else -> items.filter { !it.isStatementInfo }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(text = "Statement Preview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // Statement Balance Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = "Statement Balance", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(text = "Opening Balance", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = if (balances.openingBalancePaise != null) formatRupees(balances.openingBalancePaise) else "—",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Closing Balance", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = if (balances.closingBalancePaise != null) formatRupees(balances.closingBalancePaise) else "—",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Tabs Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                SelectionChip(
                    text = "All (${counts.totalDetected})",
                    selected = selectedTab == "All",
                    onClick = { selectedTab = "All" }
                )
            }
            item {
                SelectionChip(
                    text = "Ready (${counts.readyToImport})",
                    selected = selectedTab == "Ready",
                    onClick = { selectedTab = "Ready" }
                )
            }
            item {
                SelectionChip(
                    text = "Review (${counts.needsReview})",
                    selected = selectedTab == "Review",
                    onClick = { selectedTab = "Review" }
                )
            }
            item {
                SelectionChip(
                    text = "Ignored (${counts.ignoredCount})",
                    selected = selectedTab == "Ignored",
                    onClick = { selectedTab = "Ignored" }
                )
            }
            if (counts.infoCount > 0) {
                item {
                    SelectionChip(
                        text = "Info (${counts.infoCount})",
                        selected = selectedTab == "Info",
                        onClick = { selectedTab = "Info" }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Account Selector
        Text(text = "Destination Account", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(accounts) { acc ->
                SelectionChip(
                    text = acc.name,
                    selected = acc.id == targetAccountId,
                    onClick = { onAccountSelect(acc.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Rows ($selectedTab: ${filteredItems.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Advanced Mapping",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onSwitchToAdvanced() }.padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No items in category '$selectedTab'",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(filteredItems) { index, previewItem ->
                    var showCategoryMenu by remember { mutableStateOf(false) }
                    var showMoveMenu by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                previewItem.isStatementInfo -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                !previewItem.isValid -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                previewItem.isAmbiguous -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (previewItem.transaction.createdAt > 0L) formatDate(previewItem.transaction.createdAt) else "Date unavailable",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )

                                val isIncome = previewItem.transaction.type == "Income"
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isIncome) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = if (isIncome) "↑ Income" else "↓ Expense",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isIncome) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = previewItem.transaction.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val amountText = if (previewItem.transaction.amountPaise > 0L) {
                                    (if (previewItem.transaction.type == "Income") "+ " else "- ") + formatRupees(previewItem.transaction.amountPaise)
                                } else {
                                    "Amount missing"
                                }
                                Text(
                                    text = amountText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        !previewItem.isValid && !previewItem.isStatementInfo -> MaterialTheme.colorScheme.error
                                        previewItem.transaction.type == "Income" -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box {
                                        Text(
                                            text = previewItem.transaction.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clickable { showCategoryMenu = true }
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                        DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                                            categories.forEach { cat ->
                                                DropdownMenuItem(
                                                    text = { Text(cat.name) },
                                                    onClick = {
                                                        onItemChange(index, previewItem.transaction.copy(category = cat.name, categoryId = cat.id), previewItem.tags)
                                                        showCategoryMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Box {
                                        Text(
                                            text = "Move ▾",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier
                                                .clickable { showMoveMenu = true }
                                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        )
                                        DropdownMenu(expanded = showMoveMenu, onDismissRequest = { showMoveMenu = false }) {
                                            DropdownMenuItem(text = { Text("Move to Ready") }, onClick = { onReclassifyItem(index, "Ready"); showMoveMenu = false })
                                            DropdownMenuItem(text = { Text("Move to Review") }, onClick = { onReclassifyItem(index, "Review"); showMoveMenu = false })
                                            DropdownMenuItem(text = { Text("Move to Ignored") }, onClick = { onReclassifyItem(index, "Ignored"); showMoveMenu = false })
                                            DropdownMenuItem(text = { Text("Move to Info") }, onClick = { onReclassifyItem(index, "Info"); showMoveMenu = false })
                                        }
                                    }

                                    Text(
                                        text = "✕",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { onRemoveItem(index) }
                                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (previewItem.tags.isNotEmpty()) {
                                Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    previewItem.tags.forEach { tag ->
                                        Text(
                                            text = "#${tag.name}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }

                            if (previewItem.isDuplicate) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = previewItem.error ?: "⚠️ Duplicate match (${previewItem.matchReason})",
                                        color = if (previewItem.isUserOverridden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (previewItem.isUserOverridden) "✓ Import Anyway" else "Import Anyway",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (previewItem.isUserOverridden) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable { onToggleImportAnyway(index) }
                                            .background(
                                                color = if (previewItem.isUserOverridden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            } else if (previewItem.isAmbiguous) {
                                Text(
                                    text = "⚠️ Single amount detected — verify direction",
                                    color = MaterialTheme.colorScheme.tertiary,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            if (!previewItem.isValid && !previewItem.isDuplicate) {
                                Text(
                                    text = previewItem.error ?: "Invalid row",
                                    color = if (previewItem.isStatementInfo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                Text("Cancel")
            }
            Button(
                onClick = onImport,
                modifier = Modifier.weight(2f),
                enabled = items.any { it.isValid && !it.isStatementInfo },
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Import ${items.count { it.isValid && !it.isStatementInfo }} Valid Rows")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
