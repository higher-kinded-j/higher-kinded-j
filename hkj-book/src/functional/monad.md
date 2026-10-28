<!-- description: Chain steps that need the last result with Monad's flatMap: write one chain for any container, see what each instance does, and check the laws with hkj-test. -->

# Monad: Sequencing Dependent Computations

_Chain steps that each need the last result, and let each container's instance decide what a failure does._

<!-- source: Voltaire, Candide, ou l'Optimisme (1759), Chapter XXX. English text from the Boni and Liveright edition (New York, 1918), introduction by Philip Littell, checked against Project Gutenberg #19942; the French original ("Tous les événements sont enchaînés dans le meilleur des mondes possibles") checked against #4650 -->
> _"There is a concatenation of events in this best of all possible worlds: for if you had not been kicked out of a magnificent castle for love of Miss Cunegonde: if you had not been put into the Inquisition: … you would not be here eating preserved citrons and pistachio-nuts."_
>
> — Voltaire, *Candide* (Chapter XXX)

Voltaire's Pangloss tells a chain of `flatMap` calls along its happy path: each event needs the one before it. What a chain does when a link fails is left to the instance.

~~~admonish info title="What You'll Learn"
- Recognise `Optional.flatMap`, `thenCompose`, `Stream.flatMap` and a Path's `via` as one shape: `Monad`'s `flatMap`
- Write a chain once for any container, through its `Monad`
- Predict what each instance's `flatMap` does when a step has no value to work on
- Use `flatMapIfOrElse`, `as`, `asUnit`, `peek` and `flatMap2` to `flatMap5` in place of hand-written lambdas
- Check an instance against the three monad laws with `hkj-test`'s `MonadLaws`
~~~

~~~admonish example title="See Example Code"
**The code on this page is [MonadBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monad/MonadBook.java)**: the page includes it, so the build compiles and runs it. [MonadBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/monad/MonadBookTest.java) holds what the page says each instance does, and [FoundationsLawsBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java) checks the laws.
~~~

---

## When the next step needs the last result {#when-the-next-step-needs-the-last-result}

You have written this code:

<!-- verify -->
```java
optional.flatMap(order -> findPayment(order));             // an Optional<Payment>
future.thenCompose(order -> fetchPayment(order));          // a CompletableFuture<Payment>
orders.stream().flatMap(order -> order.lines().stream());  // a Stream<LineItem>
maybePath.via(order -> paymentOf(order));                  // a MaybePath<Payment>
```

Each line runs a step that needs the order, and the step gives back a container of the same kind, which the call flattens into one. Java spells that shape two ways, and a Path a third. `Monad` is its name: a container's `flatMap`, keeping the three laws on this page.

For one container, an [Effect Path](../effect/ch_intro.md) chains with `via`, as `maybePath.via` does, and application code stays on the Paths. This page is for a method written once for any container.

~~~admonish tip title="Plain Java wins here"
A chain over one `Optional`, one `CompletableFuture` or one `Stream` needs nothing from this page: `flatMap` and `thenCompose` already chain them. `Monad` earns its place only when one method must serve any container.
~~~

The code on this page uses these imports, with `Order` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
```

## What a `Monad` adds {#what-a-monad-adds}

`Monad<M>` extends [`Applicative<M>`](applicative.md) with one method, and builds `flatMap2` to `flatMap5` from it. Its source names the container `M`, where this page's own code says `F`:

<!-- verify -->
```java
public interface Monad<M extends WitnessArity<TypeArity.Unary>> extends Applicative<M> {

  <A, B> Kind<M, B> flatMap(Function<? super A, ? extends Kind<M, B>> f, Kind<M, A> ma);

