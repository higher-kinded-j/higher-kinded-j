#!/usr/bin/env node
/*
 * Measure the book's prose against the style guide's readability limits, page
 * by page, and compare it with a committed baseline.
 *
 * For each page under hkj-book/src it counts what docs/STYLE-GUIDE.md asks a
 * reviewer to check:
 *
 *   long35      sentences over 35 words (Prose Limits: the practical lane's ceiling)
 *   long50      sentences over 50 words (Prose Limits: the ceiling everywhere)
 *   bullets     bullets over 60 words (Prose Limits: the fine-print bullet ceiling)
 *   runs        unbroken prose runs over 400 words (Page size: a heading does not
 *               break a run; code, a table or a diagram does)
 *   dashes      em dashes, and the spaced ` – ` and ` -- ` separators (General
 *               Principles)
 *   aboveBelow  "above" and "below" (Prose Limits: name the destination)
 *
 * Only the book's own prose counts. Code, tables, headings, HTML comments,
 * includes, images and diagrams are left out, and so is an epigraph, a
 * quotation whose words are someone else's: a blockquote with an attribution
 * line (`> — Author`). An inline code span counts as one word, as the guide
 * says. Two pages are not measured: SUMMARY.md, the sidebar's list of links,
 * and release-history.md, an append-only log whose released entries are never
 * rewritten, so a ceiling on them could only ever be spent.
 *
 * The counts are a ratchet, not a verdict: every page is measured, reference
 * pages and fine print included, so a rise asks for a look rather than a
 * rewrite.
 *
 * Usage (a page is named by its path under hkj-book/src):
 *   node .github/scripts/book-readability-check.cjs                report against the baseline
 *   node .github/scripts/book-readability-check.cjs --strict       fail when a count rises
 *   node .github/scripts/book-readability-check.cjs --update       lower the baseline to today's counts
 *   node .github/scripts/book-readability-check.cjs --accept PAGE  raise one page's ceiling to today's
 *   node .github/scripts/book-readability-check.cjs --page PAGE    list what a page's counts are made of
 *
 * --update only ever lowers a ceiling, adds a new page and drops a page that is
 * gone; a rise is never written by it. Raising a page takes --accept, which
 * says which counts it raised, so the reason can go in the commit message.
 *
 * A rise never fails the report without --strict; it is printed with the
 * page's sentences over the limit, as a warning, and in the job summary. A
 * fall is printed too, with the reminder to run --update so the gain holds.
 */
"use strict";

const fs = require("fs");
const path = require("path");
const { markdownFiles, linesAsProse } = require("./book-prose.cjs");

const repoRoot = path.resolve(__dirname, "..", "..");
const bookSrc = path.join(repoRoot, "hkj-book", "src");
const baselineFile = path.join(__dirname, "book-readability-baseline.json");

const METRICS = ["long35", "long50", "bullets", "runs", "dashes", "aboveBelow"];
const LABELS = {
  long35: "sentences over 35 words",
  long50: "sentences over 50 words",
  bullets: "bullets over 60 words",
  runs: "prose runs over 400 words",
  dashes: "em dashes and spaced dashes",
  aboveBelow: "'above' and 'below'",
};

/** The sidebar's list of links, and the append-only release log. */
const UNMEASURED = new Set(["SUMMARY.md", "release-history.md"]);

/** Abbreviations whose full stop does not end a sentence. */
const ABBREVIATIONS = /\b(e\.g|i\.e|vs|cf)\.$/i;

/** A blockquote line that attributes a quotation: `> — Author`. */
const ATTRIBUTION = /^>\s*(—|–|--)\s/;

