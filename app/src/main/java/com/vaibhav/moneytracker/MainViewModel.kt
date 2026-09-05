package com.vaibhav.moneytracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaibhav.moneytracker.capture.CapturedTransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(private val repository: MoneyRepository) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(DashboardPeriod.THIS_MONTH)
    val selectedPeriod: StateFlow<DashboardPeriod> = _selectedPeriod.asStateFlow()

    val transactions: StateFlow<List<TransactionUiModel>> = repository.getTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> = repository.getAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAccounts: StateFlow<List<AccountEntity>> = repository.getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repository.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tags: StateFlow<List<TagEntity>> = repository.getTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subscriptions: StateFlow<List<SubscriptionEntity>> = repository.getSubscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgets: StateFlow<List<BudgetEntity>> = repository.getBudgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goals: StateFlow<List<GoalEntity>> = repository.getGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val smartRules: StateFlow<List<RuleWithTags>> = repository.getSmartRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingCaptures: StateFlow<List<CapturedTransactionEntity>> =
        repository.getPendingCapturesFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingCaptureCount: StateFlow<Int> = repository.getPendingCaptureCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val financialPosition: StateFlow<FinancialPosition?> = combine(
        transactions,
        allAccounts
    ) { txs, accs ->
        FinancialEngine.calculateFinancialPosition(txs.map { it.toEntity() }, accs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val insights: StateFlow<List<IntelligenceEngine.Insight>> = combine(
        transactions,
        selectedPeriod
    ) { txs, period ->
        IntelligenceEngine.generateInsights(txs, period)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectPeriod(period: DashboardPeriod) {
        _selectedPeriod.value = period
    }

    /**
     * Holds a transaction that is pending user confirmation after a potential
     * duplicate was detected. The UI observes [duplicateWarning] to show a
     * warning dialog before committing the insert.
     */
    data class DuplicateWarning(
        val pendingTransaction: TransactionEntity,
        val pendingTags: List<TagEntity>,
        val existingTransaction: TransactionEntity
    )

    private val _duplicateWarning = MutableStateFlow<DuplicateWarning?>(null)

    /**
     * Non-null when [addTransaction] detected a potential duplicate.
     * The UI must display a confirmation dialog and call either
     * [saveTransactionAnyway] or [dismissDuplicateWarning].
     */
    val duplicateWarning: StateFlow<DuplicateWarning?> = _duplicateWarning.asStateFlow()

    /**
     * One-shot event emitted after a transaction is successfully inserted.
     * The UI collects this to close the Add Transaction screen.
     */
    private val _transactionSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val transactionSaved: SharedFlow<Unit> = _transactionSaved.asSharedFlow()

    // Prevents a second addTransaction() call from being processed while the
    // first is in-flight. Set and cleared on Dispatchers.Main only.
    private var isSaving = false

    fun addTransaction(transaction: TransactionEntity, tags: List<TagEntity>) {
        if (isSaving) return
        isSaving = true
        viewModelScope.launch {
            try {
                when (val result = repository.insertTransactionChecked(transaction, tags)) {
                    is MoneyRepository.InsertTransactionResult.Inserted -> {
                        _transactionSaved.emit(Unit)
                    }
                    is MoneyRepository.InsertTransactionResult.DuplicateDetected -> {
                        _duplicateWarning.value = DuplicateWarning(
                            transaction, tags, result.existing
                        )
                    }
                }
            } finally {
                isSaving = false
            }
        }
    }

    /** Proceeds with the insert for a transaction that triggered a duplicate warning. */
    fun saveTransactionAnyway() {
        val warning = _duplicateWarning.value ?: return
        _duplicateWarning.value = null
        viewModelScope.launch {
            repository.insertTransaction(warning.pendingTransaction, warning.pendingTags)
            _transactionSaved.emit(Unit)
        }
    }

    /** Discards the pending duplicate warning without inserting anything. */
    fun dismissDuplicateWarning() {
        _duplicateWarning.value = null
    }

    fun updateTransaction(transaction: TransactionEntity, tags: List<TagEntity>) {
        viewModelScope.launch {
            repository.updateTransaction(transaction, tags)
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun deleteTransactions(ids: List<Long>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteTransactions(ids)
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    // Capture Inbox methods
    fun approveCapturedTransaction(captured: CapturedTransactionEntity) {
        viewModelScope.launch {
            repository.approveCapturedTransaction(captured)
        }
    }

    fun rejectCapturedTransaction(captured: CapturedTransactionEntity) {
        viewModelScope.launch {
            repository.rejectCapturedTransaction(captured)
        }
    }

    fun updateCapturedTransaction(captured: CapturedTransactionEntity) {
        viewModelScope.launch {
            repository.updateCapturedTransaction(captured)
        }
    }

    // Account methods
    fun addAccount(name: String, type: String, balance: Long) {
        viewModelScope.launch {
            repository.insertAccount(AccountEntity(name = name, type = type, openingBalancePaise = balance))
        }
    }
    
    fun updateAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.updateAccount(account)
        }
    }
    
    fun deactivateAccount(id: Long) {
        viewModelScope.launch {
            repository.deactivateAccount(id)
        }
    }

    // Category methods
    fun addCategory(name: String) {
        viewModelScope.launch {
            repository.insertCategory(CategoryEntity(name = name))
        }
    }

    fun updateCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    // Tag methods
    fun addTag(name: String) {
        viewModelScope.launch {
            repository.insertTag(TagEntity(name = name))
        }
    }

    fun updateTag(tag: TagEntity) {
        viewModelScope.launch {
            repository.updateTag(tag)
        }
    }

    // Subscription methods
    fun confirmSubscription(suggestion: SubscriptionEntity) {
        viewModelScope.launch { repository.confirmSubscription(suggestion) }
    }

    fun updateSubscription(subscription: SubscriptionEntity) {
        viewModelScope.launch {
            repository.updateSubscription(subscription)
        }
    }

    fun deactivateSubscription(subscription: SubscriptionEntity) {
        viewModelScope.launch { repository.deactivateSubscription(subscription) }
    }

    fun recordSubscriptionPayment(subscription: SubscriptionEntity) {
        viewModelScope.launch { repository.recordSubscriptionPayment(subscription) }
    }

    // Budget methods
    fun addBudget(categoryId: Long, limit: Long) {
        viewModelScope.launch {
            repository.insertBudget(BudgetEntity(categoryId = categoryId, limitPaise = limit, period = "MONTHLY"))
        }
    }

    // Goal methods
    fun addGoal(name: String, target: Long, linkedAccountId: Long?) {
        viewModelScope.launch {
            repository.insertGoal(
                GoalEntity(
                    name = name,
                    targetPaise = target,
                    linkedAccountId = linkedAccountId,
                    manualProgressPaise = 0
                )
            )
        }
    }

    fun updateGoal(goal: GoalEntity) {
        viewModelScope.launch {
            repository.updateGoal(goal)
        }
    }

    // Smart Rule methods
    fun addSmartRule(pattern: String, categoryId: Long?, tagIds: List<Long>) {
        viewModelScope.launch {
            repository.insertSmartRule(CategorizationRuleEntity(titlePattern = pattern, targetCategoryId = categoryId), tagIds)
        }
    }
}
