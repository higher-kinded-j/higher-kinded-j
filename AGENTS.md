# AGENTS.md

This file is for AI coding agents working in this repository. It points to what
[CONTRIBUTING.md](CONTRIBUTING.md) asks, and adds what the build and CI check. Where the two
overlap, CONTRIBUTING.md wins.

Higher-Kinded-J brings higher-kinded types, composable effects (the Effect Path API), optics with
generated code, and compile-time DTO mapping to Java 25. [The book](https://higher-kinded-j.github.io/latest/home.html)
documents the library for its users; [CONTRIBUTING.md](CONTRIBUTING.md#project-modules) lists the modules.

## Before you start

- Take an issue only if it is unassigned or assigned to you. If someone else has it, choose another.
- Do what the issue asks, and no more. Report anything else you find in the pull request description.
- Before writing a class, method or test, open its nearest sibling, or the model the issue names,
  and follow its shape: see [Matching the Code Around It](CONTRIBUTING.md#matching-the-code-around-it).

## Setup and commands

The build compiles and tests on JDK 25, because it uses preview features. Gradle itself runs on
JDK 17 or later, and downloads a JDK 25 toolchain when none is installed, which needs network access.

```bash
./gradlew :hkj-core:test --tests "EitherPathTest"   # one test class
./gradlew :hkj-core:test                            # one module
./gradlew build javadoc                             # what CI runs: the tests, coverage minimums, Spotless and the Javadoc
./gradlew spotlessApply                             # format the Java
node .github/scripts/british-spelling-check.cjs     # British spelling in prose, as CI checks it
```

For a change to the book, or to a rule, refusal or diagnostic the book documents, also run:

```bash
./gradlew :hkj-examples:test :hkj-examples:bookVerify
hkj-book/check.sh                                   # or hkj-book/docker.sh check, which needs no Node.js
```

If a check fails, run the same command on `main`, for example in a second worktree made with
`git worktree add ../hkj-main origin/main`. If it fails there too, your change did not cause it:
name the check and its message in the pull request description.

## Skills

The skills under `.claude/skills/` hold procedure and API detail that would otherwise take a long
search to rediscover. Claude Code loads them by itself. Other agents should open the matching
`.claude/skills/<name>/SKILL.md`, and the reference files it names, before starting.

| The task touches | Skill |
|---|---|
| A page under `hkj-book/src`, a glossary entry, a book example under `hkj-examples`, a mermaid diagram, the readability baseline, or a rule, refusal or diagnostic the book documents | `book-authoring`, then its `reference/<chapter>.md` where the chapter has one |
| Tests that use hkj-test assertions or law harnesses | `hkj-test` |
| Effect Paths and choosing one, `NonEmptyList`, `EitherOrBoth`, retry and other resilience | `hkj-guide` |
| Optics, the Focus DSL and the optics annotations | `hkj-optics` |
| Optics meeting Effect Paths: `focus`, `ValidatedPrism`, `Edits` | `hkj-bridge` |
| `@GenerateMapping`, `@GenerateAssembly`, `@GenerateMerge` | `hkj-mapping` |
| `@EffectAlgebra`, Free programs and their interpreters | `hkj-effects` |
| The Spring Boot starter | `hkj-spring` |
| Pure core and effectful shell, `TimeSource` and testing time | `hkj-arch` |

The `hkj-*` skills ship to users and repeat parts of the book, so a change to what the book says
about an API changes them too: see [The Claude Code skills](CONTRIBUTING.md#the-claude-code-skills).

## What the build and CI check

- **Formatting.** Spotless formats the Java to Google Java Style, and `./gradlew build` fails on a
  file it would change.
- **Warnings are errors.** javac runs with `-Xlint:unchecked,rawtypes -Werror`, and the project's own
  checker, hkj-checker, runs over the library too. A deliberate exception is a `@SuppressWarnings`
  on the narrowest declaration that needs it, with a short comment saying why.
- **Architecture rules.** hkj-core, hkj-processor, hkj-checker, hkj-test and hkj-spring/autoconfigure
  each keep ArchUnit rules under `src/test/java/org/higherkindedj/architecture/`. When one fails,
  change the code to satisfy it. Do not edit or relax the rule.
- **Coverage.** Codecov fails a pull request unless a test covers every line it adds or changes, in
  every module but `hkj-examples` and `hkj-benchmarks`. `hkj-test`, `hkj-processor` and
  `hkj-processor-plugins` also have minimums in their build files, which fail the build.
  `./gradlew :hkj-core:test` writes hkj-core's report to
  `hkj-core/build/reports/jacoco/test/html/index.html`.
- **Generated code.** CI compares what hkj-processor generates with its golden files, through
  `./gradlew :hkj-processor:verifyGoldenFiles`. When a change alters that output on purpose, run
  `./gradlew :hkj-processor:updateGoldenFiles` and commit the regenerated files.
- **British spelling** in all prose: Markdown, Javadoc and comments, and test display names. Code
  keeps its own spelling.

## Code and tests

- Follow [Matching the Code Around It](CONTRIBUTING.md#matching-the-code-around-it): Javadoc on
  public API, imports rather than fully qualified names, package-private test classes with a
  class-level `@DisplayName`, and no test that repeats one already there.
- A type class instance is tested through `TypeClassContract` and hkj-test's `*Laws`, as
  "Reusable Law Primitives" in [docs/TESTING-GUIDE.md](docs/TESTING-GUIDE.md) describes.
- Give a new jqwik property test `@Property(tries = 50)` or fewer, and keep the collections it
  generates small. hkj-core and hkj-checker already default to 50 tries.
- Where the code under test takes a `TimeSource`, drive it with `SteppableClock` rather than
  sleeping. Otherwise, keep any wait as short as the sibling tests do.
- Show that each new test would catch the problem: undo the fix, or break the new code, and note
  what fails, as step 5 of [Pull Requests](CONTRIBUTING.md#pull-requests) describes.

## Documentation and release notes

- A change to the book follows [docs/STYLE-GUIDE.md](docs/STYLE-GUIDE.md), and the chapter's own
  guide where its Chapter Guides table lists one. The `book-authoring` skill has the procedure.
- A change a user can see gets an entry in `hkj-book/src/release-history/unreleased.md`, under its
  area, with its upgrade impact in that page's Upgrading section. The entry must be accurate; its
  layout is tidied at release time.

## Pull requests

Fill in the pull request template, and follow [Pull Request Titles](CONTRIBUTING.md#pull-request-titles),
[Linking the Issue](CONTRIBUTING.md#linking-the-issue) and
[Keeping Your Branch Up to Date](CONTRIBUTING.md#keeping-your-branch-up-to-date). Two of their rules
are the ones most often missed:

- The title is a sentence that starts with a verb, with no `feat:` or `docs:` prefix and no issue
  number.
- GitHub reads "close", "fix" or "resolve" before an issue number as closing it, even in "this does
  not close #123". For an issue the pull request only partly does, write "Part of #123".
