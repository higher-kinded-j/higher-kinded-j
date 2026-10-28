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

Five recipe groups are shipped; each one has a composite that runs every recipe in the group, plus individual recipes you can activate on their own.

| Group | Composite | Mode |
|-------|-----------|------|
| Arity migration (0.2.x to 0.3.0) | `org.higherkindedj.openrewrite.AddArityBounds` | rewrites source |
| Effect algebra helpers | `org.higherkindedj.openrewrite.EffectAlgebraMigration` | detection only |
| 0.5.0 deprecations | `org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0` | rewrites source, and marks null successes, null errors and containers whose elements have a field named like a path method |
| 0.6.0 deprecations | `org.higherkindedj.openrewrite.MigrateDeprecationsTo0_6_0` | rewrites source, and marks calls a typed form does not fit |
| 0.6.0 Path family | `org.higherkindedj.openrewrite.AddPathFamily` | rewrites source |

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

Rewrites call sites of APIs deprecated for removal in 0.5.0 to their replacements, removes a deprecated annotation that has no effect, and replaces two deprecated `@PathSource` capabilities with the levels they generate. It also drops the `Monad` argument that `StateT` no longer takes, and names in `widenFields` each container a 0.4.x navigator stepped into. These recipes rewrite source, apart from `DetectNullSuccessValuesRecipe` and `DetectNullErrorValuesRecipe`, which only mark each null success or null error that 0.5.0 refuses, for you to change by hand. `rewriteRun` writes each mark into the source as a `/*~~(…)~~>*/` comment; remove it once the site is fixed.

