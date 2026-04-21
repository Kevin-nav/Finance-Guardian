# SMS Ingestion Backend Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build the backend path that receives an incoming SMS broadcast, parses it, records SMS processing status, and stores a transaction when parsing succeeds.

**Architecture:** Add a thin Android `BroadcastReceiver` plus a testable ingestion service. Keep Android telephony extraction separate from Room persistence so ingestion can be covered with JVM/Robolectric tests. Persist only sender, body hash, parse status, parsed fields, and reasons; never persist raw SMS bodies.

**Tech Stack:** Kotlin, Android BroadcastReceiver, Android Telephony APIs, Hilt, Room, JUnit 4, Robolectric, coroutines.

---

### Task 1: Add Ingestion Support Types And Hashing

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/data/sms/SmsMessageEnvelope.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/sms/SmsIngestionResult.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/sms/BodyHasher.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/time/AppClock.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/time/SystemAppClock.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/id/IdGenerator.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/id/UuidIdGenerator.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/data/sms/BodyHasherTest.kt`

**Step 1: Write the failing hash test**

Create `BodyHasherTest`:

```kotlin
package com.kevin.financeguardian.data.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class BodyHasherTest {
    @Test
    fun sha256HexReturnsLowercaseHexDigest() {
        assertEquals(
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
            BodyHasher.sha256Hex("test"),
        )
    }
}
```

**Step 2: Run the failing hash test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*BodyHasherTest"
```

Expected: fails because `BodyHasher` does not exist.

**Step 3: Add support types**

Create `SmsMessageEnvelope.kt`:

```kotlin
package com.kevin.financeguardian.data.sms

import java.time.Instant

data class SmsMessageEnvelope(
    val sender: String,
    val body: String,
    val receivedAt: Instant,
)
```

Create `SmsIngestionResult.kt`:

```kotlin
package com.kevin.financeguardian.data.sms

sealed interface SmsIngestionResult {
    data class Parsed(
        val smsRecordId: String,
        val transactionId: String,
    ) : SmsIngestionResult

    data class Ignored(
        val smsRecordId: String,
        val reason: String,
    ) : SmsIngestionResult

    data class Failed(
        val smsRecordId: String,
        val reason: String,
    ) : SmsIngestionResult

    data class Duplicate(
        val existingSmsRecordId: String,
    ) : SmsIngestionResult
}
```

Create `AppClock.kt`:

```kotlin
package com.kevin.financeguardian.core.time

import java.time.Instant

interface AppClock {
    fun now(): Instant
}
```

Create `SystemAppClock.kt`:

```kotlin
package com.kevin.financeguardian.core.time

import java.time.Instant
import javax.inject.Inject

class SystemAppClock @Inject constructor() : AppClock {
    override fun now(): Instant = Instant.now()
}
```

Create `IdGenerator.kt`:

```kotlin
package com.kevin.financeguardian.core.id

interface IdGenerator {
    fun newId(): String
}
```

Create `UuidIdGenerator.kt`:

```kotlin
package com.kevin.financeguardian.core.id

import java.util.UUID
import javax.inject.Inject

class UuidIdGenerator @Inject constructor() : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
```

Create `BodyHasher.kt`:

```kotlin
package com.kevin.financeguardian.data.sms

import java.security.MessageDigest

object BodyHasher {
    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
```

**Step 4: Run the hash test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*BodyHasherTest"
```

Expected: passes.

**Step 5: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/sms/SmsMessageEnvelope.kt app/src/main/java/com/kevin/financeguardian/data/sms/SmsIngestionResult.kt app/src/main/java/com/kevin/financeguardian/data/sms/BodyHasher.kt app/src/main/java/com/kevin/financeguardian/core/time/AppClock.kt app/src/main/java/com/kevin/financeguardian/core/time/SystemAppClock.kt app/src/main/java/com/kevin/financeguardian/core/id/IdGenerator.kt app/src/main/java/com/kevin/financeguardian/core/id/UuidIdGenerator.kt app/src/test/java/com/kevin/financeguardian/data/sms/BodyHasherTest.kt
git commit -m "feat: add SMS ingestion support types"
```

---

