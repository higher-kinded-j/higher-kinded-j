# Unreleased: 0.5.0

_Not released yet: these changes are on `main`, and the `latest` book describes them._

To try them, depend on `0.5.0-SNAPSHOT` from the snapshots repository, as [Gradle SNAPSHOT Configuration](../tooling/manual_setup.md#gradle-snapshot-configuration) shows.

~~~admonish info title="At a glance"
- **Optics over a nullable field pass a nullness checker**: every optic and Focus path type takes a `@Nullable` focus.
- **`@PathSource` has a page of its own**, beside `GenericPath` in the Effect Path chapter.
- **`StateT` is built from its state function alone**, and `mapT` takes only the transformation, as on every other transformer.
~~~

---

## Mapping {#mapping}

---

## Optics {#optics}

- **Any two of `Iso`, `Lens`, `Prism`, `Affine` and `Traversal` compose directly with `andThen`** ([#1021](https://github.com/higher-kinded-j/higher-kinded-j/pull/1021)). An `Iso` followed by a `Traversal` now returns a `Traversal`, and so does a `Traversal` followed by an `Affine` or an `Iso`, with no `asTraversal()` first. The composition table is read from the overloads, so it always matches the library. See [Composition Rules Table](../optics/composition_rules.md#composition-rules-table).
- **A map written through an optic keeps its source's order** ([#1022](https://github.com/higher-kinded-j/higher-kinded-j/pull/1022)): every optic that writes a `Map`, from `Traversals.forMapValues` to `AtInstances.mapAt` and `Setter.forMapValues`, hands back a map in the source's iteration order. An updated key keeps its place and a new key goes at the end. See [Persistent and Third-Party Maps](../optics/common_data_structure_traversals.md#persistent-and-third-party-maps-formapvaluescollecting).
- **`modifyWhen` and `branch` on a `Traversal` call only the function their predicate picks** ([#1026](https://github.com/higher-kinded-j/higher-kinded-j/pull/1026)). Each element is now tested first, so a function may assume the condition that guards it, and an expensive one runs only where it is needed. `Lens.modifyWhen`, `modifyBranch` and `setIf` test first too. See [Complete, Runnable Example](../optics/composing_optics.md#complete-runnable-example).
- **Optics over a nullable field pass a nullness checker** ([#767](https://github.com/higher-kinded-j/higher-kinded-j/issues/767)): every optic and Focus path type, and `Kind`, now takes a nullable type. So the `Lens<Config, @Nullable String>` the processor writes for a `@Nullable` component checks under JSpecify. A conversion into `Either`, `Validated` or an Effect Path does not take one yet. See [`.nullable()`: Handle Null Values](../optics/focus_navigation.md#nullable-handle-null-values).
- **`Setter.forList` and `Setter.forMapValues` give the same result on every run** ([#1018](https://github.com/higher-kinded-j/higher-kinded-j/issues/1018)): each time the effect from `modifyF` runs, it builds its own list or map. So an `IO` gives an equal value on every run, and the `List` applicative gives every combination of choices. `Traversals.traverseMapValues` does the same for an empty map. See [Sequencing Effects in Collections](../optics/setters.md#sequencing-effects-in-collections).

---

## Effect Paths {#effect-paths}

- **`StateT` is built from its state function alone** ([#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445)): its constructor, `create` and `StateTKindHelper.stateT` take no `Monad`, and `mapT` takes only the transformation, as on every other transformer. The record holds only that function, so `equals`, `hashCode` and `toString` leave the `Monad` out. See [Running StateT Computations](../transformers/statet_transformer.md#running-statet-computations).
- **`@PathSource` points an effect algebra to `FreePath`** ([#1008](https://github.com/higher-kinded-j/higher-kinded-j/issues/1008)): a Path over a witness that `@EffectAlgebra` generates now draws a note at the witness. The algebra has a `Functor` and no `Monad` to pass to the Path's `of` and `pure`, so the note shows how to wrap its programs in a `FreePath`. See [A witness another processor writes](../effect/path_source.md#a-witness-another-processor-writes).
- **`TrampolineUtils.traverseListStackSafe` is stack-safe under every applicative** ([#1018](https://github.com/higher-kinded-j/higher-kinded-j/issues/1018)): it combines the effects as a balanced tree, so a million-element traversal under `IO` no longer overflows the stack. It builds the result in linear time instead of copying the list at every element, and a `Validated` collecting its errors in a list is no longer quadratic in the failures. See [Core Components](../monads/trampoline_monad.md#core-components).
- **A `VStream` releases its resources when it stops early or fails** ([#1024](https://github.com/higher-kinded-j/higher-kinded-j/issues/1024)): closing reaches every finaliser upstream that the stream has started reading, through any operator, innermost first. `takeWhile`, `zipWith`, `headOption`, `find`, `exists`, `forAll` and a failing terminal operation close what they leave unread. So `bracket(...).map(f).headOption()` releases at once. `VStream.closeAfterFailure` closes a failed manual pull. See [onFinalize: Lightweight Cleanup](../monads/vstream_resources.md#onfinalize-lightweight-cleanup).
- **`VStreamReactive` passes cancellation across the bridge** ([#1024](https://github.com/higher-kinded-j/higher-kinded-j/issues/1024)): a `toPublisher` subscription closes the rest of its stream when it cancels or the stream fails, once it has requested elements. Closing a `fromPublisher` stream cancels its subscription, so `take(n)` stops the publisher. See [Reactive Interop: Flow.Publisher Bridge](../monads/vstream_advanced.md#reactive-interop-flowpublisher-bridge).

---

## Spring {#spring}

- **A streamed `VStreamPath` response closes its stream when it stops** ([#1024](https://github.com/higher-kinded-j/higher-kinded-j/issues/1024)): a client disconnect, a timeout or a serialisation failure now runs the stream's finalisers. See [VStreamPath: SSE Streaming on Virtual Threads](../spring/spring_boot_integration.md#vstreampath-sse-streaming).
- **An SSE stream from a declarative HTTP client closes when you stop early** ([#1024](https://github.com/higher-kinded-j/higher-kinded-j/issues/1024)): `headOption()`, `find(...)` or `take(n)` on a stream from `HkjClientExchange.vstream` now closes the response. There is no need to drain it first. See [Streaming with `VStreamPath`](../spring/declarative_http_clients.md#streaming-with-vstreampath).

---

## Testing {#testing}

- **`assertAffineLaws` law-checks an affine that writes to an absent focus** ([#1023](https://github.com/higher-kinded-j/higher-kinded-j/pull/1023)): `Affines.some()` and a `Lens.andThen(Prism)` affine now pass. On the absent target it checks that `modify` changes nothing, that `set` either changes nothing or writes a value that reads back, and set-set. Add `assertSetNoOpWhenAbsent` for an affine that must leave it alone. See [When the focus is absent](../optics/affine.md#when-the-focus-is-absent).

---

## Build and tooling {#build-and-tooling}

- **`MigrateDeprecationsTo0_5_0` drops the `Monad` that `StateT` no longer takes** ([#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445)): its new `RemoveStateTMonadArgument` recipe rewrites the constructor, `create`, `stateT` and `mapT` calls. Run it from `hkj-openrewrite` 0.5.0 while the project is on 0.4.11, since it matches the 0.4.x signatures. See [0.5.0 deprecation migration](../tooling/openrewrite.md#050-deprecation-migration).
- **The `state-t-mapt-arity` check is retired** ([#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445)): every `mapT` now takes the function alone, so there is no missing argument to report. A `disable=` or `severity:` entry that names it has no effect. The `kind-value-narrow` message now names the right accessor: `run()` on `ReaderT` and `WriterT`, and `runStateT(state)` on `StateT`. See [Compile-Time Checks](../tooling/compile_checks.md).

---

## Documentation {#documentation}

- **The Optics chapter reads in order, from a first nested update to a PATCH** ([#1034](https://github.com/higher-kinded-j/higher-kinded-j/pull/1034)). The sidebar starts with the pages a newcomer needs, then groups the rest by task, reference pages last. The introduction sets a Lombok `@With` cascade beside the Focus path that replaces it. Every page keeps its address. See [Optics](../optics/ch_intro.md).
- **The Focus DSL page answers "what does my field give me?"** ([#1035](https://github.com/higher-kinded-j/higher-kinded-j/pull/1035)). A table keyed by the field you hold gives the generated path and what you write next, each row compiled. The Quickstart shows the lens class the processor writes, and Collections, Optionals and Sealed Types ends on its unusual cases. See [Find your field](../optics/focus_dsl.md#find-your-field).
- **Check an update as you make it, and see the optic inside a path** ([#1036](https://github.com/higher-kinded-j/higher-kinded-j/pull/1036)). Three rewritten pages end the Optics chapter's first lane: What a Path Is Made Of, Updates That Can Fail, which reports every bad value in one call, and Many Edits at Once. Old links still land. See [Updates That Can Fail](../optics/fluent_api.md).
- **The Optics chapter writes compositions as Java code** ([#1037](https://github.com/higher-kinded-j/higher-kinded-j/pull/1037)). The chapter and the `hkj-optics` skill spell a composition `Lens.andThen(Prism) = Affine` instead of the Haskell `>>>`. The glossary gains `andThen`, `modifyF`, navigator, Focus path type and path widening. See [Composition Rules](../optics/composition_rules.md).
- **The Optics chapter's examples come from the build** ([#1020](https://github.com/higher-kinded-j/higher-kinded-j/pull/1020)): composed optics declare the precise type `andThen` returns, and each runnable program and its output come from the examples module. The Affines page says what `set` does on an absent focus. See [When the focus is absent](../optics/affine.md#when-the-focus-is-absent).
- **`@PathSource` has a page of its own** ([#999](https://github.com/higher-kinded-j/higher-kinded-j/issues/999)): [Custom Paths with `@PathSource`](../effect/path_source.md) says when a generated Path is worth it over `GenericPath`, what each capability generates, and how `errorType` types recovery.
- **`modifyAllEither` says it keeps the first error, not that it stops there** ([#1031](https://github.com/higher-kinded-j/higher-kinded-j/pull/1031)): every focused value is still validated, and the result keeps the first error in traversal order. The javadoc of `allThroughEither` and `TraversalExtensions.modifyAllEither`, and the examples, now say so too. See [`modifyAllEither`: First Error Only](../optics/optics_extensions.md#modifyalleither-first-error-only).

---

## Upgrading from 0.4.11 {#upgrading}

### Before you upgrade {#before-you-upgrade}

- **Run `MigrateDeprecationsTo0_5_0` before you move to 0.5.0** ([#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445)): take `hkj-openrewrite` 0.5.0 for the run, and keep the project on 0.4.11 until it finishes. Its `StateT` recipe matches the 0.4.x signatures, so it rewrites nothing once the build resolves 0.5.0. See [0.5.0 deprecation migration](../tooling/openrewrite.md#050-deprecation-migration).

### What a running program can notice {#runtime-changes}

#### Optics {#runtime-optics}

- **A map an optic writes iterates in the source's order** ([#1022](https://github.com/higher-kinded-j/higher-kinded-j/pull/1022)): it came back in hash order, so printed output or a test of a map's iteration order can change. `FocusPaths.mapValues` over an empty map returns a new map.
- **A `TreeMap` keeps its order but not its type** ([#1022](https://github.com/higher-kinded-j/higher-kinded-j/pull/1022)): `forMapValuesCollecting(TreeMap::new)` keeps both when the keys sort in their natural order. A `TreeMap` with its own comparator needs a collector that builds the new `TreeMap` with that comparator.
- **The function a predicate does not pick is no longer called** ([#1026](https://github.com/higher-kinded-j/higher-kinded-j/pull/1026)): by `modifyWhen` and `branch` on a `Traversal`, and by `modifyWhen` and `modifyBranch` on a `Lens`. `Lens.setIf` no longer calls `set` on a failed predicate. So a logging, counting or throwing function sees only the values its predicate picks. `Traversals.speculativeTraverseList` is unchanged: it applies both functions to every element.
- **A read through an `Optional` or a `Maybe` reads a null focus as absent** ([#767](https://github.com/higher-kinded-j/higher-kinded-j/issues/767)): `Fold.preview`, `TraversalPath.preview` and `toMaybePath`, and `FocusPath.toMaybePath` used to throw `NullPointerException`. So did `FocusPath.asAffine`, `headOption`, `IxedInstances.get` and the list optics `listAt`, `listHead` and `listLast`, whose setters, and the list prisms' `build`, now write a null.
- **`find` passes over a null focus to the next match** ([#767](https://github.com/higher-kinded-j/higher-kinded-j/issues/767)): on `Fold`, `TraversalPath`, `OpticOps` and `FoldExtensions`, where a null match threw.
- **The element optics carry a null element through** ([#767](https://github.com/higher-kinded-j/higher-kinded-j/issues/767)): `Setter.forList`, and `FocusPaths.listElements`, `arrayElements`, `listCons`, `listSnoc`, `listTail`, `listInit` and `mapValues`, used to throw.
- **`Plans.preflight` walks a batched traversal to the end** ([#767](https://github.com/higher-kinded-j/higher-kinded-j/issues/767)): `truncated` is now `false` for one, since the walk's null stub values no longer halt it.
- **`IxedInstances.contains` counts an index holding a null as present** ([#767](https://github.com/higher-kinded-j/higher-kinded-j/issues/767)).
- **Sequenced lists are unmodifiable at every size** ([#1018](https://github.com/higher-kinded-j/higher-kinded-j/issues/1018)): `IndexedTraversals.sequenceList`, `ForTraversal.toList` and `ForIndexed.toIndexedList` now return one, as `Traversals.traverseList` does. So do `IndexedTraversals.forList` on a non-empty list and `Traversals.traverseArray` on an empty array. Changing the result throws `UnsupportedOperationException`, so copy it into a new `ArrayList` first.
- **`ForTraversal.toList` and `ForIndexed.toIndexedList` honour the filters** ([#1018](https://github.com/higher-kinded-j/higher-kinded-j/issues/1018)): they collect only the elements that pass `filter` and `filterIndex`, as `modify` and `set` already did. They used to return every focused element. See [Collecting Results](../functional/for_optics.md#collecting-results).

#### Effect Paths {#runtime-effect-paths}

- **`StateT` equality leaves out the `Monad`** ([#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445)): two `StateT` values wrapping the same function instance are equal, with equal hash codes, whichever `Monad` built them. `toString` prints only `runStateTFn`, so a test comparing the old text fails.
- **`TrampolineUtils.traverseListStackSafe` and `sequenceStackSafe` return an unmodifiable list** ([#1018](https://github.com/higher-kinded-j/higher-kinded-j/issues/1018)): as `Traversals.traverseList` does. Changing it throws `UnsupportedOperationException`, so copy it into a new `ArrayList` first.
- **`onFinalize` runs its finaliser for each consumption of a `VStream`** ([#1018](https://github.com/higher-kinded-j/higher-kinded-j/issues/1018)): a second consumption, or one after `close()`, used to skip it. It now also runs when the source fails as it is pulled. `close()` runs it for the latest consumption only, unless that consumption has run it already. See [onFinalize: Lightweight Cleanup](../monads/vstream_resources.md#onfinalize-lightweight-cleanup).
- **`VStreamThrottle.throttle` gives each consumption its own window** ([#1018](https://github.com/higher-kinded-j/higher-kinded-j/issues/1018)): each consumption's first emission opens its first window. A later or concurrent consumption used to share the first one's window.
- **`VStream.defer` calls its supplier when the pulled `VTask` runs** ([#1024](https://github.com/higher-kinded-j/higher-kinded-j/issues/1024)): not when `pull()` is called, and again on each run. `generate`, `iterate` and `unfold` call their functions then too. `distinct`, `chunk` and `chunkWhile` now give the same step each time a pulled `VTask` runs, and a `distinct` tail pulled again replays its elements.
- **`VStreamPar.parEvalMap`, `parEvalMapUnordered` and the `VStream` applicative's `ap` read their input when the pulled `VTask` runs** ([#1024](https://github.com/higher-kinded-j/higher-kinded-j/issues/1024)): not when `pull()` is called, and afresh on each run.
- **Closing a pulled `bracket` stream releases its latest run** ([#1024](https://github.com/higher-kinded-j/higher-kinded-j/issues/1024)): closing the head of a `bracket` after a pull releases the resource that pull acquired, where it used to release nothing.
- **A finaliser that used to be skipped now runs** ([#1024](https://github.com/higher-kinded-j/higher-kinded-j/issues/1024)): when a `VStream` stops early, a terminal operation fails, or a function given to an operator such as `map`, `filter` or `mapTask` throws. A finaliser that throws then fails the operation that stopped, such as `take` or `headOption`. Closing runs the innermost finaliser first, as completing does.
- **`VStreamPar.merge` starts a producer for each run of its pulled `VTask`** ([#1024](https://github.com/higher-kinded-j/higher-kinded-j/issues/1024)): not when `pull()` is called. Closing it stops that run's producer and closes each unfinished source. `close()` used to stop the producer when called; now it works when its `VTask` runs, as every stream's does, so run that `VTask`. See [merge: Concurrent Multi-Source Consumption](../monads/vstream_parallel.md#merge-concurrent-multi-source-consumption).
- **Cancelling a `toPublisher` subscription runs the stream's finalisers** ([#1024](https://github.com/higher-kinded-j/higher-kinded-j/issues/1024)): once the subscriber has requested elements. It used to leave the stream open.
- **A `bracket` whose `use` function throws releases the resource** ([#1024](https://github.com/higher-kinded-j/higher-kinded-j/issues/1024)): before the failure reaches the caller. It used to leave the resource acquired.

#### Testing {#runtime-testing}

- **`assertAffineLaws` no longer requires `set` to leave an absent target alone** ([#1023](https://github.com/higher-kinded-j/higher-kinded-j/pull/1023)): a test that relied on it to catch an affine writing there now passes. Add `AffineLaws.assertSetNoOpWhenAbsent(affine, absentSource, value)` to keep that check.

### What stops a build that compiled {#build-changes}

| Area | Now fails the build | Do this | Issue |
|---|---|---|---|
| Optics | Under a nullness checker, `FocusPaths.nullable()` assigned to an `Affine<X, X>` | Declare `Affine<@Nullable X, X>`, the type `Affines.nullable()` returns | [#767](https://github.com/higher-kinded-j/higher-kinded-j/issues/767) |
| Optics | Under a nullness checker, `AtInstances.listAtWithPadding(null)` for a non-null element type | Pad with a value, or name a nullable element type: `AtInstances.<@Nullable String>listAtWithPadding(null)` | [#767](https://github.com/higher-kinded-j/higher-kinded-j/issues/767) |
| Optics | `var x = iso.andThen(traversal);` (or `traversal.andThen(affine)` or `traversal.andThen(iso)`) followed by assigning another kind of optic to `x`; your own `use(Optic)` and `use(Traversal)` overloads now pick the `Traversal` one | Declare `x` as the `Optic` you mean to hold, or call the overload you mean | [#1021](https://github.com/higher-kinded-j/higher-kinded-j/pull/1021) |
| Effect Paths | A `Monad` passed to `new StateT<>(fn, monad)`, `StateT.create`, `StateTKindHelper.stateT` or `StateT.mapT` | Run the recipe as [Before you upgrade](#before-you-upgrade) says, or drop the argument by hand | [#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445) |
| Effect Paths | A two-component `StateT` record pattern, or `StateT::create` or `StateT::new` as a `BiFunction` | Match the one component, or take a `Function`, by hand | [#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445) |
| Effect Paths | A `StateT` in `var` or a chained call over a witness with a type argument, such as `EitherKind.Witness<E>`, which now infers `Object` for it | Name the types, as in `StateT.<S, EitherKind.Witness<E>, A>create(fn)`, or declare the variable | [#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445) |
| Effect Paths | `evalStateT` and `execStateT` without a `Monad`, deprecated since 0.4.6 | Pass the outer `Monad<F>`, the one `Instances.stateT` took, as the last argument | [#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445) |
| Effect Paths | `StateT.monadF()`, deprecated since 0.4.6 | Keep a reference to the outer `Monad<F>` you built the stack with, and use it in place of the call | [#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445) |

### Deprecated for removal in 0.6.0 {#deprecated}

| Deprecated | Replacement | Recipe |
|---|---|---|

---

**Previous:** [Upgrading](upgrading.md)
**Next:** [v0.4.11](v0_4_11.md)
