# Documentation Style Guide

This document defines the house style for Higher-Kinded-J documentation. Follow these guidelines to ensure consistency across all documentation pages.

## General Principles

- Use **British English** spelling and punctuation (e.g., "colour", "behaviour", "optimisation")
- Do not use em dashes (—); use commas or semicolons instead. The one sanctioned em dash is the attribution line under an epigraph ("— Niklaus Wirth")
- Avoid double-hyphen separators (` -- `) too, since they render as en-dashes and read like em-dashes. Prefer:
  - A **colon** (`: `) to introduce an expansion after a label (e.g. "`SafeFetch`: Total runner that captures..."). This is the standard form for label-and-description bullets and for "See Also" link descriptions
  - **Parentheses** for asides and dates after a heading (e.g. "`### v0.4.5 (22 May 2026)`", "`### v0.4.6-SNAPSHOT (unreleased)`"). Also for short parenthetical lists inside prose ("five sub-chapters (Quickstart, Core Paths, ..., Reference)")
  - A **full stop** when the dash is really separating two complete thoughts, and treating them as two sentences reads more naturally
- Do not use decorative emojis in documentation. The one exception is the status markers **✅** (correct, supported, "do this") and **❌** (wrong, unsupported, "avoid this"), used sparingly to label good-versus-bad code examples or supported-versus-unsupported rows in a table. Their plain-text equivalents **✓** and **✗** are equally acceptable. Do not use them as decoration or in prose; reserve them for a clear pass/fail signal.
- Keep explanations practical and focused on Java developers
- Avoid academic jargon; prefer accessible explanations
- Describe behaviour, not provenance: no GitHub issue or pull-request references outside the release history (see [No Issue or PR References](#no-issue-or-pr-references))

## Page Structure

### Title

Each page should start with a level-1 heading (`#`) that describes the topic.

**Type class pages** should use the pattern "TypeName: Brief Description":

```markdown
# Functor: The "Mappable" Type Class
# Monad: Composing Sequential Operations
# Applicative: Combining Independent Effects
```

**Monad/container pages** should use "TypeName: Description" or "TypeName" alone:

```markdown
# Maybe: Representing Optional Values
# Either: Modelling Success or Failure
```

### Opening Quote (Chapter Introductions)

Chapter introduction pages may include an opening quote to set the tone:

```markdown
# The Type Classes: Building Blocks of Abstraction

> _"Programs = Algorithms + Data Structures"_
> — Niklaus Wirth

This chapter introduces the foundational type classes...
```

Use blockquote formatting with italicised text and an attribution line.

### What You'll Learn Section

Every content page should have a "What You'll Learn" admonition near the top, immediately after the title or introductory paragraph:

```markdown
~~~admonish info title="What You'll Learn"
- Transform values inside a container without changing the container's structure
- Tell a functorial mapping from a plain function call, and say which one a signature offers
- Check the functor laws (identity and composition) against your own instance
~~~
```

Items are **outcomes, not topics**: each starts with a verb the reader performs (Declare, Predict,
Fix, Decide, Check), and names something they can do and then verify. Keep to five at most. On a
page that carries [checkpoints](#checkpoints), each item is tested by one.

### In This Chapter Section (Chapter Introductions Only)

Chapter introduction pages (`ch_intro.md`) should use an "In This Chapter" admonition with **expanded descriptions** (1-2 sentences per item) that provide more context than the Chapter Contents list below:

```markdown
~~~admonish info title="In This Chapter"
- **Functor**: The foundational type class that enables transformation of values inside containers without changing the container's structure. Every other abstraction builds on this.
- **Applicative**: When you have multiple independent computations and need to combine their results. Unlike Monad, Applicative allows parallel evaluation since results don't depend on each other.
~~~
```

Guidelines for In This Chapter:
- Each item should have 1-2 sentences explaining the concept's purpose and significance
- Provide context that helps readers understand *why* they would use each topic
- Do not simply duplicate the brief descriptions from Chapter Contents
- Use bold for the topic name followed by a colon (not an en-dash, which the dash rule above exists to avoid)
- A chapter serving distinct audiences may replace this list with a task-routed "Pick your route" list ("Your wire is a record: Basics, then Codecs"), so a reader is sent by their own question rather than by chapter order. Keep one such list per intro: three lists of the same pages is navigation, not orientation

### Chapter Contents Section (Chapter Introductions Only)

Every chapter introduction page (`ch_intro.md`) should include a "Chapter Contents" section that lists all pages in the chapter with links. This helps users on mobile devices or those who have not discovered the sidebar navigation.

```markdown
## Chapter Contents

1. [Page Title](page_file.md): Brief description of the page content
2. [Another Page](another_page.md): Another brief description
3. [Third Page](third_page.md): Third description

---

**Next:** [Page Title](page_file.md)
```

Guidelines for Chapter Contents:
- Use a numbered list matching the order in SUMMARY.md
- Each item should include the page title as a link and a brief description
- Descriptions should be concise (under 10 words), introduced by a colon
- Follow the list with a horizontal rule and a "Next" link to the first page
- A chapter over eight pages may group the list under **Ship** (the path a reader needs to ship), **On demand** (read when the boundary calls for it) and **Look it up** (reference), with the numbering running on across the groups. The grouping makes the reading lane visible without adding sub-chapter intro pages

### Example Code Section

If the page has example code in the repository, include an admonition linking to it:

```markdown
~~~admonish example title="See Example Code"
[FunctorExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/functor/FunctorExample.java)
~~~
```

### Section Separators

Use horizontal rules (`---`) to separate major sections for visual clarity.

### Key Takeaways Section

If the page includes a Key Takeaways or Summary section, format it as an info admonition:

```markdown
~~~admonish info title="Key Takeaways"
* **Point one** with explanation
* **Point two** with explanation
* **Point three** with explanation
~~~
```

### See Also Section

Use the "See Also" admonition for **internal** Higher-Kinded-J references:

```markdown
~~~admonish tip title="See Also"
- [Foldable and Traverse](foldable_and_traverse.md): See how Monoids power folding operations
- [Applicative](applicative.md): Learn how Semigroups enable error accumulation
~~~
```

### Related Types Section

When a type has close relationships with other types (e.g., `Maybe` vs `Optional`), use a note admonition:

```markdown
~~~admonish note title="Related Types"
- **`Optional<A>`**: Java's standard library optional with an HKT wrapper
- **`Either<L, A>`**: When you need an error value, not just absence
~~~
```

### Hands-On Learning Section

For pages with associated tutorials, link to them using an info admonition:

```markdown
~~~admonish info title="Hands-On Learning"
Practice Lens basics in [Tutorial 01: Lens Basics](../tutorials/optics/Tutorial01_LensBasics.java) (7 exercises, ~8 minutes).
~~~
```

### Further Reading Section

Content pages may include a "Further Reading" admonition at the **end** of the page, before the navigation links. This section should contain **external** references only.

**Important:** Do not add a separate markdown heading (`## Further Reading`) before the admonition. The admonition title serves as the heading.

```markdown
~~~admonish tip title="Further Reading"
- **Author/Source Name**, [Article Title](https://example.com/url): Brief description
~~~
```

Guidelines for Further Reading:
- **Prefer internal "See Also" links** over external "Further Reading" links when Higher-Kinded-J already covers the topic
- **Only add external links** if they offer unique value not already covered in Higher-Kinded-J documentation (e.g., foundational articles, unique perspectives, or comprehensive treatments of a topic)
- **Verify all links resolve correctly** before adding them - broken links (404 pages) significantly harm user experience and documentation credibility
- Prefer practical, developer-focused resources over academic papers
- Java-focused resources are preferred where available
- Avoid Vavr references as Higher-Kinded-J provides its own implementations

### Navigation Links

Every content page (except chapter introductions) should end with Previous/Next navigation links:

```markdown
---

**Previous:** [Page Title](page_file.md)
**Next:** [Page Title](page_file.md)
```

Notes:
- The first page in a chapter should only have a **Next** link
- The last page in a chapter should only have a **Previous** link
- Chapter introduction pages (`ch_intro.md`) should only have a **Next** link
- Exception: a chapter **nested inside another chapter's reading order** (per SUMMARY.md) links both ways at its boundaries, so its `ch_intro.md` carries a Previous link to the preceding sibling page and its last page a Next link onward, each reciprocated by the neighbouring page

## Content Patterns

### The 80/20 Page Shape

Order every content page so that the 80% use case is served before the 20% is even mentioned:

1. **The practical lane first**: the happy path, worked with real code and *visible results*, written so a reader can stop after it and ship
2. **The refinements**: the declarations and options most boundaries reach for
3. **The fine print last**: precise contracts, edge cases, and processor rules, gathered under a trailing `## The fine print` heading, never interleaved with the teaching flow. A lane admonition may carry one short caveat; it is not a place to put a rule set

A reader should never meet a corner case before the feature it is a corner of. Precision is not lost by this ordering; it is *findable* instead of ambient.

**Mark where the lane ends.** Close the practical lane with a `tip` titled "You can ship now". In one or two sentences, say what the reader can now build. End with one sentence saying the rest of the page is for when they need it. A framework integration keeps its own "At the Spring boundary" tip in the lane, and the page's checkpoints come straight after the ship tip.

**A practical lane must be reproducible.** For a feature that needs a build (generated code, a framework integration), the lane includes the build line and the integration call, so a reader can follow it from a blank project. The chapter introduction states hard prerequisites (JDK version, preview flags, processor path) in its first screen, because readers arrive from a search engine and never pass the home page.

**Where a rule goes, in one test.** What the compiler or processor *enforces* belongs in the fine print, or in the chapter's rules reference page where it has one: the diagnostic will find the reader anyway. What it *cannot* check is taught in the lane, as a warning with an example, because the reader's attention is the only safeguard. When a chapter has a rules reference page, that page is a rule's single home, and the teaching page keeps at most one sentence and a link.

**Page size.** A content page aims for 900 to 1,800 words of prose. Over 2,000, split it or demote its fine print. Keep any unbroken run of prose under about 400 words: headings do not break a run, but code, a table or a diagram does. Reference pages are exempt from all three.

### Show the Output Early

If a feature's payoff is an error message, a response payload, or a diagnostic, show that output verbatim at the first opportunity, ideally on the chapter's introduction page. Nothing motivates like the artefact itself: a JSON error response with located paths sells accumulating validation better than any paragraph describing it.

A code-comment output inside a compiled include (`// Invalid(NonEmptyList[email: ...])`) counts **only when a test asserts it**, or when a gate runs the example and compares its output. The build compiles an example; it does not run its `main`. The pattern to copy is the boundary capstone: the payoff lives in the example, and its test asserts the messages, in order.

**Format a payoff output for a phone.** Roughly 60 columns, one field per line. A response payload that runs off the side of a 400px screen hides the very thing it was shown for.

### Selling Points: the "Why this matters" Admonition

Where the library does something genuinely differentiating (law-checked guarantees, canonical-form strictness, located errors), say so explicitly in a `tip` admonition titled **"Why this matters"**, placed beside the strict rule or law it justifies:

```markdown
~~~admonish tip title="Why this matters"
Silent normalisation is data mutation nobody asked for. The strictness here is the
property that makes round trips provable, and it is law-checked in every build.
~~~
```

Guidelines: contrast with what the reader has debugged elsewhere (NPE stack traces, silently normalising mappers); state the guarantee and where it is verified; keep the tone confident and concrete, never marketing-brochure. One per page is usually right; they lose force in crowds.

### War Stories and Dialogues

Two narrative modes can make a hard rule stick: a war story and a Socratic dialogue. Use each only where it clearly works better than plain prose. Repeated, either reads as a formula and loses its force, so there is no quota: decide site by site, and say in the pull request why a mode was used, or why a candidate site went without.

**A war story** opens the section on a rule no compiler or processor checks:

- **Where.** At the head of that section, at most one per page. Never in fine print, on a reference page or on a rules page.
- **Shape.** One paragraph of at most about 150 words, in the present tense, framed as a composite ticket ("a ticket like this"), never a real incident. No named people, companies or dates; name a tool only where the failure is its own behaviour, and use only example-safe data.
- **Content.** It shows the silent failure, names the offending line, and ends on why the tests missed it. The fix follows as compiled code, then the visible warning.
- **It adds no fact**, so a reader who skips it loses nothing. The page refers back to it at least once.

**A Socratic dialogue** carries reasoning a reader must follow step by step, such as why a spec gets the methods it does:

- **Where.** At most one per chapter, opening the page whose reasons are its content.
- **Shape.** A blockquote of short turns between **You:** and whatever is being questioned, under about 200 words, handing over to the code, picture or table that shows what it argued.
- **It adds no fact** either: the page states each of its claims again, plainly.

### Signposting Integrations: the "At the Spring boundary" Admonition

Pages whose feature has a Spring (or other framework) integration signpost it in a `tip` admonition whose title begins **"At the Spring boundary"**. The consistent title makes the integration story scannable across the book: a reader wiring a controller can skim for the phrase.

### Documenting What Does Not Exist

An unsupported capability gets **one sentence** in prose saying it is "not supported yet": never a paragraph or an admonition labouring what cannot be done, and never a link to the issue tracking it:

```markdown
Mapping to a spreadsheet row is not supported yet.
```

The sentence states what the reader can and cannot do today. When the capability lands, the sentence goes. (This mirrors the "not supported yet" comment convention in code.)

Where the chapter has a limits or rules reference page, the same absence also takes **one row there**, so an evaluator can see the whole set in one place rather than discovering it a sentence at a time.

Outside that one sentence, rule prose carries no "for now", "yet", "today", "still" or "has always": they date the page and narrate delivery order, which the [no issue or PR references](#no-issue-or-pr-references) rule already rules out.

**One carve-out: a supported version.** A statement about the JDK, or about a dependency the library tracks, is a fact that legitimately dates, and writing it open-endedly ("Java 25 or later") claims a future nobody has tested. State the release the library is built on and mark it as current ("built on Java 25 today"), so the sentence is true when written and visibly needs revisiting when the library moves.

### No Issue or PR References

Book pages, skills, javadoc, code comments, diagnostics and test names describe behaviour and the reason for it, not where it came from. Do not link or cite GitHub issues or pull requests (no "([#654](...))", "the #653 doctrine" or "since #660"), and do not narrate delivery order ("the first slice", "a follow-up", "until the next release"). Name the concept instead: "the collision sweep", not "the #654 sweep".

The test for any reference is whether a reader a year or two from now would find it useful. An issue number almost never passes: it points at a planning discussion that closes, moves or goes stale, while the page it sits in is read long after.

The **release history** (`hkj-book/src/release-history.md`) is the one exception. There, a link from a version to the issue or pull request behind each change is exactly what a reader wants, so release entries keep them.

### House Terminology Budget

Coined terms (*leaf*, *wire*, *tier*, *leg*, *canon*) are good names, but every page must survive a reader who has not memorised them. On each page: define or link a coinage at first use (the glossary is the target), and prefer restating plainly over compounding coinages ("a wire `null` becomes a located error" rather than "the doctrine applies"). If a sentence needs three coinages to parse, rewrite it.

Four rules keep the budget honest:

- **Every coinage has a glossary entry**, in the glossary's House terms section, and each page links its first use. "Link a coinage" only works when there is something to link to.
- **One coinage, one meaning, book-wide.** Do not give a term a second sense on another page (a *leg* that is an HTTP route here and a branch of generated code there), and do not reuse a name the library already owns.
- **At most four new coinages per page.** Beyond that, link the rest rather than introducing them again.
- **The budget covers another chapter's vocabulary too**, not only this book's coinages: `asIso()`, `asLens()`, "section law", "tier arithmetic". A sentence in the practical lane may name one, with a one-clause plain gloss. Otherwise it belongs in the fine print.

### Java Idiom Anchors

Most readers arrive from everyday Java, not from this library. When a page introduces an abstraction or a house coinage, name the closest familiar Java idiom in one or two sentences, then say what the new shape adds. An anchor is a bridge, not a definition: it may be approximate, provided the "what it adds" sentence names the difference.

| Abstraction | Familiar Java idiom | What it adds |
|---|---|---|
| `Kind<F, A>` | `Stream<Order>`: a shape (Stream) holding an element type (Order) | lets a function abstract over the shape itself |
| `EitherPath<E, A>` | a method that throws a checked exception | the error is a typed value you can map, recover and accumulate |
| `Lens.modify` | a record "with" copy method | composes through nested records |
| a mapping spec | a MapStruct `@Mapper` interface | one declaration gives both directions, and a failed parse locates every bad field |
| a leaf (`ValidatedPrism`) | a Jackson serialiser and deserialiser pair for one field | a failed parse is a value carrying the field's name, not an exception |

Guidelines:
- Anchor at the concept's first appearance on the page, before its precise definition; later mentions link back rather than anchor again
- Prefer idioms from the JDK, Spring, Jackson, Bean Validation and MapStruct, which readers already use
- Say where the analogy breaks ("unlike a MapStruct mapper, the spec interface is never injected")
- A chapter with many coinages keeps its own anchor table in one place (its introduction, or its glossary entries) and links to it
- Tutorials keep the same rule in their class javadoc (`docs/TUTORIAL-STYLE-GUIDE.md`, "Java Idiom Anchors"); keep the two tables consistent

### Prose Limits

The guide's readability rules, stated so a review can check them:

- **Sentences.** In the practical lane, average under 20 words and never exceed 35; nowhere over 50. An inline code span counts as one word. A sentence over the limit becomes two sentences, a list, or a table row.
- **Clause load.** At most two subordinate clauses per sentence, and not both a colon and a semicolon. Never put a clause that carries its own subordinate clause inside a parenthesis, and never parenthesise a whole paragraph.
- **Bullets.** One rule per bullet, opening with the rule as a bold, complete sentence of 12 words or fewer. Teaching bullets stay under 40 words, fine-print bullets under 60. An exception gets its own bullet or row.
- **When prose becomes a table.** Three or more cases varying along the same dimensions (shape, outcome, reason, fix); a paragraph enumerating variants ("on a record ..., on a bean ..., on a sparse spec ..."); or any passage answering "which of these applies to me?". Use a numbered list where order matters.
- **One home per rule.** Other pages link to it in one clause and never restate it, because restatements drift apart.
- **Name the agent.** "The processor refuses this", "Declare the type arguments". A verdict with no actor ("is refused, as a bridged component is") reads as a specification, not as help.
- **State conditions positively**, and avoid stacked negation ("none of the members that nothing else fills").
- **No "above" or "below".** Name the destination and link it.
- **Fine print is still prose for people**: one-sentence reason, the fix as code or an imperative, and the diagnostic quoted rather than paraphrased.

The book's CI counts, per page, what these limits and the page-size and em-dash rules ask a reviewer to check: sentences over 35 and over 50 words, bullets over 60, prose runs over 400 words, dashes, and "above" or "below". It compares the counts with a committed baseline and reports any rise on the pull request; it does not fail the build. The counts are a ratchet, not a verdict: they cover every page, reference pages and fine print included, so a rise asks for a look rather than a rewrite. `node .github/scripts/book-readability-check.cjs --page <path under hkj-book/src>` lists what a page's counts are made of. `--update` lowers the baseline after a change improves a page, and never raises it; a count that has to rise takes `--accept <page>`, with the reason in the commit message.

### Checkpoints

A content page may carry checkpoints: a question the reader answers before reading on. They make a long page recoverable, and let an expert test out of a section instead of reading it.

````markdown
~~~admonish question title="Checkpoint: predict the errors" id="check-absence-invariant"
Which errors does `parse` report for this request, and in what order?
~~~

~~~admonish success title="Answer and why" collapsible=true id="check-absence-invariant-answer"
**Both, in declaration order.** ... Where this lives: [A record's own invariants](absence.md#constructor-invariants).
~~~
````

Rules:
- **The question is visible**, never collapsed, and carries the searchable nouns (annotation names, method names, message fragments)
- **The answer is a sibling** `success` admonition with `collapsible=true`. Admonitions do not nest, so the answer cannot live inside the question
- **Both carry an explicit `id=`**, so the anchor survives a checkpoint being added above it
- **An answer adds no fact.** It applies a rule the page already teaches visibly, and ends with a "Where this lives" link. Its only unique content is the proof
- **The answer is proved by the build**: a test assertion, or a `verify:rejects` snippet whose diagnostic is the answer. Never by an output comment nothing runs
- **At most two per page**, placed straight after the "You can ship now" tip, so passing one certifies that stopping there is safe
- **A chapter's self-check page is the exception**: it is all checkpoints, numbered in their titles ("Checkpoint 3: ...") so an answer can name another, and ordered from recall to writing code
- **Its questions interleave the pages** rather than follow them, and a question may withhold a noun that would name its answer
- **It closes the chapter's Ship group**, carries no "What You'll Learn", and ends with a routing key that turns a score into a next step

### Collapsing

Collapse reasoning, never the thing a reader is searching for. A rule statement, a diagnostic, a fix and a limit stay visible; the derivation behind them may fold into a collapsed "Why". Browser find-in-page opens a closed `<details>` in current versions, but not in older ones or in print, and the reader has to know the text is there to look for it.

The one exception is a [checkpoint](#checkpoints) answer, which is safe to collapse precisely because it states no fact of its own.

### Anchors

The book has no link checker, so a broken fragment fails silently. Three rules keep them working:

- **A heading linked from outside its page carries an explicit `{#id}`**, so its wording can change without breaking the link
- **A heading that moves to another page keeps its id**, and gains an entry in the legacy-anchor map so old links still land on it
- **A redirect target is document-relative** (`beans.html`), never an absolute versioned URL, or a reader of an older version is sent to the current one

A page redirect cannot rescue a *section* that moves: the redirect is a meta refresh, which drops the fragment. Prefer keeping the page and moving content within it; where a section must move, pin the id and add the legacy-anchor entry in the same change.

### Link Text

Link text names its destination. Never "below", "above" or "here", and never a bare adjective ("bridged", "refused"), which a screen reader's link list reads out with no context. "See [the sparse PATCH tier](...)", not "see [below](...)".

### Reference Pages

A lookup page (an at-a-glance page, a limits index, an error catalogue, a migration table) opens with the thing the reader came for: its triage table or its matrix. It carries no epigraph, no "What You'll Learn" and no "Key Takeaways", and links its example code in a foot line. The reader visits it holding a question and pays the preamble on every visit.

A catalogue of messages or limits takes this shape:

1. **A triage table first**, "Find your message" or "Find your limit": a short distinctive fragment, linked to its entry, beside what it means in under ten words. Readers arrive holding a compiler message or a question, not reading top to bottom
2. **Fix-first entries**: a one-sentence summary, then **Fix.**, then the reasoning folded into a collapsible "Why" where it runs past a couple of lines
3. **A severity line** where the page needs one: whether the thing stops the build, warns, or is silent until runtime
4. **One diagram** of which stage spoke (your declaration, the processor, the generated file, your call site)

Entries state their rule and fix in the open. Only the reasoning and the reproducer fold.

### Headings and Subtitles

Use a question heading where readers arrive holding that question: decision points, troubleshooting, and fine-print entries.

```markdown
## When should you use Monad vs Applicative?
## Why doesn't my bean mapping get `asIso()`?
## Which surfaces throw instead of locating?
```

In the practical lane, a heading names what is taught, or states a thesis, which is the stronger form where the page has a point to make ("Null has an address, not a stack trace", "Bind in the caller, not on the spec").

- Headings use **sentence case**, matching the treated pages (capstone pages keep their Title Case).
- **Before rewording a heading, pin its old anchor** with `{#id}`: see [Anchors](#anchors).
- A content page may carry **one italic subtitle** under its H1: a single sentence under 20 words, imperative or a plain promise, carrying no coinage the page has not glossed.

### Comparison Tables

When comparing related concepts, use markdown tables:

```markdown
| Aspect | Functor | Applicative | Monad |
|--------|---------|-------------|-------|
| Key method | `map` | `ap`, `map2` | `flatMap` |
| Combines | One effect | Independent effects | Sequential effects |
| Use case | Transform values | Parallel validation | Chained operations |
```

Tables work well for:
- Comparing type classes
- Summarising optic types
- Contrasting implementation approaches

### Problem-Solution Structure

When documenting patterns, techniques, or recipes, prefer a problem-solution structure that connects the technique to a real need:

```markdown
### Pattern Name

**The problem:** What challenge does this address? (1-2 sentences)

**The solution:** Code showing the pattern

**Why this works:** Brief rationale (optional, for complex patterns)
```

This structure:
- Engages readers by showing relevance before code
- Makes documentation scannable (readers can skip patterns that do not match their problem)
- Provides context that makes the code memorable

Example:

````markdown
### Fallback Chain

**The problem:** You have multiple sources for the same data, each with different trade-offs.
Try the preferred source first, then fall back to alternatives.

**The solution:**

```java
EitherPath<Error, Config> config =
    Path.either(loadFromFile())
        .recoverWith(e -> Path.either(loadFromEnv()))
        .recoverWith(e -> Path.right(Config.defaults()));
```

Each `recoverWith` only triggers if the previous step failed.
````

### Type Class/Monad Page Structure

Pages documenting type classes or monadic types should follow this structure:

1. **Title** (with descriptive subtitle)
2. **What You'll Learn** admonition
3. **Example Code** link (if applicable)
4. **Introductory paragraph** explaining the purpose
5. **Core concepts** (interface, operations, laws)
6. **Practical examples** (code with explanations)
7. **Common use cases** or "When to use"
8. **Comparison with related types** (if applicable)
9. **Summary or Key Takeaways**
10. **See Also** (internal links)
11. **Further Reading** (external links)
12. **Navigation** (Previous/Next)

## Diagrams

Two diagram media are in use, each with its ground:

- **Mermaid** (rendered by `mdbook-mermaid`) for *flows and decisions*: decision trees ("which tier does my spec get?"), boundary flows (request → parse → response), sequence-like mechanisms (error paths composing outward), and before/after contrasts. Prefer mermaid wherever the reader follows arrows to an outcome.
- **ASCII** for *type shapes and railways*: boxed type diagrams, the railway view, and signature asymmetries (`build : Domain ──▶ DTO`). Transformer pages keep their existing all-ASCII rule (see "Monad Transformer Pages").

Choosing between them, and between one diagram and two:

- Use a **`sequenceDiagram`** where a value travels through participants and comes back changed, such as an error gaining its path as it surfaces
- Use a **grid** (a table, or ASCII) when a decision turns on two independent axes. A flowchart imposes an order those axes do not have
- **A diagram that needs a paragraph to explain its branches is two diagrams.** So is one whose labels run to four lines

### Theme-Safe Mermaid Colours

The book renders in both light (Latte) and dark (Frappé/Macchiato/Mocha) themes, and mermaid renders **once at page load** (a theme switch between light and dark triggers a reload via `mermaid-init.js`). Every diagram must therefore be self-contained:

- Give **every node** an explicit `classDef` with both `fill` and `color` (text); never inherit the page background
- Use the house palette: mid-tone pastel fills with near-black text, readable on both light and dark page backgrounds:

```text
classDef wire fill:#8caaee,stroke:#1e66f5,color:#232634
classDef domain fill:#a6d189,stroke:#40a02b,color:#232634
classDef tier fill:#a6d189,stroke:#40a02b,color:#232634
classDef error fill:#e78284,stroke:#d20f39,color:#232634
classDef decision fill:#e5c890,stroke:#df8e1d,color:#232634
```

- Semantics stay consistent across the book: blue = wire/outside data or neutral process steps, green = domain/trusted/successful outcomes (including the outcome leaves of a decision flow), red = errors/rejections/failing steps, yellow = decision points
- Never encode meaning in colour alone; every node carries a label that works in monochrome
- Keep diagrams small (roughly 12 nodes or fewer); a diagram that needs scrolling should be two diagrams
- The explicit-`classDef` rule applies to flowcharts and state diagrams (states take the same palette via `classDef`/`class`). Sequence diagrams have no per-node `classDef`: their lines, lifelines and typed-participant icons are theme-managed by `mermaid-init.js`'s light/dark switch, which is already theme-safe; merge specific `themeVariables` only when a filled element needs the palette, and never override `theme` per diagram (a fixed theme would break whichever page background it was not designed for)

### Diagrams Everyone Can Read

- **Declare `accTitle` and `accDescr`** on every mermaid diagram. Without them a screen reader announces the node labels in document order and none of the arrows
- **Put the diagram's conclusion in a sentence** beside it ("In words: a bean whose properties are only read maps parse-only"). It serves screen-reader users, print, and anyone skimming
- **Keep the natural width at or below the 750px content column.** A wider diagram is scaled down to fit, so a 16px label can render at 4px on a phone. Prefer `TD` over `LR` for anything with long labels, and shorten labels before accepting the shrink
- **ASCII diagrams** use `<pre class="hkj-ascii-diagram" role="img" aria-label="...">`, whose label carries the same sentence. Box-drawing characters are read out glyph by glyph otherwise, so avoid long rules as separators

## Admonition Types

Use the following admonition types consistently:

| Type | Usage |
|------|-------|
| `info` | "What You'll Learn", "Key Takeaways", "Hands-On Learning", "In This Chapter" |
| `tip` | "Further Reading", "See Also", "Why this matters", "At the Spring boundary", "You can ship now" |
| `example` | Links to example code |
| `note` | Important clarifications, "Related Types", additional context |
| `warning` | Potential pitfalls or common mistakes |
| `question` | A [checkpoint](#checkpoints) question, never collapsed |
| `success` | A checkpoint answer, always `collapsible=true` |

## Code Formatting

### Java code in hkj-book must be verified

Hand-written Java in a book page drifts: nothing compiles it, so it rots silently and readers copy
code that does not build. Prefer, in this order:

1. **Include it from a compiled example.** Anchor the code in
   `hkj-examples/src/main/java/org/higherkindedj/example/book/**` and `{{#include}}` it. The page then
   renders code the build compiles *and runs*, so it cannot drift, and a runnable example also proves
   the output comments the page asserts. Put each output comment on a line of its own, after the
   statement whose value it shows, and print that same value: bind it to a variable in the region and
   print the variable after it. A comment at the end of a code line is not checked, and nor is a value
   the example computes again outside the region.
2. **Mark the fence `<!-- verify -->`.** The gate compiles a copy of it against the real library and
   the real annotation processor. Use this only when the snippet cannot be runnable code (a shape
   written against abstract type variables, for instance).
3. **Mark a refused shape `<!-- verify:rejects "quoted diagnostic" -->`**, or `verify:reports` where
   the code builds and the processor raises a note or a warning about it. A page that says "the
   processor rejects this" is making a claim, and this is what holds it to one; the quoted fragment
   is what rots when a diagnostic is reworded.
4. A plain fence, verified by nothing, is a last resort. Three ratchets fail the build if the number
   of includes, verified snippets or quoted diagnostics falls, so it cannot become the default by
   accident.

**A verified diagnostic must be visible.** The fragment quoted in a `verify:rejects` or
`verify:reports` marker lives in an HTML comment, which no reader can see, search or find with
Ctrl-F. Show the same message as visible text beside the block, and never collapse a block whose
purpose is to show a diagnostic. A page that says "the processor refuses this" is making a claim
the reader will one day paste into a search box; give them the words.

See `hkj-examples/BOOK-SNIPPETS.md`, and run `gradle :hkj-examples:bookVerify`.

### Code Blocks

Use triple backticks with language specifier:

````markdown
```java
// Java code here
```
````

### Inline Code

Use backticks for:
- Method names: `map`, `flatMap`, `of`
- Class names: `Functor`, `Monad`, `Kind<F, A>`
- Package names: `org.higherkindedj.hkt`

## Terminology

- Use "type class" (two words) not "typeclass"
- Use "Higher-Kinded-J" when referring to the library by name
- Use "higher-kinded types" or "HKT" when referring to the concept

## File Naming

- Use lowercase with underscores for markdown files: `monad_error.md`
- Match the SUMMARY.md structure for consistency

## Chapter Structure

Within a chapter, pages should follow this order:
1. Chapter introduction (`ch_intro.md`)
2. Core concepts in logical order
3. More advanced topics towards the end

### One Cast per Chapter

A chapter keeps **one running cast** of example types, so a reader's attention goes to the feature rather than to a new pair of records in every section. Introducing a new pair needs a reason: the feature needs a shape the cast does not have. A chapter whose examples accumulate one-off types (a new domain record per section) reads as a series of unrelated notes, and nothing accumulates towards its capstone.

## Problem-First Structure for Advanced Topics

Pages in advanced topic chapters should lead with a concrete problem before introducing the abstraction:

1. **Show the pain**: A short code snippet (5-15 lines) demonstrating the problem the reader faces without this tool
2. **Name the problem**: One or two sentences explaining why this hurts
3. **Show the solution**: The same scenario using the documented tool
4. **Explain**: How and why the solution works

This structure ensures readers understand *why* they need a concept before learning *how* it works. Reference material (type signatures, witness types, helper utilities) should still be present but positioned after the narrative arc, typically inside admonitions so the teaching flow is not interrupted.

## Monad Transformer Pages

Pages documenting individual monad transformers (e.g. `eithert_transformer.md`, `readert_transformer.md`) follow a stricter template than general advanced topics. The goal is to ensure every transformer page reads consistently and emphasises the Effect Path API as the recommended starting point.

### Required Page Structure

Each per-transformer page must use the following structure, in this order:

1. **Title** with descriptive subtitle and optional epigraph
2. **What You'll Learn** admonition
3. **See Example Code** admonition with link to the runnable example
4. **Path First, Stack Later** note that points readers at the equivalent Path type
5. **The Problem**: 5 to 15 lines of imperative code demonstrating the pain
6. **The Solution**: the same scenario expressed through the transformer, ideally with a `For` comprehension
7. **The Railway View**: ASCII diagram showing the success and failure tracks
8. **How It Works**: ASCII type diagram (no UML or generated SVGs) and short explanation of the type parameters
9. **Setting Up the Monad**: constructor for the `*Monad` instance, plus a Witness/Helper note
10. **Key Operations**: table or admonition listing the core methods
11. **Creating Instances**: tour of the static factory methods
12. **Real-World Example**: one substantial worked example using `For` comprehension
13. **Transforming the Outer Monad with `mapT`**: short section with a `mapT` example and a `mapT vs map` note
14. **Common Mistakes** warning admonition
15. **See Also** admonition with internal links
16. **Further Reading** (optional) with external links
17. **Navigation** (Previous/Next)

### Effect Path First in Examples

Code samples on transformer pages should follow this order of preference:

1. **First example**: show the problem solved with the corresponding Effect Path type (e.g. `EitherPath`, `MaybePath`). This emphasises that most readers do not need the raw transformer.
2. **Second example**: show the transformer-based solution using `For.from(...)` syntax to keep witness types localised.
3. **Reference material**: explicit `flatMap` chains and raw `Kind<>` ceremony belong in admonitions or later sections, not in the headline example.

The pattern is: *Effect Path first, transformer second, raw `Kind` only when illustrating the underlying mechanism.*

### `For` Comprehension Over Raw `flatMap`

Real-world examples must prefer `For.from(monad, ...)` over chained `flatMap` calls. The witness types are noisy when written inline; `For` keeps them at the top of the comprehension and lets the body read like a sequence of named bindings.

When a chained `flatMap` example is genuinely useful (e.g. to show the mechanism), keep it short and place it after the `For` example, not before.

### No UML or SVG Class Diagrams

Per-transformer pages should not embed UML or generated SVG class diagrams. They are difficult to maintain, add little to the reader's understanding, and do not match the lightweight, code-first style of the rest of the chapter. Use the boxed ASCII type diagram (under "How It Works") and the railway diagram (under "The Railway View") instead.

### Reducing `Kind` Ceremony

The single biggest barrier to reading transformer pages is the volume of `EITHER_T.widen(...)`, `OPTIONAL_T.narrow(...)`, and `Kind<XKind.Witness<F, ...>, A>` annotations. Apply the following rules:

- Use `var` for local variables whose type is obvious from context
- Push `widen`/`narrow` calls into a dedicated "Working with Kind" admonition rather than scattering them through the headline example
- Prefer `For.from(monad, ...)` so that `Kind<>` types appear at the comprehension boundary and not inside every step
- When a code sample demonstrates a single concept, omit unrelated ceremony

### Cross-References

Every transformer page should link to:

- The corresponding **Effect Path type** (e.g. `EitherT` links to `EitherPath`)
- The relevant **MTL capability** if one exists (e.g. `ReaderT` links to `MonadReader`)
- The **Stack Archetypes** page when an archetype matches the transformer's primary use case

## Checklist for New Pages

When creating a new documentation page, ensure:

- [ ] Title is clear and descriptive
- [ ] "What You'll Learn" admonition is present
- [ ] Example code links are included (if applicable)
- [ ] Sections are separated with horizontal rules
- [ ] "Key Takeaways" uses info admonition (if applicable)
- [ ] The practical lane ends at a "You can ship now" tip (content pages)
- [ ] "See Also" section for internal links (if applicable)
- [ ] "Further Reading" section with validated external links
- [ ] Previous/Next navigation links at the end
- [ ] British English spelling throughout
- [ ] No decorative emojis (the ✅/❌ status markers are allowed)
- [ ] No GitHub issue or pull-request references (the release history is the only exception)
- [ ] All code examples are properly formatted, and every Java block is included from a compiled example or carries a verify marker
- [ ] Prose limits met: no sentence over 35 words in the lane, one rule per bullet, rules with three or more cases as tables
- [ ] Fine print is in a trailing section, not interleaved; enforced rules live in the chapter's rules page where it has one
- [ ] Coinages glossed or linked at first use, and a Java idiom anchor where a new abstraction appears
- [ ] Diagrams declare `accTitle`/`accDescr` and are summarised in a sentence
- [ ] Headings linked from elsewhere carry an explicit `{#id}`
- [ ] Any quoted diagnostic is visible, not only inside a verify marker

## Checklist for Chapter Introductions

When creating a chapter introduction page (`ch_intro.md`), ensure:

- [ ] Title reflects the chapter theme
- [ ] Opening quote and introductory prose
- [ ] "In This Chapter" admonition with **expanded descriptions** (1-2 sentences per item, not just brief phrases)
- [ ] "Chapter Contents" section with numbered links and brief descriptions
- [ ] "In This Chapter" and "Chapter Contents" are distinct (expanded context vs. brief navigation)
- [ ] **Next** link to the first page in the chapter (no Previous link, unless the chapter is nested inside another chapter's reading order; see Navigation Links)
- [ ] Hard prerequisites (JDK version, preview flags, processor path) stated in the first screen
- [ ] British English spelling throughout
- [ ] No decorative emojis (the ✅/❌ status markers are allowed)
