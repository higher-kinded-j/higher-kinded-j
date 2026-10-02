"""Generate ``sitemap.xml`` for the built mdBook.

Every built page with a source in ``SUMMARY.md`` is listed, at the URL its
canonical tag names (a directory's ``index.html`` as the directory). A built
file without a source, such as a redirect stub left for a moved page, is not:
a sitemap should name only URLs that answer with content.

Each URL's ``lastmod`` is the date of the last commit to the page's source or
to a file it includes, so a search engine can trust it to recrawl what
changed. A page git does not track (a file CI copies in) has no ``lastmod``.

Run from the repository root. Configured via environment variables:

* ``MDBOOK_OUTPUT_DIR``: built book directory (default ``hkj-book/book``)
* ``MDBOOK_SRC_DIR``: book source directory (default ``hkj-book/src``)
* ``MDBOOK_SITE_URL``: the URL the book is served under
  (default ``https://higher-kinded-j.github.io/``)
"""

import html
import os

import book_sources

# Built pages that are not content of their own.
EXCLUDED_FILES = {"404.html", "print.html", "toc.html"}

# Search engines other than Google may read priority as a hint. The key
# selling points first, then the chapters they lead into.
PRIORITY_PAGES = {
    "1.0": ["index.html", "home.html"],
    "0.9": [
        "effect/ch_intro.html",
        "effect/effect_path_overview.html",
        "optics/optics_intro.html",
        "optics/focus_dsl.html",
        "effect/focus_integration.html",
        "mapping/ch_intro.html",
        "mapping/quickstart.html",
    ],
    "0.8": [
        "core-concepts.html",
        "usage-guide.html",
        "hkt_introduction.html",
        "tutorials_intro.html",
        "spring_boot_integration.html",
        "mapping/at_a_glance.html",
        "mapping/from_mapstruct.html",
    ],
}
PRIORITY_DIRECTORIES = {"0.7": ["effect/", "optics/", "mapping/"], "0.6": ["tutorials/", "hkts/"]}


def priority(rel: str) -> str:
    for value, pages in PRIORITY_PAGES.items():
        if any(rel == page or rel.endswith("/" + page) for page in pages):
            return value
    for value, directories in PRIORITY_DIRECTORIES.items():
        if any(rel.startswith(directory) for directory in directories):
            return value
    return "0.5"


def location(rel: str) -> str:
    """A page's path under the base URL, as its canonical tag gives it."""
    if rel == "index.html":
        return ""
    if rel.endswith("/index.html"):
        return rel[: -len("index.html")]
    return rel


def main() -> None:
    book_dir = os.environ.get("MDBOOK_OUTPUT_DIR", "hkj-book/book")
    src_dir = os.environ.get("MDBOOK_SRC_DIR", "hkj-book/src")
    base_url = os.environ.get("MDBOOK_SITE_URL", "https://higher-kinded-j.github.io/")
    if not base_url.endswith("/"):
        base_url += "/"
    if not os.path.isdir(book_dir):
        raise SystemExit(f"Error: book directory '{book_dir}' does not exist")

    pages = [
        page
        for page in book_sources.all_pages(book_sources.read_summary(src_dir))
        if os.path.isfile(os.path.join(src_dir, page.path))
    ]
    sources = {page.path for page in pages}
    first_page = pages[0].path if pages else None
    dates = book_sources.page_dates(src_dir, pages)

    entries = []
    for root, _, files in os.walk(book_dir):
        for name in files:
            if not name.endswith(".html") or name in EXCLUDED_FILES:
                continue
            rel = os.path.relpath(os.path.join(root, name), book_dir).replace(os.sep, "/")
            source = book_sources.html_source(rel, first_page)
            if source in sources:
                entries.append((rel, dates.get(source)))

    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">',
    ]
    for rel, modified in sorted(entries):
        lines.append("  <url>")
        lines.append(f"    <loc>{html.escape(base_url + location(rel))}</loc>")
        if modified:
            lines.append(f"    <lastmod>{modified}</lastmod>")
        lines.append("    <changefreq>weekly</changefreq>")
        lines.append(f"    <priority>{priority(rel)}</priority>")
        lines.append("  </url>")
    lines.append("</urlset>")

    output = os.path.join(book_dir, "sitemap.xml")
    with open(output, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    dated = sum(1 for _, modified in entries if modified)
    print(f"Sitemap generated at {output}: {len(entries)} URLs, {dated} with lastmod.")


if __name__ == "__main__":
    main()
