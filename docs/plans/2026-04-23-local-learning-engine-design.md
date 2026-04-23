# Local Learning Engine Design

## Goal

Design a local-first learning system that improves transaction categorization and money-movement suggestions over time using Kevin's own correction history.

This slice should stay practical. It should not introduce a heavyweight ML stack, remote training, or opaque prediction logic. The first version should be explainable, testable, and useful with small amounts of labeled data.

## Current Context

The app now has:

- Room-backed transactions, merchants, categories, and SMS message records
- Parser support for MTN, Telecel, and GCB
- Rule-based categorization through `MerchantCategoryResolver`
- User corrections through `TransactionCorrectionService`
- Historical duplicate repair and standardized transaction references/reasons

That means the app already has the foundations needed for learning:

- clean transaction history
- explicit user corrections
- merchant identity normalization
- references/reasons that carry useful context

What is still missing is a dedicated learning layer that converts user corrections into reusable prediction signals.

## Product Posture

The learning engine should behave like a conservative assistant:

- learn only from confirmed user behavior
- prefer explainable predictions over clever but brittle ones
- auto-apply only at high confidence
- suggest at medium confidence
- stay silent at low confidence

The system should get more helpful over time without ever feeling random.

## Recommended Approach

Build the learning system in stages.

### Stage 1: Structured Memory

Persist explicit learning signals from user-confirmed corrections and from high-confidence existing defaults.

Examples:

- merchant `BATAMADWOM ENTERPRISE` repeatedly corrected to `Food`
- reference `snacks` repeatedly corrected to `Food`
- incoming credits from a known source repeatedly confirmed as `Income`
- `bank to wallet` descriptions repeatedly marked as `Internal Transfer`

This stage is not a statistical model yet. It is durable user-specific memory.

### Stage 2: Weighted Suggestion Engine

Use those stored signals to score categories and movement types for new transactions.

The score should combine:

- merchant-name match
- phone match
- reference match
- provider match
- movement-type history
- amount-bucket similarity
- recency and frequency

This produces a ranked suggestion list and a confidence score.

### Stage 3: Recurring Pattern Detection

Detect behavioral patterns that are not just merchant defaults:

- same merchant, same amount, regular interval => subscription candidate
- same merchant, varying amount, regular interval => recurring expense candidate
- repeated credit source => likely income source
- repeated account transfer phrases => internal transfer candidate

This stage improves money movement classification and future budgeting features.

### Stage 4: Optional Lightweight ML

Only after enough labeled data exists, add a small on-device classifier as a ranking layer on top of the interpretable engine.

This should be deferred until the rule-and-memory engine is working and trustworthy.

## Why Not A Full ML Model First

A neural or TFLite model is the wrong first move here because:

- the dataset will be small for a long time
- debugging category mistakes will be harder
- confidence calibration will be poor early on
- interpretability matters for trust
- the app already has structured signals that can work well without ML

The first model should be a transparent scoring engine with explicit features.

## Data Model

Add a new Room table for stored learning examples.

### `LearningSignal`

Suggested fields:

- `id: String`
- `transactionId: String?`
- `provider: Provider`
- `normalizedMerchantName: String?`
- `normalizedPhone: String?`
- `normalizedReference: String?`
- `amountBucket: String?`
- `direction: TransactionDirection`
- `moneyMovementType: MoneyMovementType`
- `categoryId: String?`
- `signalType: LearningSignalType`
- `weight: Float`
- `createdAt: Instant`
- `updatedAt: Instant`

### `LearningSignalType`

Suggested enum values:

- `USER_CORRECTION`
- `MERCHANT_DEFAULT`
- `HIGH_CONFIDENCE_AUTO_APPLY`
- `BACKFILLED_PATTERN`

In V1, `USER_CORRECTION` should carry the strongest weight.

## Feature Engineering

Normalize learning inputs using the same normalization rules already used for merchants:

- merchant name
- phone
- reference/reason text

Add a simple amount bucket strategy such as:

