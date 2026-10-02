"""Generate the documentation's AI-facing files from the book source.

``llms.txt`` (curated by hand, copied by mdBook) is the index an assistant reads
first. This script writes what that index points to, all into the built book:

* ``llms-full.txt``: the entire book in one Markdown file, for a tool that can
  ingest it whole.
* ``llms-<chapter>.txt``: one chapter per file, for the far more common tool
  that cannot. The whole book runs to megabytes, and a fetch tool truncates
  long before the later chapters. A few long lookup pages are linked from
  their chapter's file rather than inlined (``LINKED_NOT_INLINED``).
* ``<page>.md`` beside each ``<page>.html``: the page's Markdown, so an agent
  following an ``llms.txt`` link reads the page without the site's navigation.
  Relative links in the Markdown resolve to these copies too.

Pages follow ``SUMMARY.md`` reading order. mdBook ``{{#include}}`` directives
are resolved, since the book embeds its code examples that way and the
literal directive would drop the most useful part of many pages. Pages that
``SUMMARY.md`` links but that do not exist in ``src`` at generation time (root
files that CI copies in later) are skipped with a warning.

Run from the repository root. Configured via environment variables:

* ``MDBOOK_SRC_DIR``: book source directory (default ``hkj-book/src``)
* ``MDBOOK_OUTPUT_DIR``: built book directory, where the files are written
  (default ``hkj-book/book``)
* ``HKJ_CANONICAL_BASE``: the URL links in the chapter files point under
  (default ``https://higher-kinded-j.github.io/latest/``)
"""

import os

import book_sources

# Chapters that get no file of their own: licence and contributor pages, of
# no use to a reader of the library.
CORPUS_SKIP = {"project-info"}

# Lookup pages a reader opens holding one message or one limit. Each is long,
# so a chapter's file links them instead of inlining them; llms-full.txt and
# the per-page Markdown still carry them whole.
LINKED_NOT_INLINED = {
    "mapping/rules.md",
    "mapping/compiler_errors.md",
    "optics/compiler_errors.md",
}

FULL_PREAMBLE = """# Higher-Kinded-J: Full Documentation

> Composable effects, optics and compile-time DTO mapping for Java 25

This file is the complete Higher-Kinded-J documentation concatenated into a
single document for AI ingestion. It is generated from the book source in
reading order, with code examples inlined. For a concise index, see llms.txt;
each chapter is also published on its own, as llms-<chapter>.txt.

"""

CHAPTER_PREAMBLE = """# Higher-Kinded-J: {title}

> The "{title}" chapter of the Higher-Kinded-J documentation, in reading
> order, with its code examples inlined. llms.txt indexes the whole book, and
> llms-full.txt holds all of it.
"""


def page_block(page_path: str, body: str) -> str:
    return f"<!-- Source: {page_path} -->\n\n{body}\n"


def chapter_file(chapter, texts: dict, base_url: str) -> str:
    """One chapter's corpus: its pages inlined, its lookup pages linked."""
    parts = [CHAPTER_PREAMBLE.format(title=chapter.title)]
    linked = [p for p in chapter.pages if p.path in LINKED_NOT_INLINED and p.path in texts]
    if linked:
        links = "\n".join(f"- [{p.title}]({base_url}{p.path})" for p in linked)
        parts.append(
            f"Reference pages linked rather than inlined here; fetch one when needed:\n\n{links}\n"
        )
    for page in chapter.pages:
        if page.path in texts and page.path not in LINKED_NOT_INLINED:
            parts.append(page_block(page.path, texts[page.path]))
    return "\n".join(parts)


def write(path: str, text: str) -> int:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)
    return os.path.getsize(path)


def main() -> None:
    src_dir = os.environ.get("MDBOOK_SRC_DIR", "hkj-book/src")
    out_dir = os.environ.get("MDBOOK_OUTPUT_DIR", "hkj-book/book")
    base_url = os.environ.get("HKJ_CANONICAL_BASE", "https://higher-kinded-j.github.io/latest/")
    if not base_url.endswith("/"):
        base_url += "/"

    chapters = book_sources.read_summary(src_dir)
    counters = {"resolved": 0, "unresolved": 0}
    texts = {}
    missing = 0
    for page in book_sources.all_pages(chapters):
        path = os.path.join(src_dir, page.path)
        if not os.path.isfile(path):
            book_sources.warn(f"  skipping (not found): {page.path}")
            missing += 1
            continue
        with open(path, encoding="utf-8") as fh:
            text, _ = book_sources.resolve_includes(fh.read(), path, counters)
        texts[page.path] = text.strip()

    # Per-page Markdown, beside the HTML mdBook wrote.
    for page_path, text in texts.items():
        write(os.path.join(out_dir, page_path), text + "\n")

    # The whole book.
    full = [FULL_PREAMBLE] + [page_block(path, text) for path, text in texts.items()]
    size = write(os.path.join(out_dir, "llms-full.txt"), "\n".join(full))
    print(
        f"llms-full.txt: {len(texts)} pages, {missing} skipped, "
        f"{counters['resolved']} includes inlined ({counters['unresolved']} unresolved), "
        f"{size / 1024:.0f} KB."
    )
    print(f"Markdown copies: {len(texts)} pages.")

    # One file per chapter that has more than its introduction.
    written = {"full"}
    for chapter in chapters:
        if len(chapter.pages) < 2 or chapter.slug in CORPUS_SKIP:
            continue
        if chapter.slug in written:
            raise SystemExit(f"Error: two chapters would both write llms-{chapter.slug}.txt")
        written.add(chapter.slug)
        name = f"llms-{chapter.slug}.txt"
        size = write(os.path.join(out_dir, name), chapter_file(chapter, texts, base_url))
        print(f"{name}: {len(chapter.pages)} pages, {size / 1024:.0f} KB.")


if __name__ == "__main__":
    main()
