#!/usr/bin/env node
/*
 * Check that the repository's prose uses British spelling, and optionally fix it.
 *
 * docs/STYLE-GUIDE.md asks for British English in all non-code text: Markdown (the book, the docs,
 * the skills, the READMEs), Javadoc and comments, and test display names. Code keeps its own
 * spelling: identifiers, keywords, annotations, string literals, wire values and quoted third-party
 * text. So this check reads only prose, and within prose it leaves alone anything that looks like
 * code.
 *
 * What counts as prose:
 *   Markdown   everything outside fenced code (an ~~~admonish block is prose), inline code spans,
 *              link targets, URLs, HTML tags and comments, and {{#include}} lines
 *   Java       comments and Javadoc, less {@code}, {@link}, <pre> and <code>; and the text of a
 *              @DisplayName or a jqwik @Label
 *   other      comments in Gradle Kotlin scripts, JavaScript, Python, shell and YAML
 *
 * What is left alone even in prose: a word joined to code (part of a dotted name, a path, a
 * three-part hyphenated name such as a configuration key, or next to an underscore, a digit, `#`,
 * `@`, `$`, a `*` wildcard or a bracket), a camelCase or ALL-CAPS word, a word in quotes, a Javadoc @param name,
 * the names and phrases in ALLOWED, and an epigraph: a blockquote with an attribution line. String
 * literals other than a @DisplayName are code to this check, so diagnostics and example output are
 * reviewed by hand. A heading is reported but never rewritten, since its text is its
 * anchor. A released version's notes, under hkj-book/src/release-history/, are not read: they are
 * never rewritten.
 *
 * The check knows a fixed list of American spellings (WORDS below) rather than guessing, so it has
 * no false alarms on words such as "size" or "prize". Add a word there when one slips through.
 *
 * Usage:
 *   node .github/scripts/british-spelling-check.cjs          report every American spelling; exit 1 if any
 *   node .github/scripts/british-spelling-check.cjs --fix    rewrite them in place (headings excepted)
 */
'use strict';

const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { linesAsProse } = require('./book-prose.cjs');

const ROOT = path.resolve(__dirname, '..', '..');
const FIX = process.argv.includes('--fix');

// ---------------------------------------------------------------------------------------------
// The American spellings this check knows, lower case, each with its British spelling.

const WORDS = new Map();

// -ize and -yze verbs, by stem: each stem takes every ending below.
const IZE_STEMS = [
  'serial', 'deserial', 'normal', 'initial', 'final', 'parameter', 'author', 'special',
  'optim', 'organ', 'memo', 'material', 'central', 'character', 'custom', 'recogn', 'token',
  'virtual', 'civil', 'parallel', 'random', 'summar', 'util', 'priorit', 'categor', 'minim',
  'maxim', 'real', 'general', 'standard', 'stabil', 'visual', 'memor', 'sanit', 'capital',
  'canonical', 'internation', 'local', 'emphas', 'critic', 'modern', 'harmon', 'personal',
  'legal', 'neutral', 'synthes', 'hypothes', 'apolog', 'formal', 'lexical', 'lexic', 'rational',
  'commercial', 'decentral', 'desensit', 'sensit', 'energ', 'familiar', 'fertil',
  'fossil', 'marginal', 'mobil', 'monet', 'national', 'natural', 'penal', 'polar', 'popular',
  'regular', 'reorgan', 'revital', 'scrutin', 'social', 'subsid', 'symbol', 'sympath',
  'theor', 'trivial', 'vapor', 'western', 'digit', 'factor', 'vector', 'linear', 'modular',
  'monomorph', 'polymorph', 'sequential', 'binar', 'container', 'contextual',
  'conceptual', 'individual', 'operational', 'patron', 'pressur', 'proselyt', 'singular',
  'specific', 'terror', 'uniform', 'unitar', 'industrial', 'ideal', 'item', 'jeopard',
  'public', 'amort', 'annual', 'global', 'raster', 'quant', 'econom',
];
const IZE_ENDINGS = [
  ['ize', 'ise'], ['izes', 'ises'], ['ized', 'ised'], ['izing', 'ising'], ['ization', 'isation'],
  ['izations', 'isations'], ['izer', 'iser'], ['izers', 'isers'], ['izable', 'isable'],
];
for (const stem of IZE_STEMS) {
  for (const [am, br] of IZE_ENDINGS) {
    WORDS.set(stem + am, stem + br);
  }
}
for (const stem of ['anal', 'paral', 'catal']) {
  for (const [am, br] of [['yze', 'yse'], ['yzes', 'yses'], ['yzed', 'ysed'], ['yzing', 'ysing'], ['yzer', 'yser'], ['yzers', 'ysers']]) {
    WORDS.set(stem + am, stem + br);
  }
}

