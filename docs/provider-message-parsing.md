# Provider Message Detection And Transaction Parsing

This document describes how Finance Guardian currently detects SMS messages from supported providers and turns them into transaction records. It reflects the implementation in `app/src/main/java/com/kevin/financeguardian` as of now.

## High-level flow

Incoming SMS messages enter the app through `SmsBroadcastReceiver`.

1. `SmsBroadcastReceiver` listens for `Telephony.Sms.Intents.SMS_RECEIVED_ACTION`.
2. `SmsIntentExtractor` extracts Android SMS parts from the intent.
3. Multipart SMS parts are grouped by sender and timestamp, ordered, joined, and sanitized into `SmsMessageEnvelope`.
4. `SmsIngestionService` hashes the raw body with SHA-256, checks for duplicate SMS records, and passes the sender/body/timestamp to `SmsTransactionParser`.
5. `FinanceGuardianSmsParser` normalizes whitespace, ignores obvious non-transactional messages, detects the provider, and tries provider-specific parsers.
6. Provider parsers return the legacy `ParsedTransaction` fields plus a richer `ParsedTransactionEvent` when enough facts are available.
7. `SmsIngestionService` classifies parsed events into transaction flow metadata using owned wallet preferences and strong parsed evidence.
8. If parsing succeeds, `SmsIngestionService` stores both an `SmsMessageRecordEntity` and a `TransactionEntity` in Room, including flow metadata on the transaction row.
9. If parsing is ignored or failed, only the SMS record is stored with the parse status and reason.

The parser itself is pure Kotlin and has no Android dependency. Hilt wires it through `ParserModule`, which provides `FinanceGuardianSmsParser` as the app's `SmsTransactionParser`.

## Core parsed fields

Provider parsers return `SmsParseResult.Parsed`, which contains a `ParsedTransaction` plus a confidence score.

Current transaction fields extracted by the parser are:

- `provider`: `MTN_MOMO`, `TELECEL_CASH`, `GCB`, or `UNKNOWN`.
- `rawSender`: the SMS sender string.
- `providerTransactionId`: provider reference or transaction id when present.
- `occurredAt`: the transaction time parsed from the SMS, or the SMS received time as fallback.
- `direction`: `DEBIT` or `CREDIT`.
- `moneyMovementType`: `EXPENSE`, `INCOME`, `INTERNAL_TRANSFER`, `SUBSCRIPTION_CANDIDATE`, or `UNKNOWN`.
- `amountMinor`: amount in pesewas, so `GHS 12.34` becomes `1234`.
- `currency`: currently always `GHS`.
- `counterpartyName`: merchant, sender, recipient, or description.
- `counterpartyPhone`: phone number when the message exposes one.
- `reference`: provider reference, free-text sender note, or normalized description.
- `balanceAfterMinor`: post-transaction balance in pesewas when present.
- `event`: optional richer event facts, including channel, instruments, fees, taxes, planned use, inferred identifiers, and balance reliability.
- `flowType`, `flowStatus`, and `flowId`: flow-level classification persisted on the transaction row.
- `plannedUse`: intent text such as `food`, `laundry`, or `Data`, preserved separately from accounting effect.
- `includedInSpendingTotals` and `includedInIncomeTotals`: explicit reporting flags used by transaction and insight totals.

Raw SMS bodies are not persisted in normal ingestion. The app stores `rawBodyHash` for dedupe and traceability.

## Transaction events and flows

The parser now separates message facts from accounting classification. The domain rule is: reason text describes intent, ownership decides accounting.

`ParsedTransactionEvent` represents one SMS-side event. It can carry channel, source and destination instruments, planned use, fees, taxes, balance reliability, provider references, and inferred identifiers such as wallet phones, GCB account suffixes, and virtual-card tokens.

`TransactionFlowClassifier` turns an event into flow metadata. Channel phrases such as `Bank to Wallet`, `Wallet to Bank`, `VISA Card Top Up`, and `Cash In` describe rails, not ownership. Internal transfer classification requires user-confirmed instruments or strong paired-message evidence.

