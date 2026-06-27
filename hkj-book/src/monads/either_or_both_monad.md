# EitherOrBoth:
## _Success That Can Also Carry Warnings_

~~~admonish info title="What You'll Learn"
- Why neither `Either` nor `Validated` can model "success with warnings", and how `EitherOrBoth` fills the gap
- The three cases (`Left`, `Right`, `Both`) and the total accessors (`getLeft` / `getRight`) that never throw
- The accumulating `flatMap` contract: `Left` short-circuits, `Both` carries its warnings forward
- Why the monadic `ap` short-circuits (and is *not* the same as `Validated`'s accumulating `ap`)
- Converting to `Either` / `Validated` / `Maybe` at the boundary, deciding what happens to warnings
- That `EitherOrBoth` is known elsewhere as `Ior` (Cats) or `These` (Haskell)
~~~

~~~admonish example title="See Example Code:"
[EitherOrBothExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/eitherorboth/EitherOrBothExample.java)
~~~

## The Problem: A Success With Warnings Has No Home

Higher-Kinded-J models two failure shapes, and both are *exclusive*:

- **`Either<L, R>`** is `Left(e)` **or** `Right(a)`: a result is a failure or a success, never both.
- **`Validated<E, A>`** accumulates errors, but is still exclusive: `Invalid` carries errors and **no value**; `Valid` carries a value and **no errors**.

Neither models a genuinely common outcome: a **successful value that also carries accumulated, non-fatal problems**. Config that parses but reports deprecations; a document that renders but logs recoverable issues; an import that produces records *and* a list of rows it had to skip. The usual workarounds (`Either<E, Pair<List<Warning>, A>>`, a bespoke result record, or simply dropping the warnings) do not compose with the railway combinators, so the warning channel becomes manual plumbing.

`EitherOrBoth<L, R>` is the principled type. It is an *inclusive*-or: a `Left`, a `Right`, **or `Both` at once**.

```java
EitherOrBoth<NonEmptyList<Warning>, Config> result =
    parseConfig(raw)                          // Right(cfg), Both(warnings, cfg), or Left(fatal)
        .flatMap(NonEmptyList.semigroup(), cfg -> validateConfig(cfg)); // warnings accumulate
```

By convention it is **right-biased**: success on the right, problems on the left, so `map` / `flatMap` feel identical to the rest of the railway. A `Both` is therefore "a successful value *and* the warnings gathered while producing it".

~~~admonish note title="Known aliases"
`EitherOrBoth` is called `Ior` (inclusive-or) in Cats and `These` in Haskell. This library uses the
descriptive name; the aliases are noted only so the concept is recognisable.
~~~

---

## The Three Cases

`EitherOrBoth` is a sealed interface over three immutable records, so values are fully
`switch`-matchable:

```java
String describe(EitherOrBoth<NonEmptyList<Warning>, Config> r) {
  return switch (r) {
    case EitherOrBoth.Left<NonEmptyList<Warning>, Config>(var w)        -> "failed: " + w;
    case EitherOrBoth.Right<NonEmptyList<Warning>, Config>(var cfg)     -> "ok: " + cfg;
    case EitherOrBoth.Both<NonEmptyList<Warning>, Config>(var w, var c) -> "ok with warnings: " + c;
  };
}
```

Create values with `left` / `right` / `both`:

```java
EitherOrBoth<String, Integer> left  = EitherOrBoth.left("fatal");
EitherOrBoth<String, Integer> right = EitherOrBoth.right(42);
EitherOrBoth<String, Integer> both  = EitherOrBoth.both("deprecated key", 42);
```

Values are **never `null`**: the components are validated at construction, which is what keeps the
total accessors honest.

## Total Accessors (No Throwing Getters)

Unlike `Either`'s throwing `getLeft` / `getRight`, `EitherOrBoth` exposes **total** accessors that
return a `Maybe`:

| Accessor | `Left(l)` | `Right(r)` | `Both(l, r)` |
|---|---|---|---|
| `getLeft()` | `Just(l)` | `Nothing` | `Just(l)` |
| `getRight()` | `Nothing` | `Just(r)` | `Just(r)` |
| `isLeft()` / `isRight()` / `isBoth()` | `true` / – / – | – / `true` / – | – / – / `true` |

`fold` collapses all three cases at once:

```java
String s = both.fold(
    warnings -> "failed: " + warnings,
    value    -> "ok: " + value,
    (w, v)   -> "ok (" + v + ") with " + w);
```

---

## The `flatMap` Contract (the subtle part)

`flatMap` is right-biased and accumulates the left channel using a `Semigroup<L>` (the same mechanism
`Validated` uses). The contract is: **`Left` is fatal and short-circuits; `Both` carries its warnings
forward and accumulates them.**

```java
EitherOrBoth<L, R2> flatMap(
    Semigroup<L> semigroup,
    Function<? super R, ? extends EitherOrBoth<L, ? extends R2>> mapper);
```

For `Both(l, r).flatMap(⊕, f)`:

| `f(r)` returns | result |
|---|---|
| `Left(l2)` | `Left(l ⊕ l2)` |
| `Right(r2)` | `Both(l, r2)` |
| `Both(l2, r2)` | `Both(l ⊕ l2, r2)` |

`Left(l)` returns `Left(l)` without running `f`; `Right(r)` returns `f(r)` unchanged. Accumulation is
left-to-right and needs only an associative `Semigroup`.

~~~admonish warning title="Monadic `ap` short-circuits; it is NOT `Validated`-style accumulation"
`EitherOrBoth` **is** a lawful `Monad`, so its `ap` is consistent with `flatMap`: when the
function side is a `Left`, `ap` short-circuits and the argument's left is dropped. This is **different**
from `Validated.ap`, which collects errors from *both* sides even across a failure.

If you want the fully-parallel "collect every warning, even across a fatal `Left`" behaviour, use
[`EitherOrBothPath`](../effect/path_either_or_both.md)'s accumulating combinators (`zipWithAccum`,
`andAlso`). The monadic operations here are for sequential, short-circuiting composition.
~~~

