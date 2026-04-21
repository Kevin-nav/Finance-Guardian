# Room And SMS Parser Foundation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build the backend foundation that turns known MTN MoMo, Telecel Cash, and GCB SMS formats into normalized local transactions stored in Room.

**Architecture:** Keep parsing as pure Kotlin with no Android dependencies so it can be covered by fast JVM tests. Use Room as the local source of truth, storing normalized transaction fields and SMS hashes while avoiding raw SMS body persistence by default. Add Hilt bindings only after the DAOs, repositories, and parser contract are independently tested.

**Tech Stack:** Kotlin, Room, KSP, Hilt, Coroutines Flow, JUnit4, AndroidX Room testing, pure JVM parser tests.

---

## Current Context

The first implementation batch already created the Android app scaffold, dependency catalog, Gradle wrapper, Hilt application shell, domain models, parser interfaces, and feature package structure.

The workspace is intentionally not a git repository. Do not initialize git unless Kevin explicitly asks. Wherever this plan says "checkpoint", that means stop and report the files changed and verification output instead of committing.

Fixture inputs already exist at the repository root:

- `MTN_Mobile_Money.md`
- `Telecel_Cash.md`
- `GCB_Bank.md`

These files contain real-world message structure. Use them to derive anonymized test cases in source-controlled test fixtures. Do not copy unnecessary personal names, phone numbers, account fragments, or full transaction IDs into production code.

## Batch Scope

Implement plan tasks 4 and 5 only:

- Room schema, DAOs, database, category seed, repository baseline.
- Pure Kotlin parser module, provider parser registry, MTN/Telecel/GCB rules, generic fallback, fixture-based tests.

Do not implement the SMS `BroadcastReceiver`, runtime permission request flow, app lock, or transaction correction UI in this batch.

## Parsing Rules To Cover

### MTN MoMo

Recognize debit formats:

- `Your payment of GHS 18.50 to BATAMADWOM ENTERPRISE has been completed...`
- `Payment made for GHS 30.00 to ATHANASIUS...`
- `Payment for GHS58.65 to Paystack Ghana Limited...`
- `Y'ello. You have Paid GHS 28.5 to Merchant 004501...`

Recognize credit format:

- `Payment received for GHS 77.00 from GIFTY...`

Ignore promotional trailer text such as MoMo app download links.

### Telecel Cash

Recognize debit formats:

- `Confirmed. GHS17.00 sent to 024... NAME on MTN MOBILE MONEY...`
- `Confirmed. GHS276.00 paid to 125012 - PAYSTACK II...`
- `confirmed. Your bundle purchase request of GHS5.50...`
- `Confirmed. You bought GHS1.00 of airtime for 233...`

Recognize credit formats:

- `Confirmed. You have received GHS252.00 from MTN MOBILE MONEY...`
- `Dear customer, you have received GHS0.16 from Telecel Cash as interest earned...`

Ignore:

- Failed transfer messages.
- Balance-only confirmations.
- Insufficient funds messages.
- Promotional app download messages.

### GCB

Recognize bank account debit/credit format:

- `Your A/C No:XXXX4127 has been debited GHS12.00 ... Desc: ... Date: ... Bal: ...`
- `Your A/C No:XXXX4127 has been credited GHS1,050.00 ... Desc: ... Date: ... Bal: ...`

Recognize prepaid card debit/credit format:

- `Your Prepaid Card VISA ... has been debited with amount of : 12.00 GHANA CEDIS...`
- `Your Prepaid Card has been credited with an amount of : 12 GHS...`

Ignore bank security notices and holiday greetings.

## Data Decisions

- Use `java.time.Instant` in domain APIs, already established in the first batch.
- Store timestamps in Room as epoch milliseconds through a `TypeConverter`.
- Store enums in Room as stable `String` names through a `TypeConverter`.
- Store `amountMinor` and `balanceAfterMinor` as `Long`, where `GHS 58.65` becomes `5865`.
- Store the SMS body hash, sender, received timestamp, parse status, and optional failure reason. Do not persist raw SMS bodies in this batch.
- Use a unique index for duplicate SMS records on `(sender, bodyHash, receivedAtEpochMillis)`.
- Use a unique index for parsed transactions on `sourceMessageId` where available.
- Default category assignment is deliberately conservative:
  - `Airtime/Data` for airtime and bundle purchase patterns.
  - `Subscriptions` for known subscription-like descriptors such as `OPENAI`, `CHATGPT`, `Spotify`, `T3 CHAT`.
  - `Transfers` for wallet-to-wallet or bank-to-wallet transfer patterns.
  - `Income` for credits.
  - `Unknown` for everything else.

