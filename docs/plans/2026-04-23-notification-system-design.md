# Notification System Design

## Goal

Design a notification system for Finance Guardian that feels calm, helpful, and privacy-aware across both Android system notifications and in-app notices.

The target quality bar is high restraint: notifications should feel intentional, never mechanical, and should only interrupt when there is clear user value.

## Current Context

The app currently:

- Requests `POST_NOTIFICATIONS` during onboarding and from Settings.
- Detects incoming financial SMS through `SmsBroadcastReceiver` and `SmsIngestionService`.
- Produces concrete ingestion outcomes through `SmsIngestionResult`.
- Lets users correct transactions from the transactions screen.
- Has no notification architecture, no channels, no event model, and no in-app notice system.

That means the product has real events worth surfacing, but no UX layer yet to decide which ones deserve attention and how they should be presented.

## Product Posture

The agreed posture for V1 is:

- `Balanced assistant`
- Privacy-aware lock screen behavior
- Simple controls in Settings
- A small amount of proactive behavior

More specifically:

- Transaction notifications should be useful, but not exhaustive.
- Lock screen notifications may acknowledge money movement and may include the amount.
- Lock screen notifications must not reveal merchant names, counterparties, references, or category detail.
- The settings model should stay simple, not expose a large matrix of per-event toggles.
- Proactive insights are allowed, but only if they are high-signal and tightly rate-limited.

## Notification Philosophy

The system should follow five rules:

1. `Earn the interruption`
Only interrupt when the user gets clear value from knowing now.

2. `Respect privacy by context`
Keep lock screen copy generic; reveal more only after unlock or inside the app.

3. `Prefer reassurance over alarm`
Normal financial activity should sound calm and competent, not dramatic.

4. `Escalate progressively`
Use inline state and in-app banners first; reserve tray notifications for meaningful activity or required action.

5. `Be predictable`
Similar events should produce similar notification behavior so the app feels deliberate.

## Surface Hierarchy

The notification system should use three layers:

- `System notifications`
For time-sensitive alerts, important changes, and moments the user would otherwise miss.

- `In-app notices`
For temporary confirmations and lightweight updates while the app is open.

- `Inline status messaging`
For persistent conditions such as missing permissions, review backlog, or security/privacy issues.

## User-Facing Categories

Internally the system can be more detailed, but the product should organize notifications into four clear families:

- `Transaction alerts`
- `Review needed`
- `Security`
- `Insights`

These categories matter for copy, grouping, priority, and future tuning, even if V1 exposes only simple controls.

## Notification Matrix

### System Notifications

These events should be eligible for Android tray notifications.

| Event | Notify? | Why |
| --- | --- | --- |
| New parsed transaction | Yes, selectively | Core proof that the app is working and tracking money movement |
| Transaction needs review | Yes | Actionable and improves trust and categorization quality |
| SMS permission revoked | Yes | Core functionality may silently stop |
| Notification permission revoked | Yes, in-app first and system if possible before loss | User may stop receiving alerts |
| App lock disabled | Yes | Trust-critical security change |
| Notification privacy weakened | Yes | Trust-critical privacy change |
| High-signal proactive insight | Yes, sparingly | Makes the app feel intelligent without becoming chatty |

### In-App Only

These should be shown only while the user is in the app.

| Event | Surface | Why |
| --- | --- | --- |
| Correction saved | Banner/snackbar | Useful confirmation, not interruption-worthy |
| Transaction updated | Banner/snackbar | Same |
| Permission granted | Banner/snackbar | Contextual confirmation |
| Fixture import result | Banner/inline | Developer-facing and contextual |
| Data reset result | Banner/inline | Important but only relevant in context |
| Onboarding step confirmation | Inline/banner | Flow guidance, not a tray alert |
| Soft insight summaries | Home card/inline | Better inside the product than in the tray |

### Silent

These should not notify the user.

- Duplicate SMS detected
- Ignored non-financial SMS
- Routine parser success with no user value in surfacing it
- Automatic merchant-learning updates
- Routine app relock on background
- Background maintenance success

## Priority Model

The system should use three priority levels:

- `High priority`
Review-needed alerts, permission degradation, and security/privacy changes.

- `Standard priority`
New expense, new income, and other meaningful transaction detections.

- `Low priority`
Proactive insights.

Security notifications should always stand alone. Insights should never compete with more important alerts.

## Grouping And Rate Limits

Grouping and rate limiting are necessary to avoid notification fatigue.

### Grouping

- Group by `notification family`, not by provider.
- Group normal transaction alerts together.
- Group review-needed alerts together.
- Keep security notifications separate.

Example summaries:

- `3 new transactions detected`
- `2 transactions need review`

### Rate Limits

Recommended defaults:

- `Transaction alerts`
One immediate alert every 2 to 3 minutes, then group the rest.

- `Review needed`
Allow an immediate alert, but cap repeated alerts in a short window.

