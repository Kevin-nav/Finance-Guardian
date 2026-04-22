# Room Backed Transactions UI Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the preview Transactions screen with real Room-backed transactions and wire the correction sheet to the existing correction backend.

**Architecture:** Add a `TransactionsViewModel` that observes `TransactionRepository`, `CategoryDao`, and permission status, then maps domain data into stable UI models. Keep the existing Compose screen layout and components, but remove preview data and route all filter/detail/correction actions through the ViewModel. Use `TransactionCorrectionService` for category/type corrections so merchant defaults keep working.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt ViewModel, Room Flow, Coroutines StateFlow, JUnit/Robolectric, Turbine/coroutines test.

---

## Current Context

Already implemented:

- SMS ingestion stores parsed transactions in Room.
- `TransactionRepository.observeTransactions()` exposes transactions as Flow.
- `CategoryDao.observeAll()` exposes seeded categories.
- `TransactionCorrectionService.applyCorrection()` updates category/type and can save merchant defaults.
- `TransactionsRoute` has a polished layout, filters, summary card, rows, and `TransactionDetailSheet`.

Missing:

- `TransactionsRoute` still uses hardcoded preview data.
- Correction sheet save is a TODO.
- Category chips in the sheet are static instead of Room-backed.
- The screen does not distinguish empty-without-SMS-permission from empty-with-permission.

## Task 1: Add Transaction UI Models And ViewModel

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/feature/transactions/TransactionsViewModel.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/feature/transactions/TransactionsViewModelTest.kt`

**Steps:**

1. Write tests using in-memory Room, real `RoomTransactionRepository`, real `TransactionCorrectionService`, and fake permission/id/clock.
2. Cover:
   - Empty state reports no transactions and SMS permission state.
   - All filter returns all transactions grouped by date.
   - Income filter returns credit/income transactions.
   - Expenses filter excludes internal transfers and savings contributions.
   - Transfers filter returns internal transfers.
   - Unknown filter returns unknown-category or unknown-type transactions.
   - Summary totals compute income, spending, savings, and latest known balance.
   - Category names map from Room categories, falling back to `Unknown`.
3. Run focused tests and verify they fail because the ViewModel does not exist.
4. Implement `TransactionsViewModel`, `TransactionsUiState`, `TransactionFilter`, `TransactionListItem`, `TransactionGroup`, `TransactionCategoryOption`, and mapper helpers.
5. Run focused tests and make them pass.
6. Commit as `feat: add transactions view model`.

## Task 2: Make Detail Sheet Category Options Dynamic

**Files:**
- Modify: `app/src/main/java/com/kevin/financeguardian/ui/components/TransactionDetailSheet.kt`

**Steps:**

1. Add a `categoryOptions: List<String>` parameter with the current default category names as the default.
2. Replace the private hardcoded usage with the parameter.
3. Keep existing UI behavior and `onSave(selectedCategory, selectedType)` callback for this batch.
4. Run `.\gradlew.bat :app:compileDebugKotlin`.
5. Commit as `feat: support dynamic correction categories`.

## Task 3: Wire TransactionsRoute To ViewModel

**Files:**
- Modify: `app/src/main/java/com/kevin/financeguardian/feature/transactions/TransactionsRoute.kt`

**Steps:**

1. Remove preview transaction data.
2. Collect `TransactionsViewModel.uiState` with lifecycle.
3. Render filters from `uiState.filters`.
4. Render summary card from `uiState`.
5. Render grouped transactions from `uiState.groups`.
6. Use `viewModel.selectTransaction(id)` when a row is clicked.
7. Show `TransactionDetailSheet` for `uiState.selectedTransaction`.
8. Pass dynamic category names to the sheet.
9. On sheet save, call `viewModel.saveCorrection(selectedCategory, selectedType)` and close the sheet.
10. Empty state:
    - if no transactions and SMS permission denied, instruct user to grant SMS access in Settings.
    - if no transactions and SMS permission granted, say new parsed SMS transactions will appear here.
11. Run focused ViewModel tests and compile.
12. Commit as `feat: render room transactions`.

## Task 4: Final Verification

**Files:**
- No edits expected.

**Steps:**

1. Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*TransactionsViewModelTest" --tests "*TransactionCorrectionServiceTest" --tests "*FinanceGuardianDatabaseTest"
```

2. Run full suite:

```powershell
.\gradlew.bat :app:testDebugUnitTest --stacktrace
```

3. Build debug APK:

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace
```

4. Confirm `git status --short` is clean.

## Deferred Follow-Up

- Add explicit “save merchant default” checkbox in the correction sheet if Kevin wants per-correction control.
- Add custom category CRUD/archive UI.
- Wire Insights to Room-derived summaries after Transactions is real-data backed.
