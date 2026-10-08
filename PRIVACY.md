# Privacy

Scope: current Decryptum development source, reviewed 2026-10-07. Published builds may differ. This is a technical data-flow statement, not a claim of legal certification. Read [Security](SECURITY.md) for limitations.

## What is stored and where

The Android app stores service/account names, usernames, passwords, optional URLs, timestamps, TOTP configuration and passkey data in app-private storage. Room/SQLCipher encrypts the database; passwords, TOTP fields and private passkey material have an additional vault-key encryption layer. Settings and wrapped-key blobs use private SharedPreferences. Site-icon caches are stored locally and can reveal domains to someone with filesystem access. The optional debug advisor configuration file contains a developer API key in app-private storage; it is not part of the vault and is absent unless provisioned.

There is no Decryptum account, hosted vault, cross-device sync or recovery escrow. Core vault operations, the password generator, TOTP generation and Security Center analysis can run offline. Decrypted values and local findings exist in memory during use; guaranteed memory erasure is not claimed.

## What may leave the device

| Flow | Outbound data / recipient | Current behavior |
| --- | --- | --- |
| Autofill / Credential Provider | Selected credentials or signed passkey responses to the selected app/site via Android | User-initiated integration; recipient authorization and compatibility still require verification |
| Site icons | Requested domain, IP/timing and HTTP metadata to Google, DuckDuckGo and potentially the site; redirects/apex fallback can add recipients | Enabled by default for existing callers; cached locally. The favicon toggle is not a complete network-off guarantee |
| CSV export | Readable passwords, TOTP data and potentially private passkey material to the user-selected document destination | Unencrypted manual export; a cloud-backed document provider may upload the file independently |
| HIBP transport | Five hex characters of a password SHA-1 hash, IP/timing and transport metadata to HIBP | Code retained, but not invoked by current loading/edit/local-center flows; breaches are not checked |
| Configured debug Claude advisor | Four counts plus fixed instructions/schema, model, EN/RU language, token limit, developer API-key authentication and IP/timing to Anthropic | Explicit per-request consent, optional exact-data preview; disabled in release |

No vault-wide upload or secret-bearing AI request is implemented. This does not mean all app network requests contain no account-related metadata: favicon domains themselves can reveal services you use. The project does not control third-party or document-provider retention policies.

## HIBP behavior

The retained Pwned Passwords implementation uses a k-anonymity range query over HTTPS with a five-character SHA-1 prefix and padding request header; full passwords and full hashes are not sent by that transport. Prefix matching is not anonymity or zero knowledge. Error/body handling and partial coverage need correction before it is wired back into a consented feature. Security Center explicitly shows unavailable/not checked rather than a reassuring zero breach count.

## Claude Security Advisor

Only these vault-derived integer fields enter the debug request:

```json
{
  "password_entry_count": 5,
  "weak_password_count": 2,
  "reused_password_count": 3,
  "duplicate_credential_count": 0
}
```

These are synthetic example values. Passwords, hashes, fingerprints, usernames, service labels, domains, URLs, TOTP seeds/codes, passkey private material, master keys and encrypted vault blobs are excluded by the explicit DTO/serializer boundary. Counts can overlap and can still reveal sensitive information about a small vault.

No automatic request or persisted consent is added. Each send/retry requires consent; lock, stop, refresh and vault changes invalidate the snapshot and cancel work. Cancellation cannot retract data already received by the provider. Responses are untrusted manual advice, not local findings or automated actions. No tools or vault writes are granted. Live provider retention, compatibility and quality have not been verified; this document makes no retention promise. Release builds construct a disabled advisor. See [implementation and development configuration](docs/SECURITY_ADVISOR.md).

## Camera, clipboard, crashes and analytics

Camera access is used for QR scanning, and selected images can be decoded locally. No camera-image upload flow was found in the reviewed code. Barcode-library/OS service provisioning may have its own network behavior; no comprehensive OS traffic audit has been performed.

Copying sends the selected value to Android's clipboard. A sensitive flag on Android 13+ and best-effort timed clearing reduce exposure but cannot prevent an already-authorized observer from reading it. Screenshot protection is not consistent across all activities.

No analytics, advertising, tracking-pixel or remote crash-reporting SDK was found in current app dependencies/source. Android logging is present and can include labels, URIs and errors. OS crash handling and externally installed diagnostics are outside this app's control. Do not share raw logs or exports without reviewing/redacting them.

## Backup, deletion and loss

Android automatic backup is disabled and extraction rules exclude database/preferences/files for cloud and device transfer. Device/OEM behavior still needs validation. CSV is not an encrypted backup; Base64 passkey fields are not encryption. Full restore and independent encrypted recovery are not established. Loss of the device, Keystore or master password may mean permanent loss of vault access.

Deleting a record/uninstalling does not erase copies you exported, other apps' clipboard captures, third-party logs or the document provider's copies. Secure wiping of flash storage is not promised. No remote administrator can restore or reset the vault.

## Landing website

The [public site](https://doffi4.github.io/Decryptum/) is hosted on GitHub Pages. It contains no analytics, tracking pixels, session recording, embedded third-party widgets, remote fonts or browser API calls to an AI provider. It serves local assets. Its optional theme control stores only `decryptum-theme` (`light`/`dark`) in browser localStorage. With JavaScript disabled it remains usable in dark mode. External links navigate to GitHub; GitHub receives ordinary HTTP metadata for website requests and may keep hosting access logs. No custom domain or privacy mailbox is configured.

## Contact and changes

No privacy mailbox or custom domain is configured. Use the repository for general questions; follow [private disclosure instructions](SECURITY.md#reporting-a-vulnerability) for sensitive issues. New sync, telemetry, advisor production enablement or network features must update this document with actual recipients, payloads and controls before being described publicly.
