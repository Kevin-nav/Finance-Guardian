# SMS Sender Name Detection Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Recognize the real SMS sender names `MobileMoney`, `T-CASH`, and `GCB Bank` as authoritative provider signals for MTN MoMo, Telecel Cash, and GCB messages.

**Architecture:** Keep provider detection centralized in `ProviderDetector.kt`. Add exact, normalized sender-name detection before transactional body-pattern fallbacks, while preserving ignored-message handling in `FinanceGuardianSmsParser`.

**Tech Stack:** Kotlin, Android app module, JUnit 4, Gradle wrapper.

---

### Task 1: Add Sender Detection Tests

**Files:**
- Modify: `app/src/test/java/com/kevin/financeguardian/domain/parser/ProviderDetectorTest.kt`

**Step 1: Add failing tests for real sender names**

Add these tests to `ProviderDetectorTest`:

```kotlin
@Test
fun detectsMtnMobileMoneySenderName() {
    val body = "Payment received for GHS 77.00 from SAMPLE SENDER Current Balance: GHS 538.01. Reference: R. Transaction ID: 123."

    assertEquals(Provider.MTN_MOMO, detect("MobileMoney", body))
}

@Test
fun detectsTelecelTCashSenderName() {
    val body = "000001 Confirmed. You bought GHS1.00 of airtime for 233000000000 on 2026-02-03 at 16:01:10. Your Telecel Cash balance is GHS0.62."

    assertEquals(Provider.TELECEL_CASH, detect("T-CASH", body))
}

@Test
fun detectsGcbBankSenderName() {
    val body = "Hi Customer Your A/C No:XXXX0000 has been debited GHS12.00 Desc: VISA Card Top Up Date: 2026-04-08 14:13 Bal: GHS 217.41"

    assertEquals(Provider.GCB, detect("GCB Bank", body))
}
```

**Step 2: Run tests to verify the current gap**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*ProviderDetectorTest"
```

Expected: at least one new sender-name test fails before implementation.

**Step 3: Commit is not required yet**

Do not commit after this task. Commit after implementation and verification.

---

### Task 2: Implement Exact Sender Name Detection

**Files:**
- Modify: `app/src/main/java/com/kevin/financeguardian/domain/parser/ProviderDetector.kt`

**Step 1: Add known sender matching**

Update `detect(sender: String, body: String)` so it normalizes the sender and checks exact known names:

```kotlin
fun detect(sender: String, body: String): Provider {
    val senderLower = sender.trim().lowercase()
    val normalized = normalizeWhitespace(body)
    val lower = normalized.lowercase()

    val senderProvider = when (senderLower) {
        "mobilemoney" -> Provider.MTN_MOMO
        "t-cash" -> Provider.TELECEL_CASH
        "gcb bank" -> Provider.GCB
        else -> null
    }
    if (senderProvider != null) {
        return senderProvider
    }

    // Existing body-pattern detection stays below this point.
}
```

Keep the existing body fallback rules after the new sender block:

```kotlin
if (lower.contains("telecel cash") || lower.contains("sendi k3k3")) {
    return Provider.TELECEL_CASH
}

if (lower.contains("a/c no:") || lower.contains("prepaid card")) {
    return Provider.GCB
}

if (
    senderLower.contains("mtn") ||
    lower.contains("financial transaction id") ||
    lower.contains("momo app") ||
    lower.contains("y'ello") ||
    lower.startsWith("payment made for ghs") ||
    lower.startsWith("payment received for ghs") ||
    lower.startsWith("payment for ghs") ||
    lower.startsWith("your payment of ghs")
) {
    return Provider.MTN_MOMO
}
```

**Step 2: Keep non-transaction filtering in the parser**

Do not move ignored-message rules into `ProviderDetector.kt`. `FinanceGuardianSmsParser.isGloballyIgnored` already filters failed, promotional, and security texts before provider dispatch.

**Step 3: Run focused detector tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*ProviderDetectorTest"
```

Expected: all provider detector tests pass.

---

### Task 3: Update Parser Dispatch Coverage

**Files:**
- Modify: `app/src/test/java/com/kevin/financeguardian/domain/parser/FinanceGuardianSmsParserTest.kt`

**Step 1: Replace placeholder sender names**

In `dispatchesMtnTelecelAndGcbMessages`, use the real sender names:

```kotlin
parse("MobileMoney", "Payment received for GHS 77.00 from SAMPLE SENDER Current Balance: GHS 538.01. Reference: R. Transaction ID: 123.")

parse("T-CASH", "000001 Confirmed. You bought GHS1.00 of airtime for 233000000000 on 2026-02-03 at 16:01:10. Your Telecel Cash balance is GHS0.62.")

parse("GCB Bank", "Hi Customer Your A/C No:XXXX0000 has been debited GHS25.83 Desc: Spotify Stockholm Date: 2026-03-30 06:43 Bal: GHS 12.61")
```

Keep the expected providers unchanged:

```kotlin
Provider.MTN_MOMO
Provider.TELECEL_CASH
Provider.GCB
```

**Step 2: Add ignored security coverage for `GCB Bank` sender**

Update the security-message assertion to use the real GCB sender:

```kotlin
assertTrue(parse("GCB Bank", "Dear Valued Customer, GCB Bank will NEVER call to request your PIN.") is SmsParseResult.Ignored)
```

**Step 3: Run dispatch tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*FinanceGuardianSmsParserTest"
```

Expected: all parser dispatch tests pass.

---

### Task 4: Verify Parser Suite

**Files:**
- No edits.

**Step 1: Run focused parser tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*ProviderDetectorTest" --tests "*FinanceGuardianSmsParserTest"
```

Expected: both focused test classes pass.

**Step 2: Run broader parser tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*ParserTest"
```

Expected: all parser tests pass.

**Step 3: Inspect git diff**

Run:

```powershell
git diff -- app/src/main/java/com/kevin/financeguardian/domain/parser/ProviderDetector.kt app/src/test/java/com/kevin/financeguardian/domain/parser/ProviderDetectorTest.kt app/src/test/java/com/kevin/financeguardian/domain/parser/FinanceGuardianSmsParserTest.kt
```

Expected: only sender detection and related test updates are present.

---

### Task 5: Commit Implementation

**Files:**
- Modify: `app/src/main/java/com/kevin/financeguardian/domain/parser/ProviderDetector.kt`
- Modify: `app/src/test/java/com/kevin/financeguardian/domain/parser/ProviderDetectorTest.kt`
- Modify: `app/src/test/java/com/kevin/financeguardian/domain/parser/FinanceGuardianSmsParserTest.kt`

**Step 1: Stage implementation files**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/domain/parser/ProviderDetector.kt app/src/test/java/com/kevin/financeguardian/domain/parser/ProviderDetectorTest.kt app/src/test/java/com/kevin/financeguardian/domain/parser/FinanceGuardianSmsParserTest.kt
```

**Step 2: Commit**

Run:

```powershell
git commit -m "feat: recognize known SMS sender names"
```

Expected: one implementation commit containing only the detector and parser test changes.