## Task 1: Add Room Entities And Type Converters

**Files:**

- Create: `app/src/main/java/com/kevin/financeguardian/data/local/converter/RoomConverters.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/local/entity/TransactionEntity.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/local/entity/MerchantEntity.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/local/entity/CategoryEntity.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/local/entity/SmsMessageRecordEntity.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/local/entity/ParserRuleEntity.kt`

**Step 1: Write entity classes**

Create one Room entity per domain model. Keep entity fields close to the domain model but store dates as `Instant` through converters.

`TransactionEntity` table:

- `tableName = "transactions"`
- Primary key: `id`
- Indices:
  - `sourceMessageId`, unique.
  - `occurredAt`.
  - `categoryId`.

`SmsMessageRecordEntity` table:

- `tableName = "sms_message_records"`
- Primary key: `id`
- Indices:
  - `sender`, `bodyHash`, `receivedAt`, unique.
  - `parseStatus`.

**Step 2: Add converters**

`RoomConverters` should convert:

- `Instant <-> Long` using epoch milliseconds.
- `Provider <-> String`
- `TransactionDirection <-> String`
- `MoneyMovementType <-> String`
- `CategoryType <-> String`
- `ParseStatus <-> String`

Use `enumValueOf<T>()` in private helper functions or direct converter functions. Do not silently coerce unknown enum values; failing fast is acceptable for schema version 1.

