package com.kevin.financeguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kevin.financeguardian.data.local.entity.MerchantEntity
import java.time.Instant

@Dao
interface MerchantDao {
    @Upsert
    suspend fun upsert(entity: MerchantEntity)

    @Query("SELECT * FROM merchants WHERE id = :id")
    suspend fun getById(id: String): MerchantEntity?

    @Query("SELECT * FROM merchants WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): MerchantEntity?

    @Query("SELECT * FROM merchants WHERE phone = :phone LIMIT 1")
    suspend fun findByPhone(phone: String): MerchantEntity?

    @Query(
        """
        UPDATE merchants
        SET defaultCategoryId = :defaultCategoryId, updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateDefaultCategory(
        id: String,
        defaultCategoryId: String?,
        updatedAt: Instant,
    )
}
