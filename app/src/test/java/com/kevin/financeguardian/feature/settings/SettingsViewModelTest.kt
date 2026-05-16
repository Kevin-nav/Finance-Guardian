package com.kevin.financeguardian.feature.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kevin.financeguardian.core.notifications.InAppNotice
import com.kevin.financeguardian.core.notifications.InAppNoticeManager
import com.kevin.financeguardian.core.notifications.NotificationDispatcher
import com.kevin.financeguardian.core.notifications.NotificationEvent
import com.kevin.financeguardian.core.permissions.AppPermissionStatuses
import com.kevin.financeguardian.core.permissions.FinanceGuardianPermission
import com.kevin.financeguardian.core.permissions.PermissionStatusChecker
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.fixture.SmsFixture
import com.kevin.financeguardian.data.fixture.SmsFixtureImportResult
import com.kevin.financeguardian.data.fixture.SmsFixtureImporter
import com.kevin.financeguardian.data.local.AppDataResetter
import com.kevin.financeguardian.data.preferences.AppThemeMode
import com.kevin.financeguardian.data.preferences.UserPreferencesRepository
import com.kevin.financeguardian.data.sms.SmsIngestionResult
import com.kevin.financeguardian.domain.model.Provider
import com.kevin.financeguardian.testing.MainDispatcherRule
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
        viewModel.setNotificationsEnabled(false)
        viewModel.setProactiveInsightsEnabled(false)
        viewModel.setThemeMode(AppThemeMode.DARK)
        viewModel.setShowAmountsOnLockScreen(false)
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertFalse(state.appLockEnabled)
        assertTrue(state.screenPrivacyEnabled)
        assertTrue(state.debugParserModeEnabled)
        assertFalse(state.notificationsEnabled)
        assertFalse(state.proactiveInsightsEnabled)
        assertEquals(AppThemeMode.DARK, state.themeMode)
        assertFalse(state.showAmountsOnLockScreen)
    }

    @Test
    fun refreshPermissionsDispatchesPermissionGrantedEvents() = runTest {
        val checker = FakePermissionStatusChecker()
        val dispatcher = RecordingNotificationDispatcher()
        val viewModel = viewModel(
            checker = checker,
            dispatcher = dispatcher,
        )
        advanceUntilIdle()

        checker.statuses = AppPermissionStatuses(
            receiveSmsGranted = true,
            postNotificationsGranted = true,
        )
        viewModel.refreshPermissions()
        advanceUntilIdle()

        assertTrue(
            dispatcher.events.contains(
                NotificationEvent.PermissionGranted(
                    permission = NotificationEvent.Permission.Sms,
                    occurredAt = now,
                ),
            ),
        )
        assertTrue(
            dispatcher.events.contains(
                NotificationEvent.PermissionGranted(
                    permission = NotificationEvent.Permission.Notifications,
                    occurredAt = now,
                ),
            ),
        )
    }

    @Test
    fun resetAllDataCallsBackendAndReportsSuccess() = runTest {
        val resetter = FakeAppDataResetter()
        val noticeManager = InAppNoticeManager()
        val viewModel = viewModel(
            resetter = resetter,
            noticeManager = noticeManager,
        )

        viewModel.resetAllData()
        advanceUntilIdle()

        assertTrue(resetter.resetCalled)
        assertTrue(noticeManager.notice.value?.message?.contains("reset") == true)
    }

    @Test
    fun importFixtureJsonCallsBackendAndReportsCounts() = runTest {
        val importer = FakeSmsFixtureImporter(
            results = listOf(
                SmsFixtureImportResult(
                    fixture = SmsFixture(
                        provider = Provider.MTN_MOMO,
                        sender = "MobileMoney",
                        body = "Payment received for GHS 1.00",
                        receivedAt = Instant.parse("2026-04-22T12:00:00Z"),
                    ),
                    ingestionResult = SmsIngestionResult.Parsed(
                        smsRecordId = "sms-1",
                        transactionId = "transaction-1",
                    ),
                ),
            ),
        )
        val noticeManager = InAppNoticeManager()
        val viewModel = viewModel(
            importer = importer,
            noticeManager = noticeManager,
        )

        viewModel.importFixtureJson("{}")
        advanceUntilIdle()

        assertTrue(importer.lastJson == "{}")
        assertTrue(noticeManager.notice.value?.message?.contains("1 parsed") == true)
    }

    private fun viewModel(
        repository: UserPreferencesRepository = repository("settings-${System.nanoTime()}.preferences_pb"),
        checker: FakePermissionStatusChecker = FakePermissionStatusChecker(),
        resetter: FakeAppDataResetter = FakeAppDataResetter(),
        importer: FakeSmsFixtureImporter = FakeSmsFixtureImporter(),
        dispatcher: NotificationDispatcher = RecordingNotificationDispatcher(),
        noticeManager: InAppNoticeManager = InAppNoticeManager(),
    ): SettingsViewModel =
        SettingsViewModel(
            userPreferencesRepository = repository,
            permissionStatusChecker = checker,
            appDataResetter = resetter,
            smsFixtureImporter = importer,
            notificationDispatcher = dispatcher,
            noticeManager = noticeManager,
            clock = FixedClock(now),
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

    private class FakeAppDataResetter : AppDataResetter {
        var resetCalled = false

        override suspend fun resetAllData() {
            resetCalled = true
        }
    }

    private class FakeSmsFixtureImporter(
        private val results: List<SmsFixtureImportResult> = emptyList(),
    ) : SmsFixtureImporter {
        var lastJson: String? = null

        override suspend fun importJson(json: String): List<SmsFixtureImportResult> {
            lastJson = json
            return results
        }
    }

    private class RecordingNotificationDispatcher : NotificationDispatcher {
        val events = mutableListOf<NotificationEvent>()

        override suspend fun dispatch(event: NotificationEvent) {
            events += event
        }
    }

    private class FixedClock(private val instant: Instant) : AppClock {
        override fun now(): Instant = instant
    }

    private companion object {
        val now: Instant = Instant.parse("2026-04-23T12:00:00Z")
    }
}
