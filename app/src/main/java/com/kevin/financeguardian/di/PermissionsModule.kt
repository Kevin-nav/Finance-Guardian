package com.kevin.financeguardian.di

import com.kevin.financeguardian.core.permissions.AndroidPermissionStatusChecker
import com.kevin.financeguardian.core.permissions.PermissionStatusChecker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionsModule {
    @Binds
    @Singleton
    abstract fun bindPermissionStatusChecker(
        checker: AndroidPermissionStatusChecker,
    ): PermissionStatusChecker
}
