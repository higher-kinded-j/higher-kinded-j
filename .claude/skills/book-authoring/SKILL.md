---
name: book-authoring
description: "Contributor workflow for Higher-Kinded-J's own mdbook. Use when writing or editing any page under hkj-book/src, the compiled examples behind the book (hkj-examples/src/main/java/org/higherkindedj/example/book and their tests), a checkpoint, a mermaid diagram, a heading or anchor, the readability baseline, or when a feature pull request changes what a chapter documents (a new rule, refusal or diagnostic). Loads chapter-specific rules from reference/<chapter>.md. For contributors to this repository, not for users of the library."
---

# Writing the Higher-Kinded-J Book

The rules live in committed documents. This skill is the procedure and the traps, and it points
at those documents rather than restating them. Read the parts that apply before writing:

- `docs/STYLE-GUIDE.md`: prose limits, page shape, checkpoints, diagrams, anchors, link text, and
  the war story and dialogue modes.
- The chapter's own guide, if the style guide's "Chapter Guides" table lists one.
- `hkj-examples/BOOK-SNIPPETS.md`: how Java on a page is compiled, and the ratchets that hold it.
- `docs/TUTORIAL-STYLE-GUIDE.md`, for a tutorial.

## Chapter references

| Editing | Also read |
|---|---|
| `hkj-book/src/mapping/**`, or a processor feature the Mapping chapter documents | `reference/mapping.md` |

A chapter with no row has no rules beyond the style guide. When a chapter gains a guide, add its
row here and a `reference/<chapter>.md` beside this file.

## Workflow

1. **Branch from a fresh `origin/main`.** A local `main` goes stale, and a branch cut from it
   silently lacks tooling that has since merged.
2. **Decide where each change goes** before writing it: which page's reader needs it, and where each
   rule lives. The chapter guide says both.
3. **Write the code first**, in the page's example file under `hkj-examples`, and include its
   anchored regions. An output comment sits on its own line after the statement that binds the
   value, in a shape the output gate recognises. The test asserts every checkpoint answer. A claim
   about another library, such as what Jackson binds, is asserted with that library.
4. **Write the prose to the style guide.** Mark a refused shape `<!-- verify:rejects "fragment" -->`,
   and keep that fragment visible on the page, because a gate checks that the reader can see it.
5. **Draw a diagram only where the mechanism has a shape.** Give it `accTitle` and `accDescr`, use
   the theme-safe palette, and fit it to the page width.
6. **Run the checks**, then lower the readability ceilings the change earned:

   ```bash
   .claude/skills/book-authoring/scripts/check-book.sh
   ./gradlew :hkj-examples:test :hkj-examples:bookVerify
   node .github/scripts/book-readability-check.cjs --update
   ```

7. **Review the real diff before the pull request.** Use independent lenses: accuracy, which probes
   each claim against the real processor or library; style, against the guide; a first reader; and
   code, for the example Java. Check every finding against the code before acting on it, and say in
   the pull request which findings were declined and why.
8. **In the pull request**, quote the readability counts that fell, and say whether a war story or
   dialogue was used and why.

## Traps

- **A gate that reports zero is checking nothing.** Read its counts. The output gate names each
  example with the number of output comments it checked.
- **The snippet gate normalises what it compiles.** It drops a top-level access modifier, for
  example. When a probe and the gate disagree, suspect the harness.
- **mdbook heading ids drop punctuation.** A colon or a full stop vanishes rather than becoming a
  hyphen. Compute an id with the anchor checker's `slug()`, never by hand.
- **The anchor checker knows heading ids only.** An admonition's `id=` is not a link target, so
  link the heading around it instead.
- **A class-initialisation trap needs a fresh class loader to test.** Static initialisation is
  global to the JVM, so an earlier test can hide the trap. The mapping tests' `FreshPackage` helper
  loads a package afresh.
- **`hkj-core` can fail `-Werror` after a javadoc-only edit** with "Implicitly compiled files were not
  subject to annotation processing". Clean the module and rebuild.
- **Never run two Gradle builds at once**, and do not push while one runs: the pre-push hook runs a
  repository-wide `spotlessCheck`.
- **Render with `hkj-book/serve.sh`.** It uses the pinned mdbook, and a global install is the wrong
  version.
- **Book text carries no issue or pull request numbers.** The style guide explains why.
