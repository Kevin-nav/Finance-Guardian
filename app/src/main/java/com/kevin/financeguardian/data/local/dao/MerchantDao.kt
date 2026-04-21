package com.kevin.financeguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kevin.financeguardian.data.local.entity.MerchantEntity

@Dao
interface MerchantDao {
    @Upsert
    suspend fun upsert(entity: MerchantEntity)

    @Query("SELECT * FROM merchants WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): MerchantEntity?

    @Query("SELECT * FROM merchants WHERE phone = :phone LIMIT 1")
    suspend fun findByPhone(phone: String): MerchantEntity?
}
