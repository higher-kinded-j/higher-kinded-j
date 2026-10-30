<!-- description: Combine values with Semigroup, start from a Monoid's empty value, total money exactly with bigDecimalAddition, and build your own monoid with Monoid.of. -->

# Semigroup and Monoid: Foundational Type Classes

_Combine two values of one type, and start from an empty value to total any number of them._

~~~admonish info title="What You'll Learn"
- Combine two values of one type with a `Semigroup`, and say what associativity promises
- Start a combination from a `Monoid`'s empty value, so an empty list totals to zero
- Total money exactly with `Monoids.bigDecimalAddition()`, and build a monoid the library lacks with `Monoid.of`
- Pick a ready-made monoid for numbers, strings, collections, optional values and updates
- Check a monoid against its three laws
~~~

~~~admonish example title="See Example Code"
**The code on this page is [MonoidBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monoid/MonoidBook.java)**: the page includes it, so the build compiles and runs it. [MonoidBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/monoid/MonoidBookTest.java) holds what the page says, and [FoundationsLawsBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java) checks the laws.
~~~

---

## Combining two values {#combining-two-values}

A `Semigroup<A>` has one method, `combine`, which takes two values of type `A` and returns one. Unlike the type classes on the pages around this one, it works on plain values, not on a container `Kind<F, A>`: two lists of errors become one list, and two strings become one string.

The code on this page uses these imports, with `LineItem` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.Update;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monoid/MonoidBook.java:semigroup}}
```

`combine` must be associative: `combine(combine(a, b), c)` equals `combine(a, combine(b, c))`. So a list of values combines to the same result however you group the calls, and a library can split a long list and combine the halves.

The `Semigroups` interface holds the common ones:

| Factory | Combines two values by |
|---|---|
| `Semigroups.list()` | Joining two lists, the first's elements first |
| `Semigroups.set()` | Taking the union of two sets |
| `Semigroups.string()` | Joining two strings |
| `Semigroups.string(delimiter)` | Joining two strings with `delimiter` between them |
| `Semigroups.first()` | Keeping the first value |
| `Semigroups.last()` | Keeping the last value |

The library's main use for one is [`Validated`](../monads/validated_monad.md)'s error accumulation. `Instances.validated(Semigroups.list())` gives an `Applicative` whose `map2` joins the errors of every invalid input with the semigroup, as [Applicative](applicative.md#what-each-instance-does) shows.

~~~admonish tip title="The non-empty alternative: `NonEmptyList.semigroup()`"
For errors, a [`NonEmptyList`](../monads/nonemptylist_monad.md) says in its type what an invalid result promises: at least one error, so its `head` and `reduce` need no empty case. `NonEmptyList.semigroup()` joins two of them, left to right. It lives on the type rather than in `Semigroups`, because `NonEmptyList` is in `hkj-core` and `Semigroups` in `hkj-api`. `Path.validNel` and `Path.invalidNel` build it in, so the common validation needs no `Semigroup` argument.
~~~

---

## Starting from an empty value {#starting-from-an-empty-value}

A `Monoid<A>` is a `Semigroup<A>` with one more method, `empty`, which returns a value that changes nothing when combined with another. Zero is addition's empty value, and an empty list is joining's. With it, `combineAll`, a default method on every `Monoid`, can total any number of values, none included, since a total of nothing is the empty value.

If you have called `stream.reduce(identity, accumulator)`, you have used a monoid: `reduce` asks for an identity and an associative accumulator, and `Monoid` names that pair.

The examples total an order's lines:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monoid/MonoidBook.java:lines}}
```

An order's total is the monoid for `BigDecimal` addition, run over each line's total:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monoid/MonoidBook.java:total}}
```

On an [Effect Path](../effect/ch_intro.md), `ListPath.foldMap` takes the monoid and the function together, and [Foldable and Traverse](foldable_and_traverse.md#foldable-one-structure-many-summaries) does the same for any container.

Money is `BigDecimal`, as `LineItem.price` is, since `Monoids.doubleAddition()` rounds, as [Things people get wrong](#things-people-get-wrong) shows. A `BigDecimal` sum keeps the larger scale of its two values, so prices with two decimal places total to a value with two.

### Ready-made monoids {#ready-made-monoids}

| Factory | Combines by | Empty value |
|---|---|---|
| `integerAddition()`, `longAddition()`, `doubleAddition()` | `+` | `0` |
| `integerMultiplication()`, `longMultiplication()`, `doubleMultiplication()` | `*` | `1` |
| `bigDecimalAddition()` | `BigDecimal.add` | `BigDecimal.ZERO` |
| `booleanAnd()`, `booleanOr()` | `&&`, `\|\|` | `true`, `false` |
| `string()` | Joining two strings | `""` |
| `list()`, `set()` | Joining two lists, or the union of two sets | An empty list, or an empty set |
| `firstOptional()`, `lastOptional()` | Keeping the first, or the last, present value | `Optional.empty()` |
| `maximum()`, `minimum()`, with or without a `Comparator` | Keeping the greater, or the lesser, present value | `Optional.empty()` |
| `update()` | Applying one `Update` and then the other | `Update.identity()` |

Each is a static method on `Monoids`, such as `Monoids.list()`.

### A monoid of your own {#a-monoid-of-your-own}

`Monoid.of(empty, combine)` builds one from its empty value and a `Semigroup`, which can be a lambda. Counting the units of each SKU across orders needs a monoid `Monoids` does not have: an empty map, and a merge that adds the counts.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monoid/MonoidBook.java:units}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monoid/MonoidBook.java:units_run}}
```

