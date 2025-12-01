# Type Classes: The Building Blocks

> *"Quality is not a thing. It is an event."*
>
> – Robert M. Pirsig, *Zen and the Art of Motorcycle Maintenance*

---

A type class is not a thing you can point to. It is not an object in memory, nor a concrete class you instantiate. It is, rather, an event: the moment when a type demonstrates that it possesses certain capabilities.

When we say `Optional` is a `Functor`, we mean that mapping over an Optional is a meaningful operation. When we say it is also an `Applicative`, we add the ability to combine independent Optional values. When we say it is a `Monad`, we gain sequencing, the power to chain operations where each step depends on the previous result. The type hasn't changed. Our understanding of what we can do with it has.

This chapter presents the type class hierarchy that powers Higher-Kinded-J. At the foundation sits `Functor`, providing `map`. Above it, `Applicative` adds `of` and `ap`. `Monad` contributes `flatMap`. `MonadError` handles failure. Each builds on what came before, each unlocking new compositional possibilities.

The hierarchy is not arbitrary. It reflects mathematical structure (specifically, category theory), though you need not understand the mathematics to use the tools. Think of it as a ladder: each rung grants new capabilities, and the lower rungs remain available as you climb.

---

## The Hierarchy

```
                        ┌──────────┐
                        │  Functor │  map
                        └────┬─────┘
                             │
                        ┌────┴─────┐
                        │Applicative│  of, ap
                        └────┬─────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
        ┌─────┴─────┐  ┌─────┴─────┐  ┌─────┴─────┐
        │   Monad   │  │Alternative│  │ Selective │
        │  flatMap  │  │ orElse    │  │  select   │
        └─────┬─────┘  └───────────┘  └───────────┘
              │
        ┌─────┴─────┐
        │MonadError │  raiseError, handleErrorWith
        └───────────┘
```

Each arrow represents "extends": Monad is an Applicative with additional power.

---

## Why This Matters

The practical benefit is polymorphism over *behaviour*, not just data:

```java
// This method works with ANY Monad: Optional, Either, List, IO, Future...
public static <F, A, B> Kind<F, B> transform(
    Monad<F> monad,
    Kind<F, A> value,
    Function<A, Kind<F, B>> operation) {

    return monad.flatMap(operation, value);
}
```

Write once. Use everywhere the capability exists.

---

## What You'll Learn

~~~admonish info title="In This Chapter"
- **Functor** – The foundation: mapping over wrapped values
- **Applicative** – Combining independent computations
- **Alternative** – Choice and fallback operations
- **Monad** – Sequencing dependent computations
- **MonadError** – Explicit error handling in monadic contexts
- **Semigroup and Monoid** – Combining values associatively
- **Foldable and Traverse** – Iterating and transforming structures
- **MonadZero** – Filtering in comprehensions
- **Selective** – Conditional effects with static analysis
- **Profunctor** – Transforming both input and output
- **Bifunctor** – Mapping over two type parameters
- **For Comprehension** – Readable monadic composition
~~~

---

## Chapter Contents

