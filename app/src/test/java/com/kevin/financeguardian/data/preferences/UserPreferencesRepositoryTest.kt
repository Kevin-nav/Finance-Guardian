package com.kevin.financeguardian.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kevin.financeguardian.domain.model.InstrumentProvider
import com.kevin.financeguardian.domain.model.InstrumentType
import com.kevin.financeguardian.domain.model.OwnedInstrument
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
                appLockEnabled = false,
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

    @Test
    fun savesOnlyOneNormalizedMtnWallet() = runTest {
        val repository = repository("wallet-mtn.preferences_pb")

        repository.setOwnedWallets(
            listOf(OwnedInstrument("mtn", "My MTN", InstrumentType.WALLET, InstrumentProvider.MTN, "0549037907")),
        )

        val wallets = repository.preferences.first().ownedWallets
        assertEquals(1, wallets.size)
        assertEquals("233549037907", wallets.single().identifier)
    }

    @Test
    fun savesOnlyTelecelWithoutMtnOrGcb() = runTest {
        val repository = repository("wallet-telecel.preferences_pb")

        repository.addOrUpdateOwnedWallet(
            OwnedInstrument("telecel", "Telecel", InstrumentType.WALLET, InstrumentProvider.TELECEL, "+233 50 560 0861"),
        )

        val wallets = repository.preferences.first().ownedWallets
        assertEquals(listOf(InstrumentProvider.TELECEL), wallets.map { it.provider })
    }

    @Test
    fun updatesWalletLabelAndRemovesWallet() = runTest {
        val repository = repository("wallet-update.preferences_pb")

        repository.addOrUpdateOwnedWallet(
            OwnedInstrument("mtn", "Old", InstrumentType.WALLET, InstrumentProvider.MTN, "0549037907"),
        )
        repository.addOrUpdateOwnedWallet(
            OwnedInstrument("mtn", "New", InstrumentType.WALLET, InstrumentProvider.MTN, "+233549037907"),
        )
        assertEquals("New", repository.preferences.first().ownedWallets.single().label)

        repository.removeOwnedWallet("mtn")
        assertEquals(emptyList<OwnedInstrument>(), repository.preferences.first().ownedWallets)
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
