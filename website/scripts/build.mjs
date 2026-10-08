import { readFile, writeFile, mkdir, cp, rm } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { marked } from "marked";
import config from "../site.config.mjs";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const repo = resolve(root, "..");
const output = resolve(root, "dist");
export const pages = [
  ["security", "Security", "SECURITY.md"],
  ["privacy", "Privacy", "PRIVACY.md"],
  ["threat-model", "Threat model", "docs/THREAT_MODEL.md"],
  ["product", "Product & roadmap", "docs/PRODUCT.md"],
  ["security-center", "Local Security Center", "docs/SECURITY_CENTER.md"],
  ["security-advisor", "Optional Security Advisor", "docs/SECURITY_ADVISOR.md"],
  ["contributing", "Contributing", "CONTRIBUTING.md"],
];
/** @param {string} value */
export const escape = (value) =>
  value.replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ] ?? c,
  );
/** @param {string} siteUrl @param {string} basePath */
export function deployment(siteUrl, basePath) {
  if (!/^\/(?:[A-Za-z0-9_-]+\/)*$/.test(basePath))
    throw new Error("basePath must be / or /path/");
  if (!siteUrl) return { base: basePath, origin: "" };
  const url = new URL(siteUrl);
  if (
    url.protocol !== "https:" ||
    url.username ||
    url.password ||
    url.search ||
    url.hash ||
    url.pathname !== "/"
  ) {
    throw new Error(
      "siteUrl must be an HTTPS origin, e.g. https://your-domain; set subpaths in basePath",
    );
  }
  return { base: basePath, origin: url.origin };
}
const { base, origin } = deployment(
  process.env.SITE_URL ?? config.siteUrl,
  process.env.BASE_PATH ?? config.basePath,
);
if (
  !/^https:\/\/github\.com\/[A-Za-z0-9_-]+\/[A-Za-z0-9_.-]+$/.test(
    config.repository,
  )
)
  throw new Error("Invalid repository URL");
