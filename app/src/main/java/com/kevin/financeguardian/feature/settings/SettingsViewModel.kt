package com.kevin.financeguardian.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.financeguardian.core.permissions.AppPermissionStatuses
import com.kevin.financeguardian.core.permissions.PermissionStatusChecker
import com.kevin.financeguardian.data.fixture.SmsFixtureImportResult
import com.kevin.financeguardian.data.fixture.SmsFixtureImporter
import com.kevin.financeguardian.data.local.AppDataResetter
import com.kevin.financeguardian.data.preferences.UserPreferencesRepository
import com.kevin.financeguardian.data.sms.SmsIngestionResult
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
) : ViewModel() {
    private val permissionRefreshes = MutableStateFlow(0)
    private val resetInProgress = MutableStateFlow(false)
    private val dataActionMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferencesRepository.preferences,
        permissionRefreshes,
        resetInProgress,
        dataActionMessage,
    ) { preferences, _, isResetting, message ->
            SettingsUiState(
                appLockEnabled = preferences.appLockEnabled,
                screenPrivacyEnabled = preferences.screenPrivacyEnabled,
                debugParserModeEnabled = preferences.debugParserModeEnabled,
                permissions = permissionStatusChecker.currentStatuses(),
                isResetInProgress = isResetting,
                dataActionMessage = message,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsUiState(),
        )

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setAppLockEnabled(enabled) }
    }

    fun setScreenPrivacyEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setScreenPrivacyEnabled(enabled) }
    }

    fun setDebugParserModeEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setDebugParserModeEnabled(enabled) }
    }

    fun refreshPermissions() {
        permissionRefreshes.value += 1
    }

    fun resetAllData() {
        viewModelScope.launch {
            resetInProgress.value = true
            dataActionMessage.value = null
            runCatching { appDataResetter.resetAllData() }
                .onSuccess { dataActionMessage.value = "App data reset. Default categories restored." }
                .onFailure { dataActionMessage.value = it.message ?: "Data reset failed." }
            resetInProgress.value = false
        }
    }

    fun importFixtureJson(json: String) {
        viewModelScope.launch {
            dataActionMessage.value = null
            runCatching { smsFixtureImporter.importJson(json) }
                .onSuccess { results ->
                    dataActionMessage.value = results.toImportSummary()
                }
                .onFailure { dataActionMessage.value = it.message ?: "Fixture import failed." }
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
    val appLockEnabled: Boolean = true,
    val screenPrivacyEnabled: Boolean = false,
    val debugParserModeEnabled: Boolean = false,
    val permissions: AppPermissionStatuses = AppPermissionStatuses(
        receiveSmsGranted = false,
        postNotificationsGranted = false,
    ),
    val isResetInProgress: Boolean = false,
    val dataActionMessage: String? = null,
)
