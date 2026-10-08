import { readdir, readFile, stat } from "node:fs/promises";
import { resolve, dirname, sep } from "node:path";
import { fileURLToPath } from "node:url";
import config from "../site.config.mjs";
import { deployment } from "./build.mjs";
const output = resolve(dirname(fileURLToPath(import.meta.url)), "../dist");
const { base, origin } = deployment(
  process.env.SITE_URL ?? config.siteUrl,
  process.env.BASE_PATH ?? config.basePath,
);
let checked = 0;
for (const name of await readdir(output)) {
  if (!name.endsWith(".html")) continue;
  const html = await readFile(resolve(output, name), "utf8");
  if (html.includes("{{")) throw new Error(`Unresolved placeholder: ${name}`);
  if ((html.match(/<h1\b/g) ?? []).length !== 1)
    throw new Error(`Expected one h1: ${name}`);
  if (!html.includes('lang="en"') || !html.includes('name="description"'))
    throw new Error(`Missing metadata: ${name}`);
  for (const match of html.matchAll(/\b(?:href|src)="([^"]+)"/g)) {
    const value = match[1];
    if (/^(https:|mailto:)/.test(value)) continue;
    const [path, fragment] = value.split("#");
    if (path && !path.startsWith(base))
      throw new Error(`Wrong deployment base in ${name}: ${value}`);
    const file = path
      ? resolve(output, path.slice(base.length) || "index.html")
      : resolve(output, name);
    if (!file.startsWith(output + sep) || !(await stat(file)).isFile())
      throw new Error(`Missing resource in ${name}: ${value}`);
    if (fragment && file.endsWith(".html")) {
      const target = await readFile(file, "utf8");
      if (!target.includes(`id="${fragment}"`))
        throw new Error(`Missing anchor in ${name}: ${value}`);
    }
    checked++;
  }
  if (origin) {
    if (
      !html.includes(`${origin}${base}assets/social.png`) ||
      !html.includes('rel="canonical"')
    )
      throw new Error(`Missing production metadata in ${name}`);
  } else if (
    !html.includes("noindex,nofollow") ||
    html.includes('rel="canonical"')
  )
    throw new Error(`Unexpected public metadata: ${name}`);
}
const robots = await readFile(resolve(output, "robots.txt"), "utf8");
if (origin) {
  const sitemap = await readFile(resolve(output, "sitemap.xml"), "utf8");
  if (
    !robots.includes(`${origin}${base}sitemap.xml`) ||
    !sitemap.includes(`${origin}${base}security.html`)
  )
    throw new Error("Wrong sitemap/robots base");
} else if (!robots.includes("Disallow: /"))
  throw new Error("Preview indexing enabled");
console.log(
  `Verified ${checked} local links/assets/anchors across 8 pages, metadata and robots${origin ? "/sitemap" : " preview defaults"}.`,
);
