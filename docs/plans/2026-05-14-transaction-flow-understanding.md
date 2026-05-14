# Transaction Flow Understanding Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build evidence-based transaction flow understanding so internal transfers are collapsed, delayed paired messages can be linked, and provider messages expose richer meaning than simple debit/credit records.

**Architecture:** Add a domain layer between provider parsing and transaction persistence. Provider parsers should emit richer transaction events, a classifier should infer channel/ownership/planned use, and a flow matcher should collapse related events into user-facing transaction flows with a 10-hour matching window.

**Tech Stack:** Kotlin, Android, Hilt, Room, DataStore, Jetpack Compose, JUnit.

---

## Reference Docs

- Design: `docs/plans/2026-05-14-transaction-flow-understanding-design.md`
- Current parser documentation: `docs/provider-message-parsing.md`
- MTN examples: `MTN_Mobile_Money.md`, `MTN-outliers.md`
- Telecel examples: `Telecel_Cash.md`, `Telcel-outliers.md`
- GCB examples: `GCB_Bank.md`, `GCB_Bank-outliers.md`

## Task 1: Add Ghana Phone Normalization

**Files:**

- Create: `app/src/main/java/com/kevin/financeguardian/domain/parser/GhanaPhoneNumberNormalizer.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/domain/parser/GhanaPhoneNumberNormalizerTest.kt`

**Step 1: Write failing tests**

Cover these cases:

- `0549037907` normalizes to `233549037907`.
- `+233 54 903 7907` normalizes to `233549037907`.
- `233549037907` stays `233549037907`.
- `054 903 7907` matches `233549037907`.
- `****4127` is marked masked/weak and not canonicalized as a full phone number.
- Non-Ghana-looking input returns null.

**Step 2: Run tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "*GhanaPhoneNumberNormalizerTest"
```

Expected: fail because the class does not exist.

**Step 3: Implement normalizer**

Implement a small object with:

- `normalize(raw: String): NormalizedGhanaPhone?`
- `sameNumber(left: String?, right: String?): Boolean`
- `isMasked(raw: String): Boolean`

Store canonical numbers as `233XXXXXXXXX`.

**Step 4: Run tests**

Run the same Gradle command.

Expected: pass.

**Step 5: Commit**

```bash
git add app/src/main/java/com/kevin/financeguardian/domain/parser/GhanaPhoneNumberNormalizer.kt app/src/test/java/com/kevin/financeguardian/domain/parser/GhanaPhoneNumberNormalizerTest.kt
git commit -m "feat: add Ghana phone normalization"
```

## Task 2: Add Owned Instrument Domain Model

**Files:**

- Modify: `app/src/main/java/com/kevin/financeguardian/domain/model/Models.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/domain/model/OwnedInstrument.kt` if keeping model types separate is cleaner.
- Test: `app/src/test/java/com/kevin/financeguardian/domain/model/OwnedInstrumentTest.kt`

**Step 1: Write failing tests**

Cover:

- Wallet instrument can hold label, provider, and canonical phone identifier.
- Users are not required to have MTN, Telecel, and GCB.
- System-inferred instruments are distinguishable from user-confirmed instruments.

**Step 2: Add enums/models**

Recommended types:

- `InstrumentType`: `WALLET`, `BANK_ACCOUNT`, `CARD`, `UNKNOWN`.
- `InstrumentProvider`: `MTN`, `TELECEL`, `GCB`, `OTHER`, `UNKNOWN`.
- `InstrumentOrigin`: `USER_CONFIRMED`, `SYSTEM_INFERRED`.
- `OwnedInstrument`.

**Step 3: Run model tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "*OwnedInstrumentTest"
```

Expected: pass.

**Step 4: Commit**

```bash
git add app/src/main/java/com/kevin/financeguardian/domain/model app/src/test/java/com/kevin/financeguardian/domain/model/OwnedInstrumentTest.kt
git commit -m "feat: add owned instrument model"
```

## Task 3: Persist User-Owned Wallets

**Files:**

- Modify: `app/src/main/java/com/kevin/financeguardian/data/preferences/UserPreferences.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/data/preferences/UserPreferencesRepository.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/data/preferences/UserPreferencesRepositoryTest.kt`

