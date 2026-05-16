package com.kevin.financeguardian.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.financeguardian.core.notifications.InAppNoticeManager
import com.kevin.financeguardian.core.notifications.InAppNoticeSeverity
import com.kevin.financeguardian.core.notifications.NotificationDispatcher
import com.kevin.financeguardian.core.notifications.NotificationEvent
import com.kevin.financeguardian.core.permissions.AppPermissionStatuses
import com.kevin.financeguardian.core.permissions.PermissionStatusChecker
import com.kevin.financeguardian.core.time.AppClock
import com.kevin.financeguardian.data.fixture.SmsFixtureImportResult
import com.kevin.financeguardian.data.fixture.SmsFixtureImporter
import com.kevin.financeguardian.data.local.AppDataResetter
import com.kevin.financeguardian.data.preferences.AppThemeMode
import com.kevin.financeguardian.data.preferences.UserPreferencesRepository
import com.kevin.financeguardian.data.sms.SmsIngestionResult
import com.kevin.financeguardian.domain.model.InstrumentProvider
import com.kevin.financeguardian.domain.model.InstrumentType
import com.kevin.financeguardian.domain.model.OwnedInstrument
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val permissionStatusChecker: PermissionStatusChecker,
    private val appDataResetter: AppDataResetter,
    private val smsFixtureImporter: SmsFixtureImporter,
    private val notificationDispatcher: NotificationDispatcher,
    private val noticeManager: InAppNoticeManager,
    private val clock: AppClock,
) : ViewModel() {
    private val permissionStatuses = MutableStateFlow(permissionStatusChecker.currentStatuses())
    private val resetInProgress = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferencesRepository.preferences,
        permissionStatuses,
        resetInProgress,
    ) { preferences, permissions, isResetting ->
            SettingsUiState(
                appLockEnabled = preferences.appLockEnabled,
                screenPrivacyEnabled = preferences.screenPrivacyEnabled,
                debugParserModeEnabled = preferences.debugParserModeEnabled,
                notificationsEnabled = preferences.notificationsEnabled,
                proactiveInsightsEnabled = preferences.proactiveInsightsEnabled,
                themeMode = preferences.themeMode,
                balancesVisible = preferences.balancesVisible,
                showAmountsOnLockScreen = preferences.showAmountsOnLockScreen,
                ownedWallets = preferences.ownedWallets,
                permissions = permissions,
                isResetInProgress = isResetting,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsUiState(),
        )

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAppLockEnabled(enabled)
            if (!enabled) {
                notificationDispatcher.dispatch(
                    NotificationEvent.SecurityStateChanged(
                        state = NotificationEvent.SecurityState.AppLock,
                        enabled = false,
                        occurredAt = clock.now(),
                    ),
                )
            }
        }
    }

    fun setScreenPrivacyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setScreenPrivacyEnabled(enabled)
            if (!enabled) {
                notificationDispatcher.dispatch(
                    NotificationEvent.SecurityStateChanged(
                        state = NotificationEvent.SecurityState.ScreenPrivacy,
                        enabled = false,
                        occurredAt = clock.now(),
                    ),
                )
            }
        }
    }

    fun setDebugParserModeEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setDebugParserModeEnabled(enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setNotificationsEnabled(enabled) }
    }

    fun setProactiveInsightsEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setProactiveInsightsEnabled(enabled) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { userPreferencesRepository.setThemeMode(mode) }
    }

    fun setBalancesVisible(visible: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setBalancesVisible(visible) }
    }

    fun setShowAmountsOnLockScreen(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setShowAmountsOnLockScreen(enabled) }
    }

    fun addOrUpdateOwnedWallet(
        id: String,
        label: String,
        provider: InstrumentProvider,
        phoneNumber: String,
    ) {
        viewModelScope.launch {
            userPreferencesRepository.addOrUpdateOwnedWallet(
                OwnedInstrument(
                    id = id.ifBlank { phoneNumber },
                    label = label.ifBlank { provider.name },
                    type = InstrumentType.WALLET,
                    provider = provider,
                    identifier = phoneNumber,
                    displayIdentifier = phoneNumber,
                ),
            )
        }
    }

    fun removeOwnedWallet(id: String) {
        viewModelScope.launch { userPreferencesRepository.removeOwnedWallet(id) }
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            val previous = permissionStatuses.value
            val current = permissionStatusChecker.currentStatuses()
            permissionStatuses.value = current
            emitPermissionChangeEvents(
                previous = previous,
                current = current,
            )
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            resetInProgress.value = true
            runCatching { appDataResetter.resetAllData() }
                .onSuccess {
                    noticeManager.showMessage(
                        message = "App data reset. Default categories restored.",
                        severity = InAppNoticeSeverity.Success,
                    )
                }
                .onFailure {
                    noticeManager.showMessage(
                        message = it.message ?: "Data reset failed.",
                        severity = InAppNoticeSeverity.Error,
                    )
                }
            resetInProgress.value = false
        }
    }

    fun importFixtureJson(json: String) {
        viewModelScope.launch {
            runCatching { smsFixtureImporter.importJson(json) }
                .onSuccess { results ->
                    noticeManager.showMessage(
                        message = results.toImportSummary(),
                        severity = InAppNoticeSeverity.Success,
                    )
                }
                .onFailure {
                    noticeManager.showMessage(
                        message = it.message ?: "Fixture import failed.",
                        severity = InAppNoticeSeverity.Error,
                    )
                }
        }
    }

    private suspend fun emitPermissionChangeEvents(
        previous: AppPermissionStatuses,
        current: AppPermissionStatuses,
    ) {
        if (!previous.receiveSmsGranted && current.receiveSmsGranted) {
            notificationDispatcher.dispatch(
                NotificationEvent.PermissionGranted(
                    permission = NotificationEvent.Permission.Sms,
                    occurredAt = clock.now(),
                ),
            )
        } else if (previous.receiveSmsGranted && !current.receiveSmsGranted) {
            notificationDispatcher.dispatch(
                NotificationEvent.PermissionRevoked(
                    permission = NotificationEvent.Permission.Sms,
                    occurredAt = clock.now(),
                ),
            )
        }

        if (!previous.postNotificationsGranted && current.postNotificationsGranted) {
            notificationDispatcher.dispatch(
                NotificationEvent.PermissionGranted(
                    permission = NotificationEvent.Permission.Notifications,
                    occurredAt = clock.now(),
                ),
            )
        } else if (previous.postNotificationsGranted && !current.postNotificationsGranted) {
            notificationDispatcher.dispatch(
                NotificationEvent.PermissionRevoked(
                    permission = NotificationEvent.Permission.Notifications,
                    occurredAt = clock.now(),
                ),
            )
        }
    }

    private fun List<SmsFixtureImportResult>.toImportSummary(): String {
        val parsed = count { it.ingestionResult is SmsIngestionResult.Parsed }
        val ignored = count { it.ingestionResult is SmsIngestionResult.Ignored }
        val failed = count { it.ingestionResult is SmsIngestionResult.Failed }
        val duplicates = count { it.ingestionResult is SmsIngestionResult.Duplicate }
        return "Imported $parsed parsed, $ignored ignored, $failed failed, $duplicates duplicate fixture(s)."
    }
}

data class SettingsUiState(
    val appLockEnabled: Boolean = false,
    val screenPrivacyEnabled: Boolean = false,
    val debugParserModeEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val proactiveInsightsEnabled: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val balancesVisible: Boolean = true,
    val showAmountsOnLockScreen: Boolean = true,
    val ownedWallets: List<OwnedInstrument> = emptyList(),
    val permissions: AppPermissionStatuses = AppPermissionStatuses(
        receiveSmsGranted = false,
        postNotificationsGranted = false,
    ),
    val isResetInProgress: Boolean = false,
)
