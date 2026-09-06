package com.vaibhav.moneytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.vaibhav.moneytracker.ui.AccountSelectionList
import com.vaibhav.moneytracker.ui.BalanceMiniStatPlain
import com.vaibhav.moneytracker.ui.ClassificationChoiceCard
import com.vaibhav.moneytracker.ui.HistoryScreen
import com.vaibhav.moneytracker.ui.SectionHeader
import com.vaibhav.moneytracker.ui.SelectionChip
import com.vaibhav.moneytracker.ui.TransactionRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import com.vaibhav.moneytracker.ui.ProfileScreen
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.vaibhav.moneytracker.auth.AuthManager
import com.vaibhav.moneytracker.auth.OnboardingStatus
import com.vaibhav.moneytracker.auth.OnboardingViewModel
import com.vaibhav.moneytracker.cloud.CloudSpreadsheetManager
import com.vaibhav.moneytracker.cloud.GoogleSheetsRepository
import com.vaibhav.moneytracker.cloud.SyncEngine
import com.vaibhav.moneytracker.cloud.SyncStatus
import com.vaibhav.moneytracker.cloud.SyncUiState
import com.vaibhav.moneytracker.cloud.SyncViewModel
import com.vaibhav.moneytracker.preferences.ThemeMode
import com.vaibhav.moneytracker.preferences.UserPreferencesManager
import com.vaibhav.moneytracker.ui.CaptureInboxScreen
import com.vaibhav.moneytracker.ui.OnboardingScreen
import com.vaibhav.moneytracker.ui.SettingsScreen
import com.vaibhav.moneytracker.ui.theme.MoneyTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Codex E2E test
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = MoneyTrackerDatabase.getInstance(this)
        val repository = MoneyRepository(database)
        val viewModel = MainViewModel(repository)
        val csvViewModel = CSVImportViewModel(repository)

        val authManager = AuthManager(this)
        val cloudSpreadsheetManager = CloudSpreadsheetManager(this)
        val sheetsRepository = GoogleSheetsRepository(this)
        val syncEngine = SyncEngine(database, repository, sheetsRepository, cloudSpreadsheetManager)
        val syncViewModel = SyncViewModel(database, syncEngine, authManager.preferenceManager)
        val onboardingViewModel = OnboardingViewModel(authManager, cloudSpreadsheetManager)
        val userPreferencesManager = UserPreferencesManager(this)

        setContent {
            val settingsState by userPreferencesManager.settingsState.collectAsState()
            val systemInDark = isSystemInDarkTheme()
            val isDarkTheme = when (settingsState.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemInDark
            }

            MoneyTrackerTheme(darkTheme = isDarkTheme) {
                MoneyTrackerAppRoot(
                    activity = this,
                    viewModel = viewModel,
                    csvViewModel = csvViewModel,
                    syncViewModel = syncViewModel,
                    onboardingViewModel = onboardingViewModel,
                    userPreferencesManager = userPreferencesManager,
                    authManager = authManager,
                    database = database
                )
            }
        }
    }
}

@Composable
fun MoneyTrackerAppRoot(
    activity: ComponentActivity,
    viewModel: MainViewModel,
    csvViewModel: CSVImportViewModel,
    syncViewModel: SyncViewModel,
    onboardingViewModel: OnboardingViewModel,
    userPreferencesManager: com.vaibhav.moneytracker.preferences.UserPreferencesManager,
    authManager: AuthManager,
    database: MoneyTrackerDatabase
) {
    val onboardingState by onboardingViewModel.state.collectAsState()

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            onboardingViewModel.onGoogleSignInResult(account)
        } catch (e: Exception) {
            onboardingViewModel.onGoogleSignInResult(null, error = e.localizedMessage)
        }
    }

    val sheetsScopeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val hasScope = authManager.hasSheetsScopePermission()
        onboardingViewModel.onSheetsAuthorizationResult(hasScope)
    }

    if (onboardingState.status != OnboardingStatus.COMPLETE &&
        onboardingState.status != OnboardingStatus.GUEST
    ) {
        OnboardingScreen(
            state = onboardingState,
            onGoogleSignInClick = {
                onboardingViewModel.startSignIn()
                val gso = GoogleSignInOptions.Builder(
                    GoogleSignInOptions.DEFAULT_SIGN_IN
                )
                    .requestEmail()
                    .requestProfile()
                    .requestScopes(
                        Scope("https://www.googleapis.com/auth/drive.file"),
                        Scope("https://www.googleapis.com/auth/spreadsheets")
                    )
                    .build()
                val client = GoogleSignIn.getClient(activity, gso)
                googleSignInLauncher.launch(client.signInIntent)
            },
            onContinueAsGuestClick = {
                onboardingViewModel.continueAsGuest()
            },
            onRequestSheetsScopeClick = {
                val lastAccount = GoogleSignIn.getLastSignedInAccount(activity)
                if (lastAccount != null) {
                    GoogleSignIn.requestPermissions(
                        activity,
                        1001,
                        lastAccount,
                        Scope("https://www.googleapis.com/auth/drive.file"),
                        Scope("https://www.googleapis.com/auth/spreadsheets")
                    )
                    sheetsScopeLauncher.launch(activity.intent)
                } else {
                    onboardingViewModel.retryOnboarding()
                }
            },
            onRetryClick = { onboardingViewModel.retryOnboarding() },
            onSignOutClick = { onboardingViewModel.signOut() }
        )
    } else {
        MoneyTrackerApp(
            viewModel = viewModel,
            csvViewModel = csvViewModel,
            syncViewModel = syncViewModel,
            onboardingViewModel = onboardingViewModel,
            userPreferencesManager = userPreferencesManager,
            database = database
        )
    }
}


/* =====================================================
   UI MODEL
===================================================== */



/* =====================================================
   APP
===================================================== */

