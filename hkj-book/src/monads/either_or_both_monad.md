<!-- description: Return a Java result that succeeds with warnings using EitherOrBoth, keep each non-fatal problem beside the value, and decide at the boundary what to do. -->

# EitherOrBoth: Success with Warnings

_Keep a value's warnings beside it, and decide at the boundary what they mean._

~~~admonish abstract title="EitherOrBoth at a glance"
| | |
|---|---|
| **Models** | A value, an error, or a value with warnings beside it |
| **Replaces** | A result record holding a value and a list of warnings |
| **Effect Path** | [`EitherOrBothPath`](../effect/path_either_or_both.md) |
{{#include ../../../hkj-examples/src/test/resources/golden/foundations-capabilities.md.golden:either_or_both_monad}}
| **Choose it when** | A success can carry problems that are not fatal; otherwise [Validated](validated_monad.md), or compare every type in [Choosing a Type](supported-types.md#choosing-a-type) |
~~~

~~~admonish info title="What You'll Learn"
- Return a value with warnings as a `Both`, keeping a fatal problem as a `Left`
- Join independent checks and keep every warning, even when one check is fatal
- Predict what `flatMap` does with the warnings of each case
- Decide at the boundary what happens to a `Both`'s warnings
- Keep the same steps on an `EitherOrBothPath`
~~~

~~~admonish example title="See Example Code"
**The code on this page is [EitherOrBothBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/eitherorboth/EitherOrBothBook.java)**: the page includes it, so the build compiles and runs it. [EitherOrBothBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/eitherorboth/EitherOrBothBookTest.java) holds what the page says each method does.
~~~

---

## A success with warnings {#a-success-with-warnings}

[Either](either_monad.md) and [Validated](validated_monad.md) are exclusive: a result is a success or a failure, never both. Some outcomes are both. An order line imported from a wholesaler's feed may need correcting before it can be used: a lower-case SKU upper-cased, or a price with a fraction of a penny rounded. The line is usable, and the importer should still report what it changed. Java code usually returns a record of the value and a `List` of warnings, which no `map` or `flatMap` knows how to carry.

`EitherOrBoth<L, R>` is an inclusive-or: a `Left`, a `Right`, or `Both` at once. Success goes on the right and problems on the left, so a `Both` is a value with the warnings found while producing it. Cats calls the type `Ior`, and Haskell `These`.

The code on this page uses these imports, with `LineItem` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.Validated;
```

Each of these checks passes, fails, or passes with something to say. A warning goes in a [`NonEmptyList`](nonemptylist_monad.md), since a `Both` always has at least one:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/eitherorboth/EitherOrBothBook.java:checks}}
```

## Every warning in one result {#every-warning-in-one-result}

`EitherOrBoth.accumulate()` joins independent checks, as `Validated.accumulate()` does, and keeps the value flowing past each warning. A single fatal check makes the whole result a `Left`, which holds the fatal problem beside every warning, in the order of the checks:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/eitherorboth/EitherOrBothBook.java:assemble}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/eitherorboth/EitherOrBothBook.java:line_outcomes}}
```

A `switch` over the three cases reads the result, and the compiler checks that each is handled:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/eitherorboth/EitherOrBothBook.java:describe}}
```

[Accumulating Assembly](validated_assembly.md) covers the builder in full, including `EitherOrBoth.fields()`, which names the field each warning belongs to.

## The `flatMap` contract {#the-flatmap-contract}

A step that needs the whole line can only follow the checks. `flatMap(semigroup, step)` takes a [`Semigroup`](../functional/semigroup_and_monoid.md), which joins two values, as well as the step, because a `Both`'s warnings must be joined with any the step adds. `accumulate()` needs none, because it takes `NonEmptyList` warnings and joins them itself. `REVIEW_LIMIT` is 100.00, and a line whose total exceeds it should be checked by a person:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/eitherorboth/EitherOrBothBook.java:review}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/eitherorboth/EitherOrBothBook.java:review_outcomes}}
```

<pre class="hkj-railway-diagram" role="img" aria-label="EitherOrBoth's three tracks through an import, a review and a later step. A Right stays a Right, or crosses to the Both track when the review adds a warning. A Both carries its warnings forward and adds any later ones. A fatal check, or a later step that gives a Left, moves to the Left track, which keeps every warning; after a fatal check the review never runs.">
        import             review     later step
Right  ════●══════════════════●══════════════▶  Right(line)
                               ╲ review adds a warning
Both   ════●════════════════════●═══════●═════▶  Both(every warning, line)
                                         ╲ it gives a Left
Left   ────●──────────────────────────────●───▶  Left(every warning)
        a fatal check:
        the review never runs
</pre>

The diagram shows a `Left` as the only track that stops the chain, and a review that moves a `Right` onto the `Both` track. A `Left` is fatal, so `flatMap` never runs the step, and a `Right` gives whatever the step gives. For `Both(w, r).flatMap(semigroup, f)`, the warnings `w` join whatever `f(r)` gives:

| `f(r)` gives | `flatMap` gives |
|---|---|
| `Left(w2)` | `Left(w ⊕ w2)` |
| `Right(r2)` | `Both(w, r2)` |
| `Both(w2, r2)` | `Both(w ⊕ w2, r2)` |

`w ⊕ w2` is `semigroup.combine(w, w2)`: with `NonEmptyList.semigroup()`, the warnings carried in come first, then the step's.

## Converting at the boundary {#converting-at-the-boundary}

Code outside the importer may take an `Either`, a `Validated` or a `Maybe`. The two `Either` conversions are named for what they do with a `Both`'s warnings, so the choice is visible at the call. `toValidated()` and `toMaybe()` drop the warnings, so read them first with `getLeft()` if you need them:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/eitherorboth/EitherOrBothBook.java:conversions}}
```

