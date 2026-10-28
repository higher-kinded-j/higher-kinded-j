#!/usr/bin/env node
/*
 * Fail an announcement the book would not show, or would show wrongly.
 *
 * Every /latest/ deploy copies .github/scripts/announcement.json to the site
 * root, and hkj-book/theme/announcement.js shows it above each page of every
 * copy of the book that carries that script. The script shows nothing for a
 * file it cannot read, and shows HTML or Markdown as written, so a typo in a
 * field's name or a date would leave the announcement unseen with nothing to
 * say why. This check says why instead. Book Checks runs it on a pull request,
 * and the deploy runs it before copying the file.
 *
 * The file is either {} (no announcement) or an object with these fields:
 *
 *   id         required; a new id shows the bar again to readers who
 *              dismissed the last one
 *   text       required; one sentence of plain text, at most 160 characters
 *   showUntil  required; the last day it shows, as YYYY-MM-DD
 *   link       optional; an https URL or a path on the site, such as
 *              /latest/release-history.html
 *   linkText   with link, and only with it; at most 40 characters
 *
 * Each copy of the book keeps the script it was built with, so the file must
 * stay readable by the oldest one: add optional fields only, never rename,
 * remove, loosen or change the meaning of one. This check holds the file at
 * least as strictly as announcement.js does; keep the two in step.
 *
 * A showUntil that has passed is a warning rather than a failure: the bar is
 * already hidden, and a check that failed on a date alone would fail unrelated
 * pull requests.
 */
"use strict";

const fs = require("fs");
const path = require("path");

const file = path.join(__dirname, "announcement.json");
const shown = path.relative(path.resolve(__dirname, "..", ".."), file);
const FIELDS = ["id", "text", "showUntil", "link", "linkText"];
const SITE = "https://higher-kinded-j.github.io";
// Tags that read as HTML. A one-letter tag counts only in lower case, so that a Java type such as
// List<A> or Kind<F, A> is not taken for HTML; a longer tag name counts in either case.
const ONE_LETTER_TAG = /<\/?[abipsu](\s[^>]*)?\/?>/;
const NAMED_TAG =
  /<\/?(abbr|br|code|div|em|h[1-6]|iframe|img|kbd|li|ol|pre|script|small|span|strong|style|sub|sup|table|td|tr|ul)(\s[^>]*)?\/?>/i;
// Markdown a maintainer may expect the bar to format: bold, a code span or a link
const MARKDOWN = /\*\*[^*\s][^*]*\*\*|`[^`]+`|\[[^\]]*\]\([^)]*\)/;

function problems(announcement) {
  if (announcement === null || typeof announcement !== "object" || Array.isArray(announcement)) {
    return ["must be a JSON object, or {} for no announcement"];
  }
  if (Object.keys(announcement).length === 0) return [];
  const found = [];
  for (const key of Object.keys(announcement)) {
    if (!FIELDS.includes(key)) found.push(`unknown field "${key}"; the fields are ${FIELDS.join(", ")}`);
  }
  for (const key of FIELDS) {
    const value = announcement[key];
    if (value !== undefined && (typeof value !== "string" || value.trim() === "")) {
      found.push(`"${key}" must be a string with text in it`);
    }
  }
  for (const key of ["id", "text", "showUntil"]) {
    if (announcement[key] === undefined) found.push(`"${key}" is required`);
  }
  const { text, showUntil, link, linkText } = announcement;
  if (typeof text === "string") {
    if (ONE_LETTER_TAG.test(text) || NAMED_TAG.test(text)) found.push(`"text" shows as text, so its HTML would show as tags`);
    if (MARKDOWN.test(text)) {
      found.push(`"text" shows as text, so Markdown such as **bold**, a \`code\` span or a [link](url) would show as written; put a link in "link" and "linkText"`);
    }
    if (/\n/.test(text)) found.push(`"text" must be one line`);
    if (text.length > 160) found.push(`"text" is ${text.length} characters; keep it to one sentence of at most 160`);
  }
  if (typeof linkText === "string" && linkText.length > 40) {
    found.push(`"linkText" is ${linkText.length} characters; keep it to at most 40`);
  }
  if (typeof showUntil === "string" && !isDate(showUntil)) {
    found.push(`"showUntil" must be a real date as YYYY-MM-DD, not "${showUntil}"`);
  }
  if ((link === undefined) !== (linkText === undefined)) {
    found.push(`"link" and "linkText" go together`);
  }
  if (typeof link === "string" && !isSafeLink(link)) {
    found.push(`"link" must be an https URL or a path on the site starting with /, not ${JSON.stringify(link)}`);
  }
  return found;
}

function isDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(value);
}

// As announcement.js's safeHref: browsers read a backslash as a slash and drop tabs and line
// breaks, so "/\evil.example" would leave the site
function isSafeLink(link) {
  if (/[\\\s\u0000-\u001f]/.test(link)) return false;
  try {
    const url = new URL(link, SITE);
    if (link.startsWith("/")) return url.origin === SITE;
    return link.startsWith("https://") && url.protocol === "https:" && url.hostname !== "";
  } catch (error) {
    return false;
  }
}

function fail(messages) {
  for (const message of messages) {
    console.log(`FAIL  ${shown}  ${message}`);
    console.log(`::error file=${shown}::${message}`);
  }
  console.log(`\nThe bar would show this announcement wrongly, or not at all. "Site Announcements" in docs/STYLE-GUIDE.md gives the format.`);
  process.exit(1);
}

if (!fs.existsSync(file)) {
  fail(["is missing; keep the file, and write {} in it for no announcement"]);
}
let announcement;
try {
  announcement = JSON.parse(fs.readFileSync(file, "utf8"));
} catch (error) {
  fail([`${error.message}. JSON takes no comments or trailing commas; write {} for no announcement`]);
}

const found = problems(announcement);
if (found.length > 0) fail(found);
if (Object.keys(announcement).length === 0) {
  console.log("No announcement is set.");
} else if (announcement.showUntil < new Date().toISOString().slice(0, 10)) {
  const message = `The announcement "${announcement.id}" passed its last day, ${announcement.showUntil}, by UTC, so readers no longer see it; replace the file's contents with {}`;
  console.log(message);
  console.log(`::warning file=${shown}::${message}`);
} else {
  console.log(`The announcement "${announcement.id}" shows until the end of ${announcement.showUntil}, by each reader's calendar.`);
}
