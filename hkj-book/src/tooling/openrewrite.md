# Migration Recipes: hkj-openrewrite

~~~admonish info title="What You'll Learn"
- Every OpenRewrite recipe Higher-Kinded-J ships, and what each one does
- How to run the recipes from Gradle or Maven (preview and apply)
- Which recipes rewrite source automatically and which are detection-only, and why
~~~

The `hkj-openrewrite` module ships [OpenRewrite](https://docs.openrewrite.org/) recipes that automate migrations across Higher-Kinded-J releases and surface code that should adopt newer, safer APIs. Use them to upgrade between versions without hand-editing every call site.

This page is the authoritative catalogue. The same recipe set is also referenced by the [`migration-nudge`](compile_checks.md) check in the `hkj-checker` compile-time plugin, which surfaces the detection-only recipes as compile-time nudges for projects that prefer feedback in the editor.

---

## Quick Start

### Gradle

```kotlin
plugins {
    id("org.openrewrite.rewrite") version "7.28.1"
}

dependencies {
    rewrite("io.github.higher-kinded-j:hkj-openrewrite:LATEST_VERSION")
}

rewrite {
    activeRecipe("org.higherkindedj.openrewrite.AddArityBounds")
}
```

```bash
./gradlew rewriteDryRun   # preview changes
./gradlew rewriteRun      # apply changes
```

### Maven

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.openrewrite.maven</groupId>
      <artifactId>rewrite-maven-plugin</artifactId>
      <!-- Use the latest 5.x release; it pairs with the rewrite 8.x core. -->
      <version>LATEST</version>
      <configuration>
        <activeRecipes>
          <recipe>org.higherkindedj.openrewrite.AddArityBounds</recipe>
        </activeRecipes>
      </configuration>
      <dependencies>
        <dependency>
          <groupId>io.github.higher-kinded-j</groupId>
          <artifactId>hkj-openrewrite</artifactId>
          <version>LATEST_VERSION</version>
        </dependency>
      </dependencies>
    </plugin>
  </plugins>
</build>
```

```bash
mvn rewrite:dryRun   # preview changes
mvn rewrite:run      # apply changes
```

`hkj-openrewrite` is published from 0.4.11. Each release keeps every recipe before it, so the latest version carries them all. A recipe for an API deprecated after 0.4.11 arrives with the release that deprecates it. A recipe for a signature that changes with no deprecation first, such as `StateT`'s in 0.5.0, arrives with the release that changes it: run it before the project moves to that release.

---

## Recipe Catalogue

Three recipe groups are shipped; each one has a composite that runs every recipe in the group, plus individual recipes you can activate on their own.

| Group | Composite | Mode |
|-------|-----------|------|
| Arity migration (0.2.x to 0.3.0) | `org.higherkindedj.openrewrite.AddArityBounds` | rewrites source |
| Effect algebra helpers | `org.higherkindedj.openrewrite.EffectAlgebraMigration` | detection only |
| 0.5.0 deprecations | `org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0` | rewrites source, and marks null successes |

### Arity migration (0.2.x to 0.3.0)

Version 0.3.0 introduced an embedded kind system. `Kind`, the type classes, and witness types now carry compile-time arity information via `WitnessArity<A extends TypeArity>` and `TypeArity` (`Unary` for `* -> *`, `Binary` for `* -> * -> *`). These recipes add the required bounds and `implements` clauses so older code compiles against the newer API.

| Recipe | Description |
|--------|-------------|
| `org.higherkindedj.openrewrite.AddArityBounds` | Composite; runs both arity recipes below |
| `org.higherkindedj.openrewrite.AddWitnessArityToWitness` | Adds `implements WitnessArity<TypeArity.Unary>` or `WitnessArity<TypeArity.Binary>` to witness classes |
| `org.higherkindedj.openrewrite.AddArityBoundsToTypeParameters` | Adds `extends WitnessArity<TypeArity.Unary>` or `WitnessArity<TypeArity.Binary>` bounds to type parameters used as a witness with `Kind`, `Kind2`, type classes, or transformers; covers method parameters, return types, fields, local variables, the class hierarchy, nested generics, and wildcard bounds; appended as an intersection bound when a bound already exists |

The required `WitnessArity` and `TypeArity` imports are added automatically by both recipes.

#### What changes

| Component | Before (0.2.x) | After (0.3.0) |
|-----------|----------------|---------------|
| `Kind<F, A>` | no bounds | `Kind<F extends WitnessArity<?>, A>` |
| `Kind2<F, A, B>` | no bounds | `Kind2<F extends WitnessArity<TypeArity.Binary>, A, B>` |
| Type classes | `Functor<F>` | `Functor<F extends WitnessArity<TypeArity.Unary>>` |
| Witness classes | `final class Witness {}` | `final class Witness implements WitnessArity<TypeArity.Unary> {}` |

#### Common compilation errors after upgrading

| Compiler error | Cause | Fix |
|----------------|-------|-----|
| `type argument F is not within bounds` | `F` used with `Kind<F, A>` without bounds | Add `<F extends WitnessArity<TypeArity.Unary>>` |
| `Witness is not a valid substitute for F` | Witness class missing `WitnessArity` | Add the `implements WitnessArity<...>` clause |
| `cannot find symbol: WitnessArity` | Missing import | Import `org.higherkindedj.hkt.WitnessArity` and `TypeArity` |

The `witness-arity` check in [`hkj-checker`](compile_checks.md) catches the same class of mistake at compile time for projects that have already migrated.

### Effect algebra helpers

These recipes assist migration toward `@EffectAlgebra` and `@ComposeEffects` generated infrastructure. Each one tags matching code with an OpenRewrite search-result marker so it appears in the dry-run report and in OpenRewrite data tables; they do not rewrite your source.

| Recipe | Detects |
|--------|---------|
| `org.higherkindedj.openrewrite.EffectAlgebraMigration` | Composite; runs all three below |
| `org.higherkindedj.openrewrite.AddHandleErrorCaseRecipe` | `Free` switch statements and switch expressions that match `Pure`, `Suspend`, or `FlatMapped` but are missing a `HandleError` or `Ap` case |
| `org.higherkindedj.openrewrite.ConvertRawFreeToFreePathRecipe` | Direct `Free.liftF()` and `Free.suspend()` calls that could use generated `*Ops` or the `FreePath` API |
| `org.higherkindedj.openrewrite.DetectInjectBoilerplateRecipe` | Manual `InjectInstances.injectLeft`, `injectRight`, and `injectRightThen` calls that `@ComposeEffects` would generate |

~~~admonish note title="Why detection-only?"
The replacement is user-specific generated code. The `*Ops` constructors and the `@ComposeEffects` `Support` class are named after each user's effect algebra and composition, so a correct replacement cannot be synthesised generically. Converting `Free.suspend` to `FreePath.from` also changes the static type and would not compile without migrating the surrounding composition. Flagging the call sites for a human keeps the recipes safe; the [`migration-nudge`](compile_checks.md) checker is the equivalent at compile time.
~~~

Review the matches with `./gradlew rewriteDryRun` (Gradle) or `mvn rewrite:dryRun` (Maven).

### 0.5.0 deprecation migration

Rewrites call sites of APIs deprecated for removal in 0.5.0 to their replacements, removes a deprecated annotation that has no effect, and replaces two deprecated `@PathSource` capabilities with the levels they generate. It also drops the `Monad` argument that `StateT` no longer takes. These recipes rewrite source, apart from `DetectNullSuccessValuesRecipe`, which only marks each null success that 0.5.0 refuses, for you to change by hand. `rewriteRun` writes each mark into the source as a `/*~~(…)~~>*/` comment; remove it once the site is fixed.

Run them before your build moves to 0.5.0, with `hkj-openrewrite` 0.5.0 on the rewrite classpath. The recipes match the 0.4.x signatures, and the `StateT` calls they rewrite compile only against 0.5.0, so bump the library straight after. A `StateT` held in `var` over a witness with a type argument may then need its types named, as [Removals in 0.5.0](../release-history/upgrading.md#removals-in-050) shows.

| Recipe | Change |
|--------|--------|
| `org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0` | Composite; runs all seven recipes below |
| `org.higherkindedj.openrewrite.RenameStateTKindNarrowK` | `StateTKind.narrowK(..)` becomes `StateTKind.narrow(..)`; the wildcard `Kind` overload bypassed witness type safety |
| `org.higherkindedj.openrewrite.RenameKindValidatorNarrowWithPattern` | `KindValidator.narrowWithPattern(..)` becomes `KindValidator.narrowHolder(..)` |
| `org.higherkindedj.openrewrite.SwapTryFoldToFoldFailureFirstRecipe` | `Try.fold(successMapper, failureMapper)` and `TryPath.fold(...)` become `foldFailureFirst(failureMapper, successMapper)`, swapping the two arguments as well as renaming the method |
| `org.higherkindedj.openrewrite.RemovePathConfig` | Removes `@PathConfig` from a `package-info.java`; no processor reads it, so nothing generated changes |
| `org.higherkindedj.openrewrite.ReplaceDeprecatedPathSourceCapabilitiesRecipe` | `@PathSource`'s `EFFECTFUL` becomes `CHAINABLE` and `ACCUMULATING` becomes `RECOVERABLE`; each generates the same class as its replacement |
| `org.higherkindedj.openrewrite.RemoveStateTMonadArgument` | `new StateT<>(fn, monad)`, `StateT.create(fn, monad)` and `StateTKindHelper.stateT(fn, monad)` take the function alone, and `stateT.mapT(monad, f)` becomes `stateT.mapT(f)` |
| `org.higherkindedj.openrewrite.DetectNullSuccessValuesRecipe` | Marks a `null` passed to a success factory: `Either.right`, `Try.success`, `Path.right`, `Path.success`, `Path.vresultRight`, `VResultPath.pure`, `TryKindHelper.success`, `EitherT.right` or `ErrorContext.success`. Also marks a `Try.of`, `Try.attempt`, `Path.tryOf` or `TryKindHelper.tryOf` lambda that returns `null`. And it marks `Void` as the success type of an `Either`, `Try`, `EitherT`, `EitherPath`, `TryPath`, `VResultPath` or `ErrorContext`, or as the result of a `VTask`, `VTaskPath`, `IO`, `IOPath`, `FreePath`, `CompletableFuturePath` or `Saga`. Replace each with `Unit`, or with a `Maybe` where a value may be missing |

```kotlin
rewrite {
    activeRecipe("org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0")
}
```

---

~~~admonish info title="Key Takeaways"
* **Three groups, three modes.** Arity migration and 0.5.0 deprecations rewrite source, apart from the null-success marks; the effect algebra helpers only flag call sites because their targets are user-specific generated code.
* **Always dry-run first.** `rewriteDryRun` (Gradle) or `mvn rewrite:dryRun` (Maven) shows the diff; `rewriteRun` applies it.
* **Use the composite recipe.** Activating one of the three composites runs the whole group and avoids ordering mistakes; the individual recipes are there when you need finer control.
* **Recipe coverage tracks the library.** The same migrations also surface as compile-time checks in `hkj-checker`; pick whichever feedback loop fits the team.
~~~

---

~~~admonish tip title="See Also"
- [Compile-Time Checks](compile_checks.md) - The `migration-nudge`, `free-switch-exhaustive`, and `witness-arity` checks are the compile-time equivalents of the recipes above
- [Build Plugins](gradle_plugin.md) - The Gradle and Maven plugins that set up the rest of the tooling; add `hkj-openrewrite` to the rewrite plugin as the [Quick Start](#quick-start) shows
- [Release History](../release-history.md) - Per-version notes; recipe groups are documented in the release that introduces them
- [Removals in 0.5.0](../release-history/upgrading.md#removals-in-050) - Every API deprecated for removal, the release that deprecated it, and the recipe that migrates it
~~~

---

**Previous:** [Compile-Time Checks](compile_checks.md)
**Next:** [Diagnostics](diagnostics.md)
