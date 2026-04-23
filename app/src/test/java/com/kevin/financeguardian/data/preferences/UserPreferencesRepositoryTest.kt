package com.kevin.financeguardian.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserPreferencesRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun defaultsUseSecureAndIncompleteState() = runTest {
        val repository = repository("defaults.preferences_pb")

        assertEquals(
            UserPreferences(
                appLockEnabled = true,
                screenPrivacyEnabled = false,
                debugParserModeEnabled = false,
                onboardingCompleted = false,
                notificationsEnabled = true,
                proactiveInsightsEnabled = true,
                showAmountsOnLockScreen = true,
            ),
            repository.preferences.first(),
        )
    }

    @Test
    fun setAppLockEnabledPersists() = runTest {
        val repository = repository("app-lock.preferences_pb")

        repository.setAppLockEnabled(false)

        assertEquals(false, repository.preferences.first().appLockEnabled)
    }

    @Test
    fun setScreenPrivacyEnabledPersists() = runTest {
        val repository = repository("screen-privacy.preferences_pb")

        repository.setScreenPrivacyEnabled(true)

        assertEquals(true, repository.preferences.first().screenPrivacyEnabled)
    }

    @Test
    fun setDebugParserModeEnabledPersists() = runTest {
        val repository = repository("debug-parser.preferences_pb")

        repository.setDebugParserModeEnabled(true)

        assertEquals(true, repository.preferences.first().debugParserModeEnabled)
    }

    @Test
    fun setOnboardingCompletedPersists() = runTest {
        val repository = repository("onboarding.preferences_pb")

        repository.setOnboardingCompleted(true)

        assertEquals(true, repository.preferences.first().onboardingCompleted)
    }

    @Test
    fun setNotificationsEnabledPersists() = runTest {
        val repository = repository("notifications.preferences_pb")

        repository.setNotificationsEnabled(false)

        assertEquals(false, repository.preferences.first().notificationsEnabled)
    }

    @Test
    fun setProactiveInsightsEnabledPersists() = runTest {
        val repository = repository("proactive-insights.preferences_pb")

        repository.setProactiveInsightsEnabled(false)

        assertEquals(false, repository.preferences.first().proactiveInsightsEnabled)
    }

    @Test
    fun setShowAmountsOnLockScreenPersists() = runTest {
        val repository = repository("lock-screen-amounts.preferences_pb")

        repository.setShowAmountsOnLockScreen(false)

        assertEquals(false, repository.preferences.first().showAmountsOnLockScreen)
    }

    private fun repository(fileName: String): UserPreferencesRepository {
        val file = File(temporaryFolder.root, fileName)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { file },
        )
        return UserPreferencesRepository(dataStore)
    }
}
