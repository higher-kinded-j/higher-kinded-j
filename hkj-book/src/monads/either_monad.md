<!-- description: Return a typed error from a Java method as an Either instead of throwing an exception, and chain the steps that can fail. -->

# Either: Modelling Success or Failure

_Return a typed error as a value, and chain steps that stop at the first error._

~~~admonish abstract title="Either at a glance"
| | |
|---|---|
| **Models** | A success (`Right`) or a typed error (`Left`), never both |
| **Replaces** | A method that throws a checked exception |
| **Effect Path** | [`EitherPath`](../effect/path_either.md) |
{{#include ../../../hkj-examples/src/test/resources/golden/foundations-capabilities.md.golden:either_monad}}
| **Choose it when** | The first error, and why it happened, is enough; otherwise [Validated](validated_monad.md), or compare every type in [Choosing a Type](supported-types.md#choosing-a-type) |
~~~

~~~admonish info title="What You'll Learn"
- Return a typed error from a method as an `Either`, instead of throwing it
- Chain steps that can fail, and say which step's error reaches the end
- Read an `Either` with a `switch` or `fold`, without a getter that throws
- Choose between `Either`, `EitherPath`, `Try` and `Validated` for a failure
- Pass an `Either` to generic code through its `MonadError` instance
~~~

~~~admonish example title="See Example Code"
**The code on this page is [EitherBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/either/EitherBook.java)**: the page includes it, so the build compiles and runs it. [EitherBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/either/EitherBookTest.java) holds what the page says each method does.
~~~

---

## Failure as a value {#failure-as-a-value}

A method that can fail usually says so with a checked exception, as `Order find(UUID id) throws OrderNotFoundException` does. The failure is in the signature but not in the value: a lambda passed to `map` cannot throw it, and every caller must catch it or declare it again. `Either<L, R>` puts the failure in the return type, as an ordinary value. A `Right` holds the success, and a `Left` holds the error, often a sealed type that says which failure it was. Where `Optional` and `Maybe` only say that a value is missing, `Either` says why. You can think of it as `Maybe` with a story to tell about its `Nothing`.

The code on this page uses these imports. `Order` is one of the order records the [Optics](../optics/ch_intro.md) chapter introduces, and `OrderFocus` holds its generated optics:

<!-- verify -->
```java
import java.util.Map;
import java.util.UUID;
import org.higherkindedj.example.book.foundations.OrderError;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
```

The error is a sealed interface, with one record for each way the order service can refuse:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/OrderError.java:order_error}}
```

Each step returns its error rather than throwing it:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/either/EitherBook.java:steps}}
```

## Chaining steps that can fail {#chaining-steps-that-can-fail}

`flatMap` runs the next step on a `Right`'s value, and `map` changes the value with a function that cannot fail, here `OrderFocus.status().set`, which returns a copy of the order with a new status. On a `Left`, both pass the error on untouched:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/either/EitherBook.java:chain}}
```

`ORDERS` holds two orders, `PLACED`, which is open, and `SHIPPED`. No order has the id `UNKNOWN`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/either/EitherBook.java:outcomes}}
```

<pre class="hkj-railway-diagram" role="img" aria-label="markPaid's two tracks. On the Right track, the order is found, passes requireOpen and is marked paid. A Left from find stays on the Left track, and nothing after it runs. A Left from requireOpen, for an order that has shipped, also ends on the Left track.">
         find    requireOpen    mark paid
Right  ════●══════════●═════════════●═════▶  Right(paid order)
                       ╲  the order has shipped
Left   ────●────────────●─────────────────▶  Left(error)
         no order:
         nothing after it runs
</pre>

The diagram shows the two ways onto the `Left` track: `find` finds no order, so `requireOpen` never runs, or `requireOpen` refuses an order that has shipped. Either way, the error reaches the end of the chain as the step returned it, and no `if` in `markPaid` checks for it.

## Reading an `Either` {#reading-an-either}

A `switch` over the two cases reads the result, and the compiler checks that every case is handled. A record pattern can look inside the error as well:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/either/EitherBook.java:describe}}
```

`fold(onLeft, onRight)` does the same with two functions, when both sides give one type. `getLeft` and `getRight` throw `NoSuchElementException` on the wrong side, so keep them for tests.

## Creating instances {#creating-instances}

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/either/EitherBook.java:creating}}
```

