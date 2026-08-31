# Money Tracker - Development Instructions

## Project

This is a personal finance Android application.

The application is being built incrementally in batches.

Current stack:

- Kotlin
- Jetpack Compose
- Material 3
- Room Database
- Android Studio
- Local-first architecture

The app is currently local-first. Backend and Google Sheets synchronization are
not implemented.

## IMPORTANT DATA SAFETY RULES

Never delete existing transaction data unless the user explicitly asks for it.

Never use destructive database migrations.

Never use destructive fallbackToDestructiveMigration().

When changing the Room schema:

- Use proper migrations.
- Preserve all existing transactions.
- Preserve existing account information.
- Preserve historical transaction information.

Before making a database change, inspect the existing entities, DAOs and database version.

## FINANCIAL LOGIC

There are five transaction types:

1. Income
2. Expense
3. Transfer
4. ExternalIn
5. ExternalOut

### Income

Money that belongs to the user and is received by the user.

Income increases personal money.

### Expense

Money belonging to the user that is spent.

Expense decreases personal money.

### Transfer

Money moved between accounts owned by the user.

Example:

Kotak → Cash

A transfer must NOT:

- increase income
- increase expenses
- count as new money

A transfer must affect the source and destination account balances.

### ExternalIn

Money received that does not represent personal income.

Examples:

- Someone temporarily sends money
- Someone repays/returns money
- Money belonging to another person

ExternalIn must NOT be counted as personal income.

### ExternalOut

Money paid out that does not represent a personal expense.

Examples:

- Paying someone else's money
- Returning external money
- Money temporarily handled for someone else

ExternalOut must NOT be counted as personal expenses.

## CURRENT IMPLEMENTATION

### Architecture

- The UI is built with Jetpack Compose and Material 3.
- `MainActivity` hosts the Compose navigation and screens.
- `MainViewModel` exposes Room-backed `StateFlow` UI state and performs writes
  in `viewModelScope`.
- `MoneyRepository` coordinates the DAOs, including transaction/tag and
  categorization-rule/tag cross-reference writes.
- Room is provided by the singleton `MoneyTrackerDatabase`.
- `FinancialEngine` is the canonical balance-sheet calculator. It runs database
  balance calculations on `Dispatchers.IO` and accounts for active and
  deactivated accounts when calculating personal net worth.
- Default Kotak and Cash accounts, plus default categories, are ensured during
  app startup without removing existing data.

### Available functionality

The app currently supports:

- Local Room storage, transaction history, transaction details, editing, and deletion
- Income, Expense, Transfer, ExternalIn, and ExternalOut transactions
- Optional external-money classifications: held money, receivable, and liability
- Account creation, editing, deactivation, details, physical balances, and transfers
- Category and tag management; transactions can have multiple tags
- Subscription management and confirmed subscription tracking
- Budget management
- Goal management, including optional linked accounts and manual progress
- Categorization (smart) rules with optional category and tag assignments
- Dashboard financial position and generated insights

## CURRENT DATABASE

The database currently uses Room.

Current database version:

6

The database has these entities:

- `TransactionEntity` (`transactions`): id, title, legacy category and account
  display fields, type, amountPaise, note, createdAt, nullable fromAccountId,
  toAccountId, accountId, categoryId, and externalMoneyKind
- `AccountEntity` (`accounts`): id, name, type, openingBalancePaise, isActive,
  and createdAt
- `CategoryEntity` (`categories`): id, name, optional icon/color, and isActive
- `TagEntity` (`tags`): id, name, and isActive
- `TransactionTagCrossRef` (`transaction_tag_cross_ref`): transactionId and
  tagId composite key, with a tagId index
- `SubscriptionEntity` (`subscriptions`): id, name, amountPaise, cadence,
  nextDate, nullable categoryId/accountId, isConfirmed, and isActive
- `BudgetEntity` (`budgets`): id, categoryId, limitPaise, period, and isActive
- `GoalEntity` (`goals`): id, name, targetPaise, manualProgressPaise, optional
  targetDate/linkedAccountId, isCompleted, and isActive
- `CategorizationRuleEntity` (`categorization_rules`): id, titlePattern,
  optional targetCategoryId, and isActive
- `RuleTagCrossRef` (`rule_tag_cross_ref`): ruleId and tagId composite key,
  with a tagId index

Legacy transaction fields and nullable account/category/transfer IDs are
intentional. They preserve transactions created before the corresponding
account and category relationships existed.

### Migration history

- 1 → 2: added nullable `fromAccountId` and `toAccountId` to transactions and
  created `accounts`.
- 2 → 3: added nullable `accountId` to transactions.
- 3 → 4: added nullable `categoryId` to transactions and created `categories`,
  `tags`, and `transaction_tag_cross_ref` (including its tagId index).
- 4 → 5: created `subscriptions`, `budgets`, `goals`, `categorization_rules`,
  and `rule_tag_cross_ref` (including its tagId index).
- 5 → 6: added nullable `externalMoneyKind` to transactions. Existing values
  remain null so historic external movements are not inferred or rewritten.

## MONEY STORAGE

Money is stored as integer paise.

Do not introduce floating-point money calculations.

For example:

₹500 = 50000 paise

Avoid Double or Float for financial amounts.

## PERFORMANCE

The application should be local-first.

The UI should load from the local Room database immediately.

Network/backend/Google Sheets synchronization should happen in the background.

Do not block the main/UI thread with database or network operations.

Use coroutines and appropriate dispatchers.

The application should feel instant even when synchronization is slow.

## UI

The final application should look like a modern Android finance application.

Do NOT copy the old web application's UI.

Prioritize:

- Clean hierarchy
- Modern cards
- Smooth interactions
- Clear typography
- Responsive layouts
- Minimal unnecessary navigation
- Fast interactions
- Good empty states
- Good loading states

Avoid unnecessarily complicated screens.

## DEVELOPMENT METHOD

The project is developed in batches.

Before making a large architectural change:

1. Inspect the existing code.
2. Understand the current implementation.
3. Identify affected files.
4. Make the smallest safe change.
5. Build the project.
6. Fix compilation errors.
7. Run/test the application when possible.
8. Verify existing functionality.
9. Only then proceed to the next feature.

Do not rewrite unrelated working code.

## FUTURE PLANNED FEATURES

The following are planned, not assumed to be implemented:

- Search and filters
- Recurring payments
- CSV import
- Receipt/document scanning
- Google Sheets synchronization
- Google Drive synchronization
- Notification transaction capture
- Financial insights
- Final UI/UX optimization

## IMPORTANT

If a requested change conflicts with these rules, explain the conflict before making a destructive or architectural change.

Never silently change financial logic.