- `Security`
Immediate, not batched.

- `Insights`
At most one per day initially, and never adjacent to a recent security or review-needed alert.

## Timing

- `Transaction detected`
Near-immediate.

- `Review needed`
Immediate.

- `Permission or security issue`
Immediate on detection or app resume.

- `Insights`
Delayed and context-aware. They should appear only after enough signal exists to be meaningful.

## Tone And Copy

The voice should be:

- calm
- precise
- discreet
- matter-of-fact

Avoid:

- hype
- cheerleading
- moralizing language
- technical internal language
- vague wording

Preferred pattern:

- `Title`: what happened
- `Body`: why it matters or what to do next
- `Action`: one obvious action

## Privacy Rules

Privacy behavior must be explicit in the design, not implied by convention.

### Lock Screen

Allowed:

- event type
- generalized financial activity
- amount, when appropriate

Not allowed:

- merchant names
- counterparties
- references
- categories
- account-like details

### After Unlock Or In-App

Allowed:

- merchant or source names
- fuller review context
- actionable detail

## Recommended Copy Templates

### Transaction Alerts

`New expense detected`

- Lock screen: `GHS 24.00 recorded in Finance Guardian`
- Unlocked: `GHS 24.00 at Melcom`
- Action: `Open`

`New income detected`

- Lock screen: `GHS 850.00 recorded in Finance Guardian`
- Unlocked: `GHS 850.00 received from Salary`
- Action: `Open`

### Review Needed

`Transaction needs review`

- Lock screen: `A transaction needs your review`
- Unlocked: `GHS 24.00 could not be fully categorized`
- Action: `Review`

### Permission And Security

`SMS access turned off`

- Body: `New transactions may stop appearing until access is restored.`
- Action: `Fix`

`Notifications turned off`

- Body: `Finance Guardian may not be able to alert you about new activity.`
- Action: `Open Settings`

`App lock turned off`

- Body: `Your financial data will open without authentication.`
- Action: `Review`

### Insights

`Spending is higher than usual today`

- Body: `You have more outgoing transactions than usual for this time of day.`
- Action: `View`

## Proactive Insight Guardrails

Insights are allowed, but should stay narrow in V1.

Good early candidates:

- unusual number of outgoing transactions today
- spending above recent daily norm
- review backlog building up
- repeated spending pattern in a short window

Bad early candidates:

- tiny fluctuations
- generalized financial wellness commentary
- judgmental reminders
- repeated restatements of information the app already surfaced

## Settings Model

The user-facing controls should remain simple.

Recommended V1 controls:

- `Notifications` master toggle
- `Proactive insights` toggle
- `Show amounts on lock screen` toggle

This keeps the product simple while still giving users control over the two preferences most likely to vary: insight nudges and lock-screen privacy.

If the product needs to be even simpler, the fallback set is:

- `Notifications`
- `Proactive insights`
- `Lock screen privacy`

## Recommended Architecture

The implementation should avoid scattering notification decisions across feature code.

Use one internal pipeline:

- `NotificationEvent`
High-level UX events such as `TransactionDetected`, `TransactionNeedsReview`, `PermissionRevoked`, `SecurityStateChanged`, `InsightTriggered`, and `CorrectionSaved`.

- `NotificationPolicyEngine`
Maps events to `system`, `in-app`, `inline`, or `silent`, and applies priority, grouping, privacy, and rate-limit rules.

- `NotificationComposer`
Turns approved events into user-facing copy, actions, grouping keys, and privacy-safe message variants.

- `SystemNotificationManager`
Renders Android tray notifications only.

- `InAppNoticeManager`
Owns temporary banners/snackbars and notice feed updates while the app is open.

## Privacy Model In Code

Each composed notification should carry a privacy level.

Suggested levels:

- `PublicSafe`
- `PrivateSummary`
- `SensitiveDetail`

This lets the system choose the right copy for lock-screen-safe and unlocked surfaces without hand-written conditionals throughout the codebase.

## Implementation Recommendation

The first release of this system should focus on:

1. Core event and policy foundations
2. System notifications for transactions, review-needed, permissions, and security
3. In-app notices for confirmations and contextual results
4. One or two proactive insight types only

Anything beyond that should wait until the app has real usage data.

## Explicit Non-Goals For V1

- Per-event notification settings
- Rich notification actions that mutate data directly
- Broad proactive coaching
- Judgmental copy
- Frequent reminder loops
- Surfacing parser internals to the user

## Acceptance Criteria

The notification design is successful when:

- The app notifies for meaningful financial events without becoming noisy.
- Lock screen notifications protect merchant and counterparty privacy.
- Review-needed items surface quickly and clearly.
- Security and permission regressions are surfaced immediately.
- In-app confirmations feel polished and unobtrusive.
- Insight nudges feel occasional and useful rather than promotional.