Run them before your build moves to 0.5.0, with `hkj-openrewrite` 0.5.0 on the rewrite classpath. The recipes match the 0.4.x signatures, and the `StateT` calls and `widenFields` entries they write compile only against 0.5.0, so bump the library straight after. A `StateT` held in `var` over a witness with a type argument may then need its types named, as [Removals in 0.5.0](../release-history/upgrading.md#removals-in-050) shows.

| Recipe | Change |
|--------|--------|
| `org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0` | Composite; runs all nine recipes below |
| `org.higherkindedj.openrewrite.RenameStateTKindNarrowK` | `StateTKind.narrowK(..)` becomes `StateTKind.narrow(..)`; the wildcard `Kind` parameter bypassed witness type safety, so an argument typed `Kind<?, A>` needs its witness declared by hand |
| `org.higherkindedj.openrewrite.RenameKindValidatorNarrowWithPattern` | `KindValidator.narrowWithPattern(..)` becomes `KindValidator.narrowHolder(..)` |
| `org.higherkindedj.openrewrite.SwapTryFoldToFoldFailureFirstRecipe` | `Try.fold(successMapper, failureMapper)` and `TryPath.fold(...)` become `foldFailureFirst(failureMapper, successMapper)`, swapping the two arguments as well as renaming the method |
| `org.higherkindedj.openrewrite.RemovePathConfig` | Removes `@PathConfig` from a `package-info.java`; no processor reads it, so nothing generated changes |
| `org.higherkindedj.openrewrite.ReplaceDeprecatedPathSourceCapabilitiesRecipe` | `@PathSource`'s `EFFECTFUL` becomes `CHAINABLE` and `ACCUMULATING` becomes `RECOVERABLE`; each generates the same class as its replacement |
| `org.higherkindedj.openrewrite.RemoveStateTMonadArgument` | `new StateT<>(fn, monad)`, `StateT.create(fn, monad)` and `StateTKindHelper.stateT(fn, monad)` take the function alone, and `stateT.mapT(monad, f)` becomes `stateT.mapT(f)` |
| `org.higherkindedj.openrewrite.AddWidenFieldsForNavigatedContainersRecipe` | On a `@GenerateFocus` record that writes `generateNavigators = true` and not `widenCollections = true`, adds to `widenFields` each field holding a `Map`, or third-party collection, of `@GenerateFocus` records, unless `includeFields` or `excludeFields` leaves it out. So `ImmutableList<Position> positions` gains `widenFields = "positions"`, and `positions().ticker()` compiles as it did on 0.4.x. A field whose elements have a field named like a path method, such as `last`, is marked instead, since 0.5.0 can give it no navigator |
| `org.higherkindedj.openrewrite.DetectNullSuccessValuesRecipe` | Marks a `null` passed to a success or value factory, such as `Either.right`, `Try.success`, `VTask.succeed`, `State.pure`, `Context.succeed`, a `KindHelper` or context `pure`, or a `Path` factory such as `Path.ioPure`. It also marks a `null` `iterate` seed, `StateTuple` or `Writer` value, or stream element. A lambda that returns `null` is marked when given to `Try.of`, a deferred factory such as `VTask.of` or `IO.delay`, or the `map` of a lazy type, a `Path`, an `Either` or a `Writer`. The `map` of a `Maybe`, an `Optional` or a list, or of their Paths, is left alone, since there a null result means empty. A `CompletableFuture<Void>` given to `Path.future` is marked, as is `Void` as the value type of an `Either`, `Try`, lazy type or `Path`. Replace each with `Unit`, or with a `Maybe` where a value may be missing |
| `org.higherkindedj.openrewrite.DetectNullErrorValuesRecipe` | Marks a `null`, cast or not, passed as the error to `Either.left`, `EitherT.left`, `Maybe.toEither`, `raiseError` on any `MonadError`, or a `LensExtensions` or `PrismExtensions` helper, and a `mapLeft` or `bimap` error lambda that returns `null`. Also marks `Void` as the error type of an `Either`, `EitherT`, `EitherMonad` or `EitherTMonad`. Replace each with an error that says what went wrong, or `Unit.INSTANCE` where the error type is `Unit` |

```kotlin
rewrite {
    activeRecipe("org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0")
}
```

`AddWidenFieldsForNavigatedContainersRecipe` finds its records by `generateNavigators = true` and keeps that setting, so run it before you take the setting out. Read its dry run for what it leaves to you:

- **For each field it marks, rename the clashing element field.** Do it after the dry run and before `rewriteRun`, while the project is on 0.4.x. There the compiler reports each call through the old name; on 0.5.0, `byTicker().last()` would compile against the path's own `last()`. `rewriteRun` then widens the field. [A field named like a path method](../optics/focus_navigation.md#a-field-named-like-a-path-method) explains the clash.
- **A setting or field list that is not a literal skips the record.** Widen its fields by hand.
- **A note can remain where 0.4.x gave no navigator,** such as at a `Map` of `List`s, since widening it would change what its method returns. A note does not fail the build.
- **An element whose module never ran the processor is widened anyway.** 0.5.0's note at the field says so; take the field out of `widenFields` to keep the container whole, as 0.4.x did.

### 0.6.0 deprecation migration

Rewrites call sites of APIs deprecated in 0.5.0 for removal in 0.6.0 to their replacements. A Focus path's untyped shortcuts, which do not read their element type from the focus, become their typed forms, which do. `ScopeJoiner.resultEither()`, the other API due to go, has no recipe; [Removals in 0.6.0](../release-history/upgrading.md#removals-in-060) gives its replacement.

Run it before your build moves to 0.6.0, with `hkj-openrewrite` 0.5.x on the rewrite classpath, since 0.6.0 removes the methods it matches. The typed forms compile against 0.4.x and later, so the rewritten code builds on the release you are on.

| Recipe | Change |
|--------|--------|
| `org.higherkindedj.openrewrite.MigrateDeprecationsTo0_6_0` | Composite; runs the recipe below |
| `org.higherkindedj.openrewrite.ReplaceUntypedPathShortcutsRecipe` | On a `FocusPath`, an `AffinePath`, a `TraversalPath` or a navigator, `each()` becomes `each(EachInstances.listEach())`, `some()` becomes `some(FocusPaths.optionalSome())`, `at(index)` and `atKey(key)` become `via(FocusPaths.listAt(index))` and `via(FocusPaths.mapAt(key))`, and `traverseOver(traverse)` becomes `each(EachInstances.fromTraverse(traverse))`. `head()`, `last()`, `tail()`, `init()`, `cons()`, `snoc()`, `headTail()` and `initLast()` become `via` with the `FocusPaths` list optic of the same name, such as `via(FocusPaths.listHead())`. A type witness written on the shortcut, as in `<Role>each()`, is dropped, since the typed form reads the element type from the path |

```kotlin
rewrite {
    activeRecipe("org.higherkindedj.openrewrite.MigrateDeprecationsTo0_6_0")
}
```

A typed form fits only a path that focuses on its container exactly, such as a `List<Role>`. The recipe marks each call it cannot rewrite, and `rewriteRun` leaves the mark in the source as a `/*~~(…)~~>*/` comment, to remove once the call is replaced:

- **A raw or wildcard container**, such as a lens onto a `List<? extends Number>`. The shortcut read it correctly, and no typed form takes it. View it as the container exactly through an `Iso`, as the code after this list shows, then step in. Give a raw container its type argument.
- **A subtype such as `ArrayList`, or a type variable bounded by the container**: declare the focus as the container itself, such as `List` or `Map`. For `each()` on an `ArrayList`, `each(EachInstances.fromIterableCollecting(ArrayList::new))` also works, and rebuilds an `ArrayList`.
- **Another type altogether.** The call threw `ClassCastException` when it ran; compose the optic for what the path does focus on.
- **A key or a `Traverse` that does not fit the path**, such as an `Object` key on a `Map<String, Integer>`. Pass a key of the map's key type, or the `Traverse` for the `Kind`'s witness.
- **A result read with another element type**: a witness, as in `<CharSequence>each()` on a `List<String>`, or a wider type the result is assigned to. Write the typed form, and make the code around it take the element the path holds.
- **A call made on the result with no target type**, such as `path.each().getAll(source)`, where the shortcut focused on `Object`. Write the typed form, and check the code that reads its result.
- **A method reference to a shortcut**: write the typed form in a lambda.

A call whose result nothing reads, or that another shortcut is called on, is rewritten.

Each of these views sees a wildcard container as the container exactly, so the typed form fits. The `List` is seen through a read-only view, and the `Kind` is mapped with its own `Traverse`:

<!-- verify -->
```java
TraversalPath<Basket, Number> amounts =
    amountsPath
        .via(Iso.<List<? extends Number>, List<Number>>of(Collections::unmodifiableList, l -> l))
        .each(EachInstances.listEach());

TraversalPath<Basket, CharSequence> labels =
    labelsPath
        .via(
            Iso.<Kind<ListKind.Witness, ? extends CharSequence>, Kind<ListKind.Witness, CharSequence>>of(
                k -> ListTraverse.INSTANCE.map(c -> c, k), k -> k))
        .each(EachInstances.fromTraverse(ListTraverse.INSTANCE));
```

### 0.6.0 Path family {#060-path-family}

From 0.6.0 each capability interface takes the implementing Path's family as its first type parameter, as [The Family Parameter](../effect/capabilities.md#the-family-parameter) explains. Code that names a capability type with the old arguments, such as a helper taking an `Effectful<A>` or a `PathProvider` returning `Chainable<A>`, no longer compiles. The recipe writes the family as `?`, which keeps code that maps, recovers with `recover` or `handleError`, or runs the path it is given.

Run it with `hkj-openrewrite` 0.6.0 on the rewrite classpath, before or after your build moves to 0.6.0. A type already written with the family is left as it is, so a second run changes nothing.

| Recipe | Change |
|--------|--------|
| `org.higherkindedj.openrewrite.AddPathFamily` | Composite; runs the recipe below |
| `org.higherkindedj.openrewrite.AddPathFamilyToCapabilityTypesRecipe` | `Chainable<A>` becomes `Chainable<?, A>`, and likewise `Combinable<A>` and `Effectful<A>`. `Recoverable<E, A>` becomes `Recoverable<?, E, A>`, and `Accumulating<E, A>` becomes `Accumulating<?, E, A>`. It rewrites variables, fields, parameters, return types, type arguments and `instanceof`, a provider's `createPath` included. A raw type is left as it is |

```kotlin
rewrite {
    activeRecipe("org.higherkindedj.openrewrite.AddPathFamily")
}
```

Two shapes are left to you:

- **Code that chains or combines through a capability type.** A step passed to `via`, `then`, `zipWith`, `recoverWith` or `orElse`, or the other path passed to `zipWithAccum`, `zipWith3Accum`, `andAlso` or `andThen`, must belong to the same family. Declare the family as a type parameter shared by both, as in `<P, E, A> Accumulating<P, E, A>`.
- **The supertype and `zipWith` of a class that implements `Combinable`.** Name its own class as the family, as a generated `@PathSource` Path does: `Combinable<MyPath<?>, A>`, with `zipWith` taking a `Combinable<? extends MyPath<?>, B>`. The recipe rewrites the class's other members.

---

~~~admonish info title="Key Takeaways"
* **Five groups, three modes.** Arity migration, the 0.5.0 and 0.6.0 deprecations and the Path family rewrite source, apart from the marks the two deprecation groups leave for you. The effect algebra helpers only flag call sites, because their targets are user-specific generated code.
* **Always dry-run first.** `rewriteDryRun` (Gradle) or `mvn rewrite:dryRun` (Maven) shows the diff; `rewriteRun` applies it.
* **Use the composite recipe.** Activating a group's composite runs the whole group and avoids ordering mistakes; the individual recipes are there when you need finer control.
* **Recipe coverage tracks the library.** The same migrations also surface as compile-time checks in `hkj-checker`; pick whichever feedback loop fits the team.
~~~

---

~~~admonish tip title="See Also"
- [Compile-Time Checks](compile_checks.md) - The `migration-nudge`, `free-switch-exhaustive`, and `witness-arity` checks are the compile-time equivalents of the recipes above
- [Build Plugins](gradle_plugin.md) - The Gradle and Maven plugins that set up the rest of the tooling; add `hkj-openrewrite` to the rewrite plugin as the [Quick Start](#quick-start) shows
- [Release History](../release-history.md) - Per-version notes; recipe groups are documented in the release that introduces them
- [Removals in 0.5.0](../release-history/upgrading.md#removals-in-050) - Every API deprecated for removal, the release that deprecated it, and the recipe that migrates it
- [Removals in 0.6.0](../release-history/upgrading.md#removals-in-060) - The APIs deprecated in 0.5.0, and the recipe that migrates them
~~~

---

**Previous:** [Compile-Time Checks](compile_checks.md)
**Next:** [Diagnostics](diagnostics.md)
