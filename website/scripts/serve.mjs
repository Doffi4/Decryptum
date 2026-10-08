import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { resolve, extname, dirname, sep } from "node:path";
import { fileURLToPath } from "node:url";
import config from "../site.config.mjs";
import { deployment } from "./build.mjs";
const { base } = deployment(
  process.env.SITE_URL ?? config.siteUrl,
  process.env.BASE_PATH ?? config.basePath,
);
const output = resolve(dirname(fileURLToPath(import.meta.url)), "../dist");
/** @type {Record<string, string>} */
const types = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".webp": "image/webp",
  ".png": "image/png",
  ".txt": "text/plain",
  ".md": "text/plain; charset=utf-8",
  ".xml": "application/xml",
};
createServer(async (request, response) => {
  try {
    const pathname = decodeURIComponent(
      new URL(request.url ?? "/", "http://localhost").pathname,
    );
    // Development-only QA asset; never copied to dist or referenced by product HTML.
    if (pathname === "/__qa/axe.js") {
      const bytes = await readFile(
        resolve(output, "../node_modules/axe-core/axe.min.js"),
      );
      response
        .writeHead(200, {
          "Content-Type": "text/javascript; charset=utf-8",
          "X-Content-Type-Options": "nosniff",
        })
        .end(bytes);
      return;
    }
    if (!pathname.startsWith(base)) {
      response.writeHead(404).end("Not found");
      return;
    }
    const relative = pathname.slice(base.length);
    const file = resolve(output, relative || "index.html");
    if (!file.startsWith(output + sep)) {
      response.writeHead(403).end("Forbidden");
      return;
    }
    const bytes = await readFile(file);
    response
      .writeHead(200, {
        "Content-Type": types[extname(file)] ?? "application/octet-stream",
        "X-Content-Type-Options": "nosniff",
      })
      .end(bytes);
  } catch {
    response.writeHead(404).end("Not found");
  }
}).listen(Number(process.env.PORT ?? 4173), "127.0.0.1", () =>
  console.log(
    `Local preview: http://127.0.0.1:${process.env.PORT ?? 4173}${base}`,
  ),
);
