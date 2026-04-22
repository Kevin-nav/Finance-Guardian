# Onboarding Permissions App Lock Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Gate Finance Guardian behind first-run SMS disclosure/runtime permission flow and biometric/device-credential app lock before showing financial data.

**Architecture:** Add a root app-shell ViewModel that combines persisted preferences, permission status, and in-memory lock state. Keep Android permission launchers and `BiometricPrompt` at the Activity/Compose boundary, while keeping routing and state transitions testable in pure JVM tests. Hide the main navigation until onboarding is complete and the app is unlocked.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, Hilt, DataStore Preferences, AndroidX Biometric, AndroidX Activity Result APIs, Robolectric/JUnit, Turbine/coroutines test.

---

## Current Context

The repo already has:

- `UserPreferencesRepository` with `appLockEnabled`, `screenPrivacyEnabled`, `debugParserModeEnabled`, and `onboardingCompleted`.
- `PermissionStatusChecker` and `AndroidPermissionStatusChecker`.
- `OnboardingRoute`, but it is not wired into navigation.
- `AppLockState`, but there is no biometric/device credential lock flow.
- `SettingsViewModel`, but permission grant buttons are still TODOs and permission status is not explicitly refreshed on resume.
- `MainActivity` currently extends `ComponentActivity`, observes screen privacy, and always shows `FinanceGuardianApp()`.
- `FinanceGuardianApp` starts directly at the Home tab and always shows the bottom navigation.

This plan intentionally fixes only the app-entry/security holes. Do not wire Transactions/Categories/Insights to Room in this batch.

## Behavioral Decisions

- First launch shows onboarding before any financial screen.
- Pressing `Enable SMS Access` launches the `RECEIVE_SMS` runtime permission request.
- Whether SMS permission is granted or denied, onboarding is marked complete after the request returns because the MVP must remain usable without SMS permission.
- Pressing `Set up later` marks onboarding complete without requesting SMS permission.
- App lock defaults on through existing preferences.
- When app lock is enabled and onboarding is complete, the app starts locked and hides all main financial screens.
- Successful biometric/device credential authentication unlocks the app for the current process/foreground session.
- If app lock is disabled, the app skips the lock screen.
- Settings can request SMS and notification permissions and refreshes status when the screen resumes.
- Do not request `READ_SMS`; it is still deferred.

## Task 1: Add Root App Shell State

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/ui/AppShellViewModel.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/ui/AppShellViewModelTest.kt`
- Modify only if needed: `app/src/main/java/com/kevin/financeguardian/core/security/AppLockState.kt`

**Step 1: Write failing ViewModel tests**

Create `AppShellViewModelTest` with fake preferences, fake permission checker, and coroutine test dispatchers.

Cover:

- Defaults show onboarding and keep lock state locked.
- Completing onboarding hides onboarding.
- SMS permission result completes onboarding and refreshes permissions.
- App lock disabled produces `AppLockState.Disabled`.
- Calling `unlock()` changes lock state to `Unlocked`.
- Calling `lock()` after onboarding returns to `Locked` when app lock is enabled.

Use a fake permission checker like:

```kotlin
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
```

Expected state shape:

```kotlin
data class AppShellUiState(
    val onboardingCompleted: Boolean = false,
    val permissions: AppPermissionStatuses = AppPermissionStatuses(
        receiveSmsGranted = false,
        postNotificationsGranted = false,
    ),
    val appLockEnabled: Boolean = true,
    val appLockState: AppLockState = AppLockState.Locked,
) {
    val shouldShowOnboarding: Boolean = !onboardingCompleted
    val shouldShowLock: Boolean =
        onboardingCompleted && appLockState == AppLockState.Locked
}
```

**Step 2: Run tests to verify they fail**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\Kevin\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests "*AppShellViewModelTest"
```

Expected: fails because `AppShellViewModel` does not exist.

**Step 3: Implement `AppShellViewModel`**

Implementation requirements:

