package com.kevin.financeguardian.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object AndroidNotificationChannels {
    const val TRANSACTIONS = "transactions"
    const val REVIEW_NEEDED = "review_needed"
    const val SECURITY = "security"
    const val INSIGHTS = "insights"

    fun create(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannels(
            listOf(
                channel(
                    id = TRANSACTIONS,
                    name = "Transactions",
                    description = "Alerts for newly recorded transactions.",
                    importance = NotificationManager.IMPORTANCE_DEFAULT,
                ),
                channel(
                    id = REVIEW_NEEDED,
                    name = "Review needed",
                    description = "Alerts that need quick transaction review.",
                    importance = NotificationManager.IMPORTANCE_HIGH,
                ),
                channel(
                    id = SECURITY,
                    name = "Security",
                    description = "Permission and security state alerts.",
                    importance = NotificationManager.IMPORTANCE_HIGH,
                ),
                channel(
                    id = INSIGHTS,
                    name = "Insights",
                    description = "Proactive spending insight alerts.",
                    importance = NotificationManager.IMPORTANCE_LOW,
                ),
            ),
        )
    }

    private fun channel(
        id: String,
        name: String,
        description: String,
        importance: Int,
    ): NotificationChannel =
        NotificationChannel(id, name, importance).apply {
            this.description = description
        }
}