**Step 1: Write failing repository tests**

Cover:

- Saving one MTN wallet number.
- Saving only Telecel without MTN/GCB.
- Updating a label.
- Removing a wallet.
- Phone values are stored normalized.

**Step 2: Implement minimal persistence**

Because the first useful setup is wallet phone numbers, use DataStore string storage for a JSON list or delimited value. Keep the API typed so it can later move to Room without changing UI callers.

Add repository methods:

- `setOwnedWallets(wallets: List<OwnedInstrument>)`
- `addOrUpdateOwnedWallet(wallet: OwnedInstrument)`
- `removeOwnedWallet(id: String)`

**Step 3: Run tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "*UserPreferencesRepositoryTest"
```

Expected: pass.

**Step 4: Commit**

```bash
git add app/src/main/java/com/kevin/financeguardian/data/preferences app/src/test/java/com/kevin/financeguardian/data/preferences/UserPreferencesRepositoryTest.kt
git commit -m "feat: persist owned wallet preferences"
```

## Task 4: Add Transaction Event Semantics

**Files:**

- Create: `app/src/main/java/com/kevin/financeguardian/domain/parser/ParsedTransactionEvent.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/domain/parser/SmsTransactionParser.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/domain/parser/ParsedTransactionEventTest.kt`

**Step 1: Write failing model tests**

Cover:

- Event can represent a GCB bank-to-wallet debit with destination phone and planned use.
- Event can represent Telecel-to-MTN wallet transfer with destination instrument.
- Event can represent GCB card top-up with inferred card token.

**Step 2: Add semantic fields**

Recommended enums:

- `MoneyMovementChannel`: `MERCHANT_PAYMENT`, `WALLET_TO_WALLET`, `WALLET_TO_BANK`, `BANK_TO_WALLET`, `CARD_TOP_UP`, `CARD_SPEND`, `CASH_IN`, `CASH_DEPOSIT`, `AIRTIME_DATA`, `UNKNOWN`.
- `OwnershipHint`: `OWN_TO_OWN`, `OWN_TO_EXTERNAL`, `EXTERNAL_TO_OWN`, `UNKNOWN`.
- `BalanceReliability`: `RELIABLE`, `SUSPICIOUS`, `UNKNOWN`.

Keep `ParsedTransaction` for compatibility during migration, but add the event model so provider parsers can gradually return richer details.

**Step 3: Run tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "*ParsedTransactionEventTest"
```

Expected: pass.

**Step 4: Commit**

```bash
git add app/src/main/java/com/kevin/financeguardian/domain/parser app/src/test/java/com/kevin/financeguardian/domain/parser/ParsedTransactionEventTest.kt
git commit -m "feat: add parsed transaction event semantics"
```

## Task 5: Extract Richer GCB Description Facts

**Files:**

- Modify: `app/src/main/java/com/kevin/financeguardian/domain/parser/provider/GcbBankParser.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/domain/parser/provider/GcbBankParserTest.kt`

**Step 1: Add failing tests**

Cover:

- `Bank to Wallet 0549037907 food T260` extracts channel `BANK_TO_WALLET`, destination phone `233549037907`, planned use `food`, and internal id `T260`.
- `Bank to Wallet 0596447662 laundry T` extracts destination phone and planned use `laundry` without marking internal.
- `Wallet to Bank 0549037907 09FG04301` extracts channel `WALLET_TO_BANK`, source phone, and internal id.
- `VISA Card Top Up LZDXAGEE 902125000` extracts channel `CARD_TOP_UP` and card token.
- `CASH DEPOSIT BY ...` extracts channel `CASH_DEPOSIT`.
- Negative GCB balance is marked suspicious.

**Step 2: Implement description parser**

Prefer a helper inside the provider package, such as `GcbDescriptionParser`, if `GcbBankParser` becomes too crowded.

Do not classify all `Bank to Wallet` as internal. Only extract the facts.

