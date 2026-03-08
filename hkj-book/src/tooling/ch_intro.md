# Tooling: Build-Time Safety for Higher-Kinded-J

> _"Make illegal states unrepresentable."_
> -- Yaron Minsky

Every Path type in Higher-Kinded-J can only chain with the same type. Mix a `MaybePath` into an `EitherPath` chain, and you get an `IllegalArgumentException` at runtime. The code compiles; the tests pass (until someone hits that branch); production breaks at 2 AM.

The HKJ tooling catches these mistakes before your code ever runs. Both Gradle and Maven are supported with dedicated build plugins.

---

~~~admonish info title="In This Chapter"
- **[Build Plugins](gradle_plugin.md)** -- One-line setup for Gradle or Maven that configures dependencies, preview features, and compile-time checks automatically. Replaces multi-block boilerplate configuration with a single plugin application.

- **[Compile-Time Checks](compile_checks.md)** -- A javac plugin that detects Path type mismatches at compile time, preventing runtime `IllegalArgumentException` errors. Follows a strict no-false-positives policy.

- **[Diagnostics](diagnostics.md)** -- The `hkjDiagnostics` Gradle task or `mvn hkj:diagnostics` Maven goal that reports your current HKJ configuration, showing exactly which dependencies, compiler arguments, and checks are active.
~~~

## Chapter Contents

1. [Build Plugins](gradle_plugin.md) - One-line project setup for Gradle and Maven
2. [Compile-Time Checks](compile_checks.md) - Path type mismatch detection
3. [Diagnostics](diagnostics.md) - Configuration reporting and troubleshooting

---

**Next:** [Build Plugins](gradle_plugin.md)
