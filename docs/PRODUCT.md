# Decryptum product

Reviewed against the current development source on 2026-10-07. This is a product brief and roadmap, not a release announcement.

## Problem and users

Android users need a clear place for account passwords and two-factor codes without requiring a hosted vault account. Decryptum is intended for people who prefer local control, Material 3 UI and inspectable source, and who understand that local storage also creates recovery responsibilities. The current tree is best evaluated by contributors with disposable data while release gates remain open.

## Solution and differentiators

A Kotlin/Compose Android vault combines password management, SecureRandom generation, local TOTP and Android autofill. Material 3 dynamic colors on supported devices and a fallback Material Privacy palette keep it native. Local Security Center gives actionable weak/reused/duplicate findings without an AI dependency or an invented safety score. The repository is GPLv3 and exposes both implementation and limitations.

These are implementation characteristics, not claims of unique invention, market traction or superiority over other managers. No account, persistent backend, cloud sync or advertising model is implemented.

## Current state

| Area | Source status | Practical limit |
| --- | --- | --- |
| Vault / generator / TOTP | Implemented; JVM coverage exists | Native device flows, accessibility and performance still need evidence |
| Android autofill | Framework/provider code, inline suggestions and save/choose paths | Auth/recipient boundaries and browser/device compatibility are launch gates |
| Passkeys | Android 14+ software P-256/ES256 implementation | Experimental; not hardware-bound; protocol/caller trust/restore unverified |
| Security Center | Local weak/reused/duplicate analysis | Heuristics; no breach check, 2FA coverage or global safety score |
| Claude advisor | Explicit aggregate-only consented debug transport; release disabled | No default configuration, no live-provider validation, no production credential model |
| Import/export | CSV/JSON parsers and unencrypted CSV export | Not portable encrypted backup; complete TOTP/passkey recovery unverified |
| Platforms/locales | Android 8+; provider needs Android 14+; app EN/RU | No iOS, web vault, desktop app or Ukrainian app strings |
| Website | [Public GitHub Pages site](https://doffi4.github.io/Decryptum/) and source-derived documentation | Static presentation, not a vault client; custom domain/mailbox not configured |

The public prerelease uses `versionName` 1.1.0 / `versionCode` 7, published 2026-10-08. Existing v1.0.1 APKs predate this work. Signing still uses Android Debug identity, matching the published v1.0.1 release certificate. Native upgrade/data retention and public signing policy remain unverified. Password-cache/corrupt-read, several mandatory-auth fallbacks, migration replacement/version and DB-key persistence paths were repaired. Integration/recipient trust, native migration, recovery and signing gates remain open. See [release notes](RELEASE_NOTES_1.1.md) and [known limitations](../SECURITY.md#known-limitations).

## Roadmap

1. **Release fundamentals:** reproduce and close lock/fresh-auth/recipient failures, make DB migration crash-safe, validate schema upgrades, passkey protocol/caller trust and import/edit safety. Review developer actions and network/screen/log policy.
2. **Recovery and evidence:** design and test a portable encrypted recovery format; verify TOTP-only and standalone passkey restore, wrong-password/corruption behavior, and device-loss instructions. Capture new synthetic screenshots only from a tested current build.
3. **Release candidate:** reproducible builds, real signing key/certificate continuity, version bump, device/API/browser/accessibility matrix, artifact checksums and a recorded go/no-go. Broader production readiness remains pending.
4. **Careful improvements:** better import diagnostics; explicit, truthful, consented HIBP checks after transport/result fixes; large-vault profiling. Production advisor is optional and deferred until a credential/privacy model is reviewed.
5. **Future exploration only:** encrypted cross-device sync, browser/desktop work and additional localization. None is implemented or scheduled. Sync must follow a threat model and recovery design; it is not implemented by the website.

No delivery dates, revenue, funding, download counts, customer testimonials or security certifications are asserted.
