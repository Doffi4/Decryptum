# Security

This document describes the **current development source**, reviewed on 2026-10-07. Published APKs may differ. It is not an independent audit, certification or promise that this project is ready for sole custody of important credentials. See [current product status](docs/PRODUCT.md), [privacy](PRIVACY.md) and [threat model](docs/THREAT_MODEL.md).

## Security philosophy

Keep the core vault local, use established cryptographic primitives, minimize outbound data, require authorization at every sensitive entry point, and make unknown/error states visible. Security Center findings and AI guidance are advisory; neither proves an account is safe. Source availability helps inspection but is not security assurance.

## Supported versions

| Version | Security maintenance status |
| --- | --- |
| v1.1.0 prerelease (`versionCode` 7) | Published 2026-10-08; production-readiness limitations remain. See the [release notes](docs/RELEASE_NOTES_1.1.md) |
| Published releases | No guaranteed security support window or verified backport policy has been established |

Report the exact tag/commit, APK checksum and signing certificate where possible. No version is described here as independently audited or production certified.

## Reporting a vulnerability

A working private disclosure mailbox has **not** been configured or verified. Check the repository's [Security page](https://github.com/Doffi4/Decryptum/security) for an owner-enabled private reporting option before sending sensitive details. Its availability is not guaranteed by this document. If none exists, open a minimal [issue](https://github.com/Doffi4/Decryptum/issues) asking for a private contact, without exploit details, credential data or user exports.

Privately provide affected revision/device/API level, impact, minimal reproduction with synthetic data, expected behavior and any proposed fix. Never send a live vault, passwords, TOTP seeds, private keys or provider keys. Coordinate publication with the maintainer so affected users can be warned and fixes reviewed. There is no promised response SLA, bounty or mailbox. The owner must configure a private channel before wider launch.

## Data storage and local encryption

- Room stores account/service labels, usernames, URLs, timestamps, encrypted password/TOTP fields and passkey records in a SQLCipher database (`password_db`) in app-private storage. SQLCipher uses a random 32-byte database key.
- `DatabaseKeyManager` wraps that database key with an AES-256-GCM Android Keystore key and stores the wrapped blob/IV in ordinary private SharedPreferences. This wrapper does **not** require user authentication. The database key is cached in the process and is separate from the master password.
- `PasswordCrypto` uses a random 256-bit data encryption key (DEK) for AES-GCM secret fields and private passkey material: 12-byte IV, 128-bit authentication tag. The DEK is wrapped using an Argon2id master-password-derived key: 32,768 KiB memory, 2 iterations, parallelism 2, 16-byte salt. These parameters are implementation facts, not a measured resistance guarantee.
- Account metadata relies on database encryption, not the extra password-field layer. Settings and wrapped-key blobs live in private preferences; the project does not use EncryptedSharedPreferences merely because the library is a dependency.
- Current password repository reads require an unlocked vault before/after decryption. The process-lifetime decrypted snapshot was removed, and corrupt field decryption fails with an error. This does not establish correctness of every integration or historical migration path.

The app holds decrypted values in memory while in use. Clearing key references is not guaranteed erasure of JVM strings, UI objects or crypto-provider buffers. A weak master password weakens protection; the current setup UI permits very short passwords. Device compromise can defeat app-level encryption.

## Key management and biometrics

The master password unwraps the vault DEK; it does not directly derive the SQLCipher key. The current biometric slot uses an Android Keystore **RSA-OAEP** keypair with an authentication-bound private key. A successful `BiometricPrompt.CryptoObject` flow unwraps the same DEK. Biometrics are an alternate unlock path, not a new encryption layer or recovery method. Hardware backing/StrongBox is not established for every device. Some older integration flows use a different authentication path and remain release gates.

Passkeys are software-generated P-256/ES256 keys. Their PKCS8 private bytes are encrypted with the vault DEK and stored in the database. They are **exportable**, not hardware-bound Android Keystore ECDSA keys. Android 14+ Credential Provider code exists; WebAuthn interoperability, caller/origin trust and recovery must be verified before a broader passkey claim.

## Backup and recovery

Android automatic backup is disabled in the manifest; extraction rules exclude database, preferences and files for cloud backup/device transfer. This is not an independent recovery solution, and OEM/OS behavior still needs device validation.

CSV export is **unencrypted**. It can contain passwords, TOTP configuration/seeds and Base64-encoded private passkey bytes. Base64 is not encryption. The existing passkey export can fall back to encrypted bytes after a decrypt error; therefore an apparently successful export is not proof of a portable, complete backup. CSV/JSON import parsing exists, but full migration/restore of all record types is not established. Choose export destinations carefully: a document provider can be cloud-backed. Keystore loss, uninstall, device loss or a forgotten master password can make data unrecoverable. There is no operator reset or escrow service.

