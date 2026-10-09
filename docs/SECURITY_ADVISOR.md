# Optional Claude Security Advisor

The advisor explains aggregate local findings and suggests a manual order of actions. Local Security Center remains complete without AI, a key or internet. No automatic requests, persisted consent, cloud sync, analytics, backend, tools, vault changes are introduced. Existing favicon network behavior elsewhere in the app is outside this feature; this is not a claim that the entire app is network-free.

## Exact outbound contract

Only these vault-derived fields can leave the device:

| JSON field | Type | Meaning |
| --- | --- | --- |
| `password_entry_count` | non-negative integer | Stored entries with nonempty passwords included in local analysis |
| `weak_password_count` | non-negative integer | Entries flagged by the existing local weakness heuristic |
| `reused_password_count` | non-negative integer | Entries whose exact password is reused across local account identities |
| `duplicate_credential_count` | non-negative integer | Entries in exact duplicate groups, including all copies |

Each category count is at most `password_entry_count`; categories can overlap. They are entry counts, not the number of distinct passwords or verified affected websites. No groups/entry IDs/identity labels are sent. Breaches, password age and TOTP/passkey coverage are not implemented local checks and are omitted, never represented by reassuring zero values.

Additional vault-derived fields are **not necessary** for this evaluation. The four counts and fixed instructions already support explaining the implemented local checks. New fields require a separate privacy justification and review; there is no extensible metadata map.

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

The current debug/evaluation prompt is `aggregate-v2-candidate`: a guidance writer for already-computed local findings, never a scanner. The frozen `aggregate-v1` baseline remains selectable by the standalone synthetic harness. [Prompt versions and audit policy](CLAUDE_ADVISOR_PROMPT.md) describe the canonical source, byte snapshots and limitations; no live quality improvement or production readiness is claimed. The version selector is a closed developer enum, not vault data or arbitrary instructions. It does not add outbound fields.

## What never enters this feature's request

Plaintext passwords, password hashes/fingerprints or HIBP prefixes, TOTP secrets/codes, private **or public** passkey material, usernames, emails, account/service names, domains/URLs, Android package names derived from vault records, database identifiers, notes, master-password material, encryption/recovery/DB keys, KDF outputs, encrypted vault blobs, raw exceptions, clipboard contents, autofill payloads and decrypted vault entries are prohibited. No public-passkey exception is justified for this evaluation. The developer API key is an explicitly configured authentication credential; it is not a vault encryption key.

## Code boundary and lifecycle

The module graph is `app → advisor-contract` and **debug only** `app → advisor-evaluation → advisor-contract`. Both libraries use the existing Android/Kotlin toolchain. Neither library depends on `app`, its vault models, database, repository, clipboard or local analysis. This is a compile-time dependency boundary, not a sandbox against deliberately rewritten build files or malicious code.

- In `app/src/main/java/com/doffi4/doffisecure/`, `domain/security/LocalSecurityAnalyzer.kt` performs existing local analysis; `domain/advisor/AdvisorSanitizer.kt` copies four counts and discards all local item references.
- In `advisor-contract/src/main/java/com/doffi4/doffisecure/domain/advisor/`, `AdvisorSummary.kt` is a final four-Int DTO with range validation. `SecurityAdvisorService.kt` accepts only that DTO and an EN/RU enum; typed failures contain no exception text.
- `advisor-contract/.../AdvisorPayloadJson.kt` formats four explicitly named Int properties. It accepts no map, arbitrary object, text, bytes or exception; it does not use a reflective serializer. The UI preview and provider user message use this same writer.
- In `advisor-evaluation/src/main/java/com/doffi4/doffisecure/data/advisor/`, `ClaudeAdvisorCodec.kt` builds the fixed provider envelope and validates untrusted replies. `ClaudeSecurityAdvisorService.kt` owns the fixed origin and dedicated OkHttp client. It has no vault repository, shared interceptors, body/header logging or telemetry.
- In app, `ui/security/SecurityAdvisorController.kt` holds only DTOs/guidance. The existing `SecurityCenterViewModel` supplies sanitized summaries and clears it on every new local state. `SecurityAdvisorPanel.kt` preserves the consent/transparency/loading/result/failure UI.

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

