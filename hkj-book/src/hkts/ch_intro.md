# Foundations: Higher-Kinded Types

> *"If they can get you asking the wrong questions, they don't have to worry about answers."*
>
> – Thomas Pynchon, *Gravity's Rainbow*

---

~~~admonish tip title="Start with Effect Paths"
This chapter documents the machinery that powers Higher-Kinded-J. **Most users can start with the [Effect Path API](../effect/ch_intro.md) directly** without understanding HKTs in detail. The Effect Path API provides a clean, fluent interface that hides this complexity.

Read this chapter when you want to:
- Understand how the library works internally
- Write generic code that operates across multiple effect types
- Extend the library with your own types
~~~

---

The right question is not "how do I map over this List?" or "how do I map over this Optional?" The right question is "how do I map over *any* container?"

Java's type system, for all its virtues, cannot express this directly. You can write a method that takes a `List<A>` and returns a `List<B>`. You can write another that takes an `Optional<A>` and returns an `Optional<B>`. But you cannot write a single method parameterised over the container type itself: a method that works with `F<A>` where `F` might be `List`, `Optional`, `CompletableFuture`, or any other type constructor.

Higher-Kinded-J provides a workaround. Through a technique called defunctionalisation, it simulates higher-kinded types in Java, allowing you to write generic code that operates across different container types. The mechanism involves phantom "witness" types and a `Kind<F, A>` interface that represents "some container F holding a value of type A."

This is admittedly more verbose than languages with native support. But the alternative (duplicating logic across every container type) scales poorly. Once you've written the same map-filter-flatMap chain for the fifth type, the appeal of abstraction becomes clear.

---

## The Core Insight

Think of it as abstraction over *structure*, not just *values*:

```
    ┌─────────────────────────────────────────────────────────────┐
    │  WITHOUT HKTs                                               │
    │                                                             │
    │    mapList(List<A>, fn)      → List<B>                     │
    │    mapOptional(Optional<A>, fn) → Optional<B>              │
    │    mapFuture(Future<A>, fn)  → Future<B>                   │
    │                                                             │
    │    Three methods. Same logic. Different types.              │
    └─────────────────────────────────────────────────────────────┘

    ┌─────────────────────────────────────────────────────────────┐
    │  WITH HKTs                                                  │
    │                                                             │
    │    map(Functor<F>, Kind<F, A>, fn) → Kind<F, B>            │
    │                                                             │
    │    One method. Works for List, Optional, Future, and more. │
    └─────────────────────────────────────────────────────────────┘
```

The `Functor<F>` parameter carries the implementation; `Kind<F, A>` carries the value.

---

## What You'll Learn

~~~admonish info title="In This Chapter"
- **The Analogy** – Just as higher-order functions take functions as arguments, higher-kinded types take types as arguments. Understanding this parallel unlocks the entire abstraction.
- **Core Concepts** – You'll learn how witness types and the `Kind<F, A>` interface work together to simulate HKTs in Java, and how the widen/narrow pattern bridges between concrete types and their Kind representations.
- **Usage Guide** – Practical patterns for working with `Kind` in real code, including how to write generic methods that work across multiple container types.
- **Basic Examples** – Step-by-step examples showing HKT simulation in action, from simple mappings to complex compositions.
- **Quick Reference** – A condensed reference of essential patterns you'll use repeatedly when working with the library.
- **Extending** – How to add HKT support to your own types, enabling them to participate in the type class hierarchy.
~~~

---

## Chapter Contents

1. [HKT Introduction](hkt_introduction.md) - The analogy: higher-kinded types are to types what higher-order functions are to functions
2. [Concepts](core-concepts.md) - Witness types, Kind interfaces, and the widen/narrow pattern
3. [Usage Guide](usage-guide.md) - Working with Kind in practice
4. [Basic HKT Examples](hkt_basic_examples.md) - Seeing HKT simulation in action
5. [Quick Reference](quick_reference.md) - Essential patterns at a glance
6. [Extending](extending-simulation.md) - Adding HKT support to your own types

---

**Next:** [HKT Introduction](hkt_introduction.md)
