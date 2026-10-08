# Phase 5 — public presentation evidence

Historical work record from 2026-10-07. Repository and v1.1.0 prerelease publication were subsequently authorized on 2026-10-08; see the [current release record](BUILD_VERIFICATION_1.1.md). Website hosting remains deferred.

Work date: 2026-10-07. Scope: current local source, retaining pre-existing Android/Phase 2–4 changes. No core cryptography or app behavior was modified in this phase. No commit, push, release publication, hosting deployment, domain purchase/DNS update or real-provider request was made.

## Stack and artifacts

`website/` is a lightweight static site: Node 22+ build, Marked at build time, plain semantic HTML/CSS and a small theme script. TypeScript checks the authored JS; deployment-path regressions use Node's test runner; axe-core is a development verification dependency. No persistent backend/runtime framework is needed.

Landing sections: hero, existing product image, features, privacy/network/exports, encryption overview, local Security Center, debug-only Claude boundary, open source, roadmap, FAQ and footer. Security/Privacy/Threat Model/Product/Center/Advisor/Contributing pages render maintained repository Markdown. Metadata/favicon/social asset/robots are prepared. Empty origin defaults to noindex and omits invented canonical/OG URLs/sitemap; configured HTTPS origin/base path enables them. All assets are local. No analytics/remote fonts/tracking pixels/session recording. Optional localStorage stores theme only.

Root README EN/RU/UK now leads with purpose, explicit development status, platform/locales, genuine implemented features, historical screenshots, download/build path, security summary, roadmap/contribution/disclosure/license. `SECURITY.md`, `PRIVACY.md`, `THREAT_MODEL.md`, `PRODUCT.md`, `CONTRIBUTING.md` and small issue/PR templates cover real boundaries without enterprise process. No automated deploy workflow was added; Android CI/toolchain expansion is deferred to release verification.

## Claim reconciliation

| Public statement | Evidence | Boundary kept visible |
| --- | --- | --- |
| Local-first Android vault, generator/TOTP | Repositories/use cases, TOTP/QR/parser code, manifest | Core offline is not total network isolation |
| Android 8+, EN/RU; provider 14+ | app Gradle minSdk 26, locale XML, provider API | No Ukrainian app strings, iOS/desktop/web vault/sync |
| DB encryption plus secret-field encryption | SQLCipher DI, DatabaseKeyManager, PasswordCrypto | DB key independent of master; biometric RSA-OAEP; hardware backing not universal |
| Local weak/reused/duplicate checks | LocalSecurityAnalyzer and center VM/screen | No score/entropy certificate/breach/2FA/passkey coverage |
| HIBP retained but disconnected | Password VM lacks caller, center unavailable state | No shipped active breach-check claim; prefix metadata if reconnected |
| Optional advisor sends four counts | AdvisorSummary/Sanitizer, explicit codec, service/controller | Debug configured/consented only; release disabled; IP/timing and sensitive counts |
| Software P-256 passkeys | WebAuthn engine, repository, provider activities | Exportable; no hardware-bound or verified WebAuthn/interoperability claim |
| No bundled telemetry found | Reviewed source/dependencies | Android local logs and host/provider behavior are separate |
| CSV readable; backup disabled | CsvManager, manifest/extraction rules | No protected/complete portable recovery promise |

Avoided: independent audit/certification, zero knowledge, unbreakable/military-grade/bank-grade language, hardware-backed ECDSA, Ed25519 support inferred from a comment, CBOR decoding, universal browser support, secure backup/complete restore, live provider success, current screenshots, production signing, invented domain/mailbox or company/users/downloads/testimonials.

## Images and Stitch

Reused only the existing vault/TOTP PNGs, optimized without changing their contents to WebP, and the app launcher icon. Exact historical capture build/device/account provenance is unverified; no password/seed/private key is visible in these two selected images. They are visibly labelled earlier interface images. No device/AVD was available (`adb devices -l` and `emulator -list-avds` empty). No new screen capture or instrumentation execution is claimed. Current Security Center explanation is ordinary webpage content, not a fabricated app screenshot.

Stitch visual exploration reused the existing Decryptum Material Design System project `12084559142297124082`, generated desktop screen `c520b99700864484af32c94aca1adaf0`. Its generated copy falsely inferred screenshot version/real provenance, Ed25519, entropy and complete favicon-setting enforcement. These were rejected; generated screen/code was not used as implementation evidence or a real app preview. Current Android `Color.kt` overrides the stale Stitch purple theme. Architecture review was sequential because the requested fresh-context reviewer failed due to model capacity.

