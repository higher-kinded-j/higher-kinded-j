# Effect API Journey

**Estimated Duration**: ~65 minutes (two sub-journeys) | **Exercises**: 15

~~~admonish info title="The Primary API"
The Effect Path API is the **recommended user-facing API** for Higher-Kinded-J. This journey teaches you to be productive with functional effects without needing deep HKT knowledge.
~~~

## What You'll Learn

The Effect API Journey covers the complete Effect Path system:

### Part 1: Fundamentals (~35 min, 8 exercises)
- **Creating Paths**: `Path.just()`, `Path.nothing()`, `Path.right()`, `Path.left()`, `Path.tryOf()`, `Path.io()`
- **Transforming Values**: Using `map` to transform success values
- **Chaining Operations**: Using `via` (flatMap) to chain dependent computations
- **Error Recovery**: `recover`, `recoverWith`, `orElse`, `mapError`
- **Combining Paths**: Using `zipWith` to combine independent computations
- **Real-World Workflows**: Building complete effect-based workflows

### Part 2: Advanced (~30 min, 7 exercises)
- **ForPath Comprehensions**: Readable multi-step workflows with for-comprehension syntax
- **Effect Contexts**: ErrorContext, ConfigContext, MutableContext
- **@GeneratePathBridge**: Annotations for service integration
- **Focus-Effect Integration**: Combining optics with effect paths

## Why Effect API First?

The Effect Path API is designed to be the primary interface for most users:

1. **Ergonomic**: Fluent, chainable API that feels natural in Java
2. **Type-Safe**: Compile-time guarantees about effect handling
3. **Practical**: Designed for real-world use cases
4. **Composable**: Paths compose naturally with each other and with optics

## Getting Started

The tutorials are located in `hkj-examples/src/test/java/org/higherkindedj/tutorial/effect/`:

### Tutorial 01: Effect Path Basics

**File**: `Tutorial01_EffectPathBasics.java`

```java
// Exercise 1: Creating MaybePath
MaybePath<String> present = Path.just("hello");
assertThat(present.getOrElse("default")).isEqualTo("hello");

MaybePath<String> absent = Path.nothing();
assertThat(absent.getOrElse("default")).isEqualTo("default");

// Exercise 5: Chaining with via
EitherPath<String, Double> result = Path.<String, String>right("25")
    .via(parseNumber)
    .via(validatePositive)
    .via(divideHundredBy);
assertThat(result.run().getRight()).isEqualTo(4.0);
```

### Tutorial 02: Effect Path Advanced

**File**: `Tutorial02_EffectPathAdvanced.java`

```java
// Exercise 1: ForPath comprehensions (supports up to 8 chained bindings)
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

## Path Types Quick Reference

| Factory Method | Returns | Description |
|----------------|---------|-------------|
| `Path.just(a)` | `MaybePath<A>` | Present value |
| `Path.nothing()` | `MaybePath<A>` | Absent value |
| `Path.maybe(nullable)` | `MaybePath<A>` | From nullable |
| `Path.right(a)` | `EitherPath<E, A>` | Success |
| `Path.left(e)` | `EitherPath<E, A>` | Failure |
| `Path.tryOf(supplier)` | `TryPath<A>` | From throwing code |
| `Path.io(supplier)` | `IOPath<A>` | Deferred effect |
| `Path.vtask(callable)` | `VTaskPath<A>` | Virtual thread effect |
| `Path.optional(opt)` | `OptionalPath<A>` | From Optional |

## Key Operations

| Operation | Description |
|-----------|-------------|
| `map(f)` | Transform success value |
| `via(f)` / `flatMap(f)` | Chain dependent computation |
| `zipWith(other, f)` | Combine independent paths |
| `recover(f)` | Replace error with value |
| `recoverWith(f)` | Replace error with path |
| `orElse(supplier)` | Alternative on failure |
| `mapError(f)` | Transform error |
| `focus(focusPath)` | Navigate with optics |

## Prerequisites

Before starting this journey, you should:

1. Be comfortable with Java lambdas and method references
2. Understand basic generics (type parameters)

**Optional but helpful**: Complete [Core Types: Foundations Journey](../coretypes/foundations_journey.md) for deeper HKT understanding.

## Running the Tutorials

```bash
# Run all Effect Path tutorials
./gradlew :hkj-examples:test --tests "*tutorial.effect.*"

# Run specific tutorial
./gradlew :hkj-examples:test --tests "*Tutorial01_EffectPathBasics*"

# Check solutions
./gradlew :hkj-examples:test --tests "*solutions.effect.*"
```

## Further Resources

After completing this journey, explore:

- [Effect Path Overview](../../effect/effect_path_overview.md) - Detailed documentation
- [Path Types](../../effect/path_types.md) - All available path types
- [ForPath Comprehension](../../effect/forpath_comprehension.md) - Advanced ForPath usage
- [Effect Contexts](../../effect/effect_contexts.md) - Context API documentation
- [VTask Journey](../concurrency/vtask_journey.md) - Virtual thread-based concurrency with VTaskPath
- [Optics: Focus-Effect Bridge (Tutorial 14)](../optics/focus_dsl_journey.md) - Deep integration guide

## What's Next?

After completing the Effect API journey:

- **Explore Core Types**: Understand the HKT foundations in [Core Types: Foundations](../coretypes/foundations_journey.md)
- **Master Optics**: Learn data manipulation in [Optics: Lens & Prism](../optics/lens_prism_journey.md)
- **See Production Patterns**: Check the examples in `hkj-examples/src/main/java/org/higherkindedj/example/`

---

**Previous**: [Learning Paths](../learning_paths.md)
**Next**: [Core Types: Foundations](../coretypes/foundations_journey.md)
