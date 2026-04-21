# Merchant Categorization Backend Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Apply saved merchant category defaults during SMS ingestion and expose backend correction behavior that can save a merchant default for future transactions.

**Architecture:** Add pure normalization helpers, a `MerchantCategoryResolver` used by `SmsIngestionService`, and a backend `TransactionCorrectionService`. Reuse the existing Room schema; only DAO methods and service wiring change.

**Tech Stack:** Kotlin, Room, Hilt, JUnit 4, Robolectric, coroutines test.

---

### Task 1: Add Merchant Normalization

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/data/merchant/MerchantNormalizer.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/data/merchant/MerchantNormalizerTest.kt`

**Step 1: Write failing tests**

Create `MerchantNormalizerTest`:

```kotlin
package com.kevin.financeguardian.data.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantNormalizerTest {
    @Test
    fun normalizeNameLowercasesTrimsPunctuationAndCollapsesWhitespace() {
        assertEquals("shoprite osu mall", MerchantNormalizer.normalizeName("  Shoprite - OSU, Mall!! "))
    }

    @Test
    fun normalizeNameReturnsNullForBlank() {
        assertNull(MerchantNormalizer.normalizeName("   "))
    }

    @Test
    fun normalizePhoneKeepsDigitsAndMaskCharactersOnly() {
        assertEquals("23324***1234", MerchantNormalizer.normalizePhone("+233 24-***-1234"))
    }

    @Test
    fun normalizePhoneReturnsNullForBlank() {
        assertNull(MerchantNormalizer.normalizePhone(" - + "))
    }
}
```

**Step 2: Run failing test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*MerchantNormalizerTest"
```

Expected: fails because `MerchantNormalizer` does not exist.

**Step 3: Implement normalizer**

Create `MerchantNormalizer.kt`:

```kotlin
package com.kevin.financeguardian.data.merchant

object MerchantNormalizer {
    private val nonNameCharacters = Regex("""[^a-z0-9]+""")
    private val whitespace = Regex("""\s+""")

    fun normalizeName(name: String?): String? {
        val normalized = name
            ?.trim()
            ?.lowercase()
            ?.replace(nonNameCharacters, " ")
            ?.replace(whitespace, " ")
            ?.trim()
            .orEmpty()
        return normalized.ifBlank { null }
    }

    fun normalizePhone(phone: String?): String? {
        val normalized = phone
            ?.filter { it.isDigit() || it == '*' }
            .orEmpty()
        return normalized.ifBlank { null }
    }
}
```

**Step 4: Run test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*MerchantNormalizerTest"
```

Expected: passes.

**Step 5: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/merchant/MerchantNormalizer.kt app/src/test/java/com/kevin/financeguardian/data/merchant/MerchantNormalizerTest.kt
git commit -m "feat: add merchant normalization"
```

---

### Task 2: Expand Merchant DAO

**Files:**
- Modify: `app/src/main/java/com/kevin/financeguardian/data/local/dao/MerchantDao.kt`
- Create or modify: `app/src/test/java/com/kevin/financeguardian/data/local/FinanceGuardianDatabaseTest.kt`

**Step 1: Add failing DAO test**

Add a test to `FinanceGuardianDatabaseTest`:

```kotlin
@Test
fun updateMerchantDefaultCategoryChangesMerchant() = runTest {
    val now = Instant.parse("2026-04-21T12:00:00Z")
    database.merchantDao().upsert(
        MerchantEntity(
            id = "merchant-1",
            displayName = "Shoprite",
            normalizedName = "shoprite",
            phone = null,
            defaultCategoryId = null,
            createdFromTransactionId = null,
            createdAt = now,
            updatedAt = now,
        ),
    )

    database.merchantDao().updateDefaultCategory(
        id = "merchant-1",
        defaultCategoryId = "food",
        updatedAt = now.plusSeconds(60),
    )

    assertEquals("food", database.merchantDao().getById("merchant-1")?.defaultCategoryId)
}
```

Add imports if needed:

```kotlin
import com.kevin.financeguardian.data.local.entity.MerchantEntity
```

**Step 2: Run failing database test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*FinanceGuardianDatabaseTest"
```

Expected: fails because `getById` and `updateDefaultCategory` do not exist.

**Step 3: Add DAO methods**

Update `MerchantDao`:

```kotlin
@Query("SELECT * FROM merchants WHERE id = :id")
suspend fun getById(id: String): MerchantEntity?

@Query(
    """
    UPDATE merchants
    SET defaultCategoryId = :defaultCategoryId, updatedAt = :updatedAt
    WHERE id = :id
    """,
)
suspend fun updateDefaultCategory(
    id: String,
    defaultCategoryId: String?,
    updatedAt: Instant,
)
```

Add import:

```kotlin
import java.time.Instant
```

**Step 4: Run database test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*FinanceGuardianDatabaseTest"
```

