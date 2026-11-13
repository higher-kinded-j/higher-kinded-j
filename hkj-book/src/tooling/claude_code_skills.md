# Claude Code Skills: In-Editor Guidance for HKJ

~~~admonish info title="What You'll Learn"
- What Claude Code skills are and how they help with HKJ development
- How to install skills into your project with one command
- How to tell which release your installed skills describe
- What each of the eight HKJ skills covers
- How skills trigger automatically and how to invoke them directly
~~~

---

## What Is Claude Code?

[Claude Code](https://docs.anthropic.com/en/docs/claude-code) is an AI coding assistant from Anthropic that runs in your terminal, IDE (VS Code, JetBrains), or browser. You describe what you want to do in natural language, and Claude reads your code, suggests changes, runs commands, and answers questions about your codebase.

**Skills** are project-specific knowledge files that teach Claude about the libraries and patterns used in your project. Without skills, Claude relies on general knowledge. With HKJ skills installed, Claude understands the Effect Path API, Focus DSL, effect handlers, and the library's idioms well enough to give accurate, library-specific guidance.

---

## What It Looks Like

With HKJ skills installed, you can ask Claude questions in natural language and get answers grounded in the library's actual API:

```
You: I have a method that returns null when a user isn't found,
     and throws an exception when validation fails. How should
     I model this with HKJ?

Claude: Use EitherPath with a sealed error hierarchy:

        sealed interface UserError {
            record NotFound(String id) implements UserError {}
            record ValidationFailed(List<String> errors) implements UserError {}
        }

        public EitherPath<UserError, User> findUser(String id) {
            return Path.maybe(repository.findById(id))
                .toEitherPath(new UserError.NotFound(id))
                .via(user -> validateUser(user)
                    .mapError(UserError.ValidationFailed::new));
        }
```

The `hkj-guide` skill triggers automatically because the question mentions error handling patterns that match its description keywords. Claude knows the `Path` factory methods, the `toEitherPath` conversion, and the `via`/`mapError` chaining patterns because the skill provides that reference material.

Other examples of questions the skills handle:

- *"Generate lenses for my Order record"* triggers `hkj-optics`
- *"Map my Customer record to a CustomerDto"* triggers `hkj-mapping`
- *"How do I write a test interpreter for my effect algebra?"* triggers `hkj-effects`
- *"How do I return Either from a Spring controller?"* triggers `hkj-spring`
- *"Where should I call unsafeRun?"* triggers `hkj-arch`

---

## Prerequisites

You need Claude Code installed in your development environment. Choose one:

- **Terminal**: Install the [Claude Code CLI](https://docs.anthropic.com/en/docs/claude-code)
- **VS Code**: Install the Claude Code extension from the marketplace
- **JetBrains**: Install the Claude Code plugin from the JetBrains marketplace

Skills are project-level files that live in your repository's `.claude/skills/` directory. Once installed, they are available to anyone on the team who uses Claude Code with the project.

---

## Installing Skills

### Gradle

The HKJ Gradle plugin bundles all eight skills. Install them with a single task:

```bash
./gradlew hkjInstallSkills
```

This copies the skill files from the plugin into `.claude/skills/` at the root of your build, the directory holding the settings file (`settings.gradle` or `settings.gradle.kts`). Every project that applies the plugin has its own `hkjInstallSkills` task, and each installs into that same directory. An install writes only the files that differ, so a multi-project build holds one copy: the first install brings it up to date, and the rest find nothing to do. An included build is a build of its own, so it installs into its own root.

To install automatically during every build, enable the `skills` property in the extension DSL:

```gradle
hkj {
    skills = true
}
```

When `skills` is `true` in a project, its install runs before its `classes` task, so the skills stay up to date when you upgrade the plugin. The install logs a line only when it changes something.

### Maven

The HKJ Maven plugin provides an equivalent goal:

```bash
mvn hkj:install-skills
```

It installs into `.claude/skills/` at the root of the build, once however many modules the build has. Maven takes the root to be the directory holding `.mvn`, or else the directory of the POM it starts from. So give a multi-module project a `.mvn` directory: without one, a build started from a module's directory installs into that module, whether it runs the goal or `<skills>true</skills>` runs it. A root POM that does not apply the plugin needs it in its `<pluginManagement>`, or Maven cannot find the goal there.

To install them during every build instead, set `skills` in the plugin's `<configuration>`:

```xml
<configuration>
    <skills>true</skills>
</configuration>
```

### Manual Installation

If you are not using either build plugin, copy skills directly from the repository:

```bash
# Pin to your release: `main` tracks the unreleased snapshot, whose skills describe
# APIs your version does not have. The build plugins always install the matching set.
git clone --depth 1 --branch v0.4.10 https://github.com/higher-kinded-j/higher-kinded-j.git /tmp/hkj-skills
cp -r /tmp/hkj-skills/.claude/skills/hkj-* .claude/skills/
rm -rf /tmp/hkj-skills
```

### Verifying Installation

```bash
ls .claude/skills/hkj-*/SKILL.md
```

This should list eight `SKILL.md` files. `.claude/skills/.hkj-skills-version` names the Higher-Kinded-J release they describe. The diagnostics task (`./gradlew hkjDiagnostics` or `mvn hkj:diagnostics`) reports whether `skills` is on.

---

## The Eight Skills

| Skill | What It Helps With | Invoke Directly |
|-------|--------------------|-----------------|
| `hkj-guide` | Choosing Path types, project setup, migrating imperative code, fixing compiler errors | `/hkj-guide` |
| `hkj-optics` | `@GenerateLenses`, `@GenerateFocus`, Focus DSL, deep immutable updates, external type import, `Edits`, `ValidatedPrism` | `/hkj-optics` |
| `hkj-mapping` | `@GenerateMapping` (record <-> DTO), `@GenerateMerge`, `@GenerateAssembly`, `@GenerateErrorEnvelope` | `/hkj-mapping` |
| `hkj-effects` | `@EffectAlgebra`, Free monad programs, interpreters, `EffectBoundary`, mock-free testing | `/hkj-effects` |
| `hkj-bridge` | Combining effects with optics: `.focus()` on paths, `toEitherPath()`, unified pipelines | `/hkj-bridge` |
| `hkj-spring` | Spring Boot starter, `Either`/`Validated` responses, `@EnableEffectBoundary`, `@Interpreter` beans | `/hkj-spring` |
| `hkj-arch` | Functional core / imperative shell design, boundary placement, domain modelling with Java 25 | `/hkj-arch` |
| `hkj-test` | AssertJ assertion helpers for HKJ types (including VTaskPath / VStreamPath / VTaskContext and Kind-narrowing wrappers for List / OptionalKind / Stream / Id), transformer assertion unwrappers, JEP 511 module imports | `/hkj-test` |

### How Skills Trigger

Skills trigger in two ways:

1. **Automatically**: Claude matches your question against each skill's keyword description and loads the most relevant one. For example, asking about `@GenerateLenses` triggers `hkj-optics` because those keywords appear in its description.

2. **Directly**: Type `/hkj-guide` (or any skill name) at the Claude prompt to load a specific skill. This is useful when you know which domain you need help with.

The `hkj-guide` skill serves as a navigator. If you are unsure which skill you need, start with `/hkj-guide` and it will suggest the appropriate domain skill.

---

## What Each Skill Contains

Each skill provides condensed reference material optimised for Claude to use when answering your questions:

**hkj-guide** provides a Path type decision tree, creation/extraction cheatsheet for all Path types (including `VResultPath` and `EitherOrBothPath`), operator quick reference (`map`, `via`, `recover`, `mapError`, `focus`), type conversion matrix, ForPath comprehension entry points, `NonEmptyList`, the open-arity accumulating assembly (`fields()` / `accumulate()`), and Gradle/Maven setup instructions. Supporting files cover migration recipes (try/catch to TryPath, Optional to MaybePath, etc.), service layer patterns, and resilience patterns, now led by the path-native combinators (`withRetry`, `withTimeout`, `withCircuitBreaker`, `withBulkhead`) and the railway-aware semantics of `VResultPath`, where a typed `Left` is never retried and never trips the breaker.

**hkj-optics** provides an annotation reference table, step-by-step Focus DSL walkthrough, FocusPath/AffinePath/TraversalPath hierarchy, optic-path labelling (`segments()` / `pathString()`), collection navigation (`.each(Each)`, `.some(Affine)`, `.via(FocusPaths.listAt(i))`, `.nullable()`), the `Edits` multi-edit builder with `Update<S>`, `ValidatedPrism` for parse-don't-validate boundaries, and external type import with `@ImportOptics` and `@OpticsSpec`. Supporting files cover the full optic composition matrix, a cookbook of recipes, container type support (23 types across JDK, Eclipse Collections, Guava, Vavr, Apache Commons), and indexed optics.

**hkj-mapping** covers the compile-time data-assembly generators: `@GenerateMapping` for bidirectional record <-> DTO mapping (spec interfaces, `@MapField` renames, `ValidatedPrism` leaves, derived wire fields, `@OptionalBridge` for a nullable wire component, elementwise lifting through `List`, `Set`, reference arrays, `Optional` and `Map`, nested and recursive records), `@GenerateMerge` for building one target from N sources, `@GenerateAssembly` for staged validated construction, and `@GenerateErrorEnvelope` for sealed error hierarchies with a typed context record. The unifying rule it teaches: `build` is total, `parse` is fallible and accumulates every `FieldError`. A supporting file walks through a complete DTO boundary, including law-checking the round-trip with `MappingLaws`.

**hkj-effects** provides the `@EffectAlgebra` pattern (sealed interface + CPS records), what the annotation processor generates, `@ComposeEffects` wiring, interpreter implementation guide, `EffectBoundary` and `TestBoundary` usage, and program analysis. Supporting files walk through the payment processing example, interpreter patterns (production, test, audit), and Free monad basics.

**hkj-bridge** covers both bridging directions (optics to effects via `toXxxPath()`, effects to optics via `.focus()`), a behaviour table showing what happens for each effect type when an AffinePath focus is absent, and four practical pipeline patterns. A supporting file provides the full capstone example (before/after comparison).

**hkj-spring** covers the starter setup, controller return types (`Either`, `Validated`, `CompletableFuturePath`, `VTaskPath`, `VStreamPath`, `FreePath`), HTTP status code mapping, the `EffectBoundary` adoption ladder (Levels 0-5), `@Interpreter` beans with profile switching, `@EnableEffectBoundary` auto-wiring, and `@EffectTest` test slices. A supporting file walks through the example application.

**hkj-arch** covers the functional core / imperative shell pattern, how Java 25 features (records, sealed interfaces, pattern matching, virtual threads) enable it, mapping HKJ types to core vs shell, boundary design (where to call `.run()` and `.unsafeRun()`), `EffectBoundary` as the named boundary, testing without mocks, and common anti-patterns. Supporting files provide before/after architecture examples and domain modelling patterns.

**hkj-test** covers picking the right assertion class for a given HKJ subject (a 26-row decision table from `Either` through to `Free`, `EitherF`, the Path/context wrappers `VTaskPath` / `VStreamPath` / `VTaskContext`, and the Kind-narrowing wrappers `ListAssert` / `OptionalKindAssert` / `StreamAssert` / `IdAssert`), idioms grouped by type-shape (discriminated unions, `Lazy`, Reader/Writer/State, IO/VTask/VStream, transformers), the transformer-unwrapper pattern, the Java 25 `import module org.higherkindedj.test;` shortcut, and common mistakes (forgetting `whenExecuted()` / `whenRun()`, reusing a stateful assertion, omitting the unwrap function). A supporting file describes the `AssertContract<S, A>` pattern for users writing their own AssertJ extensions.

---

## Updating Skills {#updating-skills}

Skills are versioned with the build plugin. Each install writes a stamp, `.claude/skills/.hkj-skills-version`, naming the release the skills describe:

```properties
# The Higher-Kinded-J release the hkj-* skills in this directory describe.
# The HKJ build plugin writes this file each time it installs them.
version=0.6.0
```

With `skills = true` in the Gradle extension, or `<skills>true</skills>` in the Maven plugin's configuration, each build that compiles also installs whatever differs from the plugin's skills. At the end of each build, the plugin reads the stamp and tells you when the skills are out of step: Gradle before its `BUILD SUCCESSFUL` line, Maven after its build summary. A build whose own install brought them up to date has nothing to say.

| The plugin finds | It says |
|------------------|---------|
| Skills stamped with its own release | Nothing |
| Skills stamped with another release, older or newer | A warning naming both releases and the task to run |
| HKJ skills with no stamp | One line suggesting a reinstall |

Skills installed by hand, or by an older plugin, carry no stamp. Re-run the install to bring them up to date:

```bash
# Gradle
./gradlew hkjInstallSkills

# Maven
mvn hkj:install-skills
```

The skills describe the plugin's own release, which is the library's too unless the build sets `version` in the `hkj` block, or in the Maven plugin's `<configuration>`, to another. The stamp then matches the plugin and says nothing, so keep the plugin and the library on one release.

### Removing Copies in Modules {#copies-in-modules}

The plugins install only at the root of the build, and never delete a copy anywhere else. An older plugin installed a copy into each module, and nothing updates those copies, so they drift from the release you build with. Claude Code reads a module's own skills while it works in that module, so a stale copy can mislead it. Each module that applies the plugin, and holds a copy stamped with another release or none, says so at the end of each build. Install the skills at the root first, if it holds none, then list the copies from the root of your project:

```bash
find . -path ./.claude -prune -o -path '*/.claude/skills/hkj-*' -prune -print
```

It also lists an included build's own copy, and any directory an install task writes to on purpose: keep those. Remove the rest, with `git rm -r` if you commit your skills.

---

## Version Control

The `.claude/skills/` directory should be committed to your repository. This ensures every team member gets the same skill set when they use Claude Code with the project, without needing to run the install task individually.

Add to `.gitignore` only if your team prefers not to commit generated files. In that case, each developer runs the install task after cloning.

---

~~~admonish info title="Key Takeaways"
* **Skills are project-level knowledge files** that teach Claude Code about HKJ's API and patterns
* **Eight skills** cover the library's major domains, from basic Path selection to architecture guidance
* **One command** installs all skills, once, at the root of the build: `./gradlew hkjInstallSkills` or `mvn hkj:install-skills`
* **Skills trigger automatically** when your question matches a skill's domain keywords
* **Direct invocation** via `/hkj-guide` etc. is available when you know which domain you need
* **Skills update with the plugin** version, keeping guidance in sync with the library
* **A stamp names the release** the installed skills describe, and the plugin warns when it differs from its own
~~~

~~~admonish tip title="See Also"
- [Build Plugins](gradle_plugin.md) - Full Gradle and Maven plugin configuration reference
- [Diagnostics](diagnostics.md) - Checking your HKJ configuration, including skills status
~~~

~~~admonish tip title="Further Reading"
- **Anthropic**: [Claude Code documentation](https://docs.anthropic.com/en/docs/claude-code) - Installation, features, and configuration
- **Anthropic**: [Extend Claude with skills](https://docs.anthropic.com/en/docs/claude-code/skills) - How Claude Code skills work, writing custom skills
~~~

---

**Previous:** [PCollections Optics](pcollections_optics.md)
**Next:** [Testing With hkj-test](test_assertions.md)
