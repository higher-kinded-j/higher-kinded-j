# Effect Path API
## _Navigating Computational Territory_

> *"A map is not the territory it represents, but, if correct, it has a similar
> structure to the territory, which accounts for its usefulness."*
>
> — Alfred Korzybski, *Science and Sanity*

---

Every Java application navigates territory that doesn't appear on any class diagram: the landscape of *what might go wrong*. A database connection that refuses to connect. A user ID that points to nobody. A file that was there yesterday. A validation rule that nobody told you about.

Traditional Java handles this territory with a patchwork of approaches: nulls here, exceptions there, `Optional` when someone remembered, raw booleans when they didn't. Each approach speaks a different dialect. None compose cleanly with the others.

The Effect Path API provides a unified map for this territory.

Rather than learning separate idioms for absence (`Optional`), failure (`try-catch`), typed errors (`Either`), and deferred effects (`CompletableFuture`), you work with **Path types**: thin, composable wrappers that share a common vocabulary. The same `map`, `via`, and `recover` operations work regardless of what kind of effect you're handling. The underlying complexity remains (it must), but the Path contains it.

If you've used the Focus DSL from the optics chapters, the patterns will feel familiar. Where FocusPath navigates through *data structures*, EffectPath navigates through *computational effects*. Both use `via` for composition. Both provide fluent, chainable operations. The territory differs; the cartography rhymes. And when you need to cross between territories, extracting structured data into effect pipelines or drilling into effect results with optics, the bridge API connects both worlds seamlessly.

---

~~~admonish info title="In This Chapter"
**[Quickstart](quickstart.md)** - Three runnable examples showing `MaybePath`, `EitherPath`, and `ForPath` in about 150 lines. The fastest path from zero to working code.

**Core Paths** - The everyday API for Effect Path adopters:
- **[Core Paths Overview](effect_path_overview.md)** - The problem that Path types solve, the railway model of effect composition, and your first taste of the API.
- **[Migration Cookbook](migration_cookbook.md)** - Pattern-by-pattern translations from imperative Java to Effect Path. Recipes for `try/catch`, nullable lookups, `CompletableFuture`, validation, and nested record updates.
- **[Path Types Overview](path_types.md)** - A decision tree across the six core path types, with a short primer on when to reach for each.
- **MaybePath, EitherPath, TryPath, ValidationPath, IOPath, VTaskPath** - One page per path type: operators, idioms, and worked examples.
- **[Composition Patterns](composition.md)** - Sequential chains, independent combination, parallel execution, debugging with `peek`.
- **[ForPath Comprehension](forpath_comprehension.md)** (with Examples) - The for-comprehension designed specifically for Path types. Parallel and Traverse variants live under Advanced Paths.

**Optics Integration** - Bridging Effect Paths with the Optics chapter:
- **[Focus-Effect Integration](focus_integration.md)** - Converting between FocusPath / AffinePath / TraversalPath and Effect Path types; using `focus()` to navigate within effect contexts.
- **[Capstone: Effects Meet Optics](capstone_focus_effect.md)** - A complete before/after example combining effects and optics in a single pipeline.

**Advanced Paths** - Free monads, algebraic effects, streaming, and resilience:
- **[Advanced Paths Overview](advanced_topics.md)** - Stack-safe recursion, DSL building with Free structures, resource management, parallel execution.
- **IdPath, OptionalPath, GenericPath, TrampolinePath, FreePath, FreeApPath, VStreamPath** - Advanced and specialised path types.
- **[ForPath Parallel Composition](forpath_par.md)** and **[ForPath Traverse](forpath_traverse.md)** - Concurrency primitives and typeclass-level traversal for ForPath comprehensions.
- **[Advanced Effects](advanced_effects.md)** - Reader, State, and Writer paths for environment access, stateful computation, and logging accumulation.
- **[Effect Contexts](effect_contexts.md)** - Context-scoped effects including ErrorContext, ConfigContext, RequestContext, and SecurityContext.
- **[Effect Handlers](effect_handlers_intro.md)** - Algebraic-effect-style programming via Free monads and interpreters.
- **[Patterns and Recipes](patterns.md)** - Real-world patterns distilled from production code: validation pipelines, service orchestration, fallback chains, resilience with retry.
- **[Resilience Patterns](../resilience/ch_intro.md)** - Retry, circuit breaker, bulkhead, saga, and the `ResilienceBuilder`.

