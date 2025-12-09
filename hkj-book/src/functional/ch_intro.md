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
- **Functor** – The foundational type class that enables transformation of values inside containers without changing the container's structure. Every other abstraction builds on this.
- **Applicative** – When you have multiple independent computations and need to combine their results. Unlike Monad, Applicative allows parallel evaluation since results don't depend on each other.
- **Alternative** – Provides choice and fallback semantics: try one computation, and if it fails, try another. Essential for parsing and error recovery patterns.
- **Monad** – The power to sequence dependent computations where each step can use results from previous steps. The workhorse of functional programming.
- **MonadError** – Extends Monad with explicit error handling capabilities, allowing you to raise errors and recover from them within the monadic context.
- **Semigroup and Monoid** – Type classes for combining values associatively. Monoids add an identity element, enabling operations like folding empty collections.
- **Foldable and Traverse** – Foldable lets you reduce structures to single values; Traverse lets you transform structures while collecting effects.
- **MonadZero** – Adds filtering capabilities to monads, enabling guard conditions in for-comprehensions that can short-circuit computation.
- **Selective** – Sits between Applicative and Monad, providing conditional effects that can be statically analysed. Useful for build systems and optimisation.
- **Profunctor** – For types with both input and output, allowing you to transform both sides. The foundation for advanced optics.
- **Bifunctor** – Like Functor, but for types with two type parameters. Enables simultaneous transformation of both sides of types like Either or Tuple.
- **For Comprehension** – A fluent API for composing monadic operations, inspired by Scala's for-comprehensions and Haskell's do-notation.
~~~

---

## Chapter Contents

1. [Functional API](functional_api.md) - Overview of all type class interfaces
2. [Functor](functor.md) - The foundation: mapping over wrapped values
3. [Applicative](applicative.md) - Combining independent computations
4. [Alternative](alternative.md) - Choice and fallback operations
5. [Monad](monad.md) - Sequencing dependent computations
6. [MonadError](monad_error.md) - Explicit error handling in monadic contexts
7. [Semigroup and Monoid](semigroup_and_monoid.md) - Combining values associatively
8. [Foldable and Traverse](foldable_and_traverse.md) - Iterating and transforming structures
9. [MonadZero](monad_zero.md) - Filtering in comprehensions
10. [Selective](selective.md) - Conditional effects with static analysis
11. [Profunctor](profunctor.md) - Transforming both input and output
12. [Bifunctor](bifunctor.md) - Mapping over two type parameters
13. [Natural Transformation](natural_transformation.md) - Polymorphic functions between type constructors
14. [For Comprehension](for_comprehension.md) - Readable monadic composition

---

**Next:** [Functional API](functional_api.md)
