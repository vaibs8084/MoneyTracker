package com.vaibhav.moneytracker.ui

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.moneytracker.*
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun CSVImportScreen(
    modifier: Modifier,
    viewModel: CSVImportViewModel,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    rules: List<RuleWithTags>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val columnMapping by viewModel.columnMapping.collectAsState()
    val targetAccountId by viewModel.targetAccountId.collectAsState()

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = reader.readText()
                viewModel.loadCSV(content)
                inputStream?.close()
            } catch (e: Exception) {
                // Handle error
            }
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
                Text(text = "CSV Import", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                        Text(text = "Select a CSV file to begin", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { fileLauncher.launch("text/*") },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Choose File")
                        }
                    }
                }
                is CSVImportViewModel.ImportState.Mapping -> {
                    MappingStep(
                        parsed = s.parsed,
                        mapping = columnMapping,
                        targetAccountId = targetAccountId,
                        accounts = accounts,
                        onMappingChange = { field, index -> viewModel.updateMapping(field, index) },
                        onAccountSelect = { viewModel.setTargetAccount(it) },
                        onContinue = { viewModel.generatePreview(rules, accounts, categories) }
                    )
                }
                is CSVImportViewModel.ImportState.Preview -> {
                    PreviewStep(
                        items = s.items,
                        categories = categories,
                        onItemChange = { index, tx, tags -> viewModel.updatePreviewItem(index, tx, tags) },
                        onImport = { viewModel.performImport() },
                        onBack = { viewModel.reset() }
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
                        Text(text = "Imported: ${s.imported}", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "Duplicates skipped: ${s.duplicates}", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onBack, shape = RoundedCornerShape(16.dp)) {
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
                        Text(text = "Error", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Text(text = s.message, style = MaterialTheme.typography.bodyMedium)
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
fun MappingStep(
    parsed: com.vaibhav.moneytracker.csv.ParsedCSV,
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
            Text(text = "1. Select Target Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            AccountSelectionList(
                accounts = accounts,
                selectedAccount = accounts.find { it.id == targetAccountId },
                onAccountSelected = { onAccountSelect(it.id) }
            )
        }

        item {
            Text(text = "2. Map CSV Columns", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = "Identify which columns contain the required data.", style = MaterialTheme.typography.bodySmall)
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
    categories: List<CategoryEntity>,
    onItemChange: (Int, TransactionEntity, List<TagEntity>) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(text = "3. Review Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = "${items.size} rows found. Tap category to change.", style = MaterialTheme.typography.bodySmall)
        
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(items) { index, previewItem ->
                var showCategoryMenu by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (previewItem.isValid) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) 
                                         else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = previewItem.transaction.title, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                            Text(text = formatRupees(previewItem.transaction.amountPaise), fontWeight = FontWeight.Bold, color = if (previewItem.transaction.type == "Income") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = formatDate(previewItem.transaction.createdAt), style = MaterialTheme.typography.labelSmall)
                            
                            Box {
                                Text(
                                    text = previewItem.transaction.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { showCategoryMenu = true }
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
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
                        if (!previewItem.isValid) {
                            Text(text = previewItem.error ?: "Invalid row", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                Text("Back")
            }
            Button(
                onClick = onImport,
                modifier = Modifier.weight(2f),
                enabled = items.any { it.isValid },
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Import Valid Rows")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
