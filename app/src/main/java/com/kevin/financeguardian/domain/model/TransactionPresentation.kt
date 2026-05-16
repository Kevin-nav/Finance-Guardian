package com.kevin.financeguardian.domain.model

fun Transaction.effectiveIsCredit(): Boolean =
    if (includedInIncomeTotals) {
        true
    } else if (includedInSpendingTotals) {
        false
    } else {
        direction == TransactionDirection.CREDIT && moneyMovementType != MoneyMovementType.INTERNAL_TRANSFER
    }

fun Transaction.rawDirectionIsCredit(): Boolean =
    when (moneyMovementType) {
        MoneyMovementType.INCOME -> true
        MoneyMovementType.EXPENSE,
        MoneyMovementType.SUBSCRIPTION_CANDIDATE,
        -> false
        else -> direction == TransactionDirection.CREDIT
    }