| Conversion | `Left(l)` | `Right(r)` | `Both(l, r)` |
|---|---|---|---|
| `toEitherDroppingWarnings()` | `Left(l)` | `Right(r)` | `Right(r)`, warnings dropped |
| `toEitherFailingOnWarnings()` | `Left(l)` | `Right(r)` | `Left(l)`, warnings fatal |
| `toValidated()` | `Invalid(l)` | `Valid(r)` | `Valid(r)`, warnings dropped |
| `toMaybe()` | `Nothing` | `Just(r)` | `Just(r)`, warnings dropped |

`EitherOrBoth.fromEither` and `fromValidated` turn an `Either` or a `Validated` into an `EitherOrBoth`, and never give a `Both`.

## On an `EitherOrBothPath` {#on-an-eitherorbothpath}

Application code can hold the same steps on an [`EitherOrBothPath`](../effect/path_either_or_both.md). `Path.eitherOrBoth` takes a result and the semigroup that joins its warnings, `via` chains a step with the `flatMap` contract, and `warnings()` reads them. These are the extra imports:

<!-- verify -->
```java
import org.higherkindedj.hkt.effect.EitherOrBothPath;
import org.higherkindedj.hkt.effect.Path;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/eitherorboth/EitherOrBothBook.java:path}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/eitherorboth/EitherOrBothBook.java:path_outcome}}
```

## Methods {#methods}

| Method | What it does |
|---|---|
| `EitherOrBoth.left(l)`, `right(r)`, `both(l, r)` | Build each case; none accepts `null` |
| `isLeft()`, `isRight()`, `isBoth()` | Say which case it holds |
| `getLeft()`, `getRight()` | Return a `Maybe` of that side, never throwing; a `Both` has both |
| `map(f)` | Changes the value of a `Right` or a `Both` |
| `mapLeft(f)` | Changes the warnings or error of a `Left` or a `Both` |
| `bimap(onLeft, onRight)` | Changes whichever sides it holds |
| `flatMap(semigroup, f)` | Runs a step on the value, joining warnings as the contract table shows |
| `zipWithAccum(other, semigroup, f)` | Joins two results, keeping every warning from both, even across a `Left` |
| `fold(onLeft, onRight, onBoth)` | Turns any case into one result |
| `EitherOrBoth.accumulate()`, `fields()` | Join independent checks, keeping every warning |

A `Left`, a `Right` and a `Both` never hold `null`, as [Values, never null](../hkts/core-concepts.md#values-never-null) says for every type.

## Working with Kind {#working-with-kind}

Generic code, written once for any container, takes `EitherOrBoth` through its `Monad` instance, from `Instances.eitherOrBoth(semigroup)`. The semigroup is how the instance's `flatMap` joins warnings. The instance works over `Kind<EitherOrBothKind.Witness<L>, R>`, so you widen a result to pass it in, and narrow the result to read it. These are the extra imports:

<!-- verify -->
```java
import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.eitherorboth.EitherOrBothKind;
import org.higherkindedj.hkt.instances.Instances;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/eitherorboth/EitherOrBothBook.java:kind}}
```

The instance is a `Monad`, not a `MonadError`, so it has no `raiseError` or `handleErrorWith`. `EitherOrBoth` has instances of other type classes too, such as `Traverse` and `Bifunctor`, each an object of its own, and *EitherOrBoth at a glance*, at the head of the page, lists them.

~~~admonish warning title="The instance's ap stops at a Left function"
`EitherOrBoth`'s `Monad` instance obeys the monad laws, so its `ap` agrees with its `flatMap`. When the function side is a `Left`, `ap` gives that `Left` and drops the argument's warnings, where `Validated`'s `ap` keeps both sides' errors. To keep every warning even across a `Left`, join with `zipWithAccum`, on the value or on an [`EitherOrBothPath`](../effect/path_either_or_both.md).
~~~

## When to use `EitherOrBoth` {#when-to-use-eitherorboth}

| You need | Use |
|---|---|
| A success that can carry warnings beside it | `EitherOrBoth` |
| The same steps as a fluent chain in application code | [EitherOrBothPath](../effect/path_either_or_both.md) |
| Every error, and no value at all when any check fails | [Validated](validated_monad.md) |
| The first error only, with no partial result | [Either](either_monad.md) |

~~~admonish info title="Hands-On Learning"
Practise keeping a value flowing past its warnings, in Exercise 5 of [Tutorial 12: Accumulating Assembly](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial12_AccumulatingAssembly.java) (6 exercises).
~~~

---

**Previous:** [Try](try_monad.md)
**Next:** [NonEmptyList](nonemptylist_monad.md)
