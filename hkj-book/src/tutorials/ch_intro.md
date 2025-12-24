# Hands-On Learning

> *"You look at where you're going and where you are and it never makes sense, but then you look back at where you've been and a pattern seems to emerge."*
>
> – Robert M. Pirsig, *Zen and the Art of Motorcycle Maintenance*

---

Reading about functional programming is one thing. Writing it is another entirely.

These tutorials exist because understanding emerges from doing. You will encounter exercises where you replace `answerRequired()` with working code. Tests will fail, red and insistent, until your solution is correct. The failure is not punishment; it is feedback. Each red test is a question; each green test is comprehension made concrete.

The pattern Pirsig describes applies precisely here. Midway through an exercise on Applicative composition, the relationship between `map` and `ap` may feel arbitrary. Three tutorials later, building a validation pipeline, the pattern clicks. You look back at where you've been, and suddenly the earlier struggles make sense. This is not a flaw in the learning process. It *is* the learning process.

**Eight journeys** are available, each designed to be completed in 22-40 minutes. Do one per sitting. Let concepts consolidate before moving on.

Expect to struggle. Expect moments of confusion. These are not signs that something is wrong. They are signs that learning is happening.

---

## The Learning Loop

```
    ┌─────────────────────────────────────────────────────────────┐
    │                                                             │
    │    READ  ──────►  WRITE  ──────►  RUN  ──────►  OBSERVE     │
    │      │              │              │               │        │
    │      │              │              │               │        │
    │      ▼              ▼              ▼               ▼        │
    │   Exercise      Replace        Execute          Red or      │
    │   description   answerRequired()  test          Green?      │
    │                                                             │
    │                         │                                   │
    │                         ▼                                   │
    │              ┌──────────┴──────────┐                        │
    │              │                     │                        │
    │           GREEN                   RED                       │
    │         (Next exercise)     (Read error, iterate)           │
    │                                                             │
    └─────────────────────────────────────────────────────────────┘
```

The loop is simple. The understanding it produces is not.

---

## Eight Journeys

| Journey | Focus | Duration | Exercises |
|---------|-------|----------|-----------|
| **Core: Foundations** | HKT simulation, Functor, Applicative, Monad | ~38 min | 24 |
| **Core: Error Handling** | MonadError, concrete types, real-world patterns | ~30 min | 20 |
| **Core: Advanced** | Natural Transformations, Coyoneda, Free Applicative | ~26 min | 16 |
| **Effect API** | Effect paths, ForPath, Effect Contexts | ~65 min | 15 |
| **Optics: Lens & Prism** | Lens basics, Prism, Affine | ~40 min | 30 |
| **Optics: Traversals** | Traversals, composition, practical applications | ~40 min | 27 |
| **Optics: Fluent & Free** | Fluent API, Free Monad DSL | ~37 min | 22 |
| **Optics: Focus DSL** | Type-safe path navigation | ~22 min | 18 |

Start with whichever interests you most. The [Learning Paths](learning_paths.md) guide suggests sequences for different goals.

---

## What You'll Learn

~~~admonish info title="In This Chapter"
- **Core Types Journeys** – Three journeys building from basic `Kind<F, A>` wrapping through error handling to advanced patterns like Coyoneda and Free Applicative.
- **Effect API Journey** – Learn the recommended user-facing API for working with functional effects.
- **Optics Journeys** – Four journeys progressing from simple Lens operations through Traversals, the Free Monad DSL, and the Focus DSL.
- **Learning Paths** – Curated sequences of journeys for different goals and time budgets.
- **Solutions Guide** – When you're genuinely stuck, reference implementations are available. Try to struggle first; the learning happens in the struggle.
- **Troubleshooting** – Common errors and their solutions.
~~~

---

## Chapter Contents

1. [Interactive Tutorials](tutorials_intro.md) - Getting started with the tutorial system
2. **Core Types**
   - [Foundations Journey](coretypes/foundations_journey.md) - Kind basics to Monad
   - [Error Handling Journey](coretypes/error_handling_journey.md) - MonadError and concrete types
   - [Advanced Patterns Journey](coretypes/advanced_journey.md) - Natural transformations to Free Applicative
3. **Effect API**
   - [Effect API Journey](effect/effect_journey.md) - The recommended user-facing API
4. **Optics**
   - [Lens & Prism Journey](optics/lens_prism_journey.md) - Fundamental optics
   - [Traversals & Practice Journey](optics/traversals_journey.md) - Collections and real-world use
   - [Fluent & Free DSL Journey](optics/fluent_free_journey.md) - Advanced APIs
   - [Focus DSL Journey](optics/focus_dsl_journey.md) - Type-safe path navigation
5. [Learning Paths](learning_paths.md) - Recommended journey sequences
6. [Solutions Guide](solutions_guide.md) - Reference implementations
7. [Troubleshooting](troubleshooting.md) - Common issues and solutions

---

**Next:** [Interactive Tutorials](tutorials_intro.md)
