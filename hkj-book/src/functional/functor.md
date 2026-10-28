<!-- description: Map a function inside any container with Functor: write one method for Maybe, Either and List, and check an instance against the two laws with hkj-test. -->

# Functor: The "Mappable" Type Class

_Name the `map` every container shares, and write one method that maps inside any of them._

~~~admonish info title="What You'll Learn"
- Recognise `map` on `Optional`, `List`, `CompletableFuture` and `MaybePath` as one shape: `Functor`
- Write a method that maps inside any container, through its `Functor`
- Say what `map` promises, and what it leaves to each container
- Check an instance against the two functor laws with `hkj-test`'s `FunctorLaws`
~~~

~~~admonish example title="See Example Code"
**The code on this page is [FunctorBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/functor/FunctorBook.java)**, with `totals` from [FoundationsIntroBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/intro/FoundationsIntroBook.java): the page includes both, so the build compiles and runs them. [FunctorBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/functor/FunctorBookTest.java) holds each claim the page makes, and [FoundationsLawsBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java) checks the laws.
~~~

---

You have written this code:

<!-- verify -->
```java
optional.map(String::length);
list.stream().map(String::length).toList();
completableFuture.thenApply(String::length);
maybePath.map(String::length);
```

Four method calls, four containers, the same shape. `Functor` is the name of that shape: a container's `map`, keeping the two laws on this page. Most type classes in this chapter work this way, naming a capability you already use.

For one container, an [Effect Path](../effect/ch_intro.md) has its own `map`, as `maybePath.map` shows, and application code stays there. This page is for a method written once for any container, as the [Foundations](../hkts/foundations_intro.md) introduction's `totals` is.

The code on this page uses these imports, with `Order` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Instances.functor;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
```

## What `map` promises {#what-map-promises}

`Functor<F>` has one method, `map`:

<!-- verify -->
```java
public interface Functor<F extends WitnessArity<TypeArity.Unary>> {
  <A, B> Kind<F, B> map(Function<? super A, ? extends B> f, Kind<F, A> fa);
}
```

Given a function from `A` to `B` and a [`Kind<F, A>`](../hkts/core-concepts.md#the-witness-and-kind), it returns a `Kind<F, B>`. It applies the function to the values inside and keeps the container's shape:

| The container holds | `map` gives | The function runs |
|---|---|---|
| Three orders, in a `List` | A `List` of three results | Once for each order |
| One order, in a `Just` or a `Right` | A `Just` or a `Right` of the result | Once |
| A deferred order, in an [`IO`](../monads/io_monad.md) | A new `IO` | Each time that `IO` runs |
| No value: a `Nothing`, an empty `Optional` or an empty `List` | The container, still empty | Never |
| A failure: a `Left` or a failed `Try` | The same failure | Never |

The function sees the values, never the container. The table assumes a function that returns a value and does not throw. [Values, never null](../hkts/core-concepts.md#values-never-null) says what each type does when it returns `null`.

## One method for any container {#one-method-for-any-container}

The introduction's `totals` is written over `Functor`. It takes the `Functor` for `F` and calls `map` on it:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/intro/FoundationsIntroBook.java:generic}}
```

On containers that hold an order, it gives their totals, as [the introduction](../hkts/foundations_intro.md) shows. On containers that hold none, it keeps their shape:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/functor/FunctorBook.java:shapes}}
```

Nothing in `totals` checks for an empty container or a failure. Each container's `Functor` knows what mapping means there: `Maybe`'s gives back the `Nothing`, and `Either`'s gives back the `Left` as it was. That is why `totals` is written once.

## Two laws {#two-laws}

A `Functor` keeps two laws, and you have assumed both whenever you called `map`. Together they say that `map` changes the values and never the container.

~~~admonish note title="Laws"
- **Identity: mapping `a -> a` changes nothing.** `map(a -> a, fa)` equals `fa`.
- **Composition: two maps equal one map of both functions.** `map(g, map(f, fa))` equals `map(f.andThen(g), fa)`.

Two containers are equal when they hold equal values in the same shape. For a deferred container such as `IO`, they are equal when running both gives equal results. Both laws assume functions that never return `null`, and [Values, never null](../hkts/core-concepts.md#values-never-null) says what each type does with one.

`FunctorLaws`, in [`hkj-test`](../tooling/test_assertions.md#adding-the-dependency)'s `org.higherkindedj.hkt.laws` package, checks both laws on any instance, your own included. You pass it the equality to use. `KindEquivalence.byEqualsAfter`, in `org.higherkindedj.hkt.assertions`, builds one from a function it applies to both sides, and compares the two results with `equals`: `MAYBE::narrow` unwraps a `Maybe`, and for an `IO` the function also runs it. A test of `Maybe` and `IO` uses these imports, with JUnit's:

<!-- verify -->
```java
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
```

The test checks both laws on a `Just`, a `Nothing` and an `IO`, where `ORDER` is any `Order`:

```java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java:functor_laws}}
```
~~~

## Back to the one-liner {#back-to-the-one-liner}

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/oneliner/OneLinerBook.java:line}}
```

