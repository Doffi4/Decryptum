# Decryptum v1.1.1 — tester prerelease

2026-10-09 · versionCode 8 · Android 8.0+ / API 26+.

This release includes the existing local vault, password generator, TOTP, Android autofill and local Security Center, plus realistic synthetic datasets for the tester round. Use a disposable profile/device and fictional credentials only. Do not uninstall an important vault or bypass platform protection to install it.

## What changed

- Six taps on the app title, then `Heytest08`, open developer tools. The code does not replace vault authentication.
- Create 1–5,000 records across up to 200 familiar services with uneven account counts. The default is 500 / 200; fictional logins differ between batches.
- Review totals and account allocation before adding. Busy/error feedback and the actual saved count distinguish a partial result.
- Separate four-record Security Center and public RFC TOTP fixtures make tester checks repeatable. The Security Center fixture has 4 password entries, 2 weak, 2 reused and 2 duplicate entries; categories overlap.
- Synthetic values use the existing encrypted bulk import; no plaintext CSV file is created by the generator. Favicon loading can still make network requests.
- The debug advisor now has an isolated four-integer contract, standalone synthetic evaluation harness and versioned EN/RU prompts. Its client and prompts are excluded from the release classpath. No live-response quality is claimed.
- Updated repository/site screenshots are renders of real Compose screens and controls with synthetic data; they are not phone captures.

## For testers

Download `Decryptum-v1.1.1.apk` or the tester-kit ZIP from [this release](https://github.com/Doffi4/Decryptum/releases/tag/v1.1.1). Follow [the Russian tester instructions](TESTING.md). The ZIP contains the same APK, checksum, instructions, local autofill demo and verification notes. Share reproduction steps and build/Android details, never vault exports, credentials, QR/seed or raw logs.

## Known limits

The APK retains Android Debug signing. Actual installation/upgrade, data retention, native storage/crypto, autofill recipient authorization and device accessibility remain unverified. Complete portable encrypted recovery is not implemented. CSV export is plaintext; passkeys are experimental software/exportable credentials. **Claude guidance is disabled in release.** Read [Security](../SECURITY.md#known-limitations), [Privacy](../PRIVACY.md) and [artifact verification](BUILD_VERIFICATION_1.1.1.md).
