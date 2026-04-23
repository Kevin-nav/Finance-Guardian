# Notification System Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a centralized notification system for Finance Guardian that supports privacy-aware Android system notifications, polished in-app notices, and a restrained first wave of proactive insight nudges.

**Architecture:** Introduce a notification domain layer in `core/notifications` that converts app events into policy decisions and composed content before anything touches Android notification APIs or Compose snackbars. Keep raw event production close to existing flows such as SMS ingestion, settings/security state changes, and transaction correction, but centralize notification behavior so copy, privacy rules, grouping, and rate limits stay consistent.

**Tech Stack:** Kotlin, Jetpack Compose, StateFlow, Hilt, DataStore Preferences, Android notification APIs, NotificationCompat, Room-backed transaction data, JUnit, coroutines test, Turbine, Robolectric where Android services are involved.

---

## Current Context

The current app already provides the main event sources this feature needs:

- `SmsIngestionService` returns `SmsIngestionResult` for parsed, ignored, failed, and duplicate SMS outcomes.
- `TransactionsViewModel` owns the correction flow and already dismisses the detail sheet after save.
- `SettingsViewModel` already refreshes permission state and owns app lock, screen privacy, and dev actions.
- `UserPreferencesRepository` persists app settings, but has no notification-specific preferences yet.
- `FinanceGuardianApp` and `MainActivity` are the Compose and Android entry points where in-app banners and activity-scoped Android services can be wired.

The app does not yet have:

- a notification event model
- notification channels
- a tray notification publisher
- an in-app notice host
- notification preferences
- insight evaluation logic

## Behavioral Decisions To Preserve

- Keep transaction notifications balanced, not exhaustive.
- Allow amounts on the lock screen.
- Do not show merchant names, counterparties, references, or categories on the lock screen.
- Expose simple settings only: master notifications toggle, proactive insights toggle, and show-amounts-on-lock-screen toggle.
- Keep proactive insights narrow in V1 and rate-limit them hard.

## Task 1: Add Notification Preferences

**Files:**
- Modify: `app/src/main/java/com/kevin/financeguardian/data/preferences/UserPreferences.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/data/preferences/UserPreferencesRepository.kt`
- Modify: `app/src/test/java/com/kevin/financeguardian/data/preferences/UserPreferencesRepositoryTest.kt`

**Step 1: Write the failing repository tests**

Add tests that assert the new defaults and persistence behavior:

```kotlin
@Test
fun defaultNotificationPreferences_areEnabledWithPrivateLockScreenPolicy() = runTest {
    val repository = buildRepository()

    repository.preferences.test {
        assertEquals(
            UserPreferences(
                appLockEnabled = true,
                screenPrivacyEnabled = false,
                debugParserModeEnabled = false,
                onboardingCompleted = false,
                notificationsEnabled = true,
                proactiveInsightsEnabled = true,
                showAmountsOnLockScreen = true,
            ),
            awaitItem(),
        )
    }
}
```

**Step 2: Run the focused test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*UserPreferencesRepositoryTest"
```

Expected: FAIL because the new preference fields do not exist yet.

**Step 3: Implement the new preferences**

Add these fields to `UserPreferences`:

```kotlin
val notificationsEnabled: Boolean = true,
val proactiveInsightsEnabled: Boolean = true,
val showAmountsOnLockScreen: Boolean = true,
```

Add matching `booleanPreferencesKey(...)` entries and repository setters:

```kotlin
suspend fun setNotificationsEnabled(enabled: Boolean)
suspend fun setProactiveInsightsEnabled(enabled: Boolean)
suspend fun setShowAmountsOnLockScreen(enabled: Boolean)
```

**Step 4: Run the focused test to verify it passes**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*UserPreferencesRepositoryTest"
```

Expected: PASS for the new and existing preference tests.

**Step 5: Commit**

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/preferences/UserPreferences.kt app/src/main/java/com/kevin/financeguardian/data/preferences/UserPreferencesRepository.kt app/src/test/java/com/kevin/financeguardian/data/preferences/UserPreferencesRepositoryTest.kt
git commit -m "feat: add notification preferences"
```

## Task 2: Create The Notification Domain Model

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/core/notifications/NotificationEvent.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/notifications/NotificationFamily.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/notifications/NotificationPriority.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/notifications/NotificationSurface.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/notifications/NotificationPrivacy.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/core/notifications/NotificationEventTest.kt`

**Step 1: Write the failing model test**

