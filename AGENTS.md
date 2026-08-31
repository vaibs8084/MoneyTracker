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

The application will eventually connect to a backend and Google Sheets.

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

## CURRENT DATABASE

The database currently uses Room.

Current database version:

2

The database contains:

TransactionEntity
AccountEntity

TransactionEntity currently supports:

- id
- title
- category
- account
- type
- amountPaise
- note
- createdAt
- fromAccountId
- toAccountId

AccountEntity currently supports:

- id
- name
- type
- openingBalancePaise
- isActive
- createdAt

Existing transactions were created before the account system existed.

Therefore nullable transfer account IDs are intentional.

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

## EXISTING FUNCTIONALITY

The application already supports:

- Adding transactions
- Local Room storage
- History
- Transaction details
- Editing transactions
- Deleting transactions
- Income
- Expense
- Transfer
- External Money In
- External Money Out

Do not break existing functionality while adding new features.

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

## CURRENT DEVELOPMENT STAGE

Current batch:

Batch 4 — Accounts and Real Balances

Batch 4 goals:

- Account management
- Account balances
- Default Kotak account
- Default Cash account
- Add account
- Edit account
- Safe account deletion/deactivation
- Proper account selector
- Proper transfer From → To
- Transfer affects both account balances
- Existing transactions remain intact

Future planned features include:

- Modern dashboard
- Search and filters
- Tags
- Budgets
- Goals
- Recurring payments
- Subscriptions
- Automatic categorization rules
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