package com.kevin.financeguardian.core.notifications

interface SystemNotificationPublisher {
    fun publish(
        notificationId: Int,
        notification: ComposedNotification,
    )
}
