package com.vaibhav.moneytracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaibhav.moneytracker.csv.CSVParser
import com.vaibhav.moneytracker.csv.ParsedCSV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CSVImportViewModel(private val repository: MoneyRepository) : ViewModel() {

    sealed class ImportState {
        object Idle : ImportState()
        data class Mapping(val parsed: ParsedCSV) : ImportState()
        data class Preview(val items: List<ImportPreviewItem>) : ImportState()
        data class Success(val imported: Int, val duplicates: Int) : ImportState()
        data class Error(val message: String) : ImportState()
    }

    data class ImportPreviewItem(
        val transaction: TransactionEntity,
        val tags: List<TagEntity>,
        val isValid: Boolean,
        val error: String? = null
    )

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private val _columnMapping = MutableStateFlow<Map<String, Int>>(emptyMap())
    val columnMapping: StateFlow<Map<String, Int>> = _columnMapping.asStateFlow()

    private val _targetAccountId = MutableStateFlow<Long?>(null)
    val targetAccountId: StateFlow<Long?> = _targetAccountId.asStateFlow()

    fun loadCSV(content: String) {
        try {
            val parsed = CSVParser.parse(content)
            _state.value = ImportState.Mapping(parsed)
            // Auto-detect basic mappings if possible
            autoDetectMapping(parsed.headers)
        } catch (e: Exception) {
            _state.value = ImportState.Error("Failed to parse CSV: ${e.message}")
        }
    }

    private fun autoDetectMapping(headers: List<String>) {
        val mapping = mutableMapOf<String, Int>()
        headers.forEachIndexed { index, header ->
            val h = header.lowercase()
            when {
                h.contains("date") -> mapping["Date"] = index
                h.contains("desc") || h.contains("title") || h.contains("memo") || h.contains("particulars") -> mapping["Title"] = index
                h.contains("amount") || h.contains("val") || h.contains("sum") -> mapping["Amount"] = index
                h.contains("category") -> mapping["Category"] = index
                h.contains("type") || h.contains("cr/dr") || h.contains("transaction kind") -> mapping["Type"] = index
            }
        }
        _columnMapping.value = mapping
    }

    fun updateMapping(field: String, index: Int) {
        _columnMapping.value = _columnMapping.value + (field to index)
    }

    fun setTargetAccount(accountId: Long) {
        _targetAccountId.value = accountId
    }

    fun generatePreview(rules: List<RuleWithTags>, accounts: List<AccountEntity>, categories: List<CategoryEntity>) {
        val currentState = _state.value
        if (currentState !is ImportState.Mapping) return

        val mapping = _columnMapping.value
        val accountId = _targetAccountId.value
        if (accountId == null) {
            _state.value = ImportState.Error("Please select a target account.")
            return
        }

        val account = accounts.find { it.id == accountId } ?: return

        viewModelScope.launch {
            val previewItems = currentState.parsed.rows.map { row ->
                try {
                    val dateStr = mapping["Date"]?.let { row.getOrNull(it) } ?: ""
                    val title = mapping["Title"]?.let { row.getOrNull(it) } ?: "Imported Transaction"
                    val amountStr = mapping["Amount"]?.let { row.getOrNull(it) } ?: "0"
                    val typeStr = mapping["Type"]?.let { row.getOrNull(it) }?.lowercase()
                    val categoryOverride = mapping["Category"]?.let { index -> 
                        row.getOrNull(index)?.let { name -> 
                            categories.find { it.name.lowercase() == name.lowercase() } 
                        } 
                    }

                    val date = CSVParser.parseDate(dateStr) ?: System.currentTimeMillis()
                    var amountPaise = CSVParser.parseAmountPaise(amountStr) ?: 0L

                    if (typeStr != null && (typeStr.contains("dr") || typeStr.contains("debit") || typeStr.contains("out"))) {
                        amountPaise = -Math.abs(amountPaise)
                    } else if (typeStr != null && (typeStr.contains("cr") || typeStr.contains("credit") || typeStr.contains("in"))) {
                        amountPaise = Math.abs(amountPaise)
                    }

                    if (amountPaise == 0L) {
                        return@map ImportPreviewItem(
                            createBaseEntity(title, "General", account.name, "Expense", 0, date, account.id),
                            emptyList(),
                            false,
                            "Invalid amount"
                        )
                    }

                    val type = if (amountPaise > 0) "Income" else "Expense"
                    val absoluteAmount = Math.abs(amountPaise)

                    // Apply Smart Rules
                    var finalCategoryId = categoryOverride?.id
                    var finalTags = emptyList<TagEntity>()
                    
                    if (finalCategoryId == null) {
                        val (matchedCatId, matchedTags) = IntelligenceEngine.matchRules(title, rules)
                        finalCategoryId = matchedCatId
                        finalTags = matchedTags
                    }

                    val finalCategoryName = if (finalCategoryId != null) {
                        categories.find { it.id == finalCategoryId }?.name ?: "General"
                    } else {
                        "General"
                    }

                    val entity = createBaseEntity(
                        title,
                        finalCategoryName,
                        account.name,
                        type,
                        absoluteAmount,
                        date,
                        account.id,
                        finalCategoryId
                    )
                    
                    ImportPreviewItem(entity, finalTags, true)
                } catch (e: Exception) {
                    ImportPreviewItem(
                        createBaseEntity("Error", "General", account.name, "Expense", 0, 0, account.id),
                        emptyList(),
                        false,
                        e.message
                    )
                }
            }
            _state.value = ImportState.Preview(previewItems)
        }
    }

    fun updatePreviewItem(index: Int, transaction: TransactionEntity, tags: List<TagEntity>) {
        val currentState = _state.value
        if (currentState is ImportState.Preview) {
            val newList = currentState.items.toMutableList()
            if (index in newList.indices) {
                newList[index] = newList[index].copy(transaction = transaction, tags = tags)
                _state.value = ImportState.Preview(newList)
            }
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
        note = "CSV Import",
        createdAt = date,
        accountId = accountId,
        categoryId = categoryId
    )

    fun performImport() {
        val currentState = _state.value
        if (currentState !is ImportState.Preview) return

        val validItems = currentState.items.filter { it.isValid }.map { it.transaction to it.tags }
        if (validItems.isEmpty()) return

        viewModelScope.launch {
            try {
                val result = repository.bulkImportTransactions(validItems)
                _state.value = ImportState.Success(result.imported, result.duplicates)
            } catch (e: Exception) {
                _state.value = ImportState.Error("Import failed: ${e.message}")
            }
        }
    }

    fun reset() {
        _state.value = ImportState.Idle
        _columnMapping.value = emptyMap()
    }
}
