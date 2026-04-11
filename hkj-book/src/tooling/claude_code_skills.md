# Claude Code Skills: In-Editor Guidance for HKJ

~~~admonish info title="What You'll Learn"
- What Claude Code skills are and how they help with HKJ development
- How to install skills into your project with one command
- What each of the six HKJ skills covers
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

The HKJ Gradle plugin bundles all six skills. Install them with a single task:

```bash
./gradlew hkjInstallSkills
```

This copies skill files from the plugin's classpath resources into your project's `.claude/skills/` directory.

To install automatically during every build, enable the `skills` property in the extension DSL:

```gradle
hkj {
    skills = true
}
```

When `skills` is `true`, the `hkjInstallSkills` task runs as part of the `classes` lifecycle, ensuring skills stay up to date when you upgrade the plugin.

### Maven

The HKJ Maven plugin provides an equivalent goal:

```bash
mvn hkj:install-skills
```

### Manual Installation

If you are not using either build plugin, copy skills directly from the repository:

```bash
git clone --depth 1 https://github.com/higher-kinded-j/higher-kinded-j.git /tmp/hkj-skills
cp -r /tmp/hkj-skills/.claude/skills/hkj-* .claude/skills/
rm -rf /tmp/hkj-skills
```

### Verifying Installation

```bash
ls .claude/skills/hkj-*/SKILL.md
```

This should list six `SKILL.md` files. You can also run the diagnostics task (`./gradlew hkjDiagnostics` or `mvn hkj:diagnostics`) which reports skills status.

---

## The Six Skills

| Skill | What It Helps With | Invoke Directly |
|-------|--------------------|-----------------|
| `hkj-guide` | Choosing Path types, project setup, migrating imperative code, fixing compiler errors | `/hkj-guide` |
| `hkj-optics` | `@GenerateLenses`, `@GenerateFocus`, Focus DSL, deep immutable updates, external type import | `/hkj-optics` |
| `hkj-effects` | `@EffectAlgebra`, Free monad programs, interpreters, `EffectBoundary`, mock-free testing | `/hkj-effects` |
| `hkj-bridge` | Combining effects with optics: `.focus()` on paths, `toEitherPath()`, unified pipelines | `/hkj-bridge` |
| `hkj-spring` | Spring Boot starter, `Either`/`Validated` responses, `@EnableEffectBoundary`, `@Interpreter` beans | `/hkj-spring` |
| `hkj-arch` | Functional core / imperative shell design, boundary placement, domain modelling with Java 25 | `/hkj-arch` |

### How Skills Trigger

Skills trigger in two ways:

1. **Automatically**: Claude matches your question against each skill's keyword description and loads the most relevant one. For example, asking about `@GenerateLenses` triggers `hkj-optics` because those keywords appear in its description.

2. **Directly**: Type `/hkj-guide` (or any skill name) at the Claude prompt to load a specific skill. This is useful when you know which domain you need help with.

The `hkj-guide` skill serves as a navigator. If you are unsure which skill you need, start with `/hkj-guide` and it will suggest the appropriate domain skill.

---

## What Each Skill Contains

Each skill provides condensed reference material optimised for Claude to use when answering your questions:

**hkj-guide** provides a Path type decision tree, creation/extraction cheatsheet for all 18 Path types, operator quick reference (`map`, `via`, `recover`, `mapError`, `focus`), type conversion matrix, ForPath comprehension entry points, and Gradle/Maven setup instructions. Supporting files cover migration recipes (try/catch to TryPath, Optional to MaybePath, etc.), service layer patterns, and resilience patterns (retry, circuit breaker, saga).

**hkj-optics** provides an annotation reference table, step-by-step Focus DSL walkthrough, FocusPath/AffinePath/TraversalPath hierarchy, collection navigation methods (`.each()`, `.at()`, `.some()`, `.nullable()`), and external type import with `@ImportOptics` and `@OpticsSpec`. Supporting files cover the full optic composition matrix, a cookbook of recipes, container type support (23 types across JDK, Eclipse Collections, Guava, Vavr, Apache Commons), and indexed optics.

**hkj-effects** provides the `@EffectAlgebra` pattern (sealed interface + CPS records), what the annotation processor generates, `@ComposeEffects` wiring, interpreter implementation guide, `EffectBoundary` and `TestBoundary` usage, and program analysis. Supporting files walk through the payment processing example, interpreter patterns (production, test, audit), and Free monad basics.

**hkj-bridge** covers both bridging directions (optics to effects via `toXxxPath()`, effects to optics via `.focus()`), a behaviour table showing what happens for each effect type when an AffinePath focus is absent, and four practical pipeline patterns. A supporting file provides the full capstone example (before/after comparison).

**hkj-spring** covers the starter setup, controller return types (`Either`, `Validated`, `CompletableFuturePath`, `VTaskPath`, `VStreamPath`, `FreePath`), HTTP status code mapping, the `EffectBoundary` adoption ladder (Levels 0-5), `@Interpreter` beans with profile switching, `@EnableEffectBoundary` auto-wiring, and `@EffectTest` test slices. A supporting file walks through the example application.

**hkj-arch** covers the functional core / imperative shell pattern, how Java 25 features (records, sealed interfaces, pattern matching, virtual threads) enable it, mapping HKJ types to core vs shell, boundary design (where to call `.run()` and `.unsafeRun()`), `EffectBoundary` as the named boundary, testing without mocks, and common anti-patterns. Supporting files provide before/after architecture examples and domain modelling patterns.

---

## Updating Skills

Skills are versioned with the build plugin. When you upgrade the HKJ plugin version, re-run the install task to get updated skills:

```bash
# Gradle
./gradlew hkjInstallSkills

# Maven
mvn hkj:install-skills
```

If `skills = true` is set in the Gradle extension, skills update automatically on the next build.

---

## Version Control

The `.claude/skills/` directory should be committed to your repository. This ensures every team member gets the same skill set when they use Claude Code with the project, without needing to run the install task individually.

Add to `.gitignore` only if your team prefers not to commit generated files. In that case, each developer runs the install task after cloning.

---

~~~admonish info title="Key Takeaways"
* **Skills are project-level knowledge files** that teach Claude Code about HKJ's API and patterns
* **Six skills** cover the library's major domains, from basic Path selection to architecture guidance
* **One command** installs all skills: `./gradlew hkjInstallSkills` or `mvn hkj:install-skills`
* **Skills trigger automatically** when your question matches a skill's domain keywords
* **Direct invocation** via `/hkj-guide` etc. is available when you know which domain you need
* **Skills update with the plugin** version, keeping guidance in sync with the library
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

**Previous:** [Traversal Generator Plugins](generator_plugins.md)
**Next:** [Integration Guides](../spring/ch_intro.md)
