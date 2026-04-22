# Room Backed Categories CRUD Implementation Plan

**Goal:** Replace the preview Categories screen with Room-backed categories and add MVP custom category create, edit, and archive behavior.

**Architecture:** Keep `CategoryDao` as the persistence boundary, add small DAO methods for single-category upsert, lookup, and archive, then introduce `CategoriesViewModel` to combine active categories with transaction counts from `TransactionRepository`. `CategoriesRoute` should collect ViewModel state, render the existing grid style with real data, and use a compact editor dialog for add/edit/archive.

## Current Context

Already implemented:

- Default categories are seeded into Room at startup.
- `CategoryDao.observeAll()` exposes active categories.
- Transactions already reference category ids and the Transactions screen now reads category names from Room.
- Categories screen has a polished grid, empty state, and add FAB, but it still uses preview data.

Missing:

- Categories screen does not collect Room data.
- Add category FAB is a TODO.
- Custom category edit/archive is absent.
- Category transaction counts are hardcoded preview values.

## Task 1: Extend CategoryDao

Files:

- Modify: `app/src/main/java/com/kevin/financeguardian/data/local/dao/CategoryDao.kt`
- Modify fake DAO implementations in tests as needed.

Steps:

1. Add `upsert(category: CategoryEntity)` for single category writes.
2. Add `getById(categoryId: String)` for edit preservation of `createdAt`.
3. Add `archive(categoryId: String, updatedAt: Instant)` to set `isArchived = 1`.
4. Keep `observeAll()` active-only so archived categories do not appear in selection UIs.

## Task 2: Add CategoriesViewModel

Files:

- Create: `app/src/main/java/com/kevin/financeguardian/feature/categories/CategoriesViewModel.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/feature/categories/CategoriesViewModelTest.kt`

Steps:

1. Combine `CategoryDao.observeAll()` and `TransactionRepository.observeTransactions()`.
2. Map active categories into list items with transaction counts.
3. Treat seeded default category ids as non-editable for MVP safety.
4. Allow custom category creation with trimmed non-empty names.
5. Allow custom category rename/type edit.
6. Allow custom category archive only when it has no transactions.
7. Surface validation errors in UI state instead of crashing on invalid input.

## Task 3: Wire CategoriesRoute

Files:

- Modify: `app/src/main/java/com/kevin/financeguardian/feature/categories/CategoriesRoute.kt`

Steps:

1. Remove preview category data.
2. Collect `CategoriesViewModel.uiState` with lifecycle.
3. Render count and cards from `uiState.categories`.
4. Open the editor from the FAB for add.
5. Open the editor by tapping editable custom categories.
6. Save through the ViewModel.
7. Archive through the ViewModel when the editor allows it.
8. Preserve the existing empty state, grid spacing, and card presentation.

## Task 4: Verification

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*CategoriesViewModelTest" --tests "*TransactionsViewModelTest" --tests "*FinanceGuardianDatabaseTest"
.\gradlew.bat :app:testDebugUnitTest --stacktrace
.\gradlew.bat :app:assembleDebug --stacktrace
```

Expected:

- Focused tests pass.
- Full unit suite passes.
- Debug APK builds.

## Deferred Follow-Up

- Decide whether archived categories with historical transactions should remain visible in transaction detail display.
- Add delete/merge tooling only after transaction history preservation rules are explicit.
