package com.vaibhav.moneytracker

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaibhav.moneytracker.csv.CSVParser
import com.vaibhav.moneytracker.csv.DuplicateConfidence
import com.vaibhav.moneytracker.csv.DuplicateDetector
import com.vaibhav.moneytracker.csv.PDFStatementParser
import com.vaibhav.moneytracker.csv.PDFTransactionCandidate
import com.vaibhav.moneytracker.csv.StatementFormatDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class CSVImportViewModel(private val repository: MoneyRepository) : ViewModel() {

    data class ImportCounts(
        val totalDetected: Int = 0,
        val readyToImport: Int = 0,
        val needsReview: Int = 0,
        val ignoredCount: Int = 0,
        val infoCount: Int = 0,
        val duplicateCount: Int = 0
    )

    data class StatementBalances(
        val openingBalancePaise: Long? = null,
        val closingBalancePaise: Long? = null
    )

    sealed class ImportState {
        object Idle : ImportState()

        /** Prompt for password on encrypted PDFs. Password is NEVER saved or persisted. */
        data class PasswordRequired(
            val uri: Uri,
            val errorMessage: String? = null
        ) : ImportState()

        data class Mapping(
            val headers: List<String>,
            val rows: List<List<String>>,
            val uri: Uri? = null
        ) : ImportState()

        data class Preview(
            val items: List<ImportPreviewItem>,
            val counts: ImportCounts,
            val balances: StatementBalances = StatementBalances(),
            val rawHeaders: List<String> = emptyList(),
            val rawRows: List<List<String>> = emptyList()
        ) : ImportState()

        data class Success(val imported: Int, val duplicates: Int) : ImportState()
        data class Error(val message: String) : ImportState()
    }

    data class ImportPreviewItem(
        val transaction: TransactionEntity,
        val tags: List<TagEntity>,
        val isValid: Boolean,
        val isAmbiguous: Boolean = false,
        val isStatementInfo: Boolean = false,
        val isDuplicate: Boolean = false,
        val duplicateConfidence: DuplicateConfidence = DuplicateConfidence.UNIQUE,
        val matchReason: String? = null,
        val isUserOverridden: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private val _columnMapping = MutableStateFlow<Map<String, Int>>(emptyMap())
    val columnMapping: StateFlow<Map<String, Int>> = _columnMapping.asStateFlow()

    private val _targetAccountId = MutableStateFlow<Long?>(null)
    val targetAccountId: StateFlow<Long?> = _targetAccountId.asStateFlow()

    /**
     * Entry point to load a statement (CSV or PDF).
     * Automatically attempts to parse, normalize, apply Smart Rules, run Duplicate Detection, and present Preview.
     */
    fun loadStatement(
        context: Context,
        uri: Uri,
        password: String? = null,
        rules: List<RuleWithTags>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri)?.lowercase() ?: ""
            val fileName = uri.path?.lowercase() ?: ""
            val isPdf = mimeType.contains("pdf") || fileName.endsWith(".pdf")

            if (isPdf) {
                processPdfStatement(context, uri, password, rules, accounts, categories)
            } else {
                processCsvStatement(context, uri, rules, accounts, categories)
            }
        }
    }

    private suspend fun processPdfStatement(
        context: Context,
        uri: Uri,
        password: String?,
        rules: List<RuleWithTags>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ) {
        val inputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            _state.value = ImportState.Error("Unable to open PDF file: ${e.localizedMessage}")
            return
        }

        if (inputStream == null) {
            _state.value = ImportState.Error("Unable to open PDF file stream.")
            return
        }

        val extractResult = try {
            PDFStatementParser.parsePdf(context, inputStream, password)
        } finally {
            try { inputStream.close() } catch (e: Exception) {}
        }

        if (extractResult.isEncrypted) {
            _state.value = ImportState.PasswordRequired(
                uri = uri,
                errorMessage = extractResult.errorMessage
            )
            return
        }

        if (extractResult.isScanned) {
            _state.value = ImportState.Error(
                extractResult.errorMessage ?: "This PDF appears to be scanned/image-based and does not contain selectable transaction text. OCR is required to import it reliably."
            )
            return
        }

        if (extractResult.errorMessage != null && extractResult.rawLines.isEmpty()) {
            _state.value = ImportState.Error(extractResult.errorMessage)
            return
        }

        if (extractResult.rawLines.isEmpty()) {
            _state.value = ImportState.Error("No transactions could be detected from this statement.")
            return
        }

        if (extractResult.parsedRows.isNotEmpty()) {
            autoProcessPdfCandidates(extractResult.parsedRows, rules, accounts, categories)
        } else {
            val (headers, rows) = parsePdfLinesToTable(extractResult.rawLines)
            if (rows.isEmpty()) {
                _state.value = ImportState.Error("No structured transaction rows could be extracted from this PDF statement.")
                return
            }
            autoProcessStatement(headers, rows, rules, accounts, categories)
        }
    }

    private suspend fun autoProcessPdfCandidates(
        candidates: List<PDFTransactionCandidate>,
        rules: List<RuleWithTags>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ) {
        val defaultAccount = accounts.find { it.id == _targetAccountId.value } ?: accounts.firstOrNull()
        if (defaultAccount != null) {
            _targetAccountId.value = defaultAccount.id
        }

        val accountName = defaultAccount?.name ?: "Main Account"
        val accountId = defaultAccount?.id ?: 1L

        // Pre-fetch existing database transactions in statement range for fast deduplication
        val validDates = candidates.mapNotNull { CSVParser.parseDate(it.rawDate) }
        val minDate = (validDates.minOrNull() ?: System.currentTimeMillis()) - 2 * 24 * 3600 * 1000L
        val maxDate = (validDates.maxOrNull() ?: System.currentTimeMillis()) + 2 * 24 * 3600 * 1000L
        val existingDbTxs = repository.getTransactionsInRange(accountId, minDate, maxDate)

        var readyCount = 0
        var needsReviewCount = 0
        var ignoredCount = 0
        var infoCount = 0
        var duplicateCount = 0

        var openingBalPaise: Long? = null
        var closingBalPaise: Long? = null

        val previewItems = mutableListOf<ImportPreviewItem>()

        candidates.forEach { candidate ->
            val isOpening = StatementFormatDetector.isOpeningBalanceRow(candidate.narration)
            val isClosing = StatementFormatDetector.isClosingBalanceRow(candidate.narration)

            if (candidate.isSummaryRow || isOpening || isClosing) {
                infoCount++
                val amount = candidate.amountPaise ?: CSVParser.parseAmountPaise(candidate.narration)
                if (isOpening && openingBalPaise == null) {
                    openingBalPaise = amount
                } else if (isClosing && closingBalPaise == null) {
                    closingBalPaise = amount
                }

                previewItems.add(
                    ImportPreviewItem(
                        transaction = createBaseEntity(candidate.narration, "General", accountName, "Expense", amount ?: 0L, 0L, accountId),
                        tags = emptyList(),
                        isValid = false,
                        isStatementInfo = true,
                        error = candidate.summaryReason ?: "Opening / Summary Balance Row"
                    )
                )
                return@forEach
            }

            val parsedDate = CSVParser.parseDate(candidate.rawDate)
            if (parsedDate == null || candidate.amountPaise == null || candidate.amountPaise == 0L) {
                needsReviewCount++
                val errReason = when {
                    parsedDate == null && candidate.amountPaise == null -> "Date and amount unavailable"
                    parsedDate == null -> "Date unavailable"
                    else -> "Amount missing"
                }
                previewItems.add(
                    ImportPreviewItem(
                        transaction = createBaseEntity(candidate.narration, "General", accountName, "Expense", candidate.amountPaise ?: 0L, parsedDate ?: 0L, accountId),
                        tags = emptyList(),
                        isValid = false,
                        error = errReason
                    )
                )
                return@forEach
            }

            val type = if (candidate.isExpense) "Expense" else "Income"
            val absAmount = candidate.amountPaise

            // Apply Smart Rules
            val (matchedCatId, matchedTags) = IntelligenceEngine.matchRules(candidate.narration, rules)
            val finalCategoryId = matchedCatId
            val finalCategoryName = if (finalCategoryId != null) {
                categories.find { it.id == finalCategoryId }?.name ?: "General"
            } else {
                "General"
            }

            val entity = createBaseEntity(
                title = candidate.narration,
                category = finalCategoryName,
                account = accountName,
                type = type,
                amount = absAmount,
                date = parsedDate,
                accountId = accountId,
                categoryId = finalCategoryId
            )

            // Run Duplicate Detection Engine
            val dupMatch = DuplicateDetector.detectDuplicate(entity, existingDbTxs)
            val isHighConf = dupMatch.confidence == DuplicateConfidence.HIGH_CONFIDENCE_DUPLICATE
            val isPoss = dupMatch.confidence == DuplicateConfidence.POSSIBLE_DUPLICATE
            val isDup = isHighConf || isPoss

            val isValidItem = if (isHighConf) false else true
            val isAmbiguousItem = candidate.isAmbiguous || isPoss

            if (isDup) duplicateCount++

            if (!isValidItem || isAmbiguousItem) {
                needsReviewCount++
            } else {
                readyCount++
            }

            val errorMsg = when {
                isHighConf -> "High-confidence duplicate (Skipped): ${dupMatch.matchReason}"
                isPoss -> "Possible duplicate — Verify: ${dupMatch.matchReason}"
                else -> null
            }

            previewItems.add(
                ImportPreviewItem(
                    transaction = entity,
                    tags = matchedTags,
                    isValid = isValidItem,
                    isAmbiguous = isAmbiguousItem,
                    isDuplicate = isDup,
                    duplicateConfidence = dupMatch.confidence,
                    matchReason = dupMatch.matchReason,
                    error = errorMsg
                )
            )
        }

        val totalTransactions = readyCount + needsReviewCount + ignoredCount
        val counts = ImportCounts(
            totalDetected = totalTransactions,
            readyToImport = readyCount,
            needsReview = needsReviewCount,
            ignoredCount = ignoredCount,
            infoCount = infoCount,
            duplicateCount = duplicateCount
        )

        val balances = StatementBalances(
            openingBalancePaise = openingBalPaise,
            closingBalancePaise = closingBalPaise
        )

        withContext(Dispatchers.Main) {
            _state.value = ImportState.Preview(
                items = previewItems,
                counts = counts,
                balances = balances
            )
        }
    }

    private suspend fun processCsvStatement(
        context: Context,
        uri: Uri,
        rules: List<RuleWithTags>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ) {
        val content = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }
        } catch (e: Exception) {
            null
        }

        if (content.isNullOrBlank()) {
            _state.value = ImportState.Error("Unable to read CSV file content.")
            return
        }

        val parsed = CSVParser.parse(content)
        if (parsed.headers.isEmpty() && parsed.rows.isEmpty()) {
            _state.value = ImportState.Error("CSV file is empty.")
            return
        }

        autoProcessStatement(parsed.headers, parsed.rows, rules, accounts, categories)
    }

    private fun parsePdfLinesToTable(lines: List<String>): Pair<List<String>, List<List<String>>> {
        val defaultHeaders = listOf("Date", "Narration", "Amount")
        val parsedRows = mutableListOf<List<String>>()

        for (line in lines) {
            val tokens = line.split(Regex("\\s{2,}|\\t")).map { it.trim() }.filter { it.isNotBlank() }
            if (tokens.size >= 2) {
                parsedRows.add(tokens)
            }
        }

        return defaultHeaders to parsedRows
    }

    private suspend fun autoProcessStatement(
        headers: List<String>,
        rows: List<List<String>>,
        rules: List<RuleWithTags>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ) {
        val detected = StatementFormatDetector.detectColumns(headers)
        val defaultAccount = accounts.find { it.id == _targetAccountId.value } ?: accounts.firstOrNull()

        if (defaultAccount != null) {
            _targetAccountId.value = defaultAccount.id
        }

        val accountName = defaultAccount?.name ?: "Main Account"
        val accountId = defaultAccount?.id ?: 1L

        var readyCount = 0
        var needsReviewCount = 0
        var ignoredCount = 0
        var infoCount = 0
        var duplicateCount = 0

        var openingBalPaise: Long? = null
        var closingBalPaise: Long? = null

        val previewItems = mutableListOf<ImportPreviewItem>()

        // Pre-fetch DB transactions in statement range for fast deduplication
        val validDates = rows.mapNotNull { row ->
            row.firstNotNullOfOrNull { CSVParser.parseDate(it) }
        }
        val minDate = (validDates.minOrNull() ?: System.currentTimeMillis()) - 2 * 24 * 3600 * 1000L
        val maxDate = (validDates.maxOrNull() ?: System.currentTimeMillis()) + 2 * 24 * 3600 * 1000L
        val existingDbTxs = repository.getTransactionsInRange(accountId, minDate, maxDate)

        rows.forEach { row ->
            val titleCandidate = detected.titleIndex?.let { row.getOrNull(it) }?.trim()?.takeIf { it.isNotBlank() }
                ?: row.find { cell ->
                    val clean = cell.trim()
                    clean.length > 2 &&
                    CSVParser.parseDate(clean) == null &&
                    CSVParser.parseAmountPaise(clean) == null &&
                    !StatementFormatDetector.isBalanceColumn(clean)
                }?.trim()
                ?: "Imported Transaction"

            val isOpening = StatementFormatDetector.isOpeningBalanceRow(titleCandidate) || StatementFormatDetector.isOpeningBalanceRow(row.joinToString(" "))
            val isClosing = StatementFormatDetector.isClosingBalanceRow(titleCandidate) || StatementFormatDetector.isClosingBalanceRow(row.joinToString(" "))

            if (isOpening || isClosing) {
                infoCount++
                val amountCell = row.firstOrNull { CSVParser.parseAmountPaise(it) != null }
                val amountPaise = amountCell?.let { CSVParser.parseAmountPaise(it) }
                if (isOpening && openingBalPaise == null) {
                    openingBalPaise = amountPaise
                } else if (isClosing && closingBalPaise == null) {
                    closingBalPaise = amountPaise
                }

                previewItems.add(
                    ImportPreviewItem(
                        transaction = createBaseEntity(titleCandidate, "General", accountName, "Expense", amountPaise ?: 0L, 0L, accountId),
                        tags = emptyList(),
                        isValid = false,
                        isStatementInfo = true,
                        error = "Opening / Summary Balance Row"
                    )
                )
                return@forEach
            }

            val dateStrCandidate = detected.dateIndex?.let { row.getOrNull(it) }?.trim()?.takeIf { it.isNotBlank() }
            var parsedDate = dateStrCandidate?.let { CSVParser.parseDate(it) }
            if (parsedDate == null) {
                for (cell in row) {
                    val d = CSVParser.parseDate(cell)
                    if (d != null) {
                        parsedDate = d
                        break
                    }
                }
            }

            val debitStr = detected.debitIndex?.let { row.getOrNull(it) }
            val creditStr = detected.creditIndex?.let { row.getOrNull(it) }
            val amountStr = detected.amountIndex?.let { row.getOrNull(it) }
            val typeStr = detected.typeIndex?.let { row.getOrNull(it) }

            var normalizedPaise = StatementFormatDetector.normalizeAmount(debitStr, creditStr, amountStr, typeStr, titleCandidate)
            if (normalizedPaise == null) {
                val amountCell = row.firstOrNull { cell ->
                    val p = CSVParser.parseAmountPaise(cell)
                    p != null && Math.abs(p) > 0L && CSVParser.parseDate(cell) == null && cell.trim() != titleCandidate
                }
                if (amountCell != null) {
                    normalizedPaise = StatementFormatDetector.normalizeAmount(null, null, amountCell, typeStr, titleCandidate)
                }
            }

            if (parsedDate == null || normalizedPaise == null || Math.abs(normalizedPaise) == 0L) {
                val hasMeaningfulData = titleCandidate != "Imported Transaction" || parsedDate != null || normalizedPaise != null
                if (!hasMeaningfulData) {
                    infoCount++
                    return@forEach
                }
                needsReviewCount++
                val err = when {
                    parsedDate == null && (normalizedPaise == null || Math.abs(normalizedPaise) == 0L) -> "Missing date and amount"
                    parsedDate == null -> "Unable to parse date"
                    else -> "Unable to parse amount"
                }
                previewItems.add(
                    ImportPreviewItem(
                        transaction = createBaseEntity(
                            title = titleCandidate,
                            category = "General",
                            account = accountName,
                            type = if ((normalizedPaise ?: 0L) > 0) "Income" else "Expense",
                            amount = normalizedPaise?.let { Math.abs(it) } ?: 0L,
                            date = parsedDate ?: 0L,
                            accountId = accountId
                        ),
                        tags = emptyList(),
                        isValid = false,
                        error = err
                    )
                )
                return@forEach
            }

            val type = if (normalizedPaise > 0) "Income" else "Expense"
            val absAmount = Math.abs(normalizedPaise)

            val (matchedCatId, matchedTags) = IntelligenceEngine.matchRules(titleCandidate, rules)
            val finalCategoryId = matchedCatId
            val finalCategoryName = if (finalCategoryId != null) {
                categories.find { it.id == finalCategoryId }?.name ?: "General"
            } else {
                "General"
            }

            val debitPaise = debitStr?.let { CSVParser.parseAmountPaise(it) } ?: 0L
            val creditPaise = creditStr?.let { CSVParser.parseAmountPaise(it) } ?: 0L
            val hasExplicitDirection = (detected.debitIndex != null && Math.abs(debitPaise) > 0L) ||
                    (detected.creditIndex != null && Math.abs(creditPaise) > 0L) ||
                    (!typeStr.isNullOrBlank()) ||
                    (amountStr != null && amountStr.contains("-")) ||
                    (amountStr != null && (amountStr.lowercase().endsWith("dr") || amountStr.lowercase().endsWith("cr"))) ||
                    (titleCandidate.lowercase(Locale.getDefault()).let { t ->
                        t.contains("/dr/") || t.contains("/dr ") || t.contains("-dr-") || t.contains(" dr ") ||
                        t.contains("[dr]") || t.contains("(dr)") || t.contains("debit") ||
                        t.contains("/cr/") || t.contains("/cr ") || t.contains("-cr-") || t.contains(" cr ") ||
                        t.contains("[cr]") || t.contains("(cr)") || t.contains("credit")
                    })

            val entity = createBaseEntity(
                title = titleCandidate,
                category = finalCategoryName,
                account = accountName,
                type = type,
                amount = absAmount,
                date = parsedDate,
                accountId = accountId,
                categoryId = finalCategoryId
            )

            // Run Duplicate Detection Engine
            val dupMatch = DuplicateDetector.detectDuplicate(entity, existingDbTxs)
            val isHighConf = dupMatch.confidence == DuplicateConfidence.HIGH_CONFIDENCE_DUPLICATE
            val isPoss = dupMatch.confidence == DuplicateConfidence.POSSIBLE_DUPLICATE
            val isDup = isHighConf || isPoss

            val isValidItem = if (isHighConf) false else true
            val isAmbiguousItem = (!hasExplicitDirection) || isPoss

            if (isDup) duplicateCount++

            if (!isValidItem || isAmbiguousItem) {
                needsReviewCount++
            } else {
                readyCount++
            }

            val errorMsg = when {
                isHighConf -> "High-confidence duplicate (Skipped): ${dupMatch.matchReason}"
                isPoss -> "Possible duplicate — Verify: ${dupMatch.matchReason}"
                else -> null
            }

            previewItems.add(
                ImportPreviewItem(
                    transaction = entity,
                    tags = matchedTags,
                    isValid = isValidItem,
                    isAmbiguous = isAmbiguousItem,
                    isDuplicate = isDup,
                    duplicateConfidence = dupMatch.confidence,
                    matchReason = dupMatch.matchReason,
                    error = errorMsg
                )
            )
        }

        val totalTransactions = readyCount + needsReviewCount + ignoredCount
        val counts = ImportCounts(
            totalDetected = totalTransactions,
            readyToImport = readyCount,
            needsReview = needsReviewCount,
            ignoredCount = ignoredCount,
            infoCount = infoCount,
            duplicateCount = duplicateCount
        )

        val balances = StatementBalances(
            openingBalancePaise = openingBalPaise,
            closingBalancePaise = closingBalPaise
        )

        withContext(Dispatchers.Main) {
            _state.value = ImportState.Preview(
                items = previewItems,
                counts = counts,
                balances = balances,
                rawHeaders = headers,
                rawRows = rows
            )
        }
    }

    fun toggleImportAnyway(index: Int) {
        val currentState = _state.value
        if (currentState is ImportState.Preview) {
            val newList = currentState.items.toMutableList()
            if (index in newList.indices) {
                val item = newList[index]
                val newOverrideState = !item.isUserOverridden
                val newValidState = if (newOverrideState) true else (item.duplicateConfidence != DuplicateConfidence.HIGH_CONFIDENCE_DUPLICATE && item.transaction.amountPaise > 0L)

                newList[index] = item.copy(
                    isUserOverridden = newOverrideState,
                    isValid = newValidState,
                    isAmbiguous = if (newOverrideState) false else item.isAmbiguous,
                    error = if (newOverrideState) null else item.error
                )

                recalculateCountsAndUpdateState(currentState, newList)
            }
        }
    }

    fun setTargetAccount(accountId: Long, accounts: List<AccountEntity>) {
        _targetAccountId.value = accountId
        val account = accounts.find { it.id == accountId } ?: return
        val currentState = _state.value

        if (currentState is ImportState.Preview) {
            val updated = currentState.items.map { item ->
                item.copy(
                    transaction = item.transaction.copy(
                        accountId = account.id,
                        account = account.name
                    )
                )
            }
            _state.value = currentState.copy(items = updated)
        }
    }

    fun updatePreviewItem(index: Int, transaction: TransactionEntity, tags: List<TagEntity>) {
        val currentState = _state.value
        if (currentState is ImportState.Preview) {
            val newList = currentState.items.toMutableList()
            if (index in newList.indices) {
                newList[index] = newList[index].copy(
                    transaction = transaction,
                    tags = tags,
                    isAmbiguous = false
                )
                _state.value = currentState.copy(items = newList)
            }
        }
    }

    fun reclassifyItem(index: Int, targetCategory: String) {
        val currentState = _state.value
        if (currentState is ImportState.Preview) {
            val newList = currentState.items.toMutableList()
            if (index in newList.indices) {
                val current = newList[index]
                val updated = when (targetCategory) {
                    "Ready" -> {
                        val canBeReady = current.transaction.createdAt > 0L && current.transaction.amountPaise > 0L
                        if (canBeReady) {
                            current.copy(isValid = true, isAmbiguous = false, isStatementInfo = false, isUserOverridden = true, error = null)
                        } else {
                            current.copy(isValid = false, isAmbiguous = true, isStatementInfo = false, error = "Date or amount missing for Ready")
                        }
                    }
                    "Review" -> current.copy(isValid = false, isAmbiguous = true, isStatementInfo = false, error = current.error ?: "User flagged for review")
                    "Ignored" -> current.copy(isValid = false, isAmbiguous = false, isStatementInfo = false, isUserOverridden = false, error = "Ignored by user")
                    "Info" -> current.copy(isValid = false, isAmbiguous = false, isStatementInfo = true, error = "Moved to Statement Info")
                    else -> current
                }
                newList[index] = updated

                recalculateCountsAndUpdateState(currentState, newList)
            }
        }
    }

    fun removePreviewItem(index: Int) {
        val currentState = _state.value
        if (currentState is ImportState.Preview) {
            val newList = currentState.items.toMutableList()
            if (index in newList.indices) {
                newList.removeAt(index)
                recalculateCountsAndUpdateState(currentState, newList)
            }
        }
    }

    private fun recalculateCountsAndUpdateState(currentState: ImportState.Preview, newList: List<ImportPreviewItem>) {
        val readyCount = newList.count { it.isValid && !it.isAmbiguous && !it.isStatementInfo }
        val needsReviewCount = newList.count { (!it.isValid || it.isAmbiguous) && !it.isStatementInfo }
        val ignoredCount = newList.count { !it.isValid && !it.isAmbiguous && !it.isStatementInfo }
        val infoCount = newList.count { it.isStatementInfo }
        val dupCount = newList.count { it.isDuplicate }

        val totalTransactions = readyCount + needsReviewCount + ignoredCount
        val updatedCounts = currentState.counts.copy(
            totalDetected = totalTransactions,
            readyToImport = readyCount,
            needsReview = needsReviewCount,
            ignoredCount = ignoredCount,
            infoCount = infoCount,
            duplicateCount = dupCount
        )

        _state.value = currentState.copy(items = newList, counts = updatedCounts)
    }

    fun switchToAdvancedMapping() {
        val currentState = _state.value
        if (currentState is ImportState.Preview) {
            _state.value = ImportState.Mapping(
                headers = currentState.rawHeaders,
                rows = currentState.rawRows
            )
        }
    }

    fun updateMapping(field: String, index: Int) {
        _columnMapping.value = _columnMapping.value + (field to index)
    }

    fun generateManualPreview(rules: List<RuleWithTags>, accounts: List<AccountEntity>, categories: List<CategoryEntity>) {
        val currentState = _state.value
        if (currentState !is ImportState.Mapping) return

        viewModelScope.launch(Dispatchers.IO) {
            autoProcessStatement(currentState.headers, currentState.rows, rules, accounts, categories)
        }
    }

    private fun createBaseEntity(
        title: String, category: String, account: String, type: String,
        amount: Long, date: Long, accountId: Long, categoryId: Long? = null
    ) = TransactionEntity(
        title = title,
        category = category,
        account = account,
        type = type,
        amountPaise = amount,
        note = "Statement Import",
        createdAt = date,
        accountId = accountId,
        categoryId = categoryId
    )

    fun performImport() {
        val currentState = _state.value
        if (currentState !is ImportState.Preview) return

        val validItems = currentState.items.filter { it.isValid && !it.isStatementInfo }.map { it.transaction to it.tags }
        if (validItems.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.bulkImportTransactions(validItems)
                withContext(Dispatchers.Main) {
                    _state.value = ImportState.Success(result.imported, result.duplicates)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.value = ImportState.Error("Import failed: ${e.localizedMessage}")
                }
            }
        }
    }

    fun reset() {
        _state.value = ImportState.Idle
        _columnMapping.value = emptyMap()
    }
}
