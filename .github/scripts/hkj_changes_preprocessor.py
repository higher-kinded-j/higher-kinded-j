#!/usr/bin/env python3
"""mdBook preprocessor: mark content that is new/changed relative to a baseline.

For every chapter the preprocessor compares the current markdown source against
a baseline version (the previous release, typically) and injects lightweight
HTML markers around the blocks that were added or edited. A companion CSS/JS
pair (`theme/change-tracking.css`, `theme/change-tracking.js`) turns those
markers into a vertical margin line plus a faint underline of the individual
words that changed, with a three-state header toggle.

Design notes
------------
* We never wrap markdown in a `<div>` (pulldown-cmark would stop parsing the
  markdown inside it). Instead we emit an *empty* marker `<div>` on its own
  line immediately before each changed block. The block still renders as normal
  markdown; the runtime JS uses each marker's `nextElementSibling` as "the
  block" and does the DOM wrapping / word underlining safely on rendered HTML.
* Block-level classification (`new` vs `modified`) and the per-block word diff
  are computed here, at build time, and shipped to the browser as marker data
  attributes. The browser only has to *match* tokens, never diff.

Baseline resolution (first that is set wins):
* ``HKJ_CHANGES_BASELINE_DIR`` — a directory holding a snapshot of the book
  ``src`` tree. Handy for local testing.
* ``HKJ_CHANGES_BASE`` — a git ref (tag/commit). Baseline content is read with
  ``git show <ref>:<repo-path>``.

If neither is set (or a lookup fails), the preprocessor is a no-op and passes
the book through unchanged, so it is always safe to leave enabled.
"""

import base64
import json
import os
import subprocess
import sys
from difflib import SequenceMatcher

# Repository path to the book source, used to build `git show` targets.
SRC_PREFIX = os.environ.get("HKJ_CHANGES_SRC_PREFIX", "hkj-book/src")

BASELINE_DIR = os.environ.get("HKJ_CHANGES_BASELINE_DIR", "").strip()
BASELINE_REF = os.environ.get("HKJ_CHANGES_BASE", "").strip()
SINCE_LABEL = os.environ.get("HKJ_CHANGES_SINCE_LABEL", BASELINE_REF).strip()

# Chapters to skip. The deploy workflow copies these root files into src at
# build time, so they never exist under `<baseline>:hkj-book/src/` and would
# otherwise be flagged as "new" on every deploy. Override with a comma-
# separated HKJ_CHANGES_EXCLUDE list.
_DEFAULT_EXCLUDE = "CONTRIBUTING.md,LICENSE.md,CODE_OF_CONDUCT.md"
EXCLUDE_PATHS = frozenset(
    p.strip()
    for p in os.environ.get("HKJ_CHANGES_EXCLUDE", _DEFAULT_EXCLUDE).split(",")
    if p.strip()
)

MARK_CLASS = "hkj-ct-mark"


def enabled():
    return bool(BASELINE_DIR or BASELINE_REF)


def baseline_source(chapter_path):
    """Return the baseline markdown for `chapter_path`, or None if it is new."""
    if not chapter_path:
        return None
    if BASELINE_DIR:
        candidate = os.path.join(BASELINE_DIR, chapter_path)
        try:
            with open(candidate, "r", encoding="utf-8") as handle:
                return handle.read()
        except FileNotFoundError:
            return None
    if BASELINE_REF:
        target = "{ref}:{prefix}/{path}".format(
            ref=BASELINE_REF, prefix=SRC_PREFIX, path=chapter_path
        )
        try:
            result = subprocess.run(
                ["git", "show", target],
                capture_output=True,
                encoding="utf-8",
                check=False,
            )
        except OSError:
            return None
        if result.returncode != 0:
            return None  # File did not exist in baseline -> treat chapter as new.
        return result.stdout
    return None


# --- block segmentation -----------------------------------------------------

def _fence_marker(line):
    """Return (char, length) if `line` opens/closes a fenced block, else None."""
    stripped = line.lstrip()
    for ch in ("`", "~"):
        if stripped.startswith(ch * 3):
            run = len(stripped) - len(stripped.lstrip(ch))
            return ch, run
    return None