if (
  config.contactEmail &&
  !/^[A-Za-z0-9._+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(config.contactEmail)
)
  throw new Error("Invalid contact mailbox");
/** @param {string} path */
const sourceUrl = (path) => `${config.repository}/blob/main/${path}`;
const localSources = [
  ...pages.map((p) => p[2]),
  "docs/LAUNCH_AUDIT.md",
  "docs/PHASE6_HANDOFF.md",
  "docs/LAUNCH_CHECKLIST.md",
  "docs/RELEASE_NOTES_1.1.md",
  "docs/PUBLIC_PRESENTATION.md",
  "docs/screenshots/README.md",
  "website/README.md",
  "DESIGN.md",
];
const header = `<a class="skip-link" href="#main">Skip to content</a><header class="header"><div class="wrap header-inner"><a class="brand" href="${base}" aria-label="Decryptum home"><img src="${base}assets/icon.webp" width="36" height="36" alt="">Decryptum</a><nav aria-label="Main navigation"><a href="${base}#features">Features</a><a href="${base}#privacy">Privacy</a><a href="${base}#security">Security</a><a href="${config.repository}">GitHub ↗</a></nav><button class="theme-toggle" type="button" data-theme-toggle hidden>Light theme</button></div></header>`;
const contact = config.contactEmail
  ? `<a href="mailto:${escape(config.contactEmail)}">Contact</a>`
  : "<span>Contact mailbox not configured</span>";
const footer = `<footer class="footer"><div class="wrap"><div class="footer-inner"><div><a class="brand" href="${base}">Decryptum</a><p>Local-first Android essentials. Open development.</p></div><nav aria-label="Footer navigation"><a href="${config.repository}">GitHub</a><a href="${base}security.html">Security</a><a href="${base}privacy.html">Privacy</a><a href="${config.repository}/releases">Releases</a><a href="${sourceUrl("LICENSE")}">GPLv3</a><a href="${base}contributing.html">Contribute</a></nav></div><p class="footer-note">Current development source · ${contact} · No site analytics</p></div></footer>`;
/** @param {string} title @param {string} description @param {string} path @param {string} body */
function document(title, description, path, body) {
  const canonical = origin ? `${origin}${base}${path}` : "";
  return `<!doctype html><html lang="en" data-theme="dark"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${escape(title)}</title><meta name="description" content="${escape(description)}"><meta name="color-scheme" content="dark light"><meta name="theme-color" content="#111318"><meta name="robots" content="${origin ? "index,follow" : "noindex,nofollow"}"><meta name="referrer" content="strict-origin-when-cross-origin"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src 'self'; style-src 'self'; script-src 'self'; font-src 'self'; connect-src 'none'; base-uri 'none'; form-action 'none'"><meta property="og:type" content="website"><meta property="og:title" content="${escape(title)}"><meta property="og:description" content="${escape(description)}"><meta property="og:site_name" content="Decryptum">${canonical ? `<link rel="canonical" href="${canonical}"><meta property="og:url" content="${canonical}"><meta property="og:image" content="${origin}${base}assets/social.png"><meta property="og:image:width" content="1200"><meta property="og:image:height" content="630"><meta name="twitter:card" content="summary_large_image">` : ""}<link rel="icon" href="${base}assets/favicon.png" type="image/png"><link rel="apple-touch-icon" href="${base}assets/favicon.png"><link rel="stylesheet" href="${base}styles.css"><script src="${base}theme.js"></script></head><body>${header}${body}${footer}</body></html>`;
}
/** @param {string} content @param {string} source */
function markdown(content, source) {
  const renderer = new marked.Renderer();
  renderer.heading = ({ tokens, depth }) => {
    const text = renderer.parser.parseInline(tokens);
    const id = text
      .replace(/<[^>]*>/g, "")
      .toLowerCase()
      .replace(/[^a-z0-9\s-]/g, "")
      .trim()
      .replace(/\s+/g, "-");
    return `<h${depth} id="${id}">${text}</h${depth}>`;
  };
  renderer.table = (token) =>
    `<div class="table-scroll" tabindex="0" role="region" aria-label="Scrollable documentation table">${marked.Renderer.prototype.table.call(renderer, token)}</div>`;
  renderer.code = (token) =>
    marked.Renderer.prototype.code
      .call(renderer, token)
      .replace(
        "<pre>",
        '<pre tabindex="0" role="region" aria-label="Scrollable code example">',
      );
  renderer.link = ({ href, title, tokens }) => {
    if (!/^(https:\/\/|#|mailto:)/.test(href)) {
      const [rawPath, fragment] = href.split("#");
      const path = resolve(repo, dirname(source), rawPath)
        .slice(repo.length + 1)
        .replaceAll("\\", "/");
      const page = pages.find((p) => p[2] === path);
      href = page
        ? `${base}${page[0]}.html${fragment ? `#${fragment}` : ""}`
        : localSources.includes(path)
          ? `${base}source/${path}`
          : sourceUrl(path) + (fragment ? `#${fragment}` : "");
    }
    if (!/^(https:\/\/|#|mailto:|\/)/.test(href))
      throw new Error(`Unsafe link in ${source}`);
    return `<a href="${escape(href)}"${title ? ` title="${escape(title)}"` : ""}>${renderer.parser.parseInline(tokens)}</a>`;
  };
  // Only maintained local Markdown is a build input. Raw HTML is escaped, not executed.
  renderer.html = ({ text }) => escape(text);
  return marked.parse(content, { renderer, async: false });
}
export async function build() {
  // Deletion is restricted to this script's fixed generated output directory.
  if (output !== resolve(root, "dist")) throw new Error("Unsafe output path");
  await rm(output, { recursive: true, force: true });
  await mkdir(output, { recursive: true });
  await cp(resolve(root, "public"), output, { recursive: true });
  await cp(resolve(root, "src/styles.css"), resolve(output, "styles.css"));
  await cp(resolve(root, "src/theme.js"), resolve(output, "theme.js"));
  for (const source of localSources) {
    const target = resolve(output, "source", source);
    await mkdir(dirname(target), { recursive: true });
    await cp(resolve(repo, source), target);
  }
  const template = await readFile(resolve(root, "src/index.html"), "utf8");
  const home = template
    .replaceAll("{{base}}", base)
    .replaceAll("{{repository}}", config.repository);
  await writeFile(
    resolve(output, "index.html"),
    document(
      "Decryptum — Your passwords. Your keys. Your device.",
      "Local-first Android password manager and TOTP authenticator. Open-source development, local security checks, and clear privacy boundaries.",
      "",
      home,
    ),
  );
  for (const [slug, title, source] of pages) {
    const content = await readFile(resolve(repo, source), "utf8");
    const body = `<main id="main" class="doc wrap"><div class="doc-source"><a href="${base}">← Back to Decryptum</a><a href="${base}source/${source}">Source Markdown: ${source} ↗</a></div><p class="fine">Rendered from the current local source. Published APKs and GitHub may differ.</p>${markdown(content, source)}</main>`;
    await writeFile(
      resolve(output, `${slug}.html`),
      document(
        `${title} — Decryptum`,
        `Decryptum ${title.toLowerCase()}: current implementation, boundaries and limitations.`,
        `${slug}.html`,
        body,
      ),
    );
  }
  await writeFile(resolve(output, ".nojekyll"), "");
  await writeFile(
    resolve(output, "robots.txt"),
    origin
      ? `User-agent: *\nAllow: /\nSitemap: ${origin}${base}sitemap.xml\n`
      : "User-agent: *\nDisallow: /\n",
  );
  if (origin) {
    const urls = ["", ...pages.map((p) => `${p[0]}.html`)];
    await writeFile(
      resolve(output, "sitemap.xml"),
      `<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${urls.map((path) => `<url><loc>${escape(origin + base + path)}</loc></url>`).join("")}</urlset>`,
    );
  }
  console.log(
    `Built ${pages.length + 1} static pages. ${origin ? `Public metadata: ${origin}${base}` : "Preview: noindex; no invented domain/canonical/sitemap."}`,
  );
}
if (
  process.argv[1] &&
  resolve(process.argv[1]) === fileURLToPath(import.meta.url)
)
  await build();
