# v1.1.0 prerelease artifact verification

Publication approved by the owner on 2026-10-08 after reviewing the private versionCode 7 candidate. The public APK is the **same file**, renamed for release; it was not rebuilt or resigned for publication. Source and public documentation are recorded under tag `v1.1.0`.

## Artifact

| Field | Verified value |
| --- | --- |
| File | `Decryptum-v1.1.0.apk` |
| Size | 48,507,636 bytes |
| SHA-256 | `5fe57baa958da5fc48d8c90dcf31fc1cf5862b6a00053a14e17068c142d32c95` |
| Application ID | `com.doffi4.doffisecure` |
| Version | 1.1.0 / versionCode 7 |
| Android | Minimum API 26 / target API 37 |
| APK signature | v2 verified |
| Signing certificate SHA-256 | `93965360a3a6903851cecddb9370eab62afc269ec107cce9b472190ea99b76fa` |

Signing uses the existing **Android Debug identity**, matching the previously checked v1.0.1 and private versionCode 6 artifacts. Certificate continuity is verified; actual installation, upgrade and data retention on Android are not. Production key custody/rotation policy remains unresolved. Do not uninstall or reset an important vault to force installation.

## Build-time evidence (2026-10-07)

Gradle 9.6.0, AGP 9.4, Kotlin 2.2.10, JBR 25.0.3, SDK 37. Isolated full build completed in 3m 4s: 157 tasks, 153 executed and 4 up-to-date. Debug/release APK, R8 and instrumentation APK assembly passed.

- Debug JVM: **207 tests / 27 suites**, zero failures/errors/skips.
- Release JVM: **208 tests / 28 suites**, zero failures/errors/skips.
- Lint: **0 errors / 0 fatal / 151 warnings** per variant. Warnings are retained, including existing clipboard exact-alarm permission warnings; no clean-lint claim is made.
- Instrumentation compiled but **was not executed**: no connected device or configured emulator was available.
- Five real-NavHostController Robolectric regressions and six Compose/Robolectric password-sheet tests passed. A local native-view render used synthetic credentials; it is not a physical-device screenshot.
- A 205-file Android/Gradle working-tree snapshot was hashed after assembly. All snapshot hashes were rechecked before publication. Git line-ending normalization means these working-tree hashes are not a byte-for-byte Git archive or reproducible-build claim.

## Publication checks (2026-10-08)

Debug and release JVM suites were rerun before publication. Website build, lint, typecheck, four build tests and generated-link validation were rerun against the publication documentation. The approved APK checksum was rechecked; APK and checksums are uploaded as release assets, not committed as source.

For equivalent isolated Android checks (choose your installed JDK/SDK paths):

```powershell
$env:JAVA_HOME='E:\Android Studio\jbr'
.\gradlew.bat -I scripts/verification.init.gradle :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest --console=plain
```

Generated output is under ignored `.artifacts/verification/`. Exact binary reproduction is not established. Local publication checks are not hosted CI or an independent audit.

## Remaining limits

Native SQLCipher/Keystore/biometrics/lifecycle, migration crash/low-space behavior, complete encrypted recovery, passkey recipient/protocol interoperability, device UI/accessibility and production signing/disclosure policy remain open. Core local checks do not imply breach verification. Claude Advisor is disabled in release. See [release notes](RELEASE_NOTES_1.1.md), [Security](../SECURITY.md), [Privacy](../PRIVACY.md) and [production-readiness checklist](LAUNCH_CHECKLIST.md).

The release is explicitly a **prerelease**. Website source is included; website hosting/DNS and startup application submission are deferred.