`TransactionFlowMatcher` links likely paired events for up to 10 hours. A first side can be visible after 1 hour as a single pending flow, and a later matching side can still attach until the 10-hour window expires. Matching scores amount, opposite direction, compatible channels, timing, provider references, and shared instrument evidence.

Internal-transfer flows are excluded from income and spending totals by default. Cash-in and cash-deposit flows are distinct from ordinary income.

## Owned instruments

Users can optionally save any subset of their own wallets. The first implementation persists owned wallets in `UserPreferencesRepository` as typed `OwnedInstrument` values backed by DataStore.

Ghana phone identities are normalized before storage and comparison. `0549037907`, `+233 54 903 7907`, and `233549037907` compare as the same wallet identity. Masked values such as `****4127` are weak evidence and are not canonical full phone numbers.

## Provider detection

Detection lives in `ProviderDetector.kt`.

The app first checks exact sender names:

- `MobileMoney` -> `MTN_MOMO`
- `T-CASH` -> `TELECEL_CASH`
- `GCB Bank` -> `GCB`

If sender matching does not decide the provider, the detector inspects the normalized SMS body:

- Telecel Cash is detected when the body contains `telecel cash` or `sendi k3k3`.
- GCB is detected when the body contains `a/c no:` or `prepaid card`.
- MTN MoMo is detected when the sender contains `mtn`, or the body contains/starts with MTN-specific phrases like `financial transaction id`, `momo app`, `y'ello`, `payment made for ghs`, `payment received for ghs`, `payment for ghs`, or `your payment of ghs`.
- Anything else is `UNKNOWN`.

After detection, `FinanceGuardianSmsParser` tries the detected provider parser first and then the generic Ghana money parser. If provider detection returns `UNKNOWN`, it tries all registered parsers in order: MTN, Telecel Cash, GCB, then generic.

## Global ignores

Before provider-specific parsing, the registry ignores messages that are clearly not successful financial transactions.

Current global ignore patterns include:

- Blank messages.
- Failed or unsuccessful transactions, such as messages starting with `Failed.` or `Transaction failed`.
- Insufficient balance messages.
- Security/promotional text such as `will never call`, `never share your pin`, or holiday greetings.
- Telecel wallet balance-only confirmations.

Ignored messages are stored as SMS records with `ParseStatus.IGNORED`; they do not create transactions.

## MTN MoMo parsing

Implemented in `MtnMomoParser.kt`.

Supported MTN message shapes:

- Completed merchant payments:
  - Pattern like `Your payment of GHS ... to ... has been completed at yyyy-MM-dd HH:mm:ss.`
  - Parsed as `DEBIT` and `EXPENSE`.
  - Extracts amount, merchant/counterparty, exact timestamp, reference, transaction id, and new balance.

- `Payment made for ...` messages:
  - Pattern like `Payment made for GHS ... to ... Current Balance: ...`
  - Parsed as `DEBIT` and `EXPENSE`.
  - Extracts amount, counterparty, reference, transaction id, and current balance.

- `Payment for ...` messages:
  - Pattern like `Payment for GHS ... to ... .Current Balance: ...`
  - Parsed as `DEBIT` and `EXPENSE`.
  - Also runs `CounterpartyDetailsNormalizer`, which currently handles merchant-id-like details such as `Bills.INV` by splitting a cleaner counterparty and reference.

- Y'ello merchant messages:
  - Pattern like `Y'ello. You have Paid GHS ... to Merchant ... at yyyyddHHmmss.`
  - Parsed as `DEBIT` and `EXPENSE`.
  - The compact MTN timestamp uses the SMS received month because the compact value does not include a month.

- Incoming payments:
  - Pattern like `Payment received for GHS ... from ... Current Balance: ...`
  - Parsed as `CREDIT` and `INCOME`.

MTN balance extraction supports `Your new balance:`, `new balance:`, and `Current Balance:`.

## Telecel Cash parsing

Implemented in `TelecelCashParser.kt`.

The code uses `TELECEL_CASH`; if someone says "Telcel" in discussion, they are usually referring to the Telecel Cash provider in this codebase.

Supported Telecel message shapes:

- Successful sends to MTN Mobile Money:
  - Pattern like `Confirmed. GHS... sent to <phone> <name> on MTN MOBILE MONEY on yyyy-MM-dd at HH:mm:ss.`
  - Parsed as `DEBIT` and `EXPENSE`.
  - Extracts amount, recipient phone, recipient name, date/time, transaction id from the leading confirmed id, reference, and balance.

- Merchant payments:
  - Pattern like `Confirmed. GHS... paid to <merchant> on yyyy-MM-dd at HH:mm:ss.`
  - Parsed as `DEBIT` and `EXPENSE`.
  - Extracts merchant, amount, date/time, reference, transaction id, and balance.

- Incoming transfers:
  - Supports MTN Mobile Money transfer wording and same-network sender wording.
  - Parsed as `CREDIT` and `INCOME`.
  - Extracts sender name, optional phone, amount, date/time, reference, transaction id, and balance.

- Bundle purchases:
  - Pattern like `bundle purchase request of GHS... on yyyy-MM-dd has been received at HH:mm:ss.`
  - Parsed as `DEBIT` and `EXPENSE`.
  - Uses `Data Bundle` as counterparty and `Bundle purchase` as reference.

- Airtime purchases:
  - Pattern like `You bought GHS... of airtime for <phone> on yyyy-MM-dd at HH:mm:ss.`
  - Parsed as `DEBIT` and `EXPENSE`.
  - Uses `Airtime` as counterparty and `Airtime purchase` as reference.

- Interest credits:
  - Pattern like `you have received GHS... from Telecel Cash as interest earned`.
  - Parsed as `CREDIT` and `INCOME`.
  - Uses `Telecel Cash Interest` as counterparty and `Interest earned` as reference.

Telecel balance extraction supports `Telecel Cash balance is GHS...` and interest messages with `new balance is GHS...`.

## GCB Bank parsing

Implemented in `GcbBankParser.kt`.

Supported GCB message shapes:

- Account debit/credit alerts:
  - Pattern like `A/C No:<account> has been debited/credited GHS... Desc: ... Date: yyyy-MM-dd HH:mm Bal: GHS ...`
  - Optional fees are tolerated between amount and description.
  - Extracts direction, amount, description, date/time, balance, and uses the description as both counterparty and reference.

- Prepaid card debit/credit alerts:
  - Pattern like `Prepaid Card ... has been debited/credited with amount of : ... GHS/GHANA CEDIS ... balance is ...`
  - Extracts direction, amount, and balance.
  - Uses `Prepaid Card` as counterparty and reference.

GCB movement-type classification has extra logic:

- Credits are `INCOME`.
- Debits whose description contains `bank to wallet`, `b2w`, or `visa card top up` are not marked internal from wording alone. The parser extracts channel, source/destination instrument, planned use, fees, and inferred identifiers. The classifier decides internal-vs-external from ownership or matching evidence.
- Debits whose description contains known subscription markers like `openai`, `chatgpt`, `spotify`, or `t3 chat` are `SUBSCRIPTION_CANDIDATE`.
- Other debits are `EXPENSE`.

GCB-specific event extraction includes:

- `Bank to Wallet 0549037907 food T260`: channel `BANK_TO_WALLET`, destination phone `233549037907`, planned use `food`, inferred id `T260`.
- `Bank to Wallet 0596447662 laundry T`: channel `BANK_TO_WALLET`, destination phone, planned use `laundry`, and no internal classification unless the number is owned.
- `Wallet to Bank 0549037907 09FG04301`: channel `WALLET_TO_BANK`, source phone `233549037907`, inferred id `09FG04301`.
- `VISA Card Top Up LZDXAGEE 902125000`: channel `CARD_TOP_UP` with an inferred card token.
- `CASH DEPOSIT BY ...`: channel `CASH_DEPOSIT`.
- Negative GCB balances are parsed but marked `SUSPICIOUS`.

## Generic fallback parser

Implemented in `GenericGhanaMoneyParser.kt`.

The fallback parser is intentionally conservative. It only parses unknown messages when all of these are true:

- The body contains `GHS` or `GHANA CEDIS`.
- The body has a transaction verb such as `paid`, `payment`, `debited`, `credited`, `received`, `sent`, or `bought`.
- The body has transaction evidence such as a balance, transaction id/reference marker, account/wallet wording, counterparty wording, or explicit debit/credit wording.