## Verification

Android check used the existing ignored `.codex-phase4/release-tests.init.gradle` to enable release tests and redirect Windows build output, preserving source/build configuration. Command:

```powershell
$env:JAVA_HOME='E:\Android Studio\jbr'
.\gradlew.bat -I .codex-phase4/release-tests.init.gradle :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:compileDebugAndroidTestKotlin --console=plain
```

Result: exit 0, BUILD SUCCESSFUL in 10 seconds, 121 tasks (2 executed, 119 UP-TO-DATE). The unchanged unit reports contain debug 189 tests/24 suites and release 190/25, zero failures/errors/skips. This run reused those results, not a new clean test execution. Lint report has 0 errors/150 warnings. APK packaging and instrumentation compilation are not device execution. Native DB/Keystore/biometric, live Claude, actual traffic and current Android UX remain unverified.

To replace cached unit evidence with actual execution, an ignored `.codex-phase5/rerun-tests.init.gradle` sets `Test.outputs.upToDateWhen { false }`. The final command adds `-I .codex-phase5/rerun-tests.init.gradle` and runs only `:app:testDebugUnitTest :app:testReleaseUnitTest`. Final result: **exit 0, BUILD SUCCESSFUL in 12s**, both test tasks executed; **189 debug + 190 release tests, 0 failures/errors/skips**. Compile dependencies reused unchanged outputs. The first forced run hit ReadOnly on release result directories; the attribute was cleared only in the resolved verification `test-results`/`reports/tests` subtree, then tests reran successfully. No manual directory deletion/process termination/source workaround. JDK 25 warns about native library access; it does not prove native Android behavior. Logs: `.codex-phase5/android-unit-tests{,-final}.log`.

Website final checks:

| Check | Actual result |
| --- | --- |
| `npm ci --ignore-scripts --no-fund --no-audit` | Lockfile install successful; all dependencies are development-only |
| `npm run lint` | Authored JS syntax/structure and Prettier checks pass |
| `npm run typecheck` | TypeScript checkJs passes |
| `npm test` | 4 deployment-origin/base-path validation tests pass |
| `npm run build` | 8 static pages built, no backend/client framework bundle |
| `npm run check:links` | 157 local links/resources/anchors pass; default noindex/robots behavior verified |
| Configured build test | `SITE_URL=https://owner.example`, `BASE_PATH=/Decryptum/` verifies canonical/OG URLs, robots/sitemap and 157 links; test values then removed and noindex preview rebuilt |
| Repository doc links | 13 maintained documents / 81 local references and image paths exist; no missing target |
| External product links | GitHub repository, releases, Security page, issues and LICENSE each returned HTTP 200; private reporting availability still unverified |
| Premium strict static audit | Exit 0, no findings; not runtime certification |
| Browser axe-core 4.14.0 | All 8 pages in dark/light at 1440×1000: zero automatic WCAG-tag violations after fixes. Landing also checked at 390×844 in both themes. Only remaining incomplete item is color contrast on an aria-hidden decorative arrow; not a full accessibility certification |
| Browser interactions | FAQ opens by Enter with SUMMARY focus; skip link puts focus on main; theme switches and persists; no horizontal page overflow at 320×800 / 390×844 |
| Reduced motion / no JS | Reduced-motion gives `scroll-behavior:auto`; with script execution disabled, headings/content remain, theme button is hidden, native FAQ works |
| Resources/logs | Observed root-page resources are local CSS/theme/icon/vault/TOTP only; both screenshot assets load; no browser warning/error logs in final root check |
| Git whitespace | Changed README/CHANGELOG `git diff --check` passes |

Runtime checks found and fixed non-focusable scrollable code examples, unsupported aria-label on generic containers and main skip-link focus. Table/code regions are keyboard reachable. Browser screenshots and final axe/static reports are retained in ignored `.codex-phase5/`; `desktop-dark-final.jpg`, `desktop-light.jpg`, `mobile-dark.jpg`, `mobile-light.jpg` and `full-desktop.jpg` are website images, not new Android captures. Temporary QA scripts/media/script-disable/viewport overrides were cleared before leaving the local root page open.

## Remaining owner actions

Close [Phase 6 gates](PHASE6_HANDOFF.md), configure a verified private disclosure channel, capture current synthetic screenshots, review final copy, choose/control HTTPS hosting/origin/base path, set any real mailbox, build with configured metadata, upload `website/dist` and validate public routes/headers/robots/social image. No domain availability or mailbox existence is assumed. Deployment instructions are in [website README](../website/README.md).
