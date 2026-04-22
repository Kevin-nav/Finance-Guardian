package com.kevin.financeguardian.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.financeguardian.core.permissions.AppPermissionStatuses
import com.kevin.financeguardian.core.permissions.PermissionStatusChecker
import com.kevin.financeguardian.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val permissionStatusChecker: PermissionStatusChecker,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = userPreferencesRepository.preferences
        .map { preferences ->
            SettingsUiState(
                appLockEnabled = preferences.appLockEnabled,
                screenPrivacyEnabled = preferences.screenPrivacyEnabled,
                debugParserModeEnabled = preferences.debugParserModeEnabled,
                permissions = permissionStatusChecker.currentStatuses(),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
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
}

data class SettingsUiState(
    val appLockEnabled: Boolean = true,
    val screenPrivacyEnabled: Boolean = false,
    val debugParserModeEnabled: Boolean = false,
    val permissions: AppPermissionStatuses = AppPermissionStatuses(
        receiveSmsGranted = false,
        postNotificationsGranted = false,
    ),
)
