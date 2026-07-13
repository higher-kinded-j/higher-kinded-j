# Either: Modelling Success or Failure

~~~admonish info title="What We'll Learn"
- How `Either<L, R>` makes failure a value rather than an exception
- Why right-bias makes the "happy path" read like a sentence
- How `Left` short-circuits a chain without a single `if` in sight
- How `Either` lifts into Higher-Kinded-J's type-class machinery via `EitherMonad`
- Where `Either` shows up inside the Foundations one-liner
~~~

~~~admonish example title="See Example Code"
[EitherExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/either/EitherExample.java)
~~~

## Failure as a Value

`Either<L, R>` represents a value that is one of two things: a success on the right or an alternative on the left. The convention is older than Java itself, and it pays its way every time we use it.

- `Right<L, R>` is the **success** rail and carries an `R`.
- `Left<L, R>` is the **alternative** rail and carries an `L`, typically an error.

Where exceptions hide failure inside a side channel, `Either` puts it in the return type, in the open, where the compiler can argue with us about it. Where `Optional` and `Maybe` only signal absence, `Either` carries information about *why* something went wrong. We can think of `Either` as `Maybe` with a story to tell about its `Nothing`.

The implementation is a `sealed interface Either<L, R>` with two `record` cases, `Left<L, R>` and `Right<L, R>`. Both directly implement `EitherKind<L, R>` and `EitherKind2<L, R>`, so widening into `Kind` is a zero-allocation cast.

```java
public sealed interface Either<L, R>
    permits Either.Left, Either.Right { ... }

record Left<L, R>(L value)  implements Either<L, R> { ... }
record Right<L, R>(R value) implements Either<L, R> { ... }
```

That is the whole shape of the type. Two cases, one rail each.

~~~admonish note title="Related Types"
For exceptions specifically, see [Try](./try_monad.md), an `Either` specialised with `Throwable` on the left. For fail-slow validation that gathers every error rather than the first, see [Validated](./validated_monad.md). For a success that can also carry non-fatal warnings (a value *and* problems, not one or the other), see [EitherOrBoth](./either_or_both_monad.md), the inclusive-or.
~~~

---

## Two Rails, One Chain

The mental picture worth keeping is two parallel rails. `Right` keeps us on the success rail; `Left` jumps us to the alternative rail and stays there.

<pre class="hkj-railway-diagram">
    <span style="color:#4CAF50"><b>Right</b>  ═══●═══════════════●═══▶  result</span>
    <span style="color:#4CAF50">           map             flatMap</span>
                ╲               ╲   any step returns Left (or starts on Left)
                 ╲               ╲
    <span style="color:#F44336"><b>Left</b>   ────●───────────────●───▶  short-circuit</span>
</pre>

Once we are on the left rail, every downstream `map` and `flatMap` is a no-op. The error reaches the end of the chain unchanged, ready to be inspected with `fold` or pattern matching. We never wrote that propagation logic; the type wrote it for us.

---

## Creating Instances

```java
Either<String, Integer> success = Either.right(123);
Either<String, Integer> failure = Either.left("File not found");

// Null is permitted on either side; the type does not police absence,
// it only models the success/alternative split.
Either<String, Integer> rightNull = Either.right(null);
Either<String, Integer> leftNull  = Either.left(null);
```

If null-safety on the success side matters, lift through [`Maybe`](./maybe_monad.md) first or convert with `Maybe::toEither`.

---

## Working with `Either`

### Checking State

```java
if (success.isRight()) {
    System.out.println("It's Right!");
}
if (failure.isLeft()) {
    System.out.println("It's Left!");
}
```

### Extracting Values (Use With Caution)

`getLeft` and `getRight` exist for the rare case where we already know the rail:

```java
try {
    Integer value = success.getRight(); // 123
    String  error = failure.getLeft();  // "File not found"
    // success.getLeft();               // throws NoSuchElementException
} catch (NoSuchElementException e) {
    System.err.println("Asked the wrong rail: " + e.getMessage());
}
```

