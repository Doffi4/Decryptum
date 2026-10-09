# Screenshots

The current gallery uses `vault-current.webp`, `totp-current.webp`, `security-center.webp` and `test-data.webp`, captured on 2026-10-09 from real v1.1.1 Compose screens/controls through Robolectric API 35. Configuration: EN, 412 × 892 dp, xhdpi (824 × 1784 pixels), dark fallback theme, dynamic color disabled. These are screen/component renders, not an installed APK on a physical device; the main navigation shell/system bars are not pictured.

All data is synthetic. The vault uses the real synthetic generator with 100 entries / 70 services and fictional `example.invalid` logins; favicon requests are disabled for capture. TOTP uses three copies of the public RFC test seed and codes calculated at capture time. Security Center uses the real analyzer on the four-record control fixture: 4 password entries / 2 weak / 2 reused / 2 duplicate entries. Test-data controls show their real idle default of 500 / 200, separately from those fixture counts.

`ReleaseScreenshotsTest` is an opt-in capture helper (`decryptum.screenshots` JVM system property selects the output directory). Its repositories have no network/database I/O and reject writes. The image encoder uses WebP quality 85; no screen content is painted or invented outside Compose.

`vault.png` and `totp.png` are retained as historical assets, but are no longer used in the current README/site gallery. Their exact build/device and account provenance are unverified.

`password-entry.png` renders the current Compose password-entry component locally through Robolectric with synthetic data. It is not a physical-device screenshot and does not establish phone keyboard/inset or dynamic-color behavior.

Future screenshots should use a disposable vault with synthetic identities and record the APK/version, device/API, locale and theme. Never include live credentials or recovery secrets.
