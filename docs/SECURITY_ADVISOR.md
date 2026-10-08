# Optional Claude Security Advisor (Phase 4)

The advisor explains aggregate local findings and suggests a manual order of actions. Local Security Center remains complete without AI, a key or internet. No automatic requests, persisted consent, cloud sync, analytics, backend, tools, vault changes or release publication are introduced. Existing favicon network behavior elsewhere in the app is outside this feature; this is not a claim that the entire app is network-free.

## Exact outbound contract

Only these vault-derived fields can leave the device:

| JSON field | Type | Meaning |
| --- | --- | --- |
| `password_entry_count` | non-negative integer | Stored entries with nonempty passwords included in local analysis |
| `weak_password_count` | non-negative integer | Entries flagged by the existing local weakness heuristic |
| `reused_password_count` | non-negative integer | Entries whose exact password is reused across local account identities |
| `duplicate_credential_count` | non-negative integer | Entries in exact duplicate groups, including all copies |

Each category count is at most `password_entry_count`; categories can overlap. They are entry counts, not the number of distinct passwords or verified affected websites. No groups/entry IDs/identity labels are sent. Breaches, password age and TOTP/passkey coverage are not implemented local checks and are omitted, never represented by reassuring zero values.

Example with synthetic values:

```json
{
  "password_entry_count": 5,
  "weak_password_count": 2,
  "reused_password_count": 3,
  "duplicate_credential_count": 0
}
```

The HTTPS request also contains a developer-selected model ID, a fixed system instruction with an EN/RU response-language choice, a 1200-token limit, a fixed response schema and one user message containing exactly this JSON. Authentication uses the provider API key in `x-api-key`, with `anthropic-version: 2023-06-01`; no application account/device ID is added. Anthropic sees the client's IP address, timing and ordinary transport metadata. Counts can themselves be sensitive (especially for a small vault); aggregate-only is not anonymity or zero knowledge.

## What never enters this feature's request

Plaintext passwords, any password hashes/fingerprints, usernames, service names, URLs/domains, notes, TOTP seeds/codes, passkey private material, recovery/master/DB keys, KDF outputs, encrypted vault blobs, autofill payloads and decrypted vault entries are never passed to the advisor. The developer API key is an explicitly configured authentication credential; it is not a vault encryption key.

## Code boundary and lifecycle

Paths below are relative to `app/src/main/java/com/doffi4/doffisecure/`:

- `domain/security/LocalSecurityAnalyzer.kt` performs existing local analysis.
- `domain/advisor/AdvisorSummary.kt` contains the explicit four-Int allowlist and `AdvisorSanitizer`; mapper discards all `SecuritySummary` item references.
- `domain/advisor/SecurityAdvisorService.kt` accepts only `AdvisorSummary` and an EN/RU enum; typed failures contain no exception text.
- `data/advisor/ClaudeAdvisorCodec.kt` writes each allowed key explicitly. No reflection/generic vault serialization. The same writer produces the consent JSON and user-message JSON.
- `data/advisor/ClaudeSecurityAdvisorService.kt` owns the fixed origin and dedicated OkHttp client. No vault repository, shared interceptors, body/header logging or telemetry.
- `ui/security/SecurityAdvisorController.kt` holds only DTOs/guidance. The existing `SecurityCenterViewModel` owns it, supplies sanitized summaries and clears it on every new local state.
- `ui/security/SecurityAdvisorPanel.kt` implements consent/transparency/loading/result/failure states; `SecurityCenterScreen.kt` adds the panel inside Ready state.

Before every send/retry: optional explanation, explicit recipient/privacy statement, the exact snapshot JSON and readable category counts, a separate View data being shared dialog, then explicit Send or Decline. Preview/decline performs no request. Retries reopen consent. A refresh, vault emission (even identical counts), local error/loading, lock or screen stop invalidates the snapshot and clears guidance. Cancellation invalidates a generation token so a late/non-cooperative reply cannot restore cleared state.

