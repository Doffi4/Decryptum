# Changelog

## [1.1.1] — 2026-10-09 (tester prerelease)

### Added
- Current synthetic Compose captures for the vault, TOTP, local Security Center and test-data controls; refreshed EN/RU/UK repository presentation and website release links.
- Isolated aggregate-only advisor contract and standalone synthetic evaluation harness, versioned prompts and stronger offline privacy/release isolation checks. The remote client remains debug-only.
- Configurable synthetic vault datasets: 1–5,000 records across up to 200 familiar services with uneven account counts, unique fictional logins per batch, sample counts and confirmation before adding.
- Separate four-record Security Center fixture and a passwordless public RFC 6238 TOTP demo, matching the tester instructions.
- EN/RU validation, pending/error/actual-save-count feedback and focused regression tests for the generator and native Material 3 controls.
- [Tester instructions](docs/TESTING.md) with the complete public fixtures and clear distinctions between the large dataset and expected Security Center counts.

### Fixed
- Developer entry code now matches tester instructions: six taps on the app title, then `Heytest08`. This code opens tools; vault authentication still uses the master password.
- Test generation no longer cycles evenly through six services. Records use the existing encrypted bulk import without writing a plaintext CSV file.

VersionName 1.1.1 / versionCode 8. This is a synthetic-data tester prerelease; native device validation remains pending. Claude guidance remains disabled in release builds.

## [1.1.0] — 2026-10-08 (prerelease)

### Added
- Local Security Center for weak-password heuristics, exact reuse and exact credential duplicates, with links to affected entries.
- Optional Claude Security Advisor prototype in debug builds: per-request consent and an exact four-count JSON preview. Disabled in release builds.
- Static presentation site with source-rendered privacy/security/product pages; release notes and product documentation.

### Improved
- Material 3 fallback colors, vault/TOTP/detail layouts, shared component styling and EN/RU copy. Dynamic color remains supported.
- Explicit unavailable/not-checked states for breach checks and unsupported coverage, without a misleading safety score.

### Fixed
- Main-tab navigation after Security Center no longer restores another screen through the start-tab saved-stack alias. Security-to-generator shortcuts share the bar action.
- Opaque tab surfaces and immediate screen transitions prevent overlapping screen content; rounded clipping contains bottom-tab ripple.
- Add-password sheet replaces the legacy alert, offers local generation/keyboard flow and waits for a successful write before closing; failed writes retain input and allow retry.
- Password repository no longer keeps a process-lifetime decrypted snapshot; locked reads and corrupt field decryption fail closed, and literal `enc:` user passwords are encrypted normally.
- Autofill picker reads metadata headers before authentication and fetches the selected password afterward; guarded repository reads no longer crash its locked pre-auth loading path.
- Mandatory auth no longer falls through to filling when biometrics are unavailable in picker/password Credential Manager/passkey auth paths. Password Credential Manager reuses the picker/master-password fallback.
- Master-password verification no longer accepts arbitrary input merely because a DEK is already cached.
- Plaintext DB migration checkpoints WAL, preserves `user_version`, reopens/checks the export and atomically replaces the source without deleting it first. Failed DB-key persistence and incomplete stored key pairs refuse database access. Native crash/low-space validation remains open.

### Security
- Automatic HIBP calls disconnected from password loading/edit flows; breaches explicitly remain not checked.
- Advisor uses an explicit aggregate DTO, bounded/cancellable HTTPS, no redirects/retries, untrusted text responses and no vault tools. No production provider key is embedded.
- Removed service/exception details from the touched bulk-encryption log and SQLCipher migration exception logging. Picker/password credential auth honor screenshot protection.
- Documented actual encryption, software/exportable passkeys, network metadata, readable CSV and recovery limits; corrected misleading zero-knowledge/hardware claims.

### Developer
- Published versionName 1.1.0 / versionCode 7; JVM regression coverage and disposable native migration fixtures added.
- Build/test/lint results, certificate comparison and artifact hashes recorded in the release verification notes. Android runtime tests require a device; compilation is not execution.
- README EN/RU/UK, policies, contribution/issue/PR guidance and static website source accompany this release. The website is published on GitHub Pages.

Published as a prerelease with versionCode 7. Native authorization/migration/recovery/passkey evidence and production signing/disclosure decisions remain open. See [release notes](docs/RELEASE_NOTES_1.1.md) and [known limitations](SECURITY.md#known-limitations).

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-09-08

### Changed
- Redesigned Autofill Bottom Sheet (`AutofillPickerActivity`) to full Material 3 standards:
  - Account cards styled with clean outlines (`outlineVariant`), `surfaceContainerHigh` container fill, and native ripple feedback.
  - Compact dialog height with removed redundant "Later" button.
  - Added Material 3 filled tonal close button (12dp rounded square container).
- Branded Gboard suggestion chip:
  - Added transparent-background Decryptum shield brand vector icon (`ic_autofill_decryptum`).
  - Updated chip action text to "Search in Decryptum" / "Искать в Decryptum".
- Added `org.gradle.daemon=false` to `gradle.properties` to prevent Windows file-lock issues during build.
- Bumped version to 1.0.1 (versionCode 5).

## [1.0.0] - 2026-09-08

### Added
- Implemented Passkey (FIDO2 / WebAuthn) registration and authentication using AndroidX Credentials API.
- Implemented software ECDSA P-256 keypair generation with encrypted exportable private keys (corrected description; the implementation does not generate these keys inside Android Keystore).
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
- Implemented `ClipboardClearReceiver` via AlarmManager for best-effort background clipboard clearing; OS restrictions can prevent it.
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
- Added per-app language selection with Android 13+ `LocaleManager` support. Current app strings are English/Russian; Ukrainian README availability does not establish Ukrainian app localization.

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
