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

Every page also gets what ``head.hbs`` can only give site-wide, taken from its
Markdown source through ``book_sources``:

* **its own description** (subtitle, "What You'll Learn" bullets, or first
  paragraph) in the ``description``, ``og:description`` and
  ``twitter:description`` tags, where the site-wide text would otherwise
  repeat on every page;
* **its chapter's keywords**;
* a **``TechArticle`` JSON-LD block** carrying the headline, description,
  keywords, chapter and the date the page last changed (the site-wide
  ``SoftwareApplication`` identity lives in ``head.hbs``);
* a ``<link rel="alternate" type="text/markdown">`` to the page's Markdown
  copy, which ``generate_llms_files.py`` writes.

A built HTML file with no source page, such as a redirect stub, keeps only the
og:url and canonical handling. When ``HKJ_SOFTWARE_VERSION`` is set, it becomes
the ``softwareVersion`` of the site-wide ``SoftwareApplication``.

The script is idempotent: re-running it neither duplicates canonical tags nor
re-injects the article block.

Run from the repository root. Configured via environment variables:

* ``MDBOOK_OUTPUT_DIR`` - built book directory (default ``hkj-book/book``)
* ``MDBOOK_SRC_DIR`` - book source directory (default ``hkj-book/src``)
* ``HKJ_CANONICAL_BASE`` - canonical base URL
  (default ``https://higher-kinded-j.github.io/latest/``)
* ``HKJ_SOFTWARE_VERSION`` - the released version the docs describe (optional)
"""

import html
import json
import os
import re

import book_sources

# Built pages that must never be advertised as canonical/indexable content.
EXCLUDED_FILES = {"404.html", "print.html", "toc.html"}

OG_URL_RE = re.compile(
    r'(<meta\s+property="og:url"\s+content=")[^"]*(">)', re.IGNORECASE
)
CANONICAL_RE = re.compile(r'<link\s+rel="canonical"', re.IGNORECASE)
TECHARTICLE_MARKER = "hkj-techarticle"
HEAD_CLOSE_RE = re.compile(r"</head>", re.IGNORECASE)
ALTERNATE_RE = re.compile(r'<link\s+rel="alternate"\s+type="text/markdown"', re.IGNORECASE)
# The site-wide JSON-LD block from head.hbs: the one whose body has an @graph.
SITE_LDJSON_RE = re.compile(
    r'(<script type="application/ld\+json">)'
    r'((?:(?!</script>).)*"@graph"(?:(?!</script>).)*)'
    r"(</script>)",
    re.DOTALL,
)

# Meta tags whose content is replaced per page: (attribute, name) pairs.
DESCRIPTION_TAGS = [
    ("name", "description"),
    ("property", "og:description"),
    ("name", "twitter:description"),
]
KEYWORDS_TAG = ("name", "keywords")

# Keywords for every page, then each chapter's own, keyed by the chapter's
# slug (its URL directory). Search engines give meta keywords little weight;
# the TechArticle block carries the same list for crawlers that read it.
BASE_KEYWORDS = ["Higher-Kinded-J", "Java", "functional programming"]
CHAPTER_KEYWORDS = {
    "effect": [
        "Effect Path API", "error handling", "typed errors", "railway-oriented programming",
        "EitherPath", "MaybePath", "TryPath", "ValidationPath", "IOPath", "VTaskPath", "ForPath",
    ],
    "optics": [
        "optics", "lens", "prism", "traversal", "iso", "Focus DSL", "immutable records",
        "nested updates", "@GenerateLenses", "@GenerateFocus", "sealed interfaces",
    ],
    "mapping": [
        "DTO mapping", "object mapping", "@GenerateMapping", "MapStruct alternative",
        "Bean Validation", "request validation", "422 Unprocessable Content", "field errors",
        "REST PATCH", "JsonNullable", "protobuf-java", "gRPC", "Lombok", "openapi-generator",
        "annotation processor", "compile-time code generation",
    ],
    "transformers": [
        "monad transformers", "EitherT", "MaybeT", "OptionalT", "ReaderT", "StateT", "WriterT",
        "MTL", "MonadReader", "MonadState", "MonadWriter",
    ],
    "hkts": [
        "higher-kinded types", "HKT", "Kind", "defunctionalisation", "type classes", "Functor",
        "Applicative", "Monad", "Either", "Maybe", "Validated", "Try", "IO",
    ],
    "examples": [
        "examples", "order processing workflow", "Effect Path API", "optics", "virtual threads",
    ],
    "tutorials": ["tutorials", "exercises", "hands-on learning", "Effect Path API", "optics"],
    "tooling": [
        "Gradle plugin", "Maven plugin", "annotation processor", "compile-time checks",
        "OpenRewrite", "hkj-test", "AssertJ", "Claude Code skills",
    ],
    "spring": [
        "Spring Boot", "REST controllers", "HTTP status mapping", "422 Unprocessable Content",
        "@HkjHttpClient", "declarative HTTP clients", "Jackson",
    ],
    "reference": ["glossary", "release notes", "benchmarks"],
}


def meta_content_re(attribute: str, name: str) -> "re.Pattern":
    """Match one meta tag, capturing everything around its content value."""
    return re.compile(
        r'(<meta\s+' + attribute + r'="' + re.escape(name) + r'"\s+content=")[^"]*(")',
        re.IGNORECASE,
    )


def set_meta(content: str, attribute: str, name: str, value: str) -> str:
    """Replace the content of every ``<meta attribute="name">`` tag."""
    escaped = html.escape(value, quote=True)
    pattern = meta_content_re(attribute, name)
    return pattern.sub(lambda m: m.group(1) + escaped + m.group(2), content)


def page_url(base_url: str, book_dir: str, file_path: str) -> str:
    """Map a built HTML file to its canonical URL under ``base_url``."""
    rel = os.path.relpath(file_path, book_dir).replace(os.path.sep, "/")
    # Canonicalise directory index pages to the clean directory URL
    # (".../" rather than ".../index.html"), including the site root.
    if rel == "index.html":
        return base_url
    if rel.endswith("/index.html"):
        return base_url + rel[: -len("index.html")]
    return base_url + rel


def techarticle_ldjson(url: str, meta: dict) -> str:
    """Build a per-page TechArticle JSON-LD ``<script>`` for the given page."""
    data = {
        "@context": "https://schema.org",
        "@type": "TechArticle",
        "headline": meta["title"],
        "url": url,
        "mainEntityOfPage": {"@type": "WebPage", "@id": url},
        "inLanguage": "en-GB",
        "isPartOf": {"@id": "https://higher-kinded-j.github.io/#website"},
        "about": {"@id": "https://higher-kinded-j.github.io/#software"},
        "author": {"@id": "https://higher-kinded-j.github.io/#author"},
        "publisher": {"@id": "https://higher-kinded-j.github.io/#author"},
    }
    if meta.get("description"):
        data["description"] = meta["description"]
    if meta.get("keywords"):
        data["keywords"] = meta["keywords"]
    if meta.get("section"):
        data["articleSection"] = meta["section"]
    if meta.get("modified"):
        data["dateModified"] = meta["modified"]
    # json.dumps handles all string escaping, keeping the block valid JSON.
    return (
        f'<script type="application/ld+json" data-hkj="{TECHARTICLE_MARKER}">'
        + json.dumps(data, ensure_ascii=False)
        + "</script>"
    )


def set_software_version(content: str, version: str) -> str:
    """Set ``softwareVersion`` on the site-wide ``SoftwareApplication`` node."""

    def replace(match: "re.Match") -> str:
        try:
            data = json.loads(match.group(2))
        except ValueError:
            return match.group(0)
        for node in data.get("@graph", []):
            if node.get("@id", "").endswith("#software"):
                node["softwareVersion"] = version
        return match.group(1) + json.dumps(data, ensure_ascii=False) + match.group(3)

    return SITE_LDJSON_RE.sub(replace, content, count=1)


def page_meta(src_dir: str, page, dates: dict) -> dict:
    """Collect what one source page contributes to its HTML head."""
    with open(os.path.join(src_dir, page.path), encoding="utf-8") as fh:
        description = book_sources.page_description(fh.read())
    return {
        "title": page.title,
        "description": description,
        "keywords": BASE_KEYWORDS + CHAPTER_KEYWORDS.get(page.chapter.slug, []),
        "section": page.chapter.title,
        "modified": dates.get(page.path),
        "markdown": page.path,
    }


def process_file(file_path: str, url: str, base_url: str, meta, version: str) -> bool:
    """Apply the SEO fix-ups to one file. Returns True if it was changed.

    ``meta`` is ``None`` for a file with no source page.
    """
    with open(file_path, encoding="utf-8") as fh:
        content = fh.read()
    original = content

    # HTML-escape the URL for use in attribute values. (The JSON-LD block below
    # takes the raw URL - json.dumps handles its escaping.)
    href = html.escape(url, quote=True)

    # 1. Rewrite og:url to the real page URL. A function replacement avoids
    #    re.sub interpreting backslashes / group refs in the URL, and needs no
    #    manual escaping of the replacement text.
    content = OG_URL_RE.sub(lambda m: m.group(1) + href + m.group(2), content, count=1)

    # 2. The page's own description and keywords replace the site-wide ones.
    if meta is not None:
        if meta["description"]:
            for attribute, name in DESCRIPTION_TAGS:
                content = set_meta(content, attribute, name, meta["description"])
        content = set_meta(content, *KEYWORDS_TAG, ", ".join(meta["keywords"]))
    if version:
        content = set_software_version(content, version)

    # 3. Inject canonical, TechArticle and the Markdown alternate before
    #    </head>, once each.
    additions = []
    if not CANONICAL_RE.search(content):
        additions.append(f'<link rel="canonical" href="{href}">')
    if meta is not None and TECHARTICLE_MARKER not in content:
        additions.append(techarticle_ldjson(url, meta))
    if meta is not None and not ALTERNATE_RE.search(content):
        markdown = html.escape(base_url + meta["markdown"], quote=True)
        additions.append(f'<link rel="alternate" type="text/markdown" href="{markdown}">')

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
    src_dir = os.environ.get("MDBOOK_SRC_DIR", "hkj-book/src")
    base_url = os.environ.get(
        "HKJ_CANONICAL_BASE", "https://higher-kinded-j.github.io/latest/"
    )
    version = os.environ.get("HKJ_SOFTWARE_VERSION", "").strip().lstrip("v")
    if not base_url.endswith("/"):
        base_url += "/"

    if not os.path.isdir(book_dir):
        raise SystemExit(f"Error: book directory '{book_dir}' does not exist")

    pages = {
        page.path: page
        for page in book_sources.all_pages(book_sources.read_summary(src_dir))
        if os.path.isfile(os.path.join(src_dir, page.path))
    }
    first_page = next(iter(pages), None)
    dates = book_sources.page_dates(src_dir, list(pages.values()))
    metas = {path: page_meta(src_dir, page, dates) for path, page in pages.items()}

    changed = 0
    total = 0
    described = 0
    for root, _, files in os.walk(book_dir):
        for name in files:
            if not name.endswith(".html") or name in EXCLUDED_FILES:
                continue
            total += 1
            path = os.path.join(root, name)
            rel = os.path.relpath(path, book_dir).replace(os.path.sep, "/")
            meta = metas.get(book_sources.html_source(rel, first_page))
            if meta is not None and meta["description"]:
                described += 1
            url = page_url(base_url, book_dir, path)
            if process_file(path, url, base_url, meta, version):
                changed += 1

    print(f"SEO post-processing complete: {changed}/{total} HTML files updated.")
    print(f"Pages with their own description: {described}; dated: {len(dates)}.")
    print(f"Canonical base: {base_url}")
    if version:
        print(f"softwareVersion: {version}")


if __name__ == "__main__":
    main()
