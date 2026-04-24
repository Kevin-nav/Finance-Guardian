# MTN Merchant Normalization And Dedupe Repair Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix MTN payment parsing so merchant names display correctly, merchant IDs are retained as transaction details, historical duplicates are collapsed during startup repair, and expense/income rendering follows corrected transaction intent.

**Architecture:** Normalize merchant display text at parse time and reuse the same normalization during historical repair so newly ingested and legacy rows converge on the same canonical shape. Repair existing transactions in a single startup transaction, merge duplicate rows by dedupe key, and update UI/viewmodel sign logic to prefer explicit money movement type over stale direction when they disagree.

**Tech Stack:** Kotlin, Room, Android ViewModel, JUnit/Robolectric

---

### Task 1: Merchant normalization helper

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/domain/parser/CounterpartyDetailsNormalizer.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/domain/parser/provider/MtnMomoParser.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/domain/parser/provider/MtnMomoParserTest.kt`

**Steps:**
1. Add a shared normalizer that can split `Bills.INV`-style values into display name plus merchant-ID detail text.
2. Update the MTN payment parser to use that helper for merchant-facing payment messages.
3. Add a parser test covering the exact `Payment for GHS50.50 to Bills.INV ..Current Balance...` shape.

### Task 2: Historical repair and duplicate collapse

**Files:**
- Modify: `app/src/main/java/com/kevin/financeguardian/data/transaction/HistoricalTransactionRepairService.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/core/startup/AppStartupRunnerTest.kt`

**Steps:**
1. Reuse the merchant normalization helper during historical repair.
2. Normalize stale direction values when `moneyMovementType` explicitly says income or expense.
3. Group repaired transactions by dedupe key, keep the best canonical row, and delete duplicates.
4. Extend the startup repair test to prove duplicate collapse keeps the human-readable merchant name.

### Task 3: UI sign consistency

**Files:**
- Modify: `app/src/main/java/com/kevin/financeguardian/feature/transactions/TransactionsViewModel.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/feature/categories/CategoryDetailViewModel.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/feature/insights/InsightsViewModel.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/ui/components/TransactionDetailSheet.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/feature/transactions/TransactionsViewModelTest.kt`

**Steps:**
1. Make transaction sign/rendering prefer explicit movement type when it conflicts with direction.
2. Keep the detail sheet label readable when merchant ID text is stored as transaction detail.
3. Add a regression test showing a credit-direction transaction marked as expense is rendered/filtered as an expense.
