# Local Learning Engine Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a local-first learning engine that stores user correction signals, scores future transactions using those signals, and prepares the app for recurring-pattern detection without introducing heavyweight ML.

**Architecture:** Build this in layers. First add a Room-backed learning-signal store and a service that records training examples from transaction corrections. Then add a scoring engine that combines hard rules, merchant/default history, and learning signals into category and movement suggestions with confidence thresholds. Keep the first version backend-first and explainable.

**Tech Stack:** Kotlin, Room, Hilt, coroutines, JUnit 4, Robolectric.

---

### Task 1: Add Learning Signal Schema

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/data/local/entity/LearningSignalEntity.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/local/dao/LearningSignalDao.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/data/local/FinanceGuardianDatabase.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/data/local/DatabaseMigrations.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/di/DatabaseModule.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/data/local/FinanceGuardianDatabaseTest.kt`
- Schema: `app/schemas/com.kevin.financeguardian.data.local.FinanceGuardianDatabase/3.json`

**Step 1: Write the failing database tests**

Add tests for:

- inserting and querying a `LearningSignalEntity`
- upserting a signal by a stable key
- querying signals by normalized merchant, phone, and reference

**Step 2: Run the focused database test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*FinanceGuardianDatabaseTest"
```

Expected: fail because the entity and DAO do not exist.

**Step 3: Add the new entity and DAO**

Implement `LearningSignalEntity` with:

- `id`
- `transactionId`
- `provider`
- `normalizedMerchantName`
- `normalizedPhone`
- `normalizedReference`
- `amountBucket`
- `direction`
- `moneyMovementType`
- `categoryId`
- `signalType`
- `weight`
- `createdAt`
- `updatedAt`

Add a unique `signalKey` column so repeated corrections can strengthen an existing signal instead of creating infinite duplicates.

**Step 4: Wire the entity into Room**

- add the DAO to `FinanceGuardianDatabase`
- expose it in `DatabaseModule`
- add a migration from the current DB version
- export the new Room schema json

**Step 5: Run the focused database test again**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*FinanceGuardianDatabaseTest"
```

Expected: pass.

**Step 6: Commit**

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/local/entity/LearningSignalEntity.kt app/src/main/java/com/kevin/financeguardian/data/local/dao/LearningSignalDao.kt app/src/main/java/com/kevin/financeguardian/data/local/FinanceGuardianDatabase.kt app/src/main/java/com/kevin/financeguardian/data/local/DatabaseMigrations.kt app/src/main/java/com/kevin/financeguardian/di/DatabaseModule.kt app/src/test/java/com/kevin/financeguardian/data/local/FinanceGuardianDatabaseTest.kt app/schemas/com.kevin.financeguardian.data.local.FinanceGuardianDatabase/3.json
git commit -m "feat: add learning signal storage"
```

### Task 2: Add Learning Feature Extraction Helpers

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/data/learning/LearningFeatureExtractor.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/data/learning/LearningFeatureExtractorTest.kt`

**Step 1: Write the failing tests**

Cover:

- normalized merchant extraction from transaction data
- normalized phone extraction
- normalized reference extraction
- amount bucket mapping
- stable signal-key construction

**Step 2: Run the focused test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*LearningFeatureExtractorTest"
```

Expected: fail because the extractor does not exist.

**Step 3: Implement the extractor**

Build a pure Kotlin helper that:

- reuses `MerchantNormalizer`
- converts `amountMinor` into stable amount buckets
- produces normalized learning features from transactions
- builds stable keys for merchant, reference, and merchant-plus-reference signals

**Step 4: Run the focused test again**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*LearningFeatureExtractorTest"
```

Expected: pass.

**Step 5: Commit**

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/learning/LearningFeatureExtractor.kt app/src/test/java/com/kevin/financeguardian/data/learning/LearningFeatureExtractorTest.kt
git commit -m "feat: add learning feature extraction"
```

### Task 3: Persist Learning Signals From Corrections

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/data/learning/LearningSignalRecorder.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/data/transaction/TransactionCorrectionService.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/data/transaction/TransactionCorrectionServiceTest.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/data/learning/LearningSignalRecorderTest.kt`

**Step 1: Write the failing tests**

Add tests for:

- correction creates a learning signal for category choice
- correction strengthens an existing signal instead of duplicating it
- correction stores movement-type learning information
- correction with merchant default creates merchant and reference-driven signals

**Step 2: Run the focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*TransactionCorrectionServiceTest" --tests "*LearningSignalRecorderTest"
```

