package com.kevin.financeguardian.data.learning

import com.kevin.financeguardian.domain.model.MoneyMovementType

data class RecurringPattern(
    val identityKey: String,
    val kind: Kind,
    val transactionCount: Int,
    val averageIntervalDays: Double,
    val averageAmountMinor: Long,
    val suggestedMoneyMovementType: MoneyMovementType,
) {
    enum class Kind {
        SUBSCRIPTION_CANDIDATE,
        RECURRING_EXPENSE,
        INCOME_SOURCE,
    }
}
