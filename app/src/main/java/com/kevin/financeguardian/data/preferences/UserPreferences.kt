package com.kevin.financeguardian.data.preferences

data class UserPreferences(
    val appLockEnabled: Boolean = true,
    val screenPrivacyEnabled: Boolean = false,
    val debugParserModeEnabled: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val proactiveInsightsEnabled: Boolean = true,
    val showAmountsOnLockScreen: Boolean = true,
)
