# Foundations Chapter Guide

This guide holds the rules particular to the book's Foundations chapter and the examples behind it.
The chapter is the block `SUMMARY.md` lists under `hkts/foundations_intro.md`, with pages in
`hkj-book/src/hkts/`, `functional/` and `monads/`. The [Style Guide](STYLE-GUIDE.md) applies here as
everywhere, and this guide links to it rather than repeating it. Follow both in every pull request
that touches the chapter, including a feature pull request that changes a type class, a core type or
the `Kind` encoding.

`hkj-book/src/hkts/` also holds the Examples Gallery's order and draughts pages (`order-*.md`,
`draughts.md`). They belong to that chapter, not this one.

## Reading Lanes

The chapter's pages form three reading lanes, the Style Guide's groups. A change goes on the page
whose reader needs it.

| Lane | Pages | Reader and job |
|---|---|---|
| Ship | `foundations_intro`, `one_line_six_layers`, `core-concepts`, `usage-guide`, `instances_facade`, `functor`, `applicative`, `monad`, `monad_error`, `for_comprehension` (which absorbed `for_par` and `for_traverse`) | Reads in order, then writes one method that works for any container. |
| On demand | Three groups, each led by a group introduction: Higher-Kinded Types (`hkts/ch_intro`), Type Classes (`functional/ch_intro`) and Core Types (`monads/ch_intro`) | Reads one page when a task needs it, often arriving from a search for one type. |
| Look it up | `supported-types`, `quick_reference`, `functional_api`, `faq`, `hkt_basic_examples` | Arrives holding a question. |

`SUMMARY.md` holds the order, and the chapter introduction's Chapter Contents lists the lanes. A
group's pages are nested under its introduction in `SUMMARY.md`, so the sidebar shows the lane first.

- **No page changes its URL.** Readers, other chapters and the library's javadoc link to these pages,
  and mdbook URLs follow files rather than sidebar nesting, so a reorder moves nothing. Headings keep
  their ids as the Style Guide's [Anchors](STYLE-GUIDE.md#anchors) rules say.
- **A new page needs a reader no existing page serves.** It joins its lane or group in the chapter
  introduction's contents, takes its place in `SUMMARY.md`, and appears in its group introduction's
  list.
- **Previous and Next follow `SUMMARY.md`.** After reordering it, regenerate the footers with
  `python3 .github/scripts/chapter_footers.py hkts`, in a commit of their own so a reviewer can skip
  it.

## Group Introductions

Each On demand group's introduction follows the Style Guide's
[Group Introductions](STYLE-GUIDE.md#group-introductions): at most 400 words of prose and one list,
titled "Pages in this group", in `SUMMARY.md` order. Core Types sets its list under the names of its
families. A group links the chapter's decision pages rather than drawing a tree of its own:
[Supported Types](../hkj-book/src/monads/supported-types.md) for choosing a core type, and
[Choosing Abstraction Levels](../hkj-book/src/functional/abstraction_levels.md) for choosing between
Applicative, Selective and Monad.

## Which Level a Page Works At

Higher-Kinded-J offers the same operations at two levels. Effect Paths (`MaybePath`, `EitherPath`)
are concrete types for application code, with no witness, `widen` or `narrow` to write. Type classes
(`Functor<F>`, `Monad<F>`) work over `Kind<F, A>`, for one method that serves any container. This
chapter teaches the second level and must never imply that the first is built on it: an `EitherPath`
holds an `Either` and calls it directly, with no type class instance in between. `GenericPath` is
the bridge from the second level to the first.

- **The chapter introduction's "Which level am I working at?" tip is the chapter's one statement of
  the levels.** Other pages link to it rather than restate it.
- **The Core Types group lists its pages by family**: Failure, Absence, Deferred, Virtual threads,
  Environment and state, Collections, and Programs as data, in that order.

## Reference Pages

The Look it up pages follow the Style Guide's [Reference Pages](STYLE-GUIDE.md#reference-pages)
shape: no epigraph, no "What You'll Learn" and no "Key Takeaways". Each opens with the thing its
reader came for.

## Examples

- **A page's compiled examples live in `hkj-examples`** under
  `org.higherkindedj.example.book.foundations`, one package per page, as the chapter introduction's
  `intro` package does. A new or rewritten page gets a `<Topic>Book.java` beside a
  `<Topic>BookTest.java` that holds the page's claims, and its runnable blocks are includes from
  them, as the Style Guide's
  [Java code in hkj-book must be verified](STYLE-GUIDE.md#java-code-in-hkj-book-must-be-verified)
  rule says. A shape that cannot run, such as a signature over type variables, is a `verify` fence.
- **An output comment sits on a line of its own** after the statement whose value it shows, and the
  example's `main` prints that same variable after the region, so `BookExampleOutputTest` can hold
  the page to it.
- **An `// ANCHOR:` comment goes after the `package` line.** Spotless's licence-header step deletes
  anything above it.
- **The chapter's running cast is the order service** the Optics and Mapping chapters share, in
  `org.higherkindedj.example.book.optics.cast`: `Order`, `Customer`, `EmailAddress`, `LineItem` and
  `OrderStatus`, among others. A name keeps one shape across the three chapters. A type this chapter
  needs and the cast lacks is named for its role beside the cast, and the Style Guide's
  [One Cast per Chapter](STYLE-GUIDE.md#one-cast-per-chapter) says when a page may add one. Those
  role types, such as `OrderRepository` and `OrderError`, live in
  `org.higherkindedj.example.book.foundations`, the parent of the page packages, so every page shares
  them.
- **A width proof keeps placeholder types**, such as the arity examples, with a comment saying why.

## Diagrams

The Style Guide's [Diagrams](STYLE-GUIDE.md#diagrams) rules hold here. In this chapter's mermaid
diagrams, the `step` class (`fill:#8caaee,stroke:#1e66f5,color:#232634`) marks the mechanism (the
encoding, a type class, a call), and the `domain` class (`fill:#a6d189,stroke:#40a02b,color:#232634`)
marks a type a reader holds (a core type, an Effect Path). Errors and decisions take the Style Guide's
`error` and `decision` classes.

## Checks

Run these before opening the pull request:

```bash
./gradlew :hkj-examples:test :hkj-examples:bookVerify    # the pages' tests, then the book gate
hkj-book/check.sh                                        # headings, links, diagrams, readability
python3 .github/scripts/chapter_footers.py hkts --check  # Previous and Next match SUMMARY.md
hkj-book/serve.sh                                        # render with the pinned mdbook
```

Claude Code sessions in this repository can load the `book-authoring` skill
(`.claude/skills/book-authoring/`), which carries the procedure and the traps. This guide stays the
source of truth for the rules.
