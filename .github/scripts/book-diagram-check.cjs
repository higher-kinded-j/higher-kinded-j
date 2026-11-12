#!/usr/bin/env node
/*
 * Hold the chapters under the Style Guide's diagram law to it, page by page.
 *
 *   Line art lives only in an accessible <pre>. A box-drawing character
 *        (─ │ ┌ ═ ╲), a block or a shape (█ ● ▶) outside one fails, in prose,
 *        in a table or in a code fence alike: a screen reader spells it out
 *        glyph by glyph, and a fence gives it no label to read instead.
 *   An accessible <pre> is <pre class="hkj-ascii-diagram"> or
 *        class="hkj-railway-diagram", with role="img" and an aria-label that
 *        says what the drawing shows.
 *   No SVG and no UML. A drawn SVG cannot be checked against the code, and it
 *        fixes one theme's background; a mermaid diagram or a table replaces it.
 *
 * The chapters are DIAGRAM_LAW_CHAPTERS in book-prose.cjs. Two rules hold on
 * every page of the book: a <pre> must close, and no <pre> takes a style
 * attribute, which ignores the theme; a railway takes its colours from the
 * hkj-rail-* classes instead.
 *
 * A <pre> is read as CommonMark reads one: a line that starts with `<pre`
 * opens it, the tag may run over several lines, and only `</pre>` closes it.
 * A file a page pulls in with {{#include}} is read too: a Markdown file as a
 * page, and any other file, such as an example's Java, for line art. A
 * mermaid diagram's accTitle, accDescr and node classes are checked by
 * mermaid-check/check.cjs, which has the parser.
 */
"use strict";

const fs = require("fs");
const path = require("path");
const { markdownFiles, linesAsProse, diagramLawPages } = require("./book-prose.cjs");

const repoRoot = path.resolve(__dirname, "..", "..");
const bookSrc = path.join(repoRoot, "hkj-book", "src");

/** Box drawing, block elements and geometric shapes: U+2500 to U+25FF. */
const LINE_ART = /[─-◿]/;

/** A line that opens a <pre> block, as CommonMark's HTML block start condition 1 reads it. */
const PRE_OPEN = /^ {0,3}<pre(?=[\s>]|$)/i;

