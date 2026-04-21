# Personal Finance App — High Level PRD

---

## Overview
A personal Android application built for one user (Kevin) that automatically monitors spending across Ghanaian mobile money and banking platforms via SMS, learns spending patterns over time, and helps make informed budgeting decisions with no fixed income assumption.

---

## Problem Statement
Tracking personal spending across multiple platforms (MoMo, bank, etc.) in Ghana is fragmented and manual. There is no single tool that understands the local financial ecosystem well enough to automatically categorize, learn, and budget around how money actually moves in this context.

---

## Goals
- Automatically capture all financial activity via SMS
- Learn and categorize spending patterns over time with minimal ongoing manual input
- Enable flexible, income-event-driven percentage-based budgeting
- Surface unnecessary spending and suggest reductions
- Track progress toward savings goals

---

## Target User
Single user. Android (Samsung). Ghanaian financial ecosystem (MTN MoMo, Telecel Cash, GCB and other banks).

---

## Core Features

### 1. SMS Transaction Ingestion
- App reads and parses incoming SMS from MoMo, bank, and other financial platforms in real time
- Detects transaction type: debit, credit, transfer
- Understands money movement between bank and MoMo wallet as a distinct event (not an expense)
- Subscription charges (bank card debits, recurring amounts) are flagged automatically

### 2. Merchant Learning & Categorization
- User manually flags merchants/numbers with categories in early usage
- App builds a merchant registry over time — linking phone numbers/merchant names to categories
- Three-tier expense model:
  - **Tier 1** — Fixed subscriptions (same merchant, same amount, regular interval)
  - **Tier 2** — Recurring variable expenses (same merchant, changing amount, consistent frequency e.g. laundry)
  - **Tier 3** — One-off/general spending
- As data accumulates, app begins suggesting categories for new/unknown merchants
- User confirms or corrects suggestions — corrections feed back into the model
- Long-term goal: enough labeled data to train a personal ML model on actual spending behavior

### 3. Income Detection & Budget Allocation
- Income is confirmed via:
  - Manual confirmation when a credit is detected
  - Automatic flagging if balance increases significantly
  - Whitelisted sources (e.g. parents' numbers tagged as income)
- On income confirmation, app suggests a percentage-based budget allocation informed by historical spending patterns
- Tier 1 & 2 expenses are pre-filled in the suggestion based on learned patterns
- User approves or adjusts the allocation
- Budget stays locked until the next income event
- If total balance increases significantly mid-cycle, reallocation is triggered and suggested

### 4. Spending Alerts & Insights
- Notifications when spending in a category approaches or exceeds its allocated %
- Periodic insights surfacing categories where spending is higher than previous periods
- Suggestions on where spending could be reduced based on patterns

### 5. Savings Goals
- User creates named goals with a target amount (e.g. "New Laptop — GHS 3,000")
- Every outflow to a designated savings account number is logged as a contribution
- App cannot see inside the savings account — only tracks outflows toward it
- Progress tracked visually against the goal
- App can suggest contribution amounts based on current available balance

### 6. Subscription Tracking
- Subscriptions detected via bank SMS (same merchant, similar amount, ~monthly interval)
- User confirms once, tracked permanently
- Surfaced clearly so user is always aware of recurring committed expenses

---

## Data & Learning Strategy
- **Local-first** — all data stored on device using Room DB
- **Cloud sync** — Convex used for backup and sync when needed
- **Learning pipeline:**
  - Manual labels in early usage → labeled transaction dataset
  - Pattern recognition kicks in after sufficient data
  - Model improves continuously through user corrections
  - Long-term: personal ML model trained on user's own data

---

## Tech Stack
| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Local DB | Room |
| SMS | BroadcastReceiver |
| Cloud | Convex |
| Background processing | Android WorkManager / Foreground Service |

---

## Out of Scope (for MVP)
- Cross-platform support
- Multi-user support
- Direct bank/MoMo API integrations
- Web dashboard

---

## Key Design Constraints
- No fixed income — all budgeting is event-driven and percentage-based
- App must work even if user hasn't opened it (background SMS listening)
- Privacy-first — financial data stays local unless user opts into cloud sync
- Must handle Ghanaian SMS formats specifically (MTN MoMo, Telecel Cash, GCB, etc.)
