# Context Journey

~~~admonish info title="What We'll Learn"
- Working with `ScopedValue`-backed contexts as composable values
- Threading request, security, and trace contexts through a workflow without `ThreadLocal`
- Propagating context across virtual-thread boundaries with VTask
- Combining contexts with the Effect Path API for production-shaped flows
~~~

**Tutorials**: 6 | **Prerequisites**: complete the [Effect API Journey](../effect/effect_journey.md) and the [Concurrency: VTask Journey](../concurrency/vtask_journey.md) first.

~~~admonish tip title="Where This Fits in the Bigger Picture"
A `Context` is the value-level form of "the request id, principal, or trace id every part of this workflow needs". In production, [One Line, Six Layers](../../hkts/one_line_six_layers.md) almost always runs inside one or more contexts: `RequestContext` for tracing, `SecurityContext` for the principal, custom `Context` for tenant or feature-flag state. This journey teaches the patterns.

Each tutorial opens with a Pain → Promise header showing the imperative-Java pattern (`ThreadLocal`, MDC, `SecurityContextHolder`) the value-level Context replaces.
~~~

## Tutorials

| # | Tutorial | Focus |
|---|----------|-------|
| 01 | Context Basics | `ScopedValue`-backed contexts, the `ContextValue` constructor pair |
| 02 | Context Composition | `flatMap`, the Functor / Monad / Applicative instance |
| 03 | RequestContext Patterns | Distributed tracing, request id propagation |
| 04 | SecurityContext Patterns | Authentication and authorisation as values |
| 05 | Context with VTask | Propagation across virtual-thread boundaries |
| 06 | Advanced Context Patterns | Error handling and recovery |

## How Each Exercise Is Structured

Same template as the rest of the chapter:

- **Pain → Promise** header at the top of each file
- **Java idiom anchor** mapping each Context type to its `ThreadLocal` / `MDC` / `SecurityContextHolder` cousin
- Exercise bodies and solutions are unchanged from the original; per-exercise tiered hints and teaching-solution commentary are scheduled for the follow-up Phase 3.5 pass

## Running the Tutorials

```bash
./gradlew :hkj-examples:tutorialTest --tests "*tutorial.context.*"

# Solutions
./gradlew :hkj-examples:test --tests "*solutions.context.*"

# Per-journey progress
./gradlew :hkj-examples:tutorialProgress
```

---

**Previous:** [Concurrency: Scope & Resource](../concurrency/scope_resource_journey.md)
**Next:** [Optics Journeys](../optics/ch_intro.md)
