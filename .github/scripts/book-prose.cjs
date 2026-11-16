/*
 * How the book's checks read a page: which files are pages, which lines are
 * prose a reader meets rather than code they copy, and which chapter a page
 * belongs to.
 *
 * Shared by book-anchor-check.cjs, which resolves the links in that prose,
 * book-heading-html-check.cjs, which checks its headings,
 * book-readability-check.cjs, which measures it, and book-diagram-check.cjs and
 * mermaid-check/check.cjs, which hold the diagram law's chapters to it. One
 * reading, so the checks can never disagree about where a fence ends.
 */
"use strict";

const fs = require("fs");
const path = require("path");

/** Every .md under a directory, depth first. */
function markdownFiles(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return markdownFiles(full);
    return entry.isFile() && entry.name.endsWith(".md") ? [full] : [];
  });
}

/**
 * Lines the reader meets as prose, with their 1-based numbers.
 *
 * Two kinds of block open with a marker and they behave differently. A code
 * fence hides everything until its own marker closes it. An `~~~admonish`
 * block does not: mdbook-admonish renders its body as markdown, so the
 * headings inside one are real headings with real ids, and the links inside
 * one are real links. The book has no tilde-fenced code block, so a bare `~~~`
 * closes the admonition it follows.
 *
 * A `<pre>` block is prose from its opening line to its `</pre>`, as
 * CommonMark reads an HTML block that starts with `<pre`: a fence marker
 * inside one is text, never a fence.
 *
 * With `{ markers: true }`, the line that opens a fence is kept as `{ line,
 * text, marker: "code" }`, and both lines of an admonition as `marker:
 * "admonition"`, for a reader that needs to know where prose was interrupted.
 */
function linesAsProse(text, { markers = false } = {}) {
  const out = [];
  let code = null;
  let admonitions = 0;
  let pre = false;
  text.split("\n").forEach((line, i) => {
    if (pre) {
      out.push({ line: i + 1, text: line });
      if (/<\/pre>/i.test(line)) pre = false;
      return;
    }
    const marker = line.match(/^\s*(`{3,}|~{3,})(.*)$/);
    if (marker) {
      const [char, len, info] = [marker[1][0], marker[1].length, marker[2].trim()];
      if (code) {
        if (char === code.char && len >= code.len && !info) code = null;
        return;
      }
      if (char === "~" && info.startsWith("admonish")) {
        admonitions++;
        if (markers) out.push({ line: i + 1, text: line, marker: "admonition" });
        return;
      }
      if (char === "~" && !info && admonitions > 0) {
        admonitions--;
        if (markers) out.push({ line: i + 1, text: line, marker: "admonition" });
        return;
      }
      code = { char, len };
      if (markers) out.push({ line: i + 1, text: line, marker: "code" });
      return;
    }
    if (code) return;
    out.push({ line: i + 1, text: line });
    if (/^ {0,3}<pre(?=[\s>]|$)/i.test(line) && !/<\/pre>/i.test(line)) pre = true;
  });
  return out;
}

/** A SUMMARY.md entry: optional list marker, then [Title](path.md). */
const SUMMARY_ENTRY = /^(\s*)(-\s+)?\[([^\]]*)\]\(([^)]+\.md)\)/;

/**
 * Name a chapter after its first page: the directory, else the file stem, so
 * `mapping/ch_intro.md` gives `mapping` and `reference_intro.md` gives
 * `reference`.
 */
function chapterSlug(page) {
  const directory = path.posix.dirname(page);
  if (directory !== ".") return directory.split("/")[0];
  const stem = path.posix.basename(page, ".md").replace(/_intro$/, "");
  return stem.replace(/_/g, "-").toLowerCase();
}

/**
 * The chapters of SUMMARY.md in reading order, each `{ slug, title, pages }`
 * with its pages as paths under the source directory. A top-level entry starts
 * a chapter, and every entry indented under it belongs to that chapter; a page
 * listed twice keeps its first place. This is book_sources.py's read_summary,
 * so a chapter's slug is the same name in the Python and JavaScript checks.
 */
function summaryChapters(bookSrc) {
  const chapters = [];
  const seen = new Set();
  let current = null;
  const summary = fs.readFileSync(path.join(bookSrc, "SUMMARY.md"), "utf8");
  for (const line of summary.replace(/\r\n?/g, "\n").split("\n")) {
    const match = line.match(SUMMARY_ENTRY);
    if (!match) continue;
    const [, indent, , title, target] = match;
    if (/^(https?:\/\/|#)/.test(target.trim())) continue;
    const page = path.posix.normalize(target.trim());
    if (current === null || !indent) {
      current = { slug: chapterSlug(page), title: title.trim(), pages: [] };
      chapters.push(current);
    }
    if (seen.has(page)) continue;
    seen.add(page);
    current.pages.push(page);
  }
  return chapters;
}

/**
 * The chapters held to the Style Guide's diagram law, by slug: Foundations
 * (`hkts`) and Optics. A chapter joins once every one of its pages complies;
 * its chapter guide then says so.
 */
const DIAGRAM_LAW_CHAPTERS = ["hkts", "optics"];

/** The pages of the chapters held to the diagram law, as paths under the source directory. */
function diagramLawPages(bookSrc) {
  const chapters = summaryChapters(bookSrc).filter((c) => DIAGRAM_LAW_CHAPTERS.includes(c.slug));
  const missing = DIAGRAM_LAW_CHAPTERS.filter((slug) => !chapters.some((c) => c.slug === slug));
  if (missing.length) {
    throw new Error(
      `SUMMARY.md has no chapter named ${missing.join(", ")}: ` +
        "update DIAGRAM_LAW_CHAPTERS in .github/scripts/book-prose.cjs"
    );
  }
  return new Set(chapters.flatMap((c) => c.pages));
}

module.exports = {
  markdownFiles,
  linesAsProse,
  summaryChapters,
  DIAGRAM_LAW_CHAPTERS,
  diagramLawPages,
};
