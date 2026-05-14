package com.kevin.financeguardian.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kevin.financeguardian.data.local.converter.RoomConverters
import com.kevin.financeguardian.data.local.dao.CategoryDao
import com.kevin.financeguardian.data.local.dao.LearningSignalDao
import com.kevin.financeguardian.data.local.dao.MerchantDao
import com.kevin.financeguardian.data.local.dao.ParserRuleDao
import com.kevin.financeguardian.data.local.dao.SmsMessageRecordDao
import com.kevin.financeguardian.data.local.dao.TransactionDao
import com.kevin.financeguardian.data.local.entity.CategoryEntity
import com.kevin.financeguardian.data.local.entity.LearningSignalEntity
import com.kevin.financeguardian.data.local.entity.MerchantEntity
import com.kevin.financeguardian.data.local.entity.ParserRuleEntity
import com.kevin.financeguardian.data.local.entity.SmsMessageRecordEntity
import com.kevin.financeguardian.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        MerchantEntity::class,
        LearningSignalEntity::class,
        CategoryEntity::class,
        SmsMessageRecordEntity::class,
        ParserRuleEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class FinanceGuardianDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun learningSignalDao(): LearningSignalDao
    abstract fun merchantDao(): MerchantDao
    abstract fun smsMessageRecordDao(): SmsMessageRecordDao
    abstract fun parserRuleDao(): ParserRuleDao
}
