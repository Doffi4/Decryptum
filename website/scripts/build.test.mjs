import { test } from "node:test";
import assert from "node:assert/strict";
import { deployment } from "./build.mjs";
test("preview has no invented origin", () =>
  assert.deepEqual(deployment("", "/"), { base: "/", origin: "" }));
test("custom domain and project Pages preserve subpath", () =>
  assert.deepEqual(deployment("https://owner.example", "/Decryptum/"), {
    base: "/Decryptum/",
    origin: "https://owner.example",
  }));
test("reject credentials, non-HTTPS and URL fragments/query/path", () => {
  for (const value of [
    "http://owner.example",
    "https://user:pass@owner.example",
    "https://owner.example?track=1",
    "https://owner.example#x",
    "https://owner.example/sub/",
  ])
    assert.throws(() => deployment(value, "/"));
});
test("reject unsafe or ambiguous deployment paths", () => {
  for (const value of [
    "//evil.example/",
    "/x/../",
    "/x",
    "/x?y/",
    "/<script>/",
  ])
    assert.throws(() => deployment("", value));
});
