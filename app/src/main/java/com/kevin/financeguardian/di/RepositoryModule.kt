package com.kevin.financeguardian.di

import com.kevin.financeguardian.data.repository.RoomTransactionRepository
import com.kevin.financeguardian.data.repository.TransactionRepository
import com.kevin.financeguardian.data.transaction.TransactionCorrectionApplier
import com.kevin.financeguardian.data.transaction.TransactionCorrectionService
import dagger.Binds
import dagger.Module
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
}
