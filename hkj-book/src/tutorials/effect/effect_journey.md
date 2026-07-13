# Effect API Journey

**Estimated Duration**: ~70 minutes (two sub-journeys, with the new diagnostic exercises) | **Exercises**: 17 (15 graded + 2 diagnostic)

~~~admonish info title="The Primary API"
The Effect Path API is the **recommended user-facing API** for Higher-Kinded-J. This journey teaches us to be productive with functional effects without needing deep HKT knowledge.
~~~

~~~admonish tip title="Where This Fits in the Bigger Picture"
The opening `repo.find(id).toEitherPath()` in [One Line, Six Layers](../../hkts/one_line_six_layers.md) is precisely what this journey teaches. The Path types are the fluent wrappers that let us write the rest of that expression without ever opening the [Foundations chapter](../../hkts/foundations_intro.md). When we want to know what is happening underneath, [Lifting the Hood](../../hkts/lifting_the_hood.md) traces a single `flatMap` call end-to-end.
~~~

~~~admonish note title="One-page Cheatsheet"
A single-page reference covering every Path factory, the four core operations, ForPath shape, the three Effect Contexts, the `@GeneratePathBridge` pattern, and the boundary rule is at [Effect API Cheatsheet](effect_cheatsheet.md). Useful when revising or applying these patterns in our own code.
~~~

## What We'll Learn

The Effect API journey covers the complete Effect Path system:

### Part 1: Fundamentals (~35 min, 9 exercises)
- **Creating Paths**: `Path.just()`, `Path.nothing()`, `Path.maybe()`, `Path.right()`, `Path.left()`, `Path.either()`, `Path.tryOf()`, `Path.io()`
- **Transforming Values**: `map` to transform success values; errors pass through unchanged
- **Chaining Operations**: `via` (a.k.a. `flatMap`) for dependent computations; short-circuit on failure
- **Error Recovery**: `recover`, `recoverWith`, `orElse`, `mapError`
- **Combining Paths**: `zipWith` to combine independent computations
- **A real-world workflow**: putting it all together
- **Diagnostic**: `via` vs `map` when the function returns a Path

### Part 2: Advanced (~35 min, 8 exercises)
- **ForPath Comprehensions**: readable multi-step workflows with for-comprehension syntax (and tuple-binding semantics)
- **Effect Contexts**: `ErrorContext` (typed error + IO), `ConfigContext` (Reader/DI), `MutableContext` (workflow-local state)
- **Service Integration**: the `@GeneratePathBridge` pattern, by hand
- **Focus-Effect Integration**: `focus(...)` to navigate inside an Effect Path
- **Diagnostic**: build the workflow first, run once at the boundary

## Why Effect API First?

The Effect Path API is designed to be the primary interface for most users:

1. **Ergonomic**: fluent, chainable, reads naturally in Java
2. **Type-safe**: compile-time guarantees about effect handling
3. **Practical**: designed for real-world use cases
4. **Composable**: paths compose with each other and with optics

## Tutorial 01: Effect Path Basics
**File**: `Tutorial01_EffectPathBasics.java` | **Exercises**: 9 (8 graded + 1 diagnostic)

Demystify the four core Path operations on the four core Path types.

**What we'll learn**:
- How to build paths with `Path.just`, `Path.nothing`, `Path.maybe`, `Path.right`, `Path.left`, `Path.either`, `Path.tryOf`, `Path.io`
- How to transform values with `map` and chain dependent steps with `via`
- How to recover from errors with `recover`, `recoverWith`, `orElse`, `mapError`
- How to combine independent paths with `zipWith`
- How a real-world workflow combines find/validate/extract/transform with optional graceful degradation

**Java idiom anchor**: `MaybePath` ↔ `Optional`, `EitherPath` ↔ `Result<E, A>`, `TryPath` ↔ try/catch as a value, `IOPath` ↔ `Supplier` with pipeline operations.

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/effect/Tutorial01_EffectPathBasics.java)

```java
// Exercise 5: Chaining with via
EitherPath<String, Double> result = Path.<String, String>right("25")
    .via(parseNumber)
    .via(validatePositive)
    .via(divideHundredBy);
assertThat(result.run().getRight()).isEqualTo(4.0);
```

---

## Tutorial 02: Effect Path Advanced
**File**: `Tutorial02_EffectPathAdvanced.java` | **Exercises**: 8 (7 graded + 1 diagnostic)

ForPath comprehensions, Effect Contexts, service integration, and the Focus-Effect bridge.

**What we'll learn**:
- How `ForPath` flattens multi-step workflows so each step reads as a named local
- How `ErrorContext`, `ConfigContext`, `MutableContext` give us typed errors + IO, dependency injection, and workflow-local state respectively
- What `@GeneratePathBridge` generates for us, by writing the equivalent wrapper by hand
- How `focus(...)` integrates Effect Paths with the Focus DSL
- The boundary rule: build the workflow as a value first, run once at the edge

**Java idiom anchor**: `ErrorContext` ↔ `CompletableFuture<Either<Error, A>>`, `ConfigContext` ↔ Spring DI as a value, `MutableContext` ↔ `ThreadLocal` accumulator made local and explicit.

