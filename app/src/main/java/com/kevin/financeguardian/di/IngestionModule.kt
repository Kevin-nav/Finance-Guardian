package com.kevin.financeguardian.di

import com.kevin.financeguardian.core.id.IdGenerator
import com.kevin.financeguardian.core.id.UuidIdGenerator
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.core.time.SystemAppClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class IngestionModule {
    @Binds
    @Singleton
    abstract fun bindIdGenerator(generator: UuidIdGenerator): IdGenerator

    @Binds
    @Singleton
    abstract fun bindAppClock(clock: SystemAppClock): AppClock
}
