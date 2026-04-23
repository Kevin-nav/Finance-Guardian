package com.kevin.financeguardian.core.notifications

data class NotificationDecision(
    val family: NotificationFamily,
    val priority: NotificationPriority,
    val surface: NotificationSurface,
    val privacy: NotificationPrivacy,
    val groupKey: String,
)