In application code, prefer `fold` or pattern matching. The throwing accessors are there for tests and for the few cases where the type system has already proved the rail.

### Folding Both Rails at Once

```java
String resultMessage = failure.fold(
    leftValue  -> "Operation failed with: "    + leftValue,
    rightValue -> "Operation succeeded with: " + rightValue);
// "Operation failed with: File not found"

String successMessage = success.fold(
    leftValue  -> "Error: "   + leftValue,
    rightValue -> "Success: " + rightValue);
// "Success: 123"
```

`fold` is the safe extractor: both rails must be handled, both branches must agree on a return type, and the compiler keeps us honest.

### `map` (Right-Biased)

`map` only touches the right rail.

```java
Function<Integer, String> intToString = Object::toString;

Either<String, String> mappedSuccess = success.map(intToString); // Right("123")
Either<String, String> mappedFailure = failure.map(intToString); // Left("File not found"), unchanged
```

This is what "right-biased" means: `Either` treats success as the default flow, the same way `Optional.map` treats "present" as the default flow. The error rail is preserved verbatim.

### `flatMap`

`flatMap` is the reason we keep coming back to `Either`. It chains operations where each step might fail, and the moment a step returns `Left`, the rest of the chain stops.

```java
Function<String, Either<String, Integer>> parse = s -> {
    try { return Either.right(Integer.parseInt(s.trim())); }
    catch (NumberFormatException e) { return Either.left("Invalid number"); }
};

Function<Integer, Either<String, Integer>> checkPositive = i ->
    (i > 0) ? Either.right(i) : Either.left("Number not positive");

Either<String, Integer> r1 = Either.<String, String>right(" 10 ").flatMap(parse).flatMap(checkPositive);
// Right(10)

Either<String, Integer> r2 = Either.<String, String>right(" -5 ").flatMap(parse).flatMap(checkPositive);
// Left("Number not positive")

Either<String, Integer> r3 = Either.<String, String>right(" abc ").flatMap(parse).flatMap(checkPositive);
// Left("Invalid number")

Either<String, Integer> r4 = Either.<String, String>left("Initial error").flatMap(parse).flatMap(checkPositive);
// Left("Initial error")
```

Three different errors, one chain, no `if`, no `try`. The `Left` reaches the end of the pipeline carrying its original message.

---

## Lifting `Either` Into the Type-Class Machinery

When we want to write code that is generic over the container, we go through `EitherMonad`. It implements `MonadError<EitherKind.Witness<L>, L>` for any chosen left type `L`.

```java
MonadError<EitherKind.Witness<String>, String> eitherMonad = Instances.monadError(either());

// Widen
Either<String, Integer> myEither = Either.right(10);
Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(myEither);

// Map through the monad
Kind<EitherKind.Witness<String>, String> mapped =
    eitherMonad.map(Object::toString, kind);

// Sequence with flatMap
Function<Integer, Kind<EitherKind.Witness<String>, Double>> nextStep = i ->
    EITHER.widen(i > 5 ? Either.right(i / 2.0) : Either.left("TooSmall"));

Kind<EitherKind.Witness<String>, Double> sequenced =
    eitherMonad.flatMap(nextStep, kind);

// Raise an error explicitly
Kind<EitherKind.Witness<String>, Integer> errorKind = eitherMonad.raiseError("E101");

// Recover from an error
Kind<EitherKind.Witness<String>, Integer> recovered =
    eitherMonad.handleErrorWith(errorKind, error -> {
        System.out.println("Handling error: " + error);
        return eitherMonad.of(0);
    });

// Narrow
Either<String, Integer> finalEither = EITHER.narrow(recovered);
// Right(0)
```

