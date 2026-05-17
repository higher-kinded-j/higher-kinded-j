# Higher-Kinded-J OpenRewrite Recipes

Automated refactoring recipes for [Higher-Kinded-J](https://higher-kinded-j.github.io/). Use them
to perform version migrations and to surface code that should adopt newer, safer APIs — without
hand-editing every call site.

The module ships three recipe groups:

| Group | Composite recipe | What it does |
|-------|------------------|--------------|
| Arity migration (0.2.x → 0.3.0) | `org.higherkindedj.openrewrite.AddArityBounds` | Adds `WitnessArity` / `TypeArity` bounds introduced by the embedded kind system |
| Effect algebra helpers | `org.higherkindedj.openrewrite.EffectAlgebraMigration` | Flags raw `Free` / manual `Inject` usage and incomplete `Free` switches |
| 0.5.0 deprecations | `org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0` | Renames APIs deprecated for removal in 0.5.0 |

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
> exist from 0.3.0 onward, the 0.5.0 deprecation recipes from the 0.5.0-era release onward. Newer
> releases retain the older recipes.

---

## Recipe Catalog

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

Rewrites call sites of APIs deprecated for removal in 0.5.0 to their signature-compatible
replacements. These **do** rewrite source (pure method renames).

| Recipe | Rename |
|--------|--------|
| `org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0` | **Composite** — runs both renames below |
| `org.higherkindedj.openrewrite.RenameStateTKindNarrowK` | `StateTKind.narrowK(..)` → `StateTKind.narrow(..)` (the wildcard `Kind` overload bypassed witness type safety) |
| `org.higherkindedj.openrewrite.RenameKindValidatorNarrowWithPattern` | `KindValidator.narrowWithPattern(..)` → `KindValidator.narrowHolder(..)` |

```kotlin
rewrite {
    activeRecipe("org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0")
}
```

---

## Need help?

- [GitHub Discussions](https://github.com/higher-kinded-j/higher-kinded-j/discussions) — ask questions
- [Issue Tracker](https://github.com/higher-kinded-j/higher-kinded-j/issues) — report problems with a recipe