Provision this file using a trusted development tool/Android Studio Device Explorer on a disposable test device running the debug APK. Do not put the real key in a command line/history, source tree, Gradle property, BuildConfig, resources/assets or a shared production build. Do not paste credentials into logs or public discussions. Delete the device file and revoke the disposable key after testing. The debug file is app-private but not Keystore encrypted: debug/root access can read it, so this is **not** a production key-storage solution.

The configuration file is limited to 4 KiB; keys/model are validated without logging values. Missing or blank key/model produces Missing configuration, unreadable/invalid config produces Configuration error. No fallback key or model is embedded. A configured model must support `output_config.format` structured JSON; provider/model/schema compatibility must be live-tested rather than inferred from a successful mock.

The parser checks full consumption of both the outer API object and the inner guidance object, and checks real string types instead of Android `JSONObject.getString` coercion. It uses the platform JSON parser with its syntax tolerance; the enforced contract is complete objects, exact guidance keys/types, limits and completion status, not a claim of full RFC JSON grammar validation. Android-specific coercion regressions live in instrumentation tests as well as JVM tests.

`app/src/release/.../AdvisorServiceFactory.kt` always returns `DisabledSecurityAdvisorService`. The evaluation module is connected with `debugImplementation`, so its HTTP service, developer configuration and provider codec are absent from the app release classpath even before R8. Release cannot read developer configuration or send an advisor request through this feature, even if that file exists. This does not alter the existing release signing policy, which still uses the Android Debug identity.

## Isolated offline evaluation

Use synthetic fixtures without a key or network. `advisor-contract` tests exercise the real writer and run the JDK Java compiler against the actual Kotlin/JVM API: four integers compile; strings, byte arrays, maps, objects, exceptions, extra fields and DTO subclassing do not. A positive compilation control prevents compiler/classpath failures from being mistaken for privacy protection.

`advisor-evaluation` tests capture the actual OkHttp request constructed by the service using a synthetic `Call.Factory`, then inspect the complete envelope and exact four-field user content. Existing offline, cancellation, authorization and malformed-response regressions stay with the client. `app/src/testDebug/.../AdvisorEvaluationPrivacyTest.kt` runs the real local analyzer, sanitizer and envelope writer with synthetic secret-bearing records and checks sentinel exclusion, literal payload values and snapshot detachment. A release test verifies that evaluation classes cannot be loaded.

Run `./gradlew :advisor-contract:testDebugUnitTest :advisor-evaluation:testDebugUnitTest :app:testDebugUnitTest :app:testReleaseUnitTest` (`.\gradlew.bat` on Windows). Release unit tests are enabled in the tracked app Gradle configuration; no private init script is needed to enable the release guard. No live API request is required to exercise these boundaries. Offline tests do not evaluate Claude's actual guidance quality or prove provider compatibility; live synthetic evaluation requires a separately authorized developer key/budget and remains outside this change.

## Provider documentation and privacy caveat

Integration was checked against the official [Messages API](https://platform.claude.com/docs/en/api/messages/create) and [structured outputs](https://platform.claude.com/docs/en/build-with-claude/structured-outputs) documentation on 2026-10-07. Do not promise no retention. Anthropic's [commercial retention policy](https://privacy.claude.com/en/articles/7996866-how-long-do-you-store-my-organization-s-data) describes standard deletion within 30 days with exceptions; model-specific requirements and negotiated arrangements may differ. Recheck current [API retention arrangements](https://platform.claude.com/docs/en/manage-claude/api-and-data-retention) and selected model terms before enablement. No live request or billing/eligibility check was performed.

## Production enablement

Release remains disabled pending a reviewed production credential and privacy model. A backend/proxy, if introduced, must preserve the four-field allowlist, enforce authentication and rate/spend limits, bound requests/responses, and avoid logging secrets or request bodies. Responses must remain untrusted manual guidance. No production backend is implemented.

## Verification

JVM coverage includes the exact serialized payload, consent/retry lifecycle, cancellation, authorization changes, invalid/refused/truncated responses and the release-disabled factory. Android JSON/Compose instrumentation sources exist but have not been executed. No live-provider validation is claimed. See [release verification](BUILD_VERIFICATION_1.1.md) and [Security](../SECURITY.md).
