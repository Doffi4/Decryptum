# Decryptum 1.1.0 — launch checklist

Updated 2026-10-08. **Public prerelease: authorized after explicit owner approval of versionCode 7. Production readiness: BLOCKING. Startup application: draft only.** The exact reviewed APK is published as v1.1.0; APK assembly and owner review do not close native security/recovery gates. Repository publication is authorized. Website deployment, DNS/domain changes and application submission remain deferred.

## GO

These are individual verified checks, not overall launch approval.

- Source version is **1.1.0 / versionCode 7**, a compatible minor-version draft relative to published 1.0.1/5. Application ID unchanged.
- Local Security Center and Material 3 polish implemented. Definitions remain heuristic; breaches, password age, full 2FA/standalone-passkey coverage and global security score are unavailable.
- Advisor privacy boundary source-reviewed and JVM tested: four explicit integer counts, fresh consent/exact preview, cancellation/auth checks, bounded untrusted responses, no vault tools. Release factory disabled; no production key provisioned or live Anthropic request performed.
- Website clean install/build/lint/typecheck passed; 4 tests and **159** local links passed after policy/product updates. Eight static pages; preview remains noindex. No deployment.
- Earlier candidate 1 (versionCode 6) Android build into a fresh directory passed in **2m 51s**, **146 tasks executed / 2 up-to-date**: **196 debug / 197 release JVM tests**, zero failure/error/skip, APK/R8 and instrumentation assembly, lint **0 errors / 150 warnings** per variant. Do not infer native execution from compilation.
- Public v1.0.1 release and local candidate both verify with certificate SHA-256 `93965360a3a6903851cecddb9370eab62afc269ec107cce9b472190ea99b76fa`. This is Android Debug identity; actual upgrade/data retention is untested.
- Critical migration replacement/key-persistence triggers and cached-DEK fresh-verification failure were reproduced in JVM regressions before repairs. After repairs focused/full JVM tests pass. Native validation remains blocked.
- No real API/private signing key or analytics SDK was found in reviewed changed production paths; placeholder/synthetic strings were identified. This is scoped source inspection, not an exhaustive history/dependency audit.
- Release notes/application drafts and current limitations are documented; screenshots are explicitly labelled historical, not fabricated current-device captures.

## NEEDS OWNER ACTION

- Owner review/publication approval was received on 2026-10-08. Continue device testing with disposable data and report behavior. Do not uninstall/reset an existing vault to force installation. Matching certificate is not a tested recovery/upgrade guarantee.
- Confirm public signing identity, private-key custody/backup and any supported rotation plan. Current/old releases use Android Debug identity. Do not enter keys into chat/source/logs.
- Establish a private security disclosure channel and real contact/privacy mailbox.
- Confirm founder/contact/team/legal-entity status, founding/funding dates, registration/residence/operating country, measured traction, prior startup benefits and program eligibility. Fill every `[OWNER INPUT REQUIRED]` in [application draft](STARTUP_APPLICATION.md).
- Choose an owned domain or separately authorize purchase. Approve hosting, publish reviewed static site/policies, configure matching domain email and verify the appropriate Claude Console organization/project settings. No purchase, deployment or DNS action was performed.
- Recheck current official program requirements, review the form and separately authorize submission. No guarantee of acceptance/credits is implied.
- Production release promotion remains pending technical gates. Public prerelease approval is recorded above; it does not establish native security assurance.

## BLOCKING

- **S01 authorization/lifecycle:** repository locked/corrupt-read, fresh master verification and unavailable mandatory-auth fallbacks were repaired in source. Picker uses headers/current reads. Native cold/warm lock, process death, background, biometric fallback, settings enforcement and export freshness across every entry point remain unexecuted. Main password ViewModel still retains plaintext UI models until lifecycle disposal; full clearing is not established.
- **S02 migration/legacy:** source now preserves schema version, checkpoints WAL, checks reopened schema/row counts/integrity, syncs and atomically replaces the source. Failed/partial key persistence fails closed. Two native fixtures test WAL/v2→4 and encrypted idempotence, but cannot run here. Native crash/power loss/low disk/failed export/unsupported atomic move and all historical upgrade/legacy-unlock fixtures remain required. No destructive fallback or invented v1 schema was added.
- **S03/S06 recovery/edit:** CSV remains readable and passkey decrypt fallback/identity omissions, whitespace preservation, TOTP-only/standalone passkey restore and REPLACE-linked edit behavior remain open. No independent encrypted recovery format. Developer duplicate cleanup still operates on incomplete identity criteria. Do not use these as tested recovery or cleanup on important records.
- **S04/S11 recipient/protocol:** passkey caller/origin/RP/challenge/allowCredentials correctness and strict server/browser/device evidence are absent. Heuristic autofill/domain matching does not establish trustworthy recipients. Experimental code is not a compatibility guarantee.
- **S07/S09 network/exposure:** favicon defaults/toggle propagation, metadata/error logs in untouched paths, screen policy across remaining activities and clipboard/background behavior need review/device evidence. No comprehensive network-off/privacy-consent guarantee.
- **S08 breaches:** automatic callers are removed and UI explicitly says not checked. Transport error/schema/coverage/consent verification is required before reconnecting HIBP; this deferred feature is not a positive clean result.
- **S10 release evidence:** public signing/custody decision, Android install/update/native smoke/accessibility/device matrix and private disclosure channel remain open. No attached devices, AVDs or installed system image were found. Compile success is not a pass for these gates.

