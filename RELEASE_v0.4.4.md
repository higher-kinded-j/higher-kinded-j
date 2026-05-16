## Overview

This release ships **hkj-test**, a new publishable module of fluent AssertJ assertion helpers for every public Higher-Kinded-J type, so tests read in the same vocabulary as the code under test. It also validates and extends **PCollections** persistent-collection support across the HKT and optics infrastructure, enriches the type class hierarchy with `Alternative.orElseAll(Iterable)` and `MonadZero.filter`, makes `ForState.zoom` and `ReaderPath.magnify` **optic-polymorphic**, standardises the internal validation package, and refreshes the Tooling chapter to lead with the recommended build-plugin setup.

No breaking changes. All new features are additive. One method, `KindValidator.narrowWithPattern`, is now `@Deprecated(forRemoval = true)` and will be removed in 0.5.0; the new `narrowHolder` is its replacement. Existing code continues to compile and run unchanged.

## Key Features

### hkj-test: Fluent AssertJ Assertions for Every HKJ Type

A new publishable module (`io.github.higher-kinded-j:hkj-test`) hosting fluent AssertJ assertion helpers for every public type, plus the contract-based infrastructure that keeps them correct as the API evolves. Add it as a test-scope dependency and assert directly on the railway:

```gradle
dependencies {
    testImplementation("io.github.higher-kinded-j:hkj-test:0.4.4")
}
```

```java
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;

assertThatEither(result).isRight().hasRight(42);
assertThatMaybe(value).isJust().hasValue("hello");
assertThatTry(computation).isFailure().hasExceptionOfType(IOException.class);
```

Coverage spans the discriminated unions (`Either`, `Maybe`, `Try`, `Validated`, `Lazy`), the effect types (`IO`, `VTask`, `VStream`), the Reader / Writer / State trio, every monad transformer (`EitherT`, `MaybeT`, `OptionalT`, `ReaderT`, `StateT`, `WriterT`), and the `Free` / `EitherF` algebras. The module is published as a JPMS module (`org.higherkindedj.test`) with `requires transitive` on `org.higherkindedj.core` and `org.assertj.core`, so a single dependency declaration is sufficient. On Java 25 with `--enable-preview`:

```java
import module org.higherkindedj.test;   // every helper in scope, one line
```

The `AssertContract<S, A>` framework backs every assertion: subclasses enumerate `Row`s of `(label, passingInput, failingInput, chain)` and two `@TestFactory` methods dispatch each row as a happy-path and a failure-path dynamic test, holding the module at a 100% line + instruction coverage gate. A new `/hkj-test` Claude Code skill auto-triggers on test-related queries mentioning the assertion surface.

