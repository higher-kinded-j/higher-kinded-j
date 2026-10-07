# Optics Chapter Guide

This guide holds the rules particular to the book's Optics chapter (`hkj-book/src/optics/`) and the
examples behind it. The [Style Guide](STYLE-GUIDE.md) applies here as everywhere, and this guide
links to it rather than repeating it. Follow both in every pull request that touches the chapter,
including a feature pull request that changes what an optic, a Focus path or an annotation does.

## Reading Lanes

The chapter's pages form three reading lanes, the Style Guide's groups. A change goes on the page
whose reader needs it.

| Lane | Pages | Reader and job |
|---|---|---|
| Ship | `ch_intro`, `quickstart`, `focus_dsl`, `focus_navigation`, `optics_intro`, `fluent_api`, `multi_edit`, `capstone`, `self_check` | Reads in order, then stops and updates nested records at work. |
| On demand | Seven groups, each led by a group introduction: The Optic Types (`ch1_intro`), Collections (`ch2_intro`), Precision and Filtering (`ch3_intro`), The Focus DSL in Depth (`ch4_intro`), Optics for External Types (`importing_optics`), Validation, Batching and Auditing (`ch5_intro`), Programs as Data (`ch6_intro`) | Reads one page when a task needs it. |
| Look it up | Look It Up (`ch7_intro`) and its pages: `production_readiness`, `annotations_at_a_glance`, `decision_trees`, `cookbook`, `optic_capabilities`, `conversions`, `composition_rules`, `focus_reference`, `compiler_errors` | Arrives holding a question or a compiler message. |

`SUMMARY.md` holds the order, and the chapter intro's Chapter Contents lists the lanes. A group's
pages are nested under its introduction in `SUMMARY.md`, so the sidebar shows the lane first.

- **No page changes its URL.** Readers and other chapters link to these pages, and mdbook URLs
  follow files rather than sidebar nesting, so a reorder moves nothing. Headings keep their ids as
  the Style Guide's [Anchors](STYLE-GUIDE.md#anchors) rules say. A heading removed from a page
  takes an entry in `hkj-book/theme/legacy-anchors.js` pointing at the section that now answers it.
- **A new page needs a reader no existing page serves.** It joins its lane or group in the intro's
  contents, takes its place in `SUMMARY.md`, and appears in the list of its group's introduction.
- **Previous and Next follow `SUMMARY.md`.** Every page, the group introductions included, links to
  the page before and after it in the sidebar's order. After reordering `SUMMARY.md`, regenerate
  them with `python3 .github/scripts/chapter_footers.py optics`, and check them with `--check`.

## Group Introductions

