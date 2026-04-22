package com.kevin.financeguardian.feature.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kevin.financeguardian.core.permissions.AppPermissionStatuses
import com.kevin.financeguardian.core.permissions.FinanceGuardianPermission
import com.kevin.financeguardian.core.permissions.PermissionStatusChecker
import com.kevin.financeguardian.data.preferences.UserPreferencesRepository
import com.kevin.financeguardian.testing.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun refreshPermissionsUpdatesSmsStatus() = runTest {
        val checker = FakePermissionStatusChecker()
        val viewModel = viewModel(checker = checker)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.permissions.receiveSmsGranted)

        checker.statuses = AppPermissionStatuses(
            receiveSmsGranted = true,
            postNotificationsGranted = false,
        )
        viewModel.refreshPermissions()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.permissions.receiveSmsGranted)
    }

    @Test
    fun refreshPermissionsUpdatesNotificationStatus() = runTest {
        val checker = FakePermissionStatusChecker()
        val viewModel = viewModel(checker = checker)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.permissions.postNotificationsGranted)

        checker.statuses = AppPermissionStatuses(
            receiveSmsGranted = false,
            postNotificationsGranted = true,
        )
        viewModel.refreshPermissions()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.permissions.postNotificationsGranted)
    }

    @Test
    fun preferenceTogglesPersistToUiState() = runTest {
        val viewModel = viewModel()

        viewModel.setAppLockEnabled(false)
        viewModel.setScreenPrivacyEnabled(true)
        viewModel.setDebugParserModeEnabled(true)
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertFalse(state.appLockEnabled)
        assertTrue(state.screenPrivacyEnabled)
        assertTrue(state.debugParserModeEnabled)
    }

    private fun viewModel(
        repository: UserPreferencesRepository = repository("settings-${System.nanoTime()}.preferences_pb"),
        checker: FakePermissionStatusChecker = FakePermissionStatusChecker(),
    ): SettingsViewModel =
        SettingsViewModel(
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
