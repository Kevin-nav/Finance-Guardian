package com.kevin.financeguardian.data.transaction

import com.kevin.financeguardian.domain.model.MoneyMovementType

interface TransactionCorrectionApplier {
    suspend fun applyCorrection(
        transactionId: String,
        categoryId: String?,
        moneyMovementType: MoneyMovementType?,
        saveMerchantDefault: Boolean,
    ): TransactionCorrectionResult
}
