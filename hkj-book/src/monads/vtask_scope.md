<!-- description: Structured concurrency in Java with Scope: fork VTasks, join them all, the first success or every error, cancel the rest, and time the whole group out. -->

# Scope: Structured Concurrency

_Start tasks together, end them together, and join their results the way you choose._

<!-- source: Nathaniel J. Smith, "Notes on structured concurrency, or: Go statement considered harmful", vorpus.org, 25 April 2018; text checked against the post -->
> _"Here's the core idea: every time our control splits into multiple concurrent paths, we want to make sure that they join up again."_
>
> — Nathaniel J. Smith, *Notes on structured concurrency, or: Go statement considered harmful*

A `Scope` is where they join up: it forks its tasks, and the `VTask` from its `join()` ends only when every task has finished or been cancelled.

~~~admonish info title="What You'll Learn"
- Fork tasks into a `Scope` and join them, so none outlives the scope that started it
- Choose how they are joined: every result, the first success, the first to finish, or every failure
- Hold a scope's failure as a value, and time the whole group out, cancelling the tasks still running
- Run one `Scope` again, retry it, or share it between threads
- Use a `ScopeJoiner` with a `Scope`, or with a `StructuredTaskScope` you open yourself
~~~

~~~admonish example title="See Example Code"
**The code on this page is [ScopeBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/scope/ScopeBook.java)**: the page includes it, so the build compiles and runs it. [ScopeBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/scope/ScopeBookTest.java) holds what the page says each method does. [ScopeExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ScopeExample.java) is a longer example.
~~~

---

## Tasks that start and end together {#tasks-that-start-and-end-together}

A task handed to an `ExecutorService` can outlive the method that submitted it, and a failure in one task leaves the others running. Java 25's `StructuredTaskScope` ([JEP 505](https://openjdk.org/jeps/505)) ties tasks to a block instead, as [Nathaniel J. Smith's essay](https://vorpus.org/blog/notes-on-structured-concurrency-or-go-statement-considered-harmful/) argued for: they are forked inside a `try`, and the block does not end until each has finished. A `Scope` holds the same shape as a value. You fork `VTask`s into it, and `join()` gives a `VTask` that, when it runs, opens a `StructuredTaskScope`, forks each task on a virtual thread of its own, and waits for them.

~~~admonish warning title="A preview API"
`Scope` and `ScopeJoiner` are built on `StructuredTaskScope`, a preview feature in Java 25, so code that uses them needs `--enable-preview` to compile and to run, as the [VTask](vtask_monad.md#on-a-vtaskpath) page explains.
~~~

The code on this page uses these imports, with `Order` and `LineItem` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;
```

Booking delivery for an order means asking couriers for a quote, and checking that the warehouse has the stock. Each call blocks until its service answers:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/scope/ScopeBook.java:services}}
```

`Scope.allSucceed()` waits for every task and gives their results in the order they were forked. `Scope.anySucceed()` gives the first success, and `Scope.firstComplete()` the first task to finish, success or failure; both cancel the tasks still running. `forkAll` forks a list of tasks, and `fork` one:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/scope/ScopeBook.java:quotes}}
```

`ORDER` is an order for two lamps and a shade. `COURIERS` holds three couriers, which quote 7.99, 6.50 and 9.20, and `DOWN` stands in for a courier whose service is down:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/scope/ScopeBook.java:quote_outcomes}}
```

Building a scope runs nothing, and each run forks its tasks afresh.

## Choosing a joiner {#choosing-a-joiner}

Java's `StructuredTaskScope` takes a `Joiner`, which decides when the scope is done and what its `join()` returns. A `Scope`'s factory chooses one:

| Factory | Gives | When a task fails | Use it for |
|---|---|---|---|
| `allSucceed()` | Every result, in fork order | Cancels the rest, and fails with its exception | Lookups that must all succeed |
| `anySucceed()` | The first success, cancelling the rest | Fails only if every task fails, with one of their exceptions | Redundant requests, any one of which will do |
| `firstComplete()` | The first task to finish, success or failure, cancelling the rest | Fails if that first task failed | Taking whichever answer comes first |
| `accumulating(mapper)` | A `Validated`: every result, or every failure | Keeps it, mapped by `mapper`, and waits for the rest | Checks that should all report |
| `withJoiner(joiner)` | What the `ScopeJoiner` decides | As the joiner decides | One joining policy shared by several scopes |

For a fallback that answers when the fast path fails, use `anySucceed`: `firstComplete` takes a failure if it comes first.

The scope is opened on the thread that runs the `VTask`, so its tasks see that thread's `ScopedValue` bindings, such as the [request context](../effect/context_request.md). `named(...)` gives the scope a name, which a thread dump in JSON format shows.

## Keeping every failure {#keeping-every-failure}

A form, or an order, that fails on its first problem sends the user away to fix it, and back to find the next. `Scope.accumulating(mapper)` runs every task to the end, cancelling none, and gives a [Validated](validated_monad.md): every result if all succeeded, or every failure, each mapped by `mapper`, in fork order. Checking each line of the order against the warehouse:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/scope/ScopeBook.java:stock}}
```

`SHORT` stands in for a warehouse with one lamp and no shades, and `WELL_STOCKED` for one with plenty of both:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/scope/ScopeBook.java:stock_outcome}}
```

Both shortfalls are reported at once. Each scope calls `mapper` on the thread that joins it, so a `mapper` shared by scopes running at once must be thread-safe.

## Holding the result as a value {#holding-the-result-as-a-value}

`join()` gives a task that fails when the scope does. `joinSafe()`, `joinEither()` and `joinMaybe()` give one that always succeeds, holding the failure as a `Try`, an `Either` with the exception on the left, or a `Maybe` that is `Nothing`. Use them to handle a failure inside the pipeline, rather than where the task is run. These are the extra imports:

<!-- verify -->
```java
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.trymonad.Try;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/scope/ScopeBook.java:safe}}
```

## Timing a scope out {#timing-a-scope-out}

`timeout(duration)` bounds the whole scope. When it expires, the tasks still running are interrupted, those not yet started never start, and the run fails with a `TimeoutException` once they have stopped. `SILENT` stands in for a courier that never answers. This is the extra import:

<!-- verify -->
```java
import java.time.Duration;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/scope/ScopeBook.java:timeout}}
```

A scope's timeout cancels the tasks still running, as [`VTask.timeout`](vtask_monad.md#on-a-vtaskpath) cancels its one task. A task that ignores interruption, such as one spinning in a loop, holds the failure back until it finishes. Each run's timeout starts when that run opens its scope. A timeout of zero or less expires at once, so the run fails unless its tasks have already finished.

## Running a scope again {#running-a-scope-again}

A `Scope` is a description, like the `VTask` its `join()` returns. Each run of that `VTask` opens a `StructuredTaskScope` of its own, with a new `Joiner`. So you can run it again, retry it, or run it from several threads at once, and each run joins only the tasks it forked.

Here the quote service is busy on its first call, and [`Retry`](../resilience/retry.md#vtask-native-retry) runs the same `prices` task again. These are the extra imports:

<!-- verify -->
```java
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.resilience.Retry;
import org.higherkindedj.hkt.resilience.RetryPolicy;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/scope/ScopeBook.java:run_again}}
```

The first attempt fails. The second forks both tasks again and joins only those, so the first attempt's failure cannot fail it, and it returns `[quote, rates]`. Running `prices` once more gives the same list. A retry runs every task again, `rates` included, so retry a `Scope` whose tasks are safe to repeat.

## A joiner of your own {#a-joiner-of-your-own}

A `ScopeJoiner` is a joining policy on its own: `ScopeJoiner.allSucceed()`, `anySucceed()`, `firstComplete()` and `accumulating(mapper)` match the `Scope` factories. `Scope.withJoiner` takes one, and `ScopeJoiner.joiner()` builds the JDK's `Joiner` for a scope you open yourself. These are the extra imports:

<!-- verify -->
```java
import java.util.concurrent.StructuredTaskScope;
import org.higherkindedj.hkt.vtask.ScopeJoiner;
```

`twoQuotesByHand` opens a scope with a `Joiner` from the policy, as Java's own API asks:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/scope/ScopeBook.java:by_hand}}
```

The same policy serves a `Scope` and the scope opened by hand:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/scope/ScopeBook.java:joiner}}
```

A `ScopeJoiner` holds its configuration, not a scope's state. Each call to `joiner()` returns a new `Joiner`, because a `StructuredTaskScope` needs one that no other scope has used, so one `ScopeJoiner` serves any number of scopes.

`ScopeJoiner.firstSuccessEither()` races tasks that each give an `Either`. The first `Right` wins and cancels the rest. A `Left` does not end the race; if every task gives one, the join gives a `Left` of all their errors, in fork order. A task that throws is not collected: with no `Right`, the join fails with its exception. [`VResultPath.firstSuccess`](../effect/path_vresult.md#outcome-aware-structured-concurrency) races typed paths through it.

`ScopeJoiner.resultEither()` is deprecated, and scheduled for removal in 0.6.0. Use `Scope.joinEither()` instead, or, with a scope you open yourself, the value its `join()` returns, or the cause of the `FailedException` it throws.

## Methods {#methods}

| Method | What it does |
|---|---|
| `Scope.allSucceed()`, `anySucceed()`, `firstComplete()`, `accumulating(mapper)`, `withJoiner(joiner)` | Build a scope that joins its tasks as [Choosing a joiner](#choosing-a-joiner) says |
| `fork(task)`, `forkAll(tasks)` | Give a new scope with the tasks added, running nothing |
| `timeout(duration)`, `named(name)` | Give a new scope that times out, or carries a name for thread dumps |
| `join()` | Gives a `VTask` that runs the scope and gives the joined result, or fails |
| `joinSafe()`, `joinEither()`, `joinMaybe()` | Give a `VTask` that holds a failure as a `Try`, an `Either` or a `Maybe` |
| `taskCount()`, `hasTimeout()`, `getTimeout()` | Say how many tasks are forked, and whether a timeout is set |
| `ScopeJoiner.allSucceed()` and the others, `firstSuccessEither()` | Give a joining policy, for `withJoiner` or a scope you open yourself |
| `joiner.joiner()` | Gives a new JDK `StructuredTaskScope.Joiner` |

## When to use a `Scope` {#when-to-use-a-scope}

| You need | Use |
|---|---|
| Tasks that must all succeed, joined into one value | [`Par`](vtask_monad.md#parallel-composition-with-par), or `parZipWith` on a `VTaskPath` |
| Tasks joined by first success, first to finish or every failure, or under one timeout | `Scope` |
| Typed results raced or gathered on a railway | [VResultPath](../effect/path_vresult.md#outcome-aware-structured-concurrency) |
| A resource held while the scope's tasks use it | [`Resource` with a `Scope`](vtask_resource.md#with-a-scope) |

~~~admonish info title="Hands-On Learning"
Practise `Scope` in [TutorialScope](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/concurrency/TutorialScope.java) (10 exercises), part of the [Scope & Resource journey](../tutorials/concurrency/scope_resource_journey.md).
~~~

---

**Previous:** [VTask](vtask_monad.md)
**Next:** [Resource Management](vtask_resource.md)
