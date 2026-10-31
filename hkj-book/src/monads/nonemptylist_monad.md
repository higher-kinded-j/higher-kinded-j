<!-- description: Hold a Java list that can never be empty with NonEmptyList, so head, reduce and max never throw, and use it for the errors of an invalid result. -->

# NonEmptyList: A List That Is Never Empty

_Hold a list that always has a first element, so reading it never throws._

~~~admonish abstract title="NonEmptyList at a glance"
| | |
|---|---|
| **Models** | A list with at least one element |
| **Replaces** | A `List` checked with `isEmpty()` before `getFirst()` |
| **Effect Path** | None: a Path holds it, as [`ValidationPath`](../effect/path_validation.md) holds its errors |
{{#include ../../../hkj-examples/src/test/resources/golden/foundations-capabilities.md.golden:nonemptylist_monad}}
| **Choose it when** | The list can never be empty, such as an invalid result's errors; otherwise [List](list_monad.md), or compare every type in [Choosing a Type](supported-types.md#choosing-a-type) |
~~~

~~~admonish info title="What You'll Learn"
- Turn a list that may be empty into a `NonEmptyList` once, at the boundary
- Total a list with `reduce`, and read its first or largest element, with nothing to guard
- Say why `List<String>` is the wrong type for an invalid result's errors
- Choose between `NonEmptyList`'s `ap` and its `semigroup()` for combining two lists
- Say why `NonEmptyList` has no empty value and no `Monoid`
~~~

~~~admonish example title="See Example Code"
**The code on this page is [NonEmptyListBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/nonemptylist/NonEmptyListBook.java)**: the page includes it, so the build compiles and runs it. [NonEmptyListBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/nonemptylist/NonEmptyListBookTest.java) holds what the page says each method does.
~~~

---

## An order's lines {#an-orders-lines}

Code that reads the first element of a `List`, or totals it, must first check `isEmpty()`: `getFirst()` throws on an empty list, and a total needs a starting value. `NonEmptyList<A>` holds at least one element by its type, so the check happens once, where the list is made, and nowhere after.

The code on this page uses these imports, with `Order` and `LineItem` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.Validated;
```

An order has a `List` of lines, and an order with none should not reach the code that totals it. `NonEmptyList.fromList` makes the check: it gives a `Maybe`, empty for an empty list. `PLACED` has two lines, and `EMPTIED` none:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/nonemptylist/NonEmptyListBook.java:lines}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/nonemptylist/NonEmptyListBook.java:line_outcomes}}
```

Past that check, the code needs no guard. `reduce` totals the lines with `Monoids.bigDecimalAddition()`, the book's way to add money, and uses only its join, never its empty value, because there is always a first line. `max` and `head` return a line, never an `Optional`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/nonemptylist/NonEmptyListBook.java:total}}
```

`Maybe`'s `map` runs them only on an order that has lines:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/nonemptylist/NonEmptyListBook.java:total_outcomes}}
```

The same code over a `List` would need a starting value for the total, such as `BigDecimal.ZERO`, and an `Optional` for the largest line.

## Creating instances {#creating-instances}

Java has no literal for a list that is never empty, so each factory names where the first element comes from:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/nonemptylist/NonEmptyListBook.java:construct}}
```

`fromList` and `fromIterable` take data that may be empty, and give a `Maybe` rather than throwing. Every element is checked when the list is built, so a `null` is refused, as [Values, never null](../hkts/core-concepts.md#values-never-null) says for every type.

## As the error list {#as-the-error-list}

An invalid result always has at least one error, yet its errors are often held in a `List`, which can be empty. Nothing stops the impossible case being built:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/nonemptylist/NonEmptyListBook.java:empty_errors}}
```