**Step 3: Run compile**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\Kevin\AppData\Local\Android\Sdk'
.\gradlew.bat :app:compileDebugKotlin
```

Expected: compile passes or fails only because DAOs/database are not yet wired.

**Checkpoint:** Report entity/converter files created.

## Task 2: Add DAOs And Database

**Files:**

- Create: `app/src/main/java/com/kevin/financeguardian/data/local/dao/TransactionDao.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/local/dao/CategoryDao.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/local/dao/MerchantDao.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/local/dao/SmsMessageRecordDao.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/local/dao/ParserRuleDao.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/local/FinanceGuardianDatabase.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/data/local/LocalDatabaseContract.kt`

**Step 1: Add DAO contracts**

Minimum DAO surface:

- `TransactionDao.observeAll(): Flow<List<TransactionEntity>>`
- `TransactionDao.getById(id: String): TransactionEntity?`
- `TransactionDao.insert(entity: TransactionEntity)`
- `TransactionDao.updateCategory(transactionId: String, categoryId: String?, updatedAt: Instant)`
- `TransactionDao.updateMoneyMovementType(transactionId: String, type: MoneyMovementType, updatedAt: Instant)`
- `CategoryDao.observeAll(): Flow<List<CategoryEntity>>`
- `CategoryDao.getAllOnce(): List<CategoryEntity>`
- `CategoryDao.upsertAll(categories: List<CategoryEntity>)`
- `MerchantDao.upsert(entity: MerchantEntity)`
- `MerchantDao.findByNormalizedName(normalizedName: String): MerchantEntity?`
- `MerchantDao.findByPhone(phone: String): MerchantEntity?`
- `SmsMessageRecordDao.insert(entity: SmsMessageRecordEntity)`
- `SmsMessageRecordDao.findDuplicate(sender: String, bodyHash: String, receivedAt: Instant): SmsMessageRecordEntity?`
- `ParserRuleDao.upsertAll(rules: List<ParserRuleEntity>)`

**Step 2: Add database**

Create `FinanceGuardianDatabase : RoomDatabase` with:

- `version = 1`
- `exportSchema = true`
- `@TypeConverters(RoomConverters::class)`
- Abstract DAO accessors for every DAO.

**Step 3: Add schema export location**

Modify `app/build.gradle.kts` to configure Room schema export for KSP:

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

**Step 4: Run compile**

Run:

```powershell
.\gradlew.bat :app:kspDebugKotlin :app:compileDebugKotlin
```

Expected: Room generates schema and Kotlin compile passes.

**Checkpoint:** Report generated schema path and DAO/database files.

## Task 3: Add Category Seeding And Hilt Database Module

**Files:**

- Create: `app/src/main/java/com/kevin/financeguardian/data/local/DefaultCategorySeeder.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/domain/model/DefaultCategories.kt` only if mapping helpers are needed.

**Step 1: Add seeder**

Create `DefaultCategorySeeder` with:

- `suspend fun seedIfEmpty()`
- Read categories using `CategoryDao.getAllOnce()`.
- If empty, insert `DefaultCategories.values` as `CategoryEntity`.

**Step 2: Add Hilt module**

Provide:

- Singleton `FinanceGuardianDatabase`.
- DAOs from database.
- Singleton `DefaultCategorySeeder`.

Use:

```kotlin
Room.databaseBuilder(
    context,
    FinanceGuardianDatabase::class.java,
    LocalDatabaseContract.DATABASE_NAME,
).build()
```

Do not use destructive migrations in production code. Since schema version is 1, no migration is needed yet.

**Step 3: Run compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: compile passes.

**Checkpoint:** Report Hilt module and seeder files.

## Task 4: Add Entity/Domain Mappers And Repository Implementation

**Files:**

- Create: `app/src/main/java/com/kevin/financeguardian/data/local/mapper/EntityMappers.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/repository/RoomTransactionRepository.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/data/repository/TransactionRepository.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/di/RepositoryModule.kt`

**Step 1: Add mappers**

Add mapping functions:

- `TransactionEntity.toDomain(): Transaction`
- `Transaction.toEntity(): TransactionEntity`
- `CategoryEntity.toDomain(): Category`
- `Category.toEntity(): CategoryEntity`
- Equivalent for merchant, SMS record, parser rule as needed.

**Step 2: Expand repository contract**

Add methods needed by this backend batch:

```kotlin
interface TransactionRepository {
    fun observeTransactions(): Flow<List<Transaction>>
    suspend fun getTransaction(id: String): Transaction?
    suspend fun insertTransaction(transaction: Transaction)
    suspend fun updateCategory(transactionId: String, categoryId: String?)
    suspend fun updateMoneyMovementType(transactionId: String, type: MoneyMovementType)
}
```

**Step 3: Implement repository**

`RoomTransactionRepository` should delegate to `TransactionDao` and map entities to domain models.

**Step 4: Bind repository**

Use a Hilt `@Binds` module:

```kotlin
@Binds
abstract fun bindTransactionRepository(
    repository: RoomTransactionRepository,
): TransactionRepository
```

**Step 5: Run compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: compile passes.

**Checkpoint:** Report repository changes.

## Task 5: Add Room JVM Tests

**Files:**

- Create: `app/src/test/java/com/kevin/financeguardian/data/local/FinanceGuardianDatabaseTest.kt`
- Modify: `app/build.gradle.kts`

**Step 1: Add test dependency**

Add Room testing dependency if missing:

```kotlin
testImplementation(libs.androidx.room.testing)
```

Add to `gradle/libs.versions.toml`:

```toml
androidx-room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
```

**Step 2: Write database tests**

Use `Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FinanceGuardianDatabase::class.java)`.

Tests:

- Insert default categories and verify all expected IDs exist.
- Insert a transaction and observe it through DAO.
- Update transaction category.
- Insert duplicate SMS record and assert Room rejects the unique index.

**Step 3: Run tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*FinanceGuardianDatabaseTest"
```

Expected: tests pass.

**Checkpoint:** Report Room test results.

## Task 6: Add Parser Utilities And Provider Registry

**Files:**

- Create: `app/src/main/java/com/kevin/financeguardian/domain/parser/MoneyParsing.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/domain/parser/SmsTextNormalizer.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/domain/parser/ProviderDetector.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/domain/parser/ProviderParser.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/domain/parser/FinanceGuardianSmsParser.kt`

**Step 1: Add utility tests first**

Before implementation, create tests in:

- `app/src/test/java/com/kevin/financeguardian/domain/parser/MoneyParsingTest.kt`
- `app/src/test/java/com/kevin/financeguardian/domain/parser/ProviderDetectorTest.kt`

Test money formats:

- `GHS 9.00 -> 900`
- `GHS58.65 -> 5865`
- `GHS1,006.48 -> 100648`
- `12 GHANA CEDIS -> 1200`
- `12 GHS -> 1200`

Test provider detection:

- MTN text returns `MTN_MOMO`.
- Telecel text returns `TELECEL_CASH`.
- GCB account/card text returns `GCB`.
- Promotional/security text without transaction returns `UNKNOWN`.

**Step 2: Implement utilities**

`MoneyParsing` should expose:

```kotlin
fun parseAmountMinor(raw: String): Long?
```

`SmsTextNormalizer` should expose:

```kotlin
fun normalizeWhitespace(body: String): String
```

`ProviderDetector` should expose:

```kotlin
fun detect(sender: String, body: String): Provider
```

**Step 3: Add registry**

`FinanceGuardianSmsParser` should:

- Normalize text.
- Return `Ignored` for obvious non-transaction messages.
- Detect provider.
- Dispatch to provider parser.
- Fall back to generic parser if provider parser fails and the text looks financial.

**Step 4: Run parser utility tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*MoneyParsingTest" --tests "*ProviderDetectorTest"
```

Expected: tests pass.

**Checkpoint:** Report utility files and test results.

## Task 7: Implement MTN Parser With Tests

**Files:**

- Create: `app/src/main/java/com/kevin/financeguardian/domain/parser/provider/MtnMomoParser.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/domain/parser/provider/MtnMomoParserTest.kt`

**Step 1: Write tests from anonymized fixtures**

Cover:

- Payment to merchant with completed timestamp, reference, balance, fee.
- `Payment made for GHS... to NAME...`
- `Payment for GHS58.65 to Paystack...`
- `Y'ello. You have Paid GHS... to Merchant 004501...`
- `Payment received for GHS... from NAME...`

Expected parsed fields:

- `provider = MTN_MOMO`
- Debit messages: `direction = DEBIT`, `moneyMovementType = EXPENSE`
- Credit messages: `direction = CREDIT`, `moneyMovementType = INCOME`
- Amount minor, balance minor, counterparty, reference when available.
- Confidence should be at least `0.85f` for explicit MTN formats.

**Step 2: Implement parser**

Use ordered regex patterns from most specific to broadest.

Do not parse failed/non-financial messages as transactions.

**Step 3: Run MTN tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*MtnMomoParserTest"
```

Expected: all MTN parser tests pass.

**Checkpoint:** Report covered MTN fixture patterns.

## Task 8: Implement Telecel Parser With Tests

**Files:**

- Create: `app/src/main/java/com/kevin/financeguardian/domain/parser/provider/TelecelCashParser.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/domain/parser/provider/TelecelCashParserTest.kt`

**Step 1: Write tests from anonymized fixtures**

Cover:

- Successful send to MTN mobile money.
- Successful merchant payment to Paystack.
- Incoming transfer from MTN mobile money.
- Incoming same-network/person transfer.
- Bundle purchase request.
- Airtime purchase.
- Interest credit.
- Failed transfer ignored.
- Balance-only confirmation ignored.
- Insufficient funds ignored.
- Promotional app message ignored.

**Step 2: Implement parser**

Use explicit patterns for:

- `GHS... sent to ... on MTN MOBILE MONEY`
- `GHS... paid to ...`
- `You have received GHS... from ...`
- `bundle purchase request of GHS...`
- `You bought GHS... of airtime`
- `interest earned`

Set money movement type:

- Bundle/airtime: `EXPENSE`, category suggestion later should map to `airtime_data`.
- Incoming transfer/interest: `INCOME`.
- Sent transfer/payment: `EXPENSE` unless later ingestion marks internal transfer by known account rules.

**Step 3: Run Telecel tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*TelecelCashParserTest"
```

Expected: all Telecel parser tests pass.

**Checkpoint:** Report covered Telecel fixture patterns.

## Task 9: Implement GCB Parser With Tests

**Files:**

- Create: `app/src/main/java/com/kevin/financeguardian/domain/parser/provider/GcbBankParser.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/domain/parser/provider/GcbBankParserTest.kt`

**Step 1: Write tests from anonymized fixtures**

Cover:

- Account debit with `Desc`, `Date`, and `Bal`.
- Account debit with `Fees`.
- Account credit with comma amount.
- Prepaid card debit.
- Prepaid card credit.
- Security notice ignored.
- Holiday greeting ignored.

**Step 2: Implement parser**

For account messages:

- Counterparty should come from `Desc`.
- Date should come from the `Date:` line.
- Balance should come from `Bal:`.

