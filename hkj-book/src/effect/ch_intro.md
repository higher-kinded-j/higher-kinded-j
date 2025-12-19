# Effect Path API: Navigating Computational Territory

> *"A map is not the territory it represents, but, if correct, it has a similar
> structure to the territory, which accounts for its usefulness."*
>
> — Alfred Korzybski, *Science and Sanity*

---

Every Java application navigates territory that doesn't appear on any class diagram: the landscape of *what might go wrong*. A database connection that refuses to connect. A user ID that points to nobody. A file that was there yesterday. A validation rule that nobody told you about.

Traditional Java handles this territory with a patchwork of approaches: nulls here, exceptions there, `Optional` when someone remembered, raw booleans when they didn't. Each approach speaks a different dialect. None compose cleanly with the others.

The Effect Path API provides a unified map for this territory.

Rather than learning separate idioms for absence (`Optional`), failure (`try-catch`), typed errors (`Either`), and deferred effects (`CompletableFuture`), you work with **Path types**: thin, composable wrappers that share a common vocabulary. The same `map`, `via`, and `recover` operations work regardless of what kind of effect you're handling. The underlying complexity remains (it must), but the Path contains it.

If you've used the Focus DSL from the optics chapters, the patterns will feel familiar. Where FocusPath navigates through *data structures*, EffectPath navigates through *computational effects*. Both use `via` for composition. Both provide fluent, chainable operations. The territory differs; the cartography rhymes.

---

~~~admonish info title="In This Chapter"
- **[Effect Path Overview](effect_path_overview.md)** – The problem that Path types solve, the railway model of effect composition, and your first taste of the API.

- **[Capability Interfaces](capabilities.md)** – The hierarchy of powers that Path types possess: Composable, Combinable, Chainable, Recoverable, Effectful, and Accumulating. What each unlocks, and why the layering matters.

- **[Path Types](path_types.md)** – The full arsenal: `MaybePath`, `EitherPath`, `TryPath`, `IOPath`, `ValidationPath`, `TrampolinePath`, `FreePath`, `FreeApPath`, and more. When to reach for each, and what distinguishes them.

- **[Composition Patterns](composition.md)** – Sequential chains, independent combination, parallel execution, debugging with `peek`, and the art of mixing composition styles.

- **[Type Conversions](conversions.md)** – Moving between Path types as your needs change. The bridges between `Maybe` and `Either`, between `Try` and `Validation`, and the rules that govern safe passage.

- **[Patterns and Recipes](patterns.md)** – Real-world patterns distilled from production code: validation pipelines, service orchestration, fallback chains, resilience with retry, and the pitfalls that await the unwary.

- **[Advanced Effects](advanced_effects.md)** – Reader, State, and Writer paths for environment access, stateful computation, and logging accumulation.

- **[Advanced Topics](advanced_topics.md)** – Stack-safe recursion, DSL building with Free structures, resource management, parallel execution, and resilience patterns.
~~~

~~~admonish example title="See Example Code"
**Core Patterns:**
- [BasicPathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/BasicPathExample.java) - Creating and transforming paths
- [ChainedComputationsExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ChainedComputationsExample.java) - Fluent chaining patterns
- [ErrorHandlingExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ErrorHandlingExample.java) - Recovery and error handling
- [ServiceLayerExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ServiceLayerExample.java) - Real-world service patterns

**Advanced Effects:**
- [AdvancedEffectsExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/AdvancedEffectsExample.java) - Reader, State, and Writer paths
- [LazyPathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/LazyPathExample.java) - Deferred, memoised computations
- [CompletableFuturePathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/CompletableFuturePathExample.java) - Async operations
- [CollectionPathsExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/CollectionPathsExample.java) - List and Stream effects

**Stack-Safety, Resources, Parallelism & Resilience:**
- [TrampolinePathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/TrampolinePathExample.java) - Stack-safe recursion
- [FreePathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/FreePathExample.java) - DSL building and interpretation
- [ResourceManagementExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ResourceManagementExample.java) - bracket, withResource, guarantee
- [ParallelExecutionExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ParallelExecutionExample.java) - parZipWith, parSequence, race
- [ResilienceExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ResilienceExample.java) - RetryPolicy and backoff patterns
~~~

## Chapter Contents

1. [Effect Path Overview](effect_path_overview.md) - The problem, the model, the first steps
2. [Capability Interfaces](capabilities.md) - The interface hierarchy powering composition
3. [Path Types](path_types.md) - Detailed coverage of each Path type
4. [Composition Patterns](composition.md) - Chaining, combining, parallel execution, and debugging
5. [Type Conversions](conversions.md) - Moving between different Path types
6. [Patterns and Recipes](patterns.md) - Real-world patterns, resilience, and hard-won wisdom
7. [Advanced Effects](advanced_effects.md) - Reader, State, and Writer patterns
8. [Advanced Topics](advanced_topics.md) - Stack-safety, DSLs, resources, parallelism, resilience

---

**Next:** [Effect Path Overview](effect_path_overview.md)
