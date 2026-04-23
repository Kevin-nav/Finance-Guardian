package com.kevin.financeguardian.data.local

import androidx.room.withTransaction
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.local.dao.CategoryDao
import com.kevin.financeguardian.data.local.dao.MerchantDao
import com.kevin.financeguardian.data.local.dao.ParserRuleDao
import com.kevin.financeguardian.data.local.dao.SmsMessageRecordDao
import com.kevin.financeguardian.data.local.dao.TransactionDao
import com.kevin.financeguardian.data.local.entity.CategoryEntity
import com.kevin.financeguardian.domain.model.DefaultCategories
import javax.inject.Inject

class RoomAppDataResetter @Inject constructor(
    private val database: FinanceGuardianDatabase,
    private val transactionDao: TransactionDao,
    private val merchantDao: MerchantDao,
    private val smsMessageRecordDao: SmsMessageRecordDao,
    private val parserRuleDao: ParserRuleDao,
    private val categoryDao: CategoryDao,
    private val clock: AppClock,
) : AppDataResetter {
    override suspend fun resetAllData() {
        database.withTransaction {
            transactionDao.deleteAll()
            merchantDao.deleteAll()
            smsMessageRecordDao.deleteAll()
            parserRuleDao.deleteAll()
            categoryDao.deleteAll()

            val now = clock.now()
            categoryDao.upsertAll(
                DefaultCategories.values.map { category ->
                    CategoryEntity(
                        id = category.id,
                        name = category.name,
                        type = category.type,
                        isArchived = false,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
        }
    }
}
