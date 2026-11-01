<!-- description: Run blocking Java work on virtual threads with VTask: compose it on a VTaskPath, run lookups at the same time with Par, and keep typed errors on VResultPath. -->

# VTask: Deferred Tasks and Virtual Threads

_Describe work that may block, then run it when you choose, alone or alongside other work._

<!-- source: Rob Pike, "Concurrency is not Parallelism", a talk at Heroku's Waza conference, 11 January 2012; slide text checked against go.dev/talks/2012/waza.slide -->
> _"Concurrency is about dealing with lots of things at once. Parallelism is about doing lots of things at once."_
>
> — Rob Pike, *Concurrency is not Parallelism*

A `VTask` is built for the dealing. `Par` and `Scope` can keep thousands of tasks waiting on replies at once, each on a virtual thread, while only as many run at any moment as the machine has cores.

~~~admonish abstract title="VTask at a glance"
| | |
|---|---|
| **Models** | Work that may block or fail, described now and run when you choose, alone or at the same time as other work |
| **Replaces** | A `Callable` submitted to an `ExecutorService`, and the `Future` you wait on |
| **Effect Path** | [`VTaskPath`](../effect/path_vtask.md), and [`VResultPath`](../effect/path_vresult.md) for a typed error |
{{#include ../../../hkj-examples/src/test/resources/golden/foundations-capabilities.md.golden:vtask_monad}}
| **Choose it when** | The work throws a checked exception, or should run at the same time as other work; otherwise [IO](io_monad.md), or compare every type in [Choosing a Type](supported-types.md#choosing-a-type) |
~~~

~~~admonish info title="What You'll Learn"
- Describe work that blocks or throws a checked exception, and run it when you choose
- Run two lookups at the same time on a `VTaskPath`, and recover from one that fails
- Keep a refusal the caller expects as a typed `Left`, not an exception, on a `VResultPath`
- Say which thread runs a task, and what `run()`, `runSafe()` and `runAsync()` do with a failure
- Use `VTask` in generic code through its `MonadError` instance
~~~

~~~admonish example title="See Example Code"
**The code on this page is [VTaskBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vtask/VTaskBook.java)**: the page includes it, so the build compiles and runs it. [VTaskBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/vtask/VTaskBookTest.java) holds what the page says each method does.
~~~

---

## Describing work that may block {#describing-work-that-may-block}

In plain Java, you run blocking work on another thread by submitting a `Callable` to an `ExecutorService`. With `Executors.newVirtualThreadPerTaskExecutor()`, each task gets a [virtual thread](https://openjdk.org/jeps/444) of its own, which costs little while it waits. The work starts as soon as you submit it, and `Future.get()` makes you catch `InterruptedException` and `ExecutionException`. A `VTask<A>` holds a `Callable` that nothing has run yet. You compose tasks first, and choose afterwards how to run them. `run()` runs a task on the calling thread, `runAsync()` on a virtual thread of its own, and `Par` at the same time as other tasks. A `Callable` may throw a checked exception, which the `Supplier` behind an [IO](io_monad.md) cannot.

The code on this page uses these imports, with `Order` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import java.io.IOException;
import java.math.BigDecimal;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
```

Pricing an order in euros needs two lookups, the exchange rate and a courier's quote, and neither service is called until you run the task. Each call blocks until its service answers, and may throw an `IOException`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vtask/VTaskBook.java:services}}
```

## On a `VTaskPath` {#on-a-vtaskpath}

Application code holds tasks on a `VTaskPath`. `Path.vtask` puts a `Callable` on one, `via` chains a step that needs the previous result, and `map` changes the value, as on every Path. `handleError` gives a value in place of a failure, here `CACHED_RATE`, a stored rate of 1.15 for when the rate service cannot answer. `parZipWith` runs two tasks at the same time and joins their results:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vtask/VTaskBook.java:path}}
```

~~~admonish warning title="A preview API"
`parZipWith` runs its tasks through `Par`, which with [`Scope`](vtask_scope.md) and `ScopeJoiner` is built on Java 25's structured concurrency API, [JEP 505](https://openjdk.org/jeps/505), a preview feature. Their class files are marked as using it. Code that names `Par`, `Scope` or `ScopeJoiner` needs `--enable-preview` to compile and to run. Code that reaches them only through a Path, such as a `VTaskPath`'s `zipWith`, `zipWith3`, `parZipWith` or `race`, compiles without it and needs it when it runs. The [HKJ Gradle plugin](../tooling/gradle_plugin.md) adds it by default. The JDK's API may change in a later release; your code calls the library's types, not the JDK's.
~~~

`LIVE_RATES` and `RATES_DOWN` stand in for the rate service, answering 1.17 or failing. `COURIER` stands in for the courier, which quotes 7.99 and counts its quotes in `QUOTES`. `ORDER` is worth 92.50 in pounds, and `inEuros` adds the shipping and converts the sum, so the live rate gives (92.50 + 7.99) × 1.17 = 117.57:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vtask/VTaskBook.java:path_outcome}}
```

Building the path asks neither service. `unsafeRun()` waits on the calling thread while both lookups run. It is where the work happens, so call it once, at the edge of the application. Each run asks the services again. On a `VTaskPath`, `zipWith` runs both paths at once too, as `parZipWith` does.

A `VTaskPath` also gives up waiting with `timeout(duration)`, and retries with `withRetry`, as [VTaskPath](../effect/path_vtask.md) shows. A task that times out is not interrupted: it keeps running, unobserved. To cancel it as well, fork it into a [`Scope`](vtask_scope.md) with a timeout.

## A refusal as a value, on a `VResultPath` {#on-a-vresultpath}

An exception suits a failure nobody plans for, such as a service that is down. A refusal the caller expects belongs in the result. A `VResultPath<E, A>` holds a `VTask<Either<E, A>>`, and works as an [EitherPath](../effect/path_either.md) whose steps may block: `via` stops at the first `Left`, and `recover` and `mapError` work on the error. The courier refuses to collect an order that has already shipped. These are the extra imports, with `OrderError`, the order service's refusals, from the [Either](either_monad.md#failure-as-a-value) page:

<!-- verify -->
```java
import org.higherkindedj.example.book.foundations.OrderError;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.either.Either;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vtask/VTaskBook.java:vresult}}
```

`SHIPPED` is `ORDER` once it has shipped. `run()` gives the `VTask`, and running that gives the `Either`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vtask/VTaskBook.java:vresult_outcome}}
```

`withTimeout(duration, onTimeout)` gives a typed `Left` when the time runs out, and, like a `VTaskPath`'s `timeout`, leaves the task running, so a booking can still complete after the caller has its `Left`. A `VResultPath`'s `zipWith` runs its paths one after another and stops at the first `Left`. To run typed work at the same time, see [VResultPath](../effect/path_vresult.md#outcome-aware-structured-concurrency).

## Running a task {#running-a-task}

`VTaskPath.run()` hands you the `VTask` inside, without running it. That `VTask` runs in one of three ways. Its `run()` runs it on the calling thread and waits. `runSafe()` does the same, and holds a failure in a [Try](try_monad.md); prefer it in code that handles failures as values. `runAsync()` starts it on a new virtual thread and returns a `CompletableFuture`. Running a task does not move it to a virtual thread: `main` runs on a platform thread, an ordinary thread the operating system schedules, and `run()` keeps the task there. Only `runAsync()`, `timeout`, `Par` and `Scope` move work to a virtual thread. These are the extra imports:

<!-- verify -->
```java
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.VTask;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vtask/VTaskBook.java:threads}}
```

On a failure, a `VTask`'s `runSafe()` holds the exception the task threw, a checked one included:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vtask/VTaskBook.java:failures}}
```

