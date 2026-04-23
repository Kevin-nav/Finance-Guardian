package com.kevin.financeguardian.core.notifications

data class ComposedNotification(
    val systemTitle: String,
    val lockScreenTitle: String,
    val lockScreenBody: String,
    val unlockedBody: String,
    val inAppMessage: String,
    val actionLabel: String,
    val groupKey: String,
    val channelId: String,
    val privacy: NotificationPrivacy,
)