// -or where British writes -our, with their common forms.
for (const [am, br] of [
  ['behavior', 'behaviour'], ['color', 'colour'], ['favor', 'favour'], ['honor', 'honour'],
  ['flavor', 'flavour'], ['labor', 'labour'], ['neighbor', 'neighbour'], ['humor', 'humour'],
  ['rumor', 'rumour'], ['vapor', 'vapour'], ['endeavor', 'endeavour'], ['armor', 'armour'],
  ['harbor', 'harbour'], ['savior', 'saviour'], ['odor', 'odour'], ['vigor', 'vigour'],
]) {
  for (const suffix of ['', 's', 'ed', 'ing', 'al', 'ally', 'ful', 'ite', 'ites', 'able', 'hood', 'less']) {
    WORDS.set(am + suffix, br + suffix);
  }
}

// Everything else, form by form.
for (const [am, br] of [
  ['center', 'centre'], ['centers', 'centres'], ['centered', 'centred'], ['centering', 'centring'],
  ['modeling', 'modelling'], ['modeled', 'modelled'], ['modeler', 'modeller'], ['modelers', 'modellers'],
  ['labeled', 'labelled'], ['labeling', 'labelling'], ['canceled', 'cancelled'], ['canceling', 'cancelling'],
  ['traveled', 'travelled'], ['traveling', 'travelling'], ['traveler', 'traveller'], ['travelers', 'travellers'],
  ['signaled', 'signalled'], ['signaling', 'signalling'], ['leveled', 'levelled'], ['leveling', 'levelling'],
  ['marshaled', 'marshalled'], ['marshaling', 'marshalling'], ['tunneled', 'tunnelled'], ['tunneling', 'tunnelling'],
  ['totaled', 'totalled'], ['totaling', 'totalling'], ['fueled', 'fuelled'], ['fueling', 'fuelling'],
  ['counseled', 'counselled'], ['counseling', 'counselling'], ['funneled', 'funnelled'], ['funneling', 'funnelling'],
  ['channeled', 'channelled'], ['channeling', 'channelling'], ['equaled', 'equalled'], ['equaling', 'equalling'],
  ['fulfill', 'fulfil'], ['fulfills', 'fulfils'], ['fulfillment', 'fulfilment'], ['skillful', 'skilful'],
  ['enrollment', 'enrolment'], ['installment', 'instalment'], ['gray', 'grey'], ['grays', 'greys'],
  ['defense', 'defence'], ['defenses', 'defences'], ['offense', 'offence'], ['offenses', 'offences'],
  ['analog', 'analogue'], ['analogs', 'analogues'], ['catalog', 'catalogue'], ['catalogs', 'catalogues'],
  ['cataloged', 'catalogued'], ['dialogs', 'dialogues'], ['maneuver', 'manoeuvre'], ['maneuvers', 'manoeuvres'],
  ['aluminum', 'aluminium'], ['cozy', 'cosy'], ['plow', 'plough'], ['mold', 'mould'],
]) {
  WORDS.set(am, br);
}

