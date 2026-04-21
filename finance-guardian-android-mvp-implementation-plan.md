# Finance Guardian Android MVP Implementation Plan

## Summary
Build an Android-only personal finance MVP focused on the hardest foundation first: SMS ingestion into categorized transactions. The app will be Kotlin + Jetpack Compose + Room, local-first, private dev-installed, and secured with Android biometric/device credential authentication before showing financial data.

The current repo only contains `idea.md` and is not a git repository. The local machine already has Android Studio, Android SDK 36/36.1, build tools, emulator, adb, and two AVDs installed. Implementation should create the Android project in this workspace, configure the local Java/SDK paths, and use a Gradle wrapper instead of requiring global Gradle.

## Feasibility And Stack Decision
This application is feasible for a private one-user Android MVP.

The main risk is SMS access, not the Android stack. Android supports receiving incoming SMS via `Telephony.Sms.Intents.SMS_RECEIVED_ACTION`, which requires `RECEIVE_SMS`. Google Play restricts SMS permissions unless the app qualifies under permitted/exception use cases, and Play Protect may block internet-sideloaded APKs that request sensitive permissions such as `READ_SMS` or `RECEIVE_SMS`. For the MVP, use private debug/dev installation only.

Sources:
- Android SMS default-handler and restricted permission guidance: https://developer.android.com/guide/topics/permissions/default-handlers
- Google Play SMS/Call Log permission policy: https://support.google.com/googleplay/android-developer/answer/10208820
- SMS received broadcast docs: https://developer.android.com/reference/android/provider/Telephony.Sms.Intents
- Play Protect sideload warning guidance: https://developers.google.com/android/play-protect/warning-dev-guidance

Recommended stack:
- Language: Kotlin
- UI: Jetpack Compose + Material 3
- Architecture: MVVM/UDF, repository layer, Hilt DI
- Local DB: Room
- Preferences: DataStore
- Background work: BroadcastReceiver for SMS ingestion, WorkManager for later maintenance jobs
- Security: AndroidX Biometric with device credential fallback
- Cloud: defer Convex for MVP; leave a sync abstraction for later

Rejected/deferred stack choices:
- Convex now: defer. It adds auth, backend schema, encryption/sync policy, and conflict handling before validating local SMS parsing.
- React Native/Flutter: not recommended because SMS receiver, permissions, Room, and Android-only background behavior are core to the app. Native Kotlin will be simpler and more reliable.
- Notification listener instead of SMS permissions: not recommended. Notification content can be redacted on newer Android versions and is not the source of truth.
- Becoming default SMS app: not recommended for MVP because it would require implementing SMS app behavior the product does not need.

## Environment Setup Plan
Use existing Android Studio and SDK.

1. Set local environment for the implementation session:
   - `ANDROID_HOME=C:\Users\Kevin\AppData\Local\Android\Sdk`
   - Add to PATH for the session:
     - `%ANDROID_HOME%\platform-tools`
     - `%ANDROID_HOME%\cmdline-tools\latest\bin`
   - Use Android Studio bundled JBR:
     - `C:\Program Files\Android\Android Studio\jbr`

2. Do not install global Gradle.
   - Create/use `gradlew.bat`.
   - Verify with `.\gradlew.bat --version`.

3. SDK state already present:
   - `platforms;android-36`
   - `platforms;android-36.1`
   - `build-tools;35.0.0`, `36.0.0`, `36.1.0`
   - `platform-tools`
   - AVDs: `Medium_Phone_API_36.1`, `Pixel_9_Pro_XL`

4. If SDK warnings persist from `sdkmanager`, update command-line tools during implementation:
   - Run Android Studio SDK Manager update, or
   - `sdkmanager.bat --update`
   - This is setup work, not app logic.

## Public Interfaces And Types

### Android Package
Use:
- App name: `Finance Guardian`
- Package: `com.kevin.financeguardian`
- Minimum SDK: 26
- Compile SDK: 36.1 if supported by generated AGP setup, otherwise 36
- Target SDK: 36

### Permissions
Declare only what MVP needs:
- `android.permission.RECEIVE_SMS`
- `android.permission.READ_SMS` only if importing historical SMS is included later
- `android.permission.POST_NOTIFICATIONS` for Android 13+ alerts
- No contacts, call log, accessibility, or notification listener permissions

Runtime flow:
1. Show in-app disclosure explaining SMS access is used only to detect financial transactions.
2. Request SMS permission.
3. If denied, app remains usable with manual transaction entry/import fixtures.
4. Do not request SMS permission until the user enters onboarding and sees the reason.

### Core Domain Models
Create Room entities and matching domain models:

- `Transaction`
  - `id: String`
  - `sourceMessageId: String?`
  - `provider: Provider`
  - `rawSender: String`
  - `rawBodyHash: String`
  - `occurredAt: Instant`
  - `direction: TransactionDirection`
  - `moneyMovementType: MoneyMovementType`
  - `amountMinor: Long`
  - `currency: String`
  - `counterpartyName: String?`
  - `counterpartyPhone: String?`
  - `reference: String?`
  - `balanceAfterMinor: Long?`
  - `categoryId: String?`
  - `confidence: Float`
  - `createdAt: Instant`
  - `updatedAt: Instant`