See [Testing with hkj-test](https://higher-kinded-j.github.io/latest/tooling/test_assertions.html) for the full reference. ([#407](https://github.com/higher-kinded-j/higher-kinded-j/issues/407), [#511](https://github.com/higher-kinded-j/higher-kinded-j/pull/511))

### hkj-test Coverage Extension

Seven further assertion classes are promoted from `hkj-core` test sources into the published artifact so external consumers can use them directly:

| Category | Helpers |
|---|---|
| Kind-narrowing wrappers | `assertThatList`, `assertThatOptionalKind`, `assertThatStream`, `assertThatId` |
| Path / context assertions | `assertThatVTaskPath`, `assertThatVStreamPath`, `assertThatVTaskContext` |

Each gains an `AssertContract` test and a worked example, keeping the hkj-test JaCoCo gate at 100%. ([#512](https://github.com/higher-kinded-j/higher-kinded-j/issues/512), [#514](https://github.com/higher-kinded-j/higher-kinded-j/pull/514))

### PCollections × ListKind HKT Compatibility

PCollections persistent collections (`PVector`, `PStack`) are now validated end-to-end through the existing `ListKind` / `ListMonad` / `ListTraverse` / `ListSelective` / `Alternative` infrastructure via `java.util.List` compatibility — **with no production code changes**. The release adds integration tests (widen/narrow round-trips, Functor, Monad, Traverse, Foldable, Selective, Alternative over mixed-flavour pipelines), jQwik property tests for the Functor / Monad / Foldable laws, JMH benchmarks comparing `ArrayList` against `PVector` through the HKT pipeline, and a runnable example.

See [PCollections Integration](https://higher-kinded-j.github.io/latest/tooling/pcollections_integration.html). ([#440](https://github.com/higher-kinded-j/higher-kinded-j/issues/440), [#513](https://github.com/higher-kinded-j/higher-kinded-j/pull/513))

### PCollections Optics Generators

Seven `TraversableGenerator` plugins teach `@GenerateTraversals` and `@GenerateFocus` to navigate PCollections types. They auto-discover via `@ServiceProvider` whenever `org.pcollections:pcollections` is on the annotation-processor classpath:

| Generator | Persistent type |
|---|---|
| `PVectorGenerator` | `TreePVector` |
| `PStackGenerator` | `ConsPStack` |
| `PSetGenerator` | `HashTreePSet` |
| `PSortedSetGenerator` | `TreePSet` (natural ordering) |
| `PBagGenerator` | `HashTreePBag` |
| `PMapValueGenerator` | `HashTreePMap` (focuses on values) |
| `PSortedMapValueGenerator` | `TreePMap` (natural key ordering) |

The generator ecosystem grows from **23 to 30** implementations across **7** library families (JDK, Apache Commons Collections4, Eclipse Collections, Guava, Vavr, PCollections, HKJ native).

See [PCollections Optics](https://higher-kinded-j.github.io/latest/tooling/pcollections_optics.html). ([#441](https://github.com/higher-kinded-j/higher-kinded-j/issues/441), [#516](https://github.com/higher-kinded-j/higher-kinded-j/pull/516))

### Traversals.forMapValuesCollecting

The map-shaped companion to `forIterableCollecting`, for persistent and third-party map types whose values you want to traverse:

```java
// Bounded single-arg overload for java.util.Map subtypes (PMap, Guava ImmutableMap)
Traversals.forMapValuesCollecting(map -> HashTreePMap.from(map));

// General two-arg overload (unbounded M) for non-java.util.Map types
// (Eclipse Collections ImmutableMap, Vavr Map)
Traversals.forMapValuesCollecting(M::toJavaMap, javaMap -> M.ofAll(javaMap));
```

`EachInstances.mapValuesEachCollecting` mirrors both overloads for the Focus DSL, and `@GenerateFocus` on `PMap` / `PSortedMap` fields now emits a `TraversalPath` navigator. See [Common Data Structure Traversals](https://higher-kinded-j.github.io/latest/optics/common_data_structure_traversals.html). ([#515](https://github.com/higher-kinded-j/higher-kinded-j/issues/515), [#517](https://github.com/higher-kinded-j/higher-kinded-j/pull/517))

### Alternative.orElseAll(Iterable)

A dynamically-sized counterpart to the existing varargs `orElseAll` and the analogue of Haskell's `asum` / `msum`. It folds an iterable of alternatives via `orElse` starting from `empty()`, so semantics match the varargs form: first non-empty for `Maybe` / `Optional`, concatenation for `List` / `Stream` / `VStream`.

```java
Kind<F, A> winner = alternative.orElseAll(List.of(tryCache, tryDb, tryRemote));
```

`ListMonad` and `StreamMonad` override it to avoid the O(n²) result-list copying and deeply-nested `Stream.concat` chains the derived form would produce, while preserving lazy evaluation end-to-end. See [Alternative](https://higher-kinded-j.github.io/latest/functional/alternative.html). ([#460](https://github.com/higher-kinded-j/higher-kinded-j/issues/460), [#519](https://github.com/higher-kinded-j/higher-kinded-j/pull/519))

### MonadZero.filter

A default `filter(Predicate, Kind)` derived from `flatMap` + `of` / `zero`, so it adds no new algebraic obligations. Argument order matches `Monad.flatMap` (predicate first) for typeclass-API consistency:

```java
Kind<F, Integer> evens = monadZero.filter(n -> n % 2 == 0, numbers);
```

`ListMonad` and `StreamMonad` override it to avoid per-element singleton/empty allocations (`StreamMonad` delegates to `Stream.filter` to preserve laziness). The duplicated guard pattern is refactored out of `For.when`, `ForState.when` / `zoom`, and the `ForPath` comprehension builders to call `filter` directly. See [MonadZero](https://higher-kinded-j.github.io/latest/functional/monad_zero.html). ([#459](https://github.com/higher-kinded-j/higher-kinded-j/issues/459), [#518](https://github.com/higher-kinded-j/higher-kinded-j/pull/518))

### Optic-Polymorphic ForState.zoom and ReaderPath.magnify

`ForState.zoom` now accepts `FocusPath`, `AffinePath` (short-circuiting via `MonadZero.zero()` when the focus is absent), and `Iso` in addition to `Lens`. `ReaderPath` gains optic-aware `magnify(Getter)` and `magnify(FocusPath)` overloads alongside the existing `local(Function)` escape hatch — letting narrowly-typed sub-services be lifted into a larger `AppEnv` for Reader-as-DI:

```java
// Lift a narrowly-typed sub-service into the larger application environment
ReaderPath<AppEnv, Quote> quote =
    pricingPath.magnify(AppEnvFocus.pricingService());
```

A new chapter, [Axes of Transformer Transformation](https://higher-kinded-j.github.io/latest/transformers/transformer_axes.html), plus a `MagnifyServiceLayerExample` and Tutorial 05 (Optic-Polymorphic Zoom and Magnify, six exercises) accompany the change. The [ForState comprehension](https://higher-kinded-j.github.io/latest/functional/forstate_comprehension.html) docs describe the new `zoom` overloads. ([#506](https://github.com/higher-kinded-j/higher-kinded-j/issues/506), [#507](https://github.com/higher-kinded-j/higher-kinded-j/pull/507))

## Bug Fixes and Improvements

* **Validation package standardised** &mdash; the `Operation` enum gains `WIDEN` / `NARROW` / `OR_ELSE_ALL` / `FILTER`; `Validation` exposes `KIND` / `FUNCTION` / `TRANSFORMER` / `CORE` static fields alongside the existing accessors; `FunctionValidator` gains `validateMap` with 44 Functor/Monad sites migrated off the repeated `require(f) + requireNonNull(fa)` pair; `TransformerValidator` gains `requireOuterApplicative` / `requireOuterFunctor` and its mistyped `methodName` parameter is renamed to `operation`; `KindValidator` gains `narrowHolder` and the 12 KindHelpers move to method-reference accessors. `Resource` and `FreeAp` migrate from raw `Objects.requireNonNull` to the validators so their messages carry the same operation context as the rest of the codebase ([#520](https://github.com/higher-kinded-j/higher-kinded-j/pull/520))
* **`KindValidator.narrowWithPattern` deprecated** &mdash; `@Deprecated(since = "0.4.4", forRemoval = true)`, scheduled for removal in 0.5.0; use `narrowHolder` instead ([#520](https://github.com/higher-kinded-j/higher-kinded-j/pull/520))
* **Benchmark report tooling** &mdash; `benchmarkReport` / `benchmarkSummary` tasks are now tolerant of JMH `NaN` scores so single-iteration runs no longer crash the report ([#513](https://github.com/higher-kinded-j/higher-kinded-j/pull/513))

## Documentation

* New [Testing with hkj-test](https://higher-kinded-j.github.io/latest/tooling/test_assertions.html) reference page, registered in the Tooling chapter
* New [PCollections Integration](https://higher-kinded-j.github.io/latest/tooling/pcollections_integration.html) and [PCollections Optics](https://higher-kinded-j.github.io/latest/tooling/pcollections_optics.html) pages
* New [Axes of Transformer Transformation](https://higher-kinded-j.github.io/latest/transformers/transformer_axes.html) chapter in the Transformers section
* New [Where to Start](https://higher-kinded-j.github.io/latest/where_to_start.html) task-first landing page, routing on "what are you trying to do?" before the chapter-level decision trees
* The Tooling chapter is reordered so [Build Plugins](https://higher-kinded-j.github.io/latest/tooling/gradle_plugin.html) lead as the recommended path and Manual Setup follows as the explicit fallback, with Previous/Next navigation rewired across the chapter; the Spring Boot Quickstart gains an `hkj-bom` option (Gradle and Maven forms)
* Updated [Alternative](https://higher-kinded-j.github.io/latest/functional/alternative.html), [MonadZero](https://higher-kinded-j.github.io/latest/functional/monad_zero.html), [Common Data Structure Traversals](https://higher-kinded-j.github.io/latest/optics/common_data_structure_traversals.html), and [ForState comprehension](https://higher-kinded-j.github.io/latest/functional/forstate_comprehension.html) pages
* [Claude Code Skills](https://higher-kinded-j.github.io/latest/tooling/claude_code_skills.html) catalogue updated to seven skills, adding `/hkj-test`

## Compatibility

No breaking changes. All new features are additive.

* `KindValidator.narrowWithPattern` is `@Deprecated(forRemoval = true)` for removal in **0.5.0**; migrate to `narrowHolder`. All other validation-package changes are source- and binary-compatible additions
* PCollections support requires no production code changes; PCollections is an optional, opt-in dependency on the relevant classpaths
* All existing hkj-book page URLs are preserved