- Annotate with `@HiltViewModel`.
- Inject `UserPreferencesRepository` and `PermissionStatusChecker`.
- Keep a private `MutableStateFlow(AppLockState.Locked)`.
- Keep a private refresh trigger flow so permission status can be re-read on demand.
- Combine preferences, lock state, and refresh trigger into `uiState`.
- If `appLockEnabled == false`, emit `AppLockState.Disabled`.
- `completeOnboarding()` calls `setOnboardingCompleted(true)`.
- `onSmsPermissionResult()` calls `setOnboardingCompleted(true)` and `refreshPermissions()`.
- `refreshPermissions()` re-reads `PermissionStatusChecker.currentStatuses()`.
- `unlock()` sets in-memory lock state to `Unlocked`.
- `lock()` sets in-memory lock state to `Locked` only when the app is not disabled.

Use this implementation shape:

```kotlin
@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val permissionStatusChecker: PermissionStatusChecker,
) : ViewModel() {
    private val appLockState = MutableStateFlow(AppLockState.Locked)
    private val permissionRefreshes = MutableStateFlow(0)

    val uiState: StateFlow<AppShellUiState> = combine(
        userPreferencesRepository.preferences,
        appLockState,
        permissionRefreshes,
    ) { preferences, lockState, _ ->
        AppShellUiState(
            onboardingCompleted = preferences.onboardingCompleted,
            permissions = permissionStatusChecker.currentStatuses(),
            appLockEnabled = preferences.appLockEnabled,
            appLockState = if (preferences.appLockEnabled) lockState else AppLockState.Disabled,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppShellUiState(),
    )

    fun completeOnboarding() {
        viewModelScope.launch {
            userPreferencesRepository.setOnboardingCompleted(true)
            refreshPermissions()
        }
    }

    fun onSmsPermissionResult() {
        completeOnboarding()
    }

    fun refreshPermissions() {
        permissionRefreshes.value += 1
    }

    fun unlock() {
        appLockState.value = AppLockState.Unlocked
    }

    fun lock() {
        appLockState.value = AppLockState.Locked
    }
}
```

Adjust details as needed for tests; keep state logic in this ViewModel and avoid putting routing decisions in `MainActivity`.

**Step 4: Run focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AppShellViewModelTest"
```

Expected: all AppShell tests pass.

**Step 5: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/ui/AppShellViewModel.kt app/src/test/java/com/kevin/financeguardian/ui/AppShellViewModelTest.kt app/src/main/java/com/kevin/financeguardian/core/security/AppLockState.kt
git commit -m "feat: add app shell state"
```

## Task 2: Wire Onboarding And SMS Permission Request

**Files:**
- Modify: `app/src/main/java/com/kevin/financeguardian/ui/FinanceGuardianApp.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/feature/onboarding/OnboardingRoute.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/MainActivity.kt` only if Activity-owned callbacks are chosen

**Step 1: Rename onboarding callbacks for accuracy**

In `OnboardingRoute`, rename:

```kotlin
onPermissionGranted: () -> Unit = {},
onSkip: () -> Unit = {},
```

to:

```kotlin
onRequestSmsPermission: () -> Unit = {},
onSetUpLater: () -> Unit = {},
```

Wire the button callbacks:

```kotlin
Button(onClick = onRequestSmsPermission, ...)
TextButton(onClick = onSetUpLater) { ... }
```

**Step 2: Add permission launcher at the root Compose boundary**

In `FinanceGuardianApp`, create the root ViewModel and SMS permission launcher:

```kotlin
val viewModel: AppShellViewModel = hiltViewModel()
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
val smsPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
) {
    viewModel.onSmsPermissionResult()
}
```

Required imports include:

```kotlin
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
```

**Step 3: Gate onboarding before the main scaffold**

Before creating the bottom navigation scaffold:

```kotlin
if (uiState.shouldShowOnboarding) {
    OnboardingRoute(
        modifier = modifier,
        onRequestSmsPermission = {
            smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
        },
        onSetUpLater = viewModel::completeOnboarding,
    )
    return
}
```

This must hide the bottom navigation while onboarding is visible.

**Step 4: Run compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: compile succeeds.

**Step 5: Run app shell and preference regressions**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AppShellViewModelTest" --tests "*UserPreferencesRepositoryTest"
```

Expected: tests pass.

**Step 6: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/ui/FinanceGuardianApp.kt app/src/main/java/com/kevin/financeguardian/feature/onboarding/OnboardingRoute.kt
git commit -m "feat: gate app behind onboarding"
```

## Task 3: Refresh And Request Permissions From Settings

