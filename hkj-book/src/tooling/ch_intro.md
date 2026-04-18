# Tooling
## _Build-Time Safety for Higher-Kinded-J_
> _"Make illegal states unrepresentable."_
> -- Yaron Minsky

Every Path type in Higher-Kinded-J can only chain with the same type. Mix a `MaybePath` into an `EitherPath` chain, and you get an `IllegalArgumentException` at runtime. The code compiles; the tests pass (until someone hits that branch); production breaks at 2 AM.

The HKJ tooling catches these mistakes before your code ever runs. Both Gradle and Maven are supported with dedicated build plugins.

---

~~~admonish info title="In This Chapter"
- **[Build Plugins](gradle_plugin.md)** -- One-line setup for Gradle or Maven that configures dependencies, preview features, and compile-time checks automatically. Replaces multi-block boilerplate configuration with a single plugin application.

- **[Compile-Time Checks](compile_checks.md)** -- A javac plugin that detects Path type mismatches at compile time, preventing runtime `IllegalArgumentException` errors. Follows a strict no-false-positives policy.

- **[Diagnostics](diagnostics.md)** -- The `hkjDiagnostics` Gradle task or `mvn hkj:diagnostics` Maven goal that reports your current HKJ configuration, showing exactly which dependencies, compiler arguments, and checks are active.

- **[Traversal Generator Plugins](generator_plugins.md)** -- The 23 built-in generators that power `@GenerateTraversals`, covering JDK collections, HKJ core types, and four third-party libraries. Includes a guide to writing your own generator for custom container types.

- **[Claude Code Skills](claude_code_skills.md)** -- Six bundled skills that bring contextual HKJ guidance directly into your editor. Covers Path selection, optics, effect handlers, the effects-optics bridge, Spring Boot integration, and functional core / imperative shell architecture.
~~~

## Chapter Contents

1. [Build Plugins](gradle_plugin.md) - One-line project setup for Gradle and Maven
2. [Compile-Time Checks](compile_checks.md) - Path type mismatch detection
3. [Diagnostics](diagnostics.md) - Configuration reporting and troubleshooting
4. [Traversal Generator Plugins](generator_plugins.md) - Supported types and custom generators
5. [Claude Code Skills](claude_code_skills.md) - In-editor guidance via Claude Code

---

**Next:** [Build Plugins](gradle_plugin.md)