@Composable
fun MoneyTrackerApp(
    viewModel: MainViewModel,
    csvViewModel: CSVImportViewModel,
    syncViewModel: SyncViewModel,
    onboardingViewModel: OnboardingViewModel,
    userPreferencesManager: UserPreferencesManager,
    database: MoneyTrackerDatabase
) {

    var selectedTab by remember {
        mutableIntStateOf(0)
    }

    var showAddTransaction by remember {
        mutableStateOf(false)
    }

    var selectedTransaction by remember {
        mutableStateOf<TransactionUiModel?>(null)
    }

    var editingTransaction by remember {
        mutableStateOf<TransactionUiModel?>(null)
    }

    var selectedAccountForDetails by remember {
        mutableStateOf<AccountEntity?>(null)
    }

    var selectedGoalForDetails by remember {
        mutableStateOf<GoalEntity?>(null)
    }

    /* HISTORY FILTERS */
    var historySearchQuery by remember { mutableStateOf("") }
    var historyFilterType by remember { mutableStateOf("All") }
    var historyFilterAccountId by remember { mutableStateOf<Long?>(null) }
    var historyFilterCategory by remember { mutableStateOf("All") }
    var historyFilterTagId by remember { mutableStateOf<Long?>(null) }
    var historyFilterPeriod by remember { mutableStateOf(DashboardPeriod.ALL_TIME) }
    var historyFilterExternalKind by remember { mutableStateOf<String?>(null) }

    val transactions by viewModel.transactions.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val confirmedSubscriptions by viewModel.subscriptions.collectAsState()
    val activeBudgets by viewModel.budgets.collectAsState()
    val activeGoals by viewModel.goals.collectAsState()
    val smartRules by viewModel.smartRules.collectAsState()
    val financialPosition by viewModel.financialPosition.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val duplicateWarning by viewModel.duplicateWarning.collectAsState()
    val onboardingState by onboardingViewModel.state.collectAsState()
    val syncUiState by syncViewModel.syncState.collectAsState()

    // Suggestions remain calculated on the fly or we can move them to VM
    val recurringSuggestions = remember {
        mutableStateListOf<SubscriptionEntity>()
    }

    var isLoading by remember {
        mutableStateOf(false) // VM handles loading via empty lists initially
    }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            ensureDefaultAccounts(database)
            ensureCategories(database)
            
            val loadedSuggestions = IntelligenceEngine.detectRecurring(
                database.transactionDao().getAll(),
                database
            )
            withContext(Dispatchers.Main) {
                recurringSuggestions.clear()
                recurringSuggestions.addAll(loadedSuggestions)
            }
        }
    }

    // Close the Add Transaction screen exactly when a save succeeds (either
    // direct insert or confirmed "Save Anyway" after a duplicate warning).
    LaunchedEffect(viewModel) {
        viewModel.transactionSaved.collect {
            showAddTransaction = false
        }
    }


    /* EDIT */


    if (editingTransaction != null) {

        EditTransactionScreen(

            accounts =
                accounts,

            categories =
                categories,

            tags =
                tags,

            transaction =
                editingTransaction!!,

            onBack = {
                editingTransaction = null
            },

            onSave = { updated, updatedTags ->

                viewModel.updateTransaction(updated, updatedTags)
                editingTransaction = null
                selectedTransaction = null
            }
        )

        return
    }


    /* DETAILS */

    if (selectedTransaction != null) {

        TransactionDetailsScreen(

            transaction =
                selectedTransaction!!,

            onBack = {
                selectedTransaction = null
            },

            onEdit = {
                editingTransaction =
                    selectedTransaction
            },

            onDelete = {

                val transaction =
                    selectedTransaction!!

                viewModel.deleteTransaction(transaction.toEntity())
                selectedTransaction = null
            }
        )

        return
    }


    /* ADD */

    if (showAddTransaction) {

        // Show a confirmation dialog when a potential duplicate is detected.
        // The dialog sits on top of AddTransactionScreen because it is rendered
        // before the return, so both are in the composition tree simultaneously.
        val warning = duplicateWarning
        if (warning != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDuplicateWarning() },
                title = { Text("Possible Duplicate") },
                text = {
                    Text(
                        "A similar transaction was already recorded today:\n\n" +
                        "\"${warning.existingTransaction.title}\"" +
                        "  —  ${formatRupees(warning.existingTransaction.amountPaise)}\n\n" +
                        "Do you still want to save this transaction?"
                    )
                },
                confirmButton = {
                    Button(onClick = { viewModel.saveTransactionAnyway() }) {
                        Text("Save Anyway")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDuplicateWarning() }) {
                        Text("Cancel")
                    }
                }
            )
        }

        AddTransactionScreen(

            accounts =
                accounts,

            categories =
                categories,

            tags =
                tags,

            rules =
                smartRules,

            confirmedSubscriptions =
                confirmedSubscriptions,

            onBack = {
                // Clear any pending duplicate warning before closing the screen.
                viewModel.dismissDuplicateWarning()
                showAddTransaction = false
            },

            onSave = { transaction, txTags ->
                // Do NOT close the screen here. The screen closes via the
                // transactionSaved LaunchedEffect once the insert succeeds.
                viewModel.addTransaction(transaction, txTags)
            }
        )

        return
    }


    /* MAIN APP */

    MaterialTheme {

        Scaffold(

            bottomBar = {

                NavigationBar {

                    NavigationBarItem(
                        selected =
                            selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                        },
                        icon = {
                            Text("⌂")
                        },
                        label = {
                            Text("Home")
                        }
                    )

                    NavigationBarItem(
                        selected =
                            selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                        },
                        icon = {
                            Text("₹")
                        },
                        label = {
                            Text("History")
                        }
                    )

                    NavigationBarItem(
                        selected = false,
                        onClick = {
                            showAddTransaction = true
                        },
                        icon = {
                            Text("+")
                        },
                        label = {
                            Text("Add")
                        }
                    )

                    NavigationBarItem(
                        selected =
                            selectedTab == 3,
                        onClick = {
                            selectedTab = 3
                        },
                        icon = {
                            Text("≡")
                        },
                        label = {
                            Text("More")
                        }
                    )
                }
            }

        ) { paddingValues ->

            when (selectedTab) {

                0 -> {

                    HomeScreen(

                        modifier =
                            Modifier.padding(
                                paddingValues
                            ),

                        database =
                            database,

                        transactions =
                            transactions,

                        accounts =
                            accounts,

                        isLoading =
                            isLoading,

                        selectedPeriod =
                            selectedPeriod,

                        financialPosition =
                            financialPosition,

                        onPeriodChange = {
                            viewModel.selectPeriod(it)
                        },

                        onAddTransaction = {
                            showAddTransaction = true
                        },

                        onSeeAllHistory = {
                            selectedTab = 1
                        },

                        onManageAccounts = {
                            selectedTab = 4
                        },

                        onAccountClick = {
                            selectedAccountForDetails = it
                            selectedTab = 11
                        },

                        onTransactionClick = {
                            selectedTransaction = it
                        },

                        insights = viewModel.insights.collectAsState().value,

                        confirmedSubscriptions = confirmedSubscriptions,
                        activeBudgets = activeBudgets,
                        activeGoals = activeGoals,
                        categories = categories
                    )
                }

                1 -> {

                    HistoryScreen(

                        modifier =
                            Modifier.padding(
                                paddingValues
                            ),

                        transactions =
                            transactions,

                        accounts =
                            accounts,

                        isLoading =
                            isLoading,

                        searchQuery = historySearchQuery,
                        onSearchQueryChange = { historySearchQuery = it },

                        filterType = historyFilterType,
                        onFilterTypeChange = { historyFilterType = it },

                        filterAccountId = historyFilterAccountId,
                        onFilterAccountIdChange = { historyFilterAccountId = it },

                        filterCategory = historyFilterCategory,
                        onFilterCategoryChange = { historyFilterCategory = it },

                        filterTagId = historyFilterTagId,
                        onFilterTagIdChange = { historyFilterTagId = it },

                        filterPeriod = historyFilterPeriod,
                        onFilterPeriodChange = { historyFilterPeriod = it },

                        filterExternalKind = historyFilterExternalKind,
                        onFilterExternalKindChange = { historyFilterExternalKind = it },

                        tags = tags,

                        onTransactionClick = {
                            selectedTransaction = it
                        },

                        onDeleteTransactions = { ids, onComplete ->
                            viewModel.deleteTransactions(ids, onComplete)
                        }
                    )
                }

                3 -> {
                    MoreMenuScreen(
                        modifier = Modifier.padding(paddingValues),
                        transactions = transactions,
                        signedInUserEmail = onboardingState.user?.email,
                        syncUiState = syncUiState,
                        onProfileClick = { selectedTab = 15 },
                        onSettingsClick = { selectedTab = 16 },
                        onSyncNowClick = {
                            onboardingState.user?.let { user ->
                                syncViewModel.performSyncNow(user)
                            }
                        },
                        onSignOutClick = { onboardingViewModel.signOut() },
                        onTransactionClick = {
                            selectedTransaction = it
                        },
                        onAccountsClick = {
                            selectedTab = 4
                        },
                        onCategoriesClick = {
                            selectedTab = 5
                        },
                        onTagsClick = {
                            selectedTab = 6
                        },
                        onSubscriptionsClick = {
                            selectedTab = 7
                        },
                        onBudgetsClick = {
                            selectedTab = 8
                        },
                        onGoalsClick = {
                            selectedTab = 9
                        },
                        onRulesClick = {
                            selectedTab = 10
                        },
                        onCSVImportClick = {
                            selectedTab = 13
                        },
                        onCaptureInboxClick = {
                            selectedTab = 14
                        }
                    )
                }

                4 -> {

                    AccountManagementScreen(

                        modifier =
                            Modifier.padding(
                                paddingValues
                            ),

                        viewModel = viewModel,
                        database = database,
                        accounts = accounts,
                        onBack = { selectedTab = 3 },
                        onAccountClick = {
                            selectedAccountForDetails = it
                            selectedTab = 11
                        }
                    )
                }

                5 -> {
                    CategoryManagementScreen(
                        modifier = Modifier.padding(paddingValues),
                        viewModel = viewModel,
                        categories = categories,
                        onBack = { selectedTab = 3 }
                    )
                }

                6 -> {
                    TagManagementScreen(
                        modifier = Modifier.padding(paddingValues),
                        viewModel = viewModel,
                        tags = tags,
                        onBack = { selectedTab = 3 }
                    )
                }

                7 -> {
                    SubscriptionManagementScreen(
                        modifier = Modifier.padding(paddingValues),
                        viewModel = viewModel,
                        confirmed = confirmedSubscriptions,
                        suggestions = recurringSuggestions,
                        accounts = accounts,
                        categories = categories,
                        onBack = { selectedTab = 3 },
                        onRefreshSuggestions = {
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    val loadedSuggestions = IntelligenceEngine.detectRecurring(
                                        database.transactionDao().getAll(),
                                        database
                                    )
                                    withContext(Dispatchers.Main) {
                                        recurringSuggestions.clear()
                                        recurringSuggestions.addAll(loadedSuggestions)
                                    }
                                }
                            }
                        }
                    )
                }

                8 -> {
                    BudgetManagementScreen(
                        modifier = Modifier.padding(paddingValues),
                        viewModel = viewModel,
                        budgets = activeBudgets,
                        categories = categories,
                        onBack = { selectedTab = 3 }
                    )
                }

                9 -> {
                    GoalManagementScreen(
                        modifier = Modifier.padding(paddingValues),
                        viewModel = viewModel,
                        goals = activeGoals,
                        accounts = accounts,
                        onBack = { selectedTab = 3 },
                        onGoalClick = {
                            selectedGoalForDetails = it
                            selectedTab = 12
                        }
                    )
                }

                10 -> {
                    RuleManagementScreen(
                        modifier = Modifier.padding(paddingValues),
                        viewModel = viewModel,
                        rules = smartRules,
                        categories = categories,
                        tags = tags,
                        onBack = { selectedTab = 3 }
                    )
                }

                11 -> {
                    if (selectedAccountForDetails != null) {
                        AccountDetailsScreen(
                            modifier = Modifier.padding(paddingValues),
                            account = selectedAccountForDetails!!,
                            transactions = transactions,
                            database = database,
                            onBack = { selectedTab = 0 }, // Or previous screen
                            onTransactionClick = { selectedTransaction = it }
                        )
                    }
                }

                12 -> {
                    if (selectedGoalForDetails != null) {
                        GoalDetailsScreen(
                            modifier = Modifier.padding(paddingValues),
                            goal = selectedGoalForDetails!!,
                            viewModel = viewModel,
                            onBack = { selectedTab = 9 }
                        )
                    }
                }

                13 -> {
                    com.vaibhav.moneytracker.ui.CSVImportScreen(
                        modifier = Modifier.padding(paddingValues),
                        viewModel = csvViewModel,
                        accounts = accounts,
                        categories = categories,
                        rules = smartRules,
                        onBack = { selectedTab = 3 },
                        onImportSuccess = { selectedTab = 1 }
                    )
                }

                14 -> {
                    CaptureInboxScreen(
                        modifier = Modifier.padding(paddingValues),
                        viewModel = viewModel,
                        accounts = accounts,
                        categories = categories,
                        onBack = { selectedTab = 3 }
                    )
                }

                15 -> {
                    ProfileScreen(
                        modifier = Modifier.padding(paddingValues),
                        user = onboardingState.user,
                        isGuestMode = onboardingState.status == OnboardingStatus.GUEST,
                        syncUiState = syncUiState,
                        onSyncNowClick = {
                            onboardingState.user?.let { user ->
                                syncViewModel.performSyncNow(user)
                            }
                        },
                        onConnectGoogleClick = {
                            onboardingViewModel.signOut()
                        },
                        onSignOutClick = {
                            onboardingViewModel.signOut()
                        },
                        onBack = { selectedTab = 3 }
                    )
                }

                16 -> {
                    SettingsScreen(
                        modifier = Modifier.padding(paddingValues),
                        userPreferencesManager = userPreferencesManager,
                        accounts = accounts,
                        categories = categories,
                        onNavigateToProfile = { selectedTab = 15 },
                        onNavigateToImport = { selectedTab = 13 },
                        onNavigateToCategories = { selectedTab = 5 },
                        onNavigateToTags = { selectedTab = 6 },
                        onBack = { selectedTab = 3 }
                    )
                }
            }
        }
    }
}