def split_blocks(text):
    """Split markdown into top-level blocks.

    Blank lines separate blocks, except inside fenced regions (``` code ```
    and ~~~admonish ... ~~~), which are kept atomic. Returns a list of dicts
    with the block text and its 0-based start/end line indices.
    """
    lines = text.splitlines()
    blocks = []
    current = []
    start = 0
    fence = None  # (char, length) when inside a fence.

    def flush(end_idx):
        nonlocal current, start
        if current and any(l.strip() for l in current):
            blocks.append(
                {
                    "text": "\n".join(current),
                    "start": start,
                    "end": end_idx,
                }
            )
        current = []

    for i, line in enumerate(lines):
        marker = _fence_marker(line)
        if fence is None:
            if marker is not None:
                # Opening a fence: flush any preceding text first so the fence
                # is its own block even when no blank line separates them.
                if current:
                    flush(i)
                start = i
                fence = marker
                current.append(line)
                continue
            if line.strip() == "":
                flush(i)
                start = i + 1
            else:
                if not current:
                    start = i
                current.append(line)
        else:
            current.append(line)
            # A closing fence uses the same char and at least the same length.
            if marker is not None and marker[0] == fence[0] and marker[1] >= fence[1]:
                fence = None
                # Flush the fenced block so following text (with no blank line)
                # becomes a separate block.
                flush(i + 1)
                start = i + 1
    flush(len(lines))
    return blocks


def normalize_block(text):
    """Whitespace-insensitive key so reflowed-but-identical blocks still match."""
    return " ".join(text.split())


# --- word diff --------------------------------------------------------------

def tokenize_words(text):
    """Split a block into word tokens for the intra-block diff.

    We strip common markdown punctuation from the edges so the runtime, which
    matches against *rendered* text, sees comparable tokens.
    """
    raw = text.split()
    words = []
    for tok in raw:
        cleaned = tok.strip("`*_[](){}#>-.,:;!?\"'")
        if cleaned:
            words.append(cleaned)
    return words


def changed_words(old_text, new_text):
    """Tokens present on the new side of an insert/replace (the edited words)."""
    old_words = tokenize_words(old_text)
    new_words = tokenize_words(new_text)
    matcher = SequenceMatcher(a=old_words, b=new_words, autojunk=False)
    changed = []
    for tag, _i1, _i2, j1, j2 in matcher.get_opcodes():
        if tag in ("insert", "replace"):
            changed.extend(new_words[j1:j2])
    return changed


# --- chapter processing -----------------------------------------------------

def make_marker(kind, words, run_pos):
    payload = ""
    if words:
        raw = json.dumps(words, ensure_ascii=False).encode("utf-8")
        payload = base64.b64encode(raw).decode("ascii")
    attrs = [
        'class="{cls}"'.format(cls=MARK_CLASS),
        'data-kind="{kind}"'.format(kind=kind),
        'data-run="{run}"'.format(run=run_pos),
    ]
    if payload:
        attrs.append('data-words="{payload}"'.format(payload=payload))
    if SINCE_LABEL:
        attrs.append('data-since="{since}"'.format(since=SINCE_LABEL))
    return "<div {attrs}></div>".format(attrs=" ".join(attrs))


def classify_blocks(new_blocks, old_blocks):
    """Return {new_block_index: (kind, baseline_text_or_None)} for changed blocks."""
    old_keys = [normalize_block(b["text"]) for b in old_blocks]
    new_keys = [normalize_block(b["text"]) for b in new_blocks]
    matcher = SequenceMatcher(a=old_keys, b=new_keys, autojunk=False)
    changed = {}
    for tag, i1, i2, j1, j2 in matcher.get_opcodes():
        if tag == "equal":
            continue
        if tag == "insert":
            for j in range(j1, j2):
                changed[j] = ("new", None)
        elif tag == "replace":
            old_slice = old_blocks[i1:i2]
            for offset, j in enumerate(range(j1, j2)):
                if offset < len(old_slice):
                    changed[j] = ("modified", old_slice[offset]["text"])
                else:
                    changed[j] = ("new", None)
        # 'delete' removes only from the old side -> nothing to mark.
    return changed


