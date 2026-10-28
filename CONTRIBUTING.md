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
4.  **Make your changes.** Ensure you adhere to standard Java coding conventions.
5.  **Add tests** for your changes. This is important!
6.  **Format the code:** run `./gradlew spotlessApply`, or set up the git hook under [Coding Style](#coding-style) once so that every push checks it. The build, and so CI, fails on Java that Spotless would reformat.
7.  **Run the tests:** Make sure the full test suite passes using `./gradlew test`. If you changed the book, or a rule the book documents, see [Documentation](#documentation) too.
8.  **Build the project:** Ensure the project builds without errors using `./gradlew build`.
9.  **Commit your changes:** Use clear and descriptive commit messages. `git commit -am 'Add some feature'`
10. **Push to your fork:** `git push origin name-of-your-feature-or-fix`
11. **Open a Pull Request** against the `main` branch of the original repository, with a title as described in [Pull Request Titles](#pull-request-titles).
12. **Describe your changes** in the Pull Request description. Link to any relevant issues (e.g., "Closes #123").
13. Ensure the **GitHub Actions CI checks pass**.

### Pull Request Titles

A pull request's commits are squashed into one when it is merged, and its title becomes that commit's subject on `main`. Write the title as a sentence that says what the change does, starting with a verb, in British English:

* Make OpticOpFunctor.map apply its function
* Read payment confirmation time from an injected TimeSource
* Add zipWith3 to TrampolinePath and FreePath, rather than `feat(effect): add zipWith3 to TrampolinePath and FreePath`

Leave out a conventional-commit prefix such as `feat:`, `fix:` or `docs(book):`, and the issue number. GitHub adds the pull request's number to the commit, and "Closes #123" in the description links the issue.

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
* **hkj-checker** -- Javac compiler plugin for compile-time Path type mismatch detection
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

## Documentation

Changes to the book in `hkj-book/src` follow the [Style Guide](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/docs/STYLE-GUIDE.md). Some chapters add rules of their own, listed in its [Chapter Guides](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/docs/STYLE-GUIDE.md#chapter-guides) table. The [Mapping Chapter Guide](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/docs/MAPPING-CHAPTER-GUIDE.md) is one: it says where a new mapping rule or refusal belongs, so a feature that adds one updates those pages in the same pull request. [BOOK-SNIPPETS.md](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/BOOK-SNIPPETS.md) explains how the build compiles the book's Java.

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
