# Higher-Kinded-J OpenRewrite Recipes

Automated refactoring recipes for [Higher-Kinded-J](https://higher-kinded-j.github.io/). Use them
to perform version migrations and to surface code that should adopt newer, safer APIs — without
hand-editing every call site.

The module ships five recipe groups:

| Group | Composite recipe | What it does |
|-------|------------------|--------------|
| Arity migration (0.2.x → 0.3.0) | `org.higherkindedj.openrewrite.AddArityBounds` | Adds `WitnessArity` / `TypeArity` bounds introduced by the embedded kind system |
| Effect algebra helpers | `org.higherkindedj.openrewrite.EffectAlgebraMigration` | Flags raw `Free` / manual `Inject` usage and incomplete `Free` switches |
| 0.5.0 deprecations | `org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0` | Migrates APIs deprecated for removal or changed in 0.5.0 |
| 0.6.0 deprecations | `org.higherkindedj.openrewrite.MigrateDeprecationsTo0_6_0` | Migrates APIs deprecated in 0.5.0 for removal in 0.6.0 |
| 0.6.0 Path family | `org.higherkindedj.openrewrite.AddPathFamily` | Adds the Path family to code that names a capability interface, such as `Chainable<A>` |

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

> Pick the `hkj-openrewrite` version that contains the recipe group you need: the arity recipes
> exist from 0.3.0 onward, the 0.5.0 and 0.6.0 deprecation recipes from the 0.5.0-era release
> onward. Newer releases retain the older recipes.

---

## Recipe Catalogue

### Arity migration (0.2.x → 0.3.0)

Version 0.3.0 introduced an **embedded kind system**: `Kind`, type classes, and witness types now
carry compile-time arity information via `WitnessArity<A extends TypeArity>` and `TypeArity`
(`Unary` for `* -> *`, `Binary` for `* -> * -> *`). These recipes add the required bounds and
`implements` clauses.

| Recipe | Description |
|--------|-------------|
| `org.higherkindedj.openrewrite.AddArityBounds` | **Composite** — runs both arity recipes below |
| `org.higherkindedj.openrewrite.AddWitnessArityToWitness` | Adds `implements WitnessArity<TypeArity.Unary\|Binary>` to witness classes |
| `org.higherkindedj.openrewrite.AddArityBoundsToTypeParameters` | Adds `extends WitnessArity<TypeArity.Unary\|Binary>` bounds to type parameters used as a witness with `Kind`, `Kind2`, type classes, or transformers — covering method params, return types, fields, locals, the class hierarchy, nested generics, and wildcard bounds (appended as an intersection bound when a bound already exists) |

> Required `WitnessArity` / `TypeArity` imports are added automatically by the two recipes above.

Run it with `activeRecipe("org.higherkindedj.openrewrite.AddArityBounds")`.

#### What changes

| Component | Before (0.2.x) | After (0.3.0) |
|-----------|----------------|---------------|
| `Kind<F, A>` | no bounds | `Kind<F extends WitnessArity<?>, A>` |
| `Kind2<F, A, B>` | no bounds | `Kind2<F extends WitnessArity<TypeArity.Binary>, A, B>` |
| Type classes | `Functor<F>` | `Functor<F extends WitnessArity<TypeArity.Unary>>` |
| Witness classes | `final class Witness {}` | `final class Witness implements WitnessArity<TypeArity.Unary> {}` |

#### Manual equivalents

The recipe handles the common cases; the following shows the equivalent hand edits if you need to
migrate something it does not reach (e.g. fields or local variables).

**Unary witness** (one type parameter — `Maybe<A>`, `List<A>`, `IO<A>`):

```java
public interface MyTypeKind<A> extends Kind<MyTypeKind.Witness, A> {
    final class Witness implements WitnessArity<TypeArity.Unary> {
        private Witness() {}
    }
}
```

**Binary witness** (used with `Kind2` — `Either<L, R>`):

```java
public interface MyPairKind2<A, B> extends Kind2<MyPairKind2.Witness, A, B> {
    final class Witness implements WitnessArity<TypeArity.Binary> {
        private Witness() {}
    }
}
```

**Partially applied** (binary fixed to unary, e.g. `Either<Error, ?>`) — still `Unary`, because it
represents `* -> *`:

```java
final class Witness<L> implements WitnessArity<TypeArity.Unary> {
    private Witness() {}
}
```

**Generic method signatures** using unbounded `F`:

```java
public <F extends WitnessArity<TypeArity.Unary>, A, B> Kind<F, B> transform(
        Kind<F, A> fa, Function<A, B> f, Functor<F> functor) {
    return functor.map(f, fa);
}
```

#### Common compilation errors after upgrading

| Compiler error | Cause | Fix |
|----------------|-------|-----|
| `type argument F is not within bounds` | `F` used with `Kind<F, A>` without bounds | Add `<F extends WitnessArity<TypeArity.Unary>>` |
| `Witness is not a valid substitute for F` | Witness class missing `WitnessArity` | Add the `implements WitnessArity<...>` clause |
| `cannot find symbol: WitnessArity` | Missing import | Import `org.higherkindedj.hkt.WitnessArity` and `TypeArity` |

See the [Type Arity documentation](https://higher-kinded-j.github.io/latest/hkts/type-arity.html)
for the rationale (compile-time safety, self-documenting types, clearer errors).

### Effect algebra helpers

These recipes assist migration toward `@EffectAlgebra` / `@ComposeEffects` generated
infrastructure. They are **detection-only**: each tags matching code with an OpenRewrite
[search-result marker](https://docs.openrewrite.org/concepts-and-explanations/data-tables) so it
appears in the dry-run report and data tables — they do **not** rewrite your source.

| Recipe | Detects |
|--------|---------|
| `org.higherkindedj.openrewrite.EffectAlgebraMigration` | **Composite** — runs all three below |
| `org.higherkindedj.openrewrite.AddHandleErrorCaseRecipe` | `Free` switch statements *and expressions* that match `Pure`/`Suspend`/`FlatMapped` but are missing a `HandleError` or `Ap` case |
| `org.higherkindedj.openrewrite.ConvertRawFreeToFreePathRecipe` | Direct `Free.liftF()` / `Free.suspend()` calls that could use generated `*Ops` or the `FreePath` API |
| `org.higherkindedj.openrewrite.DetectInjectBoilerplateRecipe` | Manual `InjectInstances.injectLeft/injectRight/injectRightThen` calls that `@ComposeEffects` would generate |

**Why detection-only?** The replacement is user-specific *generated* code — the `*Ops`
constructors and the `@ComposeEffects` `Support` class are named after each user's effect algebra
and composition, so a correct replacement cannot be synthesised generically. `Free.suspend` →
`FreePath.from` also changes the static type and would not compile without migrating the
surrounding composition. Flagging the call sites for a human keeps the recipes safe.

Review the matches with `./gradlew rewriteDryRun` (Gradle) or `mvn rewrite:dryRun` (Maven).

### 0.5.0 deprecation migration

Rewrites call sites of APIs deprecated for removal in 0.5.0 to their replacements, removes a
deprecated annotation that has no effect, replaces two deprecated `@PathSource` capabilities
with the levels they generate, and drops the `Monad` argument that `StateT` no longer takes. It
adds to `@GenerateFocus`'s `widenFields` each container a 0.4.x navigator stepped into. It
also marks, without rewriting, each null success or null error that 0.5.0 refuses, and each
container it would widen but for an element field named like a path method: `rewriteRun` writes each mark
into the source as a `/*~~(…)~~>*/` comment, to remove once the site is fixed. The composite recipe
runs all sub-recipes in one pass. Run it with `hkj-openrewrite` 0.5.0 before the
project moves to 0.5.0: the recipes match the 0.4.x signatures, and the rewritten `StateT` calls
and `widenFields` entries compile only against 0.5.0, so bump the library straight after.

| Recipe | Change |
|--------|--------|
| `org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0` | **Composite** — runs the sub-recipes below |
| `org.higherkindedj.openrewrite.RenameStateTKindNarrowK` | `StateTKind.narrowK(..)` → `StateTKind.narrow(..)` (the wildcard `Kind` parameter bypassed witness type safety, so an argument typed `Kind<?, A>` needs its witness declared by hand) |
| `org.higherkindedj.openrewrite.RenameKindValidatorNarrowWithPattern` | `KindValidator.narrowWithPattern(..)` → `KindValidator.narrowHolder(..)` |
| `org.higherkindedj.openrewrite.SwapTryFoldToFoldFailureFirstRecipe` | `Try.fold(successMapper, failureMapper)` and `TryPath.fold(successMapper, failureMapper)` → `foldFailureFirst(failureMapper, successMapper)` (renames the method *and* swaps the two arguments) |
| `org.higherkindedj.openrewrite.RemovePathConfig` | Removes `@PathConfig` from a `package-info.java`; no processor reads it, so nothing generated changes |
| `org.higherkindedj.openrewrite.ReplaceDeprecatedPathSourceCapabilitiesRecipe` | `@PathSource`'s `EFFECTFUL` → `CHAINABLE` and `ACCUMULATING` → `RECOVERABLE`; each generates the same class as its replacement |
| `org.higherkindedj.openrewrite.RemoveStateTMonadArgument` | `new StateT<>(fn, monad)`, `StateT.create(fn, monad)` and `StateTKindHelper.stateT(fn, monad)` → the function alone; `stateT.mapT(monad, f)` → `stateT.mapT(f)` |
| `org.higherkindedj.openrewrite.AddWidenFieldsForNavigatedContainersRecipe` | On a `@GenerateFocus` record that writes `generateNavigators = true` and not `widenCollections = true`, adds to `widenFields` each field holding a `Map`, or third-party collection, of `@GenerateFocus` records, unless `includeFields` or `excludeFields` leaves it out. So `ImmutableList<Position> positions` gains `widenFields = "positions"`, and `positions().ticker()` compiles as it did on 0.4.x. Marks, without rewriting, a field whose elements have a field named like a path method, such as `last`, since 0.5.0 can give it no navigator: rename that field while the project is on 0.4.x. Appends where `widenFields` is written, and keeps `generateNavigators = true`. A note can remain at a field that had no navigator in 0.4.x, such as a `Map` of `List`s |
| `org.higherkindedj.openrewrite.DetectNullSuccessValuesRecipe` | Marks, without rewriting: `null` passed to a success or value factory, such as `Either.right`, `Try.success`, `VTask.succeed`, `State.pure`, `Context.succeed`, a `KindHelper` or context `pure`, or a `Path` factory such as `Path.ioPure`; a `null` `iterate` seed, `StateTuple` or `Writer` value, or stream element; a lambda returning `null` given to `Try.of`, a deferred factory such as `VTask.of` or `IO.delay`, or the `map` of a lazy type, a `Path`, an `Either` or a `Writer` (never that of a `Maybe`, an `Optional` or a list, or of their Paths); a `CompletableFuture<Void>` given to `Path.future`; and `Void` as the value type of an `Either`, `Try`, lazy type or `Path`. Replace each with `Unit`, or with a `Maybe` where a value may be missing |
| `org.higherkindedj.openrewrite.DetectNullErrorValuesRecipe` | Marks, without rewriting: `null`, cast or not, passed as the error to `Either.left`, `EitherT.left`, `Maybe.toEither`, `raiseError` on any `MonadError`, or a `LensExtensions` or `PrismExtensions` helper; a `mapLeft` or `bimap` error lambda returning `null`; `Void` as the error type of an `Either`, `EitherT`, `EitherMonad` or `EitherTMonad`. Replace each with an error that says what went wrong, or `Unit.INSTANCE` where the error type is `Unit` |

```kotlin
rewrite {
    activeRecipe("org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0")
}
```

### 0.6.0 deprecation migration

Rewrites call sites of APIs deprecated in 0.5.0 for removal in 0.6.0 to their replacements: a
Focus path's untyped shortcuts, which do not read their element type from the focus, become their
typed forms. `ScopeJoiner.resultEither()` has no recipe and is replaced by hand. Run it with
`hkj-openrewrite` 0.5.x before the project moves to 0.6.0, which removes the methods it matches.
The typed forms compile against 0.4.x and later. A typed form fits only a path that focuses on its
container exactly, so the recipe marks, rather than rewrites, a call on a raw or wildcard container
(view it through an `Iso` to the exact type), on a subtype such as `ArrayList` (declare the field as
`List`), or on another type. It also marks a key or `Traverse` that does not fit the path, a result
read with another element type, from a witness such as `<CharSequence>each()` or from the type it is
assigned to, and a method reference to a shortcut. Each mark says how to replace the call.

| Recipe | Change |
|--------|--------|
| `org.higherkindedj.openrewrite.MigrateDeprecationsTo0_6_0` | **Composite** — runs the recipe below |
| `org.higherkindedj.openrewrite.ReplaceUntypedPathShortcutsRecipe` | On a `FocusPath`, `AffinePath`, `TraversalPath` or navigator: `each()` → `each(EachInstances.listEach())`, `some()` → `some(FocusPaths.optionalSome())`, `at(index)` → `via(FocusPaths.listAt(index))`, `atKey(key)` → `via(FocusPaths.mapAt(key))`, `traverseOver(traverse)` → `each(EachInstances.fromTraverse(traverse))`, and `head()`, `last()`, `tail()`, `init()`, `cons()`, `snoc()`, `headTail()`, `initLast()` → `via(FocusPaths.listHead())` and so on. Drops a type witness written on the shortcut, such as `<Role>each()` |

```kotlin
rewrite {
    activeRecipe("org.higherkindedj.openrewrite.MigrateDeprecationsTo0_6_0")
}
```

### 0.6.0 Path family

From 0.6.0 each Effect Path capability interface takes the implementing Path's family as its first
type parameter, so the compiler checks what a chained step returns. Code that names a capability
type with the old arguments, such as a helper taking an `Effectful<A>` or a `PathProvider` returning
`Chainable<A>`, no longer compiles. The recipe opens the family with `?`, which keeps code that
maps, recovers with `recover` or `handleError`, or runs the path it is given. Code that calls `via`,
`then`, `zipWith`, `recoverWith`, `orElse` or an accumulating method such as `zipWithAccum` through
a capability type needs a type parameter for the family instead, as in `<P, A> Chainable<P, A>`, and
a class that implements `Combinable` names its own class as the family in its supertype and its
`zipWith`; the recipe leaves both to the author. Run it with `hkj-openrewrite` 0.6.0, before or after the project moves to 0.6.0.

| Recipe | Change |
|--------|--------|
| `org.higherkindedj.openrewrite.AddPathFamily` | **Composite** — runs the recipe below |
| `org.higherkindedj.openrewrite.AddPathFamilyToCapabilityTypesRecipe` | `Chainable<A>` → `Chainable<?, A>`, `Combinable<A>` → `Combinable<?, A>`, `Effectful<A>` → `Effectful<?, A>`, `Recoverable<E, A>` → `Recoverable<?, E, A>` and `Accumulating<E, A>` → `Accumulating<?, E, A>`, in variables, fields, parameters, return types, type arguments and `instanceof`. A raw type, and one already written with the family, are left as they are |

```kotlin
rewrite {
    activeRecipe("org.higherkindedj.openrewrite.AddPathFamily")
}
```

---

## Need help?

- [GitHub Discussions](https://github.com/higher-kinded-j/higher-kinded-j/discussions) — ask questions
- [Issue Tracker](https://github.com/higher-kinded-j/higher-kinded-j/issues) — report problems with a recipe
