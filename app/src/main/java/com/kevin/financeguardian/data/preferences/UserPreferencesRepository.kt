package com.kevin.financeguardian.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<UserPreferences> = dataStore.data.map { values ->
        UserPreferences(
            appLockEnabled = values[APP_LOCK_ENABLED] ?: true,
            screenPrivacyEnabled = values[SCREEN_PRIVACY_ENABLED] ?: false,
            debugParserModeEnabled = values[DEBUG_PARSER_MODE_ENABLED] ?: false,
            onboardingCompleted = values[ONBOARDING_COMPLETED] ?: false,
            notificationsEnabled = values[NOTIFICATIONS_ENABLED] ?: true,
            proactiveInsightsEnabled = values[PROACTIVE_INSIGHTS_ENABLED] ?: true,
            showAmountsOnLockScreen = values[SHOW_AMOUNTS_ON_LOCK_SCREEN] ?: true,
        )
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setScreenPrivacyEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[SCREEN_PRIVACY_ENABLED] = enabled }
    }

    suspend fun setDebugParserModeEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[DEBUG_PARSER_MODE_ENABLED] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { values -> values[ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setProactiveInsightsEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[PROACTIVE_INSIGHTS_ENABLED] = enabled }
    }

    suspend fun setShowAmountsOnLockScreen(enabled: Boolean) {
        dataStore.edit { values -> values[SHOW_AMOUNTS_ON_LOCK_SCREEN] = enabled }
    }

    private companion object {
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val SCREEN_PRIVACY_ENABLED = booleanPreferencesKey("screen_privacy_enabled")
        val DEBUG_PARSER_MODE_ENABLED = booleanPreferencesKey("debug_parser_mode_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val PROACTIVE_INSIGHTS_ENABLED = booleanPreferencesKey("proactive_insights_enabled")
        val SHOW_AMOUNTS_ON_LOCK_SCREEN = booleanPreferencesKey("show_amounts_on_lock_screen")
    }
}
