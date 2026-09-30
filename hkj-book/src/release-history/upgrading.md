# Upgrading

Read down from the release after the one you are on. Each section lists what the release notes flag as able to stop a build that compiled, or to change what a program does. [Removals in 0.5.0](#removals-in-050) lists every API due to go in the next minor release, with the recipe that migrates it.

---

## Removals in 0.5.0 {#removals-in-050}

Each of these compiles today with javac's `[removal]` warning, which fails a `-Werror` build. The `MigrateDeprecationsTo0_5_0` recipe rewrites the ones it names; see [0.5.0 deprecation migration](../tooling/openrewrite.md#050-deprecation-migration).

| Deprecated | Since | Use instead | Recipe |
|---|---|---|---|
| `KindValidator.narrowWithPattern` | 0.4.4 | `KindValidator.narrowHolder` | `RenameKindValidatorNarrowWithPattern` |
| `StateTKind.narrowK` | 0.4.5 | `StateTKind.narrow` | `RenameStateTKindNarrowK` |
| `StateT.evalStateT(state)` and `execStateT(state)`, and the `StateTKindHelper` forms without a monad | 0.4.6 | The overloads that take the `Monad<F>` | By hand |
| `StateT.monadF()` | 0.4.6 | Pass the `Monad<F>` to the runner | By hand |
| `Try.fold` and `TryPath.fold`, success first | 0.4.6 | `foldFailureFirst(failureMapper, successMapper)` | `SwapTryFoldToFoldFailureFirstRecipe` |
| `Each.eachWithIndex()` | 0.4.7 | Narrow to `EachIndexed` and call `indexedTraversal()` | By hand |
| `@PathConfig` | 0.4.11 | Nothing, since it has no effect; to rename a Path, set `suffix` on `@PathSource` | `RemovePathConfig` |
| `@PathSource` capability `EFFECTFUL` | 0.4.11 | `CHAINABLE`, which generates the same | `ReplaceDeprecatedPathSourceCapabilitiesRecipe` |
| `@PathSource` capability `ACCUMULATING` | 0.4.11 | `RECOVERABLE`, which generates the same | `ReplaceDeprecatedPathSourceCapabilitiesRecipe` |

`StateT` also changes shape in 0.5.0: its `monadF` record component goes, so two `StateT` values with the same state function compare equal whichever `Monad` built them. Its `equals`, `hashCode` and `toString` change with it.

---

## To 0.4.11 {#to-0411}

This release is not out yet. Its notes split what changes into what a running program can notice and what stops a build that compiled: read [Upgrading from 0.4.10](unreleased.md#upgrading) before you move.

---

## To 0.4.10 {#to-0410}

The details are in each group of the [0.4.10 notes](v0_4_10.md).

- **A sparse PATCH scans a same-typed container for `null`**: a `null` element now reports as `tags.1: must not be null`, where it reached the domain.
- **Explicit type witnesses on some `@ImportOptics` methods stop compiling**: a generated method's type parameters can fall, as `OpticsSpec<Pair<A, String>>` now generates `<A>`. Drop the witnesses: the inferred result is unchanged.
- **Some `@InstanceOf` focuses are refused**: one naming a type argument the source does not pin, and a parameterised member of a generic type.
- **A resolved `copyConstructor` is emitted as a cast**: the cast can select a different constructor than ran in 0.4.9, so check overloaded types with `LensLaws`.
- **Generated signatures carry `@Nullable`**: a nullness checker in your build can report differently.
- **A raw or wildcard `Set` or `Collection` is refused under `@GenerateFocus`**: it used to throw `ClassCastException` on first use. Name the type argument.
- **Three navigator return types move to what the static method reports**: for example, a `Collection` subtype such as `ArrayList` is a `FocusPath` over the container. A nested `List<Optional<String>>` composes to the leaf, so drop a trailing `.some()`.
- **Four more `@Nullable` annotations widen to `AffinePath`**: JSpecify's, JetBrains', AndroidX's and SpotBugs'. Read such a component with `getOptional`.
- **`Traversals.forSet()` and `traverseSet` return an unmodifiable set**: they used to return a mutable `LinkedHashSet`.
- **A varargs bridge method takes a bare `null` only with a cast**: write `(String[]) null`. A raw effect return type and a `static` or `private` `@PathVia` method are refused.
- **A `@ComposeEffects` support class changes shape**: `BoundSet<F>` becomes `BoundSet`, and the last effect dispatches at its correct depth. It is generated in your build, so recompiling migrates it.

---

## To 0.4.9 {#to-049}

The details are in the [0.4.9 notes](v0_4_9.md).

- **An all-`FieldError` response is a 422, not a 400** ([#627](https://github.com/higher-kinded-j/higher-kinded-j/issues/627)): set `hkj.web.validation-field-error-status: 400` to keep the old status.
- **A `null` on a wire parses as a located error**: `parse` and a fallible `assemble` used to throw `NullPointerException`. At a Spring boundary a 500 becomes a 422.
- **A list element's failure carries its index**: `emails: not an email address` becomes `emails.1: not an email address`.
- **A leaf naming no domain component is a compile error** ([#654](https://github.com/higher-kinded-j/higher-kinded-j/issues/654)): so is a spec method that collides with a generated member. A leaf on a projected component now takes effect.
- **Unused `hkj-spring` configuration properties are removed** ([#642](https://github.com/higher-kinded-j/higher-kinded-j/issues/642)): delete the keys, since none changed behaviour. Code that read them programmatically stops compiling.
- **Spring security defaults tighten** ([#642](https://github.com/higher-kinded-j/higher-kinded-j/issues/642)): `hkj.security.validated-user-details` defaults to `false`, and a JWT with a missing or malformed authorities claim is a 401.
- **An SSE response commits after the stream's first element** ([#642](https://github.com/higher-kinded-j/higher-kinded-j/issues/642)): a stream failing at its start returns the configured failure status. Emit an early heartbeat on a stream that stays idle.
- **Ambiguous effect-boundary interpreters fail at startup** ([#642](https://github.com/higher-kinded-j/higher-kinded-j/issues/642)): the choice used to follow scan order silently.

---

## To 0.4.1 through 0.4.8 {#to-041-048}

Besides the deprecations in [Removals in 0.5.0](#removals-in-050), the notes for these releases flag four changes a program can notice.

- **0.4.2: `hkj.web.either.default-error-status` takes effect** ([#490](https://github.com/higher-kinded-j/higher-kinded-j/issues/490)): the property used not to bind. The flat `hkj.web.default-error-status` still works as an alias. See the [0.4.2 notes](v0_4_2.md).
- **0.4.3: error class names match status keywords by whole word**: `RevalidationError` no longer matches the `validation` heuristic, so its status can change. See the [0.4.3 notes](v0_4_3.md).
- **0.4.5: every Effect Path prints one `toString` form** ([#530](https://github.com/higher-kinded-j/higher-kinded-j/issues/530)): a test that compares the old text fails. See the [0.4.5 notes](v0_4_5.md).
- **0.4.7: `recoverWith` rejects a `null` argument at once** ([#553](https://github.com/higher-kinded-j/higher-kinded-j/issues/553)): on every `MonadError`, where some instances used to fail later. See the [0.4.7 notes](v0_4_7.md).

---

## To 0.3.5 {#to-035}

- **`VTask.run()` no longer declares `throws Throwable`**: it wraps a checked exception in `VTaskExecutionException`, so code that caught a checked exception from `run()` catches the wrapper instead. See the [0.3.5 notes](v0_3.md#v035-15-february-2026).

---

## To 0.3.0 {#to-030}

- **0.3.0 requires Java 25, and `Kind` carries its arity.** Type parameters used as witnesses need `WitnessArity` bounds, and the `AddArityBounds` recipe adds them. See [Arity migration](../tooling/openrewrite.md#arity-migration-02x-to-030) and the [0.3.0 notes](v0_3.md#v030-4-january-2026).

---

**Previous:** [Release History](../release-history.md)
**Next:** [Unreleased: 0.4.11](unreleased.md)