  // abridged: the null checks and the other default methods are left out,
  // and flatMap3, flatMap4 and flatMap5 each build on the one before
  default <A, B, R> Kind<M, R> flatMap2(
      Kind<M, A> ma, Kind<M, B> mb, BiFunction<? super A, ? super B, ? extends Kind<M, R>> f) {
    return flatMap(a -> flatMap(b -> f.apply(a, b), mb), ma);
  }
}
```

`map` takes an `A -> B`. `flatMap` takes an `A -> Kind<M, B>`: a step that gives back a container of its own. With `map`, that step would leave a container inside a container, a `Kind<M, Kind<M, B>>`. `flatMap` flattens the step's result into one `Kind<M, B>`.

When no step needs another's result, `map2` to `map5` are enough, and the less powerful tool is the one to pick. [Should you use map3 or flatMap?](applicative.md#map3-or-flatmap) says what they can do that `flatMap` cannot, and [Choosing Your Abstraction Level](abstraction_levels.md) walks through the choice.

## One chain for any container {#one-chain-for-any-container}

`markPaid` is written over `Monad`. It takes the `Monad` for `F` and calls `flatMap`: the step marks the order paid and saves it, so it needs the order `found` holds:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monad/MonadBook.java:generic}}
```

Run it with `Either`'s `Monad`, on an order that was found and on one that was not, then with `IO`'s. `ORDER` is any `Order` that has not been paid for, and `store` adds an order to the list `stored` and hands it back:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monad/MonadBook.java:instances}}
```

`Either`'s instance runs `save` on a `Right`, and passes a `Left` on without running it. `IO`'s runs nothing: `markPaid` gives back an `IO` that has not run, and the order is saved only when it runs.

## What each instance does {#what-each-instance-does}

`Monad` promises that `flatMap` hands the step the value when there is one. What it does otherwise, and when the step runs, is each instance's own. `Either`'s keeps a success track and a failure track:

<pre class="hkj-railway-diagram" role="img" aria-label="Either's two tracks through markPaid. On the Right track, the order that was found is marked paid and saved, and the result is the saved order. A Left from the lookup stays on the Left track, and save never runs. A Left from save also ends on the Left track.">
         found    mark paid, save
Right  ════●═════════════●══════▶  Right(saved order)
                          ╲  save gives a Left
Left   ────●───────────────●────▶  Left(error)
         a Left from found:
         save never runs
</pre>

The diagram shows two ways to end on the `Left` track: a `Left` from the lookup, which `save` never sees, and a `Left` from `save` itself. With one step, the instances you will meet most behave like this:

| Instance | What `flatMap` does with its input and the step |
|---|---|
| `Either`, `Validated` | Runs the step on a `Right` or a `Valid`. A `Left` or an `Invalid` passes on unchanged, and the step never runs |
| `Try` | The same with a `Success` and a `Failure`, and an exception the step throws becomes a `Failure` |
| `Maybe`, `Optional` | Runs the step on a value. An empty input stays empty, and the step never runs |
| `List`, `NonEmptyList` | Runs the step on every element and joins the results in order: two inputs that each give two results give four |
| `Stream` | The same, lazily: the step runs on each element as the result is consumed |
| [`IO`](../monads/io_monad.md), `VTask` | Builds a new deferred value and runs nothing. When it runs, it runs the input, then the step. If the input fails, the step never runs |
| `CompletableFuture` | Starts the step when the input completes. If the input fails, the result fails and the step never runs |
| `Writer`, from `Instances.writer(monoid)` | Runs the step, and joins the input's log and the step's, in that order, with the monoid |

So stopping at a failure, known as short-circuiting, comes from the instance, not from `Monad`: `Either`'s stops at a `Left`, and `List`'s runs the step on every element.

## Helpers built on `flatMap` and `map` {#helpers}

`Monad` adds default methods for chains you would otherwise write as lambdas:

| Method | What it does | Built as |
|---|---|---|
| `flatMapIfOrElse(predicate, ifTrue, ifFalse, ma)` | Chooses the next step by testing the value | `flatMap(a -> predicate.test(a) ? ifTrue.apply(a) : ifFalse.apply(a), ma)` |
| `as(b, ma)` | Keeps the outcome, and replaces the value with `b`, which must not be `null` | `map(_ -> b, ma)` |
| `asUnit(ma)` | Keeps the outcome, and replaces the value with `Unit` | `map(_ -> Unit.INSTANCE, ma)` |
| `peek(action, ma)` | Runs `action` on the value, and keeps the value | `map(a -> { action.accept(a); return a; }, ma)` |

`as`, `asUnit` and `peek` are built from `map`, so they run where `map` runs: `peek`'s action runs on a `Right` and on each element of a list, and never on a `Left`. On the order that was found, `flatMapIfOrElse` keeps an order that has not shipped, and `as` replaces it with a message:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monad/MonadBook.java:helpers}}
```