/** A whole opening <pre> tag, whose quoted values may hold a `>` or a line break. */
const PRE_TAG = /^ {0,3}<pre\b(?:[^>"']|"[^"]*"|'[^']*')*>/i;

const PRE_CLOSE = /<\/pre>/i;

/** An mdBook include directive, as book_sources.py's INCLUDE_RE reads one. */
const INCLUDE = /\{\{#(?:include|rustdoc_include|playground)\s+([^}]+?)\s*\}\}/g;

const DIAGRAM_CLASSES = ["hkj-ascii-diagram", "hkj-railway-diagram"];

const LAW = 'see "The Diagram Law" in docs/STYLE-GUIDE.md';

const STYLE_IN_DIAGRAM =
  "a style attribute in a <pre>, which ignores the theme: colour a railway's track with an hkj-rail-* class";

/** An attribute's value in an opening tag, or null when the tag lacks it. */
function attribute(tag, name) {
  const match = tag.match(new RegExp(`\\s${name}\\s*=\\s*(?:"([^"]*)"|'([^']*)'|([^\\s"'>]+))`, "i"));
  return match ? (match[1] ?? match[2] ?? match[3]) : null;
}

/** What makes an opening <pre> tag fall short of an accessible diagram, if anything. */
function preProblems(tag) {
  const problems = [];
  const classes = (attribute(tag, "class") ?? "").split(/\s+/);
  if (!classes.some((c) => DIAGRAM_CLASSES.includes(c))) {
    problems.push(`has no class ${DIAGRAM_CLASSES.join(" or ")}`);
  }
  if (attribute(tag, "role") !== "img") problems.push('has no role="img"');
  if (!(attribute(tag, "aria-label") ?? "").trim()) {
    problems.push("has no aria-label saying in a sentence what it shows");
  }
  return problems.map((p) => `<pre> ${p}: write <pre class="hkj-ascii-diagram" role="img" aria-label="...">`);
}

/**
 * The SVG and PlantUML files a stretch of Markdown or HTML shows or links, and
 * `<svg>` for an inline one. A code span is an example, not a drawing.
 */
function drawings(content) {
  const text = content.replace(/(`+)[^`]*?\1/g, "");
  const targets = [];
  for (const m of text.matchAll(/\]\(\s*(?:<([^>]*)>|([^)\s]+))/g)) targets.push(m[1] ?? m[2]);
  const definition = text.match(/^ {0,3}\[[^\]]+\]:\s*(?:<([^>]*)>|(\S+))/);
  if (definition) targets.push(definition[1] ?? definition[2]);
  for (const m of text.matchAll(/\b(?:src|data|href)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))/gi)) {
    targets.push(m[1] ?? m[2] ?? m[3]);
  }
  const found = targets.map((t) => t.trim()).filter((t) => /\.(svg|puml)(?:[?#].*)?$/i.test(t));
  if (/<svg[\s>]/i.test(text)) found.push("<svg>");
  return found;
}

/** A problem for line art in `content`, if it holds any. */
function lineArt(content) {
  const glyph = content.match(LINE_ART)?.[0];
  return glyph
    ? `line art (${glyph}) outside an accessible <pre>: draw it in mermaid, a table or an accessible <pre>, ` +
        `and in code or prose write -> or a word instead; ${LAW}`
    : null;
}

/**
 * Every way a page breaks the diagram law, each as `{ line, message }`, with
 * `law` false for a page outside the law's chapters, where only an unclosed
 * <pre> and a style attribute in one count. Also returns, as `includes`, the
 * files the page pulls in with {{#include}}, each with the line that names it
 * and whether that line sits inside a <pre>.
 */
function diagramProblems(text, law = true) {
  const problems = [];
  const report = (line, message, lawOnly) => problems.push({ line, message, lawOnly });
  const normalised = text.replace(/\r\n?/g, "\n");
  const lines = normalised.split("\n");
  const prose = linesAsProse(normalised);
  const inPre = new Set();
  const tails = new Map(); // a closing line's text after </pre>, which is prose again

  for (let k = 0; k < prose.length; k++) {
    if (!PRE_OPEN.test(prose[k].text)) continue;
    let end = k;
    while (end < prose.length && !PRE_CLOSE.test(prose[end].text)) end++;
    const first = prose[k].line;
    if (end === prose.length) {
      report(first, "<pre> is never closed, so the rest of the page renders as preformatted text", false);
      for (let j = k; j < prose.length; j++) inPre.add(prose[j].line);
      break;
    }
    const block = prose.slice(k, end + 1);
    const joined = block.map((b) => b.text).join("\n");
    const tag = joined.match(PRE_TAG);
    if (!tag) {
      report(first, "<pre> tag never ends with >", true);
    } else {
      for (const message of preProblems(tag[0])) report(first, message, true);
      if (attribute(tag[0], "style") !== null) report(first, STYLE_IN_DIAGRAM, false);
    }
    const bodyStart = tag ? tag.index + tag[0].length : 0;
    const bodyEnd = joined.search(PRE_CLOSE) >= bodyStart ? joined.search(PRE_CLOSE) : joined.length;
    let offset = 0;
    for (const { line, text: content } of block) {
      const from = Math.max(offset, bodyStart) - offset;
      const to = Math.min(offset + content.length, bodyEnd) - offset;
      const body = to > from ? content.slice(from, to) : "";
      if (/\sstyle\s*=/i.test(` ${body}`)) report(line, STYLE_IN_DIAGRAM, false);
      for (const target of drawings(body)) report(line, `an SVG or UML image (${target}): draw it in mermaid, or make it a table; ${LAW}`, true);
      inPre.add(line);
      offset += content.length + 1;
    }
    const closing = block[block.length - 1];
    const tail = closing.text.slice(closing.text.search(PRE_CLOSE) + "</pre>".length);
    if (tail.trim()) tails.set(closing.line, tail);
    k = end;
  }

  for (const { line, text: content } of prose) {
    const outside = inPre.has(line) ? (tails.get(line) ?? "") : content;
    for (const target of drawings(outside)) {
      report(line, `an SVG or UML image (${target}): draw it in mermaid, or make it a table; ${LAW}`, true);
    }
  }
  lines.forEach((content, i) => {
    const outside = inPre.has(i + 1) ? (tails.get(i + 1) ?? "") : content;
    const message = lineArt(outside);
    if (message) report(i + 1, message, true);
  });

  const includes = [];
  lines.forEach((content, i) => {
    for (const m of content.matchAll(INCLUDE)) {
      includes.push({ line: i + 1, target: m[1].split(":")[0], inPre: inPre.has(i + 1) });
    }
  });

  return {
    problems: problems
      .filter((problem) => law || !problem.lawOnly)
      .sort((a, b) => a.line - b.line)
      .map(({ line, message }) => ({ line, message })),
    includes,
  };
}

/**
 * Every problem on a page and in the files it includes, each as `{ file, line,
 * message }` with `file` relative to the repository. A Markdown include is
 * read as a page; any other file only for line art, unless the directive sits
 * inside a <pre>, where its text is the drawing.
 */
function pageProblems(file, law, seen = new Set()) {
  if (seen.has(file)) return [];
  seen.add(file);
  const rel = path.relative(repoRoot, file).split(path.sep).join("/");
  const { problems, includes } = diagramProblems(fs.readFileSync(file, "utf8"), law);
  const out = problems.map((p) => ({ file: rel, ...p }));
  for (const include of includes) {
    const target = path.resolve(path.dirname(file), include.target);
    if (!fs.existsSync(target) || include.inPre) continue;
    const by = ` (included by ${rel}:${include.line})`;
    if (target.endsWith(".md")) {
      for (const p of pageProblems(target, law, seen)) out.push({ ...p, message: p.message + by });
    } else if (law && !seen.has(target)) {
      seen.add(target);
      const targetRel = path.relative(repoRoot, target).split(path.sep).join("/");
      fs.readFileSync(target, "utf8")
        .replace(/\r\n?/g, "\n")
        .split("\n")
        .forEach((content, i) => {
          const message = lineArt(content);
          if (message) out.push({ file: targetRel, line: i + 1, message: message + by });
        });
    }
  }
  return out;
}

/*
 * Regression checks for the rules themselves, run on every invocation: a rule
 * silently narrowed would wave a broken page through, so each is pinned here.
 */
function selfTest() {
  const accessible = '<pre class="hkj-railway-diagram" role="img" aria-label="Two tracks.">';
  const cases = [
    { name: "an accessible <pre> holds line art", input: `${accessible}\n══●══▶\n</pre>\n`, expect: [] },
    {
      name: "the ASCII class serves as well as the railway class",
      input: '<pre class="hkj-ascii-diagram" role="img" aria-label="A box.">\n┌─┐\n</pre>\n',
      expect: [],
    },
    { name: "line art in an untagged fence", input: "```\n┌─┐\n```\n", expect: [2] },
    { name: "line art in a text fence", input: "```text\nA ──▶ B\n```\n", expect: [2] },
    { name: "line art in prose", input: "Success ● then ▶ done\n", expect: [1] },
    { name: "line art in a Java fence", input: "```java\n// ┌─┐\n```\n", expect: [2] },
    { name: "an arrow is not line art", input: "A → B, and A ⇒ B\n", expect: [] },
    { name: "a <pre> without role", input: '<pre class="hkj-ascii-diagram" aria-label="x">\n┌\n</pre>\n', expect: [1] },
    { name: "a <pre> without aria-label", input: '<pre class="hkj-ascii-diagram" role="img">\n┌\n</pre>\n', expect: [1] },
    {
      name: "a <pre> with an empty aria-label",
      input: '<pre class="hkj-ascii-diagram" role="img" aria-label=" ">\n┌\n</pre>\n',
      expect: [1],
    },
    { name: "a <pre> without a diagram class", input: '<pre role="img" aria-label="x">\n┌\n</pre>\n', expect: [1] },
    {
      name: "an inline colour inside a diagram",
      input: `${accessible}\n<span style="color:#4CAF50">══</span>\n</pre>\n`,
      expect: [2],
    },
    { name: "a class colours a track", input: `${accessible}\n<span class="hkj-rail-ok">══</span>\n</pre>\n`, expect: [] },
    { name: "an unclosed <pre>", input: `${accessible}\n══\n`, expect: [1] },
    { name: "an SVG image", input: "![types](../images/puml/either_type.svg)\n", expect: [1] },
    { name: "an SVG in an img tag", input: '<img src="../images/a.svg" alt="">\n', expect: [1] },
    { name: "an SVG link", input: "See [the types](../images/a.svg).\n", expect: [1] },
    { name: "an SVG reference definition", input: "![types][t]\n\n[t]: ../images/a.svg\n", expect: [3] },
    { name: "an SVG path in angle brackets", input: "![t](<my types.svg>)\n", expect: [1] },
    { name: "an unquoted src", input: "<img src=a.puml>\n", expect: [1] },
    { name: "an inline <svg>", input: '<svg viewBox="0 0 1 1"></svg>\n', expect: [1] },
    { name: "an SVG named in a code span is an example", input: "Delete `either_type.svg` and `<svg>`.\n", expect: [] },
    { name: "a PNG image is not a drawing", input: "![hero](../images/foundations.webp)\n", expect: [] },
    { name: "a <pre> shown in a fence is an example", input: "```html\n<pre>\n```\n", expect: [] },
    { name: "a <pre> named mid-line is not a block", input: "Draw it in a `<pre>`.\n\n| a | ── |\n", expect: [3] },
    { name: "line art after a <pre> closes", input: `${accessible}\n══\n</pre>\n\nA ── B\n`, expect: [5] },
    { name: "line art after </pre> on the same line", input: `${accessible}\n══\n</pre> ──\n`, expect: [3] },
    { name: "CRLF lines", input: "```\r\n┌─┐\r\n```\r\n", expect: [2] },
    {
      name: "a style on the <pre> itself",
      input: '<pre class="hkj-railway-diagram" role="img" aria-label="x" style="color:red">\n══\n</pre>\n',
      expect: [1],
    },
    {
      name: "a style after the tag on its own line",
      input: `${accessible}<span style="color:#F44336">══</span>\n</pre>\n`,
      expect: [1],
    },
    { name: "a one-line <pre>", input: `${accessible}<span class="hkj-rail-ok">══</span></pre>\n`, expect: [] },
    {
      name: "a tag over several lines",
      input: '<pre class="hkj-railway-diagram"\n     role="img"\n     aria-label="Two tracks.">\n══\n</pre>\n',
      expect: [],
    },
    { name: "a > inside a quoted label", input: '<pre class="hkj-ascii-diagram" role="img" aria-label="parse -> save">\n┌\n</pre>\n', expect: [] },
    { name: "a fence marker inside a <pre> is text", input: `${accessible}\n\`\`\`\n══\n</pre>\n\nok\n`, expect: [] },
    {
      name: "outside the law, only a style counts",
      law: false,
      input: "```\n┌─┐\n```\n<pre>\n<span style=\"color:#F44336\">══</span>\n</pre>\n![x](a.svg)\n",
      expect: [5],
    },
    { name: "outside the law, a bare <pre> of line art passes", law: false, input: "<pre>\n══\n</pre>\n", expect: [] },
    { name: "outside the law, a style on the <pre> counts", law: false, input: '<pre style="color:red">\n══\n</pre>\n', expect: [1] },
    { name: "outside the law, an unclosed <pre> counts", law: false, input: "<pre>\n══\n", expect: [1] },
  ];
  for (const c of cases) {
    const got = diagramProblems(c.input, c.law ?? true).problems.map((p) => p.line);
    if (JSON.stringify(got) !== JSON.stringify(c.expect)) {
      console.error(
        `self-test failed: ${c.name}\n  expected lines ${JSON.stringify(c.expect)}\n  got            ${JSON.stringify(got)}`
      );
      process.exit(2);
    }
  }
  const includes = diagramProblems(`${accessible}\n{{#include art.txt}}\n</pre>\n{{#include ../A.java:anchor}}\n`).includes;
  const expected = [
    { line: 2, target: "art.txt", inPre: true },
    { line: 4, target: "../A.java", inPre: false },
  ];
  if (JSON.stringify(includes) !== JSON.stringify(expected)) {
    console.error(`self-test failed: includes\n  expected ${JSON.stringify(expected)}\n  got      ${JSON.stringify(includes)}`);
    process.exit(2);
  }
}

function main() {
  selfTest();
  const lawPages = diagramLawPages(bookSrc);
  const pages = markdownFiles(bookSrc)
    .map((file) => path.relative(bookSrc, file).split(path.sep).join("/"))
    .sort();
  const problems = pages.flatMap((page) => pageProblems(path.join(bookSrc, page), lawPages.has(page)));
  for (const { file, line, message } of problems) {
    console.log(`FAIL  ${file}:${line}  ${message}`);
    console.log(`::error file=${file},line=${line}::${message}`);
  }
  console.log(
    problems.length
      ? `${problems.length} diagram ${problems.length === 1 ? "problem" : "problems"}: ${LAW}`
      : `${lawPages.size} pages keep the diagram law, and no <pre> on any of ${pages.length} pages takes a style attribute`
  );
  process.exit(problems.length ? 1 : 0);
}

main();
