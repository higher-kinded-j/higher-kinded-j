"""Read the book's Markdown sources for the post-build scripts.

The scripts that run after ``mdbook build`` (``postprocess_seo.py``,
``generate_llms_files.py`` and ``generate_sitemap.py``) all need the same view of
the sources: the pages in ``SUMMARY.md`` order, grouped into chapters, with the
mdBook ``{{#include}}`` directives resolved, a one-line description of each
page, and the date each page last changed. This module is that view, so the
three scripts agree on it.

Only the standard library is used, and nothing here writes a file.
"""

import html
import os
import re
import subprocess
import sys
from dataclasses import dataclass, field

# A SUMMARY.md entry: optional list marker, then [Title](path.md).
SUMMARY_ENTRY_RE = re.compile(r"^(\s*)(-\s+)?\[([^\]]*)\]\(([^)]+\.md)\)")

# mdBook include-family directives, capturing the argument (path[:spec]).
INCLUDE_RE = re.compile(r"\{\{#(?:include|rustdoc_include|playground)\s+([^}]+?)\s*\}\}")

# A page subtitle: a whole line in italics directly under the H1, as the
# Mapping chapter writes them ("_Map a PATCH request so ..._").
SUBTITLE_RE = re.compile(r"^_([^_].*[^_])_$")

# An explicit description, for a page whose opening is a quote or a story:
# <!-- description: One sentence for search results. -->
DESCRIPTION_MARKER_RE = re.compile(r"^<!--\s*description:\s*(.+?)\s*-->\s*$", re.MULTILINE)

# The opening line of the admonition that lists what a page teaches.
LEARN_RE = re.compile(
    r"""^~~~admonish\s+\w+\s+title="(What (You'll|We'll) Learn|What This Page Covers)\""""
)

DESCRIPTION_LIMIT = 160


@dataclass
class Chapter:
    slug: str
    title: str
    pages: list = field(default_factory=list)


@dataclass
class Page:
    path: str  # relative to the source directory, e.g. "mapping/basics.md"
    title: str
    chapter: Chapter


def warn(message: str) -> None:
    """Emit a diagnostic to stderr so it stays separate from the output."""
    print(message, file=sys.stderr)


def chapter_slug(path: str) -> str:
    """Name a chapter after its first page: the directory, else the file stem.

    ``mapping/ch_intro.md`` gives ``mapping``, and ``reference_intro.md`` gives
    ``reference``, so the name matches the chapter's URLs.
    """
    directory = os.path.dirname(path)
    if directory:
        return directory.split("/")[0]
    stem = os.path.splitext(os.path.basename(path))[0]
    stem = re.sub(r"_intro$", "", stem)
    return stem.replace("_", "-").lower()


def read_summary(src_dir: str) -> list:
    """Return the chapters of ``SUMMARY.md`` in reading order.

    A top-level entry starts a chapter, and every entry indented under it
    belongs to that chapter. A page listed twice keeps its first place.
    """
    summary_path = os.path.join(src_dir, "SUMMARY.md")
    if not os.path.isfile(summary_path):
        raise SystemExit(f"Error: SUMMARY.md not found at '{summary_path}'")

    chapters = []
    seen = set()
    current = None
    with open(summary_path, encoding="utf-8") as fh:
        for line in fh:
            match = SUMMARY_ENTRY_RE.match(line)
            if not match:
                continue
            indent, _, title, target = match.groups()
            target = target.strip()
            if target.startswith(("http://", "https://", "#")):
                continue
            path = os.path.normpath(target).replace(os.sep, "/")
            if current is None or not indent:
                current = Chapter(chapter_slug(path), title.strip())
                chapters.append(current)
            if path in seen:
                continue
            seen.add(path)
            current.pages.append(Page(path, title.strip(), current))
    return chapters


def all_pages(chapters: list) -> list:
    """Flatten chapters into their pages, in reading order."""
    return [page for chapter in chapters for page in chapter.pages]


def _extract_anchor(lines: list, name: str):
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
        if "ANCHOR:" in line or "ANCHOR_END:" in line:
            continue
        out.append(line)
    return out if found else None