**Step 3: Run tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "*GcbBankParserTest"
```

Expected: pass.

**Step 4: Commit**

```bash
git add app/src/main/java/com/kevin/financeguardian/domain/parser/provider/GcbBankParser.kt app/src/test/java/com/kevin/financeguardian/domain/parser/provider/GcbBankParserTest.kt
git commit -m "feat: extract richer GCB transfer facts"
```

## Task 6: Extract Richer MTN And Telecel Transfer Facts

**Files:**

- Modify: `app/src/main/java/com/kevin/financeguardian/domain/parser/provider/MtnMomoParser.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/domain/parser/provider/TelecelCashParser.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/domain/parser/provider/MtnMomoParserTest.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/domain/parser/provider/TelecelCashParserTest.kt`

**Step 1: Add failing tests**

Cover:

- Telecel send to MTN extracts destination phone, destination provider hint MTN, planned use from `Reference: Data`.
- MTN incoming transfer extracts planned use and source wallet phone when present inside the reference text.
- MTN cash-in message parses as `CASH_IN` with agent as counterparty.
- MTN airtime payment is channel `AIRTIME_DATA`.

**Step 2: Implement parser updates**

Extract facts without forcing internal transfer classification unless owned-instrument context is available in a later classifier.

**Step 3: Run provider tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "*MtnMomoParserTest" --tests "*TelecelCashParserTest"
```

Expected: pass.

**Step 4: Commit**

```bash
git add app/src/main/java/com/kevin/financeguardian/domain/parser/provider/MtnMomoParser.kt app/src/main/java/com/kevin/financeguardian/domain/parser/provider/TelecelCashParser.kt app/src/test/java/com/kevin/financeguardian/domain/parser/provider/MtnMomoParserTest.kt app/src/test/java/com/kevin/financeguardian/domain/parser/provider/TelecelCashParserTest.kt
git commit -m "feat: extract richer wallet transfer facts"
```

## Task 7: Add Flow Classifier

**Files:**

- Create: `app/src/main/java/com/kevin/financeguardian/domain/parser/TransactionFlowClassifier.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/domain/parser/TransactionFlowClassifierTest.kt`

**Step 1: Write failing tests**

Cover:

- Owned GCB `Bank to Wallet 0549037907 food` -> internal transfer, planned use `food`, excluded from spending/income.
- External GCB `Bank to Wallet 0596447662 laundry` -> expense, planned use/category signal `laundry`, included in spending.
- Owned `Wallet to Bank 0549037907` -> internal transfer.
- Telecel-to-owned-MTN message alone -> pending internal transfer candidate.
- Cash-in/cash-deposit -> cash deposit, not generic income.

**Step 2: Implement classifier**

Inputs:

- parsed transaction event.
- list of user-confirmed owned instruments.
- list of system-inferred instruments when available.

Output:

- transaction flow draft with flow type, planned use, inclusion flags, confidence, and pending match metadata.

**Step 3: Run classifier tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "*TransactionFlowClassifierTest"
```

Expected: pass.

**Step 4: Commit**

```bash
git add app/src/main/java/com/kevin/financeguardian/domain/parser/TransactionFlowClassifier.kt app/src/test/java/com/kevin/financeguardian/domain/parser/TransactionFlowClassifierTest.kt
git commit -m "feat: classify transaction flows from parsed events"
```

## Task 8: Add Flow Matching Service

**Files:**

- Create: `app/src/main/java/com/kevin/financeguardian/data/transaction/TransactionFlowMatcher.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/data/transaction/TransactionFlowMatcherTest.kt`

**Step 1: Write failing tests**

Cover:

- Telecel debit and MTN credit with same amount and owned numbers match into one flow.
- GCB account debit and prepaid card credit with same amount match into card top-up flow.
- First event becomes visible as a single flow after 1 hour if the second side is missing.
- Matching remains open for 10 hours.
- Events beyond 10 hours do not match unless exact reference evidence exists.

**Step 2: Implement matcher**

Use scoring rather than one brittle condition:

- amount match.
- opposite direction.
- compatible channels.
- own instrument match.
- near timestamp.
- provider/reference/card token hints.

The 10-hour window is a hard default for normal matching.

**Step 3: Run matcher tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "*TransactionFlowMatcherTest"
```

Expected: pass.

**Step 4: Commit**

