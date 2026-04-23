package com.kevin.financeguardian.core.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kevin.financeguardian.MainActivity
import com.kevin.financeguardian.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSystemNotificationPublisher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SystemNotificationPublisher {
    override fun publish(
        notificationId: Int,
        notification: ComposedNotification,
    ) {
        NotificationManagerCompat.from(context).notify(
            notificationId,
            buildNotification(
                notificationId = notificationId,
                notification = notification,
            ),
        )
    }

    private fun buildNotification(
        notificationId: Int,
        notification: ComposedNotification,
    ): Notification {
        val contentIntent = buildContentIntent(notificationId)
        return NotificationCompat.Builder(context, notification.channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notification.systemTitle)
            .setContentText(notification.unlockedBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.unlockedBody))
            .setVisibility(notification.privacy.toAndroidVisibility())
            .setPublicVersion(buildPublicVersion(notification, contentIntent))
            .setGroup(notification.groupKey)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun buildPublicVersion(
        notification: ComposedNotification,
        contentIntent: PendingIntent,
    ): Notification =
        NotificationCompat.Builder(context, notification.channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notification.lockScreenTitle)
            .setContentText(notification.lockScreenBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.lockScreenBody))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup(notification.groupKey)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

    private fun buildContentIntent(notificationId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun NotificationPrivacy.toAndroidVisibility(): Int =
        when (this) {
            NotificationPrivacy.Full -> NotificationCompat.VISIBILITY_PUBLIC
            NotificationPrivacy.Private,
            NotificationPrivacy.AmountOnly,
            -> NotificationCompat.VISIBILITY_PRIVATE
        }
}