When it parses a message:

- Provider is `UNKNOWN`.
- Amount comes from the first currency-bound amount.
- Direction is `CREDIT` if the message says `credited` or `received ... from`; otherwise it defaults to `DEBIT`.
- Movement type is `UNKNOWN`.
- Confidence is `0.45`.

This is meant to catch likely financial SMS messages without turning ordinary receipts or OTP/promotional messages into transactions.

## Shared parsing helpers

Common helpers are in `ParserHelpers.kt`, `MoneyParsing.kt`, and `DateParsing.kt`.

- `normalizeWhitespace` collapses multipart/newline-heavy SMS text into parser-friendly spacing.
- `parseAmountMinor` parses comma-separated and decimal GHS values into minor units.
- `findAmountMinor` finds the first amount with a currency marker.
- `parseDateTimeInstant` supports `yyyy-MM-dd HH:mm:ss` and `yyyy-MM-dd HH:mm`.
- `parseDateAndTimeInstant` supports separate date and time fields.
- `referenceAfter` extracts `Reference:`, `Ref:`, or `Message from sender:` values.
- `providerTransactionIdAfter` extracts `Financial Transaction Id:`, `Transaction Id:`, `Transaction ID:`, or a leading numeric Telecel confirmed id.
- `cleanParsedText` and `cleanReference` trim punctuation and remove empty/null-like references.

All parsed dates are converted to `Instant` using UTC.

## Dedupe and persistence

`SmsIngestionService` has two dedupe layers:

- SMS dedupe: sender + body hash + received timestamp in `SmsMessageRecordDao`.
- Transaction dedupe: `TransactionFingerprintFactory.fromParsed(parsed)` creates a dedupe key used by `TransactionDao.findByDedupeKey`.

If the same SMS arrives twice, ingestion returns `SmsIngestionResult.Duplicate`.

If a different SMS parses to a transaction fingerprint already in the database, the new SMS record is stored with `ParseStatus.DUPLICATE`, and no second transaction is created.

For parsed transactions, the service also resolves category information:

- First via `MerchantCategoryResolver`.
- If no category is found, via `CategorySuggestionService`.
- If the suggestion says it should auto-apply, the category and optionally the movement type are updated before persistence.

Transactions below confidence `0.85` or without a category trigger a `TransactionNeedsReview` notification event; higher-confidence categorized transactions trigger `TransactionDetected`.

## Current test coverage

Parser coverage exists under `app/src/test/java/com/kevin/financeguardian/domain/parser`.

Current tests cover:

- Provider dispatch for MTN, Telecel Cash, and GCB.
- Global ignores for failed, security, and promotional messages.
- MTN merchant payments, `Payment made`, `Payment for`, Y'ello merchant payments, incoming payments, balance parsing, references, and transaction ids.
- Telecel sends, merchant payments, incoming transfers, bundle purchases, airtime purchases, interest credits, failed-message ignores, and balance-only ignores.
- GCB account debits/credits, fee-bearing account alerts, prepaid card alerts, transfer fact extraction, suspicious balances, and subscription candidate classification.
- Generic unknown financial messages and non-financial/receipt-like unknown messages.
- Ghana phone normalization, owned wallet persistence, parsed event semantics, flow classification, and delayed flow matching.

## Current limitations

- Provider parsing is regex-based. New provider wording needs a parser/test update.
- Provider detection is heuristic after exact sender checks, so unusual sender names rely on body wording.
- The generic parser extracts only amount and direction. It does not infer counterparty, balance, reference, or exact provider.
- Failed transaction messages are ignored globally even if they contain balances.
- Fees and taxes are extracted into parsed events where currently supported. They are not yet separate Room columns.
- MTN compact Y'ello timestamps infer the month from the SMS received timestamp.
- `Provider.UNKNOWN_BANK` exists in the domain enum but is not currently assigned by detection or provider parsers.
- Flow metadata is persisted on `TransactionEntity` rows. A future dedicated `TransactionFlowEntity` can reduce duplication if richer linked-event history is needed.