```bash
git add app/src/main/java/com/kevin/financeguardian/data/transaction/TransactionFlowMatcher.kt app/src/test/java/com/kevin/financeguardian/data/transaction/TransactionFlowMatcherTest.kt
git commit -m "feat: match delayed transfer flow events"
```

## Task 9: Persist Flows Incrementally

**Files:**

- Modify: `app/src/main/java/com/kevin/financeguardian/data/local/entity/TransactionEntity.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/data/local/FinanceGuardianDatabase.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/data/local/DatabaseMigrations.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/data/local/dao/TransactionDao.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/data/local/mapper/EntityMappers.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/data/local/FinanceGuardianDatabaseTest.kt`

**Step 1: Decide minimal schema**

For the first implementation, prefer adding flow metadata to existing transactions unless the code becomes awkward. Add fields like:

- `flowId`
- `flowType`
- `flowStatus`
- `plannedUse`
- `includedInSpendingTotals`
- `includedInIncomeTotals`

If this causes too much duplication, create `TransactionFlowEntity` plus child event links.

**Step 2: Write migration tests**

Verify existing transactions migrate with null/default flow fields.

**Step 3: Implement migration**

Update schema version and exported Room schema.

**Step 4: Run database tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "*FinanceGuardianDatabaseTest"
```

Expected: pass.

**Step 5: Commit**

```bash
git add app/src/main/java/com/kevin/financeguardian/data/local app/src/test/java/com/kevin/financeguardian/data/local/FinanceGuardianDatabaseTest.kt app/schemas
git commit -m "feat: persist transaction flow metadata"
```

## Task 10: Integrate Flow Classification Into Ingestion

**Files:**

- Modify: `app/src/main/java/com/kevin/financeguardian/data/sms/SmsIngestionService.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/di/ParserModule.kt` or relevant DI module.
- Test: `app/src/test/java/com/kevin/financeguardian/data/sms/SmsIngestionServiceTest.kt`

**Step 1: Write failing ingestion tests**

Cover:

- Owned bank-to-wallet is persisted as internal flow and excluded from spending.
- External bank-to-wallet remains expense.
- Telecel-to-MTN first side creates pending flow.
- Matching side updates/links existing flow.

**Step 2: Wire classifier and matcher**

Load owned wallets from preferences or repository before classification.

Persist parsed event fields and flow fields without breaking existing transaction list behavior.

**Step 3: Run ingestion tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "*SmsIngestionServiceTest"
```

Expected: pass.

**Step 4: Commit**

```bash
git add app/src/main/java/com/kevin/financeguardian/data/sms/SmsIngestionService.kt app/src/main/java/com/kevin/financeguardian/di app/src/test/java/com/kevin/financeguardian/data/sms/SmsIngestionServiceTest.kt
git commit -m "feat: classify flows during sms ingestion"
```

## Task 11: Update Totals And Transaction List View Models

**Files:**

- Modify: `app/src/main/java/com/kevin/financeguardian/feature/transactions/TransactionsViewModel.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/feature/insights/InsightsViewModel.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/feature/transactions/TransactionsViewModelTest.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/feature/insights/InsightsViewModelTest.kt`

**Step 1: Write failing tests**

Cover:

- Internal transfer is excluded from expense totals.
- Internal transfer is excluded from income totals.
- External bank-to-wallet remains included in spending.
- Planned use is still available for display.

**Step 2: Update aggregations**

Use `includedInSpendingTotals` and `includedInIncomeTotals` rather than only direction/movement type.

**Step 3: Run tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "*TransactionsViewModelTest" --tests "*InsightsViewModelTest"
```

Expected: pass.

**Step 4: Commit**

```bash
git add app/src/main/java/com/kevin/financeguardian/feature/transactions app/src/main/java/com/kevin/financeguardian/feature/insights app/src/test/java/com/kevin/financeguardian/feature/transactions/TransactionsViewModelTest.kt app/src/test/java/com/kevin/financeguardian/feature/insights/InsightsViewModelTest.kt
git commit -m "feat: exclude internal flows from totals"
```

## Task 12: Add Wallet Setup In Onboarding And Settings

**Files:**

- Modify: `app/src/main/java/com/kevin/financeguardian/feature/onboarding/OnboardingRoute.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/feature/settings/SettingsRoute.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/feature/settings/SettingsViewModel.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/feature/settings/SettingsViewModelTest.kt`

**Step 1: Write view model tests**

Cover:

- Add one labeled MTN wallet.
- Add one labeled Telecel wallet.
- User can skip wallet setup.
- User can update or remove a wallet in settings.

**Step 2: Implement settings state**

Expose owned wallets in `SettingsViewModel`.

**Step 3: Implement UI**

In onboarding, add an optional wallet setup step after SMS permission. In settings, add a permanent Accounts & Wallets section.

Keep copy concise and clear that adding wallets improves internal-transfer detection.

**Step 4: Run tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "*SettingsViewModelTest"
```