For prepaid card messages:

- Use received timestamp when message date is absent.
- Counterparty should be `Prepaid Card`.
- Balance should parse from `Your balance is ...`.

Set movement type:

- Credits: `INCOME`.
- Bank-to-wallet descriptors: `INTERNAL_TRANSFER`.
- Card top-up descriptors: `INTERNAL_TRANSFER`.
- Known subscription descriptors: `SUBSCRIPTION_CANDIDATE`.
- Other debits: `EXPENSE`.

**Step 3: Run GCB tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*GcbBankParserTest"
```

Expected: all GCB parser tests pass.

**Checkpoint:** Report covered GCB fixture patterns.

## Task 10: Add Generic Parser And Full Registry Tests

**Files:**

- Create: `app/src/main/java/com/kevin/financeguardian/domain/parser/provider/GenericGhanaMoneyParser.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/domain/parser/FinanceGuardianSmsParserTest.kt`

**Step 1: Write registry tests**

Cover:

- MTN message dispatched and parsed.
- Telecel message dispatched and parsed.
- GCB message dispatched and parsed.
- Promotional/security/failed messages return `Ignored`.
- Unknown financial-looking GHS debit returns parsed transaction with `provider = UNKNOWN`, `moneyMovementType = UNKNOWN`, and lower confidence.
- Unknown non-financial message returns `Ignored`.

**Step 2: Implement generic parser**

Generic parser should only parse if:

- Text contains `GHS` or `GHANA CEDIS`.
- Text contains transaction verbs such as `paid`, `payment`, `debited`, `credited`, `received`, `sent`, `bought`.

Set:

- `Provider.UNKNOWN`
- `confidence = 0.45f`
- Use received timestamp if no date is parseable.

**Step 3: Wire provider parsers into registry**

`FinanceGuardianSmsParser` should receive a default list:

- `MtnMomoParser`
- `TelecelCashParser`
- `GcbBankParser`
- `GenericGhanaMoneyParser`

**Step 4: Run registry tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*FinanceGuardianSmsParserTest"
```

Expected: all registry tests pass.

**Checkpoint:** Report parser registry behavior.

## Task 11: Add Parser Hilt Binding

**Files:**

- Create: `app/src/main/java/com/kevin/financeguardian/di/ParserModule.kt`

**Step 1: Provide parser**

Bind:

```kotlin
@Provides
@Singleton
fun provideSmsTransactionParser(): SmsTransactionParser = FinanceGuardianSmsParser()
```

**Step 2: Run compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: compile passes.

**Checkpoint:** Report parser DI binding.

## Task 12: Final Verification

**Files:**

- No new files expected.

**Step 1: Run all unit tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\Kevin\AppData\Local\Android\Sdk'
.\gradlew.bat test --stacktrace --warning-mode all
```

Expected:

- All Room tests pass.
- All parser tests pass.
- Existing no-source test tasks remain non-blocking.

**Step 2: Run debug APK build**

Run:

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace --warning-mode all
```

Expected:

- Build succeeds.
- Debug APK exists at `app/build/outputs/apk/debug/app-debug.apk`.

**Step 3: Report**

Report:

- Files created/modified.
- Parser patterns covered.
- Verification command results.
- Known gaps deferred to next batch:
  - SMS receiver.
  - Runtime permission flow.
  - Duplicate handling inside ingestion service.
  - UI category correction.

## Non-Goals For This Batch

- Do not request Android SMS permissions.
- Do not read the device SMS inbox.
- Do not implement a manifest `BroadcastReceiver`.
- Do not persist raw SMS bodies.
- Do not build UI flows around parsed transactions.
- Do not add cloud sync or external APIs.

## Execution Notes

- Prefer TDD for parser work: failing test first, smallest parser implementation, then broaden.
- Keep regex patterns readable and provider-specific.
- Use helper functions for date parsing and amount parsing rather than duplicating logic across parsers.
- Avoid overfitting to names in Kevin's fixtures. Tests should preserve message structure while anonymizing personal values.
- If a fixture format is ambiguous, return `Failed` or `Ignored` rather than manufacturing a transaction.
- If Gradle reports Kotlin daemon cache errors after an interrupted build, run `.\gradlew.bat --stop` followed by `.\gradlew.bat clean` before rerunning the target task.
