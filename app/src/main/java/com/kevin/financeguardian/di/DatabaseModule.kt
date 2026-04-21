package com.kevin.financeguardian.di

import android.content.Context
import androidx.room.Room
import com.kevin.financeguardian.data.local.FinanceGuardianDatabase
import com.kevin.financeguardian.data.local.LocalDatabaseContract
import com.kevin.financeguardian.data.local.dao.CategoryDao
import com.kevin.financeguardian.data.local.dao.MerchantDao
import com.kevin.financeguardian.data.local.dao.ParserRuleDao
import com.kevin.financeguardian.data.local.dao.SmsMessageRecordDao
import com.kevin.financeguardian.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideFinanceGuardianDatabase(
        @ApplicationContext context: Context,
    ): FinanceGuardianDatabase =
        Room.databaseBuilder(
            context,
            FinanceGuardianDatabase::class.java,
            LocalDatabaseContract.DATABASE_NAME,
        ).build()

    @Provides
    fun provideTransactionDao(database: FinanceGuardianDatabase): TransactionDao =
        database.transactionDao()

    @Provides
    fun provideCategoryDao(database: FinanceGuardianDatabase): CategoryDao =
        database.categoryDao()

    @Provides
    fun provideMerchantDao(database: FinanceGuardianDatabase): MerchantDao =
        database.merchantDao()

    @Provides
    fun provideSmsMessageRecordDao(database: FinanceGuardianDatabase): SmsMessageRecordDao =
        database.smsMessageRecordDao()

    @Provides
    fun provideParserRuleDao(database: FinanceGuardianDatabase): ParserRuleDao =
        database.parserRuleDao()
}