Expected: pass.

**Step 5: Commit**

```bash
git add app/src/main/java/com/kevin/financeguardian/feature/onboarding app/src/main/java/com/kevin/financeguardian/feature/settings app/src/test/java/com/kevin/financeguardian/feature/settings/SettingsViewModelTest.kt
git commit -m "feat: add owned wallet setup"
```

## Task 13: Update Transaction Detail Correction

**Files:**

- Modify: `app/src/main/java/com/kevin/financeguardian/ui/components/TransactionDetailSheet.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/data/transaction/TransactionCorrectionService.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/data/transaction/TransactionCorrectionServiceTest.kt`

**Step 1: Write failing tests**

Cover:

- User can correct internal transfer to expense.
- User can correct expense to internal transfer.
- User can update planned use/category.
- Correction updates future learning signals where appropriate.

**Step 2: Implement correction support**

Allow correction of flow type and inclusion flags, not only category/movement type.

**Step 3: Run tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "*TransactionCorrectionServiceTest"
```

Expected: pass.

**Step 4: Commit**

```bash
git add app/src/main/java/com/kevin/financeguardian/ui/components/TransactionDetailSheet.kt app/src/main/java/com/kevin/financeguardian/data/transaction/TransactionCorrectionService.kt app/src/test/java/com/kevin/financeguardian/data/transaction/TransactionCorrectionServiceTest.kt
git commit -m "feat: support transaction flow corrections"
```

## Task 14: Backfill Existing Transactions

**Files:**

- Modify: `app/src/main/java/com/kevin/financeguardian/data/transaction/HistoricalTransactionRepairService.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/core/startup/AppStartupRunnerTest.kt`
- Test: add or update a historical repair service test if present.

**Step 1: Write failing backfill tests**

Cover:

- Existing GCB bank-to-wallet with owned phone becomes internal.
- Existing external bank-to-wallet remains expense.
- Existing Telecel/MTN pairs collapse when both are already stored.

**Step 2: Implement repair**

Run flow classification/matching over historical parsed transactions where enough structured data exists.

Do not overwrite user-corrected transactions.

**Step 3: Run tests**

Run:

```bash
./gradlew testDebugUnitTest --tests "*AppStartupRunnerTest"
```

Expected: pass.

**Step 4: Commit**

```bash
git add app/src/main/java/com/kevin/financeguardian/data/transaction/HistoricalTransactionRepairService.kt app/src/test/java/com/kevin/financeguardian/core/startup/AppStartupRunnerTest.kt
git commit -m "feat: backfill transaction flow classifications"
```

## Task 15: Run Full Verification

**Files:**

- No new files unless failures require fixes.

**Step 1: Run full test suite**

Run:

```bash
./gradlew testDebugUnitTest
```

Expected: pass.

**Step 2: Run lint if configured**

Run:

```bash
./gradlew lintDebug
```

Expected: pass or only known unrelated warnings.

**Step 3: Review parser docs**

Update `docs/provider-message-parsing.md` to mention transaction events and transaction flows after implementation lands.

**Step 4: Commit final docs**

```bash
git add docs/provider-message-parsing.md
git commit -m "docs: update provider parsing flow documentation"
```

## Execution Handoff

Plan complete and saved to `docs/plans/2026-05-14-transaction-flow-understanding.md`.

Two execution options:

**1. Subagent-Driven (this session)** - dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Parallel Session (separate)** - open a new session with executing-plans and batch execution with checkpoints.

Pick the execution mode before starting implementation.
