# SMS Fixture Import Backend Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a backend/dev fixture importer that reads anonymized SMS fixture JSON and imports each fixture through the existing `SmsIngestionService`.

**Architecture:** Keep fixtures as an in-memory dev/testing concern. Add a small fixture model and parser in `data/fixture`, then add `SmsFixtureImportService` that converts parsed fixtures into `SmsMessageEnvelope` and delegates to `SmsIngestionService`. Do not persist fixture JSON or raw SMS bodies.

**Tech Stack:** Kotlin, Android `org.json`, Room, Robolectric, JUnit 4, coroutines test.

---

### Task 1: Add Fixture Model And JSON Parser

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/data/fixture/SmsFixture.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/fixture/SmsFixtureParseException.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/fixture/SmsFixtureJsonParser.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/data/fixture/SmsFixtureJsonParserTest.kt`

**Step 1: Write failing parser tests**

Create tests for:

- Parses one JSON object fixture.
- Parses an array of fixture objects.
- Throws clear exception when required fields are missing.
- Throws clear exception for invalid provider.
- Throws clear exception for invalid `receivedAt`.

Fixture JSON shape:

```json
{
  "provider": "MTN_MOMO",
  "sender": "MobileMoney",
  "body": "Payment received for GHS 77.00 from SAMPLE SENDER Current Balance: GHS 538.01. Reference: R. Transaction ID: 123.",
  "receivedAt": "2026-04-21T18:00:00Z"
}
```

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsFixtureJsonParserTest"
```

Expected: fails because parser/model classes do not exist.

**Step 2: Implement fixture model**

Create `SmsFixture.kt`:

```kotlin
package com.kevin.financeguardian.data.fixture

import com.kevin.financeguardian.domain.model.Provider
import java.time.Instant

data class SmsFixture(
    val provider: Provider,
    val sender: String,
    val body: String,
    val receivedAt: Instant,
)
```

Create `SmsFixtureParseException.kt`:

```kotlin
package com.kevin.financeguardian.data.fixture

class SmsFixtureParseException(message: String) : IllegalArgumentException(message)
```

**Step 3: Implement JSON parser**

Create `SmsFixtureJsonParser.kt`:

```kotlin
package com.kevin.financeguardian.data.fixture

import com.kevin.financeguardian.domain.model.Provider
import java.time.Instant
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object SmsFixtureJsonParser {
    fun parseMany(json: String): List<SmsFixture> {
        val trimmed = json.trim()
        if (trimmed.isBlank()) throw SmsFixtureParseException("Fixture JSON is blank")
        return try {
            when {
                trimmed.startsWith("[") -> parseArray(JSONArray(trimmed))
                trimmed.startsWith("{") -> listOf(parseObject(JSONObject(trimmed), 0))
                else -> throw SmsFixtureParseException("Fixture JSON must be an object or array")
            }
        } catch (error: JSONException) {
            throw SmsFixtureParseException(error.message ?: "Invalid fixture JSON")
        }
    }

    private fun parseArray(array: JSONArray): List<SmsFixture> =
        List(array.length()) { index -> parseObject(array.getJSONObject(index), index) }

    private fun parseObject(obj: JSONObject, index: Int): SmsFixture {
        val label = "fixture[$index]"
        val provider = requiredString(obj, "provider", label).let { raw ->
            runCatching { Provider.valueOf(raw) }.getOrElse {
                throw SmsFixtureParseException("$label has invalid provider: $raw")
            }
        }
        val sender = requiredString(obj, "sender", label)
        val body = requiredString(obj, "body", label)
        val receivedAt = requiredString(obj, "receivedAt", label).let { raw ->
            runCatching { Instant.parse(raw) }.getOrElse {
                throw SmsFixtureParseException("$label has invalid receivedAt: $raw")
            }
        }
        return SmsFixture(provider, sender, body, receivedAt)
    }

    private fun requiredString(obj: JSONObject, field: String, label: String): String {
        val value = obj.optString(field).trim()
        if (value.isBlank()) throw SmsFixtureParseException("$label missing required field: $field")
        return value
    }
}
```

**Step 4: Run parser tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsFixtureJsonParserTest"
```

Expected: passes.

**Step 5: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/fixture/SmsFixture.kt app/src/main/java/com/kevin/financeguardian/data/fixture/SmsFixtureParseException.kt app/src/main/java/com/kevin/financeguardian/data/fixture/SmsFixtureJsonParser.kt app/src/test/java/com/kevin/financeguardian/data/fixture/SmsFixtureJsonParserTest.kt
git commit -m "feat: parse SMS fixture JSON"
```

---