- `micro` for less than GHS 5
- `small` for GHS 5 to 29.99
- `medium` for GHS 30 to 149.99
- `large` for GHS 150+

This is enough for early learning without overfitting to exact values.

## Suggestion Engine

Create a backend service that takes a parsed or stored transaction candidate and returns:

```kotlin
data class LearningSuggestion(
    val suggestedCategoryId: String?,
    val suggestedMoneyMovementType: MoneyMovementType?,
    val confidence: Float,
    val reasons: List<String>,
)
```

### Scoring Inputs

Category score candidates should come from:

- exact phone match
- exact normalized merchant match
- exact normalized reference match
- merchant plus reference combination
- provider plus reference keyword pattern
- amount bucket co-occurrence

### Suggested Weighting Direction

Initial relative strength:

- exact phone match: strongest
- exact merchant match: very strong
- exact reference match: strong
- merchant plus reference combination: very strong
- amount bucket similarity: moderate
- provider-only pattern: weak support
- stale signal decay: slight downward adjustment over time

The exact numeric weights can stay implementation-specific as long as the ranking is stable and testable.

## Confidence Policy

Use three outcome bands.

### High Confidence

- auto-apply category and/or movement type
- still store that it was auto-applied
- surface normally in UI

### Medium Confidence

- do not auto-apply
- expose suggestion in transaction detail or review UI
- let user confirm or reject

### Low Confidence

- leave category unknown
- rely on current manual correction flow

This keeps trust intact while still making the app feel adaptive.

## Learning Sources

### Immediate Source: User Corrections

This is the most important signal.

Whenever a user saves a correction, store a learning example that reflects the final chosen category and movement type.

### Secondary Source: Saved Merchant Defaults

If a correction is saved as a merchant default, that should either:

- create its own learning signal, or
- update a durable merchant-linked learning signal

### Deferred Source: High-Confidence Auto-Applies

After the engine has proven stable, high-confidence auto-applied outcomes can feed back into future scoring with lower weight than explicit user corrections.

This should not be enabled in the first cut.

## Integration Points

### During Ingestion

After parser output and duplicate checks:

1. Apply existing hard rules and merchant defaults
2. Ask the learning engine for a category and movement suggestion
3. If confidence is high, apply automatically
4. If confidence is medium, persist suggested values or a review-needed state
5. If confidence is low, leave current behavior

### During User Correction

When `TransactionCorrectionService` saves a correction:

1. update the transaction
2. optionally update merchant default
3. write or update learning signals derived from the corrected transaction

### During Startup Or Maintenance

Optional later step:

- backfill learning signals from existing corrected transactions and strong merchant defaults

## Recurring Pattern Detection

This should be a separate service from the core category scorer.

The pattern detector should scan transaction history for:

- merchant repetition
- amount stability or variance
- interval regularity
- direction consistency

It should classify recurring patterns into:

- likely subscription
- likely recurring expense
- likely income source
- likely transfer route

This output can improve both money movement classification and future budgeting logic.

## UI Implications

This design stays backend-first, but it should support later UI states such as:

- suggested category chip
- “learned from your past corrections” explanation
- “review suggestion” callout
- confidence-aware review queue

The backend should therefore preserve explanation strings or explanation codes where possible.

## Testing Strategy

The design should be verified with focused unit tests:

- learning signal creation from corrected transactions
- scoring by merchant match
- scoring by reference match
- scoring by phone match
- confidence threshold behavior
- recurring subscription detection
- recurring variable expense detection
- income-source detection
- low-data fallback to current rules

## Acceptance Criteria

The learning design is successful when:

- the system becomes more accurate after repeated corrections
- category suggestions remain explainable
- wrong guesses do not get auto-applied at low confidence
- the engine works fully offline
- the first version uses small-data-friendly logic rather than heavyweight ML

## Explicit Non-Goals For V1

- cloud training
- syncing learning state to a backend
- neural models
- TFLite integration
- free-text embeddings
- auto-learning from unconfirmed guesses
- complex active-learning UX
