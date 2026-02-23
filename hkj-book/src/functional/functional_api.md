# Core API Interfaces: Quick Reference

The `hkj-api` module contains the heart of the Higher-Kinded-J library: a set of interfaces that define the core functional programming abstractions. This page provides a quick-reference lookup for all type classes, their key operations, and when to use them.

~~~admonish info title="What You'll Learn"
- A concise overview of every type class in the library
- The key method(s) each type class provides
- When to reach for each abstraction
- Links to the detailed documentation for each type class
~~~

---

## The Monad Hierarchy

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

| Type Class | Key Method(s) | Purpose | When to Use | Docs |
|------------|--------------|---------|-------------|------|
| **`Functor`** | `map` | Transform values inside a container | You need to apply `A -> B` to a wrapped value | [Functor](functor.md) |
| **`Applicative`** | `of`, `ap`, `map2` | Combine independent computations | Validation with error accumulation; combining unrelated results | [Applicative](applicative.md) |
| **`Alternative`** | `empty`, `orElse` | Choice and fallback semantics | Fallback chains; parser combinators; trying multiple sources | [Alternative](alternative.md) |
| **`Selective`** | `select`, `whenS`, `ifS` | Conditional effects with static structure | Feature flags; config-based branching where both branches are known upfront | [Selective](selective.md) |
| **`Monad`** | `flatMap` | Sequence dependent computations | Each step's effect depends on the previous step's result | [Monad](monad.md) |
| **`MonadError`** | `raiseError`, `handleErrorWith` | Typed error handling within monadic workflows | Raising and recovering from domain-specific errors | [MonadError](monad_error.md) |
| **`MonadZero`** | `zero` | Filtering in monadic chains | Guard clauses in for-comprehensions (`when()`) | [MonadZero](monad_zero.md) |

---

## Data Aggregation Type Classes

| Type Class | Key Method(s) | Purpose | When to Use | Docs |
|------------|--------------|---------|-------------|------|
| **`Semigroup`** | `combine` | Associatively merge two values of the same type | Telling `Validated` how to accumulate errors | [Semigroup and Monoid](semigroup_and_monoid.md) |
| **`Monoid`** | `combine`, `empty` | Semigroup with an identity element | Folding collections (needs a starting value for empty cases) | [Semigroup and Monoid](semigroup_and_monoid.md) |

---

## Structure-Iterating Type Classes

| Type Class | Key Method(s) | Purpose | When to Use | Docs |
|------------|--------------|---------|-------------|------|
| **`Foldable`** | `foldMap` | Reduce a structure to a summary value | Summing, counting, concatenating, or aggregating any collection | [Foldable and Traverse](foldable_and_traverse.md) |
| **`Traverse`** | `traverse`, `sequenceA` | Iterate with effects, then flip the structure inside-out | Validating every item in a list; batch fetching with error collection | [Foldable and Traverse](foldable_and_traverse.md) |

---

## Dual-Parameter Type Classes

| Type Class | Key Method(s) | Purpose | When to Use | Docs |
|------------|--------------|---------|-------------|------|
| **`Profunctor`** | `lmap`, `rmap`, `dimap` | Transform both input (contravariant) and output (covariant) | Adapting functions to different input/output formats; optics foundation | [Profunctor](profunctor.md) |
| **`Bifunctor`** | `bimap`, `first`, `second` | Transform both parameters of a two-parameter type (both covariant) | Mapping over both sides of `Either`, `Tuple2`, `Validated`, or `Writer` | [Bifunctor](bifunctor.md) |

---

## Core HKT Abstraction

* **`Kind<F, A>`** -- The foundational interface that emulates a higher-kinded type. It represents a type `F` that is generic over a type `A`. For example, `Kind<ListKind.Witness, String>` represents a `List<String>`. This interface is the common currency for all functional abstractions in the library.

~~~admonish tip title="See Also"
- [Choosing Your Abstraction Level](abstraction_levels.md) - Decision flowchart for Applicative vs Selective vs Monad
- [For Comprehension](for_comprehension.md) - Readable syntax for composing monadic operations
- [Natural Transformation](natural_transformation.md) - Polymorphic functions between type constructors
~~~

---

**Previous:** [Introduction](ch_intro.md)
**Next:** [Functor](functor.md)
