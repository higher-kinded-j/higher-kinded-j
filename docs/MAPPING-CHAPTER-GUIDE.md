# Mapping Chapter Guide

This guide holds the rules particular to the book's "Mapping at the Boundary" chapter
(`hkj-book/src/mapping/`) and the examples behind it. The [Style Guide](STYLE-GUIDE.md) applies here
as everywhere, and this guide links to it rather than repeating it. Follow both in every pull
request that touches the chapter, including a feature pull request that adds a rule, a refusal or a
diagnostic the chapter documents.

## Reading Lanes

The chapter's pages form three reading lanes, the Style Guide's groups. A change goes on the page
whose reader needs it.

| Lane | Pages | Reader and job |
|---|---|---|
| Ship | `ch_intro`, `quickstart`, `basics`, `codecs`, `absence`, `structure`, `capstone`, `self_check` | Reads in order, then stops and ships a boundary. |
| On demand | `tiers`, `beans`, `beans_patch`, `generics`, `merge_envelopes`, `testing`, `estate` | Reads one page when their boundary needs it. |
| Look it up | `at_a_glance`, `from_mapstruct`, `rules`, `compiler_errors` | Arrives holding a question or a compiler message. |

In this guide, a **teaching page** is a Ship or On demand page other than the intro, Quickstart, the
two capstones and Check Your Understanding. The Look it up pages are reference pages.

- **No page changes its URL.** Readers and other chapters link to these pages. Headings keep their
  ids as the Style Guide's [Anchors](STYLE-GUIDE.md#anchors) rules say.
- **A new page needs a reader no existing page serves.** It joins its lane in the chapter intro's
  contents, and takes its place in `SUMMARY.md`.

## Where a Rule Lives

Every rule has one home. A teaching page keeps at most one sentence and a link for a rule that lives
elsewhere.

