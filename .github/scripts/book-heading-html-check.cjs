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

const repoRoot = path.resolve(__dirname, "..", "..");
const bookSrc = path.join(repoRoot, "hkj-book", "src");

/** Every .md under hkj-book/src, depth first. */
function markdownFiles(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) return markdownFiles(full);
    return entry.isFile() && entry.name.endsWith(".md") ? [full] : [];
  });
}

/**
 * A fence closes only on its own marker, so the ``` blocks nested inside a
 * ~~~admonish block do not end it. Tracking the open marker rather than a
 * boolean is what keeps the two kinds from cancelling each other out.
 */
function headingsOutsideFences(text) {
  const headings = [];
  let fence = null;
  text.split("\n").forEach((line, i) => {
    const marker = line.match(/^\s*(`{3,}|~{3,})/);
    if (marker) {
      const [char, len] = [marker[1][0], marker[1].length];
      if (!fence) fence = { char, len };
      else if (char === fence.char && len >= fence.len) fence = null;
      return;
    }
    if (!fence && line.startsWith("#")) headings.push({ line: i + 1, text: line });
  });
  return headings;
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
