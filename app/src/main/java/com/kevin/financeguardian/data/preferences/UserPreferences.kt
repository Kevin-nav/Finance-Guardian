package com.kevin.financeguardian.data.preferences

data class UserPreferences(
    val appLockEnabled: Boolean = true,
    val debugParserModeEnabled: Boolean = false,
    val onboardingCompleted: Boolean = false,
)
