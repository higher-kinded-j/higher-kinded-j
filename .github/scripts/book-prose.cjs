/*
 * How the book's checks read a page: which files are pages, and which lines
 * are prose a reader meets rather than code they copy.
 *
 * Shared by book-anchor-check.cjs, which resolves the links in that prose, and
 * book-heading-html-check.cjs, which checks its headings. One reading, so the
 * checks can never disagree about where a fence ends.
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
 */
function linesAsProse(text) {
  const out = [];
  let code = null;
  let admonitions = 0;
  text.split("\n").forEach((line, i) => {
    const marker = line.match(/^\s*(`{3,}|~{3,})(.*)$/);
    if (marker) {
      const [char, len, info] = [marker[1][0], marker[1].length, marker[2].trim()];
      if (code) {
        if (char === code.char && len >= code.len && !info) code = null;
        return;
      }
      if (char === "~" && info.startsWith("admonish")) {
        admonitions++;
        return;
      }
      if (char === "~" && !info && admonitions > 0) {
        admonitions--;
        return;
      }
      code = { char, len };
      return;
    }
    if (!code) out.push({ line: i + 1, text: line });
  });
  return out;
}

module.exports = { markdownFiles, linesAsProse };