Expected: fail because no recorder exists.

**Step 3: Implement `LearningSignalRecorder`**

It should:

- load the corrected transaction
- derive normalized features
- upsert signals for merchant, phone, reference, and merchant+reference
- assign strong weights to explicit user corrections

**Step 4: Inject it into `TransactionCorrectionService`**

Call the recorder after the correction and merchant-default logic succeeds.

**Step 5: Run the focused tests again**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*TransactionCorrectionServiceTest" --tests "*LearningSignalRecorderTest"
```

Expected: pass.

**Step 6: Commit**

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/learning/LearningSignalRecorder.kt app/src/main/java/com/kevin/financeguardian/data/transaction/TransactionCorrectionService.kt app/src/main/java/com/kevin/financeguardian/di/RepositoryModule.kt app/src/test/java/com/kevin/financeguardian/data/transaction/TransactionCorrectionServiceTest.kt app/src/test/java/com/kevin/financeguardian/data/learning/LearningSignalRecorderTest.kt
git commit -m "feat: record learning signals from corrections"
```

### Task 4: Add Learning Suggestion Types And Scoring Service

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/data/learning/LearningSuggestion.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/learning/CategorySuggestionService.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/data/learning/CategorySuggestionServiceTest.kt`

**Step 1: Write the failing tests**

Cover:

- exact phone match gives highest score
- exact merchant match beats provider-only hints
- exact reference match can suggest a category when merchant is generic
- merchant+reference combination beats merchant-only history
- amount-bucket mismatch weakens but does not eliminate a score
- no signals returns a null/low-confidence suggestion

**Step 2: Run the focused test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*CategorySuggestionServiceTest"
```

Expected: fail because the service does not exist.

**Step 3: Implement the scoring service**

Create a service that:

- loads matching learning signals
- aggregates category scores
- separately scores movement-type suggestions
- produces `confidence`
- returns explanation strings such as:
  - `matched merchant history`
  - `matched reference history`
  - `matched phone history`

**Step 4: Define confidence bands**

Use initial thresholds like:

- `>= 0.85` => auto-apply
- `0.55 - 0.84` => suggest only
- `< 0.55` => unknown

Keep these thresholds centralized for later tuning.

**Step 5: Run the focused test again**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*CategorySuggestionServiceTest"
```

Expected: pass.

**Step 6: Commit**

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/learning/LearningSuggestion.kt app/src/main/java/com/kevin/financeguardian/data/learning/CategorySuggestionService.kt app/src/test/java/com/kevin/financeguardian/data/learning/CategorySuggestionServiceTest.kt
git commit -m "feat: add learning-based category suggestions"
```

### Task 5: Apply Learning Suggestions During Ingestion

**Files:**
- Modify: `app/src/main/java/com/kevin/financeguardian/data/sms/SmsIngestionService.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/data/merchant/MerchantCategoryResolver.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/data/sms/SmsIngestionServiceTest.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/data/sms/SmsIngestionServiceNotificationTest.kt`

**Step 1: Write the failing ingestion tests**

Add tests for:

- high-confidence learning signal auto-applies category
- medium-confidence signal keeps transaction reviewable
- low-confidence signal falls back to current unknown behavior
- explicit merchant default still wins over learning

**Step 2: Run the focused ingestion tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsIngestionServiceTest" --tests "*SmsIngestionServiceNotificationTest"
```

Expected: fail because ingestion does not yet consult the learning service.

**Step 3: Integrate the suggestion service**

Adjust ingestion order to:

1. parse SMS
2. dedupe
3. apply hard rules / merchant default
4. call the learning suggestion service
5. auto-apply only when confidence is high and no explicit stronger rule already won

**Step 4: Preserve review behavior**

If the category came from a medium-confidence suggestion, keep the transaction in a review-needed state for future UI use. If the current data model cannot store a pending suggestion yet, keep the category unset and rely on notification/UI review behavior.

**Step 5: Run the focused ingestion tests again**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsIngestionServiceTest" --tests "*SmsIngestionServiceNotificationTest"
```

Expected: pass.

