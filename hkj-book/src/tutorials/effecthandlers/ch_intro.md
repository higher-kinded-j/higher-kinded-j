# Effect Handlers Journey

~~~admonish info title="What We'll Learn"
- Modelling effects as algebras (sealed interfaces of operations)
- Writing programs as values that we then interpret
- Composing multiple effect algebras with `EitherF`
- Multiple interpretations of one program: production, test, audit, dry-run, replay
- Static program inspection before execution
~~~

**Tutorials**: 6 | **Prerequisites**: complete the [Effect API Journey](../effect/effect_journey.md) and [Core: Advanced Patterns](../coretypes/advanced_journey.md) (Free Monad / Free Applicative).

~~~admonish tip title="Where This Fits in the Bigger Picture"
This journey is the value-level form of "Hexagonal Architecture without DI": instead of injecting interfaces and mocking them in tests, we model the effects as a sealed-interface ADT and write the program as a `Free` value over it. One program, many interpreters — production runs against real services, tests run against in-memory mocks, audit runs accumulate a trace, dry-run runs validate without executing.

Each tutorial opens with a Pain → Promise header showing the imperative-Java pattern (interface + mock framework + ad-hoc trace logging) the value-level effect algebra replaces.
~~~

## Tutorials

| # | Tutorial | Focus |
|---|----------|-------|
| 01 | Effect Algebra Basics | Sealed interface of operations, `Free` programs |
| 02 | Multiple Interpreters | One program, many interpretations |
| 03 | Error Recovery in Free Programs | Recovery as another interpreter pass |
| 04 | Combining Effects with EitherF | Coproducts of effect algebras |
| 05 | Program Inspection | Static analysis before execution |
| 06 | Advanced Interpreters | Audit and replay |

## How Each Exercise Is Structured

Same template as the rest of the chapter:

- **Pain → Promise** header at the top of each file
- **Java idiom anchor** mapping each pattern to its industry counterpart (Hexagonal Architecture ports, Mockito, MDC trace logging, etc.)
- Exercise bodies and solutions are unchanged from the original; per-exercise tiered hints and teaching-solution commentary are scheduled for the follow-up Phase 3.5 pass

## Running the Tutorials

```bash
./gradlew :hkj-examples:tutorialTest --tests "*tutorial.effecthandlers.*"

# Solutions
./gradlew :hkj-examples:test --tests "*solutions.effecthandlers.*"
```

---

**Previous:** [Context Journey](../context/ch_intro.md)
**Next:** [Optics Journeys](../optics/ch_intro.md)