| The rule is | Its home |
|---|---|
| Enforced by the processor | One heading on [Rules and Limits](../hkj-book/src/mapping/rules.md), and a row in its "Find your limit" table. A rule short enough to state in one sentence may stay on its teaching page instead; its "Find your limit" row still links to it. |
| Something the processor cannot check | The teaching page, as a warning titled "Not checked for you: …" with an example the build proves, and a row in Rules and Limits' "Find your symptom" table linking to it. |
| A capability that does not exist, and that the processor does not diagnose | One sentence on the teaching page, and a "Find your limit" row whose status is *not supported yet*, as [Documenting What Does Not Exist](STYLE-GUIDE.md#documenting-what-does-not-exist) says. A shape the processor refuses as not supported yet is an enforced rule: it takes the first row, with that status. |

**A refusal a reader is likely to meet** also gets an entry on [Compiler
Messages](../hkj-book/src/mapping/compiler_errors.md), added through the [Compiler Messages
generator](../hkj-book/tools/compiler-messages/README.md). The page is generated, so never edit it
by hand. A refusal qualifies when a plausible first declaration reaches it: a typo, a MapStruct or
Jackson habit, or an everyday type such as a primitive, an `Optional`, a `List`, a `Map` or a bean.
One that only a combination of advanced features reaches may go without. When unsure, add it.

A feature pull request places its rules and refusals this way, in that pull request. It also checks
the other places that describe what the mapper does: Mapper at a Glance's tables, Coming from
MapStruct, and the `hkj-mapping` skill in `.claude/skills/`, which ships to users.

## Page Shape

- **Shape and size** follow the Style Guide's [80/20 Page
  Shape](STYLE-GUIDE.md#the-8020-page-shape). On a teaching page, the "You can ship now" tip marks
  where the practical lane ends.
- **Checkpoints** follow the Style Guide's [Checkpoints](STYLE-GUIDE.md#checkpoints) rules. Each
  teaching page carries two. Each capstone carries one, Check Your Understanding ten, and the intro
  and Quickstart none. Mapper at a Glance's twelve-question fit test is a self-assessment, not a
  checkpoint.
- **Writing modes** follow [War Stories and Dialogues](STYLE-GUIDE.md#war-stories-and-dialogues).
  The chapter's one dialogue opens What Your Spec Generates, and a war story opens Sparse PATCH's
  section on why a PATCH getter must answer `null`. A second dialogue would break the Style Guide's
  limit.

## Examples

- **The chapter's examples share one package**, `org.higherkindedj.example.book.mapping` in
  `hkj-examples`, rather than one package per page, because they share one cast. A new top-level
  type must therefore not reuse a name already in the package. The boundary capstone has a package
  of its own, and the estate capstone's code is three Gradle modules of its own,
  `hkj-examples/estate-api`, `estate-clients` and `estate-service`, proved by `EstateBoundaryTest`,
  because the module boundary is its subject. A page's "See Example Code" box names its own example
  files, and a test beside each proves what the page claims. Basics, the Quickstart and the testing
  page also include from the Spring example app, `hkj-spring/example`. A new page in the shared
  package gets its own `<Topic>Book.java` and `<Topic>BookTest.java`.
- **On a teaching page, every runnable block is an include** from those files. A refused shape is a `verify:rejects`
  fence, and a shape that cannot run is a `verify` fence, as the Style Guide's [Java code in
  hkj-book must be verified](STYLE-GUIDE.md#java-code-in-hkj-book-must-be-verified) rule says.
- **The chapter's running cast is the order service**: `Customer` (with a checked `EmailAddress`),
  `Address`, `Order`, `LineItem`, a sealed `Payment` and an `OrderStatus` enum. The Style Guide's
  [One Cast per Chapter](STYLE-GUIDE.md#one-cast-per-chapter) says when a page may add a type; these
  rules say how, in this chapter's one package.
  - **A name keeps one shape across the chapter.** Never grow a cast member for a later page: a
    field one page needs goes on a supporting type named for its role, such as a `Courier` or a
    `Recipient`.
  - **A new wire for a cast member is named for its view**, such as `PartnerCustomerDto` or
    `CustomerView`. Basics' rename maps `Customer`'s `name` to a partner feed's `fullName` this way.
  - **Give each domain and wire pair one spec.** A nested component resolves through the only spec
    for its pair, and the processor refuses a second. A feature that needs a spec of its own, such
    as the constant trap, takes a new wire or a supporting type.
  - **A fence or a fixture declares a package name with the package's components**: the same names,
    types and order. The gate compiles each marked fence on its own, beside only its page's
    fixture, so nothing else holds it to the package. A fence that needs another shape takes
    another name.
  - **Three kinds of fence may reshape a package name.** A `verify:rejects` or `verify:reports`
    reproducer may change the one thing its diagnostic is about. The "before" half of a
    before-and-after pair shows the shape it replaces. The intro's hand-written mapper takes the
    boundary capstone's shapes.
  - **Reference pages keep their own reproducers.** Compiler Messages and Rules and Limits name
    each reproducer for its refusal, and code included from another module, such as the Spring
    example app's `User` or a processor golden file, keeps its own names.
  - **A checkpoint's fresh instance is a fresh case, not a fresh type**: it may reuse the page's
    cast with new values.
  - **The boundary capstone's package holds a larger cast of its own.** Its `Order` carries a
    customer, lines, a currency and a status, and its `CustomerDto` names the wire's `fullName`. The
    main package's `Order` is a smaller record for the Codecs page.
  - **The estate capstone's modules hold a larger `Customer` of their own**, with an id, a nickname
    and an address beside the name and the checked email, because a service keeps its customer at
    that size. Its specs, `AddressMapping` among them, map that module's pairs, and its client beans
    keep the names a partner's jar would give them.

## Checks

Run these before opening the pull request:

```bash
./gradlew :hkj-examples:test :hkj-examples:bookVerify   # the pages' tests, then the book gate
hkj-book/check.sh                                       # headings, links, diagrams, readability
hkj-book/serve.sh                                       # render with the pinned mdbook
```

`serve.sh` serves until you stop it, and its first run installs the pinned mdbook with `cargo`.

On a pull request into `main`, CI runs the same Gradle tasks. The book checks run on any pull
request that touches the book, so a pull request stacked on another gets only those: run the Gradle
tasks yourself. The readability counts are a ratchet, used as the Style Guide's [Prose
Limits](STYLE-GUIDE.md#prose-limits) section describes.

Claude Code sessions in this repository can load the `book-authoring` skill
(`.claude/skills/book-authoring/`), which carries the procedure and the traps. This guide stays the
source of truth for the rules.
