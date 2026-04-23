package com.kevin.financeguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

    @Query("SELECT * FROM transactions ORDER BY createdAt ASC, id ASC")
    suspend fun getAllOnce(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE dedupeKey = :dedupeKey LIMIT 1")
    suspend fun findByDedupeKey(dedupeKey: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TransactionEntity)

    @Update
    suspend fun update(entity: TransactionEntity)

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

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
