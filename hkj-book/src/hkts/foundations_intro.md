# Foundations

<!-- Drop a hero image file into hkj-book/src/images/ and update the src attribute below. -->
<img src="../images/foundations.webp" alt="Greek Foundations" style="width: 100%;" />

> _"The purpose of abstraction is not to be vague, but to create a new semantic level in which one can be absolutely precise."_
>
> – Edsger W. Dijkstra

---

Everything earlier in the book (Effect Paths, Optics, the Monad Transformers) sits on top of something. This chapter is the something.

The library gives you a single `map` that works across `Maybe`, `Either`, `Try`, `Validated`, `IO`, `CompletableFuture`, and every other container it ships with. That wouldn't be possible if the code inside `map` had to know which container it was dealing with. What makes it possible is a three-layer stack: an encoding that lets Java talk about "some container F" without committing to which one it is; a set of type classes that describe what F is capable of doing; and concrete instances that claim those capabilities for specific types like `Maybe` or `Either`.

You can read Foundations as three independent sections or as one connected argument. Either way, the Effect Path API on the next page wraps all of this in a fluent interface that most day-to-day code never needs to look beneath.

~~~admonish tip title="Start with the Effect Path API"
Higher-Kinded-J's **[Effect Path API](effect/ch_intro.md)** wraps all of this machinery in a fluent, Java-friendly interface. Most users can work productively with it without ever reading this chapter.

Read Foundations when you want to:
- Understand how the library works internally
- Write generic code that operates across multiple containers
- Extend the library with your own types or type-class instances
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

Each layer depends only on the one beneath it. Read bottom-up for the dependency story, top-down for the motivation.

---

~~~admonish info title="In This Chapter"
- **Higher-Kinded Types** – The encoding. Java cannot express "a container of any shape" directly, so Higher-Kinded-J simulates higher-kinded types through witness types and a `Kind<F, A>` interface. Covers how the encoding works, why it is what it is, and how to extend it for your own types.
- **Type Classes** – The capabilities. Once you can talk about "some container F", you can ask whether F supports `map` (Functor), independent combination (Applicative), sequencing (Monad), and so on. Covers the full hierarchy, the laws that keep each instance honest, and the For-comprehension syntax built on top.
- **Core Types** – The instances. The library ships seventeen concrete types, each occupying a different niche: absence (`Maybe`, `Optional`), failure (`Either`, `Try`, `Validated`), deferred effects (`IO`, `Lazy`, `CompletableFuture`), collections (`List`, `Stream`), contextual computation (`Reader`, `State`, `Writer`), and control (`Trampoline`, `Free`, `Coyoneda`). This is where you choose the right container for the problem.
~~~

---

## Chapter Contents

1. [Higher-Kinded Types](ch_intro.md) - The encoding: `Kind<F, A>`, witness types, widen/narrow
2. [Type Classes](../functional/ch_intro.md) - The abstractions: Functor, Applicative, Monad, and friends
3. [Core Types](../monads/ch_intro.md) - The instances: seventeen monads and a decision guide for choosing among them

---

**Next:** [Higher-Kinded Types](ch_intro.md)
