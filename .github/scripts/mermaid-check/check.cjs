#!/usr/bin/env node
/*
 * Parse-check every ```mermaid fence in hkj-book/src against the pinned
 * browser-side mermaid library, so a diagram syntax error fails CI instead of
 * reaching the published book as a render-time error box.
 *
 * A diagram that parses is then read for what the Style Guide asks of it:
 *   - every diagram declares an accTitle and an accDescr, since without them a
 *     screen reader announces the node labels and none of the arrows;
 *   - in a chapter under the diagram law (DIAGRAM_LAW_CHAPTERS in
 *     ../book-prose.cjs), every node of a flowchart or state diagram takes a
 *     classDef that sets its fill and its text colour, so no node inherits a
 *     page background that one theme makes unreadable.
 * ../book-diagram-check.cjs holds those chapters' other diagrams to the law.
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
const { diagramLawPages } = require("../book-prose.cjs");

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

/** Nodes that take no fill of their own: start and end dots, fork and join bars, notes. */
const UNFILLED_SHAPES = new Set(["stateStart", "stateEnd", "fork", "join", "divider", "note"]);

/** Whether a node's classDefs, taken together, set both a fill and a text colour. */
function coloursBoth(classes, names) {
  const styles = names.flatMap((name) => {
    const definition = classes instanceof Map ? classes.get(name) : classes[name];
    return definition?.styles ?? [];
  });
  return ["fill", "color"].every((property) =>
    styles.some((style) => style.trim().startsWith(`${property}:`))
  );
}

/**
 * What a parsed diagram lacks, as messages. `law` says whether its page is in
 * a chapter under the diagram law, where every node must be classed.
 *
 * Mermaid lists a flowchart's or a state diagram's nodes, nested states
 * included, in getData(); a subgraph or a composite state is a group, which
 * takes no class. Mermaid adds classes of its own, such as `statediagram-state`,
 * and gives every flowchart node `default`, which counts only when the diagram
 * declares `classDef default`.
 */
async function diagramProblems(body, law) {
  const diagram = await mermaid.mermaidAPI.getDiagramFromText(body);
  const db = diagram.db;
  const problems = [];
  if (!(db.getAccTitle?.() ?? "").trim()) {
    problems.push('no accTitle: add "accTitle: <a short name>" under the diagram type');
  }
  if (!(db.getAccDescription?.() ?? "").trim()) {
    problems.push('no accDescr: add "accDescr: <what it shows, arrows included, in words>"');
  }
  if (law && /^(flowchart|stateDiagram)/.test(diagram.type) && db.getData) {
    const classes = db.getClasses();
    const unclassed = db
      .getData()
      .nodes.filter((node) => !node.isGroup && !UNFILLED_SHAPES.has(node.shape))
      .filter((node) => !coloursBoth(classes, String(node.cssClasses ?? "").split(/\s+/).filter(Boolean)))
      .map((node) => node.id);
    if (unclassed.length) {
      problems.push(
        `no classDef with a fill and a color on ${unclassed.join(", ")}: ` +
          "class them from the Style Guide's house palette"
      );
    }
  }
  return problems;
}

/*
 * Regression checks for what a parsed diagram is held to, run on every
 * invocation like the scanner's.
 */
