<!-- description: Every Higher-Kinded-J type class at a glance, from Functor and Monad to Traverse, with its key methods, plus every ready-made Semigroup and Monoid. -->

# Quick Reference

_Choose a type class, look up its key methods and page, and find a ready-made `Semigroup` or `Monoid`._

## Which type class? {#which-type-class}

| You want to | Reach for |
|---|---|
| Change each value inside, and keep the container | [`Functor`](../functional/functor.md) |
| Validate several fields and report every error, with `Validated` | [`Applicative`](../functional/applicative.md) |
| Run an effect only when an earlier result calls for it, with every branch named up front | [`Selective`](../functional/selective.md) |
| Make a call that needs the last call's result | [`Monad`](../functional/monad.md) |
| Fail with a typed error, and recover from it | [`MonadError`](../functional/monad_error.md) |
| Try a cache, then the database | [`Alternative`](../functional/alternative.md) |
| Keep only the values that pass a test, inside a `For` | [`MonadZero`](../functional/monad_zero.md) |
| Sum or count a list, with a `Monoid` | [`Foldable`](../functional/foldable_and_traverse.md) |
| Look up every id, failing if any is missing | [`Traverse`](../functional/foldable_and_traverse.md) |

[Applicative, Selective or Monad?](../functional/abstraction_levels.md) decides between `Applicative`, `Selective` and `Monad`. Behaviour belongs to the instance, not the type class: `Either`'s `ap` stops at the first error, while `Validated`'s joins them all with its `Semigroup`.

## Type classes over a container {#type-classes-over-a-container}

These work on a container held as a `Kind`, as [How the Encoding Works](core-concepts.md) explains, and [the `Instances` facade](../functional/instances_facade.md) gives you one for a type. On an [Effect Path](../effect/ch_intro.md), the same operations are methods of the Path itself.

| Type class | Key methods | What it gives you | Page |
|---|---|---|---|
| `Functor` | `map` | Change the value inside, and keep the container | [Functor](../functional/functor.md) |
| `Applicative` | `of`, `ap`, `map2` to `map5` | Put a value in the container, and combine steps that do not depend on one another | [Applicative](../functional/applicative.md) |
| `Selective` | `select`, `branch`, `ifS`, `whenS` | Run an effect only when an earlier result calls for it, with every branch named up front | [Selective](../functional/selective.md) |
| `Monad` | `flatMap`, `flatMap2` to `flatMap5`, `as`, `peek` | Build the next step from the last result | [Monad](../functional/monad.md) |
| `MonadError` | `raiseError`, `handleErrorWith`, `handleError`, `recover`, `recoverWith` | Fail with a typed error, and recover from it | [MonadError](../functional/monad_error.md) |
| `Alternative` | `empty`, `orElse`, `orElseAll`, `guard` | Fall back to a second container when the first is empty (`Maybe`), or join the two (`List`) | [Alternative](../functional/alternative.md) |
| `MonadZero` | `zero`, `filter` | Drop a value that fails a test, as the `when` step of a [`For`](../functional/for_comprehension.md) does | [MonadZero](../functional/monad_zero.md) |
| `Foldable` | `foldMap` | Reduce a structure to one plain value, with a `Monoid` | [Foldable and Traverse](../functional/foldable_and_traverse.md) |
| `Traverse` | `traverse`, `sequenceA` | Run an effect on every element, and gather the results in one container | [Foldable and Traverse](../functional/foldable_and_traverse.md) |
| `MonadReader` | `ask`, `asks`, `reader`, `local` | Read an environment | [MTL Capabilities](../transformers/mtl_capabilities.md) |
| `MonadState` | `get`, `gets`, `inspect`, `put`, `modify` | Read and replace a state | [MTL Capabilities](../transformers/mtl_capabilities.md) |
| `MonadWriter` | `tell`, `listen`, `listens`, `pass`, `censor` | Add to an output kept beside the result | [MTL Capabilities](../transformers/mtl_capabilities.md) |

Argument order varies: `map(f, fa)` and `flatMap(f, ma)` take the function first, while `map2(fa, fb, f)` and `handleErrorWith(ma, handler)` take it last. Each linked page, apart from MTL Capabilities, states its type class's laws, and [the hierarchy](../functional/ch_intro.md#the-hierarchy) shows which type class extends which, drawn from `hkj-api` itself. [Lifting the Hood](lifting_the_hood.md#what-this-means-in-practice) says what a call through the encoding costs.

