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
            debugParserModeEnabled = values[DEBUG_PARSER_MODE_ENABLED] ?: false,
            onboardingCompleted = values[ONBOARDING_COMPLETED] ?: false,
        )
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setDebugParserModeEnabled(enabled: Boolean) {
        dataStore.edit { values -> values[DEBUG_PARSER_MODE_ENABLED] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { values -> values[ONBOARDING_COMPLETED] = completed }
    }

    private companion object {
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val DEBUG_PARSER_MODE_ENABLED = booleanPreferencesKey("debug_parser_mode_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}
