package com.kevin.financeguardian.data.transaction

sealed interface TransactionCorrectionResult {
    data object Applied : TransactionCorrectionResult
    data object NotFound : TransactionCorrectionResult
}
