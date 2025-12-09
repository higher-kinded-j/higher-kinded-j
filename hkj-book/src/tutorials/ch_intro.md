# Hands-On Learning

> *"You look at where you're going and where you are and it never makes sense, but then you look back at where you've been and a pattern seems to emerge."*
>
> – Robert M. Pirsig, *Zen and the Art of Motorcycle Maintenance*

---

Reading about functional programming is one thing. Writing it is another entirely.

These tutorials exist because understanding emerges from doing. You will encounter exercises where you replace `answerRequired()` with working code. Tests will fail, red and insistent, until your solution is correct. The failure is not punishment; it is feedback. Each red test is a question; each green test is comprehension made concrete.

The pattern Pirsig describes applies precisely here. Midway through an exercise on Applicative composition, the relationship between `map` and `ap` may feel arbitrary. Three tutorials later, building a validation pipeline, the pattern clicks. You look back at where you've been, and suddenly the earlier struggles make sense. This is not a flaw in the learning process. It *is* the learning process.

Two tracks are available. The Core Types Track builds intuition for `Kind<F, A>`, Functors, Applicatives, and Monads: the theoretical foundation. The Optics Track focuses on practical data manipulation: Lenses, Prisms, Traversals, and the Free Monad DSL. Neither requires the other, though they complement each other well.

Expect to struggle. Expect moments of confusion. These are not signs that something is wrong. They are signs that learning is happening.

---

## The Learning Loop

```
    ┌─────────────────────────────────────────────────────────────┐
    │                                                             │
    │    READ  ──────►  WRITE  ──────►  RUN  ──────►  OBSERVE    │
    │      │              │              │               │        │
    │      │              │              │               │        │
    │      ▼              ▼              ▼               ▼        │
    │   Exercise      Replace        Execute          Red or     │
    │   description   answerRequired()  test          Green?     │
    │                                                             │
    │                         │                                   │
    │                         ▼                                   │
    │              ┌──────────┴──────────┐                       │
    │              │                     │                        │
    │           GREEN                   RED                       │
    │         (Next exercise)     (Read error, iterate)          │
    │                                                             │
    └─────────────────────────────────────────────────────────────┘
```

The loop is simple. The understanding it produces is not.

---

## Two Tracks

| Track | Focus | Duration | Exercises |
|-------|-------|----------|-----------|
| **Core Types** | HKT simulation, type classes, monadic workflows | ~60 min | 45 |
| **Optics** | Lenses, Prisms, Traversals, Free Monad DSL | ~90 min | 64 |

Start with whichever interests you more. Circle back to the other when ready.

---

## What You'll Learn

~~~admonish info title="In This Chapter"
- **Core Types Track** – Forty-five exercises building from basic `Kind<F, A>` wrapping through Functors, Applicatives, and Monads to production-ready workflow patterns. Each exercise has tests that turn green when you've got it right.
- **Optics Track** – Sixty-four exercises progressing from simple Lens operations through Prisms, Traversals, and the Free Monad DSL. You'll build intuition for when each optic type is appropriate.
- **Solutions Guide** – When you're genuinely stuck, reference implementations are available. Try to struggle first; the learning happens in the struggle.
- **Troubleshooting** – Common errors and their solutions. When tests fail unexpectedly or types don't align, check here before giving up.
~~~

---

## Chapter Contents

1. [Interactive Tutorials](tutorials_intro.md) - Getting started with the tutorial system
2. [Core Types Track](coretypes_track.md) - From Kind basics to production workflows
3. [Optics Track](optics_track.md) - From simple Lenses to complex data transformation pipelines
4. [Solutions Guide](solutions_guide.md) - Reference implementations when you're truly stuck
5. [Troubleshooting](troubleshooting.md) - Common issues and how to resolve them

---

**Next:** [Interactive Tutorials](tutorials_intro.md)
