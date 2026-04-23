package com.kevin.financeguardian.di

import com.kevin.financeguardian.core.notifications.AndroidSystemNotificationPublisher
import com.kevin.financeguardian.core.notifications.SystemNotificationPublisher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    @Binds
    @Singleton
    abstract fun bindSystemNotificationPublisher(
        publisher: AndroidSystemNotificationPublisher,
    ): SystemNotificationPublisher
}
