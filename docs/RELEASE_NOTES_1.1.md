# Decryptum v1.1.0 — Security & Product Polish

**Public prerelease · versionCode 7 · 2026-10-08.** The owner approved the previously delivered APK for publication. The exact candidate 2 binary is retained; production-readiness gates remain open in the [launch checklist](LAUNCH_CHECKLIST.md).

This release adds useful local security checks and improves the vault interface while tightening several storage and authorization paths. It does not establish complete Android runtime safety or recovery.

## User-facing changes

- **Security Center:** inspect weak-password heuristics, exact password reuse across stored account identities, and exact duplicate credentials. Open affected entries and decide what to change manually. Checks run locally; no records are automatically deleted.
- **Material 3 polish:** updated fallback colors and clearer vault/TOTP/detail components, dialogs and EN/RU labels. Existing navigation and dynamic colors remain.
- **Honest check states:** breach status is not checked/unavailable; no global security score, certified entropy, password-age or complete 2FA/passkey coverage is implied.
- **Autofill authorization:** the picker lists metadata only, then reads the selected password after authentication. Unavailable biometrics use master-password fallback instead of bypassing mandatory auth. Password Credential Manager uses the same fallback; passkey auth no longer skips required verification when biometrics are unavailable.
- **Fresh master verification:** a wrong password is rejected even during an already unlocked session.
- **Storage repair:** migration preserves schema version, checkpoints WAL, validates encrypted export and uses atomic replacement. Failure to persist the DB key stops database use instead of risking an unrecoverable encrypted database. Android migration fixtures are provided; native execution is still required.

## Navigation and password entry

- Fixed the start-tab saved-stack alias: tapping Passwords always returns to the vault instead of reopening a saved Security Center/generator branch. Security Center's generator shortcut uses the same navigation action as the bar; other tab restoration stays enabled.
- Removed screen crossfades and opaque-background gaps that showed multiple tab contents at once. Bottom-bar press feedback is clipped to each rounded tab.
- Replaced the old add-password alert with a native Material 3 modal sheet adapted from a synthetic Stitch proposal. Service/login/password, local generation, existing optional strength display, show/hide, Next/Done keyboard actions and scrollable controls follow existing EN/RU themes.
- Saving waits for persistence, prevents repeated taps, retains inputs after a failed write and displays a localized retry message. Service/login outer whitespace is trimmed; password characters and spaces are preserved exactly. Credentials are not placed in saved-instance state, analytics or logs.
- VersionCode 7 updates the previous private versionCode 6 candidate and public v1.0.1/versionCode 5. See the [artifact verification](BUILD_VERIFICATION_1.1.md).

## Optional Security Advisor

Claude guidance is a **debug prototype and disabled in this release APK**. Configured debug requests require fresh consent and show the exact payload: only `password_entry_count`, `weak_password_count`, `reused_password_count`, `duplicate_credential_count`. Counts can still be sensitive; IP/timing/provider metadata are also disclosed. No passwords, hashes, identities, domains, TOTP secrets or private keys enter the AI payload. No live-provider request was performed. Production access is deferred until credential/backend and privacy review.

## Privacy and known limitations

Core vault, generator, TOTP generation and local checks operate offline. Favicons can contact third parties and the toggle does not cover every caller. Automatic HIBP loading/edit checks are disconnected. Selected credentials can leave via Android integration; readable CSV can expose passwords, TOTP and private passkeys through the chosen document provider.

Passkeys remain experimental software P-256 credentials. Caller/origin/RP binding, challenge/allowCredentials correctness and interoperability remain launch blockers. CSV/JSON is not a tested encrypted recovery format: TOTP-only/standalone passkey restore, exact secret preservation, linked edits and legacy upgrades still need work. Developer cleanup can affect real records; do not use it on important data.

JVM tests and APK assembly are not evidence of native SQLCipher/Keystore, biometrics, process-death behavior, TalkBack or device compatibility. Use disposable data for this prerelease and maintain independent account recovery methods. No independent security audit, guaranteed memory erasure or universally hardware-backed protection is claimed.

## Upgrade and signing notes

Application ID stays `com.doffi4.doffisecure`; versionCode increases from 5 to 7. Public v1.0.1 release APK and this v1.1.0 APK share certificate SHA-256 `93965360a3a6903851cecddb9370eab62afc269ec107cce9b472190ea99b76fa`. Both use an Android Debug signing identity. Matching signatures permit update identity continuity; actual installation/upgrade/data retention has **not** been tested.

Do not uninstall or reset an existing vault to solve an install error: Keystore loss can destroy access. Do not treat CSV as a proven complete recovery copy. Test upgrades using copied synthetic fixtures or a disposable Android profile/device before important data. Public signing policy/key custody still requires an owner decision; changing certificates without a supported rotation plan can prevent updates.

Download **[Decryptum-v1.1.0.apk](https://github.com/Doffi4/Decryptum/releases/download/v1.1.0/Decryptum-v1.1.0.apk)** from the [v1.1.0 release](https://github.com/Doffi4/Decryptum/releases/tag/v1.1.0). APK SHA-256: `5fe57baa958da5fc48d8c90dcf31fc1cf5862b6a00053a14e17068c142d32c95`. Checksums and verification notes accompany the release. Website: **[doffi4.github.io/Decryptum](https://doffi4.github.io/Decryptum/)**. Source is included in the repository; custom-domain/DNS changes and startup application submission remain deferred.
