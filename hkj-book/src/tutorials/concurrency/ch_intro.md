# Concurrency Journeys

Two journeys on virtual threads and structured concurrency. Scope & Resource builds directly on VTask, so work them in order.

~~~admonish tip title="Where This Fits in the Bigger Picture"
`VTask` is the deferred-effect type in the [Core Types](../../monads/ch_intro.md) section of Foundations and the concurrent counterpart to `IO`. These journeys exercise the `Monad` instance for `VTask` and the structured-concurrency primitives that appear in any production version of the [One Line, Six Layers](../../hkts/one_line_six_layers.md) anchor (where `repo.find(id)` is almost always async).
~~~

| Journey | Focus | Duration | Exercises |
|---------|-------|----------|-----------|
| [VTask](vtask_journey.md) | Virtual threads, `Par` combinators, `VTaskPath`, `VTaskContext` | ~45 min | 16 |
| [Scope & Resource](scope_resource_journey.md) | `Scope` joiners, `Resource` bracket, concurrent cleanup | ~30 min | 12 |

---

**Next:** [VTask](vtask_journey.md)
