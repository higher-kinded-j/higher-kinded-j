# Capstone: One Line, Six Layers Grows Up

~~~admonish info title="What We'll Build"
Tutorial 00 wrote one line that changed the email on an order and touched every layer of the library. By the time we reach this capstone we have met each layer in the Foundations and Effect API journeys. The capstone grows the same line into a checkout step on the same order service. It finds the order, marks it paid, takes a discount off every line and saves it, and the save refuses an order that has already shipped:

<!-- verify -->
```java
var outcome = orders.find(id)
    .toEitherPath(OrderError.notFound(id))
    .map(order -> OrderFocus.status().set(OrderStatus.PAID, order))
    .map(order -> OrderFocus.lines().price().modifyAll(DISCOUNT, order))
    .via(orders::save)
    .run();
```

The same layers, the same mental model, and still one expression.
~~~

**Tutorials**: 1 (capstone) | **Exercises**: 7
<!-- exercises: coretypes/TutorialCapstone_OneLineSixLayersGrowsUp -->

**Prerequisites**: complete the [Foundations Journey](../coretypes/foundations_journey.md) and the [Effect API Journey](../effect/effect_journey.md). The [Optics: Traversals & Practice](../optics/traversals_journey.md) journey helps with Exercise 2, and [Resilience Patterns](../resilience/resilience_journey.md) introduces the circuit breaker that Exercise 5 puts around the save.

~~~admonish tip title="Where This Fits in the Bigger Picture"
The capstone is the single best demonstration that the chapter material composes. We use the One Line, Six Layers expression as the through-line, applied to a realistic order pipeline that touches every journey:

| Step | Capstone exercise | Journey |
|-------|-------------------|---------|
| `orders.find(id)` | E1 lift to `EitherPath` | Effect API |
| `.toEitherPath(...)` | E1 natural transformation | Foundations |
| `OrderFocus.lines().price()` | E2 discount every line through a traversal | Optics |
| `OrderFocus.status()`, inside `map` | E3 mark it paid | Optics and Foundations Functor |
| `.via(orders::save)` | E3 a save that can refuse | Foundations Monad |
| `.run()` | E4 the `Either` at the end | Effect API |
| Full expression | E4 assemble | every journey |
| The save behind a circuit breaker | E5 `EitherPath.withCircuitBreaker` | Resilience |
| `ForPath` spelling | E6 Exercise 3's step, comprehension form | Expression |
| Recovery | E7 a fresh order for `NotFound`, a refusal passed on | Effect API |
~~~

## What We'll Learn

- How the patterns from each journey compose into one realistic workflow
- Two spellings of Exercise 3's step: a fluent chain and a `ForPath` comprehension
- Where to add resilience without disrupting the rest of the pipeline, and why an eager `EitherPath` wraps the save inside `via` rather than chaining a breaker
- How to recover from one error and pass the others on

## How the Tutorial Works

A single test file and a matching solution file. Exercises 1-3 build the pipeline incrementally; exercise 4 assembles them all; exercises 5-7 add a circuit breaker, an alternative spelling, and recovery.

```bash
./gradlew :hkj-examples:tutorialTest --tests "*TutorialCapstone_OneLineSixLayersGrowsUp*"
./gradlew :hkj-examples:test --tests "*TutorialCapstone_OneLineSixLayersGrowsUp_Solution*"
```

## Where to Next

- Read the production order example: [`hkj-examples/src/main/java/org/higherkindedj/example/order`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/order). Same shape, more services.
- Replace the in-memory repository with real database access through `VTaskPath` or `VResultPath`, add a saga around payment and shipment, and log every step through an audit interpreter (Effect Handlers journey).
- Use `OpticsSpecInterfaces` (Optics Tutorial 16) to lift third-party domain objects (Jackson POJOs, jOOQ records) into the same pipeline.
- Read [One Line, Six Layers](../../hkts/one_line_six_layers.md), the opening of the Foundations chapter, for the theory under the expression.

---

**Previous:** [Resilience Patterns](../resilience/resilience_journey.md)
**Next:** [Learning Paths](../learning_paths.md)
