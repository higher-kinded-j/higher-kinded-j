<!-- description: Filter inside a monadic chain with MonadZero: drop a value with filter, guard a For comprehension with when, and check the zero laws. -->

# MonadZero: Filtering in Monadic Chains

_Give a monad an empty value, and a chain can drop whatever fails a test._

~~~admonish info title="What You'll Learn"
- Say what a `zero` adds to a `Monad`, and why a chain ends at one
- Drop a value that fails a test with `filter`, in a method written once for any container
- Guard a step of a `For` comprehension with `when`
- Name the types that have a zero, and what each one's is
- Check an instance against the two zero laws
~~~

~~~admonish example title="See Example Code"
**The code on this page is [MonadZeroBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monadzero/MonadZeroBook.java)**: the page includes it, so the build compiles and runs it. [MonadZeroBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/monadzero/MonadZeroBookTest.java) holds what the page says each instance does, and [FoundationsLawsBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java) checks the laws.
~~~

---

## A monad with an empty value {#a-monad-with-an-empty-value}

`MonadZero` extends [Monad](monad.md) and [Alternative](alternative.md), and adds `zero()`: a container with nothing in it, which is also its `Alternative`'s `empty()`. A `flatMap` over a zero has no value to run its step on, so the chain ends there. For a `List`, each element runs the rest of the chain on its own, so a zero ends only that element's run. That is enough to filter: replace a value that fails a test with a zero. If you have called `stream.filter` or `optional.filter`, you have done this for one container.

On an [Effect Path](../effect/ch_intro.md), `MaybePath.filter` does it for a `Maybe`, and application code stays there. This page is for a method written once for any container. Its code uses these imports, with `LineItem` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
```

The examples use two baskets:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monadzero/MonadZeroBook.java:baskets}}
```

## Filtering with `filter` {#filtering-with-filter}

`filter(predicate, ma)` keeps a value that passes and gives a zero for one that fails: it is `flatMap(a -> predicate.test(a) ? of(a) : zero(), ma)`, with the arguments in `flatMap`'s order, the test first. Written once over `MonadZero`, one method filters any container that has a zero:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monadzero/MonadZeroBook.java:generic}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monadzero/MonadZeroBook.java:filter}}
```

Each type's zero is what a failed test leaves:

| Type | `zero()` | A failed test |
|---|---|---|
| `List` | `[]` | Drops that element from the list |
| `Maybe` | `Nothing` | Ends the chain at `Nothing` |
| `Optional` | `Optional.empty()` | Ends the chain at an empty `Optional` |
| `Stream` | An empty stream | Drops that element, lazily |

`Instances.monadZero(token)` gives the instance for `list()`, `maybe()`, `optional()` and `stream()`, and for any other token fails when it runs, as [what the compiler checks](instances_facade.md#what-the-compiler-checks) explains.

## Guarding a `For` comprehension with `when` {#guarding-a-for-comprehension-with-when}

A `For` comprehension given a `MonadZero`, as in `For.from(monadZero, ...)`, has a `when(test)` step, and so do `For.par` and `ForState.withState` given one. When the test fails for a combination, that combination gives a zero, and the comprehension drops it. The test sees what the comprehension holds so far: one value as itself, two or more as a tuple, and in `ForState` the state. Here it holds a basket and a line, so `t._2()` is the line, and `when` keeps only the lines that ask for more than one unit:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monadzero/MonadZeroBook.java:when}}
```

A comprehension started with a plain `Monad` has no `when`, and javac says so:

<!-- verify:rejects "cannot find symbol" -->
```java
For.from(Instances.monad(list()), LIST.widen(List.of(1, 2, 3)))
    .when(n -> n > 1)
    .yield(n -> n);
```

javac refuses it with "cannot find symbol", naming `when`. [For Comprehension](for_comprehension.md#filtering-with-when) shows `when` in a longer chain.

## Two laws {#two-laws}

A `MonadZero` keeps two laws beyond the `Monad` and `Alternative` ones. Together they say a zero ends a chain whichever end it is at.

~~~admonish note title="Laws"
- **Left zero: no step runs after a zero.** `flatMap(f, zero())` equals `zero()`.
- **Right zero: a step that gives a zero makes the chain a zero.** `flatMap(a -> zero(), fa)` equals `zero()`.

Two containers are equal when they hold equal values in the same shape. The laws assume functions that never return `null`, and [Values, never null](../hkts/core-concepts.md#values-never-null) says what each type does with one.

Checking these laws with `hkj-test` is not supported yet, so the test checks them by hand, for `Maybe` and `List`. `ORDER` is any `Order`, and the test uses these imports, with JUnit's:

<!-- verify -->
```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.MaybeKind;
```

```java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java:monad_zero_laws}}
```
~~~

## Things people get wrong {#things-people-get-wrong}

~~~admonish warning title="Common Misunderstandings"
- **"Filtering needs a `For` comprehension."** `filter(predicate, ma)` filters directly, as `bulk` does.
- **"`when` works with any `Monad`."** Only a comprehension given a `MonadZero` has it.
- **"A filtered `Maybe` keeps the value it failed on."** It gives `Nothing`, and the value is gone, as it is from `optional.filter`.
- **"`Either` has a zero."** `Either` has no empty value to give, since a `Left` must hold an error. `Instances.monadZero(either())` compiles, and throws `ClassCastException` when it runs.
~~~

---

~~~admonish tip title="See Also"
- [Alternative](alternative.md): `empty` and `orElse`, which every `MonadZero` has
- [For Comprehension](for_comprehension.md): `when` among the other comprehension steps
~~~

---

**Previous:** [Alternative](alternative.md)
**Next:** [Applicative, Selective or Monad?](abstraction_levels.md)