### `flatMap2` to `flatMap5` {#flatmap2-to-flatmap5}

`flatMap2` to `flatMap5` combine two to five values with a step that gives back a container, for a combination that can itself fail. `map2` to `map5` take a plain function instead. Because they are built from `flatMap`, they take their inputs in order, so on `Validated` they stop at the first error, where its `map2` reports both:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monad/MonadBook.java:flatmap2}}
```

The step always succeeds, so it is not why `flatMap2` stops at one error: `flatMap2` is a chain of `flatMap` calls, and `Validated`'s `flatMap` stops at the first `Invalid`. [Where Validated bends a rule](applicative.md#where-validated-bends-a-rule) says why its `map2` differs.

## Three laws {#three-laws}

A `Monad` keeps three laws. Together they say that `of` adds nothing of its own before or after a step, and that a chain gives the same result however you group its steps.

~~~admonish note title="Laws"
- **Left identity: running a step on a lifted value is the step.** `flatMap(f, of(a))` equals `f.apply(a)`.
- **Right identity: running `of` as the step changes nothing.** `flatMap(a -> of(a), ma)` equals `ma`.
- **Associativity: grouping the steps differently changes nothing.** `flatMap(g, flatMap(f, ma))` equals `flatMap(a -> flatMap(g, f.apply(a)), ma)`.

Two containers are equal when they hold equal values, or equal errors, in the same shape. For a deferred container such as `IO`, they are equal when running both gives equal results. The laws assume values that are never `null` and functions that never return one, and [Values, never null](../hkts/core-concepts.md#values-never-null) says what each type does with one.

`MonadLaws`, beside `ApplicativeLaws` in [`hkj-test`](../tooling/test_assertions.md#adding-the-dependency)'s `org.higherkindedj.hkt.laws` package, checks the three laws on any instance. It takes an equality from `KindEquivalence.byEqualsAfter`, as on the [Applicative](applicative.md#four-laws) page, which here unwraps both sides with `EITHER::narrow` and compares them with `equals`. The test checks `Either`'s instance on a `Right` and on a `Left`. `verifyEmail` always gives a `Left`, so the laws are checked when a step fails too. `ORDER` is any `Order`, and the test uses these imports, with JUnit's:

<!-- verify -->
```java
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;

import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.MonadLaws;
```

```java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java:monad_laws}}
```
~~~

## Back to the one-liner {#back-to-the-one-liner}

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/oneliner/OneLinerBook.java:line}}
```

