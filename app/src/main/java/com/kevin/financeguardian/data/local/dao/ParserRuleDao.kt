package com.kevin.financeguardian.data.local.dao

import androidx.room.Dao
import androidx.room.Upsert
import com.kevin.financeguardian.data.local.entity.ParserRuleEntity

@Dao
interface ParserRuleDao {
    @Upsert
    suspend fun upsertAll(rules: List<ParserRuleEntity>)
}