**Step 6: Commit**

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/sms/SmsIngestionService.kt app/src/main/java/com/kevin/financeguardian/data/merchant/MerchantCategoryResolver.kt app/src/test/java/com/kevin/financeguardian/data/sms/SmsIngestionServiceTest.kt app/src/test/java/com/kevin/financeguardian/data/sms/SmsIngestionServiceNotificationTest.kt
git commit -m "feat: apply learning suggestions during ingestion"
```

### Task 6: Add Startup Backfill For Existing Corrections

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/data/learning/LearningBackfillService.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/core/startup/AppStartupRunner.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/core/startup/AppStartupRunnerTest.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/data/learning/LearningBackfillServiceTest.kt`

**Step 1: Write the failing tests**

Cover:

- startup backfills learning signals from already-categorized transactions
- backfill does not duplicate existing signals
- unknown-category transactions do not create learning signals

**Step 2: Run the focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AppStartupRunnerTest" --tests "*LearningBackfillServiceTest"
```

Expected: fail because the backfill service does not exist.

**Step 3: Implement the backfill service**

It should:

- scan existing transactions
- choose only transactions with meaningful category or movement classifications
- create lower-weight backfilled signals than explicit user corrections

**Step 4: Wire it into startup**

Run it after duplicate repair so the signal store learns from the cleaned transaction history.

**Step 5: Run the focused tests again**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AppStartupRunnerTest" --tests "*LearningBackfillServiceTest"
```

Expected: pass.

**Step 6: Commit**

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/learning/LearningBackfillService.kt app/src/main/java/com/kevin/financeguardian/core/startup/AppStartupRunner.kt app/src/test/java/com/kevin/financeguardian/core/startup/AppStartupRunnerTest.kt app/src/test/java/com/kevin/financeguardian/data/learning/LearningBackfillServiceTest.kt
git commit -m "feat: backfill learning signals at startup"
```

### Task 7: Add Recurring Pattern Detection Foundations

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/data/learning/RecurringPatternDetector.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/learning/RecurringPattern.kt`
- Test: `app/src/test/java/com/kevin/financeguardian/data/learning/RecurringPatternDetectorTest.kt`

**Step 1: Write the failing tests**

Cover:

- repeated same merchant and same amount across regular intervals => subscription candidate
- repeated same merchant with variable amounts => recurring expense candidate
- repeated credits from same source => income source candidate
- sparse inconsistent activity => no recurring pattern

**Step 2: Run the focused test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*RecurringPatternDetectorTest"
```

Expected: fail because the detector does not exist.

**Step 3: Implement the detector**

Use transaction history only. Keep the algorithm simple:

- group by merchant / phone / reference
- measure interval consistency
- measure amount variance
- classify into recurring types

Do not yet auto-write anything into transactions.

**Step 4: Run the focused test again**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*RecurringPatternDetectorTest"
```

Expected: pass.

**Step 5: Commit**

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/learning/RecurringPatternDetector.kt app/src/main/java/com/kevin/financeguardian/data/learning/RecurringPattern.kt app/src/test/java/com/kevin/financeguardian/data/learning/RecurringPatternDetectorTest.kt
git commit -m "feat: detect recurring transaction patterns"
```

### Task 8: Full Verification

**Files:**
- No edits expected.

**Step 1: Run focused learning tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*LearningFeatureExtractorTest" --tests "*LearningSignalRecorderTest" --tests "*CategorySuggestionServiceTest" --tests "*LearningBackfillServiceTest" --tests "*RecurringPatternDetectorTest"
```

Expected: all pass.

**Step 2: Run ingestion and correction regressions**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsIngestionServiceTest" --tests "*SmsIngestionServiceNotificationTest" --tests "*TransactionCorrectionServiceTest" --tests "*AppStartupRunnerTest"
```

Expected: all pass.

**Step 3: Run the full unit suite**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: pass.

**Step 4: Inspect working tree**

Run:

```powershell
git status --short
git log --oneline --decorate -12
```

Expected: only intended files changed and commit history is task-oriented.

---

## Deferred Follow-Up

After this backend slice:

- add UI support for medium-confidence suggestions
- show explanation text such as `learned from your past corrections`
- tune thresholds with real usage data
- optionally promote the scoring engine into an on-device ML ranking layer later
