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

/*
 * Collect mermaid fences the way the mdbook-mermaid preprocessor's CommonMark
 * parser sees them: any fence of three or more backticks or tildes, indented
 * up to three spaces, whose info string names mermaid. A line-based scan also
 * tracks non-mermaid fences, so a ```mermaid shown INSIDE a wider example
 * fence is documentation, not a diagram, and is not collected.
 */
function mermaidFences(rawSource) {
  // Normalise CRLF so a Windows-authored file cannot slip its fences past
  // the gate; line numbers are unaffected.
  const lines = rawSource.replace(/\r\n/g, "\n").split("\n");
  const fences = [];
  let open = null; // { char, len, indent, mermaid, line, body }
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (open === null) {
      const m = line.match(/^( {0,3})(`{3,}|~{3,})(.*)$/);
      if (!m) continue;
      const [, indent, marker, rest] = m;
      const info = rest.trim();
      // A backtick fence's info string may not contain a backtick (CommonMark).
      if (marker[0] === "`" && info.includes("`")) continue;
      open = {
        char: marker[0],
        len: marker.length,
        indent: indent.length,
        mermaid: /^mermaid(\s|$)/.test(info),
        line: i + 1,
        body: [],
      };
    } else {
      const closer = line.match(/^ {0,3}(`{3,}|~{3,})[ \t]*$/);
      if (closer && closer[1][0] === open.char && closer[1].length >= open.len) {
        if (open.mermaid) {
          fences.push({ body: open.body.join("\n") + "\n", line: open.line });
        }
        open = null;
      } else if (open.mermaid) {
        // CommonMark strips up to the opening fence's indentation.
        open.body.push(line.replace(new RegExp(`^ {0,${open.indent}}`), ""));
      }
    }
  }
  return fences;
}

/*
 * Regression checks for the scanner itself, run on every invocation: a
 * silently narrowed scanner would wave broken diagrams through, so its
 * matching rules are pinned here.
 */
function selfTest() {
  const cases = [
    {
      name: "plain three-backtick fence",
      input: "x\n```mermaid\nflowchart TD\n```\ny",
      expect: ["flowchart TD\n"],
    },
    {
      name: "CRLF fence",
      input: "x\r\n```mermaid\r\nflowchart TD\r\n```\r\n",
      expect: ["flowchart TD\n"],
    },
    {
      name: "indented fence (two spaces)",
      input: "  ```mermaid\n  flowchart TD\n  ```\n",
      expect: ["flowchart TD\n"],
    },
    {
      name: "four-backtick fence",
      input: "````mermaid\nflowchart TD\n````\n",
      expect: ["flowchart TD\n"],
    },
    {
      name: "tilde fence",
      input: "~~~mermaid\nflowchart TD\n~~~\n",
      expect: ["flowchart TD\n"],
    },
    {
      name: "mermaid inside a wider example fence is not a diagram",
      input: "````markdown\n```mermaid\nflowchart TD\n```\n````\n",
      expect: [],
    },
    {
      name: "non-mermaid fence is ignored",
      input: "```java\nint x;\n```\n",
      expect: [],
    },
    {
      name: "four-space indent is an indented code block, not a fence",
      input: "    ```mermaid\n    flowchart TD\n    ```\n",
      expect: [],
    },
  ];
  for (const c of cases) {
    const got = mermaidFences(c.input).map((f) => f.body);
    if (JSON.stringify(got) !== JSON.stringify(c.expect)) {
      console.error(
        `self-test failed: ${c.name}\n  expected ${JSON.stringify(c.expect)}\n  got      ${JSON.stringify(got)}`
      );
      process.exit(2);
    }
  }
}
selfTest();

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