async function diagramSelfTest() {
  const NO_TITLE = 'no accTitle: add "accTitle: <a short name>" under the diagram type';
  const NO_DESCR = 'no accDescr: add "accDescr: <what it shows, arrows included, in words>"';
  const unclassed = (nodes) =>
    `no classDef with a fill and a color on ${nodes}: class them from the Style Guide's house palette`;
  const acc = "  accTitle: T\n  accDescr: D\n";
  const ok = "  classDef ok fill:#a6d189,stroke:#40a02b,color:#232634\n";
  const cases = [
    { name: "a classed, described flowchart", law: true, input: `flowchart TD\n${acc}  A --> B\n${ok}  class A,B ok\n`, expect: [] },
    { name: "no accTitle", law: false, input: "flowchart TD\n  accDescr: D\n  A --> B\n", expect: [NO_TITLE] },
    { name: "no accDescr", law: false, input: "flowchart TD\n  accTitle: T\n  A --> B\n", expect: [NO_DESCR] },
    { name: "a block accDescr", law: false, input: "flowchart TD\n  accTitle: T\n  accDescr {\n    D\n  }\n  A --> B\n", expect: [] },
    { name: "a sequence diagram needs both", law: true, input: "sequenceDiagram\n  A->>B: hi\n", expect: [NO_TITLE, NO_DESCR] },
    { name: "a described sequence diagram takes no classes", law: true, input: `sequenceDiagram\n${acc}  A->>B: hi\n`, expect: [] },
    {
      name: "an unclassed node under the law",
      law: true,
      input: `flowchart TD\n${acc}  A --> B\n${ok}  class A ok\n`,
      expect: [unclassed("B")],
    },
    { name: "an unclassed node outside the law", law: false, input: `flowchart TD\n${acc}  A --> B\n`, expect: [] },
    {
      name: "a classDef with no text colour",
      law: true,
      input: `flowchart TD\n${acc}  A\n  classDef pale fill:#a6d189\n  class A pale\n`,
      expect: [unclassed("A")],
    },
    { name: "the ::: shorthand", law: true, input: `flowchart TD\n${acc}  A:::ok --> B:::ok\n${ok}`, expect: [] },
    {
      name: "a subgraph joined by ~~~ is a group",
      law: true,
      input: `flowchart TD\n${acc}  subgraph S["s"]\n    A\n  end\n  S ~~~ B\n${ok}  class A,B ok\n`,
      expect: [],
    },
    {
      name: "a fill from one class and a color from another",
      law: true,
      input: `flowchart TD\n${acc}  A\n  classDef f fill:#a6d189\n  classDef c color:#232634\n  class A f\n  class A c\n`,
      expect: [],
    },
    { name: "classDef default colours every node", law: true, input: `flowchart TD\n${acc}  A --> B\n  classDef default fill:#8caaee,color:#232634\n`, expect: [] },
    {
      name: "a state diagram's start, end and composite take no class",
      law: true,
      input: `stateDiagram-v2\n${acc}  [*] --> A\n  A --> C\n  state C {\n    [*] --> D\n    D --> [*]\n  }\n  A --> [*]\n${ok}  class A,D ok\n`,
      expect: [],
    },
    {
      name: "a nested state is checked",
      law: true,
      input: `stateDiagram-v2\n${acc}  [*] --> A\n  state C {\n    [*] --> D\n  }\n${ok}  class A ok\n`,
      expect: [unclassed("D")],
    },
  ];
  // Run twice, so a diagram that inherited the last one's accTitle would show.
  for (const c of [...cases, ...cases]) {
    await mermaid.parse(c.input);
    const got = await diagramProblems(c.input, c.law);
    if (JSON.stringify(got) !== JSON.stringify(c.expect)) {
      console.error(
        `self-test failed: ${c.name}\n  expected ${JSON.stringify(c.expect)}\n  got      ${JSON.stringify(got)}`
      );
      process.exit(2);
    }
  }
}

(async () => {
  await diagramSelfTest();
  const lawPages = diagramLawPages(bookSrc);
  let diagrams = 0;
  let failures = 0;
  for (const file of markdownFiles(bookSrc).sort()) {
    const rel = path.relative(repoRoot, file);
    const law = lawPages.has(path.relative(bookSrc, file).split(path.sep).join("/"));
    for (const fence of mermaidFences(fs.readFileSync(file, "utf8"))) {
      diagrams++;
      let problems;
      try {
        await mermaid.parse(fence.body);
        problems = await diagramProblems(fence.body, law);
      } catch (e) {
        failures++;
        const message = String(e.message || e).split("\n").slice(0, 3).join(" | ");
        console.log(`FAIL  ${rel}:${fence.line}  ${message}`);
        console.log(
          `::error file=${rel},line=${fence.line}::mermaid parse error: ${message}`
        );
        continue;
      }
      if (problems.length) {
        failures++;
        const message = problems.join("; ");
        console.log(`FAIL  ${rel}:${fence.line}  ${message}`);
        console.log(`::error file=${rel},line=${fence.line}::${message}`);
      } else {
        console.log(`OK    ${rel}:${fence.line}`);
      }
    }
  }
  console.log(
    failures
      ? `${failures} of ${diagrams} mermaid diagrams failed`
      : `${diagrams} mermaid diagrams parse cleanly, each with an accTitle and an accDescr`
  );
  process.exit(failures ? 1 : 0);
})();