// Words that are spelt this way on purpose: a type or a header whose name is its spelling, and a
// Gradle feature whose name is "version catalog".
const ALLOWED = new Set(['Serializable']);
const ALLOWED_PHRASES = [
  /version catalogs?/gi, // Gradle's feature
  /HTML Sanitizer/g, // the OWASP product
  /Resource Acquisition Is Initialization/gi, // the C++ idiom's name
];

// ---------------------------------------------------------------------------------------------
// Which files are read.

const SKIP_FILES = new Set([
  'CODE_OF_CONDUCT.md', // the Contributor Covenant, quoted as written
  '.github/scripts/british-spelling-check.cjs', // this file's word list
]);
// A released version's notes are never rewritten, so they are not read; the chapter's other pages are.
const RELEASED_NOTES = /^hkj-book\/src\/release-history\/(v\d+(_\d+)*|earlier)\.md$/;
function skipped(file) {
  return (
    SKIP_FILES.has(file) ||
    RELEASED_NOTES.test(file) ||
    /(^|\/)LICENSE/.test(file) ||
    file.endsWith('.min.js') ||
    file.includes('node_modules/')
  );
}

function kind(file) {
  if (file.endsWith('.md')) return 'md';
  if (file.endsWith('.java')) return 'java';
  if (/\.(kts|js|cjs|mjs)$/.test(file)) return 'slash';
  if (/\.(py|sh|ya?ml|toml|properties)$/.test(file)) return 'hash';
  return null;
}

// ---------------------------------------------------------------------------------------------
// Prose regions: [start, end) spans of the file, and within them the text a word may be read in
// (code-like parts blanked to spaces, so offsets stay put).

function blank(text, from, to, into) {
  for (let i = from; i < to; i++) {
    if (text[i] !== '\n') into[i] = ' ';
  }
}

// A copy of the text with every character but its line ends blanked, to copy prose into.
function emptyLike(text) {
  const chars = new Array(text.length);
  for (let i = 0; i < text.length; i++) chars[i] = text[i] === '\n' ? '\n' : ' ';
  return chars;
}