Create a test that proves the important event types and their stable data shape exist:

```kotlin
@Test
fun reviewNeededEvent_carriesTransactionContextWithoutLockScreenDetails() {
    val event = NotificationEvent.TransactionNeedsReview(
        transactionId = "txn-1",
        amountMinor = 2400,
        currency = "GHS",
        merchantName = "Melcom",
        occurredAt = Instant.parse("2026-04-23T10:15:30Z"),
    )

    assertEquals("txn-1", event.transactionId)
    assertEquals(NotificationFamily.ReviewNeeded, event.family)
}
```

**Step 2: Run the focused test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*NotificationEventTest"
```

Expected: FAIL because the new notification model files do not exist.

**Step 3: Implement the model**

Define a sealed `NotificationEvent` hierarchy that covers the approved V1 signals:

- `TransactionDetected`
- `TransactionNeedsReview`
- `PermissionRevoked`
- `SecurityStateChanged`
- `InsightTriggered`
- `CorrectionSaved`
- `PermissionGranted`

Support it with enums for `NotificationFamily`, `NotificationPriority`, `NotificationSurface`, and `NotificationPrivacy`.

**Step 4: Re-run the focused test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*NotificationEventTest"
```

Expected: PASS.

**Step 5: Commit**

```powershell
git add app/src/main/java/com/kevin/financeguardian/core/notifications app/src/test/java/com/kevin/financeguardian/core/notifications/NotificationEventTest.kt
git commit -m "feat: add notification event model"
```

## Task 3: Implement Policy And Composition Rules

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/core/notifications/NotificationPolicyEngine.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/notifications/NotificationDecision.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/notifications/NotificationComposer.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/notifications/ComposedNotification.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/core/notifications/NotificationPolicyEngineTest.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/core/notifications/NotificationComposerTest.kt`

**Step 1: Write failing policy tests**

Cover the agreed behavior:

```kotlin
@Test
fun duplicateEvents_areSilent() {
    val decision = engine.decide(
        event = NotificationEvent.SystemDuplicateSuppressed("sms-1"),
        context = defaultContext(),
    )

    assertEquals(NotificationSurface.Silent, decision.surface)
}

@Test
fun reviewNeededEvents_useHighPrioritySystemSurface() {
    val decision = engine.decide(
        event = reviewNeededEvent(),
        context = defaultContext(),
    )

    assertEquals(NotificationSurface.System, decision.surface)
    assertEquals(NotificationPriority.High, decision.priority)
}
```

**Step 2: Write failing composer tests**

Cover privacy-aware copy:

```kotlin
@Test
fun transactionDetected_hidesMerchantOnLockScreen() {
    val composed = composer.compose(
        event = transactionDetectedEvent(),
        decision = standardSystemDecision(),
        showAmountsOnLockScreen = true,
    )

    assertEquals("New expense detected", composed.lockScreenTitle)
    assertEquals("GHS 24.00 recorded in Finance Guardian", composed.lockScreenBody)
    assertEquals("GHS 24.00 at Melcom", composed.unlockedBody)
}
```

**Step 3: Run the focused tests to verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*NotificationPolicyEngineTest" --tests "*NotificationComposerTest"
```

Expected: FAIL because the policy and composer do not exist.

**Step 4: Implement the policy engine**

Encode the V1 rules:

- parsed transactions notify only when the event is meaningful and not suppressed by the rate limiter
- review-needed notifications are high priority
- permission/security changes notify immediately
- correction-saved and permission-granted events become in-app notices
- duplicates and ignored SMS stay silent
- insights require `proactiveInsightsEnabled`

Represent rate limiting behind a small injected interface instead of hard-coding timestamps into the engine:

```kotlin
interface NotificationRateLimiter {
    fun allow(key: String, now: Instant): Boolean
}
```

**Step 5: Implement the composer**

Create a `ComposedNotification` data class that contains:

- `systemTitle`
- `lockScreenBody`
- `unlockedBody`
- `inAppMessage`
- `actionLabel`
- `groupKey`
- `channelId`
- `privacy`

Make sure transaction notifications use amount-only lock-screen copy and richer unlocked copy.

