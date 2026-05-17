# Tooling
## _Build-Time Safety for Higher-Kinded-J_
> _"Make illegal states unrepresentable."_
> -- Yaron Minsky

Every Path type in Higher-Kinded-J can only chain with the same type. Mix a `MaybePath` into an `EitherPath` chain, and you get an `IllegalArgumentException` at runtime. The code compiles; the tests pass (until someone hits that branch); production breaks at 2 AM.

The HKJ tooling catches these mistakes before your code ever runs. Both Gradle and Maven are supported with dedicated build plugins.

---

~~~admonish info title="In This Chapter"
- **[Build Plugins](gradle_plugin.md)** -- One-line setup for Gradle or Maven that configures dependencies, preview features, and compile-time checks automatically. This is the recommended way to add Higher-Kinded-J to a project; reach for it first.

- **[Manual Gradle and Maven Setup](manual_setup.md)** -- Full `build.gradle.kts` and `pom.xml` configuration for the projects that cannot use the build plugin (constrained environments, in-house build frameworks, or a conflicting plugin). The fallback, not the starting point.

- **[Compile-Time Checks](compile_checks.md)** -- A javac plugin that detects Path type mismatches at compile time, preventing runtime `IllegalArgumentException` errors. Follows a strict no-false-positives policy.

- **[Migration Recipes](openrewrite.md)** -- The `hkj-openrewrite` recipe catalogue for upgrading between Higher-Kinded-J releases. Covers the 0.2.x to 0.3.0 arity migration, detection-only effect-algebra helpers, and the 0.5.0 deprecation renames; runnable from Gradle or Maven.

- **[Diagnostics](diagnostics.md)** -- The `hkjDiagnostics` Gradle task or `mvn hkj:diagnostics` Maven goal that reports your current HKJ configuration, showing exactly which dependencies, compiler arguments, and checks are active.

- **[Traversal Generator Plugins](generator_plugins.md)** -- The 30 built-in generators that power `@GenerateTraversals`, covering JDK collections, HKJ core types, and five third-party libraries (Eclipse Collections, Guava, Vavr, Apache Commons, PCollections). Includes a guide to writing your own generator for custom container types.

- **[PCollections Integration](pcollections_integration.md)** -- Tests, benchmarks, and an example that confirm PCollections persistent collections (`PVector`, `PStack`) compose with the existing `ListKind` infrastructure through `java.util.List` compatibility, with no production code changes.

- **[PCollections Optics](pcollections_optics.md)** -- Seven `@GenerateTraversals` plugins that recognise `PVector`, `PStack`, `PSet`, `PSortedSet`, `PBag`, `PMap`, and `PSortedMap` fields and generate type-correct traversals against them.

- **[Claude Code Skills](claude_code_skills.md)** -- Seven bundled skills that bring contextual HKJ guidance directly into your editor. Covers Path selection, optics, effect handlers, the effects-optics bridge, Spring Boot integration, functional core / imperative shell architecture, and AssertJ-based testing with hkj-test.

- **[Testing With hkj-test](test_assertions.md)** -- A test-scope dependency that ships fluent AssertJ helpers for every HKJ type. Replaces hand-written unwrapping with assertions that read like the business intent: `isRight()`, `hasJustValue(...)`, `whenExecuted().hasValue(...)`, and so on across discriminated unions, effect types, and monad transformers.
~~~

## Chapter Contents

1. [Build Plugins](gradle_plugin.md) - One-line project setup for Gradle and Maven (recommended)
2. [Manual Gradle and Maven Setup](manual_setup.md) - Full manual build configuration (fallback)
3. [Compile-Time Checks](compile_checks.md) - Path type mismatch detection
4. [Migration Recipes](openrewrite.md) - OpenRewrite recipes for upgrading between releases
5. [Diagnostics](diagnostics.md) - Configuration reporting and troubleshooting
6. [Traversal Generator Plugins](generator_plugins.md) - Supported types and custom generators
7. [PCollections Integration](pcollections_integration.md) - PCollections compatibility through `ListKind`
8. [PCollections Optics](pcollections_optics.md) - `@GenerateTraversals` plugins for PCollections types
9. [Claude Code Skills](claude_code_skills.md) - In-editor guidance via Claude Code
10. [Testing With hkj-test](test_assertions.md) - Fluent assertions for HKJ types

---

**Next:** [Build Plugins](gradle_plugin.md)
