# Capstone: One Line, Six Layers Grows Up

~~~admonish info title="What We'll Build"
Tutorial 00 introduced the chapter anchor expression, one line of Higher-Kinded-J that touched every layer of the library. By the time we reach this capstone we have learned each layer in depth: the Effect Path API, optics, expression comprehensions, structured concurrency, resilience patterns. The capstone is the same expression, grown up to handle a realistic order workflow:

```java
findOrder(id)
    .toEitherPath(OrderError.NOT_FOUND)
    .map(o -> itemsLens.modify(this::recompute, o))
    .via(this::reserveInventory)
    .via(this::save)
    .run();
```

Same six layers. Same mental model. One expression.
~~~

**Duration**: ~30 minutes | **Tutorials**: 1 (capstone) | **Exercises**: 7

**Prerequisites**: complete the [Foundations Journey](../coretypes/foundations_journey.md), the [Effect API Journey](../effect/effect_journey.md), and at least one of [Optics: Lens & Prism](../optics/lens_prism_journey.md) or [Concurrency: VTask](../concurrency/vtask_journey.md).

~~~admonish tip title="Where This Fits in the Bigger Picture"
The capstone is the single best demonstration that the chapter material composes. We use the One Line, Six Layers expression as the through-line, applied to a realistic order pipeline that touches every journey:

| Token | Capstone exercise | Journey |
|-------|-------------------|---------|
| `find(id)` | E1 lift to EitherPath | Effect API |
| `.toEitherPath(...)` | E1 natural transformation | Foundations + Effect API |
| `.map(itemsLens.modify(...))` | E2 bulk update | Optics |
| `.via(reserveInventory)` | E3 chain dependent steps | Foundations Monad |
| `.via(save)` | E3 chain dependent steps | Foundations Monad |
| `.run()` | E3 dispatch | Effect API |
| Full expression | E4 assemble | every journey |
| Resilience layer | E5 circuit breaker + retry | Resilience |
| ForPath spelling | E6 same pipeline, comprehension form | Expression |
| Graceful degradation | E7 recover with a default | Effect API |
~~~

## What We'll Learn

- How the patterns from each journey compose into one realistic workflow
- Two equivalent spellings of the same pipeline (fluent chain and ForPath comprehension)
- Where to add resilience without disrupting the rest of the pipeline
- Where graceful degradation belongs (recover early; downstream steps proceed normally)

## How the Tutorial Works

A single test file with 7 exercises and a matching solution file. Exercises 1-3 build the pipeline incrementally; exercise 4 assembles them all; exercises 5-7 add resilience, an alternative spelling, and recovery.

```bash
./gradlew :hkj-examples:tutorialTest --tests "*TutorialCapstone_OneLineSixLayersGrowsUp*"
./gradlew :hkj-examples:test --tests "*TutorialCapstone_OneLineSixLayersGrowsUp_Solution*"
```

## Where to Next

- Read the production order example: [`hkj-examples/src/main/java/org/higherkindedj/example/order`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/order). Same shape, more services.
- Replace the in-memory repo with a real database access via `VTaskPath`, add a saga around payment + shipment, log every step through an audit interpreter (Effect Handlers journey).
- Use `OpticsSpecInterfaces` (Optics Tutorial 16) to lift third-party domain objects (Jackson POJOs, jOOQ records) into the same pipeline.

---

**Previous:** [Effect Handlers Journey](../effecthandlers/ch_intro.md)
**Next:** [Foundations chapter](../../hkts/foundations_intro.md) - the theory underneath