`.map(...)` is the line's Functor layer: on a `Right` it changes the order's email, and a `Left` passes through untouched. [Two encodings, one algebra](../hkts/one_line_six_layers.md#two-encodings-one-algebra) matches it to `Functor`'s `map` over `Kind`.

## Things people get wrong {#things-people-get-wrong}

~~~admonish warning title="Common Misunderstandings"
- **"Mapping iterates."** Only over a collection. `map` runs the function at most once on an `Optional`, and on an `Either` only for a `Right`.
- **"`map` runs the function now."** Not on a deferred container. `IO`'s `map` builds a new `IO`, and the function runs only when that `IO` runs. `Functor` promises the shape, not when the function runs.
- **"It is `Stream.map` with extra steps."** The aim is one method for every container that can be mapped. You pay some ceremony at the boundary, the `widen` and `narrow`, so that the method's body is written once.
- **"`map` can run a step that fails."** A function that returns an `Either` gives an `Either` inside an `Either`, such as `Right(Left(order already shipped))`. A step that can fail needs `flatMap`, which [Monad](monad.md) teaches.
~~~

~~~admonish tip title="You can ship now"
You can now write a method that maps inside any container, as `totals` does, and check an instance of your own against the two laws. The rest of this page is for which types have a `Functor`, and what builds on it.
~~~

---

## The fine print {#the-fine-print}

### Which types have a `Functor` {#which-types-have-a-functor}

| Types | Their `Functor` comes from |
|---|---|
| Every token in `Witnesses` | Their `Monad`, so [`Instances.functor(token)` always succeeds](instances_facade.md#what-the-compiler-checks) |
| `Validated`, `Writer`, `EitherOrBoth` and the transformers | Their `Monad`, from `Instances` with its argument, such as `Instances.validated(semigroup)` |
| `Free` | Its `Monad`, built with `new FreeMonad<>()` |
| `Const`, `FreeAp` and `Fetch` | An `Applicative`, with no `Monad` |
| `EitherF` | `EitherFFunctor` alone |
| `Coyoneda` | `CoyonedaFunctor`, which gives a `Functor` to a type that has none, as [Coyoneda](../monads/coyoneda.md) shows |

### What builds on `Functor` {#what-builds-on-functor}

- **`Applicative` extends `Functor`, and `Monad` extends `Applicative`.** Every instance on the next pages has this `map`.
- **`Traverse` extends both `Functor` and `Foldable`.** [Foldable and Traverse](foldable_and_traverse.md) covers it.
- **`Bifunctor` is not a `Functor`.** It maps a type with two parameters, such as both sides of an `Either`, as [Bifunctor](bifunctor.md) shows.

---

~~~admonish tip title="See Also"
- [Applicative](applicative.md): combining independent computations
- [Monad](monad.md): sequencing dependent computations with `flatMap`
- [Bifunctor](bifunctor.md): mapping over types with two parameters
- [One Line, Six Layers](../hkts/one_line_six_layers.md): where `map` sits in a whole service method
~~~

~~~admonish tip title="Further Reading"
- **Magnus Smith**, [Functors and Monads with Java and Scala](https://blog.scottlogic.com/2025/03/31/functors-monads-with-java-and-scala.html): a practical guide to functors and monads in Java
- **Bartosz Milewski**, [Functors](https://bartoszmilewski.com/2015/01/20/functors/): functors from category theory to code
- **Mark Seemann**, [Functors](https://blog.ploeh.dk/2018/03/22/functors/): a practical introduction, with examples in languages close to Java
~~~

~~~admonish info title="Hands-On Learning"
Practise Functor mapping in [Tutorial 02: Functor Mapping](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial02_FunctorMapping.java) (7 exercises).
~~~

---

**Previous:** [Obtaining Instances](instances_facade.md)
**Next:** [Applicative](applicative.md)