## Type classes over a two-sided type {#type-classes-over-a-two-sided-type}

These work on a type with two parameters, held as a `Kind2`.

| Type class | Key methods | What it gives you | Page |
|---|---|---|---|
| `Bifunctor` | `bimap`, `first`, `second` | Change either side of a two-sided type, such as `Either` or `Tuple2` | [Bifunctor](../functional/bifunctor.md) |
| `Profunctor` | `dimap`, `lmap`, `rmap` | Adapt a function's input and its output | [Profunctor](../functional/profunctor.md) |

## Type classes over a plain type {#type-classes-over-a-plain-type}

These take plain values, such as two `String`s, rather than a `Kind`.

| Type class | Key methods | What it gives you | Page |
|---|---|---|---|
| `Semigroup` | `combine` | Join two values of one type, where the grouping never changes the result | [Semigroup and Monoid](../functional/semigroup_and_monoid.md) |
| `Monoid` | `empty`, `combine`, `combineAll`, `combineN`, `of` | A `Semigroup` with an empty value, so that it can fold nothing at all | [Semigroup and Monoid](../functional/semigroup_and_monoid.md) |

## Ready-made Semigroups and Monoids {#ready-made-semigroups-and-monoids}

A `Semigroup` joins two values:

| Factory | Joins |
|---|---|
| `Semigroups.list()` | Two lists, end to end, into a new list |
| `Semigroups.set()` | Two sets, by union |
| `Semigroups.string()` | Two strings, end to end |
| `Semigroups.string(delimiter)` | Two strings, with the delimiter between them |
| `Semigroups.first()` | Two values, keeping the first |
| `Semigroups.last()` | Two values, keeping the second |

A `Monoid` adds an empty value, which changes nothing it is joined to, and which a fold of an empty structure gives:

| Factory | Joins | Empty value |
|---|---|---|
| `Monoids.list()` | Lists, end to end | An empty list |
| `Monoids.set()` | Sets, by union | An empty set |
| `Monoids.string()` | Strings, end to end | `""` |
| `Monoids.integerAddition()` | `Integer`s, by `+` | `0` |
| `Monoids.integerMultiplication()` | `Integer`s, by `*` | `1` |
| `Monoids.longAddition()` | `Long`s, by `+` | `0L` |
| `Monoids.longMultiplication()` | `Long`s, by `*` | `1L` |
| `Monoids.doubleAddition()` | `Double`s, by `+`, which rounds | `0.0` |
| `Monoids.doubleMultiplication()` | `Double`s, by `*`, which rounds | `1.0` |
| `Monoids.bigDecimalAddition()` | `BigDecimal`s, by `add`, exactly: use it for money | `BigDecimal.ZERO` |
| `Monoids.booleanAnd()` | `Boolean`s, by `&&` | `true` |
| `Monoids.booleanOr()` | `Boolean`s, by `\|\|` | `false` |
| `Monoids.firstOptional()` | `Optional`s, keeping the first present one | `Optional.empty()` |
| `Monoids.lastOptional()` | `Optional`s, keeping the last present one | `Optional.empty()` |
| `Monoids.maximum()` | `Optional`s, keeping the larger value | `Optional.empty()` |
| `Monoids.maximum(comparator)` | `Optional`s, keeping the larger value by the comparator | `Optional.empty()` |
| `Monoids.minimum()` | `Optional`s, keeping the smaller value | `Optional.empty()` |
| `Monoids.minimum(comparator)` | `Optional`s, keeping the smaller value by the comparator | `Optional.empty()` |
| `Monoids.update()` | [`Update`](../functional/semigroup_and_monoid.md#composing-updates)s, applying the first and then the second | `Update.identity()` |

`Monoid.of(empty, combine)` makes your own from an empty value and a `Semigroup`, as [Semigroup and Monoid](../functional/semigroup_and_monoid.md) does to count the units of each SKU.

---

**Previous:** [Supported Types](../monads/supported-types.md)
**Next:** [Functional API](../functional/functional_api.md)