**Step 6: Re-run the focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*NotificationPolicyEngineTest" --tests "*NotificationComposerTest"
```

Expected: PASS.

**Step 7: Commit**

```powershell
git add app/src/main/java/com/kevin/financeguardian/core/notifications app/src/test/java/com/kevin/financeguardian/core/notifications/NotificationPolicyEngineTest.kt app/src/test/java/com/kevin/financeguardian/core/notifications/NotificationComposerTest.kt
git commit -m "feat: add notification policy and composer"
```

## Task 4: Add Android Notification Publishing

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/core/notifications/AndroidNotificationChannels.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/notifications/SystemNotificationPublisher.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/notifications/AndroidSystemNotificationPublisher.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/di/NotificationModule.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/FinanceGuardianApplication.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/core/notifications/AndroidNotificationChannelsTest.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/core/notifications/AndroidSystemNotificationPublisherTest.kt`

**Step 1: Write the failing channel test**

Assert the four channels exist and use the right importance profile:

```kotlin
@Test
fun createChannels_registersTransactionReviewSecurityAndInsightChannels() {
    val manager = shadowOf(notificationManager)

    AndroidNotificationChannels.create(context)

    assertThat(manager.notificationChannels.map { it.id }).containsExactly(
        "transactions",
        "review_needed",
        "security",
        "insights",
    )
}
```

**Step 2: Write the failing publisher test**

Verify the publisher maps a composed notification into a real Android notification with the right channel and visibility.

**Step 3: Run the focused tests to verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AndroidNotificationChannelsTest" --tests "*AndroidSystemNotificationPublisherTest"
```

Expected: FAIL because the Android notification publisher does not exist.

**Step 4: Implement the Android channel registry**

Create four channels:

- `transactions`
- `review_needed`
- `security`
- `insights`

Map them to importance levels consistent with the approved priority model.

**Step 5: Implement the publisher**

Build notifications through `NotificationCompat.Builder`, set the correct:

- small icon
- content title
- content text
- visibility/public version
- group key
- auto-cancel behavior
- content intent

Use a public-safe version for the lock screen and a richer version for the unlocked state.

**Step 6: Register channels on app start**

In `FinanceGuardianApplication`, call the channel setup once during startup.

**Step 7: Re-run the focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AndroidNotificationChannelsTest" --tests "*AndroidSystemNotificationPublisherTest"
```

Expected: PASS.

**Step 8: Commit**

```powershell
git add app/src/main/java/com/kevin/financeguardian/core/notifications app/src/main/java/com/kevin/financeguardian/di/NotificationModule.kt app/src/main/java/com/kevin/financeguardian/FinanceGuardianApplication.kt app/src/test/java/com/kevin/financeguardian/core/notifications/AndroidNotificationChannelsTest.kt app/src/test/java/com/kevin/financeguardian/core/notifications/AndroidSystemNotificationPublisherTest.kt
git commit -m "feat: add system notification publishing"
```

## Task 5: Add In-App Notice State And Host UI

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/core/notifications/InAppNotice.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/notifications/InAppNoticeManager.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/ui/components/InAppNoticeHost.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/ui/FinanceGuardianApp.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/core/notifications/InAppNoticeManagerTest.kt`

**Step 1: Write the failing manager test**

Verify that an in-app event emits a temporary notice and clears it on dismissal.

**Step 2: Run the focused test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*InAppNoticeManagerTest"
```

Expected: FAIL because the in-app notice manager does not exist.

**Step 3: Implement the notice manager**

Use a `MutableStateFlow<List<InAppNotice>>` or a single-current-notice model with:

- `id`
- `message`
- `actionLabel`
- `severity`
- `duration`

Keep it simple for V1: one active notice at a time is acceptable.

**Step 4: Add the Compose host**

Create a top-level host in `FinanceGuardianApp` that can render notices above the current screen using a Material 3 snackbar or custom banner wrapper.

**Step 5: Re-run the focused test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*InAppNoticeManagerTest"
```

Expected: PASS.

**Step 6: Commit**

```powershell
git add app/src/main/java/com/kevin/financeguardian/core/notifications/InAppNotice.kt app/src/main/java/com/kevin/financeguardian/core/notifications/InAppNoticeManager.kt app/src/main/java/com/kevin/financeguardian/ui/components/InAppNoticeHost.kt app/src/main/java/com/kevin/financeguardian/ui/FinanceGuardianApp.kt app/src/test/java/com/kevin/financeguardian/core/notifications/InAppNoticeManagerTest.kt
git commit -m "feat: add in-app notice host"
```

## Task 6: Connect SMS Ingestion To Notification Events

**Files:**
- Modify: `app/src/main/java/com/kevin/financeguardian/data/sms/SmsIngestionService.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/data/sms/SmsIngestionResult.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/notifications/NotificationDispatcher.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/data/sms/SmsIngestionServiceNotificationTest.kt`

**Step 1: Write the failing ingestion notification test**

Add a fake dispatcher and verify:

- parsed transaction emits `TransactionDetected`
- parsed unknown/low-confidence transaction emits `TransactionNeedsReview`
- ignored and duplicate results do not emit user-visible events

**Step 2: Run the focused test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsIngestionServiceNotificationTest"
```

