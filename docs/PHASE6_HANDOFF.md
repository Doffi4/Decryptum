# Phase 6 handoff — release verification

**Historical preparation handoff (2026-10-07).** Public v1.1.0 prerelease publication was subsequently authorized on 2026-10-08. Follow the [current checklist](LAUNCH_CHECKLIST.md) and [artifact verification](BUILD_VERIFICATION_1.1.md); the following preparation instructions do not revoke that approval.

Phase 5 prepared a local public presentation, not a release. Preserve earlier uncommitted Android/Phase 2–4 work. Website output, docs and successful builds do not close security gates. Do not publish, deploy, push a release, buy a domain, change DNS or submit external applications without a separate owner request.

## Start here

Read `LAUNCH_AUDIT.md` as a historical baseline, current `SECURITY.md`/`PRIVACY.md`, `THREAT_MODEL.md`, `SECURITY_CENTER.md`, `SECURITY_ADVISOR.md`, `PRODUCT.md` and `PUBLIC_PRESENTATION.md`. Reconcile audit S01–S11 against current code. Some password-read/cache, corrupt-decryption and automatic HIBP paths were fixed later; do not assume the rest is closed.

## Required checkpoints

1. Reproduce lock/fresh-auth/recipient trust with synthetic vaults across MainActivity, Autofill chooser/copy/save, Credential Provider, passkey creation/assertion and export. Verify background/process-death handling and absence of stale plaintext. Fix at existing integration points.
2. Make plaintext DB migration crash-safe, preserve the last usable source, verify rollback/low space/rename failures and all historical schema upgrades. Test with copied synthetic fixtures, never the owner's vault.
3. Validate import/edit and complete TOTP-only/standalone-passkey round trips. Design portable encrypted recovery separately; the existing readable CSV and fallback private-key encoding are not a safe restore proof.
4. Resolve WebAuthn correctness/caller-origin-RP binding. Either prove capabilities with browser/server/device evidence or gate unsupported capability safely. Do not claim hardware-backed passkeys.
5. Review screen/log/clipboard policy, developer destructive actions and complete favicon toggle/consent. HIBP remains disconnected until strict parsing, consent, cancellation, coverage/timestamps and unknown/error states are verified.
6. Choose a real release signing key, verify certificate/update continuity, bump versionName/versionCode, create a reproducible candidate and hashes. Do not expose private signing/provider keys in source, commands or logs. Current release config uses debug signing.
7. Execute native SQLCipher/Keystore/biometrics and instrumentation on a disposable device matrix: minimum supported API, modern Credential Provider API, light/dark/dynamic colors, font scaling/TalkBack, offline/slow network, large vault and process death. JVM/unit and instrumentation compilation are not substitutes.
8. Capture current polished screenshots with synthetic identities and record device/build/theme/hash. Replace labelled historical images only after capture and review. Keep password/seed/private-key material out of media.
9. Verify a private disclosure channel and any contact mailbox. Update website status, README/policies and supported-version table only against actual release evidence. If the advisor remains release-disabled, keep that fact prominent.

## Evidence and go/no-go

Record commit plus worktree snapshot, toolchain/commands, actual vs UP-TO-DATE task results, APK hashes/certificate, device/API/browser matrix, test totals, warning categories, upgrade/restore instructions and per-gate status. Distinguish source-inspected, executed and unverified behavior. A candidate remains blocked if authorization, lossless migration, trustworthy passkeys/recipient binding, recovery or signing gates are unresolved.

After gates: the owner selects hosting/domain and deployment path, reviews disclosure contact and final copy, then separately authorizes publication. The landing stays a presentation; no vault web client or sync is introduced by it. Production advisor needs a separate credential/privacy review, and no live provider validation is claimed today.
