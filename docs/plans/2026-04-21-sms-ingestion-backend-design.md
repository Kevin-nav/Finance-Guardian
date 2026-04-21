# SMS Ingestion Backend Design

## Goal

Implement the backend path that turns an incoming Android SMS broadcast into durable local records: one `SmsMessageRecordEntity` and, when parsing succeeds, one `TransactionEntity`.

This slice intentionally excludes runtime permission UI, transaction list UI wiring, app lock, historical SMS import, notification handling, and manual correction screens.

## Current Context

The project already has the pieces that ingestion should use:

- `SmsTransactionParser` and provider parsers for MTN MoMo, Telecel Cash, GCB, and generic Ghana money messages.
- Room entities and DAOs for transactions and SMS message records.
- Hilt modules for the database and parser.
- `RECEIVE_SMS` declared in the manifest.

What is missing is the bridge between Android's SMS broadcast and the local database.

## Recommended Approach

Use a manifest-registered `BroadcastReceiver` that extracts SMS messages from `Telephony.Sms.Intents.getMessagesFromIntent(intent)` and hands normalized payloads to an injected ingestion service.

The receiver should stay thin:

- Validate the broadcast action.
- Extract sender, body, and timestamp.
- Use Hilt entry points to obtain the ingestion service.
- Launch async ingestion using `goAsync()` and a short coroutine.

The ingestion service owns durable behavior:

- Hash the body with SHA-256.
- Check duplicate records by `(sender, bodyHash, receivedAt)`.
- Parse the SMS with `SmsTransactionParser`.
- Insert an `SmsMessageRecordEntity` for parsed, ignored, and failed messages.
- Insert a `TransactionEntity` only for parsed messages.
- Never persist the raw SMS body.

## Components

### `SmsMessageEnvelope`

A small immutable data model for extracted SMS:

```kotlin
data class SmsMessageEnvelope(
    val sender: String,
    val body: String,
    val receivedAt: Instant,
)
```

This keeps Android framework classes out of ingestion tests.

### `SmsIntentExtractor`

Android-facing extractor that returns zero or more `SmsMessageEnvelope` values.

Rules:

- Ignore non-`SMS_RECEIVED_ACTION` intents.
- Use `Telephony.Sms.Intents.getMessagesFromIntent(intent)`.
- Group multipart messages by sender and timestamp.
- Join multipart bodies in received order.
- Use the earliest positive timestamp from the grouped messages; otherwise use an injected clock/fallback instant.
- Return no envelope when sender or body is blank.

### `BodyHasher`

Pure Kotlin utility:

```kotlin
fun sha256Hex(input: String): String
```

The hash should be lowercase hex and deterministic.

### `SmsIngestionService`

Main application service:

```kotlin
class SmsIngestionService @Inject constructor(
    private val smsMessageRecordDao: SmsMessageRecordDao,
    private val transactionDao: TransactionDao,
    private val parser: SmsTransactionParser,
)
```

Expected method:

```kotlin
suspend fun ingest(envelope: SmsMessageEnvelope): SmsIngestionResult
```

Result states:

- `Parsed(transactionId, smsRecordId)`
- `Ignored(smsRecordId, reason)`
- `Failed(smsRecordId, reason)`
- `Duplicate(existingSmsRecordId)`

The service should generate stable UUIDs at write time. For tests, inject an `IdGenerator` and `Clock`.

### `SmsBroadcastReceiver`

Manifest receiver:

- `android.provider.Telephony.SMS_RECEIVED`
- `android:exported="true"`
- Requires `android.permission.BROADCAST_SMS` on the receiver declaration.

The receiver should use a Hilt entry point instead of field injection because manifest receivers can be created by the system.

## Data Flow

1. Android receives SMS and sends `SMS_RECEIVED_ACTION`.
2. `SmsBroadcastReceiver` calls `SmsIntentExtractor.extract(intent)`.
3. For each envelope, receiver calls `SmsIngestionService.ingest(envelope)`.
4. Ingestion hashes the body.
5. Ingestion checks `SmsMessageRecordDao.findDuplicate(sender, bodyHash, receivedAt)`.
6. If duplicate exists, return `Duplicate` and perform no insert.
7. Otherwise parse with `SmsTransactionParser`.
8. Insert `SmsMessageRecordEntity` with:
   - `PARSED` for parsed messages.
   - `IGNORED` for ignored messages.
   - `FAILED` for failed parser results or unexpected ingestion errors.
9. For parsed messages, insert a matching `TransactionEntity` linked by `sourceMessageId`.

## Error Handling

- Duplicate SMS is not an error.
- Parser `Ignored` should be stored as an ignored SMS record, not discarded.
- Parser `Failed` should be stored as a failed SMS record with reason.
- If transaction insert fails after the SMS record insert, return failed and allow tests to expose the mismatch. Do not add migrations or retry infrastructure in this slice.
- Receiver should catch exceptions per envelope so one bad SMS does not prevent the receiver from finishing.

## Testing

Pure unit tests should cover:

- SHA-256 hashing.
- Parsed ingestion inserts both SMS record and transaction.
- Ignored ingestion inserts only SMS record.
- Failed ingestion inserts only SMS record.
- Duplicate ingestion returns duplicate and performs no new insert.
- Parsed transaction never stores raw body, only `rawBodyHash`.

Android/Robolectric tests should cover:

- `SmsBroadcastReceiver` ignores unrelated actions.
- Manifest includes the SMS receiver.

The Android framework PDU path can be manually tested on device later; this slice should not depend on connected device tests.

## Deferred Work

- Runtime permission request UI.
- Settings permission status wiring.
- Historical `READ_SMS` import.
- WorkManager retry queue.
- Transaction list UI backed by Room.
- Correction sheet and merchant-learning behavior.
- Biometric app lock.

