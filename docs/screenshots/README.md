# Screenshots

Gallery order: vault, TOTP, Password Generator, Local Security Center. Files: `vault-current.webp`, `totp-current.webp`, `password-generator.webp`, `security-center.webp`.

Captured on 2026-10-09 from real Compose screens through Robolectric API 35: EN, 412 × 892 dp, xhdpi (824 × 1784 pixels), dark fallback theme, dynamic color disabled. These are local screen renders, not an installed APK on a physical device. Main navigation and system bars are not pictured.

The images reflect the current source snapshot, including favicon URL normalization added after the v1.1.1 APK was published. Existing release binaries are unchanged.

All displayed identities and vault records are fictional. Service domains identify publicly available favicons, downloaded into a local cache before capture; the app's actual Coil/FaviconFetcher/IcoDecoder path renders them. TOTP uses public demonstration data; displayed services do not imply real accounts or endorsement. The generator shows its real options and masks its transient generated value. Security Center runs the real local analyzer on fictional entries.

The opt-in `ReleaseScreenshotsTest` uses read-only repositories, requires a prepared icon cache and rejects writes. WebP quality is 85; no screenshot content is painted outside Compose.

Historical `vault.png` and `totp.png` are not used in the current gallery; their exact build/device provenance is unverified. `password-entry.png` is a local Compose component render.

Never include real credentials, QR recovery secrets or exports in public screenshots.
