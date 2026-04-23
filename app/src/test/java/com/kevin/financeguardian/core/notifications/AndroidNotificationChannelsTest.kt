package com.kevin.financeguardian.core.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
class AndroidNotificationChannelsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        ShadowNotificationManager.reset()
    }

    @Test
    fun createChannels_registersTransactionReviewSecurityAndInsightChannels() {
        AndroidNotificationChannels.create(context)

        val channels = notificationManager.notificationChannels.associateBy { it.id }

        assertEquals(
            setOf(
                AndroidNotificationChannels.TRANSACTIONS,
                AndroidNotificationChannels.REVIEW_NEEDED,
                AndroidNotificationChannels.SECURITY,
                AndroidNotificationChannels.INSIGHTS,
            ),
            channels.keys,
        )
        assertEquals(
            NotificationManager.IMPORTANCE_DEFAULT,
            channels.getValue(AndroidNotificationChannels.TRANSACTIONS).importance,
        )
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            channels.getValue(AndroidNotificationChannels.REVIEW_NEEDED).importance,
        )
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            channels.getValue(AndroidNotificationChannels.SECURITY).importance,
        )
        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            channels.getValue(AndroidNotificationChannels.INSIGHTS).importance,
        )
    }
}