def _resolve_include_arg(arg: str, base_dir: str):
    """Resolve one include argument to ``(lines, file_path, error)``."""
    parts = arg.split(":")
    rel_path = parts[0].strip()
    spec = [p.strip() for p in parts[1:]]

    file_path = os.path.normpath(os.path.join(base_dir, rel_path))
    if not os.path.isfile(file_path):
        return None, None, f"file not found: {rel_path}"
    with open(file_path, encoding="utf-8") as fh:
        lines = fh.read().splitlines()

    if not spec:  # {{#include file}}
        return lines, file_path, None
    if len(spec) == 1:
        token = spec[0]
        if token.isdigit():  # {{#include file:LINE}}
            idx = int(token) - 1
            return (lines[idx : idx + 1] if 0 <= idx < len(lines) else []), file_path, None
        anchored = _extract_anchor(lines, token)  # {{#include file:anchor}}
        if anchored is None:
            return None, None, f"anchor '{token}' not found in {rel_path}"
        return anchored, file_path, None
    if len(spec) == 2:  # {{#include file:START:END}} (either may be empty)
        start = int(spec[0]) - 1 if spec[0] else 0
        stop = int(spec[1]) if spec[1] else len(lines)
        return lines[start:stop], file_path, None
    return None, None, f"unsupported include spec: {arg}"


def resolve_includes(md_text: str, md_path: str, counters: dict = None):
    """Replace every include directive in ``md_text`` with the code it names.

    Returns ``(text, included_files)``. An unresolved directive becomes an HTML
    comment and is logged; ``counters``, when given, counts both outcomes.
    """
    base_dir = os.path.dirname(md_path)
    included = []

    def replace(match: "re.Match") -> str:
        arg = match.group(1)
        content, file_path, error = _resolve_include_arg(arg, base_dir)
        if error is not None:
            warn(f"  include unresolved ({md_path}): {arg} - {error}")
            if counters is not None:
                counters["unresolved"] += 1
            return f"<!-- include unresolved: {arg} -->"
        if counters is not None:
            counters["resolved"] += 1
        included.append(file_path)
        return "\n".join(content)

    return INCLUDE_RE.sub(replace, md_text), included


def included_files(md_path: str) -> list:
    """The files a page's include directives name, without reading their regions."""
    with open(md_path, encoding="utf-8") as fh:
        text = fh.read()
    base_dir = os.path.dirname(md_path)
    files = []
    for match in INCLUDE_RE.finditer(text):
        rel_path = match.group(1).split(":")[0].strip()
        file_path = os.path.normpath(os.path.join(base_dir, rel_path))
        if os.path.isfile(file_path):
            files.append(file_path)
    return files


def plain_text(markdown: str) -> str:
    """Flatten a line of Markdown to the text a reader sees.

    A code span keeps its text verbatim, so ``VTask<Result<A>>`` and
    ``snake_case`` survive; outside code spans, links keep their text and
    images, inline HTML, heading ids and emphasis markers go.
    """
    text = re.sub(r"!\[[^\]]*\]\([^)]*\)", "", markdown)  # images
    text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", text)  # links keep their text
    pieces = re.split(r"(`[^`]*`)", text)
    for i, piece in enumerate(pieces):
        if piece.startswith("`") and piece.endswith("`") and len(piece) > 1:
            pieces[i] = piece[1:-1]
            continue
        piece = re.sub(r"\s*\{#[^}]*\}", "", piece)  # heading ids
        piece = re.sub(r"<[^>]+>", "", piece)  # inline HTML
        piece = piece.replace("**", "").replace("__", "")
        piece = re.sub(r"(?<![\w])[*_]|[*_](?![\w])", "", piece)
        pieces[i] = html.unescape(piece)
    return re.sub(r"\s+", " ", "".join(pieces)).strip()


def _truncate(text: str, limit: int = DESCRIPTION_LIMIT) -> str:
    if len(text) <= limit:
        return text
    cut = text[: limit - 1]
    if " " in cut:
        cut = cut[: cut.rindex(" ")]
    return cut.rstrip(" ,;:") + "…"


def _body_after_title(lines: list):
    """The lines after the page's H1, or ``None`` when the page has no H1."""
    for index, line in enumerate(lines):
        if line.startswith("# "):
            return lines[index + 1 :]
    return None


def _subtitle(body: list):
    for line in body:
        stripped = line.strip()
        if not stripped:
            continue
        match = SUBTITLE_RE.match(stripped)
        return plain_text(match.group(1)) if match else None
    return None


