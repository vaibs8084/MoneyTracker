package com.vaibhav.moneytracker.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vaibhav.moneytracker.*
import com.vaibhav.moneytracker.capture.CapturedTransactionEntity
import com.vaibhav.moneytracker.csv.DuplicateConfidence
import com.vaibhav.moneytracker.csv.DuplicateDetector

@Composable
fun CaptureInboxScreen(
    modifier: Modifier,
    viewModel: MainViewModel,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pendingCaptures by viewModel.pendingCaptures.collectAsState()
    val transactions by viewModel.transactions.collectAsState()

    var editingCandidate by remember { mutableStateOf<CapturedTransactionEntity?>(null) }
    var isNotificationAccessGranted by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }

    // Refresh permission status whenever returning from Android Settings (Lifecycle ON_RESUME)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNotificationAccessGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (editingCandidate != null) {
        EditCapturedDialog(
            candidate = editingCandidate!!,
            accounts = accounts,
            categories = categories,
            onDismiss = { editingCandidate = null },
            onSave = { updated ->
                viewModel.updateCapturedTransaction(updated)
                editingCandidate = null
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
                    modifier = Modifier.clickable { onBack() }.padding(end = 12.dp)
                )
                Text(text = "Capture Inbox", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (pendingCaptures.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${pendingCaptures.size}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
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
            // Notification Listener Permission Setup Banner
            if (!isNotificationAccessGranted) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "🔔 Notification Access Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Grant permission so Money Tracker can automatically capture financial transaction notifications from PhonePe, Paytm, Google Pay, and bank apps into this inbox for your review.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    } catch (e: Exception) {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Enable Access in Settings")
                            }
                        }
                    }
                }
            }

            if (pendingCaptures.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "📥", fontSize = 64.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = "Your Capture Inbox is Empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Financial notifications from PhonePe, Paytm, Google Pay, and bank apps will appear here for review.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(pendingCaptures, key = { it.id }) { candidate ->
                    val existingEntities = transactions.map { it.toEntity() }
                    val candidateAsEntity = TransactionEntity(
                        title = candidate.title,
                        category = "General",
                        account = accounts.find { it.id == candidate.suggestedAccountId }?.name ?: "Main",
                        type = candidate.type,
                        amountPaise = candidate.amountPaise,
                        note = "",
                        createdAt = candidate.createdAt,
                        accountId = candidate.suggestedAccountId
                    )

                    val dupMatch = DuplicateDetector.detectDuplicate(candidateAsEntity, existingEntities)
                    val matchedTx = dupMatch.matchedTransaction

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = candidate.sourceApp,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }

                                Text(
                                    text = formatDate(candidate.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = candidate.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isIncome = candidate.type == "Income"
                                Text(
                                    text = (if (isIncome) "+ " else "- ") + formatRupees(candidate.amountPaise),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )

                                val catName = categories.find { it.id == candidate.suggestedCategoryId }?.name ?: "General"
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = catName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (dupMatch.confidence == DuplicateConfidence.HIGH_CONFIDENCE_DUPLICATE && matchedTx != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "🔴 Already in app: Matches '${matchedTx.title}' (${formatDate(matchedTx.createdAt)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else if (dupMatch.confidence == DuplicateConfidence.POSSIBLE_DUPLICATE && matchedTx != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "⚠️ Possible duplicate: Matches '${matchedTx.title}' (${formatDate(matchedTx.createdAt)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.approveCapturedTransaction(candidate) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Approve ✓")
                                }

                                OutlinedButton(
                                    onClick = { editingCandidate = candidate },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Edit ✎")
                                }

                                TextButton(
                                    onClick = { viewModel.rejectCapturedTransaction(candidate) }
                                ) {
                                    Text("Reject", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditCapturedDialog(
    candidate: CapturedTransactionEntity,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (CapturedTransactionEntity) -> Unit
) {
    var title by remember(candidate.id) { mutableStateOf(candidate.title) }
    var amountText by remember(candidate.id) { mutableStateOf((candidate.amountPaise / 100L).toString()) }
    var selectedAccountId by remember(candidate.id) { mutableStateOf(candidate.suggestedAccountId ?: accounts.firstOrNull()?.id) }
    var selectedCategoryId by remember(candidate.id) { mutableStateOf(candidate.suggestedCategoryId) }
    var selectedType by remember(candidate.id) { mutableStateOf(candidate.type) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Captured Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Merchant / Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (Rupees)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Type", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Expense", "Income", "Transfer")) { t ->
                        SelectionChip(t, selectedType == t) { selectedType = t }
                    }
                }

                Text("Account", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accounts) { acc ->
                        SelectionChip(acc.name, selectedAccountId == acc.id) { selectedAccountId = acc.id }
                    }
                }

                Text("Category", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        SelectionChip(cat.name, selectedCategoryId == cat.id) { selectedCategoryId = cat.id }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && (amountText.toLongOrNull() ?: 0L) > 0L && selectedAccountId != null,
                onClick = {
                    val amountPaise = (amountText.toLongOrNull() ?: 0L) * 100L
                    onSave(
                        candidate.copy(
                            title = title.trim(),
                            amountPaise = amountPaise,
                            type = selectedType,
                            suggestedAccountId = selectedAccountId,
                            suggestedCategoryId = selectedCategoryId
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