`.via(orders::save)` is the line's Monad layer: `save` needs the order the line holds so far, and on a `Left` it never runs. `via` is `EitherPath`'s own method, with no `Monad` instance involved. [Two encodings, one algebra](../hkts/one_line_six_layers.md#two-encodings-one-algebra) matches it to `Monad`'s `flatMap` over `Kind`.

## Things people get wrong {#things-people-get-wrong}

~~~admonish warning title="Common Misunderstandings"
- **"`flatMap` runs the step at once."** `Either`'s does. `IO`'s and `VTask`'s build a deferred value, and nothing runs until you run it. `Stream`'s runs the step only as the stream is consumed. The order of the steps is fixed, and the timing is the container's.
- **"Monads always short-circuit."** Instances whose input holds at most one value stop when it is missing or failed: `Either`, `Validated`, `Maybe`, `IO` and `CompletableFuture` among them. `List`'s `flatMap` runs the step on every element. The behaviour comes from the instance, not from the word "Monad".
- **"`flatMap` is `Stream.flatMap`."** `Stream.flatMap` is one instance of the shape: it flattens a stream of streams. `Monad`'s `flatMap` names the shape for any container, and each instance defines its own flattening.
- **"Nested `flatMap` calls are the cleanest you can do."** They are the most explicit, as `flatMap2`'s body shows. For more than two steps, a [For comprehension](for_comprehension.md) reads better and avoids the indentation.
- **"You can call `.flatMap` on a `Kind`."** `Kind` has no methods. Go through the `Monad<F>` instance, which knows how to flatten inside `F`.
~~~

~~~admonish tip title="You can ship now"
You can now write a chain once for any container, as `markPaid` is, and say what each instance does when a step has no value to work on. You can use the helpers in place of hand-written lambdas, and check an instance against left identity, right identity and associativity. The rest of this page is for which types have a `Monad`, and what builds on it.
~~~

---

## The fine print {#the-fine-print}

### Which types have a `Monad` {#which-types-have-a-monad}

| Types | Their `Monad` comes from |
|---|---|
| Every token in `Witnesses` | [`Instances.monad(token)`, which always succeeds](instances_facade.md#what-the-compiler-checks) |
| `Validated`, `Writer`, `EitherOrBoth` and the transformers | `Instances` with its argument, such as `Instances.writer(monoid)` |
| `Free` | `new FreeMonad<>()` |
| `Const`, `FreeAp` and `Fetch` | Nowhere: they have an `Applicative` and no `Monad` |
| `EitherF` and `Coyoneda` | Nowhere: they have a `Functor` only |

### What builds on `Monad` {#what-builds-on-monad}

- **`MonadError` extends `Monad`.** [MonadError](monad_error.md), the next page, adds raising an error and recovering from one.
- **`MonadZero` extends `Monad` and `Alternative`.** [MonadZero](monad_zero.md) adds an empty value, so a chain can filter.
- **`For` takes a `Monad`.** `For.from(monad, source)` writes a chain of `flatMap` calls as a list of steps, as [For comprehensions](for_comprehension.md) shows.
- **`GenericPath` takes a `Monad`.** `GenericPath.of(kind, monad)` turns any `Kind` that has a `Monad` into a Path, with `via`, as [GenericPath](../effect/path_generic.md) shows.

---

~~~admonish tip title="See Also"
- [Applicative](applicative.md): combining values that do not depend on one another
- [MonadError](monad_error.md): raising an error and recovering from one
- [For Comprehension](for_comprehension.md): a chain of `flatMap` calls written as steps
- [Choosing Your Abstraction Level](abstraction_levels.md): `Applicative`, `Selective` or `Monad`
- [One Line, Six Layers](../hkts/one_line_six_layers.md): where `via` sits in the chapter's one-liner
~~~

~~~admonish tip title="Further Reading"
- **Magnus Smith**, [Functors and Monads with Java and Scala](https://blog.scottlogic.com/2025/03/31/functors-monads-with-java-and-scala.html): the same two type classes, side by side in both languages
- **Bartosz Milewski**, [Monads: Programmer's Definition](https://bartoszmilewski.com/2016/11/21/monads-programmers-definition/): monads as the composition of functions that return a container
- **Mark Seemann**, [Monads](https://blog.ploeh.dk/2022/03/28/monads/): "a functor you can flatten", the start of a series with examples in C#, F# and Haskell
~~~

~~~admonish info title="Hands-On Learning"
Practise Monad chaining in [Tutorial 04: Monad Chaining](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial04_MonadChaining.java) (8 exercises).
~~~

---

**Previous:** [Applicative](applicative.md)
**Next:** [MonadError](monad_error.md)
