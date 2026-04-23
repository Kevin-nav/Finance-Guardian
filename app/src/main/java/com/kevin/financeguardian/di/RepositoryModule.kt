package com.kevin.financeguardian.di

import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.data.learning.LearningSignalRecorder
import com.kevin.financeguardian.data.local.AppDataResetter
import com.kevin.financeguardian.data.local.dao.LearningSignalDao
import com.kevin.financeguardian.data.local.RoomAppDataResetter
import com.kevin.financeguardian.data.fixture.SmsFixtureImportService
import com.kevin.financeguardian.data.fixture.SmsFixtureImporter
import com.kevin.financeguardian.data.repository.RoomTransactionRepository
import com.kevin.financeguardian.data.repository.TransactionRepository
import com.kevin.financeguardian.data.transaction.TransactionCorrectionApplier
import com.kevin.financeguardian.data.transaction.TransactionCorrectionService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindTransactionRepository(
        repository: RoomTransactionRepository,
    ): TransactionRepository

    @Binds
    abstract fun bindTransactionCorrectionApplier(
        service: TransactionCorrectionService,
    ): TransactionCorrectionApplier

    @Binds
    @Singleton
    abstract fun bindAppDataResetter(
        resetter: RoomAppDataResetter,
    ): AppDataResetter

    @Binds
    abstract fun bindSmsFixtureImporter(
        service: SmsFixtureImportService,
    ): SmsFixtureImporter

    companion object {
        @Provides
        @Singleton
        fun provideLearningSignalRecorder(
            learningSignalDao: LearningSignalDao,
            idGenerator: IdGenerator,
        ): LearningSignalRecorder = LearningSignalRecorder(learningSignalDao, idGenerator)
    }
}