`noReason.getError().getFirst()` throws `NoSuchElementException`. With a `NonEmptyList` as the error list, the impossible case cannot be built, and reading the first error needs no guard:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/nonemptylist/NonEmptyListBook.java:nel_errors}}
```

[Validated](validated_monad.md)'s `validNel`, `invalidNel`, `accumulate()` and `fields()` all hold the errors in a `NonEmptyList`, and Cats pairs its `Validated` with a `NonEmptyList` in the same way. `accumulate()` and `fields()` need no `Semigroup` argument. Neither does a `ValidationPath` built with `Path.validNel` or `Path.invalidNel`, which carries `NonEmptyList.semigroup()` with it. `Validated`'s own `ap`, and `Instances.validated`, still take one: pass `NonEmptyList.semigroup()`, which joins two error lists end to end, so the errors keep the order the checks ran.

## Methods {#methods}

| Method | What it does |
|---|---|
| `NonEmptyList.of(first, rest...)`, `single(a)` | Build a list from its first element and any others |
| `NonEmptyList.fromList(list)`, `fromIterable(items)` | Give a `Maybe`, empty for empty input |
| `head()`, `last()` | Return the first or last element |
| `tail()` | Returns the elements after the first, as a `List` that cannot be changed, possibly empty |
| `reduce(semigroup)` | Joins every element, with no starting value |
| `min(comparator)`, `max(comparator)` | Return the smallest or largest element |
| `map(f)`, `flatMap(f)` | Change each element, or replace each with a `NonEmptyList` |
| `concat(other)`, `append(a)`, `prepend(a)` | Join two lists, or add one element |
| `foldLeft(start, f)`, `reverse()`, `size()` | Fold from the left, reverse, or count |
| `stream()`, `toJavaList()` | Give a `Stream`, or a `List` that cannot be changed |
| `NonEmptyList.semigroup()` | Joins two lists end to end, keeping their order |

`NonEmptyList` is also `Iterable`, so a for-each loop reads it directly.

## Working with Kind {#working-with-kind}

Generic code, written once for any container, takes `NonEmptyList` through its `Monad` instance, from `Instances.monad(nonEmptyList())`. The instance works over `Kind<NonEmptyListKind.Witness, A>`, so you widen a list to pass it in, and narrow the result to read it. These are the extra imports:

<!-- verify -->
```java
import static org.higherkindedj.hkt.instances.Witnesses.nonEmptyList;
import static org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper.NON_EMPTY_LIST;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.nonemptylist.NonEmptyListKind;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/nonemptylist/NonEmptyListBook.java:kind}}
```

~~~admonish tip title="Two ways to combine"
`NonEmptyList.semigroup()` joins two lists end to end: `[a]` and `[b]` give `[a, b]`. The instance's `ap` pairs every element of one with every element of the other, as `List`'s does. Error lists are joined with the semigroup.
~~~

Widening allocates nothing: `NonEmptyList` implements `NonEmptyListKind` itself, so `widen` returns the same object. `NonEmptyList` has instances of other type classes too, such as `Traverse`, each an object of its own, and *NonEmptyList at a glance*, at the head of the page, lists them.

~~~admonish note title="No empty, by design"
No factory builds an empty `NonEmptyList`, so it has no `Monoid` instance and no `Alternative` or `MonadZero`. Each of those needs an empty value, which would bring back the case the type exists to rule out. `NonEmptyList.semigroup()` joins two lists, but nothing gives a `Monoid`'s empty one.
~~~

## When to use `NonEmptyList` {#when-to-use-nonemptylist}

| You need | Use |
|---|---|
| A list that can never be empty, such as an invalid result's errors | `NonEmptyList` |
| A list that may be empty | [List](list_monad.md) |
| The errors of independent checks, kept together | [Validated](validated_monad.md), whose `invalidNel` holds them in a `NonEmptyList` |
| A record built from many checked fields | [Accumulating Assembly](validated_assembly.md) |

---

**Previous:** [EitherOrBoth](either_or_both_monad.md)
**Next:** [Maybe](maybe_monad.md)