/** A line that starts an image or a hand-built diagram. */
const PICTURE = /^(!\[|<img\b|<svg\b|<picture\b)/i;

/** A code span, blanked to the same length so a column still points at the same place. */
function maskCode(text) {
  return text.replace(/`[^`]*`/g, (span) => "`" + " ".repeat(span.length - 2) + "`");
}

/**
 * Inline markup reduced to the words a reader reads: a link becomes its text,
 * a code span one placeholder word, emphasis and tags go.
 */
function plain(text) {
  return text
    .replace(/`[^`]*`/g, " CODE ")
    .replace(/<!--.*?-->/g, " ")
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, " ")
    .replace(/\[([^\]]*)\]\([^)]*\)/g, "$1")
    .replace(/\[([^\]]*)\]\[[^\]]*\]/g, "$1")
    .replace(/<\/?[a-zA-Z][^>]*>/g, " ")
    .replace(/(\*\*|__|\*|_)(?=\S)|(?<=\S)(\*\*|__|\*|_)/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

function words(text) {
  return text.split(" ").filter((w) => /[A-Za-z0-9]/.test(w));
}

/** Sentences in one paragraph or bullet, split after . ! or ? before the next word. */
function sentences(text) {
  const out = [];
  let current = "";
  for (const part of text.split(/(?<=[.!?]["')\]]?)\s+(?=["'(\[]?[A-Za-z0-9])/)) {
    current = current ? `${current} ${part}` : part;
    if (!ABBREVIATIONS.test(current)) {
      out.push(current);
      current = "";
    }
  }
  if (current) out.push(current);
  return out;
}

const LIST_ITEM = /^([-*+]|\d+[.)])\s+(.*)$/;

/** Line numbers of every blockquote that carries an attribution: the epigraphs. */
function epigraphLines(text) {
  const skip = new Set();
  let block = [];
  const flush = () => {
    if (block.some(({ line }) => ATTRIBUTION.test(line.trim()))) {
      block.forEach(({ n }) => skip.add(n));
    }
    block = [];
  };
  text.split("\n").forEach((line, i) => {
    if (/^\s*>/.test(line)) block.push({ n: i + 1, line });
    else flush();
  });
  flush();
  return skip;
}

/**
 * A page's prose as units: each paragraph or bullet, with its first line,
 * whether it is a bullet, and the run it belongs to. A run is the prose between
 * two things that break it: code, a table, an include or a picture.
 */
function units(text) {
  const out = [];
  const epigraphs = epigraphLines(text);
  let unit = null;
  let run = 0;
  let inComment = false;
  let inPre = false;
  const runStart = new Map();

  const close = () => {
    if (unit && unit.text.trim()) {
      if (!runStart.has(unit.run)) runStart.set(unit.run, unit.line);
      unit.runLine = runStart.get(unit.run);
      out.push(unit);
    }
    unit = null;
  };
  const breakRun = () => {
    close();
    run++;
  };

  for (const { line, text: raw, marker } of linesAsProse(text, { markers: true })) {
    if (marker === "code") {
      breakRun();
      continue;
    }
    if (marker === "admonition") {
      // An admonition is a box around more prose: it ends a paragraph, not a run.
      close();
      continue;
    }

    // Comments are found with the code spans blanked, so a `<!--` quoted in
    // one does not open a comment.
    let body = raw;
    if (inComment) {
      const end = maskCode(body).indexOf("-->");
      if (end < 0) continue;
      inComment = false;
      body = body.slice(end + 3);
    }
    const masked = maskCode(body);
    const open = masked.lastIndexOf("<!--");
    if (open >= 0 && masked.indexOf("-->", open) < 0) {
      inComment = true;
      body = body.slice(0, open);
    }
    const trimmed = body.trim();

    if (inPre) {
      if (/<\/pre>/i.test(trimmed)) inPre = false;
      continue;
    }
    if (/^<pre\b/i.test(trimmed)) {
      breakRun();
      if (!/<\/pre>/i.test(trimmed)) inPre = true;
      continue;
    }
    if (!trimmed) {
      close();
      continue;
    }
    if (/^\|/.test(trimmed) || /^\{\{#/.test(trimmed) || PICTURE.test(trimmed)) {
      breakRun();
      continue;
    }
    if (/^#{1,6}\s/.test(trimmed) || /^(-{3,}|\*{3,}|_{3,})$/.test(trimmed)) {
      close();
      continue;
    }
    if (/^<\/?[a-zA-Z]/.test(trimmed)) {
      close();
      continue;
    }
    if (epigraphs.has(line)) {
      close();
      continue;
    }
    const content = trimmed.replace(/^>\s?/, "");
    if (!content) {
      close();
      continue;
    }

    const item = content.match(LIST_ITEM);
    if (item) {
      close();
      unit = { line, text: item[2], bullet: true, run };
    } else if (unit) {
      unit.text += ` ${content}`;
    } else {
      unit = { line, text: content, bullet: false, run };
    }
  }
  close();
  return out;
}

/** The six counts for one page, and what each is made of. */
function measure(text) {
  const counts = Object.fromEntries(METRICS.map((m) => [m, 0]));
  const detail = Object.fromEntries(METRICS.map((m) => [m, []]));
  const runWords = new Map();
  const runLines = new Map();
  let prose = 0;

  for (const u of units(text)) {
    const flat = plain(u.text);
    const n = words(flat).length;
    prose += n;
    runWords.set(u.run, (runWords.get(u.run) || 0) + n);
    runLines.set(u.run, u.runLine);

    for (const s of sentences(flat)) {
      const w = words(s).length;
      if (w > 35) {
        counts.long35++;
        detail.long35.push({ line: u.line, size: w, text: s });
      }
      if (w > 50) {
        counts.long50++;
        detail.long50.push({ line: u.line, size: w, text: s });
      }
    }
    if (u.bullet && n > 60) {
      counts.bullets++;
      detail.bullets.push({ line: u.line, size: n, text: flat });
    }
    const dashes = (u.text.replace(/`[^`]*`/g, "").match(/—| – | -- /g) || []).length;
    if (dashes) {
      counts.dashes += dashes;
      detail.dashes.push({ line: u.line, size: dashes, text: flat });
    }
    const directions = (flat.match(/\b(above|below)\b/gi) || []).length;
    if (directions) {
      counts.aboveBelow += directions;
      detail.aboveBelow.push({ line: u.line, size: directions, text: flat });
    }
  }
  for (const [run, n] of runWords) {
    if (n > 400) {
      counts.runs++;
      detail.runs.push({ line: runLines.get(run), size: n, text: `a run of ${n} words` });
    }
  }
  return { counts, detail, prose };
}

function measureBook() {
  const pages = {};
  for (const file of markdownFiles(bookSrc).sort()) {
    const rel = path.relative(bookSrc, file).split(path.sep).join("/");
    if (!UNMEASURED.has(rel)) pages[rel] = measure(fs.readFileSync(file, "utf8"));
  }
  return pages;
}

function selfTest() {
  const fail = (what) => {
    console.log(`::error::book-readability-check self-test: ${what}`);
    process.exit(2);
  };
  const expect = (what, page, want) => {
    const { counts } = measure(page.join("\n"));
    for (const [metric, n] of Object.entries(want)) {
      if (counts[metric] !== n) fail(`${what}: ${metric} is ${counts[metric]}, expected ${n}`);
    }
  };
  const forty = Array.from({ length: 40 }, (_, i) => `word${i}`).join(" ");
  const hundred = Array.from({ length: 5 }, () => `${forty} ${forty}.`).join(" ");

  expect("sentences and bullets", [`A sentence of forty words, ${forty}.`, "", `- ${forty} ${forty}.`], {
    long35: 2,
    long50: 1,
    bullets: 1,
  });
  expect("code, tables and comments are not prose", ["```java", `int x; // ${forty}`, "```", "", `| a | ${forty} |`, "", `<!-- ${forty} -->`], {
    long35: 0,
  });
  expect("a code span quoting a comment marker", ["Write `<!--` to open one.", "", `Then ${forty}.`], { long35: 1 });
  expect("an epigraph, but not a plain blockquote", [
    `> "A quoted line — ${forty}."`,
    ">",
    "> — Niklaus Wirth",
    "",
    `> Our own aside, ${forty}.`,
  ], { long35: 1, dashes: 0 });
  expect("dashes and directions", ["It works — mostly. A label – then text. As shown above."], { dashes: 2, aboveBelow: 1 });
  expect("a run spans headings and admonitions, not code or pictures", [
    hundred,
    "## Heading",
    "~~~admonish note",
    hundred,
    "~~~",
    hundred,
    "",
    "![a diagram](d.svg)",
    "",
    hundred,
  ], { runs: 1 });
  if (sentences("Use e.g. this one. javac says so. And then that.").length !== 3) fail("sentence splitting");
  if (words(plain("See `Foo.bar(x, y)` now")).length !== 3) fail("a code span is not one word");
}

function readBaseline() {
  if (!fs.existsSync(baselineFile)) return {};
  return JSON.parse(fs.readFileSync(baselineFile, "utf8")).pages || {};
}

const ZERO = Object.fromEntries(METRICS.map((m) => [m, 0]));

function ceilingOf(baseline, rel) {
  return { ...ZERO, ...(baseline[rel] || {}) };
}

/** One line per page, so a diff of the baseline names the page it changed. */
function writeBaseline(pages) {
  const rows = Object.keys(pages)
    .sort()
    .filter((rel) => METRICS.some((m) => pages[rel][m] > 0))
    .map((rel) => {
      const counts = METRICS.map((m) => `"${m}": ${pages[rel][m]}`).join(", ");
      return `    ${JSON.stringify(rel)}: { ${counts} }`;
    });
  const about =
    "Per-page ceilings for book-readability-check.cjs. A page not listed has a ceiling of zero. " +
    "--update lowers ceilings and drops pages that are gone; --accept PAGE raises one page.";
  const doc = [
    "{",
    `  "about": ${JSON.stringify(about)},`,
    `  "metrics": ${JSON.stringify(LABELS)},`,
    '  "pages": {',
    rows.join(",\n"),
    "  }",
    "}",
    "",
  ].join("\n");
  fs.writeFileSync(baselineFile, doc);
  return rows.length;
}

function summary(lines) {
  const file = process.env.GITHUB_STEP_SUMMARY;
  if (file) fs.appendFileSync(file, `${lines.join("\n")}\n`);
}

const FLAGS = new Set(["--strict", "--update", "--accept", "--page"]);

function pageArgument(args, flag, measured) {
  const given = args[args.indexOf(flag) + 1];
  if (!given || given.startsWith("--")) {
    console.log(`${flag} needs a page, named by its path under hkj-book/src`);
    return null;
  }
  const rel = given.replace(/^\.?\/?hkj-book\/src\//, "");
  if (UNMEASURED.has(rel)) {
    console.log(`${rel} is not measured`);
    return null;
  }
  if (!measured[rel]) {
    console.log(`No page ${rel} under hkj-book/src`);
    return null;
  }
  return rel;
}

function main() {
  selfTest();
  const args = process.argv.slice(2);
  const unknown = args.filter((a) => a.startsWith("--") && !FLAGS.has(a));
  if (unknown.length) {
    console.log(`Unknown option ${unknown.join(", ")}; expected one of ${[...FLAGS].join(", ")}`);
    return 2;
  }
  const measured = measureBook();
  const baseline = readBaseline();
  const repoPath = (rel) => `hkj-book/src/${rel}`;

  if (args.includes("--page")) {
    const rel = pageArgument(args, "--page", measured);
    if (!rel) return 2;
    const m = measured[rel];
    console.log(`${repoPath(rel)}: ${m.prose} words of prose`);
    for (const metric of METRICS) {
      console.log(`\n${LABELS[metric]}: ${m.counts[metric]} (ceiling ${ceilingOf(baseline, rel)[metric]})`);
      for (const d of m.detail[metric]) console.log(`  line ${d.line} (${d.size}): ${d.text.slice(0, 160)}`);
    }
    return 0;
  }

  if (args.includes("--accept")) {
    const rel = pageArgument(args, "--accept", measured);
    if (!rel) return 2;
    const was = ceilingOf(baseline, rel);
    const now = measured[rel].counts;
    const pages = { ...baseline, [rel]: { ...now } };
    for (const metric of METRICS) {
      if (now[metric] > was[metric]) console.log(`RAISED  ${repoPath(rel)}  ${LABELS[metric]}: ${was[metric]} -> ${now[metric]}`);
    }
    writeBaseline(pages);
    return 0;
  }

  if (args.includes("--update")) {
    const pages = {};
    for (const [rel, { counts }] of Object.entries(measured)) {
      const was = rel in baseline ? ceilingOf(baseline, rel) : null;
      pages[rel] = Object.fromEntries(
        METRICS.map((m) => [m, was === null ? counts[m] : Math.min(was[m], counts[m])])
      );
      if (was !== null) {
        for (const m of METRICS) {
          if (counts[m] > was[m]) {
            console.log(`KEPT  ${repoPath(rel)}  ${LABELS[m]} is ${counts[m]}, over its ceiling of ${was[m]}; --accept raises it`);
          }
        }
      }
    }
    const written = writeBaseline(pages);
    console.log(`Baseline lowered where it could: ${written} pages carry a ceiling above zero.`);
    return 0;
  }

  const rises = [];
  const falls = [];
  const totals = { ...ZERO };
  for (const [rel, { counts, detail }] of Object.entries(measured)) {
    const was = ceilingOf(baseline, rel);
    for (const metric of METRICS) {
      totals[metric] += counts[metric];
      if (counts[metric] > was[metric]) rises.push({ rel, metric, was: was[metric], now: counts[metric], detail: detail[metric] });
      if (counts[metric] < was[metric]) falls.push({ rel, metric, was: was[metric], now: counts[metric] });
    }
  }
  const stale = Object.keys(baseline).filter((rel) => !measured[rel]);

  console.log(`Readability across ${Object.keys(measured).length} book pages:`);
  for (const metric of METRICS) console.log(`  ${String(totals[metric]).padStart(5)}  ${LABELS[metric]}`);
  const report = ["### Book readability", "", "| Count | Now |", "|---|---:|"];
  for (const metric of METRICS) report.push(`| ${LABELS[metric]} | ${totals[metric]} |`);

  const strict = args.includes("--strict");
  if (rises.length) {
    console.log(`\n${rises.length} count(s) rose above the baseline:`);
    report.push("", `**${rises.length} count(s) rose above the baseline** (the job log lists each page's sentences over the limit):`, "");
    for (const r of rises) {
      console.log(`RISE  ${repoPath(r.rel)}  ${LABELS[r.metric]}: ${r.was} -> ${r.now}`);
      report.push(`- \`${repoPath(r.rel)}\`: ${LABELS[r.metric]} ${r.was} → ${r.now}`);
      for (const d of r.detail) console.log(`        line ${d.line} (${d.size}): ${d.text.slice(0, 140)}`);
      console.log(
        `::${strict ? "error" : "warning"} file=${repoPath(r.rel)}::${LABELS[r.metric]} rose from ${r.was} to ${r.now}; ` +
          `see Prose Limits in docs/STYLE-GUIDE.md, and --page ${r.rel} for the sentences`
      );
    }
  }
  if (falls.length) {
    console.log(`\n${falls.length} count(s) fell below the baseline; run --update so the gain holds:`);
    report.push("", `${falls.length} count(s) fell below the baseline: run \`--update\` so the gain holds.`);
    for (const f of falls) console.log(`FALL  ${repoPath(f.rel)}  ${LABELS[f.metric]}: ${f.was} -> ${f.now}`);
  }
  if (stale.length) {
    console.log(`\n${stale.length} baseline page(s) no longer exist; --update drops them:`);
    report.push("", `${stale.length} baseline page(s) no longer exist: \`--update\` drops them.`);
    for (const rel of stale) console.log(`STALE  ${repoPath(rel)}`);
  }
  if (!rises.length && !falls.length && !stale.length) console.log("\nEvery page matches its baseline.");
  summary(report);

  return rises.length && strict ? 1 : 0;
}

if (require.main === module) process.exit(main());
