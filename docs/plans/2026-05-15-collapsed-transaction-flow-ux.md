# Collapsed Transaction Flow UX Notes

## Goal

The transaction list should show one meaningful money movement, not one row per SMS. When MTN, Telecel Cash, and GCB describe two sides of the same own-to-own movement, the app should collapse them into one transaction flow, explain why it did that, and let the user correct it.

This document focuses on the UX layer that should sit on top of the current transaction-flow implementation.

## Current Implementation Context

The current PR already adds the core mechanics:

- Parsed SMS events now persist provider evidence such as channel, source instrument, destination instrument, provider reference, inferred identifiers, flow type, flow status, and balance reliability.
- `SmsIngestionService` classifies new messages into transaction flows and links matching events when enough evidence exists.
- `TransactionsViewModel` groups rows by `flowId`, so linked internal transfers appear as one row.
- Corrections apply to all transactions in a collapsed flow, not only the tapped SMS row.
- Suspicious balances are excluded from provider balance summaries.
- The balances visibility preference masks balance and amount surfaces.
- Settings supports user-owned wallet setup, which improves internal transfer proof.

The larger missing UX piece is a detail experience that explains a collapsed flow and gives the user precise correction controls.

## UX Principles

- Show flows by default, events only when the user asks for evidence.
- Explain the app's confidence in plain language, not parser terminology.
- Treat planned use as intent and flow type as accounting effect. For example, `Bank to Wallet 0549037907 food T260` can be an internal transfer with planned use `food`.
- Never label a transfer internal from channel wording alone. The UI should make ownership proof visible when it matters.
- Make corrections flow-wide. If two SMS messages are collapsed into one flow, correcting the flow should update both sides.
- Respect hidden balances everywhere, including the detail sheet, evidence cards, and timeline.
- Keep the transaction list dense. The list should hint at confidence and status, while the detail sheet carries the explanation.

## Transaction List Row

Each row should represent a `flowId` group when present. The row should stay simple:

- Title: source and destination when known, such as `Telecel Cash -> MTN MoMo`, `GCB Main -> MTN Wallet`, `GCB Main -> Virtual Card`, or `External Wallet -> GCB Main`.
- Subtitle: planned use/category, flow label, and time, such as `Data - Internal transfer - 15:33`.
- Amount: neutral styling for internal transfers, expense/income styling for real spending or inflows.
- Status chip when useful: `Matched`, `Waiting for pair`, `Inferred`, `Needs review`, `Excluded from totals`, or `Suspicious balance`.

Internal transfers should not look like expenses. A neutral amount plus an `Excluded` or `Transfer` chip is clearer than a red debit, because the accounting effect is movement between the user's own instruments.

When balances are hidden, the row should keep the same structure and mask the amount only. Flow labels, category, provider names, and correction status can remain visible.

## Flow Detail Sheet

Tapping a transaction row should open a flow detail sheet. The sheet should answer three questions quickly:

1. What happened?
2. Why did the app classify it this way?
3. What can I correct?

### Header

The header should show:

- Amount, masked when balances are hidden.
- Flow type: internal transfer, expense, income, cash deposit, card spend, or needs review.
- Source to destination summary.
- Planned use/category if available.
- Status: matched, waiting, inferred, unmatched, or needs review.

Example:

```text
GCB Main -> MTN Wallet
Internal transfer - Food
Matched from 2 messages - Excluded from totals
```

### Accounting Impact

The next section should explain how this affects reports:

- `Excluded from spending and income` for internal transfers.
- `Counts as spending` for expenses and card spends.
- `Counts as income` for income.
- `Cash deposit` as a separate inflow type, because it increases balances but may not be earned income.

This section should be visible because it is where wrong internal-transfer classifications become expensive for the user's reports.

### Instruments

Show the source and destination instruments in a compact two-column or vertical layout:

- Provider: MTN, Telecel Cash, GCB, virtual card, or unknown.
- User label when available, such as `My MTN`.
- Masked identifier, such as `054 *** 7907` or `XXXX4127`.
- Ownership state: user-confirmed, strongly inferred, external, or unknown.

For inferred instruments, include a correction action:

- `This is mine`
- `Not mine`
- `Rename`

The app should not force users to know their GCB virtual card identity up front. If a GCB account suffix or virtual card token is inferred from messages, the UI can show it as system-inferred and let the user confirm or reject it.

### Evidence Timeline

The detail sheet should show the parsed evidence behind the flow. This should be short by default and expandable.

For each source SMS event, show:

- Provider.
- Message time.
- Direction from that provider.
- Parsed amount, masked when balances are hidden.
- Parsed channel, such as bank to wallet, wallet to bank, card top-up, cash-in, or merchant payment.
- Parsed source/destination identifiers.
- Reference or planned-use text.

For a matched Telecel-to-MTN transfer, the timeline might show:

```text
Telecel Cash - Debit - 15:33
Sent GHS20.00 to MTN wallet 054 *** 7907
Reference: Data

MTN MoMo - Credit - 15:34
Received GHS20.00 from Telecel wallet 050 *** 0861
Reference: Data
```

If raw SMS bodies are not stored, the UI should avoid pretending it can show the original message. It should show parsed facts and a small `Parsed from SMS` label. Raw SMS snippets can stay debug-only if needed later.

### Matching State

Delayed messages need explicit states:

- Under 1 hour from first event: `Waiting for matching SMS`.
- After 1 hour with enough evidence: show it as one flow, but keep the status `Single-message match`.
- Between 1 and 10 hours: `Still watching for the other SMS until <time>`.
- After 10 hours: `No matching SMS found`.
- If a later match arrives within 10 hours, update to `Matched from 2 messages`.

This makes the one-hour visible behavior and ten-hour matching behavior understandable instead of surprising.

## Corrections

Corrections should be available from the detail sheet through an edit action.

Recommended controls:

- Flow type segmented control: expense, income, internal transfer, cash deposit, card spend, unknown.
- Category picker.
- Planned use text field.
- Source instrument picker.
- Destination instrument picker.
- Ownership confirmation for inferred instruments.
- `Unlink messages` when two events were incorrectly collapsed.
- `Find matching message` later, if manual merge becomes necessary.

When the user changes the flow type, the app should immediately explain the accounting effect. For example, choosing internal transfer should show `This will remove the flow from spending and income totals`.

Corrections should be reversible where possible. The user should be able to fix an overconfident inference without losing the original parsed evidence.

## Settings And Onboarding

Wallet setup belongs in settings and can also appear as a lightweight onboarding prompt.

Recommended settings section:

- Title: `Wallets and accounts`
- Add wallet: provider, label, phone number.
- Saved wallet rows: label, provider, masked phone, edit/remove.
- Inferred instruments review: GCB account suffixes, GCB virtual card tokens, and wallets seen in paired messages.

The app should not require users to have MTN, Telecel, and GCB. A user can provide only one wallet, and the parser should still use that signal.

Phone input should assume Ghana numbers for now and normalize these as the same identity:

- `0549037907`
- `+233549037907`
- `233549037907`

## Balance Visibility

The eye toggle should be global. Once hidden, it should mask:

- Home balance hero.
- Provider balance pills.
- Transaction row amounts.
- Flow detail amount.
- Event timeline amounts and balances.
- Accounting impact amounts.

It should not hide non-sensitive labels such as provider names, categories, flow statuses, planned-use text, or dates. Those are useful for navigation even when the user is hiding money values.

## Empty And Review States

The UX needs calm states for imperfect evidence:

- No wallets saved: show an inline settings nudge such as `Add your wallets to improve internal transfer detection`.
- Unknown ownership: show `Needs review`, not `Internal transfer`.
- Strong system proof but no user confirmation: show `Inferred internal transfer`.
- Suspicious balance: show `Balance not used in summaries` and keep the transaction itself visible.
- One-sided flow after 10 hours: show `No matching SMS found` and retain correction options.
- Amount mismatch between two candidate messages: do not collapse automatically; show needs review if surfaced later.

## Recommended First UX Slice

The smallest useful next implementation should be:

1. Add a `TransactionFlowDetail` UI model in `TransactionsViewModel`.
2. Open a bottom sheet from `TransactionRow`.
3. Show header, accounting impact, instruments, and evidence timeline using persisted event fields.
4. Add flow-wide correction controls for flow type, category, and planned use.
5. Respect `balancesVisible` in every detail section.
6. Add tests for row tap state, hidden balances, flow-wide correction, and matched/unmatched status labels.

Manual merge/search can wait. The first slice should focus on helping the user understand and correct the flow already shown in the list.

## Open Product Questions

- Should internal transfer amounts be shown in a neutral color or in a dedicated transfer color?
- Should planned use appear as the row's first subtitle item or as a chip?
- Should inferred instruments appear in a separate review queue, or only inside the transaction detail where they are first relevant?
- Should cash deposits count in income totals by default, or only in balance movement summaries?
- Should raw SMS text ever be visible to users, or should the app show parsed facts only?
