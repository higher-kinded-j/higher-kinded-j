#!/usr/bin/env node
/*
 * Fail a cross-reference that lands nowhere.
 *
 * mdbook checks no links, so a link into a heading that has been
 * reworded, or onto a page that has been renamed, breaks silently: the reader
 * arrives at the top of the page, or at a 404, and nothing in the build says
 * so. Deep links are load-bearing here, since the glossary, the cheat sheet,
 * the skills and other chapters all point into section anchors.
 *
 * What is checked, for every markdown file under hkj-book/src:
 *   - a link to another book page resolves to a file that exists;
 *   - a fragment (`page.md#section`, or `#section` on the same page) resolves
 *     to a heading id on the target page.
 *
 * Ids follow mdbook's own rule: an explicit `{#id}` when the heading carries
 * one, otherwise the rendered heading text lowercased, spaces to hyphens,
 * everything but `[a-z0-9_-]` dropped, and a `-1`, `-2` suffix where one file
 * repeats a heading. The self-test below pins that rule against ids taken from
 * the rendered book.
 *
 * Out of scope, deliberately: external URLs, links to source files (the
 * tutorials link .java), and anything inside a fence, since an example of
 * markdown is not a link the reader can follow.
 */
"use strict";

const fs = require("fs");
const path = require("path");
const { markdownFiles, linesAsProse } = require("./book-prose.cjs");

const repoRoot = path.resolve(__dirname, "..", "..");
const bookSrc = path.join(repoRoot, "hkj-book", "src");

/** The text a heading renders to, which is what mdbook makes its id from. */
function renderedText(headingText) {
  const withoutLinks = headingText
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, "$1")
    .replace(/\[([^\]]*)\]\([^)]*\)/g, "$1")
    // Emphasis is stripped before the code spans are split out, because a
    // heading may italicise a phrase that contains one. An underscore inside a
    // word survives either way: it is part of the identifier (`ZERO_OR_MORE`).
    .replace(/(?<![A-Za-z0-9])_([^_]+)_(?![A-Za-z0-9])/g, "$1");

  // A code span is literal text, so `Deque<T>` keeps its T where a bare <T>
  // would be an HTML tag and vanish. Split on the spans and treat each side
  // by its own rules.
  return withoutLinks
    .split(/(`[^`]*`)/)
    .map((part) => {
      if (part.startsWith("`") && part.endsWith("`") && part.length > 1) {
        return part.slice(1, -1);
      }
      return (
        part
          .replace(/[*~]/g, "")
          // Raw HTML in a heading (a styled badge) is markup; `Set<? extends T>`
          // and `Free<F, A>` are not, so only a real tag name is dropped.
          .replace(/<\/?[a-zA-Z][a-zA-Z0-9-]*(?:\s[^>]*)?\/?>/g, "")
          .replace(/&lt;|&gt;|&amp;|&quot;|&#39;|&nbsp;/g, "")
      );
    })
    .join("");
}

/** mdbook's id for one heading's markdown text, before de-duplication. */
function slug(headingText) {
  const explicit = headingText.match(/\{#([^}]+)\}\s*$/);
  if (explicit) return explicit[1].trim();
  return (
    renderedText(headingText)
      .trim()
      .toLowerCase()
      // Each whitespace character becomes its own hyphen: mdbook does not
      // collapse runs, so "a,  b" keeps both.
      .replace(/\s/g, "-")
      .replace(/[^a-z0-9_-]/g, "")
  );
}

/**
 * The id one heading line gets, marker and all.
 *
 * A page may also write a heading as raw HTML (home.md's strapline). mdbook
 * gives that an id too, so a link into one resolves.
 */
function headingId(line) {
  const heading = line.match(/^#{1,6}\s+(.*)$/);
  if (heading) return slug(heading[1]);
  const html = line.match(/^\s*<h[1-6]\b[^>]*>(.*?)<\/h[1-6]>\s*$/i);
  if (html) {
    const explicit = line.match(/^\s*<h[1-6]\b[^>]*\sid="([^"]+)"/i);
    return explicit ? explicit[1] : slug(html[1]);
  }
  return null;
}

/** Every heading id a page offers, in document order, de-duplicated as mdbook does. */
function headingIds(text) {
  const seen = new Map();
  const ids = [];
  for (const { text: line } of linesAsProse(text)) {
    const base = headingId(line);
    if (!base) continue;
    const count = seen.get(base) ?? 0;
    seen.set(base, count + 1);
    ids.push(count === 0 ? base : `${base}-${count}`);
  }
  return new Set(ids);
}

/** Links worth checking: into the book, not out of it. */
function* internalLinks(text) {
  for (const { line, text: content } of linesAsProse(text)) {
    // Inline code carries example links that nobody clicks.
    const prose = content.replace(/`[^`]*`/g, "");
    for (const match of prose.matchAll(/\]\(([^)\s]+)\)/g)) {
      const target = match[1];
      if (/^(https?:|mailto:|data:|\/\/)/.test(target)) continue;
      const [file, fragment] = target.split("#");
      if (file && !file.endsWith(".md")) continue; // .java and friends
      yield { line, target, file, fragment };
    }
  }
}