/* =====================================================
   MORE MENU
===================================================== */

@Composable
fun MoreMenuScreen(
    modifier: Modifier,
    transactions: List<TransactionUiModel>,
    signedInUserEmail: String? = null,
    syncUiState: SyncUiState = SyncUiState(),
    onProfileClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onSyncNowClick: () -> Unit = {},
    onSignOutClick: (() -> Unit)? = null,
    onTransactionClick: (TransactionUiModel) -> Unit,
    onAccountsClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onTagsClick: () -> Unit,
    onSubscriptionsClick: () -> Unit,
    onBudgetsClick: () -> Unit,
    onGoalsClick: () -> Unit,
    onRulesClick: () -> Unit,
    onCSVImportClick: () -> Unit,
    onCaptureInboxClick: () -> Unit
) {

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "More Options",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Profile & Account Navigation Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onProfileClick() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "👤", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = "Profile & Account", fontWeight = FontWeight.Bold)
                        Text(text = "Manage account, cloud backup, and sync", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Settings Navigation Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSettingsClick() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⚙️", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = "Settings", fontWeight = FontWeight.Bold)
                        Text(text = "Appearance, currency, defaults & display", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Signed In User Profile Card
        if (!signedInUserEmail.isNullOrBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Signed in as", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = signedInUserEmail, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (onSignOutClick != null) {
                            TextButton(onClick = onSignOutClick) {
                                Text("Sign Out", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // Cloud Backup Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "☁️", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = "Cloud Backup", fontWeight = FontWeight.Bold)
                                val statusText = when (syncUiState.status) {
                                    SyncStatus.SYNCING -> "Syncing your data..."
                                    SyncStatus.OFFLINE -> "You're offline — changes will sync automatically"
                                    SyncStatus.PENDING_CHANGES -> "Changes waiting to sync (${syncUiState.pendingChangesCount})"
                                    SyncStatus.IDLE_NEVER_SYNCED -> "Backup not synced yet"
                                    SyncStatus.SYNC_SUCCESS -> "Backed up just now"
                                    else -> "Backup paused"
                                }
                                Text(text = statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Button(
                            onClick = onSyncNowClick,
                            enabled = syncUiState.status != SyncStatus.SYNCING,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (syncUiState.status == SyncStatus.SYNCING) "Syncing..." else "Sync Now")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Last backup: ${syncUiState.lastSyncFormatted}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!syncUiState.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = syncUiState.errorMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCaptureInboxClick() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📥",
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Capture Inbox",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Review notifications captured from PhonePe, Paytm, GPay & Banks",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAccountsClick() },
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🏦",
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Account Management",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Manage your banks and wallets",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            MoreMenuCard(title = "Subscriptions", subtitle = "Manage recurring bills", icon = "💳", onClick = onSubscriptionsClick)
        }

        item {
            MoreMenuCard(title = "Budgets", subtitle = "Set category limits", icon = "📊", onClick = onBudgetsClick)
        }

        item {
            MoreMenuCard(title = "Financial Goals", subtitle = "Track savings targets", icon = "🎯", onClick = onGoalsClick)
        }

        item {
            MoreMenuCard(title = "Smart Rules", subtitle = "Auto-classify transactions", icon = "🤖", onClick = onRulesClick)
        }

        item {
            MoreMenuCard(title = "CSV Import", subtitle = "Bulk import bank statements", icon = "📂", onClick = onCSVImportClick)
        }

        item {
            MoreMenuCard(title = "Categories", subtitle = "Manage spending types", icon = "📁", onClick = onCategoriesClick)
        }

        item {
            MoreMenuCard(title = "Tags", subtitle = "Manage transaction tags", icon = "🏷️", onClick = onTagsClick)
        }

        item {
            HorizontalDivider()
        }

        item {
            ExternalMoneySummary(
                transactions = transactions,
                onTransactionClick = onTransactionClick
            )
        }
    }
}


@Composable
fun ExternalMoneySummary(
    transactions: List<TransactionUiModel>,
    onTransactionClick: (TransactionUiModel) -> Unit
) {
    val external = transactions.filter {
        it.type == "ExternalIn" || it.type == "ExternalOut"
    }

    val moneyIn = external.filter { it.type == "ExternalIn" }.sumOf { it.amountPaise }
    val moneyOut = external.filter { it.type == "ExternalOut" }.sumOf { it.amountPaise }
    val outstanding = moneyIn - moneyOut

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "External Money",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "External Balance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatRupees(outstanding),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BalanceMiniStatPlain("In", formatRupees(moneyIn))
                    BalanceMiniStatPlain("Out", formatRupees(moneyOut))
                }
            }
        }

        if (external.isEmpty()) {
            Text("No external transactions.")
        } else {
            external.take(3).forEach { transaction ->
                TransactionRow(transaction, onClick = { onTransactionClick(transaction) })
            }
        }
    }
}


/* =====================================================
   HOME
===================================================== */

