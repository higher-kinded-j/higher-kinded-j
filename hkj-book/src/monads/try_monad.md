<!-- description: Run Java code that throws with Try, keep the exception as a value instead of a try/catch block, and chain and recover from it. -->

# Try: Capturing Exceptions as Values

_Run code that throws, and keep the exception as a value you can map, chain and recover from._

~~~admonish abstract title="Try at a glance"
| | |
|---|---|
| **Models** | A value, or the exception that stopped the computation |
| **Replaces** | A `try`/`catch` block around a call that throws |
| **Effect Path** | [`TryPath`](../effect/path_try.md) |
{{#include ../../../hkj-examples/src/test/resources/golden/foundations-capabilities.md.golden:try_monad}}
| **Choose it when** | The failure is an exception from code you call; otherwise [Either](either_monad.md), or compare every type in [Choosing a Type](supported-types.md#choosing-a-type) |
~~~

~~~admonish info title="What You'll Learn"
- Turn a call that throws into a `Try`, and choose between `Try.of` and `Try.attempt`
- Chain steps that can each throw, and say which exception reaches the end
- Recover from a failure with a value, or with another attempt
- Read a `Try` with a `switch` or `foldFailureFirst`
- Hand a `Try`'s failure to an `Either` chain through `TryPath`
~~~

~~~admonish example title="See Example Code"
**The code on this page is [TryBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trymonad/TryBook.java)**: the page includes it, so the build compiles and runs it. [TryBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/trymonad/TryBookTest.java) holds what the page says each method does.
~~~

---

## Exceptions as values {#exceptions-as-values}

Much of the JDK reports failure by throwing: `Integer.parseInt`, `new BigDecimal` and `Files.readString` among them. A `try`/`catch` block splits the result in two, the value on one path and the exception in the `catch` block, so the outcome cannot be stored, passed to `map` or returned as one value. `Try<T>` runs the call and keeps what happened. A `Success` holds the value, and a `Failure` holds the exception the call threw. It is close to an `Either` whose left side is always an exception, with the `try`/`catch` written for you.

The code on this page uses these imports, with `LineItem` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.trymonad.Try;
```

A wholesaler's feed sends order lines as text, such as `LAMP,2,40.00`. Reading one calls two methods that throw on text they cannot read, so `Try.of` runs them:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trymonad/TryBook.java:parse}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trymonad/TryBook.java:parse_outcomes}}
```

`Try.of` takes a `Supplier`, and a `Supplier` cannot throw a checked exception, so javac refuses a call such as `Files.readString` there, with "unreported exception IOException; must be caught or declared to be thrown":

<!-- verify:rejects "must be caught or declared to be thrown" -->
```java
Try<String> feed = Try.of(() -> Files.readString(Paths.get("feed.csv")));
```

`Try.attempt` takes a function that may throw one. `MISSING_FEED` names a file that does not exist:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trymonad/TryBook.java:attempt}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trymonad/TryBook.java:attempt_outcome}}
```

Both catch any `Exception`, checked or not, and so do `Try`'s `map`, `flatMap`, `recover` and `recoverWith` when their function throws. A caught `InterruptedException` sets the thread's interrupt flag again. An `Error`, such as `StackOverflowError`, propagates out of them, as it passes a `catch (Exception e)` clause.

## Chaining and recovering {#chaining-and-recovering}

On a `Success`, `flatMap` passes the value to a step that returns a `Try` of its own. On a `Failure`, the step never runs, and the first exception reaches the end:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trymonad/TryBook.java:chain}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trymonad/TryBook.java:chain_outcomes}}
```

`map` changes a `Success`'s value, and an exception its function throws becomes a `Failure` too. To leave the failure behind, `recoverWith` makes another attempt, and `recover` gives a value in its place:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trymonad/TryBook.java:recover}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trymonad/TryBook.java:recover_outcomes}}
```

A feed that cannot be read counts as empty here, so `recover` gives an empty `String`. Neither method runs its function on a `Success`, and an exception that function throws becomes the new `Failure`.

## Reading a `Try` {#handling-failures-foldfailurefirst-recover-recoverwith}

A `switch` over the two cases reads the result, and the compiler checks that both are handled:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trymonad/TryBook.java:describe}}
```

