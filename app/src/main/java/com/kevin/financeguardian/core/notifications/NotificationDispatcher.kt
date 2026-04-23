package com.kevin.financeguardian.core.notifications

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

interface NotificationDispatcher {
    suspend fun dispatch(event: NotificationEvent)
}

@Singleton
class NoOpNotificationDispatcher @Inject constructor() : NotificationDispatcher {
    override suspend fun dispatch(event: NotificationEvent) = Unit
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationDispatcherModule {
    @Binds
    @Singleton
    abstract fun bindNotificationDispatcher(
        dispatcher: NoOpNotificationDispatcher,
    ): NotificationDispatcher
}