**Reference** - Lookup material:
- **[Capability Interfaces](capabilities.md)** - The hierarchy of powers that Path types possess: Composable, Combinable, Chainable, Recoverable, Effectful, and Accumulating.
- **[Type Conversions](conversions.md)** - Moving between Path types as your needs change.
- **[Common Compiler Errors](compiler_errors.md)** - The five most common compiler errors with full messages, minimal triggers, and fixes.
- **[Production Readiness](production_readiness.md)** - Stack traces, allocation overhead, and stack safety. Honest answers for senior engineers.
~~~

~~~admonish note title="Compile-Time Safety"
The HKJ Gradle plugin can detect Path type mismatches at compile time,
catching errors that would otherwise surface as runtime exceptions.
See [Compile-Time Checks](../tooling/compile_checks.md) for setup.
~~~

~~~admonish example title="See Example Code"
**Core Paths:**
- [BasicPathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/BasicPathExample.java) - Creating and transforming paths
- [ChainedComputationsExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ChainedComputationsExample.java) - Fluent chaining patterns
- [ErrorHandlingExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ErrorHandlingExample.java) - Recovery and error handling
- [ServiceLayerExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ServiceLayerExample.java) - Real-world service patterns
- [PathOpsExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/PathOpsExample.java) - Operator reference across path types
- [LazyPathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/LazyPathExample.java) - Deferred, memoised computations
- [CollectionPathsExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/CollectionPathsExample.java) - List and Stream effects
- [AccumulatingValidationExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/AccumulatingValidationExample.java) - ValidationPath error accumulation
- [ValidationPipelineExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ValidationPipelineExample.java) - End-to-end validation pipelines
- [EitherPathBimapExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/EitherPathBimapExample.java) - Bifunctor operations on EitherPath
- [VTaskPathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/VTaskPathExample.java) - Virtual-thread concurrency basics
- [CompletableFuturePathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/CompletableFuturePathExample.java) - Async operations with CompletableFuturePath
- [ForPathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ForPathExample.java) - ForPath comprehensions across path types

**Advanced Paths:**
- [AdvancedEffectsExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/AdvancedEffectsExample.java) - Reader, State, and Writer paths
- [TrampolinePathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/TrampolinePathExample.java) - Stack-safe recursion
- [FreePathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/FreePathExample.java) - DSL building and interpretation
- [VStreamPathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/VStreamPathExample.java) - Lazy, pull-based streaming on virtual threads
- [VStreamParallelExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/VStreamParallelExample.java) - Parallel VStream operations
- [VTaskResourceExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/VTaskResourceExample.java) - Bracket pattern for VTask-managed resources
- [ResourceManagementExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ResourceManagementExample.java) - bracket, withResource, guarantee
- [ScopeExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ScopeExample.java) - Structured concurrency scopes
- [ParallelExecutionExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ParallelExecutionExample.java) - parZipWith, parSequence, race
- [EffectContextExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/EffectContextExample.java) - Context-scoped effects
- [EffectfulPolymorphismExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/EffectfulPolymorphismExample.java) - Capability-based polymorphism across path types
- [ArchetypeExamples.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ArchetypeExamples.java) - Named transformer stack archetypes
- [ResilienceExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ResilienceExample.java) - RetryPolicy and backoff patterns

**Reference:**
- [CrossPathConversionsExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/CrossPathConversionsExample.java) - Converting between Path types
~~~

~~~admonish tip title="Hands-On Tutorial"
For a guided, exercise-driven walkthrough of the Effect Path API, see the [Effect API Tutorial](../tutorials/effect/effect_journey.md). Each section pairs a chapter concept with a runnable Java exercise and a worked solution.
~~~

## Chapter Contents

1. [Quickstart](quickstart.md) - Your first three runnable Effect Path examples
2. [Core Paths](effect_path_overview.md) - The six everyday path types, composition, and basic ForPath comprehensions
3. [Optics Integration](focus_integration.md) - Bridging the Effect Path API with the Optics chapter
4. [Advanced Paths](advanced_topics.md) - Free monads, effect handlers, contexts, parallel/traverse ForPath, and resilience
5. [Reference](capabilities.md) - Capability typeclasses, type conversions, compiler errors, and production readiness

---

**Next:** [Quickstart](quickstart.md)
