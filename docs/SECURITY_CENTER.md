# Local Security Center

The local Security Center analyzes the unlocked vault on this device. Local analysis works offline, uses no AI/client/API keys, does not request favicons, and does not change or delete credentials. Results are guidance about the stored entries, not a security certification. There is no overall score. The app includes a separately labelled optional Claude guidance panel: only a consented, configured debug request sends four aggregate counts; release remote guidance remains disabled. See `docs/SECURITY_ADVISOR.md` for the exact boundary.

## Checks and counts

- **Weak passwords:** conservatively flag fewer than 12 UTF-16 code units, the existing `PasswordStrength.WEAK` classification, a small explicit common-password list, or repetition of a motif of 1–4 code units. The common list is `password`, `password123`, `password123!`, `qwerty123456`, `123456789012`. This is a heuristic, not estimated cracking time/entropy, a comprehensive dictionary, or a breach database. A longer passphrase can still be predictable. The existing strength badge has its older thresholds, so the center can flag a short password whose badge is medium/strong.
- **Reuse:** exact, case-sensitive, untrimmed password equality shared by at least two account identities. Account identity here means service trimmed/lowercased with `Locale.ROOT`, exact username and exact nullable URL; these are stored labels, not verified website/app bindings. Exact duplicate copies by themselves do not count as cross-account reuse. If another identity shares their password, all copies are affected.
- **Duplicate credentials:** exact equality of service, username, URL, password and nullable TOTP content. ID/creation timestamp are ignored. Different password/URL/TOTP content is never a duplicate. Different serialization of equivalent TOTP configuration is deliberately not merged. Review is manual; the existing developer cleanup SQL is not used.

Counts represent unique persisted entry IDs. Repeated input IDs are ignored after their first occurrence. Each category counts affected entries (both copies of a duplicate count); reused/duplicate group counts are separate. The headline union counts each affected ID once across categories.

Only `password.isEmpty()` means passwordless: TOTP/passkey-only rows are excluded from password checks. Whitespace is an actual password and is never trimmed. Standalone passkeys are not read or analyzed. Age is omitted because `createdAt` is entry creation time, not a reliable password-change timestamp. Missing stored TOTP/passkey does not establish that the service supports it or that the user lacks 2FA elsewhere, so there is no coverage penalty.

## HIBP and network

The breach category explicitly says **not checked / unavailable**; it never shows a zero-breaches success. No remote breach check is performed by this screen. Existing HIBP transport uses HTTPS, a five-character SHA-1 prefix and `Add-Padding: true`, but the response parser treats absent/invalid bodies as clean and the old batch path lacks truthful partial coverage. That implementation is intentionally not connected to the center. The password ViewModel no longer depends on the breach use case: automatic checks on loading and after edits are removed in the current implementation. The standalone repository/use case remain for a separately consented, tested future integration. Optional advisor guidance is a separate aggregate-only request, not a breach check.

Re-enable breach checks only after strict response validation, explicit per-run consent (prefix/IP/timing disclosure), cancellation, per-entry unknown/error state, coverage and timestamps, and tests with fake transport. Plaintext/full hashes must never be sent. Other app flows still have pre-existing network behavior, including favicons; local analysis does not imply the entire application is network-free.

## Architecture, lifetime and performance

`domain/security/LocalSecurityAnalyzer` is stateless and separated from Compose. Results contain only enum types, priority, groups and ID/service/username references. They contain no `Password` objects, passwords, URLs, OTP seeds, passkey keys or fingerprint values. Findings are recomputed from current data rather than persisted or marked permanently resolved.

Comparison maps use HMAC-SHA256 with a fresh random key for each analysis; fields are length-prefixed (including null vs empty), avoiding ambiguous concatenation. Temporary UTF-8 buffers and the raw key array are cleared in `finally`. JVM immutable strings, crypto-provider internal state and source models cannot be guaranteed zeroized; no such guarantee is made. Maps are discarded after the call. No passwords are logged.

The screen ViewModel collects the existing repository only while visible and started. Analysis/decryption run on background dispatchers, use maps rather than all-pairs comparisons, and cooperate with cancellation. DB emissions cancel superseded analysis. Screen stop/navigation/lock clear per-entry results. Returning from edits triggers a fresh read and analysis. The UI lazily renders affected entries with stable IDs and uses existing detail/generator routes and the app Material 3 theme.

The repository's process-lifetime decrypted snapshot was removed because it survived lock and could return stale writes. Reads now reject a locked vault before/after decryption, check cancellation between rows, and ciphertext decryption errors fail closed instead of being presented as successful passwords. Domain plaintext values are always encrypted on write, even when a literal password begins with `enc:`. Algorithms, key sizes and stored formats are unchanged; corrupt rows are preserved. A corrupt read produces a retryable error, not a reassuring empty result. Each active list subscriber now decrypts its own DB emission; this cost needs measurement on real large vaults before adding any session cache back.

## Verification

JVM tests cover local analysis, locked/corrupt repository reads and Security Center state changes. Compose instrumentation tests are provided but have not been executed on a device. Native storage, biometrics, lifecycle and large-vault performance still need device validation. See [release verification](BUILD_VERIFICATION_1.1.md) and [known limitations](../SECURITY.md#known-limitations).
