package com.kevin.financeguardian.core.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.kevin.financeguardian.MainActivity
import com.kevin.financeguardian.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidSystemNotificationPublisherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        AndroidNotificationChannels.create(context)
        notificationManager.cancelAll()
    }

    @Test
    fun publish_buildsSystemNotificationWithSafePublicVersion() {
        val publisher = AndroidSystemNotificationPublisher(context)

        publisher.publish(
            notificationId = 41,
            notification = ComposedNotification(
                systemTitle = "Transaction needs review",
                lockScreenTitle = "Transaction needs review",
                lockScreenBody = "GHS 24.00 needs your review",
                unlockedBody = "GHS 24.00 could not be fully categorized",
                inAppMessage = "A transaction needs review",
                actionLabel = "Review",
                groupKey = "review-needed",
                channelId = AndroidNotificationChannels.REVIEW_NEEDED,
                privacy = NotificationPrivacy.AmountOnly,
            ),
        )

        val postedNotification = shadowOf(notificationManager).allNotifications.single()

        assertEquals(AndroidNotificationChannels.REVIEW_NEEDED, postedNotification.channelId)
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, postedNotification.visibility)
        assertEquals(
            "Transaction needs review",
            postedNotification.extras.getCharSequence(Notification.EXTRA_TITLE),
        )
        assertEquals(
            "GHS 24.00 could not be fully categorized",
            postedNotification.extras.getCharSequence(Notification.EXTRA_TEXT),
        )
        assertEquals("review-needed", postedNotification.group)
        assertEquals(R.drawable.ic_launcher_foreground, postedNotification.smallIcon.resId)
        assertTrue((postedNotification.flags and Notification.FLAG_AUTO_CANCEL) != 0)

        val publicVersion = postedNotification.publicVersion
        assertNotNull(publicVersion)
        assertEquals(
            "Transaction needs review",
            publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE),
        )
        assertEquals(
            "GHS 24.00 needs your review",
            publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT),
        )

        val pendingIntent = requireNotNull(postedNotification.contentIntent)
        val contentIntent = shadowOf(pendingIntent).savedIntent
        assertEquals(MainActivity::class.java.name, contentIntent.component?.className)
    }

    @Test
    fun publish_usesPublicVisibilityForFullyVisibleNotifications() {
        val publisher = AndroidSystemNotificationPublisher(context)

        publisher.publish(
            notificationId = 42,
            notification = ComposedNotification(
                systemTitle = "Spending is higher than usual today",
                lockScreenTitle = "Spending is higher than usual today",
                lockScreenBody = "Outgoing spending is higher than usual today.",
                unlockedBody = "Outgoing spending is higher than usual today.",
                inAppMessage = "Outgoing spending is higher than usual today.",
                actionLabel = "View",
                groupKey = "insights",
                channelId = AndroidNotificationChannels.INSIGHTS,
                privacy = NotificationPrivacy.Full,
            ),
        )

        val postedNotification = shadowOf(notificationManager).allNotifications.last()

        assertEquals(NotificationCompat.VISIBILITY_PUBLIC, postedNotification.visibility)
        assertEquals(AndroidNotificationChannels.INSIGHTS, postedNotification.channelId)
    }
}
