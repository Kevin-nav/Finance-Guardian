package com.kevin.financeguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kevin.financeguardian.data.local.entity.LearningSignalEntity
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LearningSignalDao {
    @Query("SELECT * FROM learning_signals WHERE signalKey = :signalKey LIMIT 1")
    abstract suspend fun getBySignalKey(signalKey: String): LearningSignalEntity?

    @Query("SELECT * FROM learning_signals ORDER BY createdAt ASC, id ASC")
    abstract suspend fun getAllOnce(): List<LearningSignalEntity>

    @Query("SELECT * FROM learning_signals ORDER BY updatedAt DESC, id ASC")
    abstract fun observeAll(): Flow<List<LearningSignalEntity>>

    @Query("SELECT COUNT(*) FROM learning_signals WHERE signalType = :signalType")
    abstract fun observeCountBySignalType(signalType: String): Flow<Int>

    @Query(
        """
        SELECT * FROM learning_signals
        WHERE normalizedMerchantName = :normalizedMerchantName
        ORDER BY updatedAt DESC, id ASC
        """,
    )
    abstract suspend fun findByNormalizedMerchantName(
        normalizedMerchantName: String,
    ): List<LearningSignalEntity>

    @Query(
        """
        SELECT * FROM learning_signals
        WHERE normalizedPhone = :normalizedPhone
        ORDER BY updatedAt DESC, id ASC
        """,
    )
    abstract suspend fun findByNormalizedPhone(
        normalizedPhone: String,
    ): List<LearningSignalEntity>

    @Query(
        """
        SELECT * FROM learning_signals
        WHERE normalizedReference = :normalizedReference
        ORDER BY updatedAt DESC, id ASC
        """,
    )
    abstract suspend fun findByNormalizedReference(
        normalizedReference: String,
    ): List<LearningSignalEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(entity: LearningSignalEntity)

    @Update
    protected abstract suspend fun update(entity: LearningSignalEntity)

    @Transaction
    open suspend fun upsert(entity: LearningSignalEntity) {
        val existing = getBySignalKey(entity.signalKey)
        if (existing == null) {
            insert(entity)
            return
        }

        update(
            existing.copy(
                transactionId = entity.transactionId,
                provider = entity.provider,
                normalizedMerchantName = entity.normalizedMerchantName,
                normalizedPhone = entity.normalizedPhone,
                normalizedReference = entity.normalizedReference,
                amountBucket = entity.amountBucket,
                direction = entity.direction,
                moneyMovementType = entity.moneyMovementType,
                categoryId = entity.categoryId,
                signalType = entity.signalType,
                weight = entity.weight,
                updatedAt = entity.updatedAt,
            ),
        )
    }
}