**Files:**
- Modify: `app/src/main/java/com/kevin/financeguardian/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/feature/settings/SettingsRoute.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/ui/FinanceGuardianApp.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/feature/settings/SettingsViewModelTest.kt`

**Step 1: Write failing `SettingsViewModel` tests**

Cover:

- `refreshPermissions()` updates denied to granted SMS status.
- `refreshPermissions()` updates notification status.
- Existing preference toggles still persist.

Use the same fake permission checker pattern from Task 1.

**Step 2: Run failing tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SettingsViewModelTest"
```

Expected: fails because `refreshPermissions()` is missing or state does not update.

**Step 3: Add refresh trigger to `SettingsViewModel`**

Update `SettingsViewModel` to combine preferences with a permission refresh counter:

```kotlin
private val permissionRefreshes = MutableStateFlow(0)

val uiState: StateFlow<SettingsUiState> = combine(
    userPreferencesRepository.preferences,
    permissionRefreshes,
) { preferences, _ ->
    SettingsUiState(
        appLockEnabled = preferences.appLockEnabled,
        screenPrivacyEnabled = preferences.screenPrivacyEnabled,
        debugParserModeEnabled = preferences.debugParserModeEnabled,
        permissions = permissionStatusChecker.currentStatuses(),
    )
}.stateIn(...)

fun refreshPermissions() {
    permissionRefreshes.value += 1
}
```

**Step 4: Add settings permission callbacks**

Change `SettingsRoute` signature:

```kotlin
fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onRequestSmsPermission: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
)
```

Use lifecycle resume refresh:

```kotlin
LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    viewModel.refreshPermissions()
}
```

Update `SettingsStatusRow` to accept:

```kotlin
onGrantClick: (() -> Unit)? = null
```

and wire:

```kotlin
if (!isGranted && onGrantClick != null) {
    TextButton(onClick = onGrantClick) {
        Text("Grant")
    }
}
```

Pass callbacks:

```kotlin
SettingsStatusRow(
    icon = Icons.Filled.Sms,
    title = "SMS Access",
    isGranted = uiState.permissions.receiveSmsGranted,
    onGrantClick = onRequestSmsPermission,
)
```

For notifications, only request on Android 13+:

```kotlin
onGrantClick = onRequestNotificationPermission
```

**Step 5: Add launchers in `FinanceGuardianApp` and pass to Settings**

Add:

```kotlin
val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
) {
    viewModel.refreshPermissions()
}
```

When creating `SettingsRoute`:

```kotlin
SettingsRoute(
    onRequestSmsPermission = {
        smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
    },
    onRequestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.refreshPermissions()
        }
    },
)
```

On the SMS launcher result, call both:

```kotlin
viewModel.onSmsPermissionResult()
viewModel.refreshPermissions()
```

**Step 6: Run tests and compile**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*SettingsViewModelTest" --tests "*AppShellViewModelTest"
.\gradlew.bat :app:compileDebugKotlin
```

Expected: tests and compile pass.

**Step 7: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/feature/settings/SettingsViewModel.kt app/src/main/java/com/kevin/financeguardian/feature/settings/SettingsRoute.kt app/src/main/java/com/kevin/financeguardian/ui/FinanceGuardianApp.kt app/src/test/java/com/kevin/financeguardian/feature/settings/SettingsViewModelTest.kt
git commit -m "feat: request permissions from settings"
```

## Task 4: Add Biometric App-Lock Authenticator

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/core/security/AppLockAuthenticator.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/security/AndroidAppLockAuthenticator.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/security/AppLockPromptSpec.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/core/security/AppLockPromptSpecTest.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/MainActivity.kt`
- Modify if needed: `gradle/libs.versions.toml`
- Modify if needed: `app/build.gradle.kts`

**Step 1: Write prompt spec test**

Create `AppLockPromptSpecTest`:

```kotlin
class AppLockPromptSpecTest {
    @Test
    fun defaultPromptAllowsBiometricOrDeviceCredential() {
        val spec = AppLockPromptSpec.default()

        assertEquals("Unlock Finance Guardian", spec.title)
        assertTrue(
            spec.allowedAuthenticators and BiometricManager.Authenticators.BIOMETRIC_STRONG != 0,
        )
        assertTrue(
            spec.allowedAuthenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL != 0,
        )
    }
}
```