## Scope reconciliation

| Phase 1 v1.1 item | Current outcome |
| --- | --- |
| Reliable local vault/TOTP | Implemented with stronger locked/corrupt reads; native lifecycle, legacy/recovery gates remain |
| Safe autofill | Mandatory-auth fallbacks repaired; native verification and recipient trust remain |
| Truthful diagnostics | Local weak/reused/duplicate checks; unavailable coverage and HIBP explicitly not checked |
| Verified upgrade/migration/restore | Migration repair/regressions added; native execution and complete restore not established |
| Product/UI/public docs | Material 3 polish, local static site and factual policies/readmes; current device captures deferred |
| Reproducible versioned release | Local 1.1.0 candidate/checks/hash/certificate; public signing policy and runtime evidence remain |
| Claude integration | Debug-only allowlisted prototype; production access/retention/live evaluation intentionally deferred |
| Scope deferred | Sync/backend/accounts, categories, new platforms/locales, production AI, protected portable backup and consented breach checking |

Security-sensitive code changed in this phase: `DatabaseMigrator`, `DatabaseKeyManager`, `PasswordCrypto`, `AutofillPickerActivity`, `CredentialAuthActivity`, `PasskeyAuthActivity`, one repository log and two recovery strings. Version/changelog/readmes/policies/product/build-source links and release/application/checklist docs were updated. Existing Phase 2–5 work was preserved.

## Verification and credibility record

Build-time source baseline: `a74b6a1ab6819593f25ab40cdc5f6f918833fed6` plus the changes now recorded by tag v1.1.0. Toolchain: Gradle wrapper 9.6.0, JBR 25.0.3 from `E:\Android Studio\jbr`, SDK/target 37, minimum 26, Java source target 11. No `.github/workflows` exists; local checks are the available CI-equivalent, not a green hosted CI run.

Fresh build outputs are isolated under ignored `.codex-phase6/`. Release unit tests enabled only by local init script; every Test task forced to execute. Final command: `gradlew.bat -I .codex-phase6/deliver.init.gradle :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest --console=plain`. The script redirects build directories to a fresh tree and enables AGP release unit tests; product build policy is unchanged apart from version/comment.

No-device runtime smoke for onboarding/unlock/add/edit/reveal/copy/TOTP/generator/center/advisor consent/offline/settings is **not executed**. `connectedDebugAndroidTest` was attempted and failed with **No connected devices!**; no UI/native test passed. Real-provider traffic and current screenshots are not performed. Lint warnings are existing resource/plural/dependency/API/style items, not suppressed to make checks pass; two MissingPermission warnings affect best-effort clipboard exact alarms.

Security review covered the changed executable Android/website paths and supporting trust boundaries. The managed diff scan describes the earlier Phase 2–5 snapshot, with an unresolved native locked-picker candidate; historical exported design-source files are not compiled. It is not a certification of the final APK. Separate fresh review of the migration/auth candidate found a sibling Credential Manager auth bypass, subsequently repaired. Native proof remains missing.

As a first-time reviewer: product purpose and local/privacy boundaries are explicit; labelled real earlier screenshots exist; source builds; the previous stable release is v1.0.1 (2026-09-08); v1.1.0 is a public prerelease. Roadmap prioritizes actual gates. No traction, audits, legal company identity or completed production AI integration is invented. Keep the startup material in draft form until owner/contact facts are verified; do not submit a production-ready claim now.

Candidate 1 evidence remains in `exports/release-1.1.0/VERIFICATION.md`. Candidate 2 (versionCode 7) APK, SHA-256, source snapshot and fresh checks are in `exports/release-1.1.0-rc2/VERIFICATION.md` and `SHA256SUMS`. These are retained local build-time records. See [public verification notes](BUILD_VERIFICATION_1.1.md) and the v1.1.0 release assets.

## Candidate 2 follow-up — navigation and password entry

v1.1.0 / versionCode 7 fixes the reported main-tab restoration/overlapping transitions/rectangular ripple paths and replaces only the add-password form with the native Material 3 sheet adapted from a synthetic Stitch proposal. Save waits for persistence, prevents duplicate taps and retains inputs on write failure.

Fresh build/R8/debug+release+instrumentation assembly/lint passed in 3m 4s (153 executed / 4 up-to-date). **207 debug / 208 release tests**, zero failures/errors/skips; lint **0 errors / 151 warnings** per variant. Five real-NavController Robolectric regressions and six Compose/Robolectric state/layout tests passed. Native dark RU and compact light RU at 200% font-scale render previews were inspected locally. Actual phone IME/insets/dynamic color/whole-app transition checks remain pending. Existing native security/recovery blockers remain unchanged.

APK, source snapshot, signature, checksum and logs: `exports/release-1.1.0-rc2/`. The owner approved this exact APK on 2026-10-08 for a public prerelease; no rebuild is performed for publication.
