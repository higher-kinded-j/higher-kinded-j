# Contributing to Higher-Kinded-J

First off, thank you for considering contributing! This project explores modern functional Java, and contributions are welcome.

This document provides guidelines for contributing to this project.

## Code of Conduct

This project and everyone participating in it is governed by the [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behaviour to simulation.hkt@gmail.com.

## How Can I Contribute?

### Reporting Bugs

* Ensure the bug was not already reported by searching on GitHub under [Issues](https://github.com/higher-kinded-j/higher-kinded-j/issues).
* If you're unable to find an open issue addressing the problem, [open a new one](https://github.com/higher-kinded-j/higher-kinded-j/issues/new). Be sure to include a **title and clear description**, as much relevant information as possible, and a **code sample or an executable test case** demonstrating the expected behaviour that is not occurring.
* Use the "Bug Report" issue template if available.

### Suggesting Enhancements

* Open a new issue to discuss your enhancement suggestion. Please provide details about the motivation and potential implementation.
* Use the "Feature Request" issue template if available.

### Your First Code Contribution

Unsure where to begin contributing? You can start by looking through `good first issue` or `help wanted` issues (you can add these labels yourself to issues you think fit). Once you have a first change merged, `good second issue` marks the next step up: still one module and a clear model to copy, but with a judgement call or two to make along the way.

### Pull Requests

1.  **Fork the repository** on GitHub.
2.  **Clone your fork** locally: `git clone git@github.com:<your-username>/higher-kinded-j.git`
3.  **Create a new branch** for your changes: `git checkout -b name-of-your-feature-or-fix`
4.  **Make your changes,** keeping to what the issue asks, in the shape of the code around them (see [Matching the Code Around It](#matching-the-code-around-it)). If you find another problem on the way, say so in the pull request description, and it can have an issue of its own.
5.  **Add tests** for your changes, and check that they would catch the problem. For a bug fix, keep the new tests, undo the fix under `src/main`, run them, and note which fail and with what message; then restore the fix. For a feature, break the new code instead, for example by returning the wrong value. The pull request template asks for what failed.
6.  **Format the code:** run `./gradlew spotlessApply`, or set up the git hook under [Coding Style](#coding-style) once so that every push checks it. The build, and so CI, fails on Java that Spotless would reformat.
7.  **Run the tests:** Make sure the full test suite passes using `./gradlew test`. If a test or check fails, check out the `main` branch and run the same command there. If it fails on `main` as well, your change did not cause it, so name the check and its message in the pull request description. If you changed the book, or a rule the book documents, see [Documentation](#documentation) too.
8.  **Build the project:** Ensure the project builds without errors using `./gradlew build`.
9.  **Commit your changes:** Use clear and descriptive commit messages. `git add` the files you changed or created, including new tests, then `git commit -m 'Add some feature'`.
10. **Push to your fork:** `git push origin name-of-your-feature-or-fix`
11. **Open a Pull Request** against the `main` branch of the original repository, with a title as described in [Pull Request Titles](#pull-request-titles).
12. **Describe your changes** in the Pull Request description, and link the issue as described in [Linking the Issue](#linking-the-issue).
13. Ensure the **GitHub Actions CI checks pass**. [Running CI on Your Fork](#running-ci-on-your-fork) says how to see them before you open the pull request.

### Pull Request Titles

A pull request's commits are squashed into one when it is merged, and its title becomes that commit's subject on `main`. Write the title as a sentence that says what the change does, starting with a verb, in British English:

* Make OpticOpFunctor.map apply its function
* Read payment confirmation time from an injected TimeSource
* Add zipWith3 to TrampolinePath and FreePath, rather than `feat(effect): add zipWith3 to TrampolinePath and FreePath`

Leave out a conventional-commit prefix such as `feat:`, `fix:` or `docs(book):`, and the issue number. GitHub adds the pull request's number to the commit, and "Closes #123" in the description links the issue, as [Linking the Issue](#linking-the-issue) describes.

### Linking the Issue

Put "Closes #123" in the description for the issue the pull request finishes. GitHub then closes that issue when the pull request merges. For an issue it does only part of, write "Part of #123" instead, so the issue stays open.

GitHub treats "close", "fix" or "resolve", in any form such as "closes" or "fixed", just before an issue number as a request to close that issue. It does so even inside a sentence such as "this does not close #123", so keep those words away from an issue that should stay open. The sidebar's Development section lists the issues a merge will close; check it once the pull request is open.

### Running CI on Your Fork

CI runs on each pull request against `main`. A maintainer approves the runs only when the account is new to GitHub and has had nothing merged here before. To see the results before you open the pull request, run the same workflows in your fork:

1. On your fork's **Actions** tab, enable workflows. A new fork has them turned off.
2. Choose **Java CI with Gradle, JaCoCo, and Codecov**, and **Book Checks** as well if you changed the book, then **Run workflow** on your branch.

If **Run workflow** is missing, bring your fork's `main` up to date with the **Sync fork** button on its home page. GitHub offers it only for a workflow on the fork's default branch. Then merge `main` into your branch, as [Keeping Your Branch Up to Date](#keeping-your-branch-up-to-date) shows, because the run uses the workflow as your branch has it.

### Keeping Your Branch Up to Date

To bring your branch up to date, merge `main` into it, with the pull request's **Update branch** button or from the command line:

```bash
git remote add upstream https://github.com/higher-kinded-j/higher-kinded-j.git   # once
git fetch upstream
git merge upstream/main
```

The pull request is squash-merged, so the merge commit never reaches `main`. Once a review has started, avoid rebasing and force-pushing, which hides what changed since the review. After updating, check that the pull request's Files tab shows only your own changes.

## Development Setup

* You need a Java Development Kit (JDK), version **25**. The build uses preview features, which tie it to that release.
* This project uses Gradle. You can use the included Gradle Wrapper (`gradlew`) to build and test.
    * Build the project: `./gradlew build`
    * Run tests: `./gradlew test`
    * Check optic signatures under a nullness checker: `./gradlew :hkj-processor:nullnessTest` (part of `build`, not of `test`)
    * Generate JaCoCo coverage reports: `./gradlew test jacocoTestReport` (HTML report at `build/reports/jacoco/test/html/index.html`)

## Project Modules

The project is organised into several modules:

* **hkj-core** -- Core library with HKT simulation, Effect Path API, and Optics
* **hkj-api** -- Public API interfaces
* **hkj-annotations** -- Annotations for code generation
* **hkj-processor / hkj-processor-plugins** -- Annotation processor and extensible plugins
* **hkj-checker** -- Javac compiler plugin that explains rejected Path chain steps and reports mistakes javac accepts
* **hkj-bom** -- Bill of Materials that aligns the versions of the core library, `hkj-test` and the Spring starter
* **hkj-test** -- AssertJ assertion helpers for HKJ types (test-scope dependency)
* **plugins/hkj-gradle-plugin** -- Gradle plugin for one-line project setup
* **plugins/hkj-maven-plugin** -- Maven plugin for automated build configuration
* **hkj-spring** -- Spring Boot integration (autoconfigure, starter, example)
* **hkj-openrewrite** -- OpenRewrite recipes for automated migrations
* **hkj-examples** -- Example projects
* **hkj-benchmarks** -- JMH performance benchmarks
* **hkj-book** -- Documentation (mdbook)

## Coding Style

Please follow the [**Google Java Style Guide**](https://google.github.io/styleguide/javaguide.html). Keep code simple, readable, and well-tested.

[Spotless](https://github.com/diffplug/spotless) formats the Java to that style. It also rewraps Javadoc comments to fit the line length and adds the licence header to a new file. `./gradlew build` checks the formatting, so CI fails a pull request with a file Spotless would change. There are two ways to run it before you push:

* **By hand.** `./gradlew spotlessApply` reformats every module, and `./gradlew :hkj-core:spotlessApply` just one. `./gradlew spotlessCheck` shows what it would change, without changing anything.
* **With a git hook, set up once.** `./gradlew spotlessInstallGitPrePushHook` installs a hook that runs `spotlessCheck` before each push. If the formatting is off, the hook runs `spotlessApply` and stops the push, so you commit the reformatted files and push again.

### Matching the Code Around It

Spotless sets the layout; the rest comes from the code next to yours. Before you write a class, method or test, open its nearest sibling, or the model the issue names, and follow its shape. Reviews most often ask for these:

* **Javadoc on every public class, constructor and method,** as the Google Java Style Guide asks.
* **Imports, not fully qualified names,** unless two types share a simple name.
* **Fields and helpers where the siblings put them:** `final` fields together, before any that change, and private helpers after the methods that use them.
* **Package-private test classes,** each with a class-level `@DisplayName`. [TESTING-GUIDE.md](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/docs/TESTING-GUIDE.md) says how the tests are laid out and which shared harnesses to use.
* **No test that repeats one already there.** Look through the class you add to before writing a new case.

## Adding a Path type

A Path names its *family* in the capability interfaces it implements: its own class with the value type left open, as in `MaybePath<A> implements Recoverable<MaybePath<?>, Unit, A>`. The family is what lets javac check that a step passed to `via` is the same kind of Path, so choose it with the class, because changing it later breaks every caller.

Choose the variance of each fixed type parameter by how the Path uses it:

| The parameter is | Variance | Family | As in |
|---|---|---|---|
| Only produced, such as an error | Covariant | `XPath<? extends E, ?>` | `EitherPath`, `VResultPath` |
| Only consumed, such as an environment | Contravariant | `XPath<? super R, ?>` | `ReaderPath` |
| Both produced and consumed, such as a state, a log or a witness | Invariant | `XPath<S, ?>` | `WithStatePath`, `WriterPath`, `GenericPath` |
| Absent: the Path has only its value type | None | `XPath<?>` | `MaybePath`, `IOPath` |

Three rules keep the choice sound:

* **Rebuild a step's Path when the Path holds an instance typed on the parameter.** A covariant `ValidationPath` holds a `Semigroup<E>`, and a step typed on a subtype carries a semigroup for that subtype only. So `via`, `then`, `recoverWith` and `orElse` rebuild the result with the receiver's semigroup, and a regression test accumulates a sibling variant after each. Where that is not possible, make the parameter invariant.
* **Use the family in every chaining and combining method.** That is `via`, `then`, `zipWith`, `zipWith3`, `recoverWith`, `orElse`, the accumulating methods and `ForPath`'s steps, so that one rule holds for all of them. Static helpers over a collection of paths, such as `PathOps.sequenceEither`, infer one error type and keep it.
* **Keep the run-time kind check in each of those methods.** Raw types and code compiled against an older release can still pass another kind. Tests reach the check through the `Mismatched` helper in `hkj-core`'s tests.

## Documentation

Changes to the book in `hkj-book/src` follow the [Style Guide](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/docs/STYLE-GUIDE.md). Some chapters add rules of their own, listed in its [Chapter Guides](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/docs/STYLE-GUIDE.md#chapter-guides) table. The [Mapping Chapter Guide](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/docs/MAPPING-CHAPTER-GUIDE.md) is one: it says where a new mapping rule or refusal belongs, so a feature that adds one updates those pages in the same pull request. [BOOK-SNIPPETS.md](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/BOOK-SNIPPETS.md) explains how the build compiles the book's Java.

### The Claude Code skills

The `hkj-*` skills under `.claude/skills` repeat parts of the book, such as the cheat-sheet rows, the Path tables and their code. They ship inside the Gradle and Maven plugins, and a user installs them into their own project with `./gradlew hkjInstallSkills` or `mvn hkj:install-skills`, as [Claude Code Skills](https://higher-kinded-j.github.io/latest/tooling/claude_code_skills.html) describes. So when you change what the book says about an API, search the skills for the same text, for example `grep -rn "StreamPath" .claude/skills`, and change it there too. `./gradlew :hkj-examples:bookVerify` compiles the snippets the skills mark for checking, as it does the book's.

### Previewing the book

The quickest route needs only [Docker](https://docs.docker.com/get-docker/), with no Rust, Node or mdbook installed:

```bash
hkj-book/docker.sh serve             # live preview at http://localhost:3000
hkj-book/docker.sh preview v0.4.11   # the same, with changes marked against a tag or commit
hkj-book/docker.sh check             # the book checks CI runs
```

The first run fetches (or builds) an image holding the exact mdbook, preprocessors, Node and Python that CI uses. Later runs start at once, and the page reloads as you save.

Prefer a browser? Open the repository in a GitHub Codespace, or in VS Code with the Dev Containers extension: the dev container serves the book on port 3000 automatically.

With a Rust toolchain you can run `hkj-book/serve.sh` instead, which installs the same pinned tools under `hkj-book/.tools`.

Before opening the pull request, run `./gradlew :hkj-examples:test :hkj-examples:bookVerify` and `hkj-book/check.sh` (or `hkj-book/docker.sh check`, which needs no Node.js). The checks need Node.js with npm, and their first run downloads the pinned mermaid library with `curl`. The Mapping chapter's [Compiler Messages generator](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-book/tools/compiler-messages/README.md) also needs Python 3.

Thank you for contributing!
