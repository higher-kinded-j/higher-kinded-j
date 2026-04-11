# Claude Code Skills Distribution

Both the Gradle and Maven plugins can install HKJ Claude Code skills into a consumer project's `.claude/skills/` directory. Skills give developers contextual, in-editor guidance when working with the library.

## Skills Included

| Skill | Purpose |
|-------|---------|
| `hkj-guide` | Path type selection, project setup, migration recipes, compiler error fixes |
| `hkj-optics` | Focus DSL, annotation-driven optics generation, lens and traversal composition |
| `hkj-effects` | Effect handlers with `@EffectAlgebra`, Free monads, interpreters |
| `hkj-bridge` | Composing effects with optics via the bridge API |
| `hkj-spring` | Spring Boot starter integration with functional return types |
| `hkj-arch` | Functional core / imperative shell architecture patterns for Java 25 |

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

Skill files are bundled as classpath resources inside each plugin JAR under `META-INF/hkj-skills/`. A `manifest.txt` lists all bundled files. The install task reads the manifest, then copies each file to `<project>/.claude/skills/`.

The bundling happens at build time via the `bundleSkills` Gradle task, which copies from the repository root's `.claude/skills/hkj-*` directories into the generated resources directory.

## Manual Installation

If you are not using either build plugin, copy the skills from the repository directly:

```bash
git clone --depth 1 https://github.com/higher-kinded-j/higher-kinded-j.git /tmp/hkj-skills
cp -r /tmp/hkj-skills/.claude/skills/hkj-* .claude/skills/
rm -rf /tmp/hkj-skills
```
