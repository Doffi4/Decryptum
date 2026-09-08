# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-09-08

### Added
- Implemented Passkey (FIDO2 / WebAuthn) registration and authentication using AndroidX Credentials API.
- Implemented hardware-backed ECDSA P-256 keypair generation in Android Keystore.
- Added native CBOR encoder and WebAuthn authenticator response parser.
- Added built-in RFC 6238 TOTP authenticator with SHA-1, SHA-256, and SHA-512 support.
- Added live QR code scanning via CameraX and ML Kit Barcode Scanning.
- Added QR code image parser with ZXing fallback.
- Added Google Authenticator migration export parser (`otpauth-migration://`).
- Added HaveIBeenPwned breach detection using k-Anonymity SHA-1 prefix API.
- Added automated unit test suites for TOTP, WebAuthn, CBOR, SQLCipher migration, and rate limiting.

### Security
- Integrated SQLCipher 4.6.1 for full page-level AES-256 database encryption.
- Added automatic SQLite-to-SQLCipher migration mechanism (`DatabaseMigrator`).
- Migrated master password derivation to Argon2id (RFC 9106) via `argon2kt`.
- Added `ClipDescription.EXTRA_IS_SENSITIVE` on Android 13+ to suppress clipboard previews.
- Implemented `ClipboardClearReceiver` via AlarmManager for reliable background clipboard wiping.
- Replaced pre-unlock lock screen network requests with local `IcoDecoder` and deferred favicon fetching.

### Changed
- Migrated credential saving to dedicated `AutofillSaveActivity` and Credential Provider flow.
- Optimized R8 shrinking rules and Baseline Profiles for faster cold start.
- Bumped version to 1.0.0 (versionCode 4).

## [0.9.0] - 2026-09-05

### Added
- Implemented Android `AutofillService` with inline IME keyboard suggestions.
- Added Material 3 bottom sheet picker for account selection.
- Added strict form analysis (`AutofillStructureParser`) to filter non-login input fields.
- Added per-app language selection (English, Ukrainian, Russian) with Android 13+ `LocaleManager` support.

### Changed
- Bumped version to 0.9.0 (versionCode 2).
- Reduced APK size using R8 code and resource shrinking.

## [0.8.0] - 2026-09-04

### Added
- Initial release.
- Android Keystore AES-GCM envelope encryption.
- Biometric unlock (`BiometricPrompt`) with auto-lock timer.
- Password generator with entropy calculation and customization options.
- CSV import and export compatible with Bitwarden, KeePass, Chrome, and LastPass.
- Material You dynamic theming and custom bottom navigation.
- Diagnostic overlay for development mode (FPS and frame drop metrics).