// Blank each Javadoc inline tag such as {@code ...}, braces balanced.
function blankInlineTags(text, chars) {
  const re = /\{@(code|link|linkplain|value|literal)\b/g;
  let m;
  while ((m = re.exec(text)) !== null) {
    let depth = 0;
    let j = m.index;
    for (; j < text.length; j++) {
      if (text[j] === '{') depth++;
      else if (text[j] === '}' && --depth === 0) break;
    }
    blank(text, m.index, Math.min(j + 1, text.length), chars);
  }
}

// Blank the code-like parts inside a stretch of prose.
function maskProseCode(chars, start, end) {
  const text = chars.join('');
  const segment = text.slice(start, end);
  const patterns = [
    /`[^`\n]*`/g, // inline code
    /\]\([^)\n]*\)/g, // link targets
    /<https?:[^>\n]*>/g,
    /https?:\/\/[^\s)>\]]+/g, // bare URLs
    /<!--[\s\S]*?-->/g, // HTML comments
    /<pre\b[^>]*>[\s\S]*?<\/pre>/g, // preformatted blocks, such as the book's ASCII diagrams
    /<code>[\s\S]*?<\/code>/g,
    /<[A-Za-z/][^>\n]*>/g, // HTML tags and their attributes
    /@param\s+<?\w+>?/g, // a Javadoc parameter's name is the parameter's
    /\{#[^}\n]*\}/g, // an explicit heading id, which keeps the anchor a link already uses
  ];
  for (const re of patterns) {
    re.lastIndex = 0;
    let m;
    while ((m = re.exec(segment)) !== null) {
      blank(text, start + m.index, start + m.index + m[0].length, chars);
    }
  }
  blankInlineTags(text, chars);
  for (const re of ALLOWED_PHRASES) {
    re.lastIndex = 0;
    let m;
    while ((m = re.exec(segment)) !== null) {
      blank(text, start + m.index, start + m.index + m[0].length, chars);
    }
  }
}

function markdownProse(text) {
  const chars = emptyLike(text);
  const lines = text.split('\n');
  const starts = [];
  for (let i = 0, offset = 0; i < lines.length; i++) {
    starts.push(offset);
    offset += lines[i].length + 1;
  }
  // A blockquote with an attribution line (`> — Author`) quotes someone else's words: an epigraph
  // keeps its author's spelling, so none of its lines are read.
  const quoted = new Set();
  for (let start = 0; start < lines.length; ) {
    if (!lines[start].trim().startsWith('>')) {
      start++;
      continue;
    }
    let end = start;
    while (end < lines.length && lines[end].trim().startsWith('>')) end++;
    if (lines.slice(start, end).some((l) => /^>\s*(—|–|--)\s*\S/.test(l.trim()))) {
      for (let k = start; k < end; k++) quoted.add(k);
    }
    start = end;
  }
  const headings = [];
  // The fences are read as the book's other checks read them (book-prose.cjs), so the checks agree
  // on where code starts and ends, including code nested in an ~~~admonish block.
  for (const { line, text: content } of linesAsProse(text)) {
    const index = line - 1;
    if (quoted.has(index) || content.trim().startsWith('{{#')) continue;
    for (let k = 0; k < content.length; k++) chars[starts[index] + k] = content[k];
    if (/^#{1,6}\s/.test(content.trim())) headings.push([starts[index], starts[index] + content.length]);
  }
  maskProseCode(chars, 0, text.length);
  return { masked: chars.join(''), headings };
}

function javaProse(text) {
  const chars = emptyLike(text);
  let i = 0;
  const n = text.length;
  const keep = (from, to) => {
    for (let k = from; k < to; k++) chars[k] = text[k];
  };
  while (i < n) {
    if (text.startsWith('//', i)) {
      let j = text.indexOf('\n', i);
      if (j < 0) j = n;
      keep(i, j);
      i = j;
    } else if (text.startsWith('/*', i)) {
      let j = text.indexOf('*/', i + 2);
      j = j < 0 ? n : j + 2;
      keep(i, j);
      i = j;
    } else if (text.startsWith('"""', i)) {
      let j = text.indexOf('"""', i + 3);
      i = j < 0 ? n : j + 3;
    } else if (text[i] === '"') {
      let j = i + 1;
      while (j < n && text[j] !== '"' && text[j] !== '\n') j += text[j] === '\\' ? 2 : 1;
      // the text of a @DisplayName, or of a jqwik @Label, is prose
      if (/@(DisplayName|Label)\(\s*$/.test(text.slice(Math.max(0, i - 40), i))) keep(i + 1, j);
      i = j + 1;
    } else if (text[i] === "'") {
      let j = i + 1;
      while (j < n && text[j] !== "'" && text[j] !== '\n') j += text[j] === '\\' ? 2 : 1;
      i = j + 1;
    } else {
      i++;
    }
  }
  maskProseCode(chars, 0, n);
  return { masked: chars.join(''), headings: [] };
}

function commentProse(text, marker) {
  const chars = emptyLike(text);
  let offset = 0;
  for (const line of text.split('\n')) {
    const at = marker === '#' ? line.search(/(^|\s)#(?!!)/) : line.search(/(^|\s)\/\/|^\s*\/?\*/);
    if (at >= 0 && !(marker === '#' && /['"].*#.*['"]/.test(line))) {
      for (let k = at; k < line.length; k++) chars[offset + k] = line[k];
    }
    offset += line.length + 1;
  }
  maskProseCode(chars, 0, text.length);
  return { masked: chars.join(''), headings: [] };
}

// ---------------------------------------------------------------------------------------------
// Words in prose.

function british(word) {
  if (ALLOWED.has(word)) return null;
  if (/[a-z][A-Z]/.test(word) || (word.length > 1 && word === word.toUpperCase())) return null;
  const lower = word.toLowerCase();
  const replacement = WORDS.get(lower);
  if (replacement === undefined) return null;
  if (word[0] === word[0].toUpperCase()) return replacement[0].toUpperCase() + replacement.slice(1);
  return replacement;
}

// The maximal run of name characters around a word, to tell a word in a sentence from a word in a
// dotted name, a path or a hyphenated key.
function tokenAround(text, start, end) {
  let from = start;
  let to = end;
  while (from > 0 && /[A-Za-z0-9_.$\/-]/.test(text[from - 1])) from--;
  while (to < text.length && /[A-Za-z0-9_.$\/-]/.test(text[to])) to++;
  return { token: text.slice(from, to).replace(/^[.\/-]+|[.\/-]+$/g, ''), after: text[to] || ' ' };
}

function joinedToCode(text, start, end) {
  const before = text[start - 1] || ' ';
  const after = text[end] || ' ';
  if (/[A-Za-z0-9_$#@*\\]/.test(before) || /[A-Za-z0-9_$(\\]/.test(after)) return true;
  // a word in quotes names the word itself, such as a token a heuristic matches
  if (/['"‘“`]/.test(before) && /['"’”`]/.test(after)) return true;
  const { token, after: next } = tokenAround(text, start, end);
  if (token.includes('.')) return true; // a dotted name, a file or a package
  if (token.split('-').filter(Boolean).length >= 3) return true; // a key such as custom-serializers-enabled
  if (next === '=') return true; // a key being set
  return false;
}

function findings(text, masked, headings) {
  const found = [];
  const re = /[A-Za-z]+/g;
  let m;
  while ((m = re.exec(masked)) !== null) {
    const start = m.index;
    const end = start + m[0].length;
    if (joinedToCode(text, start, end)) continue;
    const replacement = british(m[0]);
    if (replacement === null) continue;
    const inHeading = headings.some(([from, to]) => start >= from && end <= to);
    found.push({ start, end, word: m[0], replacement, inHeading });
  }
  return found;
}

function lineOf(text, index) {
  let line = 1;
  for (let i = 0; i < index; i++) if (text[i] === '\n') line++;
  return line;
}

// ---------------------------------------------------------------------------------------------

const files = execFileSync('git', ['ls-files'], { cwd: ROOT, encoding: 'utf8' }).split('\n').filter(Boolean);
let reported = 0;
let fixed = 0;
let filesFixed = 0;
for (const file of files) {
  if (skipped(file)) continue;
  const k = kind(file);
  if (k === null) continue;
  const full = path.join(ROOT, file);
  if (!fs.existsSync(full)) continue;
  const text = fs.readFileSync(full, 'utf8');
  const { masked, headings } =
    k === 'md' ? markdownProse(text) : k === 'java' ? javaProse(text) : commentProse(text, k === 'hash' ? '#' : '//');
  const found = findings(text, masked, headings);
  if (found.length === 0) continue;
  if (FIX) {
    let out = text;
    let changed = 0;
    for (const f of [...found].reverse()) {
      if (f.inHeading) {
        console.log(`${file}:${lineOf(text, f.start)}: heading keeps "${f.word}" (its text is its anchor); fix it by hand with an explicit {#id}`);
        reported++;
        continue;
      }
      out = out.slice(0, f.start) + f.replacement + out.slice(f.end);
      changed++;
    }
    if (changed > 0) {
      fs.writeFileSync(full, out);
      fixed += changed;
      filesFixed++;
    }
  } else {
    for (const f of found) {
      console.log(`${file}:${lineOf(text, f.start)}: "${f.word}" -> "${f.replacement}"${f.inHeading ? ' (heading)' : ''}`);
      reported++;
    }
  }
}

if (FIX) {
  console.log(`Fixed ${fixed} spelling(s) in ${filesFixed} file(s).${reported ? ` ${reported} in headings left for a hand fix.` : ''}`);
  process.exit(reported ? 1 : 0);
}
if (reported) {
  console.log(`\n${reported} American spelling(s) in prose. British English is the rule for all non-code text (docs/STYLE-GUIDE.md); fix them, or run with --fix.`);
  process.exit(1);
}
console.log('Every prose file uses British spelling.');
