# Claude Code Skills Distribution

Both the Gradle and Maven plugins can install HKJ Claude Code skills into a consumer project's `.claude/skills/` directory. Skills give developers contextual, in-editor guidance when working with the library.

## Skills Included

| Skill | Purpose |
|-------|---------|
| `hkj-guide` | Path type selection, project setup, migration recipes, compiler error fixes |
| `hkj-optics` | Focus DSL, annotation-driven optics generation, lens and traversal composition, `Edits`, `ValidatedPrism` |
| `hkj-mapping` | Compile-time record <-> DTO mapping (`@GenerateMapping`), merging (`@GenerateMerge`), validated assembly (`@GenerateAssembly`), error envelopes (`@GenerateErrorEnvelope`) |
| `hkj-effects` | Effect handlers with `@EffectAlgebra`, Free monads, interpreters |
| `hkj-bridge` | Composing effects with optics via the bridge API |
| `hkj-spring` | Spring Boot starter: functional return types in controllers (inbound), plus declarative `@HkjHttpClient` HTTP clients that keep typed errors across service calls (outbound) |
| `hkj-arch` | Functional core / imperative shell architecture patterns for Java 25, `TimeSource` |
| `hkj-test` | AssertJ assertions for HKJ types (`assertThatEither`, ...), the optic-law harness, `SteppableClock` |

Each skill has a `SKILL.md` with YAML frontmatter for automatic trigger matching, plus `reference/` files loaded on demand for deeper topics.

## Installing Skills

### Gradle

```gradle
// Run the install task manually
./gradlew hkjInstallSkills

// Or enable automatic installation during the build
hkj {
    skills = true
}
```

### Maven

```bash
mvn hkj:install-skills
```

Or in the POM configuration:

```xml
<configuration>
    <skills>true</skills>
</configuration>
```

## How It Works

Skill files are bundled as classpath resources inside each plugin JAR under `META-INF/hkj-skills/`. A `manifest.txt` lists all bundled files. The install reads the manifest and writes each file whose content differs into `.claude/skills/` at the root of the build, then `.hkj-skills-version`, naming the plugin's release (read from `hkj-version.properties`). It logs a line only when it changes something.

A build keeps one copy:

- **Gradle.** Every project that applies the plugin registers its own `HKJInstallSkillsTask`, whose `outputDir` defaults to `.claude/skills` in the settings directory, and a project with `skills = true` makes its `classes` depend on it. The task is untracked: Gradle always runs it, since another project's install writes the same files, and state Gradle kept for one task would miss an edit made since. Gradle never runs two tasks with the same output at once, and each install writes only what differs, so the first brings the skills up to date and the rest do nothing. Correctness needs no state shared between projects, which holds under isolated projects too.
- **Maven.** A reactor runs `install-skills` in each project. The goal records each install in the session's data, so only the first installs, and an install that fails gives up its claim. It installs into the request's multi-module project directory, the one holding `.mvn` or else that of the starting POM, failing which the top-level project's.

Each file is written to a uniquely named file beside its place and moved over it, so Claude Code never reads half of one, and two processes installing at once do not trip over each other.

Once the build has run, each plugin reads the stamp at the root of the build. It warns when the stamp names another release, and logs one line when HKJ skills carry no stamp. Read after the build, the stamp is current whenever any install ran, so neither plugin needs to know what the build ran.

- **Gradle** reports from `SkillsReport`, a shared build service each project registers as a task completion listener, so Gradle keeps it for every build, one that reuses a configuration cache entry included, and closes it when the build ends. A service registered from another classloader has another type, so each classloader names its services with a UUID of its own. The root report is registered once per build: a project skips it when one under the shared prefix exists already, from any classloader.
- **Maven** reports from the lifecycle participant's `afterSessionEnd`, and logs through SLF4J, which Maven gives its extensions.

Neither plugin deletes anything, so a copy an older plugin installed into a module stays until its owner removes it. Each project other than the root whose own `.claude/skills` holds HKJ skills stamped with another release, or none, is told the build no longer updates them, and to install at the root first if it holds none. Gradle checks through a `SkillsReport` of its own for each project other than the root, at the end of every build; Maven checks when it reads the projects. A copy stamped with the plugin's release is one an install keeps current, such as a task whose `outputDir` points there. Every path is printed in full, since a build may run from any directory. The book's Claude Code Skills page says how to find and remove them.

The bundling happens at build time via the `bundleSkills` Gradle task, which copies from the repository root's `.claude/skills/hkj-*` directories into the generated resources directory. Every `hkj-*` skill ships to users, so a skill for contributors to this repository, such as `book-authoring`, must use a name without that prefix.

## Manual Installation

If you are not using either build plugin, copy the skills from the repository directly:

```bash
git clone --depth 1 https://github.com/higher-kinded-j/higher-kinded-j.git /tmp/hkj-skills
cp -r /tmp/hkj-skills/.claude/skills/hkj-* .claude/skills/
rm -rf /tmp/hkj-skills
```