- `Merchant`
  - `id: String`
  - `displayName: String`
  - `normalizedName: String`
  - `phone: String?`
  - `defaultCategoryId: String?`
  - `createdFromTransactionId: String?`

- `Category`
  - `id: String`
  - `name: String`
  - `type: CategoryType`
  - Seed defaults: Food, Transport, Airtime/Data, Bills, Subscriptions, Laundry, Family, Transfers, Income, Savings, Unknown

- `SmsMessageRecord`
  - `id: String`
  - `sender: String`
  - `bodyHash: String`
  - `receivedAt: Instant`
  - `processedAt: Instant?`
  - `parseStatus: ParseStatus`
  - Store raw body only if user enables debug mode; default should avoid raw SMS persistence.

- `ParserRule`
  - `id: String`
  - `provider: Provider`
  - `name: String`
  - `enabled: Boolean`
  - Used for versioning local parser behavior, not dynamic regex editing in MVP.

Enums:
- `Provider`: `MTN_MOMO`, `TELECEL_CASH`, `GCB`, `UNKNOWN_BANK`, `UNKNOWN`
- `TransactionDirection`: `DEBIT`, `CREDIT`
- `MoneyMovementType`: `EXPENSE`, `INCOME`, `INTERNAL_TRANSFER`, `SAVINGS_CONTRIBUTION`, `SUBSCRIPTION_CANDIDATE`, `UNKNOWN`
- `CategoryType`: `EXPENSE`, `INCOME`, `TRANSFER`, `SAVINGS`
- `ParseStatus`: `PARSED`, `IGNORED`, `FAILED`, `DUPLICATE`

### Parser Interface
Create a pure Kotlin parser module with no Android dependencies:

```kotlin
interface SmsTransactionParser {
    fun parse(input: SmsParseInput): SmsParseResult
}

data class SmsParseInput(
    val sender: String,
    val body: String,
    val receivedAt: Instant
)

sealed interface SmsParseResult {
    data class Parsed(val transaction: ParsedTransaction, val confidence: Float) : SmsParseResult
    data class Ignored(val reason: String) : SmsParseResult
    data class Failed(val reason: String) : SmsParseResult
}
```

Parser priority:
1. Exact known provider sender match.
2. Provider-specific regex parser.
3. Generic Ghanaian money parser fallback.
4. Ignore OTP, promotional, and non-financial messages.

## MVP Screens
Use Compose Material 3 with a practical dashboard, not a marketing landing page.

1. Lock screen
   - Uses Android biometric/device credential prompt.
   - Blocks app content until authenticated.

2. Onboarding / permission screen
   - Explains local-only SMS transaction detection.
   - Requests SMS permission.
   - Shows current permission state.

3. Transactions screen
   - List parsed transactions.
   - Show amount, direction, provider, category, counterparty, date.
   - Unknown category rows should be visibly actionable.
   - Filters: All, Income, Expenses, Transfers, Unknown.

4. Transaction detail / correction sheet
   - Raw parsed fields.
   - Category selector.
   - Mark as internal transfer, income, expense, savings contribution, or ignore.
   - Save correction and update merchant default.

5. Categories screen
   - View seeded categories.
   - Add/rename/delete custom categories if not used, or archive if used.

6. Settings screen
   - SMS permission status.
   - App lock toggle.
   - Debug parser fixture import toggle.
   - Data reset for dev only.

## Data Flow
1. Incoming SMS received by manifest-registered `BroadcastReceiver`.
2. Receiver extracts sender, body, timestamp using Android telephony APIs.
3. Receiver immediately hands work to an injected ingestion component or WorkManager-compatible service.
4. Ingestion hashes the body, checks duplicate by sender/body hash/timestamp window.
5. Parser returns parsed/ignored/failed.
6. Parsed transaction is stored in Room inside a transaction.
7. Merchant/category suggestion is applied:
   - Exact merchant phone/name match wins.
   - Known category default applied.
   - Otherwise category is `Unknown`.
8. UI observes Room via Flow and renders state.
9. User correction updates transaction and merchant registry.

## Parser Fixture Requirement
Before implementing provider-specific parsing, collect anonymized examples from Kevin.

Required fixture format:
```json
{
  "provider": "MTN_MOMO",
  "sender": "MTN MoMo",
  "body": "ANONYMIZED ORIGINAL STRUCTURE",
  "receivedAt": "2026-04-21T12:00:00Z",
  "expected": {
    "direction": "DEBIT",
    "moneyMovementType": "EXPENSE",
    "amountMinor": 12500,
    "currency": "GHS",
    "counterpartyName": "Vendor Name",
    "counterpartyPhone": "0240000000",
    "reference": "REF123",
    "balanceAfterMinor": 90000
  }
}
```