`run()` throws an unchecked exception as it is. It declares no checked exception, so it wraps a checked one in a `VTaskExecutionException`, whose `getCause()` gives the original. The future from `runAsync()` fails with the task's exception: `join()` throws it wrapped in a `CompletionException`, and `get()` in an `ExecutionException`.

## Running tasks at the same time {#parallel-composition-with-par}

`Par` runs tasks at the same time, each on a virtual thread of its own, and waits for them all. `map2` and `zip` join two tasks, and `all` a list of them. `traverse` runs the task a function gives for each item of a list. `race` keeps the first task to succeed and cancels the rest. `Par.all` is close to `ExecutorService.invokeAll`, and `race` to `invokeAny`. What `Par` adds is that when one task fails, `map2`, `zip`, `all` and `traverse` cancel the others and fail with its exception. `parZipWith` on a `VTaskPath` runs its two tasks through `Par.map2`. These are the extra imports:

<!-- verify -->
```java
import java.util.Collections;
import org.higherkindedj.hkt.vtask.Par;
```

`COURIERS` holds three couriers, which quote 7.99, 6.50 and 9.20. The three quotes take as long as the slowest of them, not the three added together:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vtask/VTaskBook.java:par}}
```

## Creating instances {#creating-instances}

`VTask.of(callable)` describes work that may throw any exception, and `VTask.delay(supplier)` work that throws no checked exception. `VTask.exec(runnable)` describes work that gives [`Unit`](../hkts/core-concepts.md#unit). `VTask.succeed(value)` and `VTask.fail(exception)` give a task that has already succeeded or failed. `VTask.blocking(callable)` is `of` under a name that says the work blocks, and runs the same.

On a Path, `Path.vtask` matches `of`, `Path.vtaskExec` matches `exec`, `Path.vtaskPure` matches `succeed` and `Path.vtaskFail` matches `fail`, and `Path.vtaskPath(task)` takes a `VTask` you already have. For a `VResultPath`, they are `Path.vresultDefer`, `Path.vresultRight`, `Path.vresultLeft` and `Path.vresult(task)`.

### A task always gives a value {#a-task-always-gives-a-value}

A `VTask` that succeeds gives a value, never `null`. `VTask.succeed(null)` throws `NullPointerException` at the call. A callable, supplier, `map` function or recovery function that returns null fails the task when it runs, and the exception names that function, as in `callable must not return null`. A task with nothing to return gives `Unit.INSTANCE`, as `VTask.exec` does, and one whose value may be missing says so with a `Maybe`. These are the extra imports:

<!-- verify -->
```java
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.maybe.Maybe;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vtask/VTaskBook.java:values}}
```

So `VTask.of(() -> { work(); return null; })`, typed `VTask<Void>`, fails when it runs. Use `VTask.exec`, or return `Unit.INSTANCE`. `VTask` is itself a functional interface, so you can write a task as a lambda; `run()`, `runSafe()` and `runAsync()` refuse a null from one of those too.

The factories and functions of every lazy type follow the same rule: `IO`, `VStream`, `State`, `Reader`, `Writer`, `Lazy`, `Trampoline`, `Free` and `Context`, and every Effect Path over one. A stream refuses a null element. A nullness checker flags a hand-written `IO` or `Reader` lambda that returns null, and an `IOPath` refuses one when it runs. Only [`Id`](identity.md) holds a null, since every optic's `modify` runs through it. A `CompletableFuture` outside a Path keeps the JDK's null too.

## Methods {#methods}

| Method | What it does |
|---|---|
| `VTask.of(callable)`, `delay(supplier)`, `exec(runnable)` | Describe work; nothing runs until the task does |
| `VTask.succeed(value)`, `VTask.fail(exception)` | Give a task that has already succeeded or failed |
| `map(f)`, `flatMap(f)`, `via(f)`, `then(next)` | Build a new task, running nothing |
| `recover(f)`, `recoverWith(f)`, `mapError(f)` | Give a value or another task in place of a failure, or change the exception |
| `timeout(duration)` | Runs the task on a virtual thread of its own, and fails with a `TimeoutException` if it has not finished in time, leaving it running |
| `run()`, `runSafe()` | Run the task on the calling thread, throwing its failure or holding it in a `Try` |
| `runAsync()` | Starts the task on a new virtual thread, and returns a `CompletableFuture` |
| `Par.map2`, `zip`, `all`, `traverse`, `race` | Run tasks at the same time, each on a virtual thread |
| `Path.vtask(callable)`, `Path.vresultDefer(supplier)` | Put work on a `VTaskPath`, or on a `VResultPath` |
| `VTASK.widen(task)`, `VTASK.narrow(kind)`, `VTASK.run(kind)` | Move a task in and out of `Kind`, or run one |

## Working with Kind {#working-with-kind}

Generic code, written once for any container, takes `VTask` through its `MonadError` instance, a `VTaskMonad`, from `Instances.monadError(vtask())`. Its error type is `Throwable`, as [MonadError](../functional/monad_error.md) shows. The instance works over `Kind<VTaskKind.Witness, A>`, so you widen a task to pass it in, and run the result with `VTASK.run`. These are the extra imports:

<!-- verify -->
```java
import static org.higherkindedj.hkt.instances.Witnesses.vtask;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.vtask.VTaskKind;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vtask/VTaskBook.java:kind}}
```

The handler receives the exception the task threw. This one ignores it and gives a constant, which the instance's [`recover`](../functional/monad_error.md#constant-fallbacks-recover-and-recoverwith) says directly, given the widened task and `CACHED_RATE`. Those instance-level forms take the `Kind` first. The fluent `recover` on a `VTask` takes a function of the exception.

The instance's `ap`, and `map2` to `map5`, run their tasks one after another, so a failure in the first stops the second from running; `Par` runs tasks at the same time. Widening allocates nothing: `VTask` implements `VTaskKind` itself, so `widen` checks for `null` and returns the same object.

## When to use `VTask` {#when-to-use-vtask}

| You need | Use |
|---|---|
| Work that may block or throw a checked exception, run when you choose | `VTask`, on a `VTaskPath` in application code |
| Two or more tasks at the same time, each on a virtual thread | `parZipWith`, or `Par` |
| A refusal the caller expects, as a typed `Left` rather than an exception | `VResultPath` |
| Tasks that start and end together, joined the way you choose | [Structured Concurrency](vtask_scope.md) |
| A resource released however the work ends | [Resource Management](vtask_resource.md) |
| A side effect with no checked exception, never run alongside other work | [IO](io_monad.md) |
| Work already started, from an API that gives you a future | [CompletableFuture](cf_monad.md) |
| Many values, produced one at a time | [VStream](vstream.md) |

~~~admonish info title="Hands-On Learning"
Practise `VTask` in [TutorialVTask](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/concurrency/TutorialVTask.java) (9 exercises), and `VTaskPath` in [TutorialVTaskPath](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/concurrency/TutorialVTaskPath.java) (9 exercises), both part of the [VTask journey](../tutorials/concurrency/vtask_journey.md).
~~~

**Benchmarks:** `VTaskBenchmark` and `VTaskPathBenchmark` measure building and running tasks, and `VTaskParBenchmark` the `Par` combinators. `VTaskVsIOBenchmark` and `VTaskVsPlatformThreadsBenchmark` compare `VTask` with `IO` and with a pool of platform threads. [Benchmarks & Performance](../benchmarks.md) says how to run them.

---

**Previous:** [CompletableFuture](cf_monad.md)
**Next:** [Structured Concurrency](vtask_scope.md)
