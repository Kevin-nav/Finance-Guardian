package com.kevin.financeguardian.domain.model

fun Transaction.effectiveIsCredit(): Boolean =
    when (moneyMovementType) {
        MoneyMovementType.INCOME -> true
        MoneyMovementType.EXPENSE,
        MoneyMovementType.SUBSCRIPTION_CANDIDATE,
        -> false
        else -> direction == TransactionDirection.CREDIT
    }