### Task 2: Add Ingestion Service

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/data/sms/SmsIngestionService.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/data/sms/SmsIngestionServiceTest.kt`

**Step 1: Write the failing ingestion tests**

Create `SmsIngestionServiceTest` with in-memory Room and fake parser/id/clock. Cover:

- Parsed SMS inserts `SmsMessageRecordEntity` and `TransactionEntity`.
- Ignored SMS inserts only an ignored SMS record.
- Failed SMS inserts only a failed SMS record.
- Duplicate SMS returns `Duplicate` and does not create a second transaction.
- Transaction stores `rawBodyHash`, not the raw body.

Use this helper shape:

```kotlin
private class FakeIdGenerator(ids: List<String>) : IdGenerator {
    private val queue = ArrayDeque(ids)
    override fun newId(): String = queue.removeFirst()
}

private class FixedClock(private val instant: Instant) : AppClock {
    override fun now(): Instant = instant
}
```

**Step 2: Run the failing ingestion tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsIngestionServiceTest"
```

Expected: fails because `SmsIngestionService` does not exist.

**Step 3: Implement `SmsIngestionService`**

Create `SmsIngestionService.kt`:

```kotlin
package com.kevin.financeguardian.data.sms

import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.dao.SmsMessageRecordDao
import com.kevin.financeguardian.data.local.dao.TransactionDao
import com.kevin.financeguardian.data.local.entity.SmsMessageRecordEntity
import com.kevin.financeguardian.data.local.entity.TransactionEntity
import com.kevin.financeguardian.domain.model.ParseStatus
import com.kevin.financeguardian.domain.parser.SmsParseInput
import com.kevin.financeguardian.domain.parser.SmsParseResult
import com.kevin.financeguardian.domain.parser.SmsTransactionParser
import javax.inject.Inject

class SmsIngestionService @Inject constructor(
    private val smsMessageRecordDao: SmsMessageRecordDao,
    private val transactionDao: TransactionDao,
    private val parser: SmsTransactionParser,
    private val idGenerator: IdGenerator,
    private val clock: AppClock,
) {
    suspend fun ingest(envelope: SmsMessageEnvelope): SmsIngestionResult {
        val bodyHash = BodyHasher.sha256Hex(envelope.body)
        val duplicate = smsMessageRecordDao.findDuplicate(
            sender = envelope.sender,
            bodyHash = bodyHash,
            receivedAt = envelope.receivedAt,
        )
        if (duplicate != null) {
            return SmsIngestionResult.Duplicate(duplicate.id)
        }

        return when (val result = parser.parse(SmsParseInput(envelope.sender, envelope.body, envelope.receivedAt))) {
            is SmsParseResult.Parsed -> persistParsed(envelope, bodyHash, result)
            is SmsParseResult.Ignored -> persistNonParsed(envelope, bodyHash, ParseStatus.IGNORED, result.reason)
            is SmsParseResult.Failed -> persistNonParsed(envelope, bodyHash, ParseStatus.FAILED, result.reason)
        }
    }

    private suspend fun persistParsed(
        envelope: SmsMessageEnvelope,
        bodyHash: String,
        result: SmsParseResult.Parsed,
    ): SmsIngestionResult.Parsed {
        val now = clock.now()
        val smsRecordId = idGenerator.newId()
        val transactionId = idGenerator.newId()
        smsMessageRecordDao.insert(
            SmsMessageRecordEntity(
                id = smsRecordId,
                sender = envelope.sender,
                bodyHash = bodyHash,
                receivedAt = envelope.receivedAt,
                processedAt = now,
                parseStatus = ParseStatus.PARSED,
                parseReason = null,
            ),
        )
        val parsed = result.transaction
        transactionDao.insert(
            TransactionEntity(
                id = transactionId,
                sourceMessageId = smsRecordId,
                provider = parsed.provider,
                rawSender = parsed.rawSender,
                rawBodyHash = bodyHash,
                occurredAt = parsed.occurredAt,
                direction = parsed.direction,
                moneyMovementType = parsed.moneyMovementType,
                amountMinor = parsed.amountMinor,
                currency = parsed.currency,
                counterpartyName = parsed.counterpartyName,
                counterpartyPhone = parsed.counterpartyPhone,
                reference = parsed.reference,
                balanceAfterMinor = parsed.balanceAfterMinor,
                categoryId = null,
                confidence = result.confidence,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return SmsIngestionResult.Parsed(smsRecordId, transactionId)
    }

    private suspend fun persistNonParsed(
        envelope: SmsMessageEnvelope,
        bodyHash: String,
        status: ParseStatus,
        reason: String,
    ): SmsIngestionResult {
        val smsRecordId = idGenerator.newId()
        smsMessageRecordDao.insert(
            SmsMessageRecordEntity(
                id = smsRecordId,
                sender = envelope.sender,
                bodyHash = bodyHash,
                receivedAt = envelope.receivedAt,
                processedAt = clock.now(),
                parseStatus = status,
                parseReason = reason,
            ),
        )
        return when (status) {
            ParseStatus.IGNORED -> SmsIngestionResult.Ignored(smsRecordId, reason)
            ParseStatus.FAILED -> SmsIngestionResult.Failed(smsRecordId, reason)
            else -> error("Unsupported non-parsed status: $status")
        }
    }
}
```

