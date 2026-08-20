#!/usr/bin/env node
/*
 * Parse-check every ```mermaid fence in hkj-book/src against the pinned
 * browser-side mermaid library, so a diagram syntax error fails CI instead of
 * reaching the published book as a render-time error box.
 *
 * Mechanics: mermaid.min.js (fetched by ../fetch_mermaid.sh) is evaluated with
 * vm.runInThisContext under a jsdom window, matching browser script semantics
 * (a plain eval misses globalThis in strict mode, and state diagrams need a
 * DOM for DOMPurify). Each fence is then fed to mermaid.parse().
 */
"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const { JSDOM } = require("jsdom");

const repoRoot = path.resolve(__dirname, "..", "..", "..");
const bookSrc = path.join(repoRoot, "hkj-book", "src");
const mermaidJs = path.join(repoRoot, "hkj-book", "mermaid.min.js");

if (!fs.existsSync(mermaidJs)) {
  console.error(
    `mermaid.min.js not found at ${mermaidJs}; run .github/scripts/fetch_mermaid.sh first`
  );
  process.exit(2);
}

const dom = new JSDOM("<!doctype html><html><body></body></html>", {
  url: "http://localhost/",
  pretendToBeVisual: true,
});
// Some of these exist as getter-only globals on newer Node versions, so
// assign via defineProperty and tolerate the ones that refuse.
for (const [name, value] of Object.entries({
  window: dom.window,
  document: dom.window.document,
  navigator: dom.window.navigator,
  DOMParser: dom.window.DOMParser,
  XMLSerializer: dom.window.XMLSerializer,
  SVGElement: dom.window.SVGElement,
})) {
  try {
    Object.defineProperty(globalThis, name, { value, configurable: true });
  } catch {
    // keep the platform-provided global
  }
}

vm.runInThisContext(fs.readFileSync(mermaidJs, "utf8"), {
  filename: "mermaid.min.js",
});
const mermaid = globalThis.mermaid;

function markdownFiles(dir) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...markdownFiles(p));
    else if (entry.isFile() && entry.name.endsWith(".md")) out.push(p);
  }
  return out;
}

function mermaidFences(source) {
  const fences = [];
  const re = /^```mermaid[ \t]*\n([\s\S]*?)^```[ \t]*$/gm;
  let match;
  while ((match = re.exec(source)) !== null) {
    const line = source.slice(0, match.index).split("\n").length;
    fences.push({ body: match[1], line });
  }
  return fences;
}

(async () => {
  let diagrams = 0;
  let failures = 0;
  for (const file of markdownFiles(bookSrc).sort()) {
    const rel = path.relative(repoRoot, file);
    for (const fence of mermaidFences(fs.readFileSync(file, "utf8"))) {
      diagrams++;
      try {
        await mermaid.parse(fence.body);
        console.log(`OK    ${rel}:${fence.line}`);
      } catch (e) {
        failures++;
        const message = String(e.message || e).split("\n").slice(0, 3).join(" | ");
        console.log(`FAIL  ${rel}:${fence.line}  ${message}`);
        console.log(
          `::error file=${rel},line=${fence.line}::mermaid parse error: ${message}`
        );
      }
    }
  }
  console.log(
    failures
      ? `${failures} of ${diagrams} mermaid diagrams failed to parse`
      : `${diagrams} mermaid diagrams parse cleanly`
  );
  process.exit(failures ? 1 : 0);
})();
