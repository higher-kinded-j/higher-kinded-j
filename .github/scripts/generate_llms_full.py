"""Generate ``llms-full.txt`` - the full-text companion to ``llms.txt``.

``llms.txt`` (curated by hand) is a concise index of the documentation for AI
crawlers. Its optional companion ``llms-full.txt`` is the *entire* documentation
concatenated into a single Markdown file, so an assistant can ingest the whole
library in one fetch rather than crawling 280+ pages.

This script reads ``SUMMARY.md`` to preserve the authored reading order, then
concatenates each referenced Markdown source. Pages that ``SUMMARY.md`` links
but that do not exist in ``src`` at generation time (e.g. root files that CI
copies in later) are skipped with a warning.

Run from the repository root. Configured via environment variables:

* ``MDBOOK_SRC_DIR``    - book source directory (default ``hkj-book/src``)
* ``MDBOOK_OUTPUT_DIR`` - built book directory, where the file is written
                          (default ``hkj-book/book``)
"""

import os
import re

# Matches Markdown links to local .md files in SUMMARY.md, e.g. [Title](foo/bar.md).
LINK_RE = re.compile(r"\[[^\]]*\]\(([^)]+\.md)\)")

PREAMBLE = """# Higher-Kinded-J - Full Documentation

> Unifying Composable Effects and Advanced Optics for Java 25+

This file is the complete Higher-Kinded-J documentation concatenated into a
single document for AI ingestion. It is generated from the book source in
reading order. For a concise index, see llms.txt.

"""


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
    for target in ordered_sources(summary_path):
        path = os.path.join(src_dir, target)
        if not os.path.isfile(path):
            print(f"  skipping (not found): {target}")
            missing += 1
            continue
        with open(path, encoding="utf-8") as fh:
            body = fh.read().strip()
        parts.append(f"<!-- Source: {target} -->\n\n{body}\n")
        included += 1

    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "llms-full.txt")
    with open(out_path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(parts))

    size_kb = os.path.getsize(out_path) / 1024
    print(
        f"llms-full.txt generated at {out_path}: "
        f"{included} pages, {missing} skipped, {size_kb:.0f} KB."
    )


if __name__ == "__main__":
    main()