Anonymization rule:
- Replace personal names, phone numbers, references, and balances if desired.
- Preserve wording, punctuation, amount formats, currency labels, and transaction sequence.

## Implementation Tasks
1. Create Android project
   - Native Kotlin Android app.
   - Compose Material 3.
   - Package `com.kevin.financeguardian`.
   - Add version catalog.
   - Add Gradle wrapper.

2. Configure dependencies
   - AndroidX Core, Lifecycle, Navigation Compose.
   - Compose UI/Foundation/Material3.
   - Room runtime + KSP/compiler.
   - Hilt + Hilt Navigation Compose.
   - WorkManager.
   - DataStore Preferences.
   - AndroidX Biometric.
   - Kotlinx datetime or Java time desugaring if needed.
   - JUnit, Turbine/coroutines test, AndroidX test.

3. Add app architecture
   - `data/local`
   - `data/repository`
   - `domain/model`
   - `domain/parser`
   - `feature/onboarding`
   - `feature/transactions`
   - `feature/categories`
   - `feature/settings`
   - `core/security`
   - `core/permissions`

4. Implement Room schema
   - Entities, DAOs, migrations baseline.
   - Seed default categories on first launch.
   - Add repository tests with in-memory Room.

5. Implement parser module
   - Pure JVM tests first.
   - Generic parser fallback.
   - Provider parser registry.
   - Fixture-based tests for every anonymized SMS sample.

6. Implement SMS receiver
   - Manifest receiver for `SMS_RECEIVED_ACTION`.
   - Runtime permission flow.
   - Duplicate protection.
   - Insert `SmsMessageRecord` and parsed `Transaction`.
   - Add Android instrumentation test where practical; unit-test extraction logic separately.

7. Implement app lock
   - Biometric/device credential gate on app open.
   - Store preference for enabled/disabled.
   - Default enabled for MVP.

8. Implement transaction UI
   - List, filters, detail sheet.
   - Category correction flow.
   - Internal transfer/income/expense override.
   - Empty states for no SMS permission and no transactions.

9. Implement settings/dev utilities
   - Permission status.
   - Fixture import for local parser testing.
   - Data reset in debug builds only.

10. Verification
   - `.\gradlew.bat test`
   - `.\gradlew.bat connectedDebugAndroidTest` against `Medium_Phone_API_36.1` if emulator is running.
   - Install debug APK on Samsung device via Android Studio or `adb install`.
   - Send/receive fixture-like SMS and confirm parsing/storage/UI updates.

## Test Cases And Scenarios
Parser tests:
- MTN MoMo debit parses amount, merchant, phone/ref, balance.
- MTN MoMo credit parses income candidate.
- Telecel Cash debit parses as expense.
- GCB/bank card debit parses as subscription candidate if merchant/amount recurs.
- Bank-to-MoMo transfer is `INTERNAL_TRANSFER`, not expense.
- Savings account outflow is `SAVINGS_CONTRIBUTION`.
- OTP/promotional SMS is ignored.
- Duplicate SMS does not create duplicate transaction.
- Unknown financial SMS becomes `UNKNOWN` with raw hash and parse failure reason.

Room tests:
- Insert transaction with category.
- Update category correction.
- Merchant default category applies to future transactions.
- Duplicate body hash constraints work.
- Seed categories exist after first DB open.

UI tests:
- Locked app hides transaction screen.
- Permission denied state shows manual/dev path.
- Unknown category transaction can be corrected.
- Filters return correct subsets.

Device/manual tests:
- Fresh install onboarding.
- Grant SMS permission.
- Revoke SMS permission from Android settings and verify app state.
- Receive real/anonymized test SMS.
- Rotate screen and confirm UI state is stable.
- Relaunch app and confirm biometric/device credential lock.

## Acceptance Criteria
MVP is complete when:
- App installs on Kevin’s Android device through private dev install.
- App requests SMS permission only after disclosure.
- Incoming supported financial SMS messages create transactions automatically.
- Parsed transactions persist locally in Room.
- User can correct category and transaction type.
- Merchant corrections are reused for later transactions.
- Internal transfers can be excluded from spending totals.
- App requires biometric/device credential before showing financial data.
- Unit tests pass for parser, repositories, and core categorization.
- No cloud sync or external financial data upload exists in MVP.

## Assumptions And Defaults
- Distribution is private dev install for MVP.
- Cloud sync is deferred.
- Convex is not added in the first implementation.
- Kevin will provide anonymized real SMS examples before provider-specific parser work.
- The first feature slice is SMS-to-transactions, not full budgeting.
- App lock is included using biometric/device credential fallback.
- The repo can be scaffolded as a new Android project in the current workspace.
- Do not initialize git unless explicitly requested during implementation.
- Do not persist raw SMS bodies by default; store hashes and parsed fields. Raw fixture/debug storage is debug-only.
