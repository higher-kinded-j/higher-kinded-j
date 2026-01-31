# Learning by Example

> *"For the things we have to learn before we can do them, we learn by doing them."*
>
> — Aristotle, *Nicomachean Ethics*

---

The best way to understand functional programming in Java is to see it in action. This chapter presents a curated collection of runnable examples that demonstrate how to apply Higher-Kinded-J patterns to real problems.

Each example is complete and self-contained. You can run them, modify them, and use them as starting points for your own code. The examples progress from simple demonstrations of individual concepts to complete applications that show how the pieces fit together.

~~~admonish info title="What You'll Find"
- **Basic Examples** – Core type demonstrations, from Maybe and Either to Monad Transformers
- **Effect Path API** – Fluent composition of computations with error handling, validation, and resource management
- **Optics** – Type-safe navigation and transformation of nested immutable data structures
- **Complete Applications** – Production-quality examples showing how functional patterns solve real problems
~~~

---

## Running Examples

All examples can be run using Gradle:

```bash
./gradlew :hkj-examples:run -PmainClass=<fully.qualified.ClassName>
```

For example:

```bash
./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.maybe.MaybeExample
```

---

## Example Categories

### Core Types

The foundation of functional programming: monadic types that represent different computational effects.

| Category | Examples | What You'll Learn |
|----------|----------|-------------------|
| **Optional Values** | [MaybeExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/maybe/MaybeExample.java), [OptionalExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/optional/OptionalExample.java) | Safe handling of missing values |
| **Error Handling** | [EitherExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/either/EitherExample.java), [TryExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/trymonad/TryExample.java) | Typed errors and exception handling |
| **Validation** | [ValidatedMonadExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/validated/ValidatedMonadExample.java) | Accumulating multiple errors |
| **Side Effects** | [IOExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/io/IOExample.java), [LazyExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/lazy/LazyExample.java) | Deferred and controlled execution |
| **State & Environment** | [StateExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/state/StateExample.java), [ReaderExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/reader/ReaderExample.java), [WriterExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/writer/WriterExample.java) | Pure state threading and dependency injection |
| **Concurrency** | [CompletableFutureExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/future/CompletableFutureExample.java), [VTaskPathExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/VTaskPathExample.java) | Async operations and virtual threads |

### Monad Transformers

Combining multiple effects into unified stacks.

| Transformer | Example | What You'll Learn |
|-------------|---------|-------------------|
| **EitherT** | [EitherTExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/either_t/EitherTExample.java) | Async operations with typed errors |
| **MaybeT** | [MaybeTExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/maybe_t/MaybeTExample.java) | Optional values in effectful contexts |
| **ReaderT** | [ReaderTExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/reader_t/ReaderTExample.java) | Dependency injection with effects |
| **StateT** | [StateTExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/state_t/StateTExample.java) | Stateful computation within effects |

### Effect Path API

Fluent, railway-oriented programming with composable effects.

| Category | Examples | What You'll Learn |
|----------|----------|-------------------|
| **Path Basics** | [BasicPathExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/BasicPathExample.java), [ChainedComputationsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ChainedComputationsExample.java) | Creating paths, map/via, chaining |
| **Error Handling** | [ErrorHandlingExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ErrorHandlingExample.java), [ServiceLayerExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ServiceLayerExample.java) | recover, mapError, handleError |
| **Validation** | [ValidationPipelineExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ValidationPipelineExample.java), [AccumulatingValidationExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/AccumulatingValidationExample.java) | Combining validations, error accumulation |
| **ForPath** | [ForPathExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ForPathExample.java) | Comprehension syntax for paths |
| **Concurrency** | [ParallelExecutionExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ParallelExecutionExample.java), [ScopeExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ScopeExample.java) | Parallel execution, structured concurrency |
| **Resilience** | [ResilienceExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ResilienceExample.java), [ResourceManagementExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ResourceManagementExample.java) | Retry policies, resource safety |

### Optics

Type-safe lenses, prisms, and traversals for immutable data.

| Category | Examples | What You'll Learn |
|----------|----------|-------------------|
| **Core Optics** | [LensUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/LensUsageExample.java), [PairedLensExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/PairedLensExample.java), [PrismUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/PrismUsageExample.java), [IsoUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/IsoUsageExample.java), [AffineUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/AffineUsageExample.java) | Fundamental optic types |
| **Traversals** | [TraversalUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/TraversalUsageExample.java), [FilteredTraversalExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/FilteredTraversalExample.java) | Multiple focus points, filtering |
| **Focus DSL** | [NavigatorExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/NavigatorExample.java), [KindFieldFocusExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/KindFieldFocusExample.java), [TraverseIntegrationExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/TraverseIntegrationExample.java), [ValidationPipelineExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/ValidationPipelineExample.java), [AsyncFetchExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/AsyncFetchExample.java) | Fluent navigation, validation, and async patterns |
| **Fluent API** | [FluentApiExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/fluent/FluentApiExample.java), [FreeDslExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/fluent/FreeDslExample.java) | Fluent optic operations, Free monad DSL |
| **External Types** | [ImportOpticsBasicExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/importoptics/ImportOpticsBasicExample.java), [SpecInterfaceUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/external/SpecInterfaceUsageExample.java) | Optics for types you don't own |
| **Cookbook** | [DeepUpdateRecipes](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/cookbook/DeepUpdateRecipes.java), [CollectionRecipes](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/cookbook/CollectionRecipes.java) | Ready-to-use patterns |

### Context and Concurrency

Thread-safe context propagation with Java's ScopedValue API.

| Example | What You'll Learn |
|---------|-------------------|
| [ContextBasicExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/context/ContextBasicExample.java) | Basic Context with ask, asks, map, flatMap |
| [ContextScopeExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/context/ContextScopeExample.java) | Context with Scope for structured concurrency |
| [RequestContextExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/context/RequestContextExample.java) | Request context propagation across layers |
| [SecurityContextExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/context/SecurityContextExample.java) | Authentication and authorization patterns |
| [DistributedTracingExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/context/DistributedTracingExample.java) | Tracing across microservice boundaries |

---

## Featured Applications

These complete applications demonstrate how functional patterns come together to solve real problems.

### Order Processing Workflow

A production-quality e-commerce workflow demonstrating:

- **Typed error hierarchies** with sealed interfaces
- **ForPath comprehensions** for readable workflow composition
- **Resilience patterns**: retries, timeouts, circuit breakers
- **Structured concurrency** with VTask and Scope
- **Focus DSL** for immutable state updates

~~~admonish example title="See the Code"
[org.higherkindedj.example.order](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/order)
~~~

**[Order Workflow Deep Dive →](examples_order.md)**

### Draughts (Checkers) Game

An interactive command-line game demonstrating:

- **Pure state management** with `WithStatePath`
- **Railway-oriented validation** using `EitherPath`
- **Side effect encapsulation** with `IOPath`
- **Focus DSL** for navigating game state
- **Stream-based patterns** for declarative iteration

~~~admonish example title="See the Code"
[org.higherkindedj.example.draughts](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/draughts)
~~~

**[Draughts Game Deep Dive →](examples_draughts.md)**

---

## Complete Examples Reference

For a comprehensive listing of all examples with run commands, see the [Examples Guide](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/EXAMPLES-GUIDE.md) in the repository.

---

## Chapter Contents

1. [Order Processing Workflow](examples_order.md) – Building production-ready business workflows
2. [Draughts Game](examples_draughts.md) – Pure functional game development

---

**Next:** [Order Processing Workflow](examples_order.md)
