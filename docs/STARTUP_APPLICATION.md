# Decryptum — startup application draft

Prepared 2026-10-07; release status updated 2026-10-08 for owner review. These are reusable answers, not a submitted application or verified company profile. Every unknown owner-specific fact stays `[OWNER INPUT REQUIRED]`. Do not describe the public 1.1.0 prerelease as production-ready or the debug advisor as a live customer integration.

## 1. One-sentence product description

Decryptum is an open-source, local-first Android password manager and TOTP authenticator with local security checks, experimental passkey-provider code, and a consented debug prototype that asks Claude to explain four aggregate security counts without sending vault secrets.

## 2. What does Decryptum do?

It stores credentials in an encrypted local vault, generates passwords and TOTP codes, implements Android autofill/provider flows, and flags weak, reused and duplicate password entries. Users review findings and make changes themselves. Passkey compatibility remains experimental; there is no hosted vault or cloud sync.

## 3. What problem does it solve?

It aims to help Android users manage credentials and understand common password-hygiene problems with clear local controls. The advisor experiment explores explaining limited local findings without giving an AI service passwords or account identities. This problem statement is a product hypothesis, not validated customer research.

## 4. Target user

Android users who prefer a local vault and transparent privacy boundaries. Initial customer segment, interviews and distribution strategy: `[OWNER INPUT REQUIRED]`. The candidate is currently intended for testing with disposable data.

## 5. Current stage

Open-source implementation with published v1.0.1 and an owner-approved public 1.1.0 prerelease. Android builds/JVM tests and a static website are available. Native authorization/migration/recovery/passkey gates remain unresolved. The [website](https://doffi4.github.io/Decryptum/) is published on GitHub Pages; the Claude prototype has fake-transport tests but no live-provider validation.

## 6. Technical differences

The design separates local secret analysis from remote explanation: temporary local comparison state produces four integer counts, and an explicit serializer sends only those fields. Local storage uses Room/SQLCipher plus additional vault-key field encryption. These are source-backed design choices, not claims of unique invention or superiority.

## 7. How is data protected?

SQLCipher encrypts the database using a random Keystore-wrapped DB key. Password/TOTP fields and private passkey material use a separate vault DEK; an Argon2id-derived key wraps the DEK for master-password unlock. Main biometric unlock uses an authenticated Keystore operation. Automatic Android backup is disabled. Native behavior, full integration authorization and encrypted portable recovery still require validation. We do not claim universal hardware backing, zero knowledge or an independent audit.

## 8. Why is Claude useful?

Claude could turn limited aggregate password findings into concise, understandable manual steps in English/Russian, clearly distinguishing heuristics from verified account security. It does not need passwords, service names or permission to change records.

## 9. How is Claude integrated?

Only in a debug prototype: fresh consent, exact-data preview, runtime developer configuration, fixed HTTPS Messages API request, bounded response validation and cancellation on lock/stop/data changes. Guidance renders as untrusted plain text. Release builds construct a disabled service. Production credential/backend access and live quality testing are future work.

## 10. Exact data Claude may receive

Four nonnegative integer fields: `password_entry_count`, `weak_password_count`, `reused_password_count`, `duplicate_credential_count`. Requests additionally include fixed instructions/schema, model, EN/RU language and token limit, developer API authentication and ordinary IP/timing/transport metadata. Counts overlap and can be sensitive; aggregate-only is not anonymity. See [advisor contract](SECURITY_ADVISOR.md).

## 11. What data never leaves the vault/device?

For the **advisor flow**, passwords, hashes/fingerprints, account/service/domain identities, TOTP seeds/codes, private passkeys and master/DEK/DB keys are excluded. There is no blanket device-wide “never leaves” guarantee: user-selected credentials leave through autofill, readable CSV can contain secrets, and favicons disclose domains. Core local operations do not require sending vault contents to Claude.

## 12. How would API credits help?

Credits would support controlled evaluations of structured explanations using synthetic aggregate fixtures, EN/RU clarity/refusal tests, invalid/truncated responses, adversarial output and timeout behavior, followed by privacy-reviewed integration work. They could also support development/testing/documentation with synthetic examples. No production vaults or real secrets are needed for these evaluations. Expected monthly use and budget: `[OWNER INPUT REQUIRED]`.

## 13. What comes next?

Close native authorization/migration and recipient-trust gates, validate or safely gate passkeys, fix exact import/edit/restore behavior and establish encrypted recovery. Verify signing, device accessibility/offline behavior and public disclosure contact. Production advisor is optional and deferred until access, retention/privacy and operating controls are reviewed. No delivery dates are promised.

## 14. Current traction

Repository and earlier releases exist; that is implementation/distribution evidence, not traction. Users, MAU, downloads with measurement dates, revenue, customer validation, funding and partnerships: `[OWNER INPUT REQUIRED]`. Do not substitute stars, test counts or screenshots for customers.

## 15. Team/company status

Founder/contact name, role, company/legal name, incorporation status/date, employees, ownership, funding/investors and prior startup-credit participation: `[OWNER INPUT REQUIRED]`. Repository owner handle is Doffi4; it does not establish a legal entity or team size.

## 16. Location/country

Founder residence, business registration country, operating location and eligibility under Anthropic supportability policies: `[OWNER INPUT REQUIRED]`. Do not infer these from locale, device timezone or repository metadata.

## 17. Links

- Repository: [Doffi4/Decryptum](https://github.com/Doffi4/Decryptum).
- Existing release: [v1.0.1](https://github.com/Doffi4/Decryptum/releases/tag/v1.0.1); it predates this work.
- Live website: https://doffi4.github.io/Decryptum/ ; privacy policy: https://doffi4.github.io/Decryptum/privacy.html . Company email/demo/custom domain: `[OWNER INPUT REQUIRED]`. GitHub Pages publication does not establish company-domain eligibility.

## 18. Anything else reviewers should know?

Decryptum is openly developed under GPLv3. Its intended AI boundary is narrow and optional; the core product does not depend on AI availability. Release readiness is still gated, no independent security audit or traction has been established, and the current advisor is not production enabled. We would use program support to evaluate this privacy-conscious approach honestly rather than imply Claude has inspected users' secrets.

## Current Claude Startups requirements — owner check

As checked on 2026-10-07, the [official program FAQ](https://claude.com/programs/startups) allows bootstrapped applicants and describes eligibility as founding within five years or funding within two. It requests a Claude Console account, a company email matching the website domain, a short product description and compliance with supportability policies. First-party API credits apply through Console, not Bedrock/Vertex; approval and benefits are not guaranteed. The [official terms](https://www.anthropic.com/startup-program-official-terms) govern the application. Recheck them at submission time; this is not legal/eligibility advice.

Before applying, the owner must confirm eligibility/date/company/country facts; choose an owned domain or separately authorize purchase; review the published website and configure any required owned-domain hosting; configure matching domain email; create/verify the correct Claude Console organization; confirm any required project/API settings without exposing keys; fill all placeholders and review the final form. No paid action, DNS change, account creation or submission was performed.

**Recommendation now:** keep this as a draft. Do not submit a production-ready claim while launch blockers, owner facts and domain/email verification are unresolved. A candid development-stage application could be considered after those owner facts/contact requirements are confirmed; it does not require falsely declaring the release safe or the advisor live.
