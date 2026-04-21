package com.kevin.financeguardian.di

import com.kevin.financeguardian.domain.parser.FinanceGuardianSmsParser
import com.kevin.financeguardian.domain.parser.SmsTransactionParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ParserModule {
    @Provides
    @Singleton
    fun provideSmsTransactionParser(): SmsTransactionParser = FinanceGuardianSmsParser()
}
