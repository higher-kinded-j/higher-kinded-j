# Expression Journeys

Two journeys on for-comprehensions beyond the basics: named state records that replace positional tuples, and explicit parallel composition for independent computations.

~~~admonish tip title="Where This Fits in the Bigger Picture"
For-comprehensions are the idiom that turns chains of `flatMap` into something that reads like a sequence of statements. They sit on top of [Monad](../../functional/monad.md) (sequential) and [Applicative](../../functional/applicative.md) (parallel); the [For Comprehension](../../functional/for_comprehension.md) reference page covers the typeclass machinery these journeys build on.
~~~

| Journey | Focus | Duration | Exercises |
|---------|-------|----------|-----------|
| [ForState](forstate_journey.md) | Named record state, lens threading, `zoom`, `matchThen` | ~25 min | 11 |
| [ForPath Parallel](forpath_parallel_journey.md) | Applicative parallel composition for Path types | ~20 min | 9 |

---

**Next:** [ForState](forstate_journey.md)
