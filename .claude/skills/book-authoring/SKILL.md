---
name: book-authoring
description: "Contributor procedure and traps for Higher-Kinded-J's own mdbook and its documentation. Use when writing, editing or documenting anything in the book: a page under hkj-book/src, the compiled examples behind it (hkj-examples/src/main/java/org/higherkindedj/example/book and their tests), a checkpoint, a mermaid diagram, a heading or anchor, the readability baseline, or a chapter's Rules and Limits or Compiler Messages page. Also use when a feature pull request changes what a chapter documents, such as adding or rewording a @GenerateMapping rule, refusal or diagnostic in hkj-processor. Chapter-specific procedure is in reference/<chapter>.md. For contributors to this repository, not for users of the library."
---

# Writing the Higher-Kinded-J Book

The rules live in committed documents, and this skill points at them rather than restating them. It
adds the procedure and the traps. Read the parts that apply before writing:

- `docs/STYLE-GUIDE.md`: prose limits, page shape, checkpoints, diagrams, anchors, link text, the
  war story and dialogue modes, and how the book's Java is verified.
- The chapter's own guide, if the style guide's "Chapter Guides" table lists one for the directory
  you are editing.
- `hkj-examples/BOOK-SNIPPETS.md`: how the gate compiles the book's Java, and the ratchets that hold
  it.
- `docs/TUTORIAL-STYLE-GUIDE.md`, for a tutorial.

The `hkj-*` skills beside this one are written for users of the library, and they can lag the book.
Take a fact from one only after checking it against the processor or the library.

## When to load supporting files

- Editing `hkj-book/src/mapping/`, or a processor feature the Mapping chapter documents: load
  `reference/mapping.md`.

A chapter not listed here has no extra procedure, and its rules are in its chapter guide if it has
one. When a chapter gains a guide, add its line here and its `reference/<chapter>.md`, as the style
guide's "Chapter Guides" section says.

## Workflow

1. **Start from the right branch.** New book work branches from a freshly fetched `origin/main`,
   because a local `main` goes stale and lacks tooling that has since merged. Documenting a feature
   stays on that feature's branch, since the book must be generated against the new processor.
   Rebase it onto fresh `origin/main` if the book tooling it needs is missing.
2. **Decide where each change goes** before writing it: which page's reader needs it, and where each
   rule lives. A chapter guide says both; without one, follow the style guide.
3. **Write the code first**, in the page's example files under `hkj-examples`, and include their
   anchored regions. The style guide's "Java code in hkj-book must be verified" section says how an
   output comment and a claim about another library are proved, and its Checkpoints section how a
   checkpoint's answer is.
4. **Write the prose to the style guide.** A refused shape carries a `verify:rejects` marker, and
   the quoted fragment also appears as visible text, because a gate checks that the reader can see
   it.
5. **Draw a diagram only where the mechanism has a shape**, as the style guide's Diagrams section
   says.
6. **Run the checks**, then lower the readability ceilings the change earned:

   ```bash
   ./gradlew :hkj-examples:test :hkj-examples:bookVerify
   hkj-book/check.sh
   node .github/scripts/book-readability-check.cjs --update
   ```

7. **Render the pages you changed** with `hkj-book/serve.sh`, run in the background, since it serves
   until stopped. Rendering is the only way to see a diagram's width or an admonition's layout.
8. **Review the real diff before the pull request.** Use independent lenses: accuracy, which probes
   each claim against the real processor or library; style, against the guides; a first reader; and
   code, for the example Java. Check every finding against the code before acting on it. The pull
   request says which findings were declined, and why.
9. **In the pull request**, fill in the template's "Book changes" section.

## Traps

- **A gate that reports zero is checking nothing.** Read its counts. The output gate names each
  example with the number of output comments it checked.
- **The snippet gate normalises what it compiles.** It drops a top-level access modifier, for
  example. When a probe and the gate disagree, suspect the harness.
- **mdbook heading ids drop punctuation.** A colon or a full stop vanishes rather than becoming a
  hyphen, so an id computed by hand is often wrong. Pin an explicit `{#id}` on the heading, then run
  the anchor check.
- **The anchor check accepts heading ids only.** mdbook renders an admonition's `id=`, but the check
  does not accept a link to it, so link the heading around the admonition.
- **A module with annotation processors can fail `-Werror` after an incremental compile**, with
  "Implicitly compiled files were not subject to annotation processing". It happens in `hkj-core`
  after a javadoc-only edit, and in `hkj-processor` after a single-file edit. Clean the module and
  rebuild.
- **Never run two Gradle builds at once.** If you installed Spotless's pre-push hook (`./gradlew
  spotlessInstallGitPrePushHook`), a push also runs a Gradle build, so do not push while another
  build runs.
- **Render with `hkj-book/serve.sh`.** It uses the pinned mdbook, and a global install is the wrong
  version.
- **A contributor skill must not be named `hkj-*`.** The build plugins ship every `hkj-*` skill to
  users.
