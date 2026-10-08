# Contributing

Start with [product status](docs/PRODUCT.md), [Security](SECURITY.md), [Privacy](PRIVACY.md) and [design context](DESIGN.md). Use synthetic credentials only. For vulnerabilities, follow private disclosure instructions; do not put exploit details, vault exports or keys into issues/PRs. The [v1.1.0 verification record](docs/BUILD_VERIFICATION_1.1.md) includes the isolated debug/release test command.

For an ordinary bug, include revision/build, Android/API/device, steps, expected/actual behavior and reviewed/redacted logs. A feature request should explain the user problem and constraints. Discuss changes to storage, crypto, auth, recovery, networking or outbound advisor data before implementing them. Keep changes focused and follow the existing Compose/ViewModel/use-case/repository boundaries.

## Local checks

Use Android Studio's SDK 37 and a JDK 25 Gradle runtime (Java source target is 11). Configure local `sdk.dir` in ignored `local.properties`; use the checked-in Gradle wrapper:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --console=plain
```

Instrumentation requires a disposable emulator/device: `:app:connectedDebugAndroidTest`. Compilation alone does not execute device tests. Release checks need the explicit release test gate described in [advisor verification](docs/SECURITY_ADVISOR.md); do not ship the current debug-signed release configuration.

For the static site:

```sh
cd website
npm ci
npm run lint
npm run typecheck
npm test
npm run build
npm run check:links
npm run preview
```

Include relevant check results and remaining unknowns in the PR. No screenshots of live credentials, real API keys, generated build directories or unrelated refactors. Contributions are distributed under the repository's [GPLv3 license](LICENSE).
