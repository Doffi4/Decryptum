# Changelog 📜

All notable changes to the **Decryptum** password manager project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.10.0 Beta] - 2026-09-08

### 🌟 Added
- **Passkeys Support (FIDO2 / WebAuthn)**:
  - Full native support for passkey registration and authentication via Android Credential Manager.
  - Hardware-backed ECDSA P-256 (secp256r1) keypair generation in Android Keystore.
  - Built-in CBOR encoding/decoding and WebAuthn authenticator response generation.
  - Dedicated passkey management in credential details (`PasskeySaveActivity`, `PasskeyAuthActivity`, `PasskeyDao`, `PasskeyRepositoryImpl`).
- **Integrated 2FA / TOTP Authenticator (RFC 6238)**:
  - Built-in time-based one-time password generator supporting SHA-1, SHA-256, and SHA-512 algorithms, customizable digit lengths (6 or 8 digits), and customizable time periods (30s / 60s).
  - Smooth animated circular countdown timer with real-time token regeneration.
  - Camera-based live QR code scanner powered by CameraX and ML Kit Barcode Scanning (`QrCodeScannerDialog`).
  - Gallery QR code image picker (`QrCodeImageScanner`) with ZXing fallback.
  - Manual secret key input dialog with algorithm and interval configuration (`ManualTotpInputDialog`).
  - One-tap batch import from Google Authenticator (`otpauth-migration://offline?data=...`) via `GoogleAuthImportDialog`.
- **Compromised Password Audit (HaveIBeenPwned API)**:
  - Zero-knowledge breach detection using the HaveIBeenPwned k-Anonymity API (SHA-1 prefix range query; full password and hash are never sent over the network).
  - Bulk vault security scanner (`BreachAuditBottomSheet`) to audit all saved accounts with a single tap.
  - Visual breach warnings and risk status badges in account details.
- **SQLCipher Full-Database Encryption**:
  - Transparent 256-bit AES database encryption for all Room tables, indexes, and metadata using SQLCipher (`sqlcipher-android`).
  - Safe, automatic on-the-fly migration from legacy plaintext SQLite database to encrypted SQLCipher via `DatabaseMigrator`.
- **Argon2id Master Key Derivation**:
  - Upgraded master password hashing from basic SHA-256 iterations to memory-hard **Argon2id** (RFC 9106) via `argon2kt`, offering maximum resistance against GPU brute-force attacks.
- **Automated Security & Unit Test Suite**:
  - 12 new comprehensive test suites covering TOTP math, WebAuthn cryptographic assertions, CBOR encoding, Google Authenticator migration parser, Argon2id KDF, SQLCipher migration, and rate limiting.

### 🛡️ Changed & Security Enhancements
- **Privacy-First Favicon Fetching**:
  - Removed domain HTTP requests from `VaultWarmup` during lock screen display, preventing network traffic leakage of saved accounts before user authentication.
  - Implemented offline-first `FaviconFetcher` with local `IcoDecoder` and deferred network fetching only after explicit vault unlock.
- **Enhanced Clipboard Auto-Clear**:
  - Implemented `ClipDescription.EXTRA_IS_SENSITIVE = true` on Android 13+ (API 33+) to prevent OS visual previews and third-party keyboard history snooping.
  - Integrated `ClipboardClearReceiver` using Android AlarmManager for guaranteed clipboard purging even if the application is killed or backgrounded.
- **Autofill Save Flow**:
  - Implemented full `AutofillSaveActivity` and Credential Manager save handler, prompting users to save newly entered credentials directly from browsers and third-party apps.

---

## [0.9.0 Beta] - 2026-09-05

### ⚡ Added
- **Native Android Autofill Service**:
  - Integrated Android `AutofillService` framework for fast credential suggestions across mobile browsers (Chrome, Firefox, Brave, etc.) and native applications.
  - Inline IME keyboard suggestions for Gboard and compatible keyboards.
  - Material 3 Bottom Sheet modal for selecting accounts and searching the vault.
  - Strict form parser (`AutofillStructureParser`) to prevent false-positive prompts in search bars and chat windows.
- **Multi-Language Support (Localization)**:
  - Built-in per-app language switcher supporting **English (🇬🇧)**, **Ukrainian (🇺🇦)**, and **Russian (🇷🇺)**.
  - Android 13+ Per-App Language Preferences integration via `AppLocaleManager`.

### 🛠️ Changed
- **Version Bump**: Updated to `v0.9.0 Beta` (`versionCode = 2`).
- **Optimization**: R8 minification and Baseline Profiles optimizations (~2.9 MB APK).

---

## [0.8.0 Beta] - 2026-09-04

### 🚀 Initial Public Beta
- Local AES-GCM envelope encryption using hardware-backed Android Keystore (`enc:2:...`).
- Biometric unlock (Fingerprint & Face) via `BiometricPrompt` with customizable auto-lock timer.
- Strong password generator with entropy meter and customizable presets.
- Bulk CSV import/export compatible with Chrome, Bitwarden, LastPass, and KeePass.
- Dynamic Material You theme, custom bottom navigation bar, and developer diagnostic overlay.