def _learn_bullets(body: list):
    """The top-level bullets of the page's "What You'll Learn" admonition."""
    for index, line in enumerate(body):
        if LEARN_RE.match(line.strip()):
            bullets = []
            for inner in body[index + 1 :]:
                if inner.startswith("~~~"):
                    break
                if inner.startswith("- "):
                    bullets.append(plain_text(inner[2:]))
            return bullets
    return []


def _first_paragraph(body: list):
    """The first paragraph of prose on the page, admonitions included.

    A paragraph opens with a letter, a code span or a link; headings, quotes,
    lists, tables, HTML and code blocks are passed over, as is a paragraph too
    short to describe anything, such as a lone link.
    """
    paragraph = []
    in_code = False
    for line in body + [""]:
        stripped = line.strip()
        if stripped.startswith("```"):
            in_code = not in_code
            stripped = ""
        elif in_code:
            continue
        if stripped.startswith("~~~"):
            stripped = ""  # an admonition's fence; its text is prose
        if not stripped or stripped.startswith("#"):
            text = plain_text(" ".join(paragraph))
            if len(text) >= 50:
                return text
            paragraph = []
            continue
        if paragraph or re.match(r"[A-Za-z`\[]", stripped):
            paragraph.append(stripped)
    return None


def page_description(md_text: str):
    """A one-line description of a page, or ``None`` when none can be taken.

    In order of preference: a ``<!-- description: ... -->`` marker; the italic
    subtitle under the H1; the bullets of the "What You'll Learn" admonition,
    as many as fit; the first paragraph of prose. The result is at most
    ``DESCRIPTION_LIMIT`` characters.
    """
    marker = DESCRIPTION_MARKER_RE.search(md_text)
    if marker:
        return _truncate(plain_text(marker.group(1)))

    body = _body_after_title(md_text.splitlines())
    if body is None:
        return None

    subtitle = _subtitle(body)
    if subtitle:
        return _truncate(subtitle)

    bullets = [b.rstrip(".") for b in _learn_bullets(body) if b]
    if bullets:
        text = bullets[0]
        for bullet in bullets[1:]:
            candidate = f"{text}; {bullet}"
            if len(candidate) + 1 > DESCRIPTION_LIMIT:
                break
            text = candidate
        return _truncate(text + ".")

    paragraph = _first_paragraph(body)
    return _truncate(paragraph) if paragraph else None


def last_modified(paths: list) -> dict:
    """Map each tracked file to the date (``YYYY-MM-DD``) of its last commit.

    One ``git log`` covers every path. A file git does not track, or a run
    outside a repository, has no entry.
    """
    paths = [os.path.abspath(p) for p in paths]
    if not paths:
        return {}
    try:
        root = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            cwd=os.path.dirname(paths[0]),
            capture_output=True,
            text=True,
            check=True,
        ).stdout.strip()
        relative = sorted({os.path.relpath(p, root) for p in paths})
        log = subprocess.run(
            ["git", "log", "--format=%x00%cs", "--name-only", "--"] + relative,
            cwd=root,
            capture_output=True,
            text=True,
            check=True,
        ).stdout
    except (OSError, subprocess.CalledProcessError) as error:
        warn(f"  git dates unavailable: {error}")
        return {}

    dates = {}
    date = None
    for line in log.splitlines():
        if line.startswith("\x00"):
            date = line[1:]
        elif line and date:
            path = os.path.join(root, line)
            if date > dates.get(path, ""):
                dates[path] = date
    return dates


def page_dates(src_dir: str, pages: list) -> dict:
    """Map each page path to the last date its source or an included file changed."""
    sources = {}
    for page in pages:
        md_path = os.path.join(src_dir, page.path)
        if os.path.isfile(md_path):
            sources[page.path] = [os.path.abspath(md_path)] + [
                os.path.abspath(f) for f in included_files(md_path)
            ]
    dates = last_modified([f for files in sources.values() for f in files])
    result = {}
    for path, files in sources.items():
        known = [dates[f] for f in files if f in dates]
        # A page git does not track (a file CI copies in) has no date, even
        # when a file it includes does.
        if known and files[0] in dates:
            result[path] = max(known)
    return result


def html_source(html_rel: str, first_page: str):
    """The source page a built HTML file renders, as a SUMMARY path.

    mdBook writes ``index.html`` as a copy of the book's first page.
    """
    if html_rel == "index.html":
        return first_page
    if html_rel.endswith(".html"):
        return html_rel[: -len(".html")] + ".md"
    return None
