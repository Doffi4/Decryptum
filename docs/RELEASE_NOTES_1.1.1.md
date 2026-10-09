# Decryptum v1.1.1 — prerelease

2026-10-09 · versionCode 8 · Android 8.0+ / API 26+.

This release includes the local vault, SecureRandom password generator, TOTP, Android autofill and local Security Center.

## What changed

- The debug advisor has an isolated four-integer contract, standalone synthetic evaluation harness and versioned EN/RU prompts. Its client and prompts are excluded from the release classpath. No live-response quality is claimed.
- Refreshed EN/RU/UK repository presentation and website release links.

## Download

Download the APK from [this release](https://github.com/Doffi4/Decryptum/releases/tag/v1.1.1). v1.1.0 remains the stable release.

## Known limits

The APK retains Android Debug signing. Actual installation/upgrade, data retention, native storage/crypto, autofill recipient authorization and device accessibility remain unverified. Complete portable encrypted recovery is not implemented. CSV export is plaintext; passkeys are experimental software/exportable credentials. **Claude guidance is disabled in release.** Read [Security](../SECURITY.md#known-limitations), [Privacy](../PRIVACY.md) and [artifact verification](BUILD_VERIFICATION_1.1.1.md).