Each On demand group's introduction follows the Style Guide's [Group
Introductions](STYLE-GUIDE.md#group-introductions). Here its one list is titled "Pages in this
group", in `SUMMARY.md` order; it opens on one compiled example of the destination the group builds
to; and the tree it links is on [Decision Trees](../hkj-book/src/optics/decision_trees.md), which
keeps one copy of each. Optics for External Types has no introduction of its own: its parent,
`importing_optics`, is the page that teaches it.

## Reference Pages

The Look it up pages follow the Style Guide's [Reference Pages](STYLE-GUIDE.md#reference-pages)
shape: no epigraph, no "What You'll Learn" and no "Key Takeaways". Each opens with an italic
subtitle and then the thing its reader came for. A fact a reference page states only in a summary
moves into its body before the summary goes. Look It Up itself opens on a "You hold, go to" table
covering every page in the group.

## Examples

- **A page's compiled examples live in `hkj-examples`** under
  `org.higherkindedj.example.book.optics`. A new or rewritten page gets a `<Topic>Book.java` beside a
  `<Topic>BookTest.java` that holds the page's claims, as the chapter intro's `intro` package does.
  Older programs in `org.higherkindedj.example.optics` stay linked from "See Example Code" boxes.
- **A new or rewritten Ship page takes every runnable block as an include** from those files, as the
  Style Guide's [Java code in hkj-book must be verified](STYLE-GUIDE.md#java-code-in-hkj-book-must-be-verified)
  rule says. A shape that cannot run is a `verify` fence, and a refused one a `verify:rejects` fence.
- **An `// ANCHOR:` comment goes after the `package` line.** Spotless's licence-header step deletes
  anything above it.
- **A Ship page shows the Focus path first.** Its first library example of an operation uses the
  generated Focus path, with `generateNavigators = true`, as the Style Guide's
  [Effect Path First](STYLE-GUIDE.md#effect-path-first-in-examples) rule does for transformers. The
  raw optic (`XLenses.a().andThen(...)`) is collapsed, or comes after the path in a section that
  says why a reader would want it, as What a Path Is Made Of does.
- **`Kind`, witnesses and `widen`/`narrow` come after a call that needs none of them**, inside a
  collapsed block that names the mechanism. The Ship lane has one: `modifyF` on Updates That Can
  Fail.
- **The chapter's optic anchors live in one table.** "Choosing an optic" on What a Path Is Made Of
  gives one row per optic type, and the Focus DSL page anchors the Focus path itself. A page that
  introduces an optic links to that table rather than coining a metaphor of its own.
- **The chapter's running cast is the order service**, in `org.higherkindedj.example.book.optics.cast`:
  `Order`, `Customer`, `EmailAddress`, `LineItem`, `OrderStatus`, a sealed `Payment` (`Card`,
  `Bank`), `Address`, a `Consignment` with a sealed `ConsignmentState`, and `CustomerProfile`.
  `Order`, `Customer`, `EmailAddress` and `LineItem` have the Mapping capstone's shapes, `Payment`
  Structure's, `CustomerProfile` Absence's and `Address` Basics', so a reader moving between the
  chapters meets the same records. Names the Mapping chapter gives other shapes (`Shipment`,
  `Fulfilment`, `Checkout`, `Delivery`) stay out of this chapter. Each
  lane book imports the cast, unless it redeclares a member as a "before" half, a page includes the
  declarations it shows from the cast's files, and
  `CastFixtures` in the test tree holds the sample values. The Style Guide's
  [One Cast per Chapter](STYLE-GUIDE.md#one-cast-per-chapter) says when a page may add a type.
  - **A name keeps one shape across both chapters.** A field one page needs goes on a supporting
    type named for its role beside the cast, such as `Basket`, `Catalogue`, `PaymentHistory` or
    `PriceBand`, declared in that page's package.
  - **The "before" half of a before-and-after pair may redeclare a cast member** in its own package
    with the withers it calls, as Lombok's `@With` would generate them, but with the cast's
    components: the chapter introduction's `Order` and `LineItem`, and Many Edits at Once's
    `LineItem`, do.
  - **A width proof keeps placeholder components**, such as the nested-container records on
    Collections, Optionals and Sealed Types, with a comment saying so.
- **A chained Focus hop needs navigators.** `OrderFocus.customer().email()` compiles only when every
  record a hop leaves carries `@GenerateFocus(generateNavigators = true)`; without it, the hop is
  `.via(...)`.

## Checks

Run these before opening the pull request:

```bash
./gradlew :hkj-examples:test :hkj-examples:bookVerify    # the pages' tests, then the book gate
hkj-book/check.sh                                        # headings, links, diagrams, readability
python3 .github/scripts/chapter_footers.py optics --check  # Previous and Next match SUMMARY.md
hkj-book/serve.sh                                        # render with the pinned mdbook
```

`serve.sh` serves until you stop it. On a pull request into `main`, CI runs the same Gradle tasks.
The book checks run on any pull request that touches the book, so a pull request stacked on another
gets only those: run the Gradle tasks yourself. The readability counts are a ratchet, used as the
Style Guide's [Prose Limits](STYLE-GUIDE.md#prose-limits) section describes.

Claude Code sessions in this repository can load the `book-authoring` skill
(`.claude/skills/book-authoring/`), which carries the procedure and the traps. This guide stays the
source of truth for the rules.
