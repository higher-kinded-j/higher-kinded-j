<!-- description: Describe a side effect in Java with IO and run it once, at the edge: read the time through TimeSource, compose steps, and catch failures on an IOPath. -->

# IO: Describing Side Effects

_Describe a side effect now, and run it once, at the edge._

<!-- source: John Keats, "Ode on a Grecian Urn", stanza II, in Lamia, Isabella, The Eve of St. Agnes, and Other Poems (1820); text checked against Project Gutenberg eBook #23684 -->
> _"Heard melodies are sweet, but those unheard / Are sweeter"_
>
> — John Keats, *Ode on a Grecian Urn*

An `IO` is Keats's unheard melody: until you run it, it is a description you can still arrange and combine, and nothing has played.

~~~admonish abstract title="IO at a glance"
| | |
|---|---|
| **Models** | A side effect described now and run later |
| **Replaces** | A method that performs its side effect as soon as it is called |
| **Effect Path** | [`IOPath`](../effect/path_io.md) |
{{#include ../../../hkj-examples/src/test/resources/golden/foundations-capabilities.md.golden:io_monad}}
| **Choose it when** | The effect waits until you run it, throws no checked exception, and need not run at the same time as other work; otherwise [VTask](vtask_monad.md), or compare every type in [Choosing a Type](supported-types.md#choosing-a-type) |
~~~

~~~admonish info title="What You'll Learn"
- Read the time through `TimeSource`, so code that uses it can be tested with a fixed clock
- Build a program from steps that each describe an effect, and say when each one runs
- Say why running an `IO` twice runs its effects twice
- Catch a failing step on an `IOPath`, since `IO` itself lets the exception through
- Use `IO` in generic code through its `Monad` instance
~~~

~~~admonish example title="See Example Code"
**The code on this page is [IOBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/io/IOBook.java)**: the page includes it, so the build compiles and runs it. [IOBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/io/IOBookTest.java) holds what the page says each method does.
~~~

---

## Reading the clock {#reading-the-clock}

A method that calls `Instant.now()` reads the clock the moment it is called, so no test can predict its result. Injecting a `java.time.Clock` fixes the test, but the read still happens at the call. An `IO<A>` is close to a `Supplier<A>` that nothing has called yet. What it adds is `map` and `flatMap`, which join steps into one description without running any of them, so you build the program in one place and run it in another. `TimeSource` wraps a `Clock`: its `now()` is `IO.delay(clock::instant)`, and `nowAsync()` gives the same read as a `VTask`.

The code on this page uses these imports, with `Order`, `Consignment`, `ConsignmentState` and `Address` from the order records the [Optics](../optics/ch_intro.md) chapter introduces. Import the library's `IO` by name: Java 25 has a `java.lang.IO` too.

<!-- verify -->
```java
import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.time.TimeSource;
```

Dispatching an order stamps its consignment with the time:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/io/IOBook.java:dispatch}}
```

`NINE_AM` is the instant a fixed time source reads, `ORDER` is a paid order ready to ship, and `LEEDS` is the address it goes to:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/io/IOBook.java:dispatch_outcome}}
```

Nothing is read until the effect runs, and each run reads afresh. In tests, inject `TimeSource.fixed(...)`, or `TimeSource.of(steppableClock)` with `hkj-test`'s [`SteppableClock`](../tooling/test_assertions.md#steppableclock), and the same code becomes deterministic. It is named `TimeSource`, not `Clock`, so that it never clashes with `java.time.Clock`.

## Describing, then running {#describing-then-running}

`flatMap` joins a step that needs the result of the one before, and `map` changes the result. Both build a new `IO`, and neither runs anything. `IO.delay` describes any effect, here adding the consignment to a log:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/io/IOBook.java:program}}
```

`unsafeRunSync` runs the whole description, in the order it was built. Its name warns that this is where the effects happen: call it once, at the edge of the application, never deep inside its logic:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/io/IOBook.java:runs}}
```

The log is empty until the program runs, and holds two consignments after two runs. An `IO` is a description, not a stored result: each run does the work again, and reads the clock again. To reuse a result, run the `IO` once and keep its value. [Lazy](lazy_monad.md) keeps the first result of a computation with no side effect.

## On an `IOPath` {#on-an-iopath}

`IO` has no `MonadError` instance: an exception a step throws leaves `unsafeRunSync`, as it would leave a direct call. Application code usually holds its effects on an [`IOPath`](../effect/path_io.md), which adds the failure handling: `catching` turns an exception into a `Left`, `handleError` into a value, and `asTry` into a `Try`. These are the extra imports:

<!-- verify -->
```java
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
```

`bookCourier` stands in for a courier call that throws. `Path.ioPath` puts an `IO` on a Path, `via` chains the next step, and `catching` turns the exception into a `Left`. An `IOPath` runs with `unsafeRun`, as an `IO` runs with `unsafeRunSync`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/io/IOBook.java:courier}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/io/IOBook.java:path_outcome}}
```

`IOPath` also releases resources with `bracket` and `withResource`, and retries or times out a step, as [IOPath](../effect/path_io.md) shows.

## Creating instances {#creating-instances}

`IO.delay(supplier)` describes an effect that gives a value, and `IO.fromRunnable(action)` one that gives [`Unit`](../hkts/core-concepts.md#unit). A plain value goes in as `IO.delay(() -> value)`, or as `Path.ioPure(value)` on a Path. `IO.delay` takes a `Supplier`, which cannot throw a checked exception, so catch one inside the lambda, as [IOPath](../effect/path_io.md#creation) shows, or use [VTask](vtask_monad.md), whose `Callable` can throw one. An `IO` always gives a value: a supplier or `map` function that returns `null` fails the `IO` when it runs, and the instance's `of(null)` throws at the call, as [Values, never null](../hkts/core-concepts.md#values-never-null) sets out.

## Methods {#methods}

| Method | What it does |
|---|---|
| `IO.delay(supplier)` | Describes an effect; the supplier runs only when the `IO` does |
| `IO.fromRunnable(action)` | Describes an effect that gives `Unit` |
| `map(f)` | Builds an `IO` whose result is changed by `f`, running nothing |
| `flatMap(f)` | Builds an `IO` that runs the next step on this one's result, running nothing |
| `asUnit()` | Keeps the effect and replaces the result with `Unit` |
| `unsafeRunSync()` | Runs the description, blocking the calling thread until it gives its result or throws its exception |
| `TimeSource.system()`, `fixed(instant)`, `of(clock)` | Give a `TimeSource` over the system clock in UTC, a fixed instant, or any `Clock` |
| `timeSource.now()`, `nowAsync()` | Give an `IO<Instant>`, or a `VTask<Instant>`, that reads the time when it runs |

## Working with Kind {#working-with-kind}

Generic code, written once for any container, takes `IO` through its `Monad` instance, from `Instances.monad(io())`. The instance works over `Kind<IOKind.Witness, A>`, so you widen an `IO` to pass it in, and run the result with `IO_OP.unsafeRunSync`. These are the extra imports:

<!-- verify -->
```java
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IOKind;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/io/IOBook.java:kind}}
```

`ran` is `"to Leeds"`. The instance's `map` builds a new `IO`, and nothing reads the clock until `IO_OP.unsafeRunSync` runs it. The `Monad` helpers, such as `peek` and `flatMapIfOrElse`, work on it too, as [Helpers built on `flatMap` and `map`](../functional/monad.md#helpers) shows. Widening allocates nothing: `IO` implements `IOKind` itself, so `widen` checks for `null` and returns the same object. `IO` has a `Selective` instance too, an object of its own, and *IO at a glance*, at the head of the page, lists it.

## When to use `IO` {#when-to-use-io}

| You need | Use |
|---|---|
| A side effect described now and run once, at the edge | `IO` |
| The same, with failure handling, resources and retries | [IOPath](../effect/path_io.md) |
| A value worked out once and kept, with no side effect | [Lazy](lazy_monad.md) |
| An effect that throws a checked exception, or runs at the same time as other work | [VTask](vtask_monad.md) |
| Work that has already started, on another thread | [CompletableFuture](cf_monad.md) |
| A program as data, which several interpreters can run | [Free](free_monad.md) |

~~~admonish info title="Hands-On Learning"
Practise deferring a side effect with `IOPath`, in Exercise 3 of [Tutorial 01: Effect Path Basics](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/effect/Tutorial01_EffectPathBasics.java) (9 exercises).
~~~

**Benchmarks:** `IOBenchmark` measures building and running `IO` chains, and `VTaskVsIOBenchmark` compares them with `VTask`. [Benchmarks & Performance](../benchmarks.md) says how to run them.

---

**Previous:** [Optional](optional_monad.md)
**Next:** [Lazy](lazy_monad.md)