`Monoid.of` does not check the laws, so check them as [Three laws](#three-laws) does.

---

## What every monoid can do {#what-every-monoid-can-do}

Besides `combineAll`, every `Monoid` has two more default methods. `combineN(value, n)` combines a value with itself `n` times: `n` of `0` gives the empty value, and a negative `n` throws `IllegalArgumentException`. `isEmpty` tests whether a value is the empty value.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monoid/MonoidBook.java:defaults}}
```

---

## Choosing among optional values {#choosing-among-optional-values}

The optional monoids pick one value from many, by the `Comparator` you pass or the type's natural order. `firstOptional` suits a fallback chain: a setting from the environment, then one from a file, then a default.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monoid/MonoidBook.java:priciest}}
```

Each skips an empty `Optional`, and a list of only empty ones, or of none, gives `Optional.empty()`.

---

## Composing updates {#composing-updates}

Every monoid so far combines data. `Monoids.update()` combines behaviour. An `Update<S>` is a transformation from an `S` to a new `S`. It extends `UnaryOperator<S>`, so it goes wherever Java takes a `Function<S, S>`, such as `Stream.map`, and its `andThen` returns an `Update`, so a chain of them stays one. The monoid's `combine(f, g)` applies `f` and then `g`, and its empty value is `Update.identity()`, which returns its input unchanged.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monoid/MonoidBook.java:update}}
```

A step with nothing to change can be `Update.identity()`. That is what makes sparse updates work: the [`Edits` builder](../optics/multi_edit.md) folds optic edits through `Monoids.update()`, and a field the request left out contributes `Update.identity()`. Fold updates with `combineAll`, which applies them in a loop. A fold through `combine`, such as `foldMap`, nests one call per update, and a long enough list overflows the stack.

~~~admonish note title="For the functionally curious"
In functional programming this is the `Endo` monoid, for *endomorphism*, a function from a type to itself. Higher-Kinded-J names it `Update` for clarity.
~~~

---

## Three laws {#three-laws}

A `Semigroup` keeps one law and a `Monoid` two more. Together they say that grouping does not matter, and that the empty value adds nothing.

~~~admonish note title="Laws"
- **Associativity: grouping the combines differently changes nothing.** `combine(combine(a, b), c)` equals `combine(a, combine(b, c))`.
- **Left identity: combining the empty value first changes nothing.** `combine(empty(), a)` equals `a`.
- **Right identity: combining the empty value last changes nothing.** `combine(a, empty())` equals `a`.

Values are equal by `equals`. For `bigDecimalAddition`, the identity laws hold by `equals` for a value of scale zero or more, which includes any price. Added to `ZERO`, `new BigDecimal("1E+3")` comes back as `1000`: equal by `compareTo`, but not by `equals`.

Checking the `Semigroup` and `Monoid` laws with `hkj-test` is not supported yet, so the test checks them by hand, for `bigDecimalAddition` and for the units monoid. It uses these imports, with JUnit's:

<!-- verify -->
```java
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.higherkindedj.example.book.foundations.monoid.MonoidBook;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
```

```java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java:monoid_laws}}
```
~~~

---

## Things people get wrong {#things-people-get-wrong}

~~~admonish warning title="Common Misunderstandings"
- **"`doubleAddition` will do for money."** It rounds: `0.1` and `0.2` give `0.30000000000000004`, where `bigDecimalAddition` sums `0.10` and `0.20` to exactly `0.30`.
- **"`combine` must be commutative."** It must be associative, and that is all. `Monoids.string()` combines `"LAMP"` and `"BULB"` into `"LAMPBULB"`, and the other way round into `"BULBLAMP"`.
- **"Any semigroup becomes a monoid once you name an empty value."** Only if that value changes nothing. The `combineAll` of `Monoid.of("", Semigroups.string(", "))` turns `"LAMP"` and `"BULB"` into `", LAMP, BULB"`: it starts from `""`, and joining `""` adds a delimiter.
- **"`isEmpty` always compares with `equals`."** `bigDecimalAddition`'s is true for zero at any scale, so `0.00` is empty, although `BigDecimal.ZERO.equals(new BigDecimal("0.00"))` is false.
~~~

---

## The fine print {#the-fine-print}

### What takes a Semigroup or a Monoid {#what-takes-a-semigroup-or-a-monoid}

- **`Instances.eitherOrBoth(semigroup)` joins the warnings each step carries.** [EitherOrBoth](../monads/either_or_both_monad.md) shows how.
- **`Instances.writer(monoid)` combines each step's log, starting from the empty log.** [Writer](../monads/writer_monad.md) shows how.
- **`Foldable.foldMap(monoid, f, fa)` turns each element into a monoid value and combines them.** [Foldable and Traverse](foldable_and_traverse.md) shows how.
- **`Const`'s `Applicative` takes a monoid to combine what it collects.** [Const](../monads/const_type.md) shows how.

---

~~~admonish tip title="See Also"
- [Foldable and Traverse](foldable_and_traverse.md): folding any container with a monoid
- [Applicative](applicative.md): how `Validated` combines errors with a semigroup
- [Multi-Edit and Sparse Updates](../optics/multi_edit.md): the `Edits` builder that `Monoids.update()` powers
~~~

---

**Previous:** [Type Classes](ch_intro.md)
**Next:** [Foldable and Traverse](foldable_and_traverse.md)
