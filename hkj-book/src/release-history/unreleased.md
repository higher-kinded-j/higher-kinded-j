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
- **Optics over a nullable field pass a nullness checker** ([#767](https://github.com/higher-kinded-j/higher-kinded-j/issues/767)): every optic and Focus path type, and `Kind`, now takes a nullable type. So the `Lens<Config, @Nullable String>` the processor writes for a `@Nullable` component checks under JSpecify. A conversion into `Either`, `Validated` or an Effect Path does not take one yet. See [`.nullable()`: Handle Null Values](../optics/focus_navigation.md#nullable-handle-null-values).
- **`Setter.forList` and `Setter.forMapValues` give the same result on every run** ([#1018](https://github.com/higher-kinded-j/higher-kinded-j/issues/1018)): each time the effect from `modifyF` runs, it builds its own list or map. So an `IO` gives an equal value on every run, and the `List` applicative gives every combination of choices. `Traversals.traverseMapValues` does the same for an empty map. See [Sequencing Effects in Collections](../optics/setters.md#sequencing-effects-in-collections).

---

## Effect Paths {#effect-paths}

- **`StateT` is built from its state function alone** ([#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445)): its constructor, `create` and `StateTKindHelper.stateT` take no `Monad`, and `mapT` takes only the transformation, as on every other transformer. The record holds only that function, so `equals`, `hashCode` and `toString` leave the `Monad` out. See [Running StateT Computations](../transformers/statet_transformer.md#running-statet-computations).
- **`@PathSource` points an effect algebra to `FreePath`** ([#1008](https://github.com/higher-kinded-j/higher-kinded-j/issues/1008)): a Path over a witness that `@EffectAlgebra` generates now draws a note at the witness. The algebra has a `Functor` and no `Monad` to pass to the Path's `of` and `pure`, so the note shows how to wrap its programs in a `FreePath`. See [A witness another processor writes](../effect/path_source.md#a-witness-another-processor-writes).
- **`TrampolineUtils.traverseListStackSafe` is stack-safe under every applicative** ([#1018](https://github.com/higher-kinded-j/higher-kinded-j/issues/1018)): it combines the effects as a balanced tree, so a million-element traversal under `IO` no longer overflows the stack. It builds the result in linear time instead of copying the list at every element, and a `Validated` collecting its errors in a list is no longer quadratic in the failures. See [Core Components](../monads/trampoline_monad.md#core-components).

---

## Spring {#spring}

---

## Testing {#testing}

- **`assertAffineLaws` law-checks an affine that writes to an absent focus** ([#1023](https://github.com/higher-kinded-j/higher-kinded-j/pull/1023)): `Affines.some()` and a `Lens.andThen(Prism)` affine now pass. On the absent target it checks that `modify` changes nothing, that `set` either changes nothing or writes a value that reads back, and set-set. Add `assertSetNoOpWhenAbsent` for an affine that must leave it alone. See [When the focus is absent](../optics/affine.md#when-the-focus-is-absent).

---

## Build and tooling {#build-and-tooling}

- **`MigrateDeprecationsTo0_5_0` drops the `Monad` that `StateT` no longer takes** ([#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445)): its new `RemoveStateTMonadArgument` recipe rewrites the constructor, `create`, `stateT` and `mapT` calls. Run it from `hkj-openrewrite` 0.5.0 while the project is on 0.4.11, since it matches the 0.4.x signatures. See [0.5.0 deprecation migration](../tooling/openrewrite.md#050-deprecation-migration).
- **The `state-t-mapt-arity` check is retired** ([#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445)): every `mapT` now takes the function alone, so there is no missing argument to report. A `disable=` or `severity:` entry that names it has no effect. The `kind-value-narrow` message now names the right accessor: `run()` on `ReaderT` and `WriterT`, and `runStateT(state)` on `StateT`. See [Compile-Time Checks](../tooling/compile_checks.md).

---

## Documentation {#documentation}

- **The Optics chapter's examples come from the build** ([#1020](https://github.com/higher-kinded-j/higher-kinded-j/pull/1020)): composed optics declare the precise type `andThen` returns, and each runnable program and its output come from the examples module. The Affines page says what `set` does on an absent focus. See [When the focus is absent](../optics/affine.md#when-the-focus-is-absent).
- **`@PathSource` has a page of its own** ([#999](https://github.com/higher-kinded-j/higher-kinded-j/issues/999)): [Custom Paths with `@PathSource`](../effect/path_source.md) says when a generated Path is worth it over `GenericPath`, what each capability generates, and how `errorType` types recovery.

---

## Upgrading from 0.4.11 {#upgrading}

### Before you upgrade {#before-you-upgrade}

- **Run `MigrateDeprecationsTo0_5_0` before you move to 0.5.0** ([#445](https://github.com/higher-kinded-j/higher-kinded-j/issues/445)): take `hkj-openrewrite` 0.5.0 for the run, and keep the project on 0.4.11 until it finishes. Its `StateT` recipe matches the 0.4.x signatures, so it rewrites nothing once the build resolves 0.5.0. See [0.5.0 deprecation migration](../tooling/openrewrite.md#050-deprecation-migration).

### What a running program can notice {#runtime-changes}

#### Optics {#runtime-optics}

- **A map an optic writes iterates in the source's order** ([#1022](https://github.com/higher-kinded-j/higher-kinded-j/pull/1022)): it came back in hash order, so printed output or a test of a map's iteration order can change. `FocusPaths.mapValues` over an empty map returns a new map.
- **A `TreeMap` keeps its order but not its type** ([#1022](https://github.com/higher-kinded-j/higher-kinded-j/pull/1022)): `forMapValuesCollecting(TreeMap::new)` keeps both when the keys sort in their natural order. A `TreeMap` with its own comparator needs a collector that builds the new `TreeMap` with that comparator.
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