The handler above uses the error (it logs it), so `handleErrorWith` is the right tool. When you only want a constant fallback and don't need the error, the [`recover`/`recoverWith` shortcuts](../functional/monad_error.md#constant-fallbacks-recover-and-recoverwith) are shorter. `eitherMonad.recover(errorKind, 0)` yields the same `Right(0)`.

This is the door through which `Either` joins every generic combinator the rest of the library defines: `traverse`, `sequence`, `flatMapN`, the lot.

---

## Back to the One-Liner

The Foundations one-liner is built on `EitherPath`, the fluent wrapper around `Either`:

```java
repo.find(id)
    .toEitherPath()              // <-- absence becomes a typed left
    .focus().attributes().at(key)
    .modify(spec::validateAndCoerce)
    .flatMap(repo::save);        // <-- EitherMonad.flatMap dispatches here
```

Two of the six layers in that line are `Either` doing its job. `.toEitherPath()` is a natural transformation from the absence-shaped `MaybePath` into a failure-shaped `EitherPath`, lifting "the node was not found" into a typed left. `.flatMap(repo::save)` is `EitherMonad.flatMap`: if validation produced a `Left`, `save` is silently skipped and the original error reaches the caller.

We never wrote the propagation, the conversion, or the short-circuit. The type system arranged them for us.

---

## When to Use `Either`

| Scenario | Use |
|----------|-----|
| Domain-specific typed errors (validation, business rules) | `Either<MyError, A>`, where the left carries context |
| Sequential operations where any step can fail | `Either` with `flatMap`; short-circuits on `Left` |
| Combining typed errors with async or other effects | [EitherT](../transformers/eithert_transformer.md) transformer; see the [Order Example](../hkts/order-walkthrough.md) |
| Wrapping `Throwable`-based APIs | Prefer [Try](./try_monad.md), an `Either<Throwable, A>` with extras |
| Accumulating multiple validation errors | Prefer [Validated](./validated_monad.md) with applicative operations |
| Application-level pipelines with a fluent API | Prefer [EitherPath](../effect/path_either.md) |

~~~admonish important title="Key Points"
- Model domain failures explicitly rather than reaching for exceptions
- Sequence operations that can each fail; the type stops the chain at the first `Left`
- Compose with other effects through [EitherT](../transformers/eithert_transformer.md) or, more often, through [EitherPath](../effect/path_either.md)
- Prefer it over returning `null` or throwing for any failure that the caller can reasonably handle
~~~

---

~~~admonish tip title="Effect Path Alternative"
For most application code, prefer **[EitherPath](../effect/path_either.md)**, which wraps `Either` and provides:

- Fluent composition with `map`, `via`, `recover`
- Direct integration with the [Focus DSL](../optics/focus_dsl.md)
- A consistent API shared across every effect type

```java
// Manual Either chaining
Either<Error, User>  user  = findUser(id);
Either<Error, Order> order = user.flatMap(u -> createOrder(u));

// EitherPath, same logic, less ceremony
EitherPath<Error, Order> order = Path.either(findUser(id))
    .via(u -> createOrder(u));
```

See [Effect Path Overview](../effect/effect_path_overview.md) for the complete guide.
~~~

~~~admonish example title="Benchmarks"
`Either` has dedicated JMH benchmarks measuring instance reuse, short-circuit efficiency, and chain composition. Headline expectations:

- `leftMap` is 5-10x faster than `rightMap`; a `Left` reuses the same instance with zero allocation
- `leftLongChain` is 10-50x faster than `rightLongChain`; the reuse benefit compounds across 50-deep chains
- If `Left` operations allocate memory, instance reuse is broken and we have a regression

```bash
./gradlew :hkj-benchmarks:jmh --includes=".*EitherBenchmark.*"
```

See [Benchmarks & Performance](../benchmarks.md) for full details, expected ratios, and how to interpret results.
~~~

---

**Previous:** [CompletableFuture](cf_monad.md)
**Next:** [EitherOrBoth](either_or_both_monad.md)