### Task 2: Add Fixture Import Service

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/data/fixture/SmsFixtureImportResult.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/data/fixture/SmsFixtureImportService.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/data/fixture/SmsFixtureImportServiceTest.kt`

**Step 1: Write failing import tests**

Use in-memory Room, real `SmsIngestionService`, real `FinanceGuardianSmsParser`, `MerchantCategoryResolver`, fake IDs, and fixed clock.

Cover:

- Valid MTN fixture imports a parsed transaction.
- OTP/non-financial fixture imports an ignored SMS record and no transaction.
- Duplicate fixture returns duplicate on second import.
- Existing merchant default still applies through normal ingestion.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsFixtureImportServiceTest"
```

Expected: fails because import service classes do not exist.

**Step 2: Implement result model**

Create `SmsFixtureImportResult.kt`:

```kotlin
package com.kevin.financeguardian.data.fixture

import com.kevin.financeguardian.data.sms.SmsIngestionResult

data class SmsFixtureImportResult(
    val fixture: SmsFixture,
    val ingestionResult: SmsIngestionResult,
)
```

**Step 3: Implement import service**

Create `SmsFixtureImportService.kt`:

```kotlin
package com.kevin.financeguardian.data.fixture

import com.kevin.financeguardian.data.sms.SmsIngestionService
import com.kevin.financeguardian.data.sms.SmsMessageEnvelope
import javax.inject.Inject

class SmsFixtureImportService @Inject constructor(
    private val ingestionService: SmsIngestionService,
) {
    suspend fun importJson(json: String): List<SmsFixtureImportResult> =
        importFixtures(SmsFixtureJsonParser.parseMany(json))

    suspend fun importFixtures(fixtures: List<SmsFixture>): List<SmsFixtureImportResult> =
        fixtures.map { fixture ->
            SmsFixtureImportResult(
                fixture = fixture,
                ingestionResult = ingestionService.ingest(
                    SmsMessageEnvelope(
                        sender = fixture.sender,
                        body = fixture.body,
                        receivedAt = fixture.receivedAt,
                    ),
                ),
            )
        }
}
```

**Step 4: Run import tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsFixtureImportServiceTest"
```

Expected: passes.

**Step 5: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/data/fixture/SmsFixtureImportResult.kt app/src/main/java/com/kevin/financeguardian/data/fixture/SmsFixtureImportService.kt app/src/test/java/com/kevin/financeguardian/data/fixture/SmsFixtureImportServiceTest.kt
git commit -m "feat: import SMS fixtures through ingestion"
```

---

### Task 3: Add Static Test Fixtures

**Files:**
- Create: `app/src/test/resources/sms-fixtures/mtn-income.json`
- Create: `app/src/test/resources/sms-fixtures/ignored-otp.json`
- Modify: `app/src/test/java/com/kevin/financeguardian/data/fixture/SmsFixtureImportServiceTest.kt`

**Step 1: Add fixture files**

Create `mtn-income.json`:

```json
{
  "provider": "MTN_MOMO",
  "sender": "MobileMoney",
  "body": "Payment received for GHS 77.00 from SAMPLE SENDER Current Balance: GHS 538.01. Reference: R. Transaction ID: 123.",
  "receivedAt": "2026-04-21T18:00:00Z"
}
```

Create `ignored-otp.json`:

```json
{
  "provider": "UNKNOWN",
  "sender": "Unknown",
  "body": "Your OTP is 123456. Do not share it.",
  "receivedAt": "2026-04-21T18:01:00Z"
}
```

**Step 2: Add resource-backed import test**

Add a test that loads both resources with:

```kotlin
private fun resourceText(path: String): String =
    requireNotNull(javaClass.classLoader?.getResource(path)).readText()
```

Assert the MTN fixture parses and the OTP fixture is ignored.

**Step 3: Run import tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsFixtureImportServiceTest"
```

Expected: passes.

**Step 4: Commit**

Run:

```powershell
git add app/src/test/resources/sms-fixtures/mtn-income.json app/src/test/resources/sms-fixtures/ignored-otp.json app/src/test/java/com/kevin/financeguardian/data/fixture/SmsFixtureImportServiceTest.kt
git commit -m "test: add SMS fixture resources"
```

---

### Task 4: Verify Fixture Import Backend

**Files:**
- No edits expected.

**Step 1: Run focused fixture tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsFixtureJsonParserTest" --tests "*SmsFixtureImportServiceTest"
```

Expected: passes.

**Step 2: Run ingestion and parser regressions**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SmsIngestionServiceTest" --tests "*ParserTest"
```

Expected: passes.

**Step 3: Run full unit suite**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: passes.

**Step 4: Confirm git state**

Run:

```powershell
git status --short
git log --oneline --decorate -10
```

Expected: working tree is clean after commits.

---

## Deferred Follow-Up

- Add debug UI or command path to trigger fixture import.
- Add more anonymized real fixtures for MTN, Telecel, and GCB.
- Add parser coverage for fixture `expected` fields once the fixture corpus is stable.

