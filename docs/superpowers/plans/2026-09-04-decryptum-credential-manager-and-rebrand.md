# Decryptum Credential Provider & Bottom Sheet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Android 14–16+ Credential Provider for Decryptum, replace legacy full-screen picker with a sleek Material 3 Bottom Sheet, enable instant biometric autofill on account tap (Option 1), and complete full rebranding from DoffiSecure to Decryptum.

**Architecture:** Integrate `androidx.credentials:credentials:1.5.0` to implement `DecryptumCredentialProviderService` for Chrome and Android 14+ apps; create `CredentialAuthActivity` for seamless one-tap biometric authentication and credential delivery; overhaul `AutofillPickerActivity` with a custom Decryptum Material 3 Bottom Sheet; update Android settings navigation in `SettingsViewModel`.

**Tech Stack:** Kotlin 2.2, Android 14–16+ (API 34–37), `androidx.credentials`, Jetpack Compose Material 3, BiometricPrompt, Koin.

---

## Global Constraints
- Preserve all existing AES/GCM envelope encryption logic in `PasswordCrypto`.
- Maintain exact key synchronization between `values/strings.xml` and `values-ru/strings.xml`.
- Keep custom bottom navigation capsule geometry and animations strictly untouched.
- Clean compilation and passing unit tests with exit code 0.

---

### Task 1: Complete Rebranding from DoffiSecure to Decryptum

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/doffi4/doffisecure/ui/password/SettingsScreen.kt`

- [ ] **Step 1:** Update all strings in `values/strings.xml` and `values-ru/strings.xml` replacing remaining occurrences of "DoffiSecure" with "Decryptum".
- [ ] **Step 2:** In `SettingsScreen.kt`, update CSV export filename from `"doffisecure_passwords.csv"` to `"decryptum_passwords.csv"`.
- [ ] **Step 3:** In `themes.xml` and `AndroidManifest.xml`, ensure translucent theme alias `Theme.Decryptum.Translucent` exists and services use `@string/autofill_service_label` ("Decryptum Autofill" / "Автозаполнение Decryptum").
- [ ] **Step 4:** Verify compilation with `.\gradlew.bat :app:compileDebugKotlin`.

---

### Task 2: Credential Manager Dependencies & XML Configuration

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/res/xml/credential_provider_config.xml`

- [ ] **Step 1:** Add `androidx-credentials = "1.5.0"` to `libs.versions.toml` and reference it in `app/build.gradle.kts` dependencies (`implementation(libs.androidx.credentials)`).
- [ ] **Step 2:** Update `credential_provider_config.xml` to declare `androidx.credentials.TYPE_PASSWORD_CREDENTIAL` and `android.credentials.TYPE_PASSWORD_CREDENTIAL`.
- [ ] **Step 3:** Run Gradle sync/compile check: `.\gradlew.bat :app:compileDebugKotlin`.

---

### Task 3: Decryptum Credential Provider Service (`DecryptumCredentialProviderService`)

**Files:**
- Create: `app/src/main/java/com/doffi4/doffisecure/autofill/DecryptumCredentialProviderService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1:** Implement `onBeginGetCredential` in `DecryptumCredentialProviderService`:
  - Extract origin or package: `request.callingAppInfo.origin ?: request.callingAppInfo.packageName`.
  - Extract web domain via `DomainUtils.extract(origin)`.
  - Retrieve matching passwords from `passwordRepository`.
  - For each matching password, build `PasswordCredentialEntry` using `PasswordCredentialEntry.Builder`:
    - Set title to `password.service`.
    - Set username to `password.username`.
    - Set icon to Decryptum key icon.
    - Set PendingIntent to launch `CredentialAuthActivity` with `passwordId`.
  - Add `ActionEntry` items:
    - "Выбрать другой пароль в Decryptum…" (opens `AutofillPickerActivity`).
    - "Сгенерировать пароль" (opens `GeneratorScreen` / dialog).
  - Return `BeginGetCredentialResponse`.
- [ ] **Step 2:** Register `DecryptumCredentialProviderService` in `AndroidManifest.xml` with `BIND_CREDENTIAL_PROVIDER_SERVICE` permission and alias to ensure backward compatibility.
- [ ] **Step 3:** Verify compilation with `.\gradlew.bat :app:compileDebugKotlin`.

---

### Task 4: One-Tap Biometric Authentication Activity (`CredentialAuthActivity`)

**Files:**
- Create: `app/src/main/java/com/doffi4/doffisecure/autofill/CredentialAuthActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1:** Implement `CredentialAuthActivity`:
  - Translucent activity triggered when an account is tapped in the Android Credential Manager bottom sheet.
  - Check `lockManager.isLocked()` and `userSettings.autofillAlwaysRequireAuth`:
    - If unlocked and auth not required: decrypt password immediately.
    - If locked / auth required: trigger `BiometricPrompt` with title "Decryptum".
  - Upon successful biometric authentication:
    - Unlock vault (`lockManager.setLocked(false)`).
    - Decrypt password with `passwordCrypto`.
    - Build `PasswordCredential(username, password)`.
    - Prepare result intent with `PendingIntentHandler.setGetCredentialResponse(resultIntent, GetCredentialResponse(credential))`.
    - Set result `Activity.RESULT_OK` and finish.
- [ ] **Step 2:** Register `CredentialAuthActivity` in `AndroidManifest.xml` with translucent theme.
- [ ] **Step 3:** Verify compilation with `.\gradlew.bat :app:compileDebugKotlin`.

---

### Task 5: Decryptum Material 3 Bottom Sheet Picker (`AutofillPickerActivity`)

**Files:**
- Modify: `app/src/main/java/com/doffi4/doffisecure/autofill/AutofillPickerActivity.kt`

- [ ] **Step 1:** Redesign UI to use a modern `ModalBottomSheet` or floating Bottom Sheet container:
  - Drag handle at top.
  - Clean Decryptum logo + title.
  - Search bar with instant filtering.
  - Matched domain cards first, then all vault items.
  - One-tap fill: tapping an account checks auth, immediately decrypts, sets dataset / credential result, and closes.
  - Copy buttons (copy username, copy password).
- [ ] **Step 2:** Support both Autofill framework datasets and Credential Manager results if invoked from an `ActionEntry`.
- [ ] **Step 3:** Verify compilation with `.\gradlew.bat :app:compileDebugKotlin`.

---

### Task 6: Android 14+ Credential Provider Settings Navigation

**Files:**
- Modify: `app/src/main/java/com/doffi4/doffisecure/ui/password/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/doffi4/doffisecure/ui/password/SettingsScreen.kt`

- [ ] **Step 1:** In `SettingsViewModel.kt`:
  - Add `openCredentialProviderSettings(context: Context)`:
    - On Android 14+ (API 34+), launch `android.provider.Settings.ACTION_CREDENTIAL_PROVIDER` (or fallback to `ACTION_SYNC_SETTINGS` / `ACTION_REQUEST_SET_AUTOFILL_SERVICE`).
- [ ] **Step 2:** In `SettingsScreen.kt`:
  - Under Autofill settings card, add information and direct button to activate Decryptum in "Поставщики учетных данных (Credential Provider)" for Android 14+ users.
- [ ] **Step 3:** Verify compilation with `.\gradlew.bat :app:compileDebugKotlin`.

---

### Task 7: Full Verification & Validation

- [ ] **Step 1:** Run `:app:compileDebugKotlin`.
- [ ] **Step 2:** Run `:app:testDebugUnitTest`.
- [ ] **Step 3:** Run `:app:assembleDebug`.
- [ ] **Step 4:** Run `:app:assembleRelease` to verify R8 rules and release bundle.