**Step 4: Run ingestion tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsIngestionServiceTest"
```

Expected: passes.

**Step 5: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/sms/SmsIngestionService.kt app/src/test/java/com/kevin/financeguardian/data/sms/SmsIngestionServiceTest.kt
git commit -m "feat: persist ingested SMS transactions"
```

---

### Task 3: Bind Ingestion Dependencies With Hilt

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/di/IngestionModule.kt`

**Step 1: Create Hilt bindings**

Create `IngestionModule.kt`:

```kotlin
package com.kevin.financeguardian.di

import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.core.id.UuidIdGenerator
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.core.time.SystemAppClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class IngestionModule {
    @Binds
    @Singleton
    abstract fun bindIdGenerator(generator: UuidIdGenerator): IdGenerator

    @Binds
    @Singleton
    abstract fun bindAppClock(clock: SystemAppClock): AppClock
}
```

**Step 2: Run a compile check**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: compile succeeds and Hilt can resolve `SmsIngestionService`.

**Step 3: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/di/IngestionModule.kt
git commit -m "feat: bind SMS ingestion dependencies"
```

---

### Task 4: Add SMS Intent Extraction

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/data/sms/SmsIntentExtractor.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/data/sms/SmsIntentExtractorTest.kt`

**Step 1: Write extractor tests**

Create `SmsIntentExtractorTest` with Robolectric:

```kotlin
package com.kevin.financeguardian.data.sms

import android.content.Intent
import android.provider.Telephony
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsIntentExtractorTest {
    private val extractor = SmsIntentExtractor()
    private val fallback = Instant.parse("2026-04-21T18:00:00Z")

    @Test
    fun ignoresUnrelatedIntentActions() {
        val result = extractor.extract(Intent("com.example.UNRELATED"), fallback)

        assertTrue(result.isEmpty())
    }

    @Test
    fun ignoresSmsActionWithoutMessages() {
        val result = extractor.extract(Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION), fallback)

        assertTrue(result.isEmpty())
    }
}
```

**Step 2: Run failing extractor tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsIntentExtractorTest"
```

Expected: fails because `SmsIntentExtractor` does not exist.

**Step 3: Implement extractor**

Create `SmsIntentExtractor.kt`:

```kotlin
package com.kevin.financeguardian.data.sms

import android.content.Intent
import android.provider.Telephony
import java.time.Instant
import javax.inject.Inject

class SmsIntentExtractor @Inject constructor() {
    fun extract(intent: Intent, fallbackReceivedAt: Instant): List<SmsMessageEnvelope> {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return emptyList()

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty()
        if (messages.isEmpty()) return emptyList()

        return messages
            .mapIndexed { index, message ->
                ExtractedPart(
                    index = index,
                    sender = message.originatingAddress.orEmpty(),
                    body = message.messageBody.orEmpty(),
                    timestampMillis = message.timestampMillis,
                )
            }
            .filter { it.sender.isNotBlank() && it.body.isNotBlank() }
            .groupBy { it.sender to it.timestampMillis }
            .values
            .mapNotNull { parts ->
                val sorted = parts.sortedBy { it.index }
                val sender = sorted.firstOrNull()?.sender.orEmpty()
                val body = sorted.joinToString(separator = "") { it.body }
                if (sender.isBlank() || body.isBlank()) {
                    null
                } else {
                    SmsMessageEnvelope(
                        sender = sender,
                        body = body,
                        receivedAt = sorted
                            .firstOrNull { it.timestampMillis > 0L }
                            ?.timestampMillis
                            ?.let(Instant::ofEpochMilli)
                            ?: fallbackReceivedAt,
                    )
                }
            }
    }

    private data class ExtractedPart(
        val index: Int,
        val sender: String,
        val body: String,
        val timestampMillis: Long,
    )
}
```

