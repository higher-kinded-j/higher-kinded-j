"""Post-build SEO fix-ups for the built mdBook HTML.

mdBook's ``{{ path }}`` template variable resolves to the Markdown *source*
path (``foo.md``), so per-page ``og:url`` and ``rel="canonical"`` tags cannot
be produced correctly in ``theme/head.hbs``. This script walks the built HTML
and rewrites them with the real page URL instead.

Two deliberate choices:

* **Canonical always points at ``/latest/``.** The same book is built once and
  copied to ``/latest/`` and to each ``/vX.Y.Z/`` path. Pointing every copy's
  canonical at ``/latest/`` consolidates search-engine ranking onto the current
  docs and stops old versions competing with themselves for duplicate content.
  The ``/latest/`` deploy is therefore self-canonical.
* **``og:url`` mirrors the canonical URL**, so social/AI unfurls resolve to the
  same page search engines treat as authoritative.

It also injects a per-page ``TechArticle`` JSON-LD block (the site-wide
``SoftwareApplication`` identity lives in ``head.hbs``). The script is
idempotent: re-running it neither duplicates canonical tags nor re-injects the
article block.

Run from the repository root. Configured via environment variables:

* ``MDBOOK_OUTPUT_DIR`` - built book directory (default ``hkj-book/book``)
* ``HKJ_CANONICAL_BASE`` - canonical base URL
  (default ``https://higher-kinded-j.github.io/latest/``)
"""

import html
import json
import os
import re

# Built pages that must never be advertised as canonical/indexable content.
EXCLUDED_FILES = {"404.html", "print.html", "toc.html"}

OG_URL_RE = re.compile(
    r'(<meta\s+property="og:url"\s+content=")[^"]*(">)', re.IGNORECASE
)
CANONICAL_RE = re.compile(r'<link\s+rel="canonical"', re.IGNORECASE)
TITLE_RE = re.compile(r"<title>(.*?)</title>", re.IGNORECASE | re.DOTALL)
TECHARTICLE_MARKER = "hkj-techarticle"
HEAD_CLOSE_RE = re.compile(r"</head>", re.IGNORECASE)


def page_url(base_url: str, book_dir: str, file_path: str) -> str:
    """Map a built HTML file to its canonical URL under ``base_url``."""
    rel = os.path.relpath(file_path, book_dir).replace(os.path.sep, "/")
    return base_url + rel


def techarticle_ldjson(url: str, title: str) -> str:
    """Build a per-page TechArticle JSON-LD ``<script>`` for the given page."""
    data = {
        "@context": "https://schema.org",
        "@type": "TechArticle",
        "headline": title,
        "url": url,
        "mainEntityOfPage": {"@type": "WebPage", "@id": url},
        "inLanguage": "en-GB",
        "isPartOf": {"@id": "https://higher-kinded-j.github.io/#website"},
        "about": {"@id": "https://higher-kinded-j.github.io/#software"},
        "author": {"@id": "https://higher-kinded-j.github.io/#author"},
        "publisher": {"@id": "https://higher-kinded-j.github.io/#author"},
    }
    # json.dumps handles all string escaping, keeping the block valid JSON.
    return (
        f'<script type="application/ld+json" data-hkj="{TECHARTICLE_MARKER}">'
        + json.dumps(data, ensure_ascii=False)
        + "</script>"
    )


def process_file(file_path: str, url: str) -> bool:
    """Apply the SEO fix-ups to one file. Returns True if it was changed."""
    with open(file_path, encoding="utf-8") as fh:
        content = fh.read()
    original = content

    # 1. Rewrite og:url to the real page URL.
    content = OG_URL_RE.sub(rf"\g<1>{url}\g<2>", content, count=1)

    # 2 + 3. Inject canonical + TechArticle just before </head>, once.
    additions = []
    if not CANONICAL_RE.search(content):
        additions.append(f'<link rel="canonical" href="{url}">')
    if TECHARTICLE_MARKER not in content:
        match = TITLE_RE.search(content)
        title = html.unescape(match.group(1).strip()) if match else "Higher-Kinded-J"
        additions.append(techarticle_ldjson(url, title))

    if additions:
        injection = "\n" + "\n".join(additions) + "\n"
        content = HEAD_CLOSE_RE.sub(injection + "</head>", content, count=1)

    if content != original:
        with open(file_path, "w", encoding="utf-8") as fh:
            fh.write(content)
        return True
    return False


def main() -> None:
    book_dir = os.environ.get("MDBOOK_OUTPUT_DIR", "hkj-book/book")
    base_url = os.environ.get(
        "HKJ_CANONICAL_BASE", "https://higher-kinded-j.github.io/latest/"
    )
    if not base_url.endswith("/"):
        base_url += "/"

    if not os.path.isdir(book_dir):
        raise SystemExit(f"Error: book directory '{book_dir}' does not exist")

    changed = 0
    total = 0
    for root, _, files in os.walk(book_dir):
        for name in files:
            if not name.endswith(".html") or name in EXCLUDED_FILES:
                continue
            total += 1
            path = os.path.join(root, name)
            if process_file(path, page_url(base_url, book_dir, path)):
                changed += 1

    print(f"SEO post-processing complete: {changed}/{total} HTML files updated.")
    print(f"Canonical base: {base_url}")


if __name__ == "__main__":
    main()
