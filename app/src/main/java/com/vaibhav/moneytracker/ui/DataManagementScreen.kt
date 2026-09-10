package com.vaibhav.moneytracker.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.moneytracker.AccountEntity
import com.vaibhav.moneytracker.CategoryEntity
import com.vaibhav.moneytracker.MainViewModel
import com.vaibhav.moneytracker.TagEntity
import com.vaibhav.moneytracker.TransactionUiModel
import com.vaibhav.moneytracker.auth.UserIdentity
import com.vaibhav.moneytracker.cloud.SyncViewModel
import com.vaibhav.moneytracker.export.CSVExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DataManagementScreen(
    modifier: Modifier,
    viewModel: MainViewModel,
    syncViewModel: SyncViewModel,
    user: UserIdentity?,
    transactions: List<TransactionUiModel>,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    pendingSyncLogsCount: Int,
    onNavigateToImport: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showStartFreshDialog by remember { mutableStateOf(false) }

    val defaultFileName = "Money_Tracker_Export_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.csv"

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val result = CSVExporter.exportToCSV(context, uri, transactions)
            if (result.isSuccess) {
                Toast.makeText(context, "Exported ${result.exportedCount} transactions to CSV", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, result.errorMessage ?: "Export failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showStartFreshDialog) {
        StartFreshDialog(
            onDismiss = { showStartFreshDialog = false },
            onConfirmReset = {
                if (user != null) {
                    syncViewModel.performStartFresh(user) {
                        Toast.makeText(context, "'Start Fresh' complete reset finished", Toast.LENGTH_SHORT).show()
                        showStartFreshDialog = false
                        onBack()
                    }
                } else {
                    viewModel.resetLocalDatabase {
                        Toast.makeText(context, "Local database reset complete", Toast.LENGTH_SHORT).show()
                        showStartFreshDialog = false
                        onBack()
                    }
                }
            }
        )
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
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(end = 12.dp)
                )
                Text(
                    text = "Data Management",
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
            // 1. EXPORT & IMPORT SECTION
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "📄 Export & Import",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Export Transactions to CSV",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Save your transaction history as a CSV file to your device or Google Drive.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { exportLauncher.launch(defaultFileName) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Export ${transactions.size} Transactions to CSV")
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                        Text(
                            text = "Import Bank Statement",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Import CSV or PDF statements from HDFC, ICICI, SBI, Paytm, PhonePe, etc.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = onNavigateToImport,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Statement Import")
                        }
                    }
                }
            }

            // 2. DATABASE DIAGNOSTICS
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "📊 Database Statistics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Transactions", style = MaterialTheme.typography.bodySmall)
                            Text("${transactions.size}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Accounts", style = MaterialTheme.typography.bodySmall)
                            Text("${accounts.size}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Categories", style = MaterialTheme.typography.bodySmall)
                            Text("${categories.size}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tags", style = MaterialTheme.typography.bodySmall)
                            Text("${tags.size}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Pending Sync Queue", style = MaterialTheme.typography.bodySmall)
                            Text("$pendingSyncLogsCount", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = if (pendingSyncLogsCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // 3. DANGER ZONE (START FRESH)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "⚠️ Danger Zone — Start Fresh",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Start Fresh (Complete Reset)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Permanently erases all local transactions, accounts, categories, and settings AND clears your cloud backup on Google Drive. Old data cannot return on future syncs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { showStartFreshDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Fresh", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StartFreshDialog(
    onDismiss: () -> Unit,
    onConfirmReset: () -> Unit
) {
    var confirmInput by remember { mutableStateOf("") }
    val isConfirmed = confirmInput.trim() == "DELETE"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start Fresh (Complete Reset)?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "This will permanently erase all local data AND clear your cloud backup on Google Drive. Old data will NOT return on future syncs.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Type DELETE in all caps to confirm:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = confirmInput,
                    onValueChange = { confirmInput = it },
                    placeholder = { Text("DELETE") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = isConfirmed,
                onClick = onConfirmReset,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Confirm Start Fresh")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
