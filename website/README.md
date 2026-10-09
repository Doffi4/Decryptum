# Decryptum static website

**Public site: [https://doffi4.github.io/Decryptum/](https://doffi4.github.io/Decryptum/).**

Build-time Node.js (22+) and Marked render maintained repository Markdown into static HTML. No runtime framework, backend, external fonts, analytics or client dependency bundle. The local browser script handles the optional theme control and progressive section entrances. CSS owns shared interaction motion, native FAQ expansion and the reading indicator. The site copy is English, matching the canonical public README; the app has EN/RU strings.

```sh
npm ci
npm run lint
npm run typecheck
npm test
npm run build
npm run check:links
npm run preview
```

Open `http://127.0.0.1:4173`. Preview server binds only to loopback. Output is `dist/`; do not use the development server as public production hosting.

## Files and ownership

- `src/index.html`: factual landing copy and semantic sections.
- `src/styles.css`: responsive Material Privacy roles adapted from Android `Color.kt`; dark default, light toggle, keyboard/reduced-motion/high-contrast behavior.
- `src/theme.js`: optional localStorage theme preference and IntersectionObserver entrances; keyboard focus reveals content immediately. The page remains readable with JS disabled. Reduced-motion and print show all content without movement.
- `scripts/build.mjs`: build, shared page shell, Markdown render, relative-link mapping, configurable metadata/robots/sitemap.
- `public/assets/`: existing app icon, optimized earlier vault/TOTP screenshots and a social card.
- `site.config.mjs`: repository, optional real origin, base path and verified mailbox.
- `scripts/check.mjs`, `build.test.mjs`, `jsconfig.json`: syntax/structure, deployment configuration regressions, JS typecheck.
- `scripts/verify-output.mjs`: local links/assets/anchors and preview/production metadata. `npm run format` applies Prettier; lint verifies it. The loopback-only preview serves `/__qa/axe.js` for temporary browser audits; no QA code is linked from or included in the production output.

Security/Privacy/Product/Threat Model and center/advisor pages are rendered directly from repository Markdown to prevent parallel policy copies. Raw Markdown HTML is escaped. These build inputs must remain reviewed local source; do not feed arbitrary untrusted Markdown into the build.

## GitHub Pages publication

`.github/workflows/pages.yml` installs locked dependencies, checks lint/types/tests, builds and verifies production links/metadata, then deploys `dist/` through GitHub Pages. It runs on relevant pushes to `main` and can be started manually from Actions. Actions are pinned to verified official commit revisions. The site URL is also set as the repository homepage.

Production uses `SITE_URL=https://doffi4.github.io` and `BASE_PATH=/Decryptum/`. Local previews retain the empty origin/root base defaults below. No custom domain, DNS change or mailbox is configured.

## Other hosting / local preview

The Pages workflow publishes reviewed source changes automatically. Outside that workflow, default `siteUrl=''` means noindex, robots disallow, no invented canonical/OG URL/sitemap. Before publication, review claims, screenshots, disclosure contact and release status; configure an owned HTTPS **origin** in `site.config.mjs` or `SITE_URL`. Do not assume any proposed domain is available. Set `basePath`/`BASE_PATH` to `/` or e.g. `/Decryptum/` for project GitHub Pages. The build then generates absolute canonical/social metadata and a sitemap.

```powershell
# Example values only; use a hostname you actually control.
$env:SITE_URL='https://your-owned-domain.example'
$env:BASE_PATH='/'
npm run build
```

For Pages upload the generated static output with `.nojekyll`; for Cloudflare Pages/Netlify/Vercel use build `npm ci && npm run build` from `website`, output `dist`, and no SPA rewrites. All routes are actual `.html` files. Review the host's logs/privacy settings and headers; serve HTTPS and add `X-Content-Type-Options: nosniff` and `Content-Security-Policy: frame-ancestors 'none'` as response headers if supported. The HTML supplies a restrictive CSP for script/style/image/network policy. Custom-domain purchase/DNS and mailbox setup are separate hosting decisions.

## Screenshots

The existing `docs/screenshots/vault.png` and `totp.png` are retained images, not new device captures; their exact build/device and whether every account was synthetic are unverified. They contain no visible passwords, TOTP seeds or private keys; the displayed historical OTP is not a recovery secret. No image is evidence of current implementation. Earlier screenshot colors are wallpaper-derived and may differ from the current fallback palette. `autofill.png` (account addresses) and `generator.png` (plaintext value) were excluded from this presentation.

The web icon is exported from current Android adaptive vector resources, not the stale Android-placeholder mipmap. Source Markdown is shipped under `dist/source/` to provide downloadable policy and product documentation.
