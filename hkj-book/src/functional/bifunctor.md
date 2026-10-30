<!-- description: Map an Either's error, its value or both with Bifunctor's bimap, write one method for any two-sided type, and check its laws with hkj-test. -->

# Bifunctor: Mapping Over Both Sides

_Map an error, a value or both, with one method written for any type with two sides._

~~~admonish info title="What You'll Learn"
- Map either side of a type with two type arguments, or both, with `bimap`, `first` and `second`
- Write one method for any two-sided type, through its `Bifunctor`
- Predict which of `bimap`'s two functions runs, for each instance
- Tell a type's `Bifunctor` from its `Functor`, and know which to use
- Check an instance against the four Bifunctor laws with `hkj-test`'s `BifunctorLaws`
~~~

~~~admonish example title="See Example Code"
**The code on this page is [BifunctorBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/bifunctor/BifunctorBook.java)**, with `OrderError` from [OrderError.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/OrderError.java): the page includes both, so the build compiles and runs them. [BifunctorBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/bifunctor/BifunctorBookTest.java) holds what the page says each instance does, and [FoundationsLawsBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java) checks the laws. [BifunctorExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/bifunctor/BifunctorExample.java) maps an `Either`, a `Tuple2`, a `Validated` and a `Writer` in turn.
~~~

---

## Two sides, two functions {#two-sides-two-functions}

If you have caught a service's exception and thrown your own, while converting what it returns, you have written a `bimap` by hand. With the error held as a value, `bimap` converts both sides without throwing, and one method can serve any type with two sides.

An `Either<L, R>` holds an error or a value, and a `Tuple2<A, B>` holds two values. A [Functor](functor.md) maps one type argument and leaves the other alone. `Bifunctor` maps either of the two, or both.

For one value, each type has its own `bimap`, such as `Either.bimap` or an [Effect Path](../effect/ch_intro.md)'s `EitherPath.bimap`, and application code stays there. This page is for a method written once for any type with two sides. `Bifunctor<F>` declares `bimap`, and builds `first` and `second` from it:

<!-- verify -->
```java
public interface Bifunctor<F extends WitnessArity<TypeArity.Binary>> {
  <A, B, C, D> Kind2<F, C, D> bimap(
      Function<? super A, ? extends C> f, Function<? super B, ? extends D> g, Kind2<F, A, B> fab);

  default <A, B, C> Kind2<F, C, B> first(Function<? super A, ? extends C> f, Kind2<F, A, B> fab) {
    return bimap(f, Function.identity(), fab);
  }

  default <A, B, D> Kind2<F, A, D> second(Function<? super B, ? extends D> g, Kind2<F, A, B> fab) {
    return bimap(Function.identity(), g, fab);
  }
}
```