Expected: FAIL because ingestion does not dispatch notification events yet.

**Step 3: Implement the dispatcher seam**

Create:

```kotlin
interface NotificationDispatcher {
    suspend fun dispatch(event: NotificationEvent)
}
```

Inject it into `SmsIngestionService` and dispatch only after the Room transaction succeeds.

**Step 4: Keep `SmsIngestionResult` mechanical**

Do not overload `SmsIngestionResult` with UI copy. Keep it backend-facing and let the notification dispatcher translate backend outcomes into UX events.

**Step 5: Re-run the focused test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsIngestionServiceNotificationTest"
```

Expected: PASS.

**Step 6: Commit**

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/sms/SmsIngestionService.kt app/src/main/java/com/kevin/financeguardian/core/notifications/NotificationDispatcher.kt app/src/test/java/com/kevin/financeguardian/data/sms/SmsIngestionServiceNotificationTest.kt
git commit -m "feat: dispatch notification events from sms ingestion"
```

## Task 7: Surface In-App Notices From Transaction And Settings Flows

**Files:**
- Modify: `app/src/main/java/com/kevin/financeguardian/feature/transactions/TransactionsViewModel.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/feature/settings/SettingsRoute.kt`
- Modify: `app/src/test/java/com/kevin/financeguardian/feature/transactions/TransactionsViewModelTest.kt`
- Modify: `app/src/test/java/com/kevin/financeguardian/feature/settings/SettingsViewModelTest.kt`

**Step 1: Write the failing transaction flow test**

Assert that saving a correction emits a `CorrectionSaved` event or directly triggers an in-app notice through the new dispatcher.

**Step 2: Write the failing settings flow test**

Assert that:

- granting permissions triggers a soft in-app confirmation
- data reset and fixture import map to in-app notices instead of raw strings only

**Step 3: Run the focused tests to verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*TransactionsViewModelTest" --tests "*SettingsViewModelTest"
```

Expected: FAIL for the new notice assertions.

**Step 4: Implement the transaction notice**

After `transactionCorrectionApplier.applyCorrection(...)` succeeds, dispatch a `CorrectionSaved` event and map it to an in-app notice.

**Step 5: Implement the settings notices**

Replace or complement `dataActionMessage` with the new in-app notice system so:

- permission grant confirmations feel polished
- import/reset outcomes use the same shared notice model

Keep persistent inline strings only for long-lived status, not transient confirmations.

**Step 6: Re-run the focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*TransactionsViewModelTest" --tests "*SettingsViewModelTest"
```

Expected: PASS.

**Step 7: Commit**

```powershell
git add app/src/main/java/com/kevin/financeguardian/feature/transactions/TransactionsViewModel.kt app/src/main/java/com/kevin/financeguardian/feature/settings/SettingsViewModel.kt app/src/main/java/com/kevin/financeguardian/feature/settings/SettingsRoute.kt app/src/test/java/com/kevin/financeguardian/feature/transactions/TransactionsViewModelTest.kt app/src/test/java/com/kevin/financeguardian/feature/settings/SettingsViewModelTest.kt
git commit -m "feat: surface in-app notification notices"
```

## Task 8: Add Notification Settings UI

**Files:**
- Modify: `app/src/main/java/com/kevin/financeguardian/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/feature/settings/SettingsRoute.kt`
- Modify: `app/src/test/java/com/kevin/financeguardian/feature/settings/SettingsViewModelTest.kt`

**Step 1: Write the failing settings preference tests**

Add assertions that toggling:

- `Notifications`
- `Proactive insights`
- `Show amounts on lock screen`

persists through `UserPreferencesRepository`.

