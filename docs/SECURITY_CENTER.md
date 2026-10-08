# Local Security Center (Phase 3)

The local Security Center analyzes the unlocked vault on this device. Local analysis works offline, uses no AI/client/API keys, does not request favicons, and does not change or delete credentials. Results are guidance about the stored entries, not a security certification. There is no overall score. Phase 4 adds a separately labelled optional Claude guidance panel: only a consented, configured debug request sends four aggregate counts; release remote guidance remains disabled. See `docs/SECURITY_ADVISOR.md` for the exact boundary.

## Checks and counts

- **Weak passwords:** conservatively flag fewer than 12 UTF-16 code units, the existing `PasswordStrength.WEAK` classification, a small explicit common-password list, or repetition of a motif of 1–4 code units. The common list is `password`, `password123`, `password123!`, `qwerty123456`, `123456789012`. This is a heuristic, not estimated cracking time/entropy, a comprehensive dictionary, or a breach database. A longer passphrase can still be predictable. The existing strength badge has its older thresholds, so the center can flag a short password whose badge is medium/strong.
- **Reuse:** exact, case-sensitive, untrimmed password equality shared by at least two account identities. Account identity here means service trimmed/lowercased with `Locale.ROOT`, exact username and exact nullable URL; these are stored labels, not verified website/app bindings. Exact duplicate copies by themselves do not count as cross-account reuse. If another identity shares their password, all copies are affected.
- **Duplicate credentials:** exact equality of service, username, URL, password and nullable TOTP content. ID/creation timestamp are ignored. Different password/URL/TOTP content is never a duplicate. Different serialization of equivalent TOTP configuration is deliberately not merged. Review is manual; the existing developer cleanup SQL is not used.

Counts represent unique persisted entry IDs. Repeated input IDs are ignored after their first occurrence. Each category counts affected entries (both copies of a duplicate count); reused/duplicate group counts are separate. The headline union counts each affected ID once across categories.

Only `password.isEmpty()` means passwordless: TOTP/passkey-only rows are excluded from password checks. Whitespace is an actual password and is never trimmed. Standalone passkeys are not read or analyzed. Age is omitted because `createdAt` is entry creation time, not a reliable password-change timestamp. Missing stored TOTP/passkey does not establish that the service supports it or that the user lacks 2FA elsewhere, so there is no coverage penalty.

## HIBP and network

The breach category explicitly says **not checked / unavailable**; it never shows a zero-breaches success. No remote breach check is performed by this screen. Existing HIBP transport uses HTTPS, a five-character SHA-1 prefix and `Add-Padding: true`, but the response parser treats absent/invalid bodies as clean and the old batch path lacks truthful partial coverage. That implementation is intentionally not connected to the center. The password ViewModel no longer depends on the breach use case: automatic checks on loading and after edits are removed in this phase. The standalone repository/use case remain for a separately consented, tested future integration. Optional Phase 4 guidance is a separate aggregate-only request, not a breach check.

Re-enable breach checks only after strict response validation, explicit per-run consent (prefix/IP/timing disclosure), cancellation, per-entry unknown/error state, coverage and timestamps, and tests with fake transport. Plaintext/full hashes must never be sent. Other app flows still have pre-existing network behavior, including favicons; this phase does not claim the entire application is network-free.

## Architecture, lifetime and performance

`domain/security/LocalSecurityAnalyzer` is stateless and separated from Compose. Results contain only enum types, priority, groups and ID/service/username references. They contain no `Password` objects, passwords, URLs, OTP seeds, passkey keys or fingerprint values. Findings are recomputed from current data rather than persisted or marked permanently resolved.

Comparison maps use HMAC-SHA256 with a fresh random key for each analysis; fields are length-prefixed (including null vs empty), avoiding ambiguous concatenation. Temporary UTF-8 buffers and the raw key array are cleared in `finally`. JVM immutable strings, crypto-provider internal state and source models cannot be guaranteed zeroized; no such guarantee is made. Maps are discarded after the call. No passwords are logged.

The screen ViewModel collects the existing repository only while visible and started. Analysis/decryption run on background dispatchers, use maps rather than all-pairs comparisons, and cooperate with cancellation. DB emissions cancel superseded analysis. Screen stop/navigation/lock clear per-entry results. Returning from edits triggers a fresh read and analysis. The UI lazily renders affected entries with stable IDs and uses existing detail/generator routes and the Phase 2 Material 3 theme.

The repository's process-lifetime decrypted snapshot was removed because it survived lock and could return stale writes. Reads now reject a locked vault before/after decryption, check cancellation between rows, and ciphertext decryption errors fail closed instead of being presented as successful passwords. Domain plaintext values are always encrypted on write, even when a literal password begins with `enc:`. Algorithms, key sizes and stored formats are unchanged; corrupt rows are preserved. A corrupt read produces a retryable error, not a reassuring empty result. Each active list subscriber now decrypts its own DB emission; this cost needs measurement on real large vaults before adding any session cache back.

## Verification

