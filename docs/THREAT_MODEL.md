# Decryptum threat model

Current development source reviewed 2026-10-07. Scope: Android app, its local storage and integrations, optional debug advisor, manual exports and the static presentation site. This is an engineering model of risks and controls, not a completed vulnerability audit or evidence that scenarios were exploited. The fresh-context reviewer was unavailable, so source-boundary review was performed sequentially, without an independent second review.

## Overview

Decryptum is a local-first Android vault/TOTP/generator with Autofill and Android 14+ Credential Provider entry points. Compose ViewModels/use cases read repositories backed by Room/SQLCipher. The field DEK is unlocked by a master-password-derived key or a supported biometric cipher. A separate Keystore-wrapped key opens the database. The local analyzer sees unlocked password models and produces local references/counts. The advisor receives a four-integer DTO, not vault objects. There is no hosted vault or sync backend.

| Component | Source (under `app/src/main/java/com/doffi4/doffisecure/`) | Role |
| --- | --- | --- |
| Startup/storage | `DecryptumApplication.kt`, `di/AppModule.kt`, `data/local/database/` | Native library, DI, database open/migration |
| Key/lock control | `security/PasswordCrypto.kt`, `DatabaseKeyManager.kt`, `AppLockManager.kt` | Distinct DB and field keys, unlock and lifetime |
| Vault repositories | `data/repository/PasswordRepositoryImpl.kt`, `PasskeyRepositoryImpl.kt` | Read/write and secret decryption |
| External entry points | `autofill/`, manifest | Framework binding, chooser/auth/save/assertion activities |
| Passkey crypto | `security/webauthn/WebAuthnCryptoEngine.kt` | Software P-256 key generation and WebAuthn structures |
| Export/import | `security/CsvManager.kt`, `ui/password/SettingsViewModel.kt` | Document-provider data transfer |
| Local analysis | `domain/security/`, `ui/security/` | Local heuristics and lifecycle-cleared findings |
| Remote advisor | `domain/advisor/`, `data/advisor/`, debug/release factories | Allowlist, consent, fixed HTTPS transport; release disabled |
| Other exposure | `security/FaviconFetcher.kt`, `SecureClipboard.kt`, `MainActivity.kt` | Icon networking/cache, clipboard and screen policy |

Effective resources and independently enforced boundaries:

| Workflow | Resource/capability | Configuration/precedence | Effective location/value | Readers/writers/recipients | Control / uncertainty |
| --- | --- | --- | --- | --- | --- |
| Database open | Random 32-byte SQLCipher key | `DatabaseKeyManager`; independent of master password | Wrapped blob/IV in private prefs; process cache | App/Keystore/SQLCipher | AES-GCM Keystore wrapper without user-auth requirement; root/app-process compromise exceeds sandbox |
| Modern vault unlock | 256-bit field DEK | Master Argon2id wrapper or biometric RSA-OAEP wrapper | Wrapped prefs; unwrapped key in RAM | Authorized app flow | Biometric CryptoObject in main flow; integration variants need fresh-auth verification |
| Passkey sign/export | Software private key | WebAuthn engine → DEK encryption → repository | Encrypted PKCS8 in DB; may be readable in CSV | App, selected RP/app, chosen export destination | Software/exportable; caller/origin binding and protocol interoperability unverified |
| Debug advisor | One aggregate request | Debug factory reads runtime private file only after consent | `files/claude-advisor.properties`; fixed `https://api.anthropic.com/v1/messages` | Anthropic; caller holds developer key | Four-Int DTO, explicit writer, no redirects/retries, bounded/cancellable response |
| Release advisor | No remote capability | Release factory constructs disabled service | No config read or remote call | None through this feature | Release gate unit test; deployment/provider enablement would change model |
| Icons | Domain fetch/cache | Caller `enabled`; setting not propagated everywhere | Google → DuckDuckGo → direct/apex; local caches | Providers, sites, possible redirect recipients | HTTPS; metadata disclosure persists; no comprehensive network-off control |
| HIBP | Retained range-query transport | Current UI/ViewModel no longer invokes it | Five-hex SHA-1 prefix if reconnected | HIBP | Current center not checked; validation/consent/coverage gate before reconnection |
| Manual export | Unencrypted credential file | User-selected SAF URI | Local or cloud-backed document provider | User/provider/any file reader | No independent encryption; success does not prove complete restore |
| Landing site | Static HTML/CSS/assets | Empty site URL = noindex preview; explicit owner domain later | `website/dist` | Browser/selected host; GitHub on navigation | No analytics/backend/remote fonts; theme localStorage only; host logging unknown |

## Assets, trust boundaries and assumptions

Protect vault passwords, account identities, TOTP seeds, private passkeys, DB/DEK/wrapping material, integrity of records/migrations, and exported copies. Availability and recoverability matter alongside confidentiality. A leaked TOTP seed/private passkey is not fixed by clearing an expired displayed code.

Trust boundaries include: device/OS sandbox ↔ app-private files; master/biometric authorization ↔ DEK access; locked ↔ unlocked session and every framework entry point; caller app/site ↔ selected credential; database ↔ import/export/document provider; decrypted source models ↔ local findings ↔ sanitized advisor DTO; network ↔ untrusted responses; maintained source ↔ signed APK/website artifact.

Expected invariants: secrets require current authorization, lock/stop clears sensitive session state, corrupted encrypted input fails visibly, migration never discards the last usable source, credential recipients are validated, network consent/payload matches user disclosure, AI cannot read identities/secrets or mutate the vault, and release artifacts match reviewed source/signing identity. These are goals; existing controls do not establish all of them.