@Composable
fun HomeScreen(
    modifier: Modifier,
    database: MoneyTrackerDatabase,
    transactions: List<TransactionUiModel>,
    accounts: List<AccountEntity>,
    isLoading: Boolean,
    selectedPeriod: DashboardPeriod,
    financialPosition: FinancialPosition?,
    onPeriodChange: (DashboardPeriod) -> Unit,
    onAddTransaction: () -> Unit,
    onSeeAllHistory: () -> Unit,
    onManageAccounts: () -> Unit,
    onAccountClick: (AccountEntity) -> Unit,
    onTransactionClick: (TransactionUiModel) -> Unit,
    insights: List<IntelligenceEngine.Insight>,
    confirmedSubscriptions: List<SubscriptionEntity>,
    activeBudgets: List<BudgetEntity>,
    activeGoals: List<GoalEntity>,
    categories: List<CategoryEntity>
) {
    var periodIncome by remember { mutableStateOf(0L) }
    var periodExpense by remember { mutableStateOf(0L) }

    LaunchedEffect(transactions.size, accounts.size, selectedPeriod) {
            // Period filtered data
            val start = getPeriodStartTimestamp(selectedPeriod)
            val end = getPeriodEndTimestamp(selectedPeriod)

            val periodTransactions = transactions.filter { it.createdAt in start..end }
            periodIncome = periodTransactions.filter { it.type == "Income" }.sumOf { it.amountPaise }
            periodExpense = periodTransactions.filter { it.type == "Expense" }.sumOf { it.amountPaise }
        }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Hello, Vaibhav", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(text = "Track your finances", style = MaterialTheme.typography.bodyMedium)
                }
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "V", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        item {
            PeriodSelector(selectedPeriod = selectedPeriod, onPeriodChange = onPeriodChange)
        }

        item {
            DashboardMainCard(
                netWorth = financialPosition?.personalNetWorthPaise ?: 0L,
                physicalBalance = financialPosition?.physicalAccountBalancesPaise ?: 0L,
                income = periodIncome, 
                expense = periodExpense
            )
        }

        item {
            Button(
                onClick = onAddTransaction,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Text(text = "+ Add Transaction", fontWeight = FontWeight.Bold)
            }
        }

        item {
            UpcomingPaymentsCard(subscriptions = confirmedSubscriptions)
        }

        item {
            DashboardBudgetsCard(transactions = transactions, budgets = activeBudgets, categories = categories)
        }

        item {
            DashboardGoalsCard(goals = activeGoals, accounts = accounts)
        }

        item {
            SectionHeader(title = "Your Accounts", action = "Manage", onActionClick = onManageAccounts)
            Spacer(modifier = Modifier.height(8.dp))
            AccountsCarousel(database = database, accounts = accounts, onAccountClick = onAccountClick)
        }

        item {
            CashFlowSection(income = periodIncome, expense = periodExpense)
        }

        item {
            SpendingSection(transactions = transactions, selectedPeriod = selectedPeriod)
        }

        item {
            InsightsSection(insights = insights)
        }

        item {
            SectionHeader(title = "Recent Activity", action = "History", onActionClick = onSeeAllHistory)
            Spacer(modifier = Modifier.height(4.dp))
            if (isLoading) {
                Text(text = "Loading...")
            } else if (transactions.isEmpty()) {
                Text(text = "No transactions yet.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    transactions.take(5).forEach { tx ->
                        TransactionRow(transaction = tx, onClick = { onTransactionClick(tx) })
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}


@Composable
fun PeriodSelector(
    selectedPeriod: DashboardPeriod,
    onPeriodChange: (DashboardPeriod) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(DashboardPeriod.entries) { period ->
            SelectionChip(
                text = period.label,
                selected = selectedPeriod == period,
                onClick = { onPeriodChange(period) }
            )
        }
    }
}


@Composable
fun DashboardMainCard(
    netWorth: Long,
    physicalBalance: Long,
    income: Long,
    expense: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(text = "Personal Net Worth", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
            Text(text = formatRupees(netWorth), color = MaterialTheme.colorScheme.onPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            
            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Income", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                    Text(text = formatRupees(income), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Expenses", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(2.dp)))
                    }
                    Text(text = formatRupees(expense), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(text = "Physical Cash", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    Text(text = formatRupees(physicalBalance), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Savings Flow", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    val flow = income - expense
                    Text(text = (if (flow >= 0) "+" else "") + formatRupees(flow), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}


@Composable
fun AccountsCarousel(
    database: MoneyTrackerDatabase,
    accounts: List<AccountEntity>,
    onAccountClick: (AccountEntity) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(accounts) { account ->
            var balance by remember { mutableLongStateOf(0L) }
            LaunchedEffect(account.id) {
                balance = FinancialEngine.calculateAccountBalance(database, account)
            }

            Card(
                modifier = Modifier.width(160.dp).clickable { onAccountClick(account) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = account.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = account.type, style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = formatRupees(balance), fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}


@Composable
fun CashFlowSection(
    income: Long,
    expense: Long
) {
    Column {
        Text(text = "Cash Flow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                if (income == 0L && expense == 0L) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text(text = "No flow data for this period", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        SimpleBar(label = "Income", value = income, color = MaterialTheme.colorScheme.primary, maxValue = maxOf(income, expense))
                        SimpleBar(label = "Expense", value = expense, color = MaterialTheme.colorScheme.error, maxValue = maxOf(income, expense))
                    }
                }
            }
        }
    }
}


@Composable
fun SimpleBar(
    label: String,
    value: Long,
    color: androidx.compose.ui.graphics.Color,
    maxValue: Long
) {
    val heightFactor = if (maxValue > 0) value.toFloat() / maxValue.toFloat() else 0f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = formatRupees(value), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight(heightFactor.coerceAtLeast(0.05f))
                .background(color, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}


@Composable
fun SpendingSection(
    transactions: List<TransactionUiModel>,
    selectedPeriod: DashboardPeriod
) {
    val start = getPeriodStartTimestamp(selectedPeriod)
    val end = getPeriodEndTimestamp(selectedPeriod)

    val expenses = transactions.filter { it.type == "Expense" && it.createdAt in start..end }
    val totalExpense = expenses.sumOf { it.amountPaise }

    val categories = expenses.groupBy { it.category }
        .mapValues { it.value.sumOf { tx -> tx.amountPaise } }
        .toList()
        .sortedByDescending { it.second }

    Column {
        Text(text = "Spending by Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        if (categories.isEmpty()) {
            Text(text = "No expenses recorded", style = MaterialTheme.typography.bodySmall)
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    categories.take(4).forEach { (cat, amt) ->
                        val pct = if (totalExpense > 0) amt.toFloat() / totalExpense.toFloat() else 0f
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = cat, fontWeight = FontWeight.SemiBold)
                                Text(text = formatRupees(amt), fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                                Box(modifier = Modifier.fillMaxWidth(pct).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun InsightsSection(
    insights: List<IntelligenceEngine.Insight>
) {
    Column {
        Text(text = "Financial Insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        if (insights.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "No insights yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Add more transactions to see trends and personalized financial tips.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                insights.forEach { insight ->
                    InsightCard(icon = insight.icon, text = insight.text)
                }
            }
        }
    }
}


@Composable
fun InsightCard(icon: String, text: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}




/* =====================================================
   ADD TRANSACTION
===================================================== */

@Composable
fun AddTransactionScreen(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    rules: List<RuleWithTags>,
    confirmedSubscriptions: List<SubscriptionEntity>,
    onBack: () -> Unit,
    onSave: (TransactionEntity, List<TagEntity>) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var moneySource by remember { mutableStateOf("Personal") }
    var transactionType by remember { mutableStateOf("Expense") }
    var note by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    // Selection States
    var selectedCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()) }
    var fromAccount by remember { mutableStateOf(accounts.firstOrNull()) }
    var toAccount by remember { mutableStateOf(accounts.getOrNull(1) ?: accounts.firstOrNull()) }
    var selectedTags by remember { mutableStateOf(emptyList<TagEntity>()) }
    var selectedExternalMoneyKind by remember { mutableStateOf<String?>(null) }

    var matchedSubscription by remember { mutableStateOf<SubscriptionEntity?>(null) }

    // Rule Matching & Subscription Suggestion
    LaunchedEffect(title, amount) {
        if (title.isNotBlank()) {
            // Match Rules
            if (selectedCategory == null && selectedTags.isEmpty()) {
                val (ruleCatId, ruleTags) = IntelligenceEngine.matchRules(title, rules)
                if (ruleCatId != null) {
                    selectedCategory = categories.find { it.id == ruleCatId }
                }
                if (ruleTags.isNotEmpty()) {
                    selectedTags = ruleTags
                }
            }

            // Match Upcoming
            val amtLong = amount.toLongOrNull()?.let { it * 100L } ?: 0L
            matchedSubscription = confirmedSubscriptions.find { 
                it.name.trim().lowercase() == title.trim().lowercase() &&
                (amtLong == 0L || it.amountPaise == amtLong)
            }
        }
    }


    Scaffold(

        topBar = {

            Row(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 14.dp
                        ),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(

                    text = "‹",

                    fontSize = 38.sp,

                    modifier =
                        Modifier
                            .clickable {
                                onBack()
                            }
                            .padding(
                                end = 12.dp
                            )
                )

                Text(

                    text =
                        "Add Transaction",

                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall,

                    fontWeight =
                        FontWeight.Bold
                )
            }
        }

    ) { paddingValues ->

        LazyColumn(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        paddingValues
                    )
                    .padding(
                        horizontal = 16.dp
                    ),

            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            item {

                AmountDisplay(
                    amount =
                        amount,

                    type =
                        transactionType,

                    moneySource =
                        moneySource
                )
            }


            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Transaction Title") },
                    singleLine = true
                )
            }

            if (matchedSubscription != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Text(
                            text = "Matches upcoming: ${matchedSubscription!!.name}",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }


            /*
             * PERSONAL / EXTERNAL
             */
            item {

                Text(
                    text =
                        "Money Type",

                    fontWeight =
                        FontWeight.SemiBold
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Row(

                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    TypeButton(

                        title =
                            "Personal",

                        selected =
                            moneySource ==
                                    "Personal",

                        modifier =
                            Modifier.weight(1f)
                    ) {

                        moneySource =
                            "Personal"

                        transactionType =
                            "Expense"
                    }


                    TypeButton(

                        title =
                            "External",

                        selected =
                            moneySource ==
                                    "External",

                        modifier =
                            Modifier.weight(1f)
                    ) {

                        moneySource =
                            "External"

                        transactionType =
                            "ExternalIn"
                    }
                }
            }


            /*
             * TRANSACTION TYPE
             */
            item {

                Text(

                    text =
                        if (
                            moneySource ==
                            "External"
                        ) {

                            "External Money"

                        } else {

                            "Transaction Type"
                        },

                    fontWeight =
                        FontWeight.SemiBold
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Row(

                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    if (
                        moneySource ==
                        "Personal"
                    ) {

                        TypeButton(
                            title =
                                "Expense",

                            selected =
                                transactionType ==
                                        "Expense",

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            transactionType =
                                "Expense"
                        }

                        TypeButton(
                            title =
                                "Income",

                            selected =
                                transactionType ==
                                        "Income",

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            transactionType =
                                "Income"
                        }

                        TypeButton(
                            title =
                                "Transfer",

                            selected =
                                transactionType ==
                                        "Transfer",

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            transactionType =
                                "Transfer"
                        }

                    } else {

                        TypeButton(
                            title =
                                "Money In",

                            selected =
                                transactionType ==
                                        "ExternalIn",

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            transactionType =
                                "ExternalIn"
                        }

                        TypeButton(
                            title =
                                "Money Out",

                            selected =
                                transactionType ==
                                        "ExternalOut",

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            transactionType =
                                "ExternalOut"
                        }
                    }
                }
            }


            if (moneySource == "External") {
                item {
                    ExternalMoneyClassificationSection(
                        selectedKind = selectedExternalMoneyKind,
                        showLegacyUnclassified = false,
                        onKindSelected = { selectedExternalMoneyKind = it }
                    )
                }
            }


            /*
             * CATEGORY
             */
            item {
                Text(text = "Category", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
                        SelectionChip(
                            text = category.name,
                            selected = selectedCategory?.id == category.id,
                            onClick = { selectedCategory = category }
                        )
                    }
                }
            }


            /*
             * ACCOUNT SELECTOR
             */
            item {
                if (moneySource == "Personal") {
                    if (transactionType == "Transfer") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "From Account", fontWeight = FontWeight.SemiBold)
                            AccountSelectionList(
                                accounts = accounts,
                                selectedAccount = fromAccount,
                                onAccountSelected = { acc: AccountEntity -> fromAccount = acc }
                            )

                            Text(text = "To Account", fontWeight = FontWeight.SemiBold)
                            AccountSelectionList(
                                accounts = accounts,
                                selectedAccount = toAccount,
                                onAccountSelected = { acc: AccountEntity -> toAccount = acc }
                            )
                        }
                    } else {
                        Text(text = "Account", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        AccountSelectionList(
                            accounts = accounts,
                            selectedAccount = selectedAccount,
                            onAccountSelected = { acc: AccountEntity -> selectedAccount = acc }
                        )
                    }
                } else {
                    // For external money, we still need a target account
                    Text(text = "Handle in Account", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    AccountSelectionList(
                        accounts = accounts,
                        selectedAccount = selectedAccount,
                        onAccountSelected = { acc: AccountEntity -> selectedAccount = acc }
                    )
                }
            }


            /*
             * TAGS
             */
            item {
                Text(text = "Tags", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(tags) { tag ->
                        val isSelected = selectedTags.any { it.id == tag.id }
                        SelectionChip(
                            text = "#${tag.name}",
                            selected = isSelected,
                            onClick = {
                                val newList = if (isSelected) {
                                    selectedTags.filter { it.id != tag.id }
                                } else {
                                    selectedTags + tag
                                }
                                selectedTags = newList
                            }
                        )
                    }
                }
            }


            /*
             * NOTE
             */
            item {

                OutlinedTextField(

                    value =
                        note,

                    onValueChange = {
                        note = it
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    label = {
                        Text(
                            "Note / Person / Reason"
                        )
                    },

                    singleLine = true
                )
            }


            /*
             * KEYPAD
             */
            item {

                KeypadRow(
                    buttons =
                        listOf(
                            "1",
                            "2",
                            "3"
                        ),

                    onClick = {
                        amount += it
                    }
                )

                KeypadRow(
                    buttons =
                        listOf(
                            "4",
                            "5",
                            "6"
                        ),

                    onClick = {
                        amount += it
                    }
                )

                KeypadRow(
                    buttons =
                        listOf(
                            "7",
                            "8",
                            "9"
                        ),

                    onClick = {
                        amount += it
                    }
                )

                KeypadRow(

                    buttons =
                        listOf(
                            "⌫",
                            "0",
                            "00"
                        ),

                    onClick = {

                        when (it) {

                            "⌫" -> {

                                if (
                                    amount.isNotEmpty()
                                ) {

                                    amount =
                                        amount.dropLast(
                                            1
                                        )
                                }
                            }

                            else -> {

                                amount += it
                            }
                        }
                    }
                )
            }


            /*
             * SAVE
             */
            item {

                Button(

                    onClick = {

                        val amountValue =
                            amount.toLongOrNull()

                        if (
                            amountValue != null &&
                            amountValue > 0
                        ) {

                            if (!isExternalMoneyClassificationValid(
                                    transactionType = transactionType,
                                    externalMoneyKind = selectedExternalMoneyKind
                                )
                            ) return@Button

                            // Validation for Transfer
                            if (transactionType == "Transfer") {
                                if (fromAccount == null || toAccount == null) return@Button
                                if (fromAccount?.id == toAccount?.id) return@Button
                            } else {
                                if (selectedAccount == null) return@Button
                            }

                            val txTitle = if (title.isNotBlank()) title else {
                                if (note.isNotBlank()) note else {
                                    when (transactionType) {
                                        "ExternalIn" -> "External Money In"
                                        "ExternalOut" -> "External Money Out"
                                        else -> selectedCategory?.name ?: "General"
                                    }
                                }
                            }


                            val transaction =
                                TransactionEntity(

                                    title =
                                        txTitle,

                                    category =
                                        selectedCategory?.name ?: "General",

                                    account =
                                        if (transactionType == "Transfer") {
                                            "${fromAccount?.name} → ${toAccount?.name}"
                                        } else {
                                            selectedAccount?.name ?: ""
                                        },

                                    type =
                                        transactionType,

                                    amountPaise =
                                        amountValue *
                                                100L,

                                    note =
                                        note,

                                    createdAt =
                                        System.currentTimeMillis(),

                                    accountId =
                                        if (transactionType == "Transfer") null else selectedAccount?.id,

                                    fromAccountId =
                                        if (transactionType == "Transfer") fromAccount?.id else null,

                                    toAccountId =
                                        if (transactionType == "Transfer") toAccount?.id else null,

                                    categoryId =
                                        selectedCategory?.id,

                                    externalMoneyKind =
                                        if (transactionType == "ExternalIn" || transactionType == "ExternalOut") {
                                            selectedExternalMoneyKind
                                        } else {
                                            null
                                        }
                                )

                            onSave(transaction, selectedTags)
                        }
                    },

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),

                    shape =
                        RoundedCornerShape(18.dp)
                ) {

                    Text(
                        text =
                            "Save Transaction",

                        fontSize = 17.sp,

                        fontWeight =
                            FontWeight.SemiBold
                    )
                }
            }


            item {

                Spacer(
                    modifier =
                        Modifier.height(20.dp)
                )
            }
        }
    }
}


/* =====================================================
   EXTERNAL MONEY
===================================================== */

@Composable
fun ExternalMoneyClassificationSection(
    selectedKind: String?,
    showLegacyUnclassified: Boolean,
    onKindSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "How should this money be classified?", fontWeight = FontWeight.Bold)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                ExternalMoneyKind.HELD,
                ExternalMoneyKind.RECEIVABLE,
                ExternalMoneyKind.LIABILITY
            ).forEach { kind ->
                ClassificationChoiceCard(
                    title = externalMoneyKindLabel(kind),
                    description = externalMoneyKindDescription(kind),
                    selected = selectedKind == kind,
                    onClick = { onKindSelected(kind) }
                )
            }
        }

        if (selectedKind == null && showLegacyUnclassified) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "This is a legacy unclassified transaction. Please select a classification to update its impact on your net worth.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}


/* =====================================================
   HISTORY
===================================================== */



/* =====================================================
   EDIT TRANSACTION
===================================================== */

@Composable
fun EditTransactionScreen(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    transaction: TransactionUiModel,
    onBack: () -> Unit,
    onSave: (TransactionEntity, List<TagEntity>) -> Unit
) {
    var title by remember { mutableStateOf(transaction.title) }
    var amount by remember {
        mutableStateOf((transaction.amountPaise / 100).toString())
    }
    var transactionType by remember {
        mutableStateOf(transaction.type)
    }
    var note by remember {
        mutableStateOf(transaction.note)
    }

    var selectedCategory by remember {
        mutableStateOf(categories.find { it.id == transaction.categoryId } ?: categories.find { it.name == transaction.category })
    }
    var selectedAccount by remember {
        mutableStateOf(accounts.find { it.id == transaction.accountId } ?: accounts.firstOrNull())
    }
    var fromAccount by remember {
        mutableStateOf(accounts.find { it.id == transaction.fromAccountId } ?: accounts.firstOrNull())
    }
    var toAccount by remember {
        mutableStateOf(accounts.find { it.id == transaction.toAccountId } ?: accounts.getOrNull(1) ?: accounts.firstOrNull())
    }
    var selectedExternalMoneyKind by remember {
        mutableStateOf(transaction.externalMoneyKind)
    }

    val isExternal = transactionType == "ExternalIn" || transactionType == "ExternalOut"
    val isLegacyUnclassifiedExternal =
        (transaction.type == "ExternalIn" || transaction.type == "ExternalOut") &&
            transaction.externalMoneyKind == null

    var selectedTags by remember { mutableStateOf(transaction.tags) }


    Scaffold(

        topBar = {

            Row(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 14.dp
                        ),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(

                    text = "‹",

                    fontSize = 38.sp,

                    modifier =
                        Modifier
                            .clickable {
                                onBack()
                            }
                            .padding(
                                end = 12.dp
                            )
                )

                Text(

                    text =
                        "Edit Transaction",

                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall,

                    fontWeight =
                        FontWeight.Bold
                )
            }
        }

    ) { paddingValues ->

        LazyColumn(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        paddingValues
                    )
                    .padding(
                        horizontal = 16.dp
                    ),

            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            item {

                AmountDisplay(

                    amount =
                        amount,

                    type =
                        transactionType,

                    moneySource =
                        if (isExternal)
                            "External"
                        else
                            "Personal"
                )
            }


            item {

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Transaction Title") },
                    singleLine = true
                )
            }

            item {

                Row(

                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    if (isExternal) {

                        TypeButton(
                            title =
                                "Money In",

                            selected =
                                transactionType ==
                                        "ExternalIn",

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            transactionType =
                                "ExternalIn"
                        }

                        TypeButton(
                            title =
                                "Money Out",

                            selected =
                                transactionType ==
                                        "ExternalOut",

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            transactionType =
                                "ExternalOut"
                        }

                    } else {

                        TypeButton(
                            title =
                                "Expense",

                            selected =
                                transactionType ==
                                        "Expense",

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            transactionType =
                                "Expense"
                        }

                        TypeButton(
                            title =
                                "Income",

                            selected =
                                transactionType ==
                                        "Income",

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            transactionType =
                                "Income"
                        }

                        TypeButton(
                            title =
                                "Transfer",

                            selected =
                                transactionType ==
                                        "Transfer",

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            transactionType =
                                "Transfer"
                        }
                    }
                }
            }


            if (isExternal) {
                item {
                    ExternalMoneyClassificationSection(
                        selectedKind = selectedExternalMoneyKind,
                        showLegacyUnclassified = isLegacyUnclassifiedExternal,
                        onKindSelected = { selectedExternalMoneyKind = it }
                    )
                }
            }


            /*
             * CATEGORY
             */
            item {
                Text(text = "Category", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
                        SelectionChip(
                            text = category.name,
                            selected = selectedCategory?.id == category.id,
                            onClick = { selectedCategory = category }
                        )
                    }
                }
            }


            /*
             * ACCOUNT SELECTOR
             */
            item {
                if (!isExternal) {
                    if (transactionType == "Transfer") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "From Account", fontWeight = FontWeight.SemiBold)
                            AccountSelectionList(
                                accounts = accounts,
                                selectedAccount = fromAccount,
                                onAccountSelected = { acc: AccountEntity -> fromAccount = acc }
                            )

                            Text(text = "To Account", fontWeight = FontWeight.SemiBold)
                            AccountSelectionList(
                                accounts = accounts,
                                selectedAccount = toAccount,
                                onAccountSelected = { acc: AccountEntity -> toAccount = acc }
                            )
                        }
                    } else {
                        Text(text = "Account", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        AccountSelectionList(
                            accounts = accounts,
                            selectedAccount = selectedAccount,
                            onAccountSelected = { acc: AccountEntity -> selectedAccount = acc }
                        )
                    }
                } else {
                    Text(text = "Handle in Account", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    AccountSelectionList(
                        accounts = accounts,
                        selectedAccount = selectedAccount,
                        onAccountSelected = { acc: AccountEntity -> selectedAccount = acc }
                    )
                }
            }


            /*
             * TAGS
             */
            item {
                Text(text = "Tags", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(tags) { tag ->
                        val isSelected = selectedTags.any { it.id == tag.id }
                        SelectionChip(
                            text = "#${tag.name}",
                            selected = isSelected,
                            onClick = {
                                val newList = if (isSelected) {
                                    selectedTags.filter { it.id != tag.id }
                                } else {
                                    selectedTags + tag
                                }
                                selectedTags = newList
                            }
                        )
                    }
                }
            }


            item {

                OutlinedTextField(

                    value =
                        note,

                    onValueChange = {
                        note = it
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    label = {
                        Text(
                            "Note / Person / Reason"
                        )
                    },

                    singleLine = true
                )
            }


            item {

                KeypadRow(

                    buttons =
                        listOf(
                            "1",
                            "2",
                            "3"
                        ),

                    onClick = {
                        amount += it
                    }
                )

                KeypadRow(

                    buttons =
                        listOf(
                            "4",
                            "5",
                            "6"
                        ),

                    onClick = {
                        amount += it
                    }
                )

                KeypadRow(

                    buttons =
                        listOf(
                            "7",
                            "8",
                            "9"
                        ),

                    onClick = {
                        amount += it
                    }
                )

                KeypadRow(

                    buttons =
                        listOf(
                            "⌫",
                            "0",
                            "00"
                        ),

                    onClick = {

                        if (it == "⌫") {

                            if (
                                amount.isNotEmpty()
                            ) {

                                amount =
                                    amount.dropLast(
                                        1
                                    )
                            }

                        } else {

                            amount += it
                        }
                    }
                )
            }


            item {

                Button(

                    onClick = {

                        val value =
                            amount.toLongOrNull()

                        if (
                            value != null &&
                            value > 0
                        ) {

                            if (!isExternalMoneyClassificationValid(
                                    transactionType = transactionType,
                                    externalMoneyKind = selectedExternalMoneyKind,
                                    allowsLegacyUnclassified = isLegacyUnclassifiedExternal
                                )
                            ) return@Button

                            // Validation for Transfer
                            if (transactionType == "Transfer") {
                                if (fromAccount == null || toAccount == null) return@Button
                                if (fromAccount?.id == toAccount?.id) return@Button
                            } else {
                                if (selectedAccount == null) return@Button
                            }

                            val updated =
                                TransactionEntity(

                                    id =
                                        transaction.id,

                                    title = title.ifBlank { transaction.title },

                                    category =
                                        selectedCategory?.name ?: "General",

                                    account =
                                        if (transactionType == "Transfer") {
                                            "${fromAccount?.name} → ${toAccount?.name}"
                                        } else {
                                            selectedAccount?.name ?: ""
                                        },

                                    type =
                                        transactionType,

                                    amountPaise =
                                        value * 100L,

                                    note =
                                        note,

                                    createdAt =
                                        transaction.createdAt,

                                    accountId =
                                        if (transactionType == "Transfer") null else selectedAccount?.id,

                                    fromAccountId =
                                        if (transactionType == "Transfer") fromAccount?.id else null,

                                    toAccountId =
                                        if (transactionType == "Transfer") toAccount?.id else null,

                                    categoryId =
                                        selectedCategory?.id,

                                    externalMoneyKind =
                                        if (transactionType == "ExternalIn" || transactionType == "ExternalOut") {
                                            selectedExternalMoneyKind
                                        } else {
                                            null
                                        }
                                )

                            // Wrap and send
                            onSave(updated.copy(id = transaction.id), selectedTags) 
                            // Cross ref handled by passing back updated entity
                            // Wait, the CrossRef needs the tag list.
                            // I should change onSave to pass (TransactionEntity, List<TagEntity>)
                        }
                    },

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),

                    shape =
                        RoundedCornerShape(18.dp)
                ) {

                    Text(
                        text =
                            "Save Changes",

                        fontSize = 17.sp,

                        fontWeight =
                            FontWeight.SemiBold
                    )
                }
            }
        }
    }
}