A `Right` and a `Left` always hold a value. `Either.right(null)`, `Either.left(null)` and a `map` whose function returns `null` throw `NullPointerException`, as [Values, never null](../hkts/core-concepts.md#values-never-null) says. A step with nothing to return gives `Unit.INSTANCE`.

## Methods {#methods}

| Method | What it does |
|---|---|
| `Either.right(value)`, `Either.left(error)` | Build a success or an error |
| `isRight()`, `isLeft()` | Say which side it holds |
| `map(f)`, `mapRight(f)` | Change a `Right`'s value; a `Left` passes through |
| `flatMap(f)` | Runs a step that can fail on a `Right`'s value; a `Left` passes through |
| `mapLeft(f)` | Changes a `Left`'s error; a `Right` passes through |
| `bimap(onLeft, onRight)` | Changes whichever side it holds |
| `fold(onLeft, onRight)` | Turns either side into one result |
| `ifLeft(action)`, `ifRight(action)` | Run an action for the side it holds |
| `getLeft()`, `getRight()` | Return that side's value, or throw `NoSuchElementException` |

A `Maybe` becomes an `Either` with `toEither(error)`, and a `Validated` with `toEither()`.

## Working with Kind {#working-with-kind}

Generic code, written once for any container, takes `Either` through its `MonadError` instance, as [MonadError](../functional/monad_error.md) shows. The instance works over `Kind<EitherKind.Witness<L>, R>`, so you widen an `Either` to pass it in, and narrow the result to read it. These are the extra imports:

<!-- verify -->
```java
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/either/EitherBook.java:kind}}
```

The instance's `flatMap` and `map` do what `Either`'s own methods do, and `handleError` turns an error into a value. Widening allocates nothing: `Either` implements `EitherKind` itself, so `widen` checks for `null` and returns the same object. `Either` has instances of other type classes too, such as `Traverse` and `Bifunctor`, each an object of its own, and *Either at a glance*, at the head of the page, lists them.

## Back to the one-liner {#back-to-the-one-liner}

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/oneliner/OneLinerBook.java:line}}
```

In [the one-liner](../hkts/one_line_six_layers.md), `toEitherPath(OrderError.notFound(id))` turns the repository's `MaybePath` into an `EitherPath`, so a missing order becomes a typed `Left`. `via(orders::save)` runs `save` only on a `Right`, as `flatMap` does on this page, and a refused save is a `Left` too. An `EitherPath` holds an `Either` and calls its methods directly, and `run()` hands that `Either` back.

## When to use `Either` {#when-to-use-either}

| You need | Use |
|---|---|
| The first error from a run of steps, typed by your domain | `Either<MyError, A>` |
| The same steps as a fluent chain in application code | [EitherPath](../effect/path_either.md) |
| A failure that is a caught exception | [Try](try_monad.md) |
| Every error from checks that do not depend on one another | [Validated](validated_monad.md) |
| A success that can carry warnings beside it | [EitherOrBoth](either_or_both_monad.md) |
| Typed errors inside another effect, such as `CompletableFuture` | An [Effect Path](../effect/ch_intro.md), or the [EitherT](../transformers/eithert_transformer.md) transformer |

~~~admonish info title="Hands-On Learning"
Practise choosing between `Either`, `Maybe` and `Validated` in [Tutorial 06: Concrete Types](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial06_ConcreteTypes.java) (7 exercises).
~~~

**Benchmarks:** `EitherBenchmark` measures how cheaply a `Left` passes along a chain. [Benchmarks & Performance](../benchmarks.md) says how to run it.

---

**Previous:** [Core Types](ch_intro.md)
**Next:** [Validated](validated_monad.md)
