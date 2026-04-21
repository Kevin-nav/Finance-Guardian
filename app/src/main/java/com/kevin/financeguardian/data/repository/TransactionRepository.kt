package com.kevin.financeguardian.data.repository

import com.kevin.financeguardian.domain.model.Transaction
import com.kevin.financeguardian.domain.model.MoneyMovementType
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun observeTransactions(): Flow<List<Transaction>>
    suspend fun getTransaction(id: String): Transaction?
    suspend fun insertTransaction(transaction: Transaction)
    suspend fun updateCategory(transactionId: String, categoryId: String?)
    suspend fun updateMoneyMovementType(transactionId: String, type: MoneyMovementType)
}
