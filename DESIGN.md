# Decryptum design context

Kotlin/Compose source defines the Android UI. App locales are EN/RU. The static website follows the same visual identity.

## Runtime token ownership

`ui/theme/Theme.kt`, `Color.kt`, `Type.kt` own Material 3 typography, shapes and semantic colors. Use `MaterialTheme.colorScheme`, typography and shapes; dynamic color on Android 12+, existing fallback palettes otherwise. No new palette/font/token adapter.

## Canonical controls and layout

Native Compose Material 3 Cards, Buttons, TextButtons, AlertDialog and progress indicators own interaction semantics and focus. Existing SecurityCenterScreen LazyColumn owns scrolling, 16dp outer padding, 12dp card spacing. Cards use large/extraLarge theme shapes and 16/20dp padding. Existing bottom navigation and routes stay intact.

Local analysis is labelled with the existing CloudOff/primary treatment. Optional AI guidance uses an outlined surface card, AutoAwesome icon and secondary label. Guidance is never presented as a verified local finding or security score. Provider text is plain text without clickable links, tools or automated actions.

## Advisor interaction contract

No default opt-in or persistent consent. Open guidance -> per-request consent -> optional exact-data dialog -> send once or decline -> loading/cancel -> checklist or specific failure/retry. Data dialog returns to consent; no send button inside the data dialog. Consent and data dialog scroll at large text sizes. Refresh/edit/lock/stop clears consent and cancels remote work. Retry opens a new consent dialog. Missing configuration explains that local analysis remains available.

All product copy lives in EN/RU strings.xml; language sent to Claude is a fixed EN/RU instruction selected from the app locale. Numbers in the JSON preview retain exact machine values. AI output is untrusted and labelled accordingly.

## Verification

JVM tests verify privacy boundary, transport and lifecycle. Compose instrumentation covers intro, decline, exact payload, loading/cancel, result and configuration/offline failures; compilation is not runtime verification. Real device light/dark, dynamic color, accessibility, font scaling and offline traffic checks remain required if no device is available.

## Static website adapter

`website/src/styles.css` adapts current Android `Color.kt` roles: dark background #111318, foreground #E2E2E9, muted #C4C6D0, low/high surface #191C20/#282A2F, primary/onPrimary #AAC7FF/#0A305F, outlineVariant #44474E, tertiary #A5D0B9; light counterparts #F9F9FF, #191C20, #44474E, #F3F3FA/#E7E8EE, #415F91/#FFFFFF, #C4C6D0, #3E6655. Android runtime ownership/dynamic colors are unchanged. System sans, tonal surfaces, rounded cards/capsule links and restrained spacing carry Material Privacy into a spacious editorial landing. No remote fonts or generated app screenshots.

Native links/buttons/details own website navigation, theme and FAQ keyboard behavior. Shared build shell owns header/footer/skip link; global CSS owns role tokens, focus and scrollbars. Default dark theme; optional script stores only a light/dark preference. Static content works without JavaScript. Earlier screenshots are labelled and are not current fallback-color evidence. Build-time Markdown rendering keeps public policy pages tied to source documents. Local preview is noindex; the Pages workflow supplies the public HTTPS origin.

## Password entry and tab behavior (v1.1.0)

`ui/password/PasswordEntrySheet.kt` owns the scoped create form; native Material 3 ModalBottomSheet/OutlinedTextField/Button and the current Theme.kt roles remain canonical. Natural-height content scrolls within the sheet when the keyboard or larger text constrains space. Required service/login/password labels and Next/Done actions, 48dp action targets, masked password/show-hide, existing optional PasswordStrengthBadge and GeneratePasswordUseCase reuse the current app behavior. No fixed palette/font, extra credential schema or network operation was added.

The create draft lives only in remember state, never rememberSaveable/Bundle. Submission waits for AddPasswordUseCase, disables repeat writes/dismissal while pending, clears the draft and closes on success, preserves it with localized inline retry copy on failure, and does not render exception text. Service/login outer whitespace is trimmed; password contents are unchanged. Existing snackbar handles success. Cancellation/back/close discards the transient draft, matching the existing create-dialog policy.

`MainTabNavigation.kt` owns main-tab and Security Center generator-shortcut navigation. Start-tab restoration is disabled because non-inclusive popUpTo can alias another saved stack to that destination; other main-tab root states remain restorable, while restored detail/security children are popped on an explicit tab press. NavHost screen crossfades are disabled, tab surfaces are opaque, and tab selectable feedback is clipped to CircleShape; the existing capsule indicator animation is preserved.
