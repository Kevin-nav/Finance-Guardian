package com.kevin.financeguardian.core.notifications

import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.preferences.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

interface NotificationDispatcher {
    suspend fun dispatch(event: NotificationEvent)
}

@Singleton
class DefaultNotificationDispatcher @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val policyEngine: NotificationPolicyEngine,
    private val composer: NotificationComposer,
    private val systemNotificationPublisher: SystemNotificationPublisher,
    private val inAppNoticeManager: InAppNoticeManager,
    private val clock: AppClock,
) : NotificationDispatcher {
    override suspend fun dispatch(event: NotificationEvent) {
        val preferences = userPreferencesRepository.preferences.first()
        val decision = policyEngine.decide(
            event = event,
            context = NotificationPolicyContext(
                notificationsEnabled = preferences.notificationsEnabled,
                proactiveInsightsEnabled = preferences.proactiveInsightsEnabled,
                now = clock.now(),
            ),
        )
        if (decision.surface == NotificationSurface.Silent) return

        val composed = composer.compose(
            event = event,
            decision = decision,
            showAmountsOnLockScreen = preferences.showAmountsOnLockScreen,
        )

        when (decision.surface) {
            NotificationSurface.Silent -> Unit
            NotificationSurface.InApp -> showInAppNotice(event, composed)
            NotificationSurface.System -> publishSystemNotification(event, composed)
            NotificationSurface.SystemAndInApp -> {
                publishSystemNotification(event, composed)
                showInAppNotice(event, composed)
            }
        }
    }

    private fun publishSystemNotification(
        event: NotificationEvent,
        notification: ComposedNotification,
    ) {
        systemNotificationPublisher.publish(
            notificationId = event.notificationId(),
            notification = notification,
        )
    }

    private fun showInAppNotice(
        event: NotificationEvent,
        notification: ComposedNotification,
    ) {
        inAppNoticeManager.show(
            InAppNotice(
                id = "${event.family.name}:${event.notificationId()}",
                message = notification.inAppMessage,
                actionLabel = notification.actionLabel,
                severity = event.toSeverity(),
                duration = event.toDuration(),
            ),
        )
    }

    private fun NotificationEvent.notificationId(): Int =
        when (this) {
            is NotificationEvent.TransactionDetected -> transactionId.hashCode()
            is NotificationEvent.TransactionNeedsReview -> ("review:$transactionId").hashCode()
            is NotificationEvent.PermissionRevoked -> ("revoked:${permission.name}").hashCode()
            is NotificationEvent.SecurityStateChanged -> ("security:${state.name}:$enabled").hashCode()
            is NotificationEvent.InsightTriggered -> ("insight:${insight.name}:$summary").hashCode()
            is NotificationEvent.CorrectionSaved -> ("correction:$transactionId").hashCode()
            is NotificationEvent.PermissionGranted -> ("granted:${permission.name}").hashCode()
        }

    private fun NotificationEvent.toSeverity(): InAppNoticeSeverity =
        when (this) {
            is NotificationEvent.CorrectionSaved,
            is NotificationEvent.PermissionGranted,
            -> InAppNoticeSeverity.Success

            is NotificationEvent.PermissionRevoked,
            is NotificationEvent.SecurityStateChanged,
            is NotificationEvent.TransactionNeedsReview,
            -> InAppNoticeSeverity.Warning

            is NotificationEvent.TransactionDetected,
            is NotificationEvent.InsightTriggered,
            -> InAppNoticeSeverity.Info
        }

    private fun NotificationEvent.toDuration(): InAppNoticeDuration =
        when (priority) {
            NotificationPriority.High -> InAppNoticeDuration.Long
            NotificationPriority.Default -> InAppNoticeDuration.Short
            NotificationPriority.Low -> InAppNoticeDuration.Short
        }
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
        dispatcher: DefaultNotificationDispatcher,
    ): NotificationDispatcher
}