**Step 2: Run failing test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AppLockPromptSpecTest"
```

Expected: fails because prompt spec does not exist.

**Step 3: Implement prompt spec and authenticator interface**

Create:

```kotlin
data class AppLockPromptSpec(
    val title: String,
    val subtitle: String,
    val allowedAuthenticators: Int,
) {
    companion object {
        fun default(): AppLockPromptSpec = AppLockPromptSpec(
            title = "Unlock Finance Guardian",
            subtitle = "Confirm it is you before viewing financial data.",
            allowedAuthenticators =
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
    }
}
```

Create:

```kotlin
interface AppLockAuthenticator {
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
        onError: (String) -> Unit,
    )
}
```

**Step 4: Implement `AndroidAppLockAuthenticator`**

Use `BiometricPrompt` with device credential fallback:

```kotlin
class AndroidAppLockAuthenticator @Inject constructor() : AppLockAuthenticator {
    override fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    onFailure()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            },
        )

        val spec = AppLockPromptSpec.default()
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(spec.title)
            .setSubtitle(spec.subtitle)
            .setAllowedAuthenticators(spec.allowedAuthenticators)
            .build()

        prompt.authenticate(promptInfo)
    }
}
```

**Step 5: Ensure Activity supports `BiometricPrompt`**

Change `MainActivity` to extend `FragmentActivity`:

```kotlin
class MainActivity : FragmentActivity() {
```

Keep `@AndroidEntryPoint`, `enableEdgeToEdge()`, `setContent`, and screen privacy logic unchanged.

If compile cannot resolve `FragmentActivity`, add direct dependency:

`gradle/libs.versions.toml`

```toml
fragment = "1.8.9"
androidx-fragment-ktx = { module = "androidx.fragment:fragment-ktx", version.ref = "fragment" }
```

`app/build.gradle.kts`

```kotlin
implementation(libs.androidx.fragment.ktx)
```

Use the latest compatible version already resolved by Gradle if the catalog version needs adjustment.

**Step 6: Run focused test and compile**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AppLockPromptSpecTest"
.\gradlew.bat :app:compileDebugKotlin
```

Expected: test and compile pass.

**Step 7: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/core/security/AppLockAuthenticator.kt app/src/main/java/com/kevin/financeguardian/core/security/AndroidAppLockAuthenticator.kt app/src/main/java/com/kevin/financeguardian/core/security/AppLockPromptSpec.kt app/src/test/java/com/kevin/financeguardian/core/security/AppLockPromptSpecTest.kt app/src/main/java/com/kevin/financeguardian/MainActivity.kt gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: add biometric app lock authenticator"
```

## Task 5: Add Lock Screen And Gate Main Navigation

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/feature/security/AppLockRoute.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/ui/FinanceGuardianApp.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/MainActivity.kt`

**Step 1: Add lock route composable**

Create `AppLockRoute`:

```kotlin
@Composable
fun AppLockRoute(
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.md))
        Text(
            text = "Finance Guardian is locked",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.lg))
        Button(onClick = onUnlockClick) {
            Text("Unlock")
        }
    }
}
```

Keep this screen simple and do not show balances, transactions, categories, or insights.

**Step 2: Inject authenticator in `MainActivity`**

Add:

```kotlin
@Inject lateinit var appLockAuthenticator: AppLockAuthenticator
```

Pass an authentication callback into `FinanceGuardianApp`:

```kotlin
FinanceGuardianApp(
    onAuthenticate = { onSuccess, onFailure, onError ->
        appLockAuthenticator.authenticate(
            activity = this,
            onSuccess = onSuccess,
            onFailure = onFailure,
            onError = onError,
        )
    },
)
```

Define the callback type in `FinanceGuardianApp` as:

```kotlin
typealias AuthenticateAppLock = (
    onSuccess: () -> Unit,
    onFailure: () -> Unit,
    onError: (String) -> Unit,
) -> Unit
```

**Step 3: Gate lock state before main scaffold**

In `FinanceGuardianApp`, after onboarding gate and before the main navigation scaffold:

```kotlin
if (uiState.shouldShowLock) {
    AppLockRoute(
        modifier = modifier,
        onUnlockClick = {
            onAuthenticate(
                { viewModel.unlock() },
                { },
                { },
            )
        },
    )
    return
}
```

This must hide the bottom navigation while locked.

**Step 4: Prompt once when locked**

Add a `LaunchedEffect` that triggers authentication once when the app enters locked state:

```kotlin
LaunchedEffect(uiState.shouldShowLock) {
    if (uiState.shouldShowLock) {
        onAuthenticate(
            { viewModel.unlock() },
            { },
            { },
        )
    }
}
```

Guard against repeated prompt loops by keying only on transition into `shouldShowLock`. If this proves noisy on device, remove auto-prompt and keep the explicit Unlock button.

**Step 5: Compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: compile succeeds.

**Step 6: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/feature/security/AppLockRoute.kt app/src/main/java/com/kevin/financeguardian/ui/FinanceGuardianApp.kt app/src/main/java/com/kevin/financeguardian/MainActivity.kt
git commit -m "feat: gate app behind app lock"
```

## Task 6: Lock Again On App Background

**Files:**
- Modify: `app/src/main/java/com/kevin/financeguardian/ui/FinanceGuardianApp.kt`
- Modify: `app/src/test/java/com/kevin/financeguardian/ui/AppShellViewModelTest.kt`

**Step 1: Add ViewModel test for relock**

In `AppShellViewModelTest`, cover:

- Complete onboarding.
- Unlock.
- Call `lock()`.
- State becomes `Locked`.

Also cover:

- Set app lock disabled.
- Call `lock()`.
- State remains effectively `Disabled` in `uiState`.

**Step 2: Run tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AppShellViewModelTest"
```

Expected: pass if Task 1 state handling is correct; otherwise fix ViewModel.

**Step 3: Add lifecycle lock hook**

In `FinanceGuardianApp`, add:

```kotlin
LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
    viewModel.lock()
}

LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    viewModel.refreshPermissions()
}
```

Required imports:

```kotlin
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
```

This keeps the app locked after leaving and returning to the foreground.

**Step 4: Run tests and compile**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AppShellViewModelTest"
.\gradlew.bat :app:compileDebugKotlin
```

Expected: tests and compile pass.

**Step 5: Commit**

Run:

```powershell
git add app/src/main/java/com/kevin/financeguardian/ui/FinanceGuardianApp.kt app/src/test/java/com/kevin/financeguardian/ui/AppShellViewModelTest.kt
git commit -m "feat: relock app after background"
```

## Task 7: Final Verification

**Files:**
- No edits expected.

**Step 1: Run focused security/app-entry tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AppShellViewModelTest" --tests "*SettingsViewModelTest" --tests "*AppLockPromptSpecTest" --tests "*UserPreferencesRepositoryTest" --tests "*AndroidPermissionStatusCheckerTest"
```

Expected: all focused tests pass.

**Step 2: Run full unit suite**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --stacktrace
```

Expected: all JVM/Robolectric tests pass.

**Step 3: Build debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace
```

Expected: build succeeds and APK exists at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

**Step 4: Manual device checks**

Install debug APK and verify:

- Fresh install opens onboarding first.
- Main tabs are not visible during onboarding.
- `Enable SMS Access` launches Android SMS permission dialog.
- Denying SMS permission still enters the app after the dialog returns.
- `Set up later` enters the app without requesting SMS permission.
- With app lock enabled, app prompts biometric/device credential before showing the main tabs.
- Canceling authentication leaves app locked.
- Successful authentication shows the main tabs.
- Turning App Lock off in Settings skips lock on next foreground entry.
- Re-enabling App Lock causes the app to lock again after leaving and returning.
- Settings `Grant` for SMS launches the permission dialog when permission is denied.
- Settings permission state updates after returning from system permission flow.

**Step 5: Inspect status**

Run:

```powershell
git status --short
git log --oneline --decorate -12
```

Expected: working tree is clean and commits reflect the tasks above.

## Deferred Follow-Up

- Wire Transactions screen to Room-backed data.
- Wire transaction correction sheet to `TransactionCorrectionService`.
- Wire Categories screen to Room-backed categories and add custom category CRUD/archive behavior.
- Wire Insights screen to Room-derived summaries.
- Add debug fixture import UI after the app entry/security flow is reliable.
- Add Compose UI tests for onboarding/lock routing if the project adds a stable Compose test harness for JVM or connected tests.
