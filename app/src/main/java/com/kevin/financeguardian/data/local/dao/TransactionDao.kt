package com.kevin.financeguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevin.financeguardian.data.local.entity.TransactionEntity
import com.kevin.financeguardian.domain.model.MoneyMovementType
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TransactionEntity)

    @Query(
        """
        UPDATE transactions
        SET categoryId = :categoryId, updatedAt = :updatedAt
        WHERE id = :transactionId
        """,
    )
    suspend fun updateCategory(transactionId: String, categoryId: String?, updatedAt: Instant)

    @Query(
        """
        UPDATE transactions
        SET moneyMovementType = :type, updatedAt = :updatedAt
        WHERE id = :transactionId
        """,
    )
    suspend fun updateMoneyMovementType(
        transactionId: String,
        type: MoneyMovementType,
        updatedAt: Instant,
    )

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
