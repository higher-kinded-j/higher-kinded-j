<!-- description: Lazy evaluation in Java: defer an expensive computation with Lazy, memoise the result on first use, even across threads, and choose between Lazy and IO. -->

# Lazy: Computing a Value Once, When Needed

_Work a value out the first time it is asked for, then keep it._

~~~admonish abstract title="Lazy at a glance"
| | |
|---|---|
| **Models** | A value worked out when first asked for, then kept |
| **Replaces** | A field filled in on first use, behind a `null` check |
| **Effect Path** | `LazyPath`, shown in [On a `LazyPath`](#on-a-lazypath) |
{{#include ../../../hkj-examples/src/test/resources/golden/foundations-capabilities.md.golden:lazy_monad}}
| **Choose it when** | The work has no side effect and its result can be kept; otherwise [IO](io_monad.md), or compare every type in [Choosing a Type](supported-types.md#choosing-a-type) |
~~~

~~~admonish info title="What You'll Learn"
- Defer expensive work until a reader asks for it, and do it only once
- Say what happens when that work fails, and when it is read from many threads
- Compose deferred values without working any of them out
- Choose between `Lazy` and `IO` for deferred work
~~~

~~~admonish example title="See Example Code"
**The code on this page is [LazyBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/lazy/LazyBook.java)**: the page includes it, so the build compiles and runs it. [LazyBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/lazy/LazyBookTest.java) holds what the page says each method does.
~~~

---

## Working it out once {#working-it-out-once}

A field that is costly to fill and rarely read is often filled on first use, the lazy initialisation idiom: the getter checks for `null`, works the value out, and stores it. Written by hand, that check must also be made safe for several threads, and a `Supplier` would work the value out again on every `get()`. A `Lazy<A>` holds the work and does it the first time it is forced, once, then memoises the result.

The code on this page uses these imports, with `Order` and `LineItem` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.lazy.Lazy;
```

An order view shows a shipping quote only when the order is opened, and working one out is slow. `quotesWorkedOut` counts how often it happens:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/lazy/LazyBook.java:quote}}
```

`ORDER` has three items across two lines:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/lazy/LazyBook.java:outcomes}}
```

Building the view does no work. The first `force()` works the quote out, and the second returns the same value without working it out again. `force()` is declared `throws Throwable`, because the work may throw anything, so the method that calls it must catch `Throwable` or declare it.

`map` and `flatMap` build a new `Lazy` from ones that have not been forced, and work nothing out themselves. The order's total needs both its subtotal and the quote:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/lazy/LazyBook.java:combine}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/lazy/LazyBook.java:combine_outcome}}
```

## When the work fails {#when-the-work-fails}

`Lazy` keeps a failure as it keeps a value: if the work throws on the first `force()`, every later `force()` throws the same exception, without trying the work again. Forced from many threads at once, a `Lazy` still does its work once, and every thread gets the same result.

## On a `LazyPath` {#on-a-lazypath}

Application code can hold the same deferred work on a `LazyPath`. `Path.lazyDefer` takes a `Supplier`, `map` changes the value without working it out, and `get()` works it out once and returns it, rethrowing an exception the work threw. These are the extra imports:

<!-- verify -->
```java
import org.higherkindedj.hkt.effect.LazyPath;
import org.higherkindedj.hkt.effect.Path;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/lazy/LazyBook.java:path}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/lazy/LazyBook.java:path_outcome}}
```

`shown` is `"shipping 7.99"`. `toIOPath()` turns a `LazyPath` into an `IOPath` and keeps the work deferred, while `toMaybePath()` and `toTryPath()` work the value out at once.

## Creating instances {#creating-instances}

`Lazy.defer(work)` holds work to do later. Its `ThrowableSupplier` may throw any exception, a checked one included. `Lazy.now(value)` holds a value already worked out. A `Lazy` always gives a value: `Lazy.now(null)` throws at the call, and work or a `map` function that gives `null` fails when forced, a failure the `Lazy` keeps like any other. [Values, never null](../hkts/core-concepts.md#values-never-null) sets out the rule.

## Methods {#methods}

| Method | What it does |
|---|---|
| `Lazy.defer(work)` | Holds work to do on the first `force()`; the work may throw any exception |
| `Lazy.now(value)` | Holds a value already worked out |
| `force()` | Works the value out the first time, then returns it or rethrows its failure; declared `throws Throwable` |
| `map(f)`, `flatMap(f)` | Build a new `Lazy`, working nothing out |
| `isEvaluated()`, `hasFailed()` | Say whether a value is held, as it is from `Lazy.now`, and whether the work failed |
| `Path.lazyDefer(supplier)`, `Path.lazyNow(value)` | Build a `LazyPath` |
| `get()` on a `LazyPath` | Works the value out once and returns it; rethrows an unchecked failure as it is, and wraps a checked one in a `RuntimeException` |
| `force()` on a `LazyPath` | The same, rethrowing any failure unchanged |
| `LAZY.widen(lazy)`, `LAZY.narrow(kind)`, `LAZY.force(kind)` | Move a `Lazy` in and out of `Kind`, or force one |

## Working with Kind {#working-with-kind}

Generic code, written once for any container, takes `Lazy` through its `Monad` instance, from `Instances.monad(lazy())`. The instance works over `Kind<LazyKind.Witness, A>`, so you widen a `Lazy` to pass it in, and force the result with `LAZY.force`, which is also declared `throws Throwable`. These are the extra imports:

<!-- verify -->
```java
import static org.higherkindedj.hkt.instances.Witnesses.lazy;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.LAZY;

import java.math.RoundingMode;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.lazy.LazyKind;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/lazy/LazyBook.java:kind}}
```

`Lazy` does not implement `LazyKind`, so `LAZY.widen` wraps it in a small holder object each time, and `narrow` unwraps it. The instance has no `MonadError`, so catch a failure around `LAZY.force`.

## When to use `Lazy` {#when-to-use-lazy}

`Lazy` and [IO](io_monad.md) both defer work, and differ in what they do with the result. A kept result is safe only when the work has no side effect: a `Lazy` that read the clock would show the same time for ever.

| You need | Use |
|---|---|
| A costly value, worked out only if read, and then kept | `Lazy` |
| The same as a fluent chain in application code | `LazyPath` |
| Work that reads a file, calls a service or writes to a database, done again on every run | [IO](io_monad.md) |
| Work on another thread | [CompletableFuture](cf_monad.md), already running, or [VTask](vtask_monad.md), which runs when you run it |
| A recursion too deep for the stack | [Trampoline](trampoline_monad.md) |

---

**Previous:** [IO](io_monad.md)
**Next:** [CompletableFuture](cf_monad.md)