Authorization is checked in the screen controller before starting and before showing a result. Since runtime configuration can suspend, the transport additionally checks coroutine cancellation and a synchronous Boolean auth guard **after** reading configuration and immediately before submitting the Call. This guard carries no vault fields. Cancellation before this point does not enqueue a request.

Request connect timeout 10s, read/write timeout 20s and whole-call timeout 30s. Coroutine cancellation calls `Call.cancel()`. Redirects and automatic connection retries are disabled so a key/payload cannot be forwarded to another origin or silently resent. Response size is bounded to 64 KiB before JSON parsing. Success requires `end_turn`, one text block and a strict `overview`/`checklist` object; refusal/truncation/extra fields/invalid types/empty or oversized text are rejected. Responses are plain text with no automatic links/actions. Shape checks do not prove factual or security correctness; a visible AI disclaimer remains mandatory.

## Development configuration: deliberately unavailable by default

`app/src/debug/.../AdvisorServiceFactory.kt` reads a **runtime private file**, only after consent, on `Dispatchers.IO`:

```text
/data/user/0/com.doffi4.doffisecure/files/claude-advisor.properties
```

Properties (values here are placeholders, not usable credentials):

```properties
api_key=YOUR_DISPOSABLE_DEVELOPER_KEY
model=YOUR_STRUCTURED_OUTPUT_CAPABLE_MODEL_ID
```

Provision this file using a trusted development tool/Android Studio Device Explorer on a disposable test device running the debug APK. Do not put the real key in a command line/history, source tree, Gradle property, BuildConfig, resources/assets or a shared production build. Do not paste credentials into agent logs/chat. Delete the device file and revoke the disposable key after testing. The debug file is app-private but not Keystore encrypted: debug/root access can read it, so this is **not** a production key-storage solution. No config/key was created or installed by this task.

The configuration file is limited to 4 KiB; keys/model are validated without logging values. Missing or blank key/model produces Missing configuration, unreadable/invalid config produces Configuration error. No fallback key or model is embedded. A configured model must support `output_config.format` structured JSON; provider/model/schema compatibility must be live-tested rather than inferred from a successful mock.

The parser checks full consumption of both the outer API object and the inner guidance object, and checks real string types instead of Android `JSONObject.getString` coercion. It uses the platform JSON parser with its syntax tolerance; the enforced contract is complete objects, exact guidance keys/types, limits and completion status, not a claim of full RFC JSON grammar validation. Android-specific coercion regressions live in instrumentation tests as well as JVM tests.

`app/src/release/.../AdvisorServiceFactory.kt` always returns `DisabledSecurityAdvisorService`. Release cannot read developer configuration or send an advisor request, even if that file exists. This does not alter the existing release signing policy, which is still a launch-audit limitation.

## Provider documentation and privacy caveat