/* =====================================================
   DETAILS
===================================================== */

@Composable
fun TransactionDetailsScreen(

    transaction:
    TransactionUiModel,

    onBack: () -> Unit,

    onEdit: () -> Unit,

    onDelete: () -> Unit
) {

    var showDeleteDialog by remember {
        mutableStateOf(false)
    }


    if (showDeleteDialog) {

        AlertDialog(

            onDismissRequest = {
                showDeleteDialog = false
            },

            title = {
                Text(
                    text =
                        "Delete transaction?"
                )
            },

            text = {
                Text(
                    text =
                        "This transaction will be permanently removed from this device."
                )
            },

            confirmButton = {

                TextButton(
                    onClick = {

                        showDeleteDialog =
                            false

                        onDelete()
                    }
                ) {

                    Text(
                        text =
                            "Delete"
                    )
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {

                        showDeleteDialog =
                            false
                    }
                ) {

                    Text(
                        text =
                            "Cancel"
                    )
                }
            }
        )
    }


    Scaffold(

        topBar = {

            Row(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 14.dp
                        ),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(

                    text = "‹",

                    fontSize = 38.sp,

                    modifier =
                        Modifier
                            .clickable {
                                onBack()
                            }
                            .padding(
                                end = 12.dp
                            )
                )

                Text(

                    text =
                        "Transaction Details",

                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall,

                    fontWeight =
                        FontWeight.Bold
                )
            }
        }

    ) { paddingValues ->

        Column(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        paddingValues
                    )
                    .padding(20.dp)
        ) {

            Card(

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(28.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (
                                transaction.type ==
                                "ExternalIn" ||
                                transaction.type ==
                                "ExternalOut"
                            ) {

                                MaterialTheme
                                    .colorScheme
                                    .secondaryContainer

                            } else {

                                MaterialTheme
                                    .colorScheme
                                    .primaryContainer
                            }
                    )
            ) {

                Column(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(

                        text =
                            transactionTypeLabel(
                                transaction.type
                            ),

                        style =
                            MaterialTheme
                                .typography
                                .labelLarge
                    )


                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )


                    Text(

                        text =
                            formatSignedAmount(
                                transaction
                            ),

                        fontSize = 38.sp,

                        fontWeight =
                            FontWeight.Bold
                    )


                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )


                    Text(

                        text =
                            transaction.title,

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,

                        fontWeight =
                            FontWeight.SemiBold
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )


            DetailItem(
                label =
                    "Category",

                value =
                    transaction.category
            )


            DetailItem(
                label =
                    "Account",

                value =
                    transaction.account
            )


            DetailItem(
                label =
                    "Date",

                value =
                    formatDate(
                        transaction.createdAt
                    )
            )

            if (transaction.type == "ExternalIn" || transaction.type == "ExternalOut") {
                DetailItem(
                    label = "Classification",
                    value = externalMoneyKindLabel(transaction.externalMoneyKind)
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = externalMoneyKindDescription(transaction.externalMoneyKind),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }


            if (
                transaction.note.isNotBlank()
            ) {

                DetailItem(
                    label =
                        "Note",

                    value =
                        transaction.note
                )
            }


            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )


            Button(

                onClick =
                    onEdit,

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(18.dp)
            ) {

                Text(
                    text =
                        "Edit Transaction"
                )
            }


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )


            OutlinedButton(

                onClick = {
                    showDeleteDialog = true
                },

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(18.dp)
            ) {

                Text(
                    text =
                        "Delete Transaction"
                )
            }
        }
    }
}


