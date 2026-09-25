# Mapping Chapter Guide

The rules for changing the book's "Mapping at the Boundary" chapter (`hkj-book/src/mapping/`) and
the examples behind it. The [Style Guide](STYLE-GUIDE.md) applies here as everywhere. This guide
adds what is particular to this chapter: where a page sits, where a rule lives, and how a claim is
proved. Follow it in every pull request that touches the chapter, including a feature pull request
that only adds a rule or a refusal.

## The three lanes

The chapter serves three readers, so its pages form three lanes. A change goes on the page whose
reader needs it.

| Lane | Pages | Reader and job |
|---|---|---|
| Ship | `ch_intro`, `quickstart`, `basics`, `codecs`, `absence`, `structure`, `capstone`, `self_check` | Reads in order, then stops and ships a boundary. |
| On demand | `tiers`, `beans`, `beans_patch`, `generics`, `merge_envelopes`, `testing` | Reads one page when their boundary needs it. |
| Look it up | `at_a_glance`, `from_mapstruct`, `rules`, `compiler_errors` | Arrives holding a question or a compiler message. |

- **No page changes its URL.** Readers and other chapters link to these pages.
- **No heading loses its id.** Pin an explicit `{#id}` before rewording a heading that anything
  links to. A section that moves to another page keeps its id, and gets an entry in
  `hkj-book/theme/legacy-anchors.js` so old deep links still land on it.
- **A new page needs a reason** that no existing page's reader already covers. It joins a lane in
  `SUMMARY.md` and in the chapter intro's contents.

## Where a rule lives

Every rule has one home. A teaching page keeps at most one sentence and a link for a rule that
lives elsewhere.

| The rule is | Its home |
|---|---|
| Enforced by the processor | One heading on [Rules and Limits](../hkj-book/src/mapping/rules.md), plus a row in its "Find your limit" or "Find your symptom" table. |
| A refusal readers commonly meet | An entry on [Compiler Messages](../hkj-book/src/mapping/compiler_errors.md), added through [its generator](../hkj-book/tools/compiler-messages/README.md). The page itself is generated: never edit it by hand. |
| Something the processor cannot check | The lane page that teaches the feature, as a warning titled "Not checked for you: …", with an example the build proves. |
| A capability that does not exist | "Not supported yet", in the same words the processor's own diagnostic uses. |

A feature pull request follows the same table. Its enforced rules go to Rules and Limits, and its
common refusals to Compiler Messages, in that pull request. A new rule never grows a teaching
page's fine print.

## Page shape

- **A lane page teaches the common path first.** A "You can ship now" tip marks where a reader who
  arrived from a search can stop. Everything they need to ship comes before it, and refinements come
  after.
- **Size** follows the Style Guide: 900 to 1,800 words of prose on a teaching page, and a split over
  2,000. Reference pages are exempt.
- **Each lane page from Basics on carries two checkpoints.** The capstone carries one, Check Your
  Understanding ten, and the intro and Quickstart none. Each asks about a fresh instance that probes
  a misconception. A checkpoint that recombines the example above it tests recall, not transfer. One
  checkpoint may test the page's "Not checked for you" rule. Every answer is asserted by the page's
  test.
- **Writing modes** are rationed. The chapter's one dialogue opens What Your Spec Generates, and a
  war story opens Sparse PATCH's section on why a PATCH getter must answer `null`. Do not add a
  second dialogue. Add a war story only where the Style Guide's rule says it beats plain prose.

## Examples and proof

- **Each page's code lives in one example file.** It is
  `hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/<Page>Book.java`, with a
  `<Page>BookTest.java` beside it in the test tree. The page includes anchored regions from both, so
  every block it shows is compiled, and the examples are run.
- **An output comment is a claim** only in a shape the output gate recognises: a scalar, or a value
  printed as `[...]` or `Capitalised(...)`. Put it on its own line after the statement that binds
  the value, and print that value.
- **A claim about another library is proved with that library.** What Jackson binds, rejects or
  truncates is asserted in the page's test through a real `JsonMapper`, not stated from memory.
- **The chapter's cast is the order service**: `Customer`, `Address`, `Order`, `LineItem`, a sealed
  `Payment` and one `OrderStatus`. A new example uses it wherever the feature fits.

## Checks

Run these before opening the pull request. CI runs the gate and the anchor check, and reports the
readability counts.

```bash
./gradlew :hkj-examples:test :hkj-examples:bookVerify   # examples, snippets, includes, output claims
node .github/scripts/book-anchor-check.cjs              # every link and fragment resolves
node .github/scripts/book-readability-check.cjs --page hkj-book/src/mapping/<page>.md
node .github/scripts/book-readability-check.cjs --update   # after a change that improves a page
hkj-book/serve.sh                                       # render with the pinned mdbook
```

`--update` only lowers a page's ceilings. If a count has to rise, raise it with `--accept <page>`
and give the reason in the commit message. The pull request quotes the counts it lowered.

Claude Code sessions in this repository load the `book-authoring` skill
(`.claude/skills/book-authoring/`) for the same workflow. This guide stays the source of truth.
