#!/usr/bin/env python3
"""Keep a chapter's Previous/Next footers in step with SUMMARY.md.

    python3 .github/scripts/chapter_footers.py CHAPTER [CHAPTER ...] [--check]

A chapter is named as book_sources names it: the directory of its first page (``optics``,
``mapping``). Its pages are every SUMMARY.md entry indented under that first one, in order. As the
Style Guide's Navigation Links say, the first page gets only a Next link, the last only a Previous
link, and every other page both, each linking the neighbour's SUMMARY title.

Run it after reordering SUMMARY.md. With --check it changes nothing, lists every footer that is out
of date, and exits 1 if there is one. A page whose Previous or Next line is not at its end is
reported, never rewritten, because the tool cannot tell which line is the footer.
"""
import argparse
import os
import posixpath
import re
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from book_sources import read_summary  # noqa: E402

SRC = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../hkj-book/src"))
NAV = re.compile(r"^\*\*(Previous|Next):\*\*")


def link(from_page, to_page):
    return posixpath.relpath(to_page, posixpath.dirname(from_page) or ".")


def footer(pages, i):
    lines = []
    if i > 0:
        lines.append(f"**Previous:** [{pages[i - 1].title}]({link(pages[i].path, pages[i - 1].path)})")
    if i + 1 < len(pages):
        lines.append(f"**Next:** [{pages[i + 1].title}]({link(pages[i].path, pages[i + 1].path)})")
    return lines


def rewrite(text, new_footer):
    """The page with its trailing footer replaced, or None if a footer line sits elsewhere."""
    lines = text.rstrip("\n").split("\n")
    while lines and (NAV.match(lines[-1]) or not lines[-1].strip()):
        lines.pop()
    if any(NAV.match(line) for line in lines):
        return None
    if lines and lines[-1].strip() != "---":
        lines += ["", "---"]
    return "\n".join(lines + [""] + new_footer) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("chapters", nargs="+", help="chapter names, such as optics or mapping")
    parser.add_argument("--check", action="store_true", help="report, change nothing")
    args = parser.parse_args()

    chapters = {chapter.slug: chapter for chapter in read_summary(SRC)}
    status = 0
    for name in args.chapters:
        chapter = chapters.get(name)
        if chapter is None:
            sys.exit(f"No chapter '{name}' in SUMMARY.md; chapters are: {', '.join(sorted(chapters))}")
        pages = chapter.pages
        stale, misplaced = [], []
        for i, page in enumerate(pages):
            path = os.path.join(SRC, page.path)
            with open(path, encoding="utf-8", newline="") as fh:
                text = fh.read().replace("\r\n", "\n")
            updated = rewrite(text, footer(pages, i))
            if updated is None:
                misplaced.append(page.path)
            elif updated != text:
                stale.append(page.path)
                if not args.check:
                    with open(path, "w", encoding="utf-8", newline="\n") as fh:
                        fh.write(updated)
        verb = "out of date" if args.check else "rewritten"
        print(f"{name}: {len(pages)} pages, {len(stale)} footers {verb}")
        for path in (stale if args.check else []) + misplaced:
            reason = "a Previous or Next line is not at the end" if path in misplaced else "out of date"
            print(f"  {path}: {reason}")
        if misplaced or (args.check and stale):
            status = 1
    sys.exit(status)


if __name__ == "__main__":
    main()