**Step 4: Run extractor tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsIntentExtractorTest"
```

Expected: passes.

**Step 5: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/sms/SmsIntentExtractor.kt app/src/test/java/com/kevin/financeguardian/data/sms/SmsIntentExtractorTest.kt
git commit -m "feat: extract SMS broadcast envelopes"
```

---

### Task 5: Add Broadcast Receiver And Manifest Registration

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/data/sms/SmsBroadcastReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/test/java/com/kevin/financeguardian/data/sms/SmsBroadcastReceiverManifestTest.kt`

**Step 1: Write manifest test**

Create `SmsBroadcastReceiverManifestTest`:

```kotlin
package com.kevin.financeguardian.data.sms

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsBroadcastReceiverManifestTest {
    @Test
    fun receiverIsRegisteredWithBroadcastSmsPermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getReceiverInfo(
            ComponentName(context, SmsBroadcastReceiver::class.java),
            PackageManager.GET_META_DATA,
        )

        assertTrue(info.enabled)
        assertTrue(info.exported)
        assertEquals("android.permission.BROADCAST_SMS", info.permission)
    }
}
```

**Step 2: Run failing manifest test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsBroadcastReceiverManifestTest"
```

Expected: fails because receiver does not exist or is not registered.

**Step 3: Implement receiver**

Create `SmsBroadcastReceiver.kt`:

```kotlin
package com.kevin.financeguardian.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.kevin.financeguardian.core.time.AppClock
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SmsBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var extractor: SmsIntentExtractor
    @Inject lateinit var ingestionService: SmsIngestionService
    @Inject lateinit var clock: AppClock

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                extractor.extract(intent, clock.now()).forEach { envelope ->
                    runCatching { ingestionService.ingest(envelope) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

**Step 4: Register receiver**

Add this inside `<application>` in `AndroidManifest.xml`:

```xml
<receiver
    android:name=".data.sms.SmsBroadcastReceiver"
    android:enabled="true"
    android:exported="true"
    android:permission="android.permission.BROADCAST_SMS">
    <intent-filter>
        <action android:name="android.provider.Telephony.SMS_RECEIVED" />
    </intent-filter>
</receiver>
```

**Step 5: Run manifest test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsBroadcastReceiverManifestTest"
```

Expected: passes.

**Step 6: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/sms/SmsBroadcastReceiver.kt app/src/main/AndroidManifest.xml app/src/test/java/com/kevin/financeguardian/data/sms/SmsBroadcastReceiverManifestTest.kt
git commit -m "feat: register SMS broadcast receiver"
```

---

### Task 6: Verify Backend SMS Ingestion Slice

**Files:**
- No edits expected.

**Step 1: Run focused ingestion tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*BodyHasherTest" --tests "*SmsIngestionServiceTest" --tests "*SmsIntentExtractorTest" --tests "*SmsBroadcastReceiverManifestTest"
```

Expected: all focused ingestion tests pass.

**Step 2: Run parser and database regression tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*ParserTest" --tests "*FinanceGuardianDatabaseTest"
```

Expected: parser and database tests still pass.

**Step 3: Run full unit suite**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: full unit suite passes.

**Step 4: Inspect final diff**

Run:

```powershell
git status --short
git log --oneline --decorate -8
```

Expected: working tree is clean after the implementation commits.

---

## Deferred Follow-Up Plan

After this backend slice, implement runtime permission UI and transaction UI wiring:

- Onboarding requests `RECEIVE_SMS`.
- Settings reports permission status and can launch permission request/settings.
- Transactions screen observes Room instead of preview data.
- Fixture importer can call `SmsIngestionService.ingest()` without Android SMS broadcasts.

