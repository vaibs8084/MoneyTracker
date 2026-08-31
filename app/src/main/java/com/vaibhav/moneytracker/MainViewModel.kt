package com.vaibhav.moneytracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    fun addTransaction(transaction: TransactionEntity, tags: List<TagEntity>) {
        viewModelScope.launch {
            repository.insertTransaction(transaction, tags)
        }
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
