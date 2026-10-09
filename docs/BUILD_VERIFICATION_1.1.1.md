# Decryptum v1.1.1 artifact verification

2026-10-09 · package `com.doffi4.doffisecure` · versionName 1.1.1 · versionCode 8 · minimum SDK 26 · target/compile SDK 37.

- Release APK assembled with R8; debug APK and instrumentation APK assembled.
- Fresh isolated verification: 471 passed checks, 0 failures/errors, 3 intentional skips. App debug/release: 219 declared tests each, 218 passed and 1 opt-in screenshot capture skipped per variant. Contract: 5 passed. Evaluation: 31 declared, 30 passed and the live entry point skipped. The advisor contract, client/prompt/harness privacy regressions passed; the ordinary suite deliberately skips the explicit live-evaluation entry point.
- App debug/release lint: 0 errors, 157 warnings per variant in the candidate verification. Warnings are not treated as proof of production readiness.
- APK v2 signature verified. Certificate: **Android Debug**, SHA-256 `93965360a3a6903851cecddb9370eab62afc269ec107cce9b472190ea99b76fa`, matching the documented v1.1.0 identity. This is not a production signing key or a verified Android upgrade.
- `SHA256SUMS.txt` on the release identifies the exact downloadable APK. Published APK SHA-256: `eef929103769af160661f3bee689cec281b4ecb9c0d63e317c1127038b17ba86`.
- Real Compose screen captures use synthetic repositories/fixtures, Robolectric API 35, EN, dark fallback theme, dynamic color disabled. They do not prove native/device behavior.

No physical Android device was connected. Instrumentation was compiled, not run. Installation/upgrade, SQLCipher/Keystore/KDF, autofill/passkey interoperability, TalkBack and native lifecycle/recovery are still open. No live Claude evaluation or real-user success is claimed. The release advisor is disabled.