Expected: passes.

**Step 5: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/local/dao/MerchantDao.kt app/src/test/java/com/kevin/financeguardian/data/local/FinanceGuardianDatabaseTest.kt
git commit -m "feat: update merchant default categories"
```

---

### Task 3: Add Merchant Category Resolver

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/data/merchant/MerchantCategoryResolver.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/data/merchant/MerchantCategoryResolverTest.kt`

**Step 1: Write failing resolver tests**

Test these behaviors with in-memory Room:

- Creates a merchant for new parsed counterparty.
- Returns existing merchant default by phone before name.
- Returns existing merchant default by normalized name.
- Returns `null` and creates no merchant when name and phone are unusable.

Use fake `IdGenerator` and fixed `Instant`.

**Step 2: Run failing tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*MerchantCategoryResolverTest"
```

Expected: fails because resolver does not exist.

**Step 3: Implement resolver**

Create `MerchantCategoryResolver.kt`:

```kotlin
package com.kevin.financeguardian.data.merchant

import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.data.local.dao.MerchantDao
import com.kevin.financeguardian.data.local.entity.MerchantEntity
import java.time.Instant
import javax.inject.Inject

class MerchantCategoryResolver @Inject constructor(
    private val merchantDao: MerchantDao,
    private val idGenerator: IdGenerator,
) {
    suspend fun resolveForParsedTransaction(
        counterpartyName: String?,
        counterpartyPhone: String?,
        transactionId: String,
        now: Instant,
    ): String? {
        val normalizedPhone = MerchantNormalizer.normalizePhone(counterpartyPhone)
        val normalizedName = MerchantNormalizer.normalizeName(counterpartyName)
        val existing = normalizedPhone
            ?.let { merchantDao.findByPhone(it) }
            ?: normalizedName?.let { merchantDao.findByNormalizedName(it) }

        if (existing != null) return existing.defaultCategoryId
        if (normalizedName == null && normalizedPhone == null) return null

        val displayName = counterpartyName?.trim()?.takeIf { it.isNotBlank() }
            ?: normalizedPhone
            ?: return null

        merchantDao.upsert(
            MerchantEntity(
                id = idGenerator.newId(),
                displayName = displayName,
                normalizedName = normalizedName ?: normalizedPhone,
                phone = normalizedPhone,
                defaultCategoryId = null,
                createdFromTransactionId = transactionId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return null
    }
}
```

**Step 4: Run resolver tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*MerchantCategoryResolverTest"
```

Expected: passes.

**Step 5: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/merchant/MerchantCategoryResolver.kt app/src/test/java/com/kevin/financeguardian/data/merchant/MerchantCategoryResolverTest.kt
git commit -m "feat: resolve merchant category defaults"
```

---

### Task 4: Apply Merchant Categories During Ingestion

**Files:**
- Modify: `app/src/main/java/com/kevin/financeguardian/data/sms/SmsIngestionService.kt`
- Modify: `app/src/test/java/com/kevin/financeguardian/data/sms/SmsIngestionServiceTest.kt`

**Step 1: Add failing ingestion tests**

Add tests to `SmsIngestionServiceTest`:

- Parsed SMS creates a merchant for `counterpartyName`.
- Parsed SMS with existing merchant default by name stores `categoryId`.
- Duplicate SMS does not create another merchant.

Update test service construction to pass `MerchantCategoryResolver`.

**Step 2: Run failing ingestion tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsIngestionServiceTest"
```

Expected: fails because service constructor and persistence do not yet use resolver.

**Step 3: Inject resolver into service**

Update constructor:

```kotlin
private val merchantCategoryResolver: MerchantCategoryResolver,
```

Add import:

```kotlin
import com.kevin.financeguardian.data.merchant.MerchantCategoryResolver
```

**Step 4: Use resolver before transaction insert**

In `persistParsed`, after `val parsed = result.transaction`, compute:

```kotlin
val categoryId = merchantCategoryResolver.resolveForParsedTransaction(
    counterpartyName = parsed.counterpartyName,
    counterpartyPhone = parsed.counterpartyPhone,
    transactionId = transactionId,
    now = now,
)
```

Use `categoryId = categoryId` in `TransactionEntity`.

**Step 5: Run ingestion tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsIngestionServiceTest"
```

Expected: passes.

**Step 6: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/sms/SmsIngestionService.kt app/src/test/java/com/kevin/financeguardian/data/sms/SmsIngestionServiceTest.kt
git commit -m "feat: apply merchant defaults during ingestion"
```

---

### Task 5: Add Transaction Correction Service

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/data/transaction/TransactionCorrectionResult.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/transaction/TransactionCorrectionService.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/data/transaction/TransactionCorrectionServiceTest.kt`

**Step 1: Write failing correction tests**

Cover:

- Missing transaction returns `NotFound`.
- Applies category to transaction.
- Applies movement type to transaction.
- Saves merchant default for existing merchant.
- Creates merchant default when transaction has merchant identity but no merchant exists.

**Step 2: Run failing correction tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*TransactionCorrectionServiceTest"
```

Expected: fails because service/result do not exist.

**Step 3: Add result type**

Create `TransactionCorrectionResult.kt`:

```kotlin
package com.kevin.financeguardian.data.transaction

sealed interface TransactionCorrectionResult {
    data object Applied : TransactionCorrectionResult
    data object NotFound : TransactionCorrectionResult
}
```

**Step 4: Implement service**

Create `TransactionCorrectionService.kt`:

```kotlin
package com.kevin.financeguardian.data.transaction

import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.dao.MerchantDao
import com.kevin.financeguardian.data.local.dao.TransactionDao
import com.kevin.financeguardian.data.local.entity.MerchantEntity
import com.kevin.financeguardian.data.merchant.MerchantNormalizer
import com.kevin.financeguardian.domain.model.MoneyMovementType
import javax.inject.Inject

class TransactionCorrectionService @Inject constructor(
    private val transactionDao: TransactionDao,
    private val merchantDao: MerchantDao,
    private val idGenerator: IdGenerator,
    private val clock: AppClock,
) {
    suspend fun applyCorrection(
        transactionId: String,
        categoryId: String?,
        moneyMovementType: MoneyMovementType?,
        saveMerchantDefault: Boolean,
    ): TransactionCorrectionResult {
        val transaction = transactionDao.getById(transactionId) ?: return TransactionCorrectionResult.NotFound
        val now = clock.now()

        transactionDao.updateCategory(transactionId, categoryId, now)
        if (moneyMovementType != null) {
            transactionDao.updateMoneyMovementType(transactionId, moneyMovementType, now)
        }

        if (saveMerchantDefault) {
            val normalizedPhone = MerchantNormalizer.normalizePhone(transaction.counterpartyPhone)
            val normalizedName = MerchantNormalizer.normalizeName(transaction.counterpartyName)
            val merchant = normalizedPhone
                ?.let { merchantDao.findByPhone(it) }
                ?: normalizedName?.let { merchantDao.findByNormalizedName(it) }

            if (merchant != null) {
                merchantDao.updateDefaultCategory(merchant.id, categoryId, now)
            } else if (normalizedName != null || normalizedPhone != null) {
                val displayName = transaction.counterpartyName?.trim()?.takeIf { it.isNotBlank() }
                    ?: normalizedPhone
                    ?: return TransactionCorrectionResult.Applied
                merchantDao.upsert(
                    MerchantEntity(
                        id = idGenerator.newId(),
                        displayName = displayName,
                        normalizedName = normalizedName ?: normalizedPhone,
                        phone = normalizedPhone,
                        defaultCategoryId = categoryId,
                        createdFromTransactionId = transactionId,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }

        return TransactionCorrectionResult.Applied
    }
}
```

**Step 5: Run correction tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*TransactionCorrectionServiceTest"
```

Expected: passes.

**Step 6: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/transaction/TransactionCorrectionResult.kt app/src/main/java/com/kevin/financeguardian/data/transaction/TransactionCorrectionService.kt app/src/test/java/com/kevin/financeguardian/data/transaction/TransactionCorrectionServiceTest.kt
git commit -m "feat: add backend transaction corrections"
```

---

### Task 6: Verify Merchant Categorization Backend

**Files:**
- No edits expected.

**Step 1: Run focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*MerchantNormalizerTest" --tests "*MerchantCategoryResolverTest" --tests "*SmsIngestionServiceTest" --tests "*TransactionCorrectionServiceTest"
```

Expected: all focused tests pass.

**Step 2: Run database and parser regressions**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*FinanceGuardianDatabaseTest" --tests "*ParserTest"
```

Expected: database and parser tests pass.

**Step 3: Run full unit suite**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: full suite passes.

**Step 4: Confirm clean git state**

Run:

```powershell
git status --short
git log --oneline --decorate -12
```

Expected: working tree is clean after implementation commits.

---

## Deferred Follow-Up

After this backend slice:

- Add fixture import backend/dev hook using `SmsIngestionService`.
- Wire transaction screen to Room-backed data.
- Add correction UI that calls `TransactionCorrectionService`.
- Add category-management backend for custom categories.