The default warning channel is [`NonEmptyList`](nonemptylist_monad.md): a `Both` always has at least
one warning, so a non-empty list is the exact fit, and `NonEmptyList.semigroup()` supplies the
accumulation.

---

## Conversions

The interesting edge is what happens to a `Both`'s warnings at the boundary. The conversions are
named so the decision is explicit:

| Conversion | `Left(l)` | `Right(r)` | `Both(l, r)` |
|---|---|---|---|
| `toEitherDroppingWarnings()` | `Left(l)` | `Right(r)` | `Right(r)` (warnings dropped) |
| `toEitherFailingOnWarnings()` | `Left(l)` | `Right(r)` | `Left(l)` (warnings fatal) |
| `toValidated()` | `Invalid(l)` | `Valid(r)` | `Valid(r)` (warnings dropped) |
| `toMaybe()` | `Nothing` | `Just(r)` | `Just(r)` |

`fromEither` and `fromValidated` lift the exclusive types in (the `Both` case is unreachable from
them).

---

## When To Use It

| Type | Use when |
|------|----------|
| `Either<L, R>` | A clean, exclusive choice: success *or* failure, no partial results |
| `Validated<E, A>` | Pure accumulation: collect *all* errors, with **no** partial value on failure |
| `EitherOrBoth<L, R>` | Success that may also carry non-fatal **warnings** (or a genuinely partial result) |

`EitherOrBoth` is complementary to the other two, not a replacement: reach for it precisely when a
result can be *both* a value and a set of problems.

~~~admonish info title="Key Takeaways"
- **`EitherOrBoth` is an inclusive-or:** `Left`, `Right`, or `Both` at once: the type for "success with warnings".
- **Right-biased with total accessors:** `map` / `flatMap` operate on the right; `getLeft` / `getRight` return `Maybe` and never throw.
- **`flatMap` accumulates:** `Left` short-circuits; `Both` carries warnings forward, combining them via a `Semigroup` (default `NonEmptyList.semigroup()`).
- **Monadic `ap` short-circuits**, unlike `Validated`'s accumulating `ap`; use `EitherOrBothPath` for full parallel accumulation.
- **Explicit conversions** force a decision about warnings at the boundary.
~~~

~~~admonish tip title="See Also"
- [EitherOrBothPath](../effect/path_either_or_both.md): the fluent railway wrapper, with both short-circuit and accumulating composition
- [Either](either_monad.md): the exclusive success-or-failure sibling
- [Validated](validated_monad.md): pure error accumulation with no partial value
- [NonEmptyList](nonemptylist_monad.md): the non-empty warning channel a `Both` pairs with
- [Semigroups and Monoids](../functional/semigroup_and_monoid.md): how the left channel accumulates
~~~

---

**Previous:** [Either](either_monad.md)
**Next:** [Identity](identity.md)