`bimap` takes a function for each side. `first` maps only the first side and `second` only the second, each passing `Function.identity()` for the side it leaves. `F` has [binary arity](../hkts/type-arity.md#unary-and-binary), so `Kind2<F, A, B>` keeps both type arguments open.

The code on this page uses these imports, with `Order` and `OrderStatus` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import org.higherkindedj.example.book.foundations.OrderError;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherBifunctor;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedBifunctor;
```

## One method for any two-sided type {#one-method-for-any-two-sided-type}

The order service refuses a request with an `OrderError`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/OrderError.java:order_error}}
```

A caller sees a message rather than the `OrderError`, and the order's status rather than the whole `Order`. `forCaller` makes that change on both sides, for any type with a `Bifunctor`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/bifunctor/BifunctorBook.java:generic}}
```

The same method runs on an `Either` and on a `Validated`, where `ORDER` is any `Order` with status `NEW`. `widen2` and `narrow2` are `widen` and `narrow` for a `Kind2`, as [Bifunctor and Functor](#bifunctor-and-functor) explains:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/bifunctor/BifunctorBook.java:run}}
```

`forCaller` never asks which side it was given. Each instance applies the function for the side its value holds.

## What `bimap` runs {#what-bimap-runs}

Whether one function runs or both depends on the instance:

| Type | `bimap` runs | Instance |
|---|---|---|
| `Either`: a `Left` or a `Right` | The first function on a `Left`, the second on a `Right` | `EitherBifunctor.INSTANCE` |
| `Validated`: an `Invalid` or a `Valid` | The first on an `Invalid`, the second on a `Valid` | `ValidatedBifunctor.INSTANCE` |
| `EitherOrBoth`: a `Left`, a `Right` or a `Both` | One on a `Left` or a `Right`, and both on a `Both` | `EitherOrBothBifunctor.INSTANCE` |
| `Tuple2`: two values | Both | `Tuple2Bifunctor.INSTANCE` |
| `Writer`: a log and a value | Both: the first on the log, the second on the value | `WriterBifunctor.INSTANCE` |
| `Const`: a value, and a type it holds no value of | Only the first: the second changes a type, and never runs | `ConstBifunctor.INSTANCE` |

These are all the instances Higher-Kinded-J ships. `Instances` has no entry for a type class with a binary witness, so take each from its `INSTANCE` field. [Const](../monads/const_type.md) explains why its second type is a [phantom type](../glossary/type-system.md#phantom-type).

## Bifunctor and Functor {#bifunctor-and-functor}

`Either` has two witnesses. `EitherKind.Witness<L>` fixes the error type, and is the one `Functor`, `Monad` and `MonadError` take. `EitherKind2.Witness` leaves both types open, and is the one `Bifunctor` takes. From the same `Either`, `EITHER.widen` gives the `Kind` a `Functor` takes, and `EITHER.widen2` the `Kind2` a `Bifunctor` takes.

`Instances.functor(either())` maps only the `Right`, as `second` does. Use the `Functor` when only the value changes, and the `Bifunctor` when the error changes too.

## Four laws {#four-laws}

A `Bifunctor` keeps four laws. The first two are [Functor's](functor.md#two-laws) laws for both sides at once. The last two tie `first` and `second` to `bimap`, and matter because most instances write their own, as `Either`'s `first` calls `mapLeft`.

~~~admonish note title="Laws"
- **Identity: mapping both sides with `a -> a` changes nothing.** `bimap(a -> a, b -> b, fab)` equals `fab`.
- **Composition: two bimaps equal one bimap of both pairs.** `bimap(f2, g2, bimap(f1, g1, fab))` equals `bimap(f1.andThen(f2), g1.andThen(g2), fab)`.
- **First: `first` is a `bimap` that leaves the second side alone.** `first(f, fab)` equals `bimap(f, b -> b, fab)`.
- **Second: `second` is a `bimap` that leaves the first side alone.** `second(g, fab)` equals `bimap(a -> a, g, fab)`.

Two values are equal when they hold equal values on the same sides. The laws assume functions that never return `null`, and [Values, never null](../hkts/core-concepts.md#values-never-null) says what each type does with one.

`BifunctorLaws`, in [`hkj-test`](../tooling/test_assertions.md#adding-the-dependency)'s `org.higherkindedj.hkt.laws` package, checks all four on any instance, your own included. You pass it the equality to use. `KindEquivalence` builds equalities for a `Kind`, not a `Kind2`, so the test writes this one as a lambda that narrows both sides. It uses these imports, with JUnit's:

<!-- verify -->
```java
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.tuple.Tuple2KindHelper.TUPLE2;

import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.example.book.foundations.OrderError;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherBifunctor;
import org.higherkindedj.hkt.laws.BifunctorLaws;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.hkt.tuple.Tuple2Bifunctor;
```

The test checks the laws on an `Either`, both a `Right` and a `Left`, and on a `Tuple2`, where `ORDER` is any `Order`:

```java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java:bifunctor_laws}}
```
~~~

## Things people get wrong {#things-people-get-wrong}

~~~admonish warning title="Common Misunderstandings"
- **"`bimap` runs both functions."** Only where both sides hold a value: a `Tuple2`, a `Writer` and an `EitherOrBoth`'s `Both`. An `Either` or a `Validated` runs one.
- **"`bimap` can turn a `Left` into a `Right`."** It maps each side in place, so a `Left` stays a `Left`. To recover from an error, use `EitherPath.recover` or [MonadError](monad_error.md#recovering).
- **"`Const`'s `second` changes its value."** It changes only a type. A `Const` holds no value of its second type, so the function never runs.
- **"A type's `Functor` is its `Bifunctor`."** They are separate instances, over separate witnesses. `Either`'s `Functor` maps only the `Right`, as its `Bifunctor`'s `second` does.
~~~

---

~~~admonish tip title="See Also"
- [Profunctor](profunctor.md): two type arguments, where the first is what a function takes
- [Functor](functor.md): mapping a container with one type argument
- [Either](../monads/either_monad.md), [Validated](../monads/validated_monad.md) and [EitherOrBoth](../monads/either_or_both_monad.md): two-sided types whose first side is an error or a warning
- [Type Arity](../hkts/type-arity.md): binary witnesses, and how they differ from unary ones
~~~

~~~admonish tip title="Further Reading"
- **Mark Seemann**, [Bifunctors](https://blog.ploeh.dk/2018/12/24/bifunctors/): bifunctors explained with `Either` and tuples
~~~

---

**Previous:** [Applicative, Selective or Monad?](abstraction_levels.md)
**Next:** [Profunctor](profunctor.md)
