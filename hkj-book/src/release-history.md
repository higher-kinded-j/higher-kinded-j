# Release History

This page documents the evolution of Higher-Kinded-J from its initial release through to the current version. Each release builds on the foundations established by earlier versions, progressively adding type classes, monads, optics, and the Effect Path API.

~~~admonish info title="What You'll Find"
- Detailed release notes for recent versions (0.3.0–0.4.2) with links to documentation
- Summary release notes for earlier versions (pre-0.3.0)
- Links to GitHub release pages for full changelogs
~~~

---

## Recent Releases

### v0.4.2 -- 18 April 2026

**EffectBoundary, Claude Code Skills, and Spring HTTP Ergonomics**

This release introduces `EffectBoundary` for gradual Spring adoption of Free-monad programs, delivers a complete `hkj-spring` order-processing showcase demonstrating the boundary pattern end-to-end, ships a suite of six Claude Code skills providing in-editor guidance to HKJ adopters, extends the Effect Path return-value handlers with `@ResponseStatus` honouring and a canonical `@WebMvcTest` slice-test recipe, widens the `Effectful` capability interface for cross-path error recovery, and adds `EitherPath.bimap` and `Try.attempt(CheckedSupplier)` alongside targeted bug fixes.

- [EffectBoundary](spring/effect_boundary_integration.md) -- Gradual adoption boundary bridging `Free` programs into the Effect Path handler ecosystem via IO-target (production) and Id-target (test) interpreters. Spring integration adds `@EnableEffectBoundary`, `@Interpreter` component meta-annotation, `@EffectTest` slice, `FreePathReturnValueHandler`, and `ObservableEffectBoundary` (Micrometer), letting teams adopt effects module-by-module without rewriting existing code
- [Effect Boundary Showcase](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-spring/effect-example) -- Complete Spring Boot order-processing example demonstrating the full boundary pattern: three effect algebras (`OrderOp`, `InventoryOp`, `NotifyOp`) composed into programs, interpreters discovered as Spring beans via `@Interpreter`, `OrderService` building pure `Free<F, A>` programs, `OrderController` invoking `boundary.runIO()` with the existing `IOPathReturnValueHandler`, `TestBoundary` + `Id` pure tests running in milliseconds, full MockMvc integration tests, and `ObservableEffectBoundary` metrics exposed via actuator
- [Claude Code Skills Suite](tooling/claude_code_skills.md) -- Six Claude Code skills (`/hkj-guide`, `/hkj-optics`, `/hkj-effects`, `/hkj-bridge`, `/hkj-spring`, `/hkj-arch`) providing contextual guidance on Path selection, optics generation, Free monads and effect algebras, effects-optics bridging, Spring adoption ladder, and functional-core architecture; auto-triggered on keywords or invoked directly
- [@ResponseStatus Support](spring/spring_boot_integration.md) -- All nine Effect Path return-value handlers (`EitherPath`, `MaybePath`, `TryPath`, `ValidationPath`, `IOPath`, `CompletableFuturePath`, `VTaskPath`, `FreePath`, `VStreamPath`) now honour `@ResponseStatus` on handler methods via the new `SuccessStatusResolver`, with controller-class fallback and meta-annotation support; POSTs can return canonical `201`, DELETEs can return `204` with body suppressed
- [@WebMvcTest Slice Recipe](spring/spring_boot_integration.md) -- Canonical slice-test pattern using `@ImportAutoConfiguration({HkjAutoConfiguration, HkjJacksonAutoConfiguration, HkjWebMvcAutoConfiguration})` with `@MockitoBean`, covering `Right` → `200` and tagged-error `Left` → `404`
- [Effectful Capability Widening](effect/capabilities.md) -- `handleError`, `handleErrorWith`, and `guarantee` now live on the sealed `Effectful` interface; `handleErrorWith` accepts `Function<? super Throwable, ? extends Effectful<A>>` so `IOPath` and `VTaskPath` can cross-recover while preserving the receiver's concrete type
- [EitherPath.bimap](effect/path_either.md) -- Transform error and success values in a single call; equivalent to `.mapError(errorFn).map(successFn)` with laziness on the unused branch
- [Try.attempt](monads/try_monad.md) -- New `Try.attempt(CheckedSupplier)` entry point for Java APIs that throw checked exceptions (`Files.readString`, `Class.forName`, JDBC, reflection). `CheckedSupplier<T, X extends Exception>` in `hkj-api` declares `throws X` on `get()`, avoiding the lambda target-type ambiguity of `Try.of(Supplier)`
- `hkj-checker` registered on `testAnnotationProcessor` and every source-set annotation-processor classpath via the Gradle plugin; the Maven plugin defensively appends HKJ entries to user-supplied `testAnnotationProcessorPaths`, resolving `error: plug-in not found: HKJChecker` during test compilation
- `hkj.web.either.default-error-status` property now binds and takes effect (#490); legacy flat path `hkj.web.default-error-status` preserved as a backward-compatible alias, with end-to-end `@WebMvcTest` regression coverage
- Test coverage uplift across `FocusProcessor`, `FoldProcessor`, `ForComprehensionProcessor`, the optics processors, `EffectAlgebra`/`ComposeEffects`/Path processors, and `KindFieldAnalyser`, plus a new `@ExcludeFromJacocoGeneratedReport` utility

---

### v0.4.1 -- 8 April 2026

**Effect Handlers, Spring Observability, and Monad Transformer Enhancements**

This release introduces algebraic effect handlers with annotation-driven code generation, delivers a complete payment processing example with four interpretation modes, adds FreePath for-comprehension support, extends Spring Boot integration with VTask/VStream metrics and virtual thread health monitoring, adds `mapT` to all monad transformers, and includes significant bug fixes for stack safety, traverse performance, and resilience patterns.

- [`@EffectAlgebra`](effect/effect_handlers.md) - Annotation processor generating five classes per sealed interface: Kind marker + Witness, KindHelper, Functor (auto-detects `mapK` for CPS vs cast-through), Ops (smart constructors + `Bound` inner class), and abstract interpreter skeleton with exhaustive `switch` dispatch
- [`@ComposeEffects`](effect/effect_handlers.md#composing-effects) - Annotation processor generating composition infrastructure for 2-4 effect algebras: `Inject` factory methods via right-nested `EitherF`, composed `Functor`, `BoundSet` record, and `interpret()` bridge method
- [`@Handles`](effect/effect_handlers.md#interpreting-programs) - Compile-time validation that interpreter classes handle all operations in an effect algebra; reports missing handlers as errors and extra handlers as warnings
- [EitherF](monads/eitherf.md) - Sum type for composing effect algebras via right-nesting, with `Inject` for embedding operations, `Free.translate` for program transformation, and `Interpreters.combine()` for 2-4 effect dispatch
- [HandleError](effect/effect_handlers.md#error-recovery) -- `Free.HandleError` wraps sub-programs with typed error recovery; delegates to `MonadError.handleErrorWith` when available, silently ignored otherwise. Supports subclass matching via `Class<E>` token
- [ErrorOp](monads/eitherf.md) - Effect algebra for typed error raising within Free programs, with `ErrorOps.raise()` smart constructor and `Bound<E, G>` for composed effects
- [StateOp](effect/effect_handlers.md) - Optics-native state effect algebra with 6 operations (`View`, `Over`, `Assign`, `Preview`, `TraverseOver`, `GetState`), CPS for correct functor mapping, and `StateOpInterpreter`/`IOStateOpInterpreter` interpreters
- [ProgramAnalyser](effect/effect_handlers.md#program-analysis) - Static analysis of Free program trees: counts instructions (`Suspend`), recovery points (`HandleError`), parallel scopes (`Ap`), and opaque regions (`FlatMapped`). All counts are lower bounds.
- [Payment Processing](examples/payment_processing.md) - Complete worked example with 4 effect algebras, 13 interpreters across production (`IO`), testing (`Id`), quote (fee estimation), and audit (`WriterT`) modes; 12 tests and 6 tutorials
- [Effect Handlers Introduction](effect/effect_handlers_intro.md) - Motivational documentation covering the DI gap, programs-as-data, DOP connection, terminology bridge mapping FP concepts to Java equivalents, and when-to-use guidance
- [FreePath For-Comprehensions](functional/for_comprehension.md) - FreePath as the 10th path type in the `ForPath` system, with `from()`, `let()`, `focus()`, `par()`, `traverse()`, `sequence()`, `flatTraverse()`, and `yield()` steps
- [FreePath.attempt()](effect/path_free.md) - Captures outcome as `Either<Throwable, A>`, mapping success to `Right` and handling errors as `Left`
- [mapT](transformers/ch_intro.md) - New method on all 6 monad transformers (`EitherT`, `MaybeT`, `OptionalT`, `WriterT`, `ReaderT`, `StateT`) for transforming the outer monad layer without unwrapping. Custom AssertJ assertions added for `WriterT`, `ReaderT`, and `StateT`
- [VTask/VStream Metrics](spring/spring_boot_integration.md) - `HkjMetricsService` records success/error counts and execution duration for `VTaskPathReturnValueHandler` and element counts for `VStreamPathReturnValueHandler`; metrics exposed via `/actuator/hkj` endpoint
- [Virtual Thread Health Indicator](spring/spring_boot_integration.md) - Spring Boot health indicator monitoring virtual thread availability with configurable threshold
- [OpenRewrite Recipes](tooling/ch_intro.md) - `AddHandleErrorCaseRecipe` for missing `HandleError`/`Ap` switch cases, `ConvertRawFreeToFreePathRecipe` for `FreePath` migration, `DetectInjectBoilerplateRecipe` for `@ComposeEffects` adoption
- [FList](monads/ch_intro.md) - Lightweight immutable cons-list replacing O(n^2) `LinkedList` copy in `ListTraverse`, `StreamTraverse`, and `VStreamTraverse` with O(n) cons accumulation
- Free.foldMap stack safety: added trampolining to prevent `StackOverflowError` on deep program chains
- FreeAp.foldMap stack safety: added trampolining for deep applicative trees
- CircuitBreaker: reset failure count on success in `HALF_OPEN` state
- ConstBifunctor: fix NPE in `second()` by applying function to second element
- IO.raceIO: fix `ClassCastException` in `firstVTaskSuccess` for checked exceptions
- Lazy: add reentrant-call detection to prevent infinite recursion
- Bulkhead: add permit-release guard to prevent negative permits
- VStreamPar.merge: join background producer thread on close to prevent thread leak
- VStreamThrottle: replace dual `AtomicLong` with `AtomicReference<WindowState>` CAS loop
- Free `F` parameter tightened from `WitnessArity<?>` to `WitnessArity<TypeArity.Unary>` across the entire hierarchy, eliminating raw type usage
- Additional edge case tests for `NavigatorClassGenerator`, `FocusProcessor`, and `ForPathStepGenerator`
- [JMH Benchmarks](benchmarks.md) -- 7 new benchmarks for EitherF dispatch, Free.translate, HandleError overhead, ProgramAnalyser traversal, and program construction cost

---

### v0.4.0 -- 22 March 2026

**SPI-Aware Path Widening, Expanded Plugin Ecosystem, and Focus DSL Restructure**

This release introduces SPI-aware path widening for the Focus DSL, allowing automatic `AffinePath` and `TraversalPath` generation based on container cardinality, expands the `TraversableGenerator` plugin ecosystem to 23 generators across 6 library families, adds `Traversal.asFold()` for read-only monoidal aggregation, restructures the Focus DSL documentation into dedicated pages, and delivers comprehensive test coverage and Javadoc quality improvements across processor modules.

- [SPI-Aware Path Widening](optics/focus_navigation.md) -- Automatic path type inference based on container cardinality: `ZERO_OR_ONE` produces `AffinePath`, `ZERO_OR_MORE` produces `TraversalPath`, eliminating manual `.each()` and `.some()` calls in generated navigators
- [Cardinality-Based Widening](optics/focus_containers.md) -- `TraversableGenerator` SPI extended with `Cardinality` enum, priority system (`PRIORITY_FALLBACK`, `PRIORITY_DEFAULT`, `PRIORITY_OVERRIDE`), `widenCollections` opt-in attribute, and wildcard type resolution for `? extends T`, `? super T`, and bare `?`
- [Nested Container Widening](optics/focus_containers.md) -- Compound types like `Optional<List<String>>` resolve correctly through recursive cardinality analysis, with navigator field collision detection
- [Generator Plugin Ecosystem](tooling/generator_plugins.md) -- 23 `TraversableGenerator` implementations across 6 library families: base JDK (Array, List, Set, Optional, MapValue), Apache Commons Collections4 (HashBag, UnmodifiableList), Eclipse Collections (ImmutableBag, MutableBag, ImmutableList, MutableList, ImmutableSet, MutableSet, ImmutableSortedSet, MutableSortedSet), Google Guava (ImmutableList, ImmutableSet), Vavr (List, Set), and HKJ native (Either, Maybe, Try, Validated)
- [Traversal.asFold()](optics/folds.md) -- Conversion from any `Traversal` to a read-only `Fold` for monoidal aggregation, existence checks, and length counting via new `ConstForFold` applicative functor
- [AffinePath](optics/focus_navigation.md) -- New `AffinePath<S, A>` for zero-or-one navigation in Focus DSL, with `Affine` optic interface supporting `getOrModify` and `set`
- [Portfolio Risk Analysis](examples/examples_portfolio_risk.md) -- Capstone example demonstrating container navigation, SPI widening, and nested optics composition
- Focus DSL documentation restructured into dedicated pages: [Containers](optics/focus_containers.md), [Navigation](optics/focus_navigation.md), [Effects](optics/focus_effects.md), and [Reference](optics/focus_reference.md)
- [Tutorial 19: Navigator Generation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial19_NavigatorGeneration.java) -- 7 exercises on annotation-driven navigator code generation
- [Tutorial 20: Container Navigation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial20_ContainerNavigation.java) -- 4 exercises on SPI-aware container type navigation
- Automated SPI service declarations via Avaje SPI processor for plugin discovery

---

### [v0.3.7](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.7) -- 15 March 2026

**WriterT Transformer, For-Comprehension Power-Ups, Build Tooling, and Spring Virtual Thread Support**

This release introduces the `WriterT` monad transformer with MTL-style capability interfaces, enriches for-comprehensions with parallel composition, traversal operations, and optics integration, adds compile-time Path type checking via the new `hkj-checker` javac plugin, delivers one-line project setup through build tool plugins (`hkj-gradle-plugin` and `hkj-maven-plugin`), and extends Spring MVC with virtual-thread-native return value handlers for `VTaskPath` and `VStreamPath`.

- [WriterT](transformers/writert_transformer.md) -- Monad transformer for output accumulation across effect boundaries, wrapping `Kind<F, Pair<A, W>>` with automatic Monoid-based combining during `flatMap` chains
- [MonadWriter](transformers/mtl_writer.md) -- MTL-style capability interface with `tell`, `listen`, `pass`, `listens`, and `censor` for output accumulation
- [MonadReader](transformers/mtl_reader.md) -- MTL-style capability interface with `ask`, `local`, `reader`, and `asks` for shared environment access
- [MonadState](transformers/mtl_state.md) -- MTL-style capability interface with `get`, `put`, `modify`, and `gets` for stateful computation
- [par()](functional/for_par.md) -- Parallel/applicative composition for `For` and `ForPath` comprehensions; true concurrency on `VTask`, intent-documenting on sequential monads
- [traverse/sequence/flatTraverse](functional/for_traverse.md) -- Bulk effectful operations within comprehension chains: apply an effectful function across a structure, flip `Structure<Effect<A>>` to `Effect<Structure<A>>`, or traverse-and-flatten in one step
- [For-Comprehension Optics Integration](functional/for_optics.md) -- `through(Iso)` for type-safe value conversion in `For`; `traverseOver()`, `modifyThrough()`, `modifyVia()`, and `updateVia()` for optics-driven state operations in `ForState`
- [Fold Combinators](optics/folds.md) -- `Fold.plus()`, `Fold.empty()`, and `Fold.sum()` forming a monoid on folds for multi-path data extraction
- [Compile-Time Path Checks](tooling/compile_checks.md) -- `hkj-checker` javac plugin detecting Path type mismatches at compile time for `via`, `then`, `zipWith`, `zipWith3`, `recoverWith`, and `orElse`
- [Build Plugins](tooling/gradle_plugin.md) -- `hkj-gradle-plugin` (one-line Gradle setup) and `hkj-maven-plugin` (Maven lifecycle extension) that auto-configure HKJ dependencies, `--enable-preview` flags, compile-time checking, and optional Spring Boot integration
- [hkj-bom](tooling/gradle_plugin.md) -- Bill of Materials POM for version-aligned dependency management across all HKJ modules in both Gradle and Maven
- [Diagnostics](tooling/diagnostics.md) -- `hkjDiagnostics` Gradle task and `mvn hkj:diagnostics` goal reporting active dependencies, compiler arguments, and checks
- [VTaskPath Spring MVC](spring/spring_boot_integration.md) -- `VTaskPathReturnValueHandler` converting controller return values to async `DeferredResult` responses on virtual threads
- [VStreamPath SSE](spring/spring_boot_integration.md) -- `VStreamPathReturnValueHandler` converting controller return values to Server-Sent Events with pull-based backpressure, no Reactor required
- Dependency updates: Gradle 9.4.0, JUnit 6.0.3, Jackson 3.1.0, Spring Boot 4.0.3, jOOQ 3.20.11, javapoet 0.12.0, and others
- Faster `FunctionValidator` and `KindValidator` with simplified implementation
- Test reliability improvements: replaced `Thread.sleep` with Awaitility across test suite

---

### [v0.3.6](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.6) -- 6 March 2026

**VStream Lazy Streaming, Resilience Patterns, and ForState Comprehensions**

This release introduces `VStream`, a lazy pull-based streaming type built on virtual threads with full HKT integration, adds four core resilience patterns (Circuit Breaker, Bulkhead, Retry, Saga) with Effect Path integration, extends `ForState` with filtering and pattern matching, and delivers a Market Data Pipeline capstone example.

- [VStream](monads/vstream.md) -- Lazy pull-based streaming on virtual threads with `Step` protocol (Emit/Done/Skip), factory methods (`of`, `range`, `iterate`, `generate`, `unfold`), transformation combinators, and error recovery
- [VStream HKT Integration](monads/vstream_hkt.md) -- `VStreamKind` witness type with Functor, Applicative, Monad, Foldable, Traverse, and Alternative type class instances
- [VStream Parallel Operations](monads/vstream_parallel.md) -- `VStreamPar` with `parEvalMap`, `parEvalMapUnordered`, `parEvalFlatMap`, `merge`, `parCollect`, and chunking combinators
- [VStream Resources](monads/vstream_resources.md) -- `bracket`/`onFinalize` resource lifecycle management and `VStreamReactive` bidirectional `Flow.Publisher` bridge with backpressure
- [VStreamPath](effect/path_vstream.md) -- Effect Path bridge with factory methods, `PathOps` operations, terminal operations bridging to `VTaskPath`, and optics focus bridge
- [Circuit Breaker](resilience/circuit_breaker.md) -- State machine (Closed/Open/HalfOpen) with configurable failure thresholds and recovery timeouts
- [Bulkhead](resilience/bulkhead.md) -- Concurrency limiting for isolating resource access
- [Retry](resilience/retry.md) -- Configurable retry policies with fixed delay, exponential backoff, and jitter
- [Saga](resilience/saga.md) -- Distributed transaction compensation with ordered rollback
- [Combined Resilience](resilience/combined.md) -- Composing multiple resilience patterns and Path API ergonomic methods: `retry()`, `circuitBreaker()`, `bulkhead()`, `timeout()`
- [ForState](functional/forstate_comprehension.md) -- Filtering (`when`), pattern matching (`matchThen`), traversals, zoom, and `toState()` bridge from For comprehensions at all arities (1–12)
- [traverseWith()](optics/focus_dsl.md) -- Parallel effectful optics traversal for `FocusPath`, `AffinePath`, and `TraversalPath` via `VTaskPath` and `StructuredTaskScope`
- [Market Data Pipeline](examples/examples_market_data.md) -- 14-feature capstone example demonstrating concurrent feed merging, parallel enrichment, risk assessment, windowed aggregation, anomaly detection, and circuit breaker failover
- Refreshed [Monads chapter](monads/ch_intro.md) with problem-first structure, real-world analogies, and consistent formatting
- `FunctionValidator` optimisation: deferred error-message construction avoids `String` allocation on the happy path; fixed Gradle benchmark commands (`-Pincludes`)
- Javadoc generation fix to include annotation-processor-generated sources (`Tuple2`–`Tuple12`, `MonadicSteps`, etc.)
- [JMH benchmarks](benchmarks.md) for VStream construction, combinators, terminals, and parallel operations

---

### [v0.3.5](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.5) -- 15 February 2026

**Extended For-Comprehensions, VTask API Refinement, and Documentation Restructure**

This release extends for-comprehension arity to 12, simplifies the VTask API, adds Maybe-to-Either conversions, upgrades to JUnit 6, and delivers a comprehensive documentation restructure with quickstart guides, cheat sheets, migration cookbooks, and railway diagrams.

- [For-Comprehension Arity 12](functional/for_comprehension.md) -- `For` and `ForPath` now support up to 12 monadic bindings (previously 5), with generated `Tuple9`–`Tuple12` and `Function9`–`Function12`
- [VTask API](monads/vtask_monad.md) -- `VTask.run()` no longer declares `throws Throwable`; checked exceptions are wrapped in `VTaskExecutionException`
- [Maybe.toEither](monads/maybe_monad.md) -- New `toEither(L)` and `toEither(Supplier<L>)` conversion methods for seamless `Maybe` → `Either` transitions
- [Quickstart](quickstart.md) -- New getting-started guide with Gradle and Maven setup including `--enable-preview` configuration
- [Cheat Sheet](cheatsheet.md) -- Quick-reference for Path types, operators, escape hatches, and type conversions
- [Stack Archetypes](transformers/archetypes.md) -- 7 named transformer stack archetypes with colour-coded railway diagrams
- [Migration Cookbook](migration_cookbook.md) -- 6 recipes for migrating from try/catch, Optional chains, null checks, CompletableFuture, validation, and nested records
- [Compiler Error Guide](compiler_errors.md) -- Solutions for the 5 most common Effect Path compiler errors
- [Effects-Optics Capstone](capstone_focus_effect.md) -- Combined effects and optics pipeline example
- Railway operator diagrams for all 8 Effect Path operators and for EitherT, MaybeT, OptionalT transformers
- JUnit 6.0.2 upgrade (from 5.14.1) across all test modules
- Golden file test infrastructure with automated sync verification and pitest mutation coverage improvements

---

### [v0.3.4](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.4) -- 31 January 2026

**External Type Optics and Examples Gallery**

This release introduces powerful optics generation for external types you cannot modify, plus a new Examples Gallery chapter documenting all runnable examples.

- [@ImportOptics](optics/importing_optics.md) -- Generate optics for JDK classes and third-party library types via auto-detection of withers and accessors
- [Spec Interfaces](optics/optics_spec_interfaces.md) -- Fine-grained control over external type optics with `OpticsSpec<S>` for complex types like Jackson's `JsonNode`
- [@ThroughField Auto-Detection](optics/copy_strategies.md#throughfield-auto-detection) -- Automatic traversal type detection for `List`, `Set`, `Optional`, arrays, and `Map` fields
- [Examples Gallery](examples/ch_intro.md) -- New chapter with categorised, runnable examples demonstrating core types, transformers, Effect Path API, and optics
- Comprehensive hkj-processor testing improvements with enhanced coverage

---

### [v0.3.3](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.3) -- 24 January 2026

**Structured Concurrency, Atomic Optics, and Enhanced Examples**

This release introduces structured concurrency primitives, atomic coupled-field updates, and a comprehensive Order Workflow example demonstrating these patterns.

- [Structured Concurrency](monads/vtask_scope.md) -- `Scope` for parallel operations with `allSucceed()`, `anySucceed()`, `firstComplete()`, and `accumulating()` joiners
- [Resource Management](monads/vtask_resource.md) -- `Resource` for bracket-pattern cleanup with guaranteed release
- [Coupled Fields](optics/coupled_fields.md) -- `Lens.paired` for atomic multi-field updates bypassing invalid intermediate states
- [Order Workflow Overview](hkts/order-walkthrough.md) -- Reorganised documentation with focused sub-pages
- [Concurrency and Scale](hkts/order-concurrency.md) -- Context, Scope, Resource, VTaskPath patterns in practice
- `EnhancedOrderWorkflow` -- Full workflow demonstrating Context, Scope, Resource, VTaskPath
- `OrderContext` -- ScopedValue keys for trace ID, tenant isolation, and deadline enforcement
- [Scope & Resource Tutorials](tutorials/concurrency/scope_resource_journey.md) -- 18 exercises on concurrency patterns
- [Release History](release-history.md) -- New page documenting all releases

---

### [v0.3.2](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.2) -- 17 January 2026

**Virtual Thread Concurrency with VTask**

This release introduces `VTask<A>`, a lazy computation effect leveraging Java 25's virtual threads for lightweight concurrent programming.

- [VTask](monads/vtask_monad.md) -- Lazy computation effect for virtual thread execution
- [Structured Concurrency](monads/vtask_scope.md) -- `Scope` for parallel operations with `allSucceed()`, `anySucceed()`, and `accumulating()` patterns
- [Resource Management](monads/vtask_resource.md) -- `Resource` for bracket-pattern cleanup guarantees
- [VTaskPath](effect/path_vtask.md) -- Integration with the Effect Path API
- [Concurrency and Scale](hkts/order-concurrency.md) -- Practical patterns in the Order Workflow example
- `Par` parallel combinators for concurrent execution
- Comprehensive benchmarks comparing virtual vs. platform threads

---

### [v0.3.1](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.1) -- 15 January 2026

**Static Analysis Utilities**

This release adds utilities for statically analysing Free Applicative and Selective functors without execution.

- [Choosing Abstraction Levels](functional/abstraction_levels.md) -- Guide to selecting Functor, Applicative, Selective, or Monad
- [Free Applicative](monads/free_applicative.md) -- Static analysis with `FreeApAnalyzer`
- [Selective](functional/selective.md) -- Conditional effects with `SelectiveAnalyzer`
- Tutorial 11 on static analysis patterns

---

### [v0.3.0](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.0) -- 4 January 2026

**Effect Path Focus Integration**

Major release introducing the unified Effect Path API and Focus DSL integration. Requires Java 25 baseline.

- [Effect Path Overview](effect/effect_path_overview.md) -- The railway model for composable effects
- [Path Types](effect/path_types.md) -- 17+ composable Path types including `EitherPath`, `MaybePath`, `IOPath`
- [Focus DSL](optics/focus_dsl.md) -- Type-safe optics with annotation-driven generation
- [Focus-Effect Integration](effect/focus_integration.md) -- Bridge between optics and effects
- [ForPath Comprehension](effect/forpath_comprehension.md) -- For-comprehension syntax for Path types
- [Spring Boot Integration](spring/spring_boot_integration.md) -- Spring Boot 4.0.1+ support
- [Order Workflow](hkts/order-walkthrough.md) -- Complete example demonstrating Effect Path patterns

---

## Earlier Releases

### [v0.2.8](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.8) -- 26 December 2025

- Introduced `ForPath` for Path-native for-comprehension syntax
- Complete Spring Boot 4.0.1 migration of `hkj-spring` from `EitherT` to Effect Path API
- New return value handlers for Spring integration

### [v0.2.7](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.7) -- 20 December 2025

- Effect Contexts: `ErrorContext`, `OptionalContext`, `ConfigContext`, `MutableContext`
- Bridge API enabling seamless transitions between optics and effects

### [v0.2.6](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.6) -- 19 December 2025

- New Effect Path API with 17+ Path types
- Retry policies and parallel execution utilities
- Kind field support in Focus DSL

### [v0.2.5](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.5) -- 9 December 2025

- Annotation-driven Focus DSL for fluent optics composition
- Free Applicative and Coyoneda functors
- Natural Transformation support

### [v0.2.4](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.4) -- 3 December 2025

- Affine optic for focusing on zero or one element
- `ForTraversal`, `ForState`, and `ForIndexed` comprehension builders
- For-comprehension and optics integration

### [v0.2.3](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.3) -- 1 December 2025

- Cross-optic composition (Lens + Prism → Traversal)
- Experimental Spring Boot starter
- Custom target package support for annotation processor

### [v0.2.2](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.2) -- 29 November 2025

- Java 25 baseline
- Experimental `hkj-spring` module
- Validation helpers and ArchUnit architecture tests
- Thread-safety fix in Lazy memoisation

### [v0.2.1](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.1) -- 23 November 2025

- 7-part Core Types tutorial series
- 9-part Optics tutorial (~150 minutes total)
- Versioned documentation system
- Property-based testing infrastructure
- JMH benchmarking framework

### [v0.2.0](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.0) -- 21 November 2025

- Six new optic types: Fold, Getter, Setter, and indexed variants
- FreeMonad for DSL construction
- Trampoline for stack-safe recursion
- Const Functor
- Enhanced Monoid with new methods
- Alternative type class

### [v0.1.9](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.9) -- 14 November 2025

- Selective type class for conditional effects
- Enhanced optics with `modifyWhen()` and `modifyBranch()`
- Bifunctor for Either, Tuple2, Validated, and Writer
- Higher-kinded Stream support

### [v0.1.8](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.8) -- 9 September 2025

- Profunctor type class
- Profunctor operations in universal Optic interface

### [v0.1.7](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.7) -- 29 August 2025

- Generated `with*` helper methods for records via `@GenerateLenses`
- `Traversals.forMap()` for key-specific Map operations
- Semigroup interface with Monoid extending it
- Validated Applicative with error accumulation

### [v0.1.6](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.6) -- 14 July 2025

- Optics introduction: Lens, Iso, Prism, and Traversals
- Annotation-based optics generation
- Plugin architecture for extending Traversal types
- Modular release structure

### [v0.1.5](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.5) -- 12 June 2025

- For comprehension with generators, bindings, guards, and yield
- Tuple1-5 and Function5 support

### [v0.1.4](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.4) -- 5 June 2025

- Validated Monad
- Standardised widen/narrow pattern for KindHelpers (breaking change)

### [v0.1.3](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.3) -- 31 May 2025

- First Maven Central publication
- 12 monads, 5 transformers
- Comprehensive documentation

### [v0.1.0](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.0) -- 3 May 2025

- Initial release
- Core types: Either, Try, CompletableFuture, IO, Lazy, Reader, State, Writer
- EitherT transformer

---

~~~admonish tip title="See Also"
- [GitHub Releases](https://github.com/higher-kinded-j/higher-kinded-j/releases) -- Full changelogs and assets
- [Contributing](CONTRIBUTING.md) -- How to contribute to Higher-Kinded-J
~~~

---

**Previous:** [Glossary](glossary.md)
**Next:** [Benchmarks & Performance](benchmarks.md)
