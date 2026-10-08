import { readdir, readFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
for (const folder of ["scripts", "src"]) {
  for (const name of await readdir(resolve(root, folder))) {
    if (/\.(mjs|js)$/.test(name)) {
      const result = spawnSync(
        process.execPath,
        ["--check", resolve(root, folder, name)],
        { encoding: "utf8" },
      );
      if (result.status !== 0) throw new Error(result.stderr);
    }
  }
}
const template = await readFile(resolve(root, "src/index.html"), "utf8");
if ((template.match(/<h1\b/g) ?? []).length !== 1)
  throw new Error("Landing must have one h1");
if (/onclick=|<iframe|<form|https:\/\/.*\.(?:js|css)/.test(template))
  throw new Error("Unexpected interaction or external resource");
const css = await readFile(resolve(root, "src/styles.css"), "utf8");
if (!css.includes(":focus-visible") || !css.includes("prefers-reduced-motion"))
  throw new Error("Missing accessibility rules");
console.log(
  "JS syntax, landing structure, no third-party embeds and focus/motion rules checked.",
);