Integration was checked against the official [Messages API](https://platform.claude.com/docs/en/api/messages/create) and [structured outputs](https://platform.claude.com/docs/en/build-with-claude/structured-outputs) documentation on 2026-10-07. Do not promise no retention. Anthropic's [commercial retention policy](https://privacy.claude.com/en/articles/7996866-how-long-do-you-store-my-organization-s-data) describes standard deletion within 30 days with exceptions; model-specific requirements and negotiated arrangements may differ. Recheck current [API retention arrangements](https://platform.claude.com/docs/en/manage-claude/api-and-data-retention) and selected model terms before enablement. No live request or billing/eligibility check was performed.

## Production enablement and Phase 5 handoff

Keep release disabled until separately reviewed production access exists. Recommended next architecture: minimal authenticated backend/proxy protects the provider key; enforce exactly the same DTO allowlist server-side, body limits, device/user auth, rate/spend caps, replay/abuse controls, timeout/cancellation and a no-body/no-key logging policy. Do not add vault tools or accept arbitrary free-text/account objects. Proxy responses must still be validated by the app. Review provider and backend retention/deletion, region/subprocessors, threat model and incident response. Backend auth/operation is a separate project; none was created here.

`PRIVACY.md` and `SECURITY.md` were absent and remain absent as requested. Phase 5 must document this exact four-field contract, Anthropic/IP/timing/retention, dev-only availability, user consent and optionality alongside the **actual** existing HIBP/favicon/SAF/backup behavior. Do not make blanket no-network/zero-knowledge/certified-security claims. Close Critical/High launch audit gates (authorization/integrations, migrations, recovery, signing and network consent) before public release. Validate real-device font scaling/TalkBack/light-dark/lock/offline behavior using synthetic entries. Add authenticated backend and live transport verification only as separately authorized work; do not publish or invent a contact/organization.

## Verification record

Verified on 2026-10-07 with `JAVA_HOME=E:\Android Studio\jbr` (JBR 25.0.3). Final command, exit 0 / `BUILD SUCCESSFUL`, 14s:

```powershell
.\gradlew.bat -I .codex-phase4/release-tests.init.gradle :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:compileDebugAndroidTestKotlin --console=plain
```

| Check | Result |
| --- | --- |
| Debug JVM suite | **189 tests, 24 suites, 0 failures/errors/skipped** |
| Release JVM suite | **190 tests, 25 suites, 0 failures/errors/skipped**, includes the release no-config/no-remote-access gate |
| Android builds | Debug and release/R8 assembled successfully under `.codex-phase4/build/app/outputs/apk/`; existing debug signing for release remains a launch limitation |
| Lint | **0 errors, 150 warnings**; dependency-update discovery accounts for the increase from the earlier Phase 3 report; no advisor source finding |
| Compose + Android JSON tests | Compiled successfully; **not executed** (no connected devices/AVDs) |
| Static UI audit | Premium strict audit exit 0, no findings; not runtime accessibility/visual verification |
| Logging review | No logger, body/header dump or raw exception-message propagation in new advisor code or its touched center/DI integration |
| Live provider request | **Not performed**; service exercised with fake Call.Factory and synthetic data only |

The debug/release unit suites and updated source/instrumentation compilation actually executed in `final-verification-fixed.log`; that run stopped at Windows APK packaging. The final successful command reused those unchanged outputs/tests (UP-TO-DATE) and completed release packaging and lint reporting. This is not a claim of a final clean rebuild. Earlier initial full compilation/R8 also passed, before review fixes. Final release factory bytecode additionally confirms it constructs only DisabledSecurityAdvisorService.

26 JVM cases were added beyond Phase 3: 4 DTO/boundary, 12 service/codec/transport, 7 controller, 3 center integration; release adds one factory gate. 10 instrumentation cases were added: 7 advisor UI/consent states and 3 Android JSON regressions. Boundary fixtures contain synthetic passwords, identity labels and TOTP seeds to prove none survive serialization. Tests catch exact outbound fields, invalid counts, error/refusal/truncation/schema/size handling, cancellation both in-flight and before enqueue, auth changes across config suspension, consent/retry and stale results.

Independent review found and fixed the post-config auth window, JSON trailing-input/type coercion and real-NUL/EOF ambiguity. `review-red.log` and `nul-red.log` contain observed failing regressions before fixes; the final suites pass after fixes. Android coercion behavior is additionally pinned in instrumentation source but has not been executed on Android.

Local generated outputs/logs/init scripts are ignored under `.codex-phase4/`. AGP 9.4 disables release unit tests by default; the local init script enables `HasUnitTestBuilder.enableUnitTest` only for verification, leaving product Gradle configuration unchanged. Incremental generated resource/dex/APK directories carried ReadOnly attributes and failed deletion on Windows. Clearing attributes through PowerShell object/property setters was ineffective for directories; `[IO.File]::SetAttributes(path, Normal)` was verified to clear them inside the resolved output subtree. No existing build/vault directory was deleted, no process was stopped, and no source workaround/baseline suppression was added.

Compilation of instrumentation is not execution. No actual Anthropic credential was requested, searched for, used, installed or committed. Real traffic, offline DNS/timeouts, device auth/lifecycle, accessibility and provider compatibility/quality remain unverified. Existing uncommitted Phase 2/3 work is preserved; no commits, pushes, deployment or release publication were made.
