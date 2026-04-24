package com.kevin.financeguardian.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kevin.financeguardian.core.permissions.AppPermissionStatuses
import com.kevin.financeguardian.core.permissions.FinanceGuardianPermission
import com.kevin.financeguardian.core.permissions.PermissionStatusChecker
import com.kevin.financeguardian.core.security.AppLockState
import com.kevin.financeguardian.data.preferences.UserPreferencesRepository
import com.kevin.financeguardian.testing.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AppShellViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun defaultsShowOnboardingAndKeepLockStateDisabled() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertFalse(state.onboardingCompleted)
        assertTrue(state.shouldShowOnboarding)
        assertFalse(state.shouldShowLock)
        assertEquals(AppLockState.Disabled, state.appLockState)
    }

    @Test
    fun completingOnboardingHidesOnboarding() = runTest {
        val viewModel = viewModel()

        viewModel.completeOnboarding()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.onboardingCompleted)
        assertFalse(viewModel.uiState.value.shouldShowOnboarding)
    }

    @Test
    fun smsPermissionResultRefreshesPermissionsWithoutCompletingOnboarding() = runTest {
        val checker = FakePermissionStatusChecker()
        val viewModel = viewModel(checker = checker)

        checker.statuses = AppPermissionStatuses(
            receiveSmsGranted = true,
            postNotificationsGranted = false,
        )
        viewModel.onSmsPermissionResult()
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertFalse(state.onboardingCompleted)
        assertTrue(state.shouldShowOnboarding)
        assertTrue(state.permissions.receiveSmsGranted)
    }

    @Test
    fun appLockDisabledProducesDisabledState() = runTest {
        val repository = repository("app-lock-disabled.preferences_pb")
        repository.setAppLockEnabled(false)
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertFalse(state.appLockEnabled)
        assertEquals(AppLockState.Disabled, state.appLockState)
        assertFalse(state.shouldShowLock)
    }

    @Test
    fun unlockChangesLockStateToUnlocked() = runTest {
        val repository = repository("unlock.preferences_pb")
        repository.setOnboardingCompleted(true)
        repository.setAppLockEnabled(true)
        val viewModel = viewModel(repository = repository)

        viewModel.beginAuthentication()
        viewModel.unlock()
        advanceUntilIdle()

        assertEquals(AppLockState.Unlocked, viewModel.uiState.value.appLockState)
        assertFalse(viewModel.uiState.value.shouldShowLock)
    }

    @Test
    fun lockAfterOnboardingReturnsToLockedWhenAppLockEnabled() = runTest {
        val repository = repository("lock.preferences_pb")
        repository.setOnboardingCompleted(true)
        repository.setAppLockEnabled(true)
        val viewModel = viewModel(repository = repository)

        viewModel.beginAuthentication()
        viewModel.unlock()
        advanceUntilIdle()
        viewModel.lock()
        advanceUntilIdle()

        assertEquals(AppLockState.Locked, viewModel.uiState.value.appLockState)
        assertTrue(viewModel.uiState.value.shouldShowLock)
    }

    @Test
    fun lockWhenAppLockDisabledStillReportsDisabled() = runTest {
        val repository = repository("lock-disabled.preferences_pb")
        repository.setOnboardingCompleted(true)
        repository.setAppLockEnabled(false)
        val viewModel = viewModel(repository = repository)

        viewModel.unlock()
        advanceUntilIdle()
        viewModel.lock()
        advanceUntilIdle()

        assertEquals(AppLockState.Disabled, viewModel.uiState.value.appLockState)
        assertFalse(viewModel.uiState.value.shouldShowLock)
    }

    @Test
    fun beginAuthenticationMovesIntoAuthenticatingState() = runTest {
        val repository = repository("authenticating.preferences_pb")
        repository.setOnboardingCompleted(true)
        repository.setAppLockEnabled(true)
        val viewModel = viewModel(repository = repository)

        viewModel.beginAuthentication()
        advanceUntilIdle()

        assertEquals(AppLockState.Authenticating, viewModel.uiState.value.appLockState)
        assertTrue(viewModel.uiState.value.shouldShowLock)
    }

    @Test
    fun lockDoesNotOverrideAuthenticatingState() = runTest {
        val repository = repository("auth-stop.preferences_pb")
        repository.setOnboardingCompleted(true)
        repository.setAppLockEnabled(true)
        val viewModel = viewModel(repository = repository)

        viewModel.beginAuthentication()
        advanceUntilIdle()
        viewModel.lock()
        advanceUntilIdle()

        assertEquals(AppLockState.Authenticating, viewModel.uiState.value.appLockState)
        assertTrue(viewModel.uiState.value.shouldShowLock)
    }

    @Test
    fun dismissingAuthenticationReturnsToLockedState() = runTest {
        val repository = repository("auth-dismiss.preferences_pb")
        repository.setOnboardingCompleted(true)
        repository.setAppLockEnabled(true)
        val viewModel = viewModel(repository = repository)

        viewModel.beginAuthentication()
        advanceUntilIdle()
        viewModel.onAuthenticationDismissed()
        advanceUntilIdle()

        assertEquals(AppLockState.Locked, viewModel.uiState.value.appLockState)
        assertTrue(viewModel.uiState.value.shouldShowLock)
    }

    @Test
    fun disablingAppLockMarksStateDisabledAndHidesLock() = runTest {
        val repository = repository("disable-app-lock.preferences_pb")
        repository.setOnboardingCompleted(true)
        repository.setAppLockEnabled(true)
        val viewModel = viewModel(repository = repository)

        viewModel.disableAppLock()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.appLockEnabled)
        assertEquals(AppLockState.Disabled, viewModel.uiState.value.appLockState)
        assertFalse(viewModel.uiState.value.shouldShowLock)
    }

    private fun viewModel(
        repository: UserPreferencesRepository = repository("preferences-${System.nanoTime()}.preferences_pb"),
        checker: FakePermissionStatusChecker = FakePermissionStatusChecker(),
    ): AppShellViewModel =
        AppShellViewModel(
            userPreferencesRepository = repository,
            permissionStatusChecker = checker,
        )

    private fun repository(fileName: String): UserPreferencesRepository {
        val file = File(temporaryFolder.root, fileName)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + mainDispatcherRule.dispatcher),
            produceFile = { file },
        )
        return UserPreferencesRepository(dataStore)
    }

    private class FakePermissionStatusChecker : PermissionStatusChecker {
        var statuses = AppPermissionStatuses(
            receiveSmsGranted = false,
            postNotificationsGranted = false,
        )

        override fun isGranted(permission: FinanceGuardianPermission): Boolean =
            when (permission) {
                FinanceGuardianPermission.ReceiveSms -> statuses.receiveSmsGranted
                FinanceGuardianPermission.PostNotifications -> statuses.postNotificationsGranted
            }

        override fun currentStatuses(): AppPermissionStatuses = statuses
    }
}
