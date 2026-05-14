# Transaction Flow Understanding Design

## Goal

Improve Finance Guardian's SMS parsing so it understands the meaning of money movement, not just the literal fields in each message. The app should collapse internal transfers into one transaction flow, infer strong ownership signals from MTN, Telecel Cash, and GCB messages, and avoid counting internal movement as income or spending.

## Current Problem

The current parser goes directly from provider regex to `ParsedTransaction`. That is enough for simple merchant payments, but it is too shallow for transfer flows that involve multiple accounts on the same device.

Examples from the current fixture notes:

- `Bank to Wallet 0549037907 food T260` is an internal transfer only if `0549037907` is one of the user's own wallets. The planned use is `food`, but the accounting effect is still internal movement.
- `Bank to Wallet 0596447662 laundry T` is not internal if `0596447662` is not owned by the user. The planned use/category is `laundry`, and the accounting effect is an expense.
- A Telecel Cash debit to `0549037907` and an MTN MoMo credit referencing `233505600861` describe the two sides of one own-to-own transfer.
- A GCB `VISA Card Top Up ...` debit and a prepaid card credit can describe one virtual card top-up flow.
- A GCB `Wallet to Bank 0549037907 ...` credit may be an internal wallet-to-bank flow if the wallet is owned or if a matching wallet-side SMS later proves ownership.

The key rule is: **reason text describes intent; ownership decides accounting.**

## Design Principles

- Channel wording is not proof of internal transfer. `Bank to Wallet`, `Wallet to Bank`, `VISA Card Top Up`, and `Cash In` describe how money moved, not who owns the destination/source.
- Internal transfer classification needs ownership proof from user-confirmed instruments or strong paired-message evidence.
- A single message can create a collapsed transaction flow when it has enough evidence. The second side can arrive later and attach to the flow.
- Internal flows should be excluded from income/expense totals by default once they are classified with strong confidence.
- Users should be able to correct a strongly inferred result when it is wrong.
- Ghana phone numbers are the default supported identity format for now.

## Core Concepts

### Transaction Event

A transaction event is the parsed meaning of one SMS message. It should retain provider-specific facts without forcing a final accounting classification too early.

Recommended fields:

- `provider`: MTN MoMo, Telecel Cash, GCB, or unknown.
- `sourceMessageId`
- `occurredAt`
- `amountMinor`
- `currency`
- `directionFromProviderPerspective`: debit or credit.
- `channel`: merchant payment, wallet-to-wallet, wallet-to-bank, bank-to-wallet, card top-up, card spend, cash-in, cash-deposit, airtime/data, unknown.
- `sourceInstrument`
- `destinationInstrument`
- `counterpartyName`
- `counterpartyPhone`
- `providerTransactionId`
- `providerReference`
- `description`
- `plannedUse`
- `feeMinor`
- `taxMinor`
- `balanceAfterMinor`
- `balanceReliability`
- `confidence`

### Transaction Flow

A transaction flow is the user-facing collapsed unit shown in the transaction list and used in reports.

Recommended fields:

- `id`
- `flowType`: expense, income, internal transfer, cash deposit, card spend, unknown.
- `status`: complete, pending match, unmatched, needs review.
- `amountMinor`
- `currency`
- `occurredAt`
- `sourceInstrument`
- `destinationInstrument`
- `providerSummary`, such as `Telecel Cash -> MTN MoMo`.
- `plannedUse`
- `categoryId`
- `includedInSpendingTotals`
- `includedInIncomeTotals`
- `confidence`
- `primaryEventId`
- `linkedEventIds`
- `matchingExpiresAt`
- `visibleAsSingleFlowAt`

For the agreed matching behavior:

- Match related events for up to **10 hours**.
- If no paired SMS arrives after **1 hour**, still show the flow as a single transaction when the first message has enough evidence.
- If the pair arrives within 10 hours, attach it and increase confidence.
- After 10 hours, do not auto-link new messages unless there is exact or near-exact proof.

### Owned Instruments

Users can optionally add their own wallets in onboarding and settings. Not every user will have every provider.

Recommended user-confirmed fields:

- `label`: `My MTN`, `Telecel Cash`, `Main Wallet`, etc.
- `instrumentType`: wallet, bank account, card.
- `provider`: MTN, Telecel, GCB, other.
- `identifier`: normalized phone/account/card value.
- `displayIdentifier`
- `createdBy`: user.

The first version should prioritize wallet phone numbers. GCB account/card identity can be inferred from SMS messages because users may not know or be able to provide those values.

### Inferred Instruments

The app should infer GCB account suffixes, virtual card tokens, and wallet identities from strong message evidence.

Recommended fields:

- `instrumentType`
- `provider`
- `identifier`
- `displayIdentifier`
- `firstSeenAt`
- `lastSeenAt`
- `evidenceEventIds`
- `confidence`
- `createdBy`: system.
- `userConfirmed`: false by default.

Strongly inferred instruments can be used for flow classification, but the user should be able to correct the resulting transaction flow. The app should not permanently treat an inferred instrument as user-confirmed unless the user accepts it.

## Ghana Phone Normalization

The parser should assume Ghana phone numbers for now.

Normalization rules:

- Remove spaces, dashes, `+`, and punctuation.
- `0549037907` becomes canonical `233549037907`.
- `+233549037907` becomes `233549037907`.
- `233549037907` remains `233549037907`.
- For comparisons, `0549037907`, `+233549037907`, and `233549037907` are the same identity.
- Masked values such as `****4127` are weak evidence only.

## Classification Rules

### Bank To Wallet

For a GCB debit like:

```text
Desc: Bank to Wallet 0549037907 food T260
```

Parse:

- channel: bank-to-wallet.
- destination wallet: `0549037907`.
- planned use: `food`.
- internal/system id: `T260`.

Classify:

- If `0549037907` is user-owned, classify as internal transfer and exclude from spending/income totals.
- If `0549037907` is not owned, classify as expense and use `food` as the planned use/category signal.
- If ownership is unknown, classify as needs review or low-confidence transfer candidate, not definitive internal transfer.

### Wallet To Bank

For a GCB credit like:

```text
Desc: Wallet to Bank 0549037907 09FG04301
```

Parse:

- channel: wallet-to-bank.
- source wallet: `0549037907`.
- destination account: inferred GCB account from `A/C No:XXXX4127`.
- GCB internal id: `09FG04301`.

Classify:

- If the source wallet is user-owned, classify as internal transfer.
- If a matching MTN/Telecel debit arrives within 10 hours and proves same amount/source, classify as internal transfer.
- If ownership remains unknown after the match window, keep as income or needs review depending on confidence and user settings.

### Telecel Cash To MTN MoMo

When Telecel sends to MTN:

```text
GHS20.00 sent to 0549037907 KEVIN ... on MTN MOBILE MONEY
Reference: Data.
```

And MTN later receives:

```text
Payment received for GHS 20.00 from NCHORBUNO KEVIN ...
Reference: ...,233505600861,Data from VODAFONE.
```

Parse:

- Telecel event: source instrument Telecel wallet, destination MTN wallet `0549037907`, planned use `Data`.
- MTN event: destination MTN wallet, likely source Telecel wallet `233505600861`, planned use `Data`.

Classify:

- If both numbers are saved as user-owned, create one internal flow immediately and attach both events when available.
- If only one side is known but the paired SMS strongly proves the other, classify the flow as internal and mark the inferred side as system-inferred.

### GCB Virtual Card Top-Up

When GCB account is debited:

```text
Desc: VISA Card Top Up LZDXAGEE 902125000
```

And prepaid card is credited with a matching amount:

```text
Prepaid Card has been credited with an amount of : 13 GHS.
```

Classify as a card top-up/internal transfer if the amount and timing match. Store `LZDXAGEE 902125000` as an inferred card token when present. If only the GCB debit exists, it can still be a strong card top-up candidate, but pairing with the prepaid-card credit raises confidence.

### Cash In And Cash Deposit

`Cash In received` on MTN and `CASH DEPOSIT BY ...` on GCB should be classified separately from normal income.

Recommended behavior:

- Cash deposit into own account/wallet is an inflow, but should be semantically distinct from salary/gifts/business income.
- It can be included in balance history but excluded from "earned income" style insights unless the user chooses otherwise.

## Balance Reliability

Store parsed balances, but do not blindly trust them.

GCB can emit impossible balances such as a negative account balance. For GCB:

- Mark negative balances as suspicious.
- Do not let suspicious balances replace a previous reliable balance in account-level summaries.
- Keep the raw parsed value on the event for audit/debug.

## User Correction

Users need a way to correct:

- internal vs expense/income.
- planned use/category.
- source/destination instrument.
- whether an inferred instrument is actually theirs.

Corrections should feed the existing local learning system and should also update the transaction flow classification when relevant.

## UI Behavior

Transaction list should show collapsed flows by default.

Examples:

- `Telecel Cash -> MTN MoMo`, `GHS20.00`, planned use `Data`, excluded from spending.
- `GCB Main -> MTN Wallet`, `GHS60.00`, planned use `food`, excluded from spending.
- `GCB Main -> External Wallet`, `GHS61.00`, category `Laundry`, counted as spending.
- `GCB Main -> Virtual Card`, `GHS13.00`, card top-up, excluded from spending.

The detail sheet should show linked source messages/events so the user can understand why the app classified the flow that way.

## Testing Strategy

Tests should be fixture-driven and use the outlier files as source material.

Minimum test groups:

- Ghana phone normalization.
- GCB `Bank to Wallet` owned number -> internal transfer with planned use.
- GCB `Bank to Wallet` external number -> expense with planned use/category.
- GCB `Wallet to Bank` owned source wallet -> internal transfer.
- Telecel debit and MTN credit paired into one flow.
- Telecel debit alone visible after one hour as one internal flow when destination is own MTN number.
- Matching expires after 10 hours.
- GCB card top-up paired with prepaid card credit.
- GCB suspicious negative balance is stored but not trusted as reliable account balance.
- Cash-in/cash-deposit classified separately from ordinary income.

## Open Implementation Choices

- Whether transaction events and transaction flows become new Room tables immediately, or whether the first implementation extends `TransactionEntity` and adds flow grouping incrementally.
- Whether inferred instruments use a new table or start as metadata attached to transaction flows.
- How much of the flow UI lands in the first implementation slice versus parser/domain tests first.

Recommended path: add domain models and tests first, then persistence, then UI.
