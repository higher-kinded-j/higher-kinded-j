#!/usr/bin/env node
/*
 * Fail a heading that hands raw HTML to the browser.
 *
 * A heading quoting a compiler diagnostic often names a generic type, and a
 * bare `<S>` in markdown is not text: it is passed through as HTML. The damage
 * depends on whether the tag happens to be a real element.
 *
 *   <S>  is <s>, the strikethrough element. Nothing closes it, so every block
 *        after the heading renders struck through.
 *   <T>  is no element at all. The browser swallows the tag and renders its
 *        children, so the type argument silently disappears from the page.
 *
 * The fix in both cases is a code span: `OpticsSpec<S>`.
 *
 * Scope is headings only, deliberately. Prose carries intentional raw HTML in
 * this book (the <pre class="hkj-railway-diagram"> blocks build their art from
 * <span> and <b>), so widening this check would be noise rather than signal.
 * An opening tag carrying attributes is left alone for the same reason: that
 * shape is always deliberate, as in reading.md's styled badge.
 */
"use strict";

const fs = require("fs");
const path = require("path");
const { markdownFiles, linesAsProse } = require("./book-prose.cjs");

const repoRoot = path.resolve(__dirname, "..", "..");
const bookSrc = path.join(repoRoot, "hkj-book", "src");

/**
 * Headings the reader meets: those outside code fences, including the ones in
 * an admonition's body, which mdbook-admonish renders as real headings.
 */
function headingsOutsideFences(text) {
  return linesAsProse(text).filter(({ text: line }) => line.startsWith("#"));
}

let failures = 0;
let scanned = 0;

for (const file of markdownFiles(bookSrc).sort()) {
  const rel = path.relative(repoRoot, file);
  for (const heading of headingsOutsideFences(fs.readFileSync(file, "utf8"))) {
    scanned++;
    // Code spans are the fix, so anything already inside one is fine.
    const bare = heading.text.replace(/`[^`]*`/g, "");
    for (const match of bare.matchAll(/<([A-Za-z][A-Za-z0-9]*)>/g)) {
      failures++;
      const tag = match[1];
      const effect =
        tag.toLowerCase() === "s"
          ? "renders as <s>, so this heading and everything after it is struck through"
          : `is read as an HTML tag, so "<${tag}>" is swallowed and vanishes from the page`;
      console.log(`FAIL  ${rel}:${heading.line}  <${tag}> ${effect}`);
      console.log(`      ${heading.text.trim()}`);
      console.log(
        `::error file=${rel},line=${heading.line}::<${tag}> in a heading ${effect}. Put the type in a code span, as \`Foo<${tag}>\`.`
      );
    }
  }
}

console.log(
  failures
    ? `${failures} heading(s) pass raw HTML to the browser`
    : `${scanned} headings carry no raw HTML`
);
process.exit(failures ? 1 : 0);