def annotate(content, blocks, changed):
    """Rebuild the markdown with marker divs inserted before changed blocks."""
    lines = content.splitlines()
    # Map each block's start line to its marker html; compute run positions.
    indices = sorted(changed.keys())
    run_pos = {}
    for pos, idx in enumerate(indices):
        prev_in_run = (idx - 1) in changed
        next_in_run = (idx + 1) in changed
        if prev_in_run and next_in_run:
            run_pos[idx] = "mid"
        elif next_in_run:
            run_pos[idx] = "start"
        elif prev_in_run:
            run_pos[idx] = "end"
        else:
            run_pos[idx] = "solo"

    # Insert markers from the bottom up so earlier line indices stay valid.
    for idx in sorted(indices, reverse=True):
        kind, old_text = changed[idx]
        words = changed_words(old_text, blocks[idx]["text"]) if kind == "modified" else []
        marker = make_marker(kind, words, run_pos[idx])
        at = blocks[idx]["start"]
        # Marker must be its own HTML block: blank line before (unless at top)
        # and after so pulldown-cmark treats it as standalone.
        insertion = [marker, ""]
        lines[at:at] = insertion
    return "\n".join(lines)


def path_to_html(path):
    """Chapter source path -> rendered html path (optics/lenses.md -> .html)."""
    if path.endswith(".md"):
        return path[: -len(".md")] + ".html"
    return path


def process_chapter(chapter):
    """Annotate a chapter's changed blocks. Returns True if anything changed."""
    content = chapter.get("content")
    path = chapter.get("path")
    if not content or not path:
        return False
    if path in EXCLUDE_PATHS:
        return False
    old = baseline_source(path)
    new_blocks = split_blocks(content)
    if old is None:
        # Whole chapter is new: mark every block as new.
        changed = {i: ("new", None) for i in range(len(new_blocks))}
    else:
        old_blocks = split_blocks(old)
        changed = classify_blocks(new_blocks, old_blocks)
    if not changed:
        return False
    chapter["content"] = annotate(content, new_blocks, changed)
    return True


def walk(items, changed_paths):
    for item in items:
        chapter = item.get("Chapter") if isinstance(item, dict) else None
        if chapter is None:
            continue
        if process_chapter(chapter):
            changed_paths.append(path_to_html(chapter["path"]))
        sub = chapter.get("sub_items")
        if sub:
            walk(sub, changed_paths)


def make_manifest(changed_paths):
    """Compact per-page payload the runtime reads to decorate the sidebar TOC."""
    data = json.dumps(sorted(set(changed_paths)), ensure_ascii=False).encode("utf-8")
    payload = base64.b64encode(data).decode("ascii")
    attrs = ['id="hkj-ct-manifest"', 'hidden', 'data-changed="{0}"'.format(payload)]
    if SINCE_LABEL:
        attrs.append('data-since="{0}"'.format(SINCE_LABEL))
    return "<div {0}></div>".format(" ".join(attrs))


def inject_manifest(items, manifest):
    """Prepend the manifest to every chapter so the sidebar can be decorated
    no matter which page the reader is on."""
    for item in items:
        chapter = item.get("Chapter") if isinstance(item, dict) else None
        if chapter is None:
            continue
        if chapter.get("content") is not None and chapter.get("path"):
            chapter["content"] = manifest + "\n\n" + chapter["content"]
        sub = chapter.get("sub_items")
        if sub:
            inject_manifest(sub, manifest)


def main():
    # `supports <renderer>` handshake: we only touch markdown, so accept all.
    if len(sys.argv) > 1 and sys.argv[1] == "supports":
        sys.exit(0)

    # mdBook speaks UTF-8 JSON over stdio; on Windows the default is the active
    # code page (e.g. cp1252), so force UTF-8 to avoid decode/encode errors.
    if hasattr(sys.stdin, "reconfigure"):
        sys.stdin.reconfigure(encoding="utf-8")
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    context_and_book = json.load(sys.stdin)
    _context, book = context_and_book

    if enabled():
        try:
            sections = book.get("sections", [])
            changed_paths = []
            walk(sections, changed_paths)
            inject_manifest(sections, make_manifest(changed_paths))
        except Exception as exc:  # never break the build over annotation errors
            sys.stderr.write("hkj-changes: skipped ({0})\n".format(exc))

    json.dump(book, sys.stdout)


if __name__ == "__main__":
    main()
