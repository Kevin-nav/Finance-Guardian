package com.kevin.financeguardian.data.preferences

import com.kevin.financeguardian.domain.model.OwnedInstrument

data class UserPreferences(
    val appLockEnabled: Boolean = false,
    val screenPrivacyEnabled: Boolean = false,
    val debugParserModeEnabled: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val proactiveInsightsEnabled: Boolean = true,
    val balancesVisible: Boolean = true,
    val showAmountsOnLockScreen: Boolean = true,
    val ownedWallets: List<OwnedInstrument> = emptyList(),
)