Verified on 2026-10-07, Windows, bundled JBR **25.0.3** (`JAVA_HOME=E:\Android Studio\jbr`). Unit tests use synthetic entries and an injected test KDF; they do not certify AndroidKeyStore, SQLCipher native behavior, biometric/auth integration or real device performance.

Final command (exit **0**, `BUILD SUCCESSFUL`, 2m 5s):

```powershell
$env:JAVA_HOME='E:\Android Studio\jbr'
.\gradlew.bat -I .codex-phase3/build.init.gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:compileDebugAndroidTestKotlin --console=plain
```

| Verification | Result |
| --- | --- |
| JVM XML reports | **163 tests, 21 suites, 0 failures/errors/skipped** |
| Debug APK | Assembled successfully in `.codex-phase3/build/app/outputs/apk/debug/app-debug.apk` |
| Release APK / R8 | Assembled successfully in `.codex-phase3/build/app/outputs/apk/release/app-release.apk`; existing debug signing configuration remains a launch limitation |
| `lintDebug` | **0 errors, 145 warnings**, no blanket baseline/suppression added |
| Compose instrumentation sources | Compiled successfully; **not executed** |
| Visual/device verification | **Not performed**: `adb devices -l` and emulator AVD inventory were empty |
| 10,000-entry analyzer fixture | Passed; final timing is in the XML report and is not a device decryption/UI benchmark |

The original build directory failed on Windows deletion of generated resource directories. A local init script redirects `layout.buildDirectory` for each project to `.codex-phase3/build/<project.name>`; source/build configuration files were not changed. Generated output directories also carried `ReadOnly`; clearing that attribute only inside the new verification output resolved the resource-update failure. Local logs/init script are kept under `.codex-phase3/` and excluded via `.git/info/exclude`. No process was force-stopped and no existing build directory/vault was deleted.

Final read-only review identified a surviving automatic breach request after edits and a non-cooperative repository decrypt loop. Both were fixed: the password ViewModel has no HIBP dependency/callers, and reads check cancellation between rows. The full final suite/build/lint run covers the resulting tree; real traffic inspection remains unperformed.

Coverage added: 10 analyzer tests (including 10,000 rows, exact whitespace/case handling, passwordless rows, group/union counting, no source mutation or secrets in result models, cancellation); 8 ViewModel tests (inactivity, lock/unlock, stopping, reactive edits, read errors/retry, in-flight cancellation and independent auth guard); 5 new repository regressions (locked reads including an empty vault, corrupt ciphertext, stale snapshots and literal `enc:` passwords); one new tampered-GCM-tag regression and replacement of the old test that incorrectly required ciphertext fallback.

Five Compose instrumentation cases cover loading, empty vault, error/retry, no-local-findings with unavailable breach checks, and affected-entry navigation without secret rendering. They require an emulator/device; compilation alone is not an executed UI test.

## Files changed in Phase 3

Paths below are relative to the repository. Existing uncommitted Phase 2 changes are preserved and excluded from this list unless the same file was also extended here.

| Area | Files |
| --- | --- |
| Domain analysis | `app/src/main/java/com/doffi4/doffisecure/domain/security/LocalSecurityAnalyzer.kt`, `SecuritySummary.kt` in the same folder |
| Screen and state | `app/src/main/java/com/doffi4/doffisecure/ui/security/SecurityCenterScreen.kt`, `SecurityCenterViewModel.kt` |
| DI/navigation | `app/src/main/java/com/doffi4/doffisecure/di/AppModule.kt`, `ui/navigation/NavGraph.kt` under the same package root |
| Existing UI integration | `app/src/main/java/com/doffi4/doffisecure/ui/password/PasswordScreen.kt`, `PasswordDetailScreen.kt`, `PasswordViewModel.kt` |
| Safe input boundary | `app/src/main/java/com/doffi4/doffisecure/data/repository/PasswordRepositoryImpl.kt`, `security/PasswordCrypto.kt` under the same package root |
| Localization | `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ru/strings.xml` |
| JVM tests | `app/src/test/java/com/doffi4/doffisecure/LocalSecurityAnalyzerTest.kt`, `SecurityCenterViewModelTest.kt`, `PasswordCryptoTest.kt`, `PasswordRepositoryTotpEncryptionTest.kt` |
| Compose UI tests | `app/src/androidTest/java/com/doffi4/doffisecure/SecurityCenterScreenTest.kt` |
| Documentation | `docs/SECURITY_CENTER.md`, `docs/superpowers/plans/2026-10-07-security-center.md` |

## Phase 4 handoff

Phase 4 implementation now exists as an optional debug-only integration; its privacy contract, verification and production gates are in `docs/SECURITY_ADVISOR.md`. The paragraph below records the original Phase 3 handoff constraints, which still apply before public release.

First close the remaining launch audit Critical/High gates, including integration authorization, migrations, restore/backup, signing and network consent. Phase 3 is not a claim that those are fixed. If an optional advisor is later added, it must be a separate, default-off feature with consent and a reviewable metadata-only payload. The local center must continue to work without it. Do not wire decrypted source lists or secret-bearing objects into an advisor.