## Clipboard, screenshots and logs

`SecureClipboard` marks copies sensitive on Android 13+ and schedules best-effort clearing after about 30 seconds using a coroutine and an alarm. OS/OEM restrictions and process death can delay or prevent clearing; another app may read a copied value before it is cleared.

`MainActivity` applies `FLAG_SECURE` when screenshots are disallowed (the default). Other sensitive activities do not consistently apply the same policy. This cannot stop an external camera, compromised OS or all accessibility-based observation. Existing Android logs can contain service labels, import URIs and exception context; do not publish raw logs without reviewing/redacting them. The advisor adds no request-body/header logging or telemetry.

## Network behavior

The app declares INTERNET permission. Core vault, generator, TOTP and local analysis can operate offline; the entire app is not guaranteed network-free. Favicons use Google, then DuckDuckGo, then direct site requests with possible apex-domain fallback and redirects. Requests can disclose service domains, IP and timing. Icons are locally cached. The settings toggle does not cover every existing caller.

HIBP Pwned Passwords transport exists: HTTPS, five hex characters of a SHA-1 hash and `Add-Padding: true`; suffix matching occurs locally. Current password loading/edit flows no longer call it, and Security Center reports breaches as **not checked/unavailable**. No active remote breach-check feature is promised. Strict error/response validation, consent and coverage must be added before reconnecting it; prefix/IP/timing would still leave the device.

## Security Center

The center checks a limited weakness heuristic, exact reuse across stored account identities and exact duplicates, locally on an unlocked vault. It does not change/delete records, certify entropy, verify recipient identity, perform breach detection, establish 2FA coverage or analyze standalone passkeys. Findings clear on stop/lock and are recomputed as data changes. See [check definitions](docs/SECURITY_CENTER.md).

## AI privacy boundary

Claude Security Advisor is **debug-only, unavailable without runtime developer configuration and disabled in release**. Each request requires fresh consent and shows the exact JSON. Its only vault-derived outbound fields are `password_entry_count`, `weak_password_count`, `reused_password_count`, `duplicate_credential_count`.

No passwords, password hashes/fingerprints, usernames, service names, domains/URLs, TOTP secrets/codes, passkey private keys or vault/master/DB keys enter that request. The HTTPS request also contains fixed instructions, EN/RU response language, model/schema/token limit, and a developer API key. Anthropic receives IP/timing/transport metadata. Counts may themselves be sensitive. Requests are bounded/cancellable; redirects/retries are disabled. Responses are untrusted plain text, with no tools, automatic links or vault actions. See [exact contract and production gates](docs/SECURITY_ADVISOR.md). No provider retention/anonymity promise is made.

## Known limitations

The following areas still require investigation and device evidence:

- Fresh authorization and lifecycle clearing across autofill/credential/export entry points; heuristic matching does not establish a trusted recipient.
- Plaintext-to-SQLCipher migration now preserves the source until atomic replacement, checkpoints WAL, preserves schema version and checks the reopened export. JVM replacement/key-persistence regressions pass; native crash/low-space, historical schema upgrade and rollback evidence remains outstanding.
- Complete encrypted recovery and tested passkey/TOTP-only restore; import/edit handling and developer tools need review.
- WebAuthn protocol/caller trust, native SQLCipher/Keystore/biometric behavior and hardware properties on real devices.
- Consistent screen/log/clipboard policy and complete network consent/toggle behavior.
- Release signing uses Android Debug identity. Its certificate matches the public v1.0.1 release APK, but native upgrade/data retention and production signing/key custody are outstanding. See [v1.1.0 artifact verification](docs/BUILD_VERIFICATION_1.1.md).

Root, a malicious OS, code executing inside an unlocked process, physical observation and compromised input/accessibility/clipboard software can exceed the app's protection. These are realistic limits, not permission to ignore reachable authorization bugs. Critical data loss or unauthorized secret access, recipient-trust failures, secret-bearing outbound requests and unsafe recovery are reportable. No known gap is declared an accepted risk or excluded from review by this document.

## Source evidence

Primary implementation paths under `app/src/main/java/com/doffi4/doffisecure/`: `security/{PasswordCrypto,DatabaseKeyManager,SecureClipboard,CsvManager,FaviconFetcher}.kt`, `data/local/database/{AppDatabase,DatabaseMigrator}.kt`, `data/repository/PasswordRepositoryImpl.kt`, `security/webauthn/WebAuthnCryptoEngine.kt`, `autofill/`, `domain/security/`, `domain/advisor/` and `data/advisor/`. Release policy is `app/src/release/.../AdvisorServiceFactory.kt`; manifest and extraction rules are under `app/src/main/`.
