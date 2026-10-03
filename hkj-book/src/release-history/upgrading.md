# Upgrading

Sections run newest first: start at the top and read down to the section for the release after the one you are on. Each lists what the release notes flag as able to stop a build that compiled, or to change what a program does. [Removals in 0.5.0](#removals-in-050) lists every API due to go in the next minor release, with the recipe that migrates it.

---

## Removals in 0.5.0 {#removals-in-050}

Each of these compiles today with javac's `[removal]` warning, which fails a `-Werror` build. The `org.higherkindedj.openrewrite.MigrateDeprecationsTo0_5_0` recipe runs every recipe the table names; see [0.5.0 deprecation migration](../tooling/openrewrite.md#050-deprecation-migration).

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

`fold` is planned to return on `Try` and `TryPath` in 0.6.0, with the failure-first order, so migrate to `foldFailureFirst` rather than to a local helper named `fold`.

---

## To 0.5.0 {#to-050}

This release is not out yet. Every API in [Removals in 0.5.0](#removals-in-050) is due to go in it, so migrate those first: the `MigrateDeprecationsTo0_5_0` recipe does most of the work. Its notes split what changes into what a running program can notice and what stops a build that compiled: read [Upgrading from 0.4.11](unreleased.md#upgrading) before you move.

---

## To 0.4.11 {#to-0411}

The 0.4.11 notes give the details in [Upgrading from 0.4.10](v0_4_11.md#upgrading). Read [Before you upgrade](v0_4_11.md#before-you-upgrade) first, since a library that publishes specs must be rebuilt before the builds that consume it. Then read [What a running program can notice](v0_4_11.md#runtime-changes), because the processor cannot point at those changes. Each entry in [What stops a build that compiled](v0_4_11.md#build-changes) fails at a line the message names, with its fix.

---

## To 0.4.10 {#to-0410}

The details are in each group of the [0.4.10 notes](v0_4_10.md).

- **A sparse PATCH scans a same-typed container for `null`**: a `null` element, which used to reach the domain, now reports as `tags.1: must not be null`.
- **Explicit type witnesses on some `@ImportOptics` methods stop compiling**: a generated method's type parameters can fall, as `OpticsSpec<Pair<A, String>>` now generates `<A>`. Drop the witnesses: the inferred result is unchanged.
- **The processor refuses some `@InstanceOf` focuses**: one naming a type argument the source does not pin, and a parameterised member of a generic type.
- **A resolved `copyConstructor` is emitted as a cast**: the cast can select a different constructor than ran in 0.4.9, so check overloaded types with `LensLaws`.
- **Generated signatures carry `@Nullable`**: a nullness checker in your build can report differently.
- **`@GenerateFocus` refuses a raw or wildcard `Set` or `Collection`**: such a component used to throw `ClassCastException` on first use. Name the type argument.
- **Three navigator return types move to what the static method reports**: a `Collection` subtype such as `ArrayList` is a `FocusPath` over the container. A nested `List<Optional<String>>` composes to the leaf, so drop a trailing `.some()`.
- **An SPI container of non-navigable elements stops at the container**: set `widenCollections = true` on the declaring record to widen it as before.
- **Four more `@Nullable` annotations widen to `AffinePath`**: JSpecify's, JetBrains', AndroidX's and SpotBugs'. Read such a component with `getOptional`.
- **`Traversals.forSet()` and `traverseSet` return an unmodifiable set**: they used to return a mutable `LinkedHashSet`.
- **A varargs bridge method takes a bare `null` only with a cast**: write `(String[]) null`.
- **`@GeneratePathBridge` refuses more shapes, and warns on an empty interface**: it refuses a raw effect return type, a wildcard `Validated` error type, and a `static` or `private` `@PathVia` method. An interface with no `@PathVia` method draws a warning, which fails a `-Werror` build.
- **A `@ComposeEffects` support class changes shape**: each field must be a `Class<XOp<?>>` naming an `@EffectAlgebra`, and `BoundSet<F>` becomes `BoundSet`. Fix any field the processor refuses, then recompile, and the last effect dispatches at its correct depth.
- **`discarded-effect` reports a deferred Path dropped as a statement**: such as `Path.io(() -> 1).peek(log)`. The check is an error by default, so such a statement stops the build.

---

## To 0.4.9 {#to-049}

The details are in the [0.4.9 notes](v0_4_9.md).

- **An all-`FieldError` response is a 422, not a 400** ([#627](https://github.com/higher-kinded-j/higher-kinded-j/issues/627)): set `hkj.web.validation-field-error-status: 400` to keep the old status.
- **A `null` on a wire parses as a located error**: `parse` and a fallible `assemble` used to throw `NullPointerException`. At a Spring boundary a 500 becomes a 422.
- **A list element's failure carries its index**: `emails: not an email address` becomes `emails.1: not an email address`.
- **A leaf naming no domain component is a compile error**: so is a spec method that collides with a generated member ([#654](https://github.com/higher-kinded-j/higher-kinded-j/issues/654)).
- **A leaf on a projected component now takes effect**: the projection then offers the validated `patch` in place of `asLens()`, so a call to `asLens()` stops compiling.
- **Unused `hkj-spring` configuration properties are removed** ([#642](https://github.com/higher-kinded-j/higher-kinded-j/issues/642)): delete the keys, since none changed behaviour. Code that read them programmatically stops compiling.
- **`hkj.security.validated-user-details` defaults to `false` and starts empty** ([#642](https://github.com/higher-kinded-j/higher-kinded-j/issues/642)): the sample accounts moved to `ValidatedUserDetailsService.withSampleUsers()`, so enabling it without registering accounts fails every login.
- **A JWT with a missing or malformed authorities claim is a 401** ([#642](https://github.com/higher-kinded-j/higher-kinded-j/issues/642)): set `hkj.security.reject-missing-authorities-claim: false` to accept a missing claim.
- **An SSE response commits after the stream's first element** ([#642](https://github.com/higher-kinded-j/higher-kinded-j/issues/642)): a stream failing at its start returns the configured failure status. Emit an early heartbeat on a stream that stays idle.
- **Ambiguous effect-boundary interpreters fail at startup** ([#642](https://github.com/higher-kinded-j/higher-kinded-j/issues/642)): the choice used to follow scan order silently.

---

## To 0.4.1 through 0.4.8 {#to-041-048}

Besides the deprecations in [Removals in 0.5.0](#removals-in-050), the notes for these releases flag these changes.

- **0.4.1: `Free` gains `HandleError` and `Ap` cases**: an exhaustive `switch` over `Free` stops compiling until it handles them, and `AddHandleErrorCaseRecipe` finds such a switch. `Free`'s `F` bound tightens to `WitnessArity<TypeArity.Unary>`. See the [0.4.1 notes](v0_4_1.md).
- **0.4.2: Effect Path handlers honour `@ResponseStatus`**: an annotated handler method now answers with its status, such as `201` for a POST. See the [0.4.2 notes](v0_4_2.md).
- **0.4.2: `hkj.web.either.default-error-status` takes effect** ([#490](https://github.com/higher-kinded-j/higher-kinded-j/issues/490)): the property used not to bind. The flat `hkj.web.default-error-status` still works as an alias.
- **0.4.3: error class names match status keywords by whole word**: `RevalidationError` no longer matches the `validation` heuristic, so its status can change. See the [0.4.3 notes](v0_4_3.md).
- **0.4.5: the compile-time checker gains eleven checks**: the build plugins enable it, so a finding at error stops the build and one at warning fails a `-Werror` build. See [Compile-Time Checks](../tooling/compile_checks.md).
- **0.4.5: every Effect Path prints one `toString` form** ([#530](https://github.com/higher-kinded-j/higher-kinded-j/issues/530)): a test that compares the old text fails. See the [0.4.5 notes](v0_4_5.md).
- **0.4.7: the checker's `raw-kind` check warns on a raw `Kind`**: the warning fails a `-Werror` build. See the [0.4.7 notes](v0_4_7.md).
- **0.4.7: `recoverWith` rejects a `null` argument at once** ([#553](https://github.com/higher-kinded-j/higher-kinded-j/issues/553)): on every `MonadError`, where some instances used to fail later.

---

## To 0.3.5 {#to-035}

- **`VTask.run()` no longer declares `throws Throwable`**: it wraps a checked exception in `VTaskExecutionException`, so code that caught a checked exception from `run()` catches the wrapper instead. See the [0.3.5 notes](v0_3.md#v035-15-february-2026).

---

## To 0.3.0 {#to-030}

- **`Kind` carries its arity from 0.3.0**: type parameters used as witnesses need `WitnessArity` bounds, and the `AddArityBounds` recipe adds them. See [Arity migration](../tooling/openrewrite.md#arity-migration-02x-to-030) and the [0.3.0 notes](v0_3.md#v030-4-january-2026).

---

**Previous:** [Release History](../release-history.md)
**Next:** [Unreleased: 0.5.0](unreleased.md)