**Step 2: Run the focused test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SettingsViewModelTest"
```

Expected: FAIL because the ViewModel and UI state do not expose the new settings.

**Step 3: Implement the settings state and handlers**

Add the new values to `SettingsUiState` and add corresponding setter methods to `SettingsViewModel`.

**Step 4: Add the UI rows**

Extend the Settings screen with three rows:

- `Notifications`
- `Proactive insights`
- `Show amounts on lock screen`

Keep the copy simple and consistent with the approved product posture.

**Step 5: Re-run the focused test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SettingsViewModelTest"
```

Expected: PASS.

**Step 6: Commit**

```powershell
git add app/src/main/java/com/kevin/financeguardian/feature/settings/SettingsViewModel.kt app/src/main/java/com/kevin/financeguardian/feature/settings/SettingsRoute.kt app/src/test/java/com/kevin/financeguardian/feature/settings/SettingsViewModelTest.kt
git commit -m "feat: add notification settings"
```

## Task 9: Add The First Proactive Insight

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/core/notifications/InsightEvaluator.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/core/notifications/InsightEvaluatorTest.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/data/repository/RoomTransactionRepository.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/feature/insights/InsightsViewModel.kt`

**Step 1: Write the failing evaluator test**

Start with one insight only: unusual outgoing transaction count today.

```kotlin
@Test
fun highOutgoingBurst_triggersSingleInsightEvent() {
    val result = evaluator.evaluate(
        transactions = sampleBurstTransactions(),
        now = Instant.parse("2026-04-23T18:00:00Z"),
    )

    assertEquals(InsightKind.OutgoingBurstToday, result?.kind)
}
```

**Step 2: Run the focused test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*InsightEvaluatorTest"
```

Expected: FAIL because the insight evaluator does not exist.

**Step 3: Implement the evaluator**

Keep it intentionally narrow:

- trigger only on outgoing-transaction bursts
- suppress if the same insight fired recently
- honor `proactiveInsightsEnabled`

**Step 4: Expose the same insight in-app**

Make the Insights screen capable of rendering the same detected insight as an in-app card so the tray and in-app product speak the same language.

**Step 5: Re-run the focused test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*InsightEvaluatorTest"
```

Expected: PASS.

**Step 6: Commit**

```powershell
git add app/src/main/java/com/kevin/financeguardian/core/notifications/InsightEvaluator.kt app/src/main/java/com/kevin/financeguardian/feature/insights/InsightsViewModel.kt app/src/test/java/com/kevin/financeguardian/core/notifications/InsightEvaluatorTest.kt app/src/main/java/com/kevin/financeguardian/data/repository/RoomTransactionRepository.kt
git commit -m "feat: add proactive notification insight"
```

## Task 10: Final Verification

**Files:**
- No code changes expected.

**Step 1: Run the notification-focused unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*NotificationEventTest" --tests "*NotificationPolicyEngineTest" --tests "*NotificationComposerTest" --tests "*AndroidNotificationChannelsTest" --tests "*AndroidSystemNotificationPublisherTest" --tests "*InAppNoticeManagerTest" --tests "*SmsIngestionServiceNotificationTest" --tests "*InsightEvaluatorTest" --tests "*SettingsViewModelTest" --tests "*TransactionsViewModelTest" --tests "*UserPreferencesRepositoryTest"
```

Expected: PASS for the full notification slice.

**Step 2: Run the full unit suite**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --stacktrace
```

Expected: PASS.

**Step 3: Build the debug app**

Run:

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace
```

Expected: PASS and a debug APK at `app/build/outputs/apk/debug/app-debug.apk`.

**Step 4: Perform manual device verification**

Verify these behaviors on device:

- New high-confidence transaction shows a calm transaction alert.
- Lock screen alert shows amount only, not merchant name.
- Opening the app from the notification clears the alert.
- A low-confidence or unknown-category transaction shows `Review needed`.
- Repeated rapid transactions collapse into a grouped summary.
- Turning SMS access off surfaces a permission warning.
- Turning app lock off surfaces a security warning.
- Saving a correction shows an in-app confirmation only.
- Disabling proactive insights suppresses insight alerts.
- Insight alerts never outnumber action-needed alerts.

**Step 5: Inspect git state**

Run:

```powershell
git status --short
git log --oneline --decorate -12
```

Expected: clean working tree and a commit history that reflects the task slices above.

## Deferred Follow-Up

- Add more than one proactive insight type only after observing real user behavior.
- Consider digest-style summaries only after the core alerts are stable.
- Add richer deep links from notifications into specific transaction detail sheets.
- Add notification analytics only if the product later needs tuning, and keep it local-first if privacy requirements remain strict.