@Composable
fun UpcomingPaymentsCard(subscriptions: List<SubscriptionEntity>) {
    val next7Days = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L)
    val upcoming = subscriptions.filter { it.nextDate in System.currentTimeMillis()..next7Days }
    
    if (upcoming.isEmpty()) return

    Column {
        Text(text = "Upcoming Payments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(upcoming) { sub ->
                Card(
                    modifier = Modifier.width(200.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = sub.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = formatDate(sub.nextDate), style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = formatRupees(sub.amountPaise), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardBudgetsCard(
    transactions: List<TransactionUiModel>,
    budgets: List<BudgetEntity>,
    categories: List<CategoryEntity>
) {
    if (budgets.isEmpty()) return

    Column {
        Text(text = "Budget Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                budgets.take(3).forEach { budget ->
                    val category = categories.find { it.id == budget.categoryId }
                    val usage = IntelligenceEngine.calculateBudgetUsage(transactions, budget)
                    val pct = if (budget.limitPaise > 0) usage.toFloat() / budget.limitPaise.toFloat() else 0f
                    
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = category?.name ?: "Unknown", fontWeight = FontWeight.SemiBold)
                            Text(text = "${formatRupees(usage)} / ${formatRupees(budget.limitPaise)}", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val barColor = when {
                            pct > 1f -> MaterialTheme.colorScheme.error
                            pct > 0.8f -> androidx.compose.ui.graphics.Color(0xFFFBC02D) // Amber
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                            Box(modifier = Modifier.fillMaxWidth(pct.coerceIn(0f, 1f)).fillMaxHeight().background(barColor, RoundedCornerShape(4.dp)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardGoalsCard(goals: List<GoalEntity>, accounts: List<AccountEntity>) {
    if (goals.isEmpty()) return

    Column {
        Text(text = "Savings Goals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(goals) { goal ->
                Card(
                    modifier = Modifier.width(260.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(text = goal.name, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        val pct = if (goal.targetPaise > 0) goal.manualProgressPaise.toFloat() / goal.targetPaise.toFloat() else 0f
                        Text(text = "${(pct * 100).toInt()}% of ${formatRupees(goal.targetPaise)}", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                            Box(modifier = Modifier.fillMaxWidth(pct.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
                        }
                        
                        if (goal.linkedAccountId != null) {
                            val account = accounts.find { it.id == goal.linkedAccountId }
                            if (account != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(text = "Ref Account: ${account.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }
}


/* =====================================================
   COMPONENTS
===================================================== */

@Composable
fun AmountDisplay(
    amount: String,
    type: String,
    moneySource: String
) {

    val prefix = when (type) {

        "Income" ->
            "+ ₹"

        "ExternalIn" ->
            "+ ₹"

        "ExternalOut" ->
            "- ₹"

        "Transfer" ->
            "↔ ₹"

        else ->
            "- ₹"
    }


    val label = when {

        type == "ExternalIn" ->
            "External Money In"

        type == "ExternalOut" ->
            "External Money Out"

        type == "Transfer" ->
            "Transfer"

        type == "Income" ->
            "Income"

        else ->
            "Expense"
    }


    Column(
        modifier =
            Modifier.fillMaxWidth(),

        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Text(
            text =
                label,

            style =
                MaterialTheme
                    .typography
                    .labelLarge
        )


        Spacer(
            modifier =
                Modifier.height(4.dp)
        )


        Text(

            text =
                "$prefix${if (amount.isEmpty()) "0" else amount}",

            fontSize = 42.sp,

            fontWeight =
                FontWeight.Bold
        )
    }
}


@Composable
fun DetailItem(
    label: String,
    value: String
) {

    Column(

        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 10.dp
                )
    ) {

        Text(

            text =
                label,

            style =
                MaterialTheme
                    .typography
                    .bodySmall
        )


        Spacer(
            modifier =
                Modifier.height(3.dp)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}


@Composable
fun TypeButton(
    title: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {

    Surface(

        modifier =
            modifier
                .height(44.dp)
                .clip(
                    RoundedCornerShape(14.dp)
                )
                .clickable {
                    onClick()
                },

        color =

            if (selected) {

                MaterialTheme
                    .colorScheme
                    .primary

            } else {

                MaterialTheme
                    .colorScheme
                    .surfaceVariant
            }
    ) {

        Box(

            contentAlignment =
                Alignment.Center
        ) {

            Text(

                text =
                    title,

                fontWeight =
                    FontWeight.SemiBold,

                color =

                    if (selected) {

                        MaterialTheme
                            .colorScheme
                            .onPrimary

                    } else {

                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                    }
            )
        }
    }
}






@Composable
fun KeypadRow(
    buttons: List<String>,
    onClick: (String) -> Unit
) {

    Row(

        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 4.dp
                ),

        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        buttons.forEach { button ->

            Surface(

                modifier =
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(
                            RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            onClick(button)
                        },

                color =
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
            ) {

                Box(

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(

                        text =
                            button,

                        fontSize = 20.sp,

                        fontWeight =
                            FontWeight.SemiBold
                    )
                }
            }
        }
    }
}




/* =====================================================
   ACCOUNT DETAILS
===================================================== */

@Composable
fun AccountDetailsScreen(
    modifier: Modifier,
    account: AccountEntity,
    transactions: List<TransactionUiModel>,
    database: MoneyTrackerDatabase,
    onBack: () -> Unit,
    onTransactionClick: (TransactionUiModel) -> Unit
) {
    var balance by remember { mutableLongStateOf(0L) }

    LaunchedEffect(account.id, transactions.size) {
        balance = FinancialEngine.calculateAccountBalance(database, account)
    }

    val accountTransactions = transactions.filter { 
        it.accountId == account.id || 
        (it.accountId == null && it.account.contains(account.name)) ||
        it.fromAccountId == account.id ||
        it.toAccountId == account.id
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
                Column {
                    Text(text = account.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(text = account.type, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Current Balance", style = MaterialTheme.typography.labelMedium)
                        Text(text = formatRupees(balance), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Text(text = "Recent Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (accountTransactions.isEmpty()) {
                item { Text("No transactions for this account.", style = MaterialTheme.typography.bodySmall) }
            } else {
                items(accountTransactions) { tx ->
                    TransactionRow(transaction = tx, onClick = { onTransactionClick(tx) })
                }
            }
            
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}


/* =====================================================
   ACCOUNT MANAGEMENT
===================================================== */

@Composable
fun AccountManagementScreen(
    modifier: Modifier,
    viewModel: MainViewModel,
    database: MoneyTrackerDatabase,
    accounts: List<AccountEntity>,
    onBack: () -> Unit,
    onAccountClick: (AccountEntity) -> Unit
) {
    var showAddAccount by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }

    if (showAddAccount) {
        AddAccountDialog(
            onDismiss = { showAddAccount = false },
            onSave = { name, type, balance ->
                viewModel.addAccount(name, type, balance)
                showAddAccount = false
            }
        )
    }

    if (editingAccount != null) {
        EditAccountDialog(
            account = editingAccount!!,
            onDismiss = { editingAccount = null },
            onSave = { updated ->
                viewModel.updateAccount(updated)
                editingAccount = null
            },
            onDeactivate = { id ->
                viewModel.deactivateAccount(id)
                editingAccount = null
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
                    text = "Accounts",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        floatingActionButton = {
            Button(
                onClick = { showAddAccount = true },
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("+ Add Account")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(accounts) { account ->
                var balance by remember { mutableLongStateOf(0L) }

                LaunchedEffect(account.id) {
                    balance = FinancialEngine.calculateAccountBalance(database, account)
                }

                AccountItem(
                    account = account,
                    currentBalance = balance,
                    onClick = { onAccountClick(account) },
                    onEdit = { editingAccount = account }
                )
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun AccountItem(
    account: AccountEntity,
    currentBalance: Long,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = account.type,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatRupees(currentBalance),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (currentBalance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onEdit) { Text("Edit") }
            }
        }
    }
}

@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Bank") }
    var balance by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Account Name") })
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type (Bank/Cash/etc)") })
                OutlinedTextField(value = balance, onValueChange = { balance = it }, label = { Text("Opening Balance") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val bal = balance.toLongOrNull() ?: 0L
                onSave(name, type, bal * 100L)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EditAccountDialog(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onSave: (AccountEntity) -> Unit,
    onDeactivate: (Long) -> Unit
) {
    var name by remember { mutableStateOf(account.name) }
    var type by remember { mutableStateOf(account.type) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Account Name") })
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(account.copy(name = name, type = type))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDeactivate(account.id) }) { Text("Deactivate", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}


/* =====================================================
   CATEGORY MANAGEMENT
===================================================== */

@Composable
fun CategoryManagementScreen(
    modifier: Modifier,
    viewModel: MainViewModel,
    categories: List<CategoryEntity>,
    onBack: () -> Unit
) {
    var showAddCategory by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    if (showAddCategory) {
        AddCategoryDialog(
            onDismiss = { showAddCategory = false },
            onSave = { name ->
                viewModel.addCategory(name)
                showAddCategory = false
            }
        )
    }

    if (editingCategory != null) {
        EditCategoryDialog(
            category = editingCategory!!,
            onDismiss = { editingCategory = null },
            onSave = { updated ->
                viewModel.updateCategory(updated)
                editingCategory = null
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
                Text(text = "Categories", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        },
        floatingActionButton = {
            Button(onClick = { showAddCategory = true }, shape = RoundedCornerShape(16.dp)) {
                Text("+ Add Category")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(categories) { category ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { editingCategory = category },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = category.name, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun AddCategoryDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Category") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }) },
        confirmButton = { Button(onClick = { onSave(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EditCategoryDialog(category: CategoryEntity, onDismiss: () -> Unit, onSave: (CategoryEntity) -> Unit) {
    var name by remember { mutableStateOf(category.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Category") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }) },
        confirmButton = { Button(onClick = { onSave(category.copy(name = name)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


/* =====================================================
   TAG MANAGEMENT
===================================================== */

@Composable
fun TagManagementScreen(
    modifier: Modifier,
    viewModel: MainViewModel,
    tags: List<TagEntity>,
    onBack: () -> Unit
) {
    var showAddTag by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<TagEntity?>(null) }

    if (showAddTag) {
        AddTagDialog(
            onDismiss = { showAddTag = false },
            onSave = { name ->
                viewModel.addTag(name)
                showAddTag = false
            }
        )
    }

    if (editingTag != null) {
        EditTagDialog(
            tag = editingTag!!,
            onDismiss = { editingTag = null },
            onSave = { updated ->
                viewModel.updateTag(updated)
                editingTag = null
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
                Text(text = "Tags", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        },
        floatingActionButton = {
            Button(onClick = { showAddTag = true }, shape = RoundedCornerShape(16.dp)) {
                Text("+ Add Tag")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tags) { tag ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { editingTag = tag },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "#${tag.name}", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun AddTagDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Tag") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tag Name") }) },
        confirmButton = { Button(onClick = { onSave(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EditTagDialog(tag: TagEntity, onDismiss: () -> Unit, onSave: (TagEntity) -> Unit) {
    var name by remember { mutableStateOf(tag.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Tag") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tag Name") }) },
        confirmButton = { Button(onClick = { onSave(tag.copy(name = name)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


@Composable
fun MoreMenuCard(
    title: String,
    subtitle: String,
    icon: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}


/* =====================================================
   SUBSCRIPTION MANAGEMENT
===================================================== */

@Composable
fun SubscriptionManagementScreen(
    modifier: Modifier,
    viewModel: MainViewModel,
    confirmed: List<SubscriptionEntity>,
    suggestions: List<SubscriptionEntity>,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onBack: () -> Unit,
    onRefreshSuggestions: () -> Unit
) {
    var confirmingSuggestion by remember { mutableStateOf<SubscriptionEntity?>(null) }
    var editingSubscription by remember { mutableStateOf<SubscriptionEntity?>(null) }
    val ignoredSuggestionNames = remember { mutableStateListOf<String>() }
    val visibleSuggestions = suggestions.filterNot { it.name in ignoredSuggestionNames }

    confirmingSuggestion?.let { suggestion ->
        SubscriptionEditorDialog(
            title = "Confirm Subscription",
            initial = suggestion.copy(isConfirmed = true),
            accounts = accounts,
            categories = categories,
            onDismiss = { confirmingSuggestion = null },
            onSave = { confirmedSubscription ->
                viewModel.confirmSubscription(confirmedSubscription)
                ignoredSuggestionNames.add(suggestion.name)
                confirmingSuggestion = null
            }
        )
    }

    editingSubscription?.let { subscription ->
        SubscriptionEditorDialog(
            title = "Edit Subscription",
            initial = subscription,
            accounts = accounts,
            categories = categories,
            onDismiss = { editingSubscription = null },
            onSave = { updated ->
                viewModel.updateSubscription(updated)
                editingSubscription = null
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
                Text(text = "Recurring & Subscriptions", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (visibleSuggestions.isNotEmpty()) {
                item {
                    Text(text = "Review Suggestions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                items(visibleSuggestions) { suggestion ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(text = suggestion.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(text = "Detected ${suggestion.cadence} payment of ${formatRupees(suggestion.amountPaise)}", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        confirmingSuggestion = suggestion
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Confirm") }
                                OutlinedButton(
                                    onClick = {
                                        // Suggestions are derived, not stored; ignore only hides this review item.
                                        ignoredSuggestionNames.add(suggestion.name)
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Ignore") }
                            }
                        }
                    }
                }
            }

            item {
                Text(text = "Confirmed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (confirmed.isEmpty()) {
                item { Text("No confirmed subscriptions yet.", style = MaterialTheme.typography.bodySmall) }
            } else {
                items(confirmed) { sub ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = sub.name, fontWeight = FontWeight.Bold)
                                Text(text = "Next: ${formatDate(sub.nextDate)}", style = MaterialTheme.typography.labelSmall)
                            }
                            Text(text = formatRupees(sub.amountPaise), fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = { viewModel.recordSubscriptionPayment(sub) }) {
                                Text("Record Payment")
                            }
                            OutlinedButton(onClick = { editingSubscription = sub }) { Text("Edit") }
                            TextButton(onClick = { viewModel.deactivateSubscription(sub) }) {
                                Text("Deactivate", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionEditorDialog(
    title: String,
    initial: SubscriptionEntity,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (SubscriptionEntity) -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var amount by remember(initial.id) { mutableStateOf((initial.amountPaise / 100L).toString()) }
    var cadence by remember(initial.id) { mutableStateOf(initial.cadence) }
    var accountId by remember(initial.id) { mutableStateOf(initial.accountId ?: accounts.firstOrNull()?.id) }
    var categoryId by remember(initial.id) { mutableStateOf(initial.categoryId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") })
                Text("Cadence", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("WEEKLY", "MONTHLY", "YEARLY")) { option ->
                        SelectionChip(option, cadence == option) { cadence = option }
                    }
                }
                Text("Pay from account", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accounts) { account ->
                        SelectionChip(account.name, accountId == account.id) { accountId = account.id }
                    }
                }
                Text("Category", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { SelectionChip("None", categoryId == null) { categoryId = null } }
                    items(categories) { category ->
                        SelectionChip(category.name, categoryId == category.id) { categoryId = category.id }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && (amount.toLongOrNull() ?: 0L) > 0L && accountId != null,
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            amountPaise = amount.toLong() * 100L,
                            cadence = cadence,
                            accountId = accountId,
                            categoryId = categoryId,
                            isConfirmed = true
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


/* =====================================================
   BUDGET MANAGEMENT
===================================================== */

@Composable
fun BudgetManagementScreen(
    modifier: Modifier,
    viewModel: MainViewModel,
    budgets: List<BudgetEntity>,
    categories: List<CategoryEntity>,
    onBack: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }

    if (showAdd) {
        AddBudgetDialog(
            categories = categories,
            onDismiss = { showAdd = false },
            onSave = { categoryId, limit ->
                viewModel.addBudget(categoryId, limit)
                showAdd = false
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
                Text(text = "Monthly Budgets", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        },
        floatingActionButton = {
            Button(onClick = { showAdd = true }, shape = RoundedCornerShape(16.dp)) {
                Text("+ Add Budget")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(budgets) { budget ->
                val category = categories.find { it.id == budget.categoryId }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = category?.name ?: "Unknown", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(text = formatRupees(budget.limitPaise), fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        // Note: Usage will be calculated in Dashboard for now, here we just show limit
                        Text(text = "Limit per month", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun AddBudgetDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (Long, Long) -> Unit
) {
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: 0L) }
    var limit by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Category Budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select Category", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        SelectionChip(text = cat.name, selected = selectedCategoryId == cat.id, onClick = { selectedCategoryId = cat.id })
                    }
                }
                OutlinedTextField(value = limit, onValueChange = { limit = it }, label = { Text("Monthly Limit") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val l = limit.toLongOrNull() ?: 0L
                onSave(selectedCategoryId, l * 100L)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


/* =====================================================
   GOAL MANAGEMENT
===================================================== */

@Composable
fun GoalManagementScreen(
    modifier: Modifier,
    viewModel: MainViewModel,
    goals: List<GoalEntity>,
    accounts: List<AccountEntity>,
    onBack: () -> Unit,
    onGoalClick: (GoalEntity) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }

    if (showAdd) {
        AddGoalDialog(
            accounts = accounts,
            onDismiss = { showAdd = false },
            onSave = { name, target, linkedAccountId ->
                viewModel.addGoal(name, target, linkedAccountId)
                showAdd = false
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
                Text(text = "Savings Goals", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        },
        floatingActionButton = {
            Button(onClick = { showAdd = true }, shape = RoundedCornerShape(16.dp)) {
                Text("+ New Goal")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(goals) { goal ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onGoalClick(goal) },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(text = goal.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Saved: ${formatRupees(goal.manualProgressPaise)}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "Target: ${formatRupees(goal.targetPaise)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val pct = if (goal.targetPaise > 0) goal.manualProgressPaise.toFloat() / goal.targetPaise.toFloat() else 0f
                        Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                            Box(modifier = Modifier.fillMaxWidth(pct.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddGoalDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (String, Long, Long?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Savings Goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Goal Name (e.g. Buy Bike)") })
                OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("Target Amount") })
                Text("Link Reference Account (Optional)", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { SelectionChip(text = "None", selected = selectedAccountId == null, onClick = { selectedAccountId = null }) }
                    items(accounts) { acc ->
                        SelectionChip(text = acc.name, selected = selectedAccountId == acc.id, onClick = { selectedAccountId = acc.id })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val t = target.toLongOrNull() ?: 0L
                onSave(name, t * 100L, selectedAccountId)
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


/* =====================================================
   GOAL DETAILS
===================================================== */

@Composable
fun GoalDetailsScreen(
    modifier: Modifier,
    goal: GoalEntity,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var contributionAmount by remember { mutableStateOf("") }

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
                Text(text = goal.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Goal Progress", style = MaterialTheme.typography.labelMedium)
                    val pct = if (goal.targetPaise > 0) goal.manualProgressPaise.toFloat() / goal.targetPaise.toFloat() else 0f
                    Text(text = "${(pct * 100).toInt()}%", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))) {
                        Box(modifier = Modifier.fillMaxWidth(pct.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(text = "Saved", style = MaterialTheme.typography.labelSmall)
                            Text(text = formatRupees(goal.manualProgressPaise), fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Target", style = MaterialTheme.typography.labelSmall)
                            Text(text = formatRupees(goal.targetPaise), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Add Contribution", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = contributionAmount,
                        onValueChange = { contributionAmount = it },
                        label = { Text("Amount") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val amt = contributionAmount.toLongOrNull() ?: 0L
                            viewModel.updateGoal(goal.copy(manualProgressPaise = goal.manualProgressPaise + (amt * 100L)))
                            contributionAmount = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Add Progress") }
                }
            }
        }
    }
}


/* =====================================================
   RULE MANAGEMENT
===================================================== */

@Composable
fun RuleManagementScreen(
    modifier: Modifier,
    viewModel: MainViewModel,
    rules: List<RuleWithTags>,
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    onBack: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }

    if (showAdd) {
        AddRuleDialog(
            categories = categories,
            tags = tags,
            onDismiss = { showAdd = false },
            onSave = { pattern, catId, tagIds ->
                viewModel.addSmartRule(pattern, catId, tagIds)
                showAdd = false
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
                Text(text = "Smart Rules", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        },
        floatingActionButton = {
            Button(onClick = { showAdd = true }, shape = RoundedCornerShape(16.dp)) {
                Text("+ Add Rule")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(rules) { ruleWithTags ->
                val category = categories.find { it.id == ruleWithTags.rule.targetCategoryId }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "If title contains \"${ruleWithTags.rule.titlePattern}\"", fontWeight = FontWeight.Bold)
                        Text(text = "Set Category: ${category?.name ?: "None"}", style = MaterialTheme.typography.bodySmall)
                        if (ruleWithTags.tags.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ruleWithTags.tags.forEach { tag ->
                                    Text(text = "#${tag.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
fun AddRuleDialog(
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    onDismiss: () -> Unit,
    onSave: (String, Long?, List<Long>) -> Unit
) {
    var pattern by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var selectedTagIds by remember { mutableStateOf(emptyList<Long>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Classification Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = pattern, onValueChange = { pattern = it }, label = { Text("Title Contains (e.g. Zomato)") })
                Text("Auto-assign Category", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { SelectionChip(text = "None", selected = selectedCategoryId == null, onClick = { selectedCategoryId = null }) }
                    items(categories) { cat ->
                        SelectionChip(text = cat.name, selected = selectedCategoryId == cat.id, onClick = { selectedCategoryId = cat.id })
                    }
                }
                Text("Auto-assign Tags", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tags) { tag ->
                        val isSelected = selectedTagIds.contains(tag.id)
                        SelectionChip(text = "#${tag.name}", selected = isSelected, onClick = {
                            selectedTagIds = if (isSelected) selectedTagIds - tag.id else selectedTagIds + tag.id
                        })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(pattern, selectedCategoryId, selectedTagIds) }) { Text("Create Rule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
