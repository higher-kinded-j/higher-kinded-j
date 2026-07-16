"""Generate ``llms-full.txt`` - the full-text companion to ``llms.txt``.

``llms.txt`` (curated by hand) is a concise index of the documentation for AI
crawlers. Its optional companion ``llms-full.txt`` is the *entire* documentation
concatenated into a single Markdown file, so an assistant can ingest the whole
library in one fetch rather than crawling 280+ pages.

This script reads ``SUMMARY.md`` to preserve the authored reading order, then
concatenates each referenced Markdown source. Because the book embeds its code
examples with mdBook ``{{#include path:anchor}}`` directives, those directives
are resolved here too - otherwise the corpus would contain the literal
``{{#include ...}}`` text instead of the code, dropping the most useful part of
many pages. Only the anchor / line-range / whole-file forms mdBook supports are
handled; an unresolved directive is replaced with an HTML comment and logged.

Pages that ``SUMMARY.md`` links but that do not exist in ``src`` at generation
time (e.g. root files that CI copies in later) are skipped with a warning.

Run from the repository root. Configured via environment variables:

* ``MDBOOK_SRC_DIR``    - book source directory (default ``hkj-book/src``)
* ``MDBOOK_OUTPUT_DIR`` - built book directory, where the file is written
                          (default ``hkj-book/book``)
"""

import os
import re
import sys

# Matches Markdown links to local .md files in SUMMARY.md, e.g. [Title](foo/bar.md).
LINK_RE = re.compile(r"\[[^\]]*\]\(([^)]+\.md)\)")

# Matches mdBook include-family directives, capturing the argument (path[:spec]).
INCLUDE_RE = re.compile(r"\{\{#(?:include|rustdoc_include|playground)\s+([^}]+?)\s*\}\}")

PREAMBLE = """# Higher-Kinded-J - Full Documentation

> Unifying Composable Effects and Advanced Optics for Java 25+

This file is the complete Higher-Kinded-J documentation concatenated into a
single document for AI ingestion. It is generated from the book source in
reading order, with code examples inlined. For a concise index, see llms.txt.

"""


def warn(message: str) -> None:
    """Emit a diagnostic to stderr so it stays separate from the output."""
    print(message, file=sys.stderr)


def extract_anchor(lines: list, name: str):
    """Return the lines of the ``ANCHOR: name`` .. ``ANCHOR_END: name`` region.

    Marker lines (and any nested anchor markers) are stripped, mirroring
    mdBook's behaviour. Returns ``None`` if the anchor is not found.
    """
    start = re.compile(r"ANCHOR:\s*" + re.escape(name) + r"\b")
    end = re.compile(r"ANCHOR_END:\s*" + re.escape(name) + r"\b")
    out = []
    inside = False
    found = False
    for line in lines:
        if not inside:
            if start.search(line):
                inside = True
                found = True
            continue
        if end.search(line):
            break
        # Drop any other anchor markers that fall within the region.
        if "ANCHOR:" in line or "ANCHOR_END:" in line:
            continue
        out.append(line)
    return out if found else None


def resolve_include_arg(arg: str, base_dir: str):
    """Resolve one include argument to a list of lines, or ``(None, error)``."""
    parts = arg.split(":")
    rel_path = parts[0].strip()
    spec = [p.strip() for p in parts[1:]]

    file_path = os.path.normpath(os.path.join(base_dir, rel_path))
    if not os.path.isfile(file_path):
        return None, f"file not found: {rel_path}"
    with open(file_path, encoding="utf-8") as fh:
        lines = fh.read().splitlines()

    if not spec:  # {{#include file}}
        return lines, None
    if len(spec) == 1:
        token = spec[0]
        if token.isdigit():  # {{#include file:LINE}}
            idx = int(token) - 1
            return (lines[idx : idx + 1] if 0 <= idx < len(lines) else []), None
        anchored = extract_anchor(lines, token)  # {{#include file:anchor}}
        if anchored is None:
            return None, f"anchor '{token}' not found in {rel_path}"
        return anchored, None
    if len(spec) == 2:  # {{#include file:START:END}} (either may be empty)
        start = int(spec[0]) - 1 if spec[0] else 0
        stop = int(spec[1]) if spec[1] else len(lines)
        return lines[start:stop], None
    return None, f"unsupported include spec: {arg}"


def resolve_includes(md_text: str, md_path: str, counters: dict) -> str:
    """Replace every include directive in ``md_text`` with the referenced code."""
    base_dir = os.path.dirname(md_path)

    def replace(match: "re.Match") -> str:
        arg = match.group(1)
        content, error = resolve_include_arg(arg, base_dir)
        if error is not None:
            warn(f"  include unresolved ({md_path}): {arg} - {error}")
            counters["unresolved"] += 1
            return f"<!-- include unresolved: {arg} -->"
        counters["resolved"] += 1
        return "\n".join(content)

    return INCLUDE_RE.sub(replace, md_text)


def ordered_sources(summary_path: str):
    """Yield unique .md targets from SUMMARY.md in document order."""
    seen = set()
    with open(summary_path, encoding="utf-8") as fh:
        for line in fh:
            for match in LINK_RE.finditer(line):
                target = match.group(1).strip()
                # Ignore absolute URLs and anchors; keep repo-relative paths.
                if target.startswith(("http://", "https://", "#")):
                    continue
                if target not in seen:
                    seen.add(target)
                    yield target


def main() -> None:
    src_dir = os.environ.get("MDBOOK_SRC_DIR", "hkj-book/src")
    out_dir = os.environ.get("MDBOOK_OUTPUT_DIR", "hkj-book/book")
    summary_path = os.path.join(src_dir, "SUMMARY.md")

    if not os.path.isfile(summary_path):
        raise SystemExit(f"Error: SUMMARY.md not found at '{summary_path}'")

    parts = [PREAMBLE]
    included = 0
    missing = 0
    counters = {"resolved": 0, "unresolved": 0}
    for target in ordered_sources(summary_path):
        path = os.path.join(src_dir, target)
        if not os.path.isfile(path):
            warn(f"  skipping (not found): {target}")
            missing += 1
            continue
        with open(path, encoding="utf-8") as fh:
            body = resolve_includes(fh.read(), path, counters).strip()
        parts.append(f"<!-- Source: {target} -->\n\n{body}\n")
        included += 1

    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "llms-full.txt")
    with open(out_path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(parts))

    size_kb = os.path.getsize(out_path) / 1024
    print(
        f"llms-full.txt generated at {out_path}: "
        f"{included} pages, {missing} skipped, "
        f"{counters['resolved']} includes inlined "
        f"({counters['unresolved']} unresolved), {size_kb:.0f} KB."
    )


if __name__ == "__main__":
    main()
