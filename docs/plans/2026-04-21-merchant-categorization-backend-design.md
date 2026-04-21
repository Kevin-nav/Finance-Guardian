# Merchant Categorization Backend Design

## Goal

Apply merchant defaults during SMS ingestion and add backend correction behavior so a user's category correction can affect future transactions from the same merchant.

This slice stays backend-only. It does not add UI, ViewModels, permission flows, fixture import screens, or app lock behavior.

## Current Context

The app can now receive SMS broadcasts, extract envelopes, parse supported messages, store `SmsMessageRecordEntity`, and create `TransactionEntity`.

The current gap is categorization:

- `SmsIngestionService` always writes parsed transactions with `categoryId = null`.
- `MerchantEntity` already has fields for `normalizedName`, `phone`, `defaultCategoryId`, and `createdFromTransactionId`.
- `MerchantDao` can find merchants by normalized name or phone, but it cannot yet update a default category directly.
- `TransactionRepository` can update transaction category and money movement type, but it does not expose a backend correction operation that updates merchant defaults.

## Recommended Approach

Add a small merchant categorization layer and a correction service:

1. `MerchantNormalizer` turns parsed names and phones into stable lookup keys.
2. `MerchantCategoryResolver` is used by ingestion:
   - Find existing merchant by phone first.
   - Fall back to normalized name.
   - If found, apply its `defaultCategoryId` to the new transaction.
   - If not found and there is a usable counterparty name or phone, create a merchant with no default category yet.
3. `TransactionCorrectionService` updates an existing transaction and optionally saves the corrected category as that merchant's default.

This keeps ingestion simple and puts correction-specific behavior outside the SMS broadcast path.

## Components

### `MerchantNormalizer`

Pure Kotlin object:

- `normalizeName(name: String?): String?`
- `normalizePhone(phone: String?): String?`

Name rules:

- Trim.
- Lowercase.
- Replace punctuation/symbol runs with spaces.
- Collapse whitespace.
- Return `null` if blank.

Phone rules:

- Keep digits and `*` for masked numbers.
- Return `null` if blank.

### `MerchantCategoryResolver`

Injected service used by `SmsIngestionService`.

Expected method:

```kotlin
suspend fun resolveForParsedTransaction(
    counterpartyName: String?,
    counterpartyPhone: String?,
    transactionId: String,
    now: Instant,
): String?
```

Behavior:

- Normalize phone and name.
- Look up by phone first when phone exists.
- Look up by normalized name second when name exists.
- If merchant exists, return its `defaultCategoryId`.
- If merchant does not exist, create one when there is a usable name or phone.
- New merchants have `defaultCategoryId = null`.
- New merchants use `createdFromTransactionId = transactionId`.

This service should not infer categories. It only applies explicit saved defaults.

### `TransactionCorrectionService`

Injected backend service for later UI/ViewModel use.

Expected API:

```kotlin
suspend fun applyCorrection(
    transactionId: String,
    categoryId: String?,
    moneyMovementType: MoneyMovementType?,
    saveMerchantDefault: Boolean,
): TransactionCorrectionResult
```

Behavior:

- Load transaction by ID.
- Return `NotFound` when missing.
- Update transaction category when `categoryId` is provided, including `null` for clearing.
- Update movement type when provided.
- If `saveMerchantDefault = true`, find or create the merchant from the transaction's `counterpartyName` / `counterpartyPhone` and update its `defaultCategoryId`.
- If transaction has no usable merchant identity, still update the transaction and return `Applied`.

## DAO Changes

Add to `MerchantDao`:

- `updateDefaultCategory(id, defaultCategoryId, updatedAt)`
- Optional `getById(id)` if useful for tests.

Add no schema migration. Existing table columns already support this behavior.

## Data Flow

### New SMS

1. `SmsIngestionService` parses SMS.
2. It generates `smsRecordId` and `transactionId`.
3. It asks `MerchantCategoryResolver` for an applicable `categoryId`.
4. Resolver finds or creates merchant metadata.
5. Ingestion writes the transaction with the resolved category.

### User Correction Later

1. A backend caller invokes `TransactionCorrectionService.applyCorrection`.
2. Service updates transaction category and/or movement type.
3. If requested, service updates or creates the merchant default.
4. Future ingested transactions from the same phone/name receive that category automatically.

## Testing

Add tests for:

- Merchant name normalization.
- Phone normalization.
- Ingestion creates merchant for parsed counterparty.
- Ingestion applies existing merchant default by phone.
- Ingestion applies existing merchant default by normalized name.
- Duplicate SMS does not create another merchant.
- Correction updates transaction category.
- Correction can save merchant default.
- A future transaction reuses the saved merchant default.

## Deferred Work

- Category validation against existing categories.
- UI correction sheet.
- ViewModels.
- Suggested category inference.
- Merchant merge/de-duplication UI.
- Historical backfill for transactions created before merchant defaults existed.

