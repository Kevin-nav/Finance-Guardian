# App Lock Authentication Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Require Android biometric/device credential authentication before Finance Guardian shows the main financial screens.

**Architecture:** Keep authentication at the `MainActivity` boundary through an injectable AndroidX Biometric wrapper. Keep lock routing in `FinanceGuardianApp` using the existing `AppShellViewModel`, so Compose only decides whether to show onboarding, lock screen, or main tabs. Relock when the app backgrounds by driving the existing in-memory lock state.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Biometric, Hilt, FragmentActivity, Lifecycle Compose, JUnit/Robolectric.

---

## Current Context

Already implemented:

- `AppShellViewModel` exposes `AppShellUiState.shouldShowLock`.
- `FinanceGuardianApp` gates onboarding before the main navigation.
- `UserPreferences.appLockEnabled` defaults to `true`.
- `SettingsRoute` can toggle app lock.
- `AppLockState` has `Locked`, `Authenticating`, `Unlocked`, and `Disabled`.

Missing:

- No `BiometricPrompt` integration exists.
- `MainActivity` is still a `ComponentActivity`.
- No locked UI exists.
- `FinanceGuardianApp` does not yet hide main navigation when `shouldShowLock` is true.
- The app does not relock after backgrounding.

## Task 1: Add Biometric App-Lock Authenticator

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/core/security/AppLockPromptSpec.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/security/AppLockAuthenticator.kt`
- Create: `app/src/main/java/com/kevin/financeguardian/core/security/AndroidAppLockAuthenticator.kt`
- Create: `app/src/test/java/com/kevin/financeguardian/core/security/AppLockPromptSpecTest.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/MainActivity.kt`

**Steps:**

1. Write `AppLockPromptSpecTest` asserting the default prompt title and allowed authenticators include `BIOMETRIC_STRONG` and `DEVICE_CREDENTIAL`.
2. Run `.\gradlew.bat :app:testDebugUnitTest --tests "*AppLockPromptSpecTest"` and verify it fails.
3. Implement `AppLockPromptSpec`, `AppLockAuthenticator`, and `AndroidAppLockAuthenticator`.
4. Change `MainActivity` from `ComponentActivity` to `FragmentActivity`.
5. Inject `AndroidAppLockAuthenticator` into `MainActivity`.
6. Run `.\gradlew.bat :app:testDebugUnitTest --tests "*AppLockPromptSpecTest"` and `.\gradlew.bat :app:compileDebugKotlin`.
7. Commit as `feat: add biometric app lock authenticator`.

## Task 2: Add Lock Screen And Gate Main Navigation

**Files:**
- Create: `app/src/main/java/com/kevin/financeguardian/feature/security/AppLockRoute.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/ui/FinanceGuardianApp.kt`
- Modify: `app/src/main/java/com/kevin/financeguardian/MainActivity.kt`

**Steps:**

1. Create a simple `AppLockRoute` with a lock icon, short title, and Unlock button.
2. Add an authentication callback type to `FinanceGuardianApp`.
3. Pass the real authenticator callback from `MainActivity`.
4. In `FinanceGuardianApp`, show `AppLockRoute` when `uiState.shouldShowLock` is true and return before main `Scaffold`.
5. On unlock button click, call the authenticator and invoke `viewModel.unlock()` on success.
6. Add a `LaunchedEffect` to trigger the prompt once when the app enters locked state.
7. Run `.\gradlew.bat :app:compileDebugKotlin`.
8. Commit as `feat: gate app behind app lock`.

## Task 3: Relock After Backgrounding

**Files:**
- Modify: `app/src/main/java/com/kevin/financeguardian/ui/FinanceGuardianApp.kt`
- Modify: `app/src/test/java/com/kevin/financeguardian/ui/AppShellViewModelTest.kt`

**Steps:**

1. Add tests for relocking after unlock and ensuring disabled app lock still reports `Disabled`.
2. Run `.\gradlew.bat :app:testDebugUnitTest --tests "*AppShellViewModelTest"`.
3. Add `LifecycleEventEffect(Lifecycle.Event.ON_STOP)` in `FinanceGuardianApp` to call `viewModel.lock()`.
4. Add `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)` to refresh permission status.
5. Run focused tests and compile.
6. Commit as `feat: relock app after background`.

## Task 4: Final Verification

**Files:**
- No edits expected.

**Steps:**

1. Run focused app-entry/security tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*AppShellViewModelTest" --tests "*SettingsViewModelTest" --tests "*AppLockPromptSpecTest" --tests "*UserPreferencesRepositoryTest" --tests "*AndroidPermissionStatusCheckerTest"
```

2. Run full JVM test suite:

```powershell
.\gradlew.bat :app:testDebugUnitTest --stacktrace
```

3. Build debug APK:

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace
```

4. Confirm `git status --short` is clean.

## Deferred Follow-Up

- Device-test the biometric prompt on Kevin's phone.
- Add UI automation once Compose test infrastructure is stable.
- Continue with Room-backed Transactions UI after app-entry security is complete.
