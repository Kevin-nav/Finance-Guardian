package com.kevin.financeguardian.data.repository

import com.kevin.financeguardian.data.local.dao.TransactionDao
import com.kevin.financeguardian.data.local.mapper.toDomain
import com.kevin.financeguardian.data.local.mapper.toEntity
import com.kevin.financeguardian.domain.model.MoneyMovementType
import com.kevin.financeguardian.domain.model.Transaction
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
) : TransactionRepository {
    override fun observeTransactions(): Flow<List<Transaction>> =
        transactionDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTransaction(id: String): Transaction? =
        transactionDao.getById(id)?.toDomain()

    override suspend fun insertTransaction(transaction: Transaction) {
        transactionDao.insert(transaction.toEntity())
    }

    override suspend fun updateCategory(transactionId: String, categoryId: String?) {
        transactionDao.updateCategory(
            transactionId = transactionId,
            categoryId = categoryId,
            updatedAt = Instant.now(),
        )
    }

    override suspend fun updateMoneyMovementType(
        transactionId: String,
        type: MoneyMovementType,
    ) {
        transactionDao.updateMoneyMovementType(
            transactionId = transactionId,
            type = type,
            updatedAt = Instant.now(),
        )
    }
}