function selfTest() {
  // Pinned against ids the rendered book actually carries.
  const cases = [
    ["## Sparse PATCH write-back: `UpdateSpec`", "sparse-patch-write-back-updatespec"],
    ["### Converting Map keys {#converting-map-keys}", "converting-map-keys"],
    ["## Null has an address, not a stack trace {#null-doctrine}", "null-doctrine"],
    ["# The Emission Tiers: Truthful Types", "the-emission-tiers-truthful-types"],
    ["## Law-checked, in the repo and in your tests", "law-checked-in-the-repo-and-in-your-tests"],
    ["### Shared vocabulary: mix-in interfaces", "shared-vocabulary-mix-in-interfaces"],
    ["## What the mapper will (and will not) generate", "what-the-mapper-will-and-will-not-generate"],
    ["### `@GenerateMerge`: merging several sources", "generatemerge-merging-several-sources"],
    ["### The `ZERO_OR_MORE` Asymmetry, and `widenCollections`", "the-zero_or_more-asymmetry-and-widencollections"],
    ["## _Why_ this matters", "why-this-matters"],
    ["## _Asynchronous Computations with `CompletableFuture`_", "asynchronous-computations-with-completablefuture"],
  ];
  const wrong = cases.filter(([heading, expected]) => headingId(heading) !== expected);
  if (wrong.length) {
    for (const [heading, expected] of wrong) {
      console.log(`SELF-TEST  ${JSON.stringify(heading)} -> ${headingId(heading)}, expected ${expected}`);
    }
    console.log("::error::book-anchor-check's id rule no longer matches mdbook's");
    process.exit(2);
  }

  const dupes = headingIds("## Limits\n\n## Limits\n\n## Limits\n");
  for (const id of ["limits", "limits-1", "limits-2"]) {
    if (!dupes.has(id)) {
      console.log(`::error::book-anchor-check does not de-duplicate repeated headings (${id} missing)`);
      process.exit(2);
    }
  }
}

selfTest();

const files = markdownFiles(bookSrc).sort();
const idsByFile = new Map(files.map((file) => [file, headingIds(fs.readFileSync(file, "utf8"))]));

/**
 * A page the deploy copies in from the repository root (CONTRIBUTING, LICENSE,
 * CODE_OF_CONDUCT). It is gitignored inside the book, so a fresh checkout does not have it,
 * but the deployed book does, and SUMMARY.md links it. Read the root copy instead of calling
 * the link broken.
 */
function copiedFromRoot(target) {
  const root = path.join(repoRoot, path.basename(target));
  if (path.dirname(target) !== bookSrc || !fs.existsSync(root)) return null;
  if (!idsByFile.has(root)) idsByFile.set(root, headingIds(fs.readFileSync(root, "utf8")));
  return root;
}

let failures = 0;
let checked = 0;

for (const file of files) {
  const rel = path.relative(repoRoot, file);
  for (const link of internalLinks(fs.readFileSync(file, "utf8"))) {
    let targetFile = link.file ? path.resolve(path.dirname(file), link.file) : file;
    checked++;

    if (!idsByFile.has(targetFile)) {
      if (fs.existsSync(targetFile)) continue; // a page outside src, left alone
      const copied = copiedFromRoot(targetFile);
      if (copied !== null) targetFile = copied;
    }

    if (!idsByFile.has(targetFile)) {
      failures++;
      console.log(`FAIL  ${rel}:${link.line}  no such page: ${link.target}`);
      console.log(`::error file=${rel},line=${link.line}::Link target does not exist: ${link.target}`);
      continue;
    }

    if (!link.fragment) continue;
    if (idsByFile.get(targetFile).has(link.fragment)) continue;

    failures++;
    const where = link.file ? link.file : "this page";
    console.log(`FAIL  ${rel}:${link.line}  no heading '#${link.fragment}' on ${where}`);
    console.log(
      `::error file=${rel},line=${link.line}::No heading with id '${link.fragment}' on ${where}. ` +
        `Give the heading an explicit {#${link.fragment}} if it was reworded, or fix the link.`
    );
  }
}

console.log(
  failures
    ? `${failures} of ${checked} book links resolve to nothing`
    : `${checked} book links all resolve`
);
process.exit(failures ? 1 : 0);
