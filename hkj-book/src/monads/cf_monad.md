<!-- description: Use Java's CompletableFuture as a monad: chain and recover futures on a CompletableFuturePath, pass them to generic code through MonadError, or choose VTask. -->

# CompletableFuture: Asynchronous Results

_Keep Java's own `CompletableFuture`, with an Effect Path and a `MonadError` instance._

~~~admonish abstract title="CompletableFuture at a glance"
| | |
|---|---|
| **Models** | Java's asynchronous result, already running |
| **Replaces** | Nothing: it is Java's own type |
| **Effect Path** | `CompletableFuturePath`, shown in [On a `CompletableFuturePath`](#on-a-completablefuturepath) |
{{#include ../../../hkj-examples/src/test/resources/golden/foundations-capabilities.md.golden:cf_monad}}
| **Choose it when** | An API gives you a future, or the work is already running on another thread; otherwise [VTask](vtask_monad.md), or compare every type in [Choosing a Type](supported-types.md#choosing-a-type) |
~~~

~~~admonish info title="What You'll Learn"
- Say when a `CompletableFuture`'s work starts, and how that differs from `IO` and `VTask`
- Recover from a failed lookup on a `CompletableFuturePath`, and say why retrying needs the work, not the future
- Combine futures that are already running through `CompletableFuture`'s `MonadError` instance
- Say what the instance's error handler receives, and what widening a future costs
~~~

~~~admonish example title="See Example Code"
**The code on this page is [CompletableFutureBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/future/CompletableFutureBook.java)**: the page includes it, so the build compiles and runs it. [CompletableFutureBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/future/CompletableFutureBookTest.java) holds what the page says each method does.
~~~

---

## Java's own type {#javas-own-type}

`java.util.concurrent.CompletableFuture` is how much of Java hands back a result that is not ready yet. The work behind a future has usually started by the time you hold it, often on another thread, where an [IO](io_monad.md) or a [VTask](vtask_monad.md) waits until you run it. Its own methods, such as `thenApply`, `thenCompose` and `exceptionallyCompose`, already chain and recover. Higher-Kinded-J adds an Effect Path, `CompletableFuturePath`, with recovery, timeouts and conversions for application code. It also adds a `MonadError` instance, so that a method written once for any container accepts a future.

The code on this page uses these imports, with `Order` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.example.book.optics.cast.Order;
```

Pricing an order in euros needs two lookups, each from a slow service, and `supplyAsync` starts each on another thread at once. `rateServiceDown` stands in for the rate service failing:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/future/CompletableFutureBook.java:lookups}}
```

## On a `CompletableFuturePath` {#on-a-completablefuturepath}

`Path.future` puts a future on a `CompletableFuturePath`. `recover` gives a value in place of a failure, here `CACHED_RATE`, 1.15, the rate to use when the rate service cannot answer. `parZipWith` joins the result with another Path's, and `join()` waits for the answer. `via` chains a step that needs the previous result, as `thenCompose` does, and [Migration Cookbook: Recipe 4](../effect/migration_cookbook.md#recipe-4-completablefuture-nesting-to-completablefuturepath) turns a nested chain into one. These are the extra imports:

<!-- verify -->
```java
import org.higherkindedj.hkt.effect.CompletableFuturePath;
import org.higherkindedj.hkt.effect.Path;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/future/CompletableFutureBook.java:path}}
```

`ORDER` is worth 92.50 in pounds and shipping costs 7.99, so the live rate gives (92.50 + 7.99) × 1.17 = 117.57:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/future/CompletableFutureBook.java:path_outcome}}
```

A `CompletableFuturePath` also times out with `withTimeout`. `toEitherPath()` waits for the future, then holds a failure as a `Left`. A future that has failed stays failed, so to try failed work again, start it with `CompletableFuturePath.supplyAsyncWithRetry(supplier, attempts, delay)`, which runs the supplier again. A future that completes with `null` fails the Path, which catches every `CompletableFuture<Void>` from `runAsync` or `allOf`: map one to `Unit` first, with `thenApply(_ -> Unit.INSTANCE)`.

## Methods {#methods}

`CompletableFuture`'s own methods are the JDK's. The library adds these:

| Method | What it does |
|---|---|
| `Path.future(future)`, `Path.futureAsync(supplier)` | Put a future on a `CompletableFuturePath`, or start one |
| `Path.futureCompleted(value)`, `Path.futureFailed(exception)` | Build a Path that has already succeeded, or failed with an `Exception` |
| `CompletableFuturePath.supplyAsyncWithRetry(supplier, attempts, delay)` | Start work, and run the supplier again after a failure |
| `map(f)`, `via(f)`, `recover(f)` | Change the value, chain a step, or give a value in place of a failure |
| `parZipWith(other, f)`, `race(other)` | Join two Paths' results, or keep whichever succeeds first, failing only if both fail |
| `withTimeout(duration)` | Fails the Path if the future has not completed in time |
| `join()`, `toEitherPath()` | Wait for the result, or wait and then hold a failure as a `Left` |
| `FUTURE.widen(future)`, `FUTURE.narrow(kind)`, `FUTURE.join(kind)` | Move a future in and out of `Kind`, or wait for one |

## Working with Kind {#working-with-kind}

Generic code, written once for any container, takes a `CompletableFuture` through its `MonadError` instance, whose error type is `Throwable`, as [MonadError](../functional/monad_error.md) shows. Each operation is one of the future's own methods:

| Instance operation | `CompletableFuture` method |
|---|---|
| `map(f, fa)` | `thenApply(f)` |
| `flatMap(f, fa)` | `thenCompose(f)` |
| `ap(ff, fa)`, and `map2` to `map5` | `thenCombine`, completing once both have |
| `of(value)` | `completedFuture(value)` |
| `raiseError(error)` | `failedFuture(error)` |
| `handleErrorWith(fa, handler)` | `exceptionallyCompose(handler)` |

These are the extra imports:

<!-- verify -->
```java
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/future/CompletableFutureBook.java:kind}}
```

`map2` neither starts nor waits for its futures: both lookups were already running, and the future it gives completes once both have. `FUTURE.join` is what waits.

The handler given to `handleErrorWith` receives the exception that failed the future, not the `CompletionException` the JDK wraps it in. `FUTURE.join` rethrows that exception too when it is unchecked; a checked one stays wrapped. A constant fallback is shorter with [`recover` or `recoverWith`](../functional/monad_error.md#constant-fallbacks-recover-and-recoverwith), which build their fallback before the call, so a fallback future runs even when the first one succeeds.

`CompletableFuture` is a JDK class, so it cannot implement `CompletableFutureKind`: `FUTURE.widen` wraps it in a small holder object each time, and `narrow` unwraps it. The instance's `of(null)` keeps the JDK's `null`, where a `CompletableFuturePath` fails.

## When to use `CompletableFuture` {#when-to-use-completablefuture}

| You need | Use |
|---|---|
| To work with APIs that already give you a `CompletableFuture` | `CompletableFuture`, with `CompletableFuturePath` or its instance |
| Work that waits until you run it, on virtual threads, alone or concurrently | [VTask](vtask_monad.md) |
| A side effect that waits until you run it, on the calling thread | [IO](io_monad.md) |
| A value worked out once and kept, with no side effect | [Lazy](lazy_monad.md) |
| Typed errors inside an asynchronous result | The [EitherT](../transformers/eithert_transformer.md) transformer |

---

**Previous:** [Lazy](lazy_monad.md)
**Next:** [VTask](vtask_monad.md)