Assume ordinary Android sandbox and Keystore behavior for the baseline attacker. A malicious local app initially lacks the app UID/private filesystem and must use exposed framework/user-authorized surfaces. A filesystem thief does not automatically have live Keystore execution. Root/OS and injected unlocked-process attackers have stronger capabilities and can observe keys/plaintext. A remote provider initially sees only data actually sent; no remote vault-read endpoint is implemented. A network attacker can deny service and observe metadata, but HTTPS limits ordinary transport tampering absent compromised trust/configuration.

No tenant/admin/hosted account model exists. No claim of universally hardware-backed keys, guaranteed zeroization, encrypted portable recovery, proven WebAuthn compliance, independent audit or complete network isolation is made. Historical legacy formats/native migration, actual hardware properties, OEM behavior, accessibility, device/browser compatibility and full restore need verification. [SECURITY.md](../SECURITY.md) lists known gaps; none is a blanket accepted-risk exclusion.

## Attacker stories and mitigations

Priorities below guide investigation, not confirmed vulnerability severity. Source anchors refer to actual consumers; the [launch audit](LAUNCH_AUDIT.md) records earlier findings and later phase docs record some fixes.

| Priority | Scenario and capability gain | Prerequisites / impact | Existing controls | Required mitigation/evidence | Source |
| --- | --- | --- | --- | --- | --- |
| Critical gate | Migration failure loses the last usable vault | Legacy plaintext DB; crash/rename failure after deletion; availability loss | Temporary encrypted export exists | Preserve source until validated atomic replacement/rollback; crash/low-space/upgrade fixtures | `data/local/database/DatabaseMigrator.kt` delete/rename sequence |
| High | Stolen locked phone leaks secret material | File access or bypassed integration authorization; account takeover | DB encryption, modern DEK wrapper, guarded password reads, lock | Verify every auth entry point and legacy path, rate limits and snapshot cleanup; OS lock remains essential | `PasswordCrypto`, `DatabaseKeyManager`, repositories, `autofill/` |
| High | Stolen unlocked phone enables copying/export/signing | App already unlocked; user authority is available | Auto-lock timing and optional integration auth | Minimize unlocked exposure and test fresh-auth/export paths; do not promise protection after authorized unlock | `AppLockManager`, `SettingsViewModel`, auth activities |
| High | Malicious local app receives the wrong credential | Framework selection, heuristic recipient match or incorrect caller/origin binding | Bind permissions, non-exported helper activities, user selection | Validate package/certificate/origin/RP binding; adversarial device/browser tests; consider capability gate | Manifest, `AutofillMatcher`, provider/save/auth activities |
| High | Rooted/compromised OS reads active keys/plaintext | Privileged filesystem/process/input access | Encryption at rest reduces a narrower offline-file threat | Explicitly outside full protection guarantee; minimize plaintext lifetime, test ordinary boundaries separately | Key managers, repositories, UI |
| High | Filesystem theft exposes metadata or enables password attack | Copy of app files; Keystore availability varies | SQLCipher, wrapped field/DB keys | Verify device hardware/legacy behavior and KDF performance; inspect favicon caches/settings exposure | `DatabaseKeyManager`, `PasswordCrypto`, `FaviconFetcher` |
| High | Compromised export exposes all exported credentials | Reader of unencrypted SAF export/provider copy | User chooses destination | Prominent plaintext boundary; implement/test independent encrypted recovery later; verify complete round trip | `CsvManager.export`, `encodePasskeyFields` |
| Medium/High | Clipboard observer copies a secret before clearing | OS-permitted clipboard observation or compromised keyboard/accessibility | Sensitive flag and best-effort ~30s cleanup | Test OS/OEM behavior; avoid unnecessary copies; no guarantee against an observer | `SecureClipboard`, clipboard receivers |
| Medium/High | Screenshot/log observer obtains sensitive context | Other activities, diagnostic logs, accessibility or external camera | MainActivity default FLAG_SECURE | Shared sensitive-screen/log policy; redaction/release checks and real-device tests | `MainActivity`, `autofill/`, existing logs |
| Medium | Network attacker/provider learns service metadata or blocks requests | Icon resolution; HIBP only if reconnected; configured debug advisor | HTTPS, icon cache; advisor time/size limits | Complete caller consent/toggle propagation, strict parsing, traffic capture; no automatic reassuring success | `FaviconFetcher`, `PwnedPasswordsRepository`, advisor transport |
| Medium | Compromised remote AI gives malicious advice or attempts data extraction | Consented configured debug request | No vault dependency, four counts only, no tools/writes/clickable generated links, strict response shape, release disabled | Keep guidance untrusted/manual; transport/lifecycle tests; changing payload/auth model requires review | DTO, codec/service/controller/panel, release factory |
| High | Compromised build/distribution swaps an artifact | Dependency/build/signing or hosting access | Source/lockfile/wrapper hash; static build | Real release key, certificate continuity, checksums, reproducibility and reviewed provenance | `app/build.gradle.kts`, Gradle wrapper, website build |

## Severity calibration

Critical: reachable loss of the only usable vault or unauthorized broad secret/key access. High: recipient-trust/auth bypass, leaked recovery/TOTP/passkey material, unrecoverable upgrades, or unsafe artifacts with concrete exposure. Medium: conditional metadata exposure, incomplete screen/clipboard policy, misleading error/coverage state or untrusted guidance with a plausible consequence. Low: narrowly scoped hardening with no established new privilege or sensitive asset loss.

An already-rooted attacker having privileged access is not itself a new app vulnerability, but reachable ordinary-privilege failures remain reportable. Unknown runtime behavior must stay unknown until tested; source tests alone do not certify AndroidKeyStore, native SQLCipher or framework caller trust.