`foldFailureFirst(onFailure, onSuccess)` does the same with two functions, and takes the failure function first, as `Either.fold` takes the `Left` function first. `get()` returns the value or throws the exception, so it is declared `throws Throwable` and a caller must catch `Throwable`; keep it for tests.

## Creating instances {#creating-instances}

`Try.success(value)` and `Try.failure(throwable)` build either case directly. A `Success` always holds a value, so `Try.success(null)` throws `NullPointerException`. A function given to `Try.of`, `map` or `recover` that returns `null` gives a `Failure` holding a `NullPointerException`. A `flatMap` or `recoverWith` function that returns `null` in place of a `Try` throws `KindUnwrapException`. [Values, never null](../hkts/core-concepts.md#values-never-null) has the rule for every type.

## Methods {#methods}

| Method | What it does |
|---|---|
| `Try.of(supplier)` | Runs the supplier now, giving a `Success` of its value, or a `Failure` of the `Exception` it throws |
| `Try.attempt(supplier)` | The same, for a supplier that throws a checked exception |
| `Try.success(value)`, `Try.failure(throwable)` | Build either case directly |
| `isSuccess()`, `isFailure()` | Say which case it holds |
| `map(f)` | Changes a `Success`'s value; an exception `f` throws becomes a `Failure` |
| `flatMap(f)` | Runs a step that gives a `Try`; a `Failure` passes through |
| `recover(f)` | Turns a `Failure` into a `Success` of the value `f` gives |
| `recoverWith(f)` | Turns a `Failure` into the `Try` that `f` gives |
| `foldFailureFirst(onFailure, onSuccess)` | Turns either case into one result |
| `match(onSuccess, onFailure)` | Runs an action for the case it holds, and takes the success action first |
| `orElse(other)`, `orElseGet(supplier)` | Return the value, or a fallback |
| `get()` | Returns the value, or throws the exception it holds |
| `toEither(f)` | Gives an `Either` whose `Left` is `f` applied to the exception |

A call to the success-first `fold` of releases before 0.5.0 does not compile: rename it `foldFailureFirst` and swap its two functions, or run `SwapTryFoldToFoldFailureFirstRecipe`, as [0.5.0 deprecation migration](../tooling/openrewrite.md#050-deprecation-migration) shows.

## On a `TryPath` {#on-a-trypath}

Application code can hold the same steps on a [`TryPath`](../effect/path_try.md). `Path.tryPath` takes a `Try`, `via` chains a step, and `toEitherPath` turns the exception into an error of your own. From there, `via` chains on to `EitherPath` steps with the same error type, here `String`. These are the extra imports:

<!-- verify -->
```java
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trymonad/TryBook.java:path}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trymonad/TryBook.java:path_outcome}}
```

## Working with Kind {#working-with-kind}

Generic code, written once for any container, takes `Try` through its `MonadError` instance, whose error type is `Throwable`, as [MonadError](../functional/monad_error.md) shows. The instance works over `Kind<TryKind.Witness, A>`, so you widen a `Try` to pass it in, and narrow the result to read it. These are the extra imports:

<!-- verify -->
```java
import static org.higherkindedj.hkt.instances.Witnesses.try_;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.trymonad.TryKind;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trymonad/TryBook.java:kind}}
```

The line could not be read, so `flatMap` never ran the price check, and `handleError` turned the exception into a reply. Widening allocates nothing: `Try` implements `TryKind` itself, so `widen` checks for `null` and returns the same object.

## When to use `Try` {#when-to-use-try}

| You need | Use |
|---|---|
| The exception from a JDK or library call, kept as a value | `Try` |
| The same steps as a fluent chain in application code | [TryPath](../effect/path_try.md) |
| An error type of your own, rather than `Throwable` | [Either](either_monad.md), converting with `toEither` |
| Every error from checks that do not depend on one another | [Validated](validated_monad.md) |
| Work described now and run later, rather than at once | [IO](io_monad.md) or [VTask](vtask_monad.md) |

~~~admonish info title="Hands-On Learning"
Practise recovering from errors with `MonadError`, and capturing an exception with `Try.of`, in [Tutorial 05: MonadError Handling](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial05_MonadErrorHandling.java) (7 exercises).
~~~

---

**Previous:** [Accumulating Assembly](validated_assembly.md)
**Next:** [EitherOrBoth](either_or_both_monad.md)
