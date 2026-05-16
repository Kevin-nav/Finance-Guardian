package com.kevin.financeguardian.ui.components

/**
 * UI-layer models for the transaction flow detail sheet.
 * These are presentation models only — they carry pre-formatted
 * display data derived from the domain layer.
 */

// ── Flow Type (what kind of money movement) ─────────────────────────────────

enum class FlowTypeUi(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    INTERNAL_TRANSFER("Internal transfer"),
    CASH_DEPOSIT("Cash deposit"),
    CARD_SPEND("Card spend"),
    NEEDS_REVIEW("Needs review"),
    UNKNOWN("Unknown"),
}

// ── Flow Status (matching/review state) ─────────────────────────────────────

enum class FlowStatusUi(val label: String) {
    MATCHED("Matched"),
    PENDING_MATCH("Waiting for pair"),
    INFERRED("Inferred"),
    UNMATCHED("No matching SMS"),
    NEEDS_REVIEW("Needs review"),
    COMPLETE("Complete"),
}

// ── Ownership display ───────────────────────────────────────────────────────

enum class OwnershipUi(val label: String) {
    USER_CONFIRMED("Confirmed"),
    STRONGLY_INFERRED("Inferred"),
    EXTERNAL("External"),
    UNKNOWN("Unknown"),
}

// ── Instrument (source or destination wallet/account) ───────────────────────

data class InstrumentUi(
    val provider: String,
    val userLabel: String?,
    val maskedIdentifier: String?,
    val ownership: OwnershipUi,
)

// ── Evidence event (a single parsed SMS that contributed to the flow) ────────

data class EvidenceEventUi(
    val provider: String,
    val direction: String,
    val time: String,
    val amountMinor: Long,
    val currency: String,
    val channel: String,
    val sourceIdentifier: String?,
    val destinationIdentifier: String?,
    val reference: String?,
)

// ── Accounting impact (how this flow affects reports) ───────────────────────

data class AccountingImpactUi(
    val label: String,
    val description: String,
    val isExcluded: Boolean,
)

// ── Matching state (SMS pairing status) ─────────────────────────────────────

data class MatchingStateUi(
    val label: String,
    val detail: String?,
    val isWaiting: Boolean,
)

// ── Full flow detail model ──────────────────────────────────────────────────

/**
 * Complete presentation model for the flow detail bottom sheet.
 * Built by [TransactionsViewModel] from a collapsed flow group.
 */
data class TransactionFlowDetail(
    val flowId: String,
    val title: String,
    val flowType: FlowTypeUi,
    val flowStatus: FlowStatusUi,
    val accountingImpact: AccountingImpactUi,
    val amountMinor: Long,
    val currency: String,
    val categoryName: String,
    val categoryId: String?,
    val plannedUse: String?,
    val sourceInstrument: InstrumentUi?,
    val destinationInstrument: InstrumentUi?,
    val events: List<EvidenceEventUi>,
    val matchingState: MatchingStateUi?,
    val dateGroup: String,
    val timestamp: String,
    val flowEventCount: Int,
    val provider: String,
    val reference: String?,
    val balanceAfterMinor: Long?,
)
