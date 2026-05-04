# Foundations

<!-- Drop a hero image file into hkj-book/src/images/ and update the src attribute below. -->
<img src="../images/foundations.webp" alt="Greek Foundations" style="width: 100%;" />

> _"The purpose of abstraction is not to be vague, but to create a new semantic level in which one can be absolutely precise."_
>
> – Edsger W. Dijkstra

---

Everything earlier in this book (Effect Paths, Optics, Monad Transformers) sits on top of something. Foundations is the something.

The library gives us a single `map` that works across `Maybe`, `Either`, `Try`, `Validated`, `IO`, `CompletableFuture`, and every other container it ships with. That would be impossible if the code inside `map` had to know which container it was dealing with. What makes it possible is a three-layer stack: an encoding that lets Java talk about "some container F" without committing to which one it is, a set of type classes that describe what F is capable of doing, and concrete instances that claim those capabilities for specific types like `Maybe` or `Either`.

We can read Foundations as three independent sections, or as one connected argument. Either way, the Effect Path API on the next page wraps all of this in a fluent interface that most day-to-day code never needs to look beneath.

~~~admonish tip title="New Here? Start with One Line"
If we want a single picture of how the whole stack fits together, the best entry point is **[One Line, Six Layers](one_line_six_layers.md)**. It takes a single expression that anyone could have written by their second week with Higher-Kinded-J, and shows which layer of this chapter is doing each job.

Foundations rewards being read after we have already shipped something with the library. Most users can work productively with the Effect Path API without ever opening this chapter; we read Foundations when we want to:

- Understand how the library works internally
- Write generic code that operates across multiple containers
- Extend the library with our own types or type-class instances
~~~

---

## How The Layers Stack

```
           Core Types (Section 3)
    ┌───────────────────────────────────────────┐
    │  Maybe   Either   Try   Validated   IO    │
    │  Reader  State    Writer  List   ...      │   the concrete types
    └───────────────────────────────────────────┘
                         ▲
                         │  claim capabilities via type-class instances
                         │
          Type Classes (Section 2)
    ┌───────────────────────────────────────────┐
    │  Functor   Applicative   Monad            │
    │  MonadError  Traverse    Semigroup  ...   │   the abstract capabilities
    └───────────────────────────────────────────┘
                         ▲
                         │  expressed as polymorphic interfaces over
                         │
     Higher-Kinded Types (Section 1)
    ┌───────────────────────────────────────────┐
    │  Kind<F, A>    WitnessArity<F>            │
    │  widen / narrow    witness types          │   the encoding
    └───────────────────────────────────────────┘
```

Each layer depends only on the one beneath it. We can read bottom-up for the dependency story, top-down for the motivation, or jump straight to a single expression and unpack it both ways at once.

---

~~~admonish info title="In This Chapter"
- **Higher-Kinded Types** – The encoding. Java cannot express "a container of any shape" directly, so Higher-Kinded-J simulates higher-kinded types through witness types and a `Kind<F, A>` interface. Covers how the encoding works, why it is what it is, and how to extend it for our own types.
- **Type Classes** – The capabilities. Once we can talk about "some container F", we can ask whether F supports `map` (Functor), independent combination (Applicative), sequencing (Monad), and so on. Covers the full hierarchy, the laws that keep each instance honest, and the For-comprehension syntax built on top.
- **Core Types** – The instances. The library ships seventeen concrete types, each occupying a different niche: absence (`Maybe`, `Optional`), failure (`Either`, `Try`, `Validated`), deferred effects (`IO`, `Lazy`, `CompletableFuture`), collections (`List`, `Stream`), contextual computation (`Reader`, `State`, `Writer`), and control (`Trampoline`, `Free`, `Coyoneda`). This is where we choose the right container for the problem.
~~~

---

## Chapter Contents

1. [One Line, Six Layers](one_line_six_layers.md) - A guided tour of the whole stack in a single expression
2. [Lifting the Hood](lifting_the_hood.md) - One `flatMap` call traced end to end through `widen`, dispatch, and `narrow`
3. [Higher-Kinded Types](ch_intro.md) - The encoding: `Kind<F, A>`, witness types, widen/narrow
4. [Type Classes](../functional/ch_intro.md) - The abstractions: Functor, Applicative, Monad, and friends
5. [Core Types](../monads/ch_intro.md) - The instances: seventeen monads and a decision guide for choosing among them
6. [Foundations FAQ](faq.md) - Design questions, runtime cost, and how Higher-Kinded-J compares to alternatives

---

**Next:** [One Line, Six Layers](one_line_six_layers.md)