[Hands On Practice](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/effect/Tutorial02_EffectPathAdvanced.java)

```java
// Exercise 1: ForPath comprehensions
MaybePath<Integer> result = ForPath.from(Path.just(10))
    .from(n -> Path.just(n * 2))
    .yield((a, b) -> a + b);
assertThat(result.getOrElse(0)).isEqualTo(30);

// Exercise 4: ConfigContext for dependency injection
ConfigContext<?, AppConfig, String> workflow = ConfigContext.<AppConfig>ask()
    .via(cfg -> ConfigContext.pure(cfg.apiUrl() + "/users"))
    .via(endpoint -> ConfigContext.io(cfg -> endpoint + "?timeout=" + cfg.timeout()));
String url = workflow.runWithSync(config);
```

---

## Path Types Quick Reference

| Factory Method | Returns | Description |
|----------------|---------|-------------|
| `Path.just(a)` | `MaybePath<A>` | Present value |
| `Path.nothing()` | `MaybePath<A>` | Absent value |
| `Path.maybe(nullable)` | `MaybePath<A>` | From nullable |
| `Path.right(a)` | `EitherPath<E, A>` | Success |
| `Path.left(e)` | `EitherPath<E, A>` | Failure |
| `Path.either(either)` | `EitherPath<E, A>` | From an existing Either |
| `Path.tryOf(supplier)` | `TryPath<A>` | From throwing code |
| `Path.io(supplier)` | `IOPath<A>` | Deferred effect |
| `Path.vtask(callable)` | `VTaskPath<A>` | Virtual-thread effect |
| `Path.optional(opt)` | `OptionalPath<A>` | From Optional |

## Key Operations

| Operation | Description |
|-----------|-------------|
| `map(f)` | Transform success value (Functor) |
| `via(f)` / `flatMap(f)` | Chain a dependent step (Monad) |
| `zipWith(other, f)` | Combine two independent paths (Applicative) |
| `recover(f)` | Replace error with value |
| `recoverWith(f)` | Replace error with another path |
| `orElse(supplier)` | Alternative on failure |
| `mapError(f)` | Transform error type |
| `focus(focusPath)` | Navigate via an optic |

For the full table see the [Cheatsheet](effect_cheatsheet.md).

## Prerequisites

- Comfortable with Java lambdas and method references
- Basic generics (type parameters)
- **Optional but helpful**: complete [Core Types: Foundations Journey](../coretypes/foundations_journey.md) for the HKT story underneath

## Running the Tutorials

```bash
# Run all Effect Path tutorials
./gradlew :hkj-examples:tutorialTest --tests "*tutorial.effect.*"

# Run a specific tutorial
./gradlew :hkj-examples:tutorialTest --tests "*Tutorial01_EffectPathBasics*"

# Check solutions
./gradlew :hkj-examples:test --tests "*solutions.effect.*"

# See per-journey progress
./gradlew :hkj-examples:tutorialProgress
```

## Common Pitfalls

### 1. `via` vs `map` confusion
**Problem**: passing a Path-returning function to `.map`, producing nested `Path<Path<A>>`.

**Solution**: when the lambda body returns a Path, the call is `via`. The diagnostic exercise in Tutorial 01 makes this concrete.

### 2. Calling `unsafeRun()` mid-workflow
**Problem**: forcing the IO to execute too early, losing the boundary discipline.

**Solution**: build the entire workflow as a value, then call `runIO().unsafeRun()` once at the program edge. The diagnostic exercise in Tutorial 02 covers the right shape.

### 3. Reaching into intermediate `from(...)` values without the tuple
**Problem**: assuming the lambda after `.from(...)` receives the latest value.

**Solution**: it receives a tuple of every previously bound value. Reach in with `t._1()`, `t._2()`, ...; the `.yield(...)` site is the only place every binding is in scope by name.

## Further Resources

- [Effect Path Overview](../../effect/effect_path_overview.md) - chapter-level reference
- [Path Types](../../effect/path_types.md) - every Path type in one page
- [ForPath Comprehension](../../effect/forpath_comprehension.md) - full ForPath documentation
- [Effect Contexts](../../effect/effect_contexts.md) - Context API documentation
- [Concurrency: VTask Journey](../concurrency/vtask_journey.md) - VTaskPath for virtual-thread async
- [Optics: Focus DSL Journey](../optics/focus_dsl_journey.md) - Focus-Effect bridge in depth

## What's Next?

After completing the Effect API journey:

- **Explore Core Types**: understand the HKT foundations in [Core Types: Foundations](../coretypes/foundations_journey.md)
- **Master Optics**: learn data manipulation in [Optics: Lens & Prism](../optics/lens_prism_journey.md)
- **Tackle Concurrency**: apply the same Path API to virtual-thread async with [Concurrency: VTask](../concurrency/vtask_journey.md)
- **See Production Patterns**: explore `hkj-examples/src/main/java/org/higherkindedj/example/` for the order, market, payment domains in full

---

**Previous:** [Core Types: Advanced](../coretypes/advanced_journey.md)
**Next:** [Concurrency: VTask](../concurrency/vtask_journey.md)
