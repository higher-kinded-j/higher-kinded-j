# MonadError: Handling Errors Gracefully

> *"The test of a first-rate intelligence is the ability to hold two opposed ideas in mind at the same time."*
>
> – F. Scott Fitzgerald, *The Crack-Up*

A resilient workflow holds two paths in mind simultaneously: the path where everything succeeds, and the path where things go wrong. `MonadError` is the type class that lets us spell both cleanly, in the same code, without sliding into nested try/catch.

~~~admonish info title="What We'll Learn"
- How `MonadError` extends `Monad` with a typed notion of failure
- Using `raiseError` to construct a failed computation declaratively
- Recovering with `handleErrorWith` (effect-level) and `handleError` (value-level)
- Chaining recovery so each fallback only fires when the previous one fails
- Where `MonadError` shows up inside the Foundations one-liner
~~~

~~~admonish example title="Hands-On Practice"
[Tutorial05_MonadErrorHandling.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial05_MonadErrorHandling.java)
~~~

## The Problem: Scattered Try-Catch

A configuration loader parses a file, validates the parsed settings, then opens a database connection. Each step can fail with a meaningful error, and we want different recovery for each. In imperative Java, we end up with nested try/catch:

```java
try {
    Config config = parseConfigFile(path);
    try {
        Settings settings = validateSettings(config);
        try {
            return connectToDatabase(settings);
        } catch (DbException e) {
            return connectToFallbackDb(settings);
        }
    } catch (ValidationException e) {
        log.error("Bad config: " + e.getMessage());
        throw e;
    }
} catch (ParseException e) {
    return loadDefaultConfig();
}
```

Three levels of nesting, three different recovery rules, and the business logic is sandwiched between them. Reordering the steps means re-arranging the pyramid. Adding a fourth step means adding a fourth level. Reading the code top to bottom does not tell us what the workflow *does*; it tells us how the author chose to indent.

---

## The Solution: `raiseError` and `handleErrorWith`

`MonadError` extends `Monad` with two operations that turn try/catch inside out.

1. **`raiseError(E error)`** constructs a failed computation by lifting an error into the monadic context.
2. **`handleErrorWith(fa, handler)`** inspects a failure and provides a fallback computation.

```
   raiseError("config not found")
        │
        ▼
   Kind<F, A>  =  Left("config not found")
        │
        ├── handleErrorWith ──> recovery function ──> Kind<F, A> = Right(defaults)
        │
        └── (no handler)     ──> propagates as Left("config not found")
```

The same workflow, rebuilt with `MonadError` over `Either<String, A>`:

```java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherMonad;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

MonadError<EitherKind.Witness<String>, String> me = Instances.monadError(either());

public Kind<EitherKind.Witness<String>, Config> parseConfig(String path) {
    if (!Files.exists(Path.of(path))) {
        return me.raiseError("Config file not found: " + path);
    }
    return me.of(Config.parse(path));
}

public Kind<EitherKind.Witness<String>, Settings> validate(Config config) {
    if (config.dbHost().isBlank()) {
        return me.raiseError("Missing required setting: db.host");
    }
    return me.of(Settings.from(config));
}

public Kind<EitherKind.Witness<String>, Connection> connect(Settings settings) {
    if (!settings.isReachable()) {
        return me.raiseError("Cannot reach database: " + settings.dbHost());
    }
    return me.of(Connection.open(settings));
}

// Compose the workflow with flatMap, then layer recovery on top
Kind<EitherKind.Witness<String>, Connection> workflow =
    me.flatMap(config ->
        me.flatMap(settings ->
            connect(settings),
        validate(config)),
    parseConfig("/etc/app.conf"));

// Recover from connection failures by trying a fallback database
Kind<EitherKind.Witness<String>, Connection> resilient = me.handleErrorWith(
    workflow,
    error -> {
        if (error.startsWith("Cannot reach database")) {
            return connect(Settings.fallback());
        }
        return me.raiseError(error);
    });
```

The business logic reads top to bottom. Recovery is a separate layer applied at the end. Re-ordering steps means re-ordering `flatMap` calls; adding a step means adding a `flatMap` call. The shape of the code matches the shape of the problem.

---

~~~admonish note title="Interface Signature"
```java
@NullMarked
public interface MonadError<F extends WitnessArity<TypeArity.Unary>, E> extends Monad<F> {

  <A> @NonNull Kind<F, A> raiseError(@Nullable E error);

  <A> @NonNull Kind<F, A> handleErrorWith(
      Kind<F, A> ma,
      Function<? super E, ? extends Kind<F, A>> handler);

  // Value-level recovery; the handler returns a plain A which is auto-lifted
  default <A> @NonNull Kind<F, A> handleError(
      Kind<F, A> ma,
      Function<? super E, ? extends A> handler) {
    return handleErrorWith(ma, error -> of(handler.apply(error)));
  }
}
```
~~~

---

## Recovery Patterns

### Value-Level Recovery with `handleError`

**The problem.** An operation might fail, and we have a sensible default value sitting in plain Java.

**The solution.** `handleError` takes a function `E -> A` and lifts the result back into the monad for us.

```java
MonadError<EitherKind.Witness<String>, String> me = Instances.monadError(either());

Kind<EitherKind.Witness<String>, Integer> safeDivide(int a, int b) {
    return b == 0
        ? me.raiseError("Cannot divide by zero")
        : me.of(a / b);
}

Kind<EitherKind.Witness<String>, Integer> result = me.handleError(
    safeDivide(10, 0),
    error -> 0);
// Right(0)
```

### Effect-Level Recovery with `handleErrorWith`

**The problem.** Recovery itself might fail. Falling back to a secondary database is no good if that database is also down.

**The solution.** `handleErrorWith` takes `E -> Kind<F, A>`. The recovery function can return a success, another failure, or whatever the type allows.

```java
Kind<EitherKind.Witness<String>, Integer> result = me.handleErrorWith(
    safeDivide(10, 0),
    error -> {
        log.warn("Division failed: " + error + ", trying alternative");
        return safeDivide(10, 2);
    });
// Right(5)
```

### Chained Recovery

**The problem.** Several fallbacks, each able to fail.

**The solution.** Stack `handleErrorWith` calls. Each layer only triggers when the previous one is still failing.

```java
Kind<EitherKind.Witness<String>, Config> config =
    me.handleErrorWith(
        me.handleErrorWith(
            loadConfigFromFile(),
            e -> loadConfigFromEnv()),
        e -> me.of(Config.defaults()));
// File first, then environment, then defaults
```

For longer fallback chains, [Effect Path's `recoverWith`](../effect/path_either.md) reads more naturally; the same logic, less ceremony.

### Constant fallbacks: `recover` and `recoverWith`

When a fallback **ignores the error** and simply substitutes a constant, two shortcuts say so directly, with no throwaway `e -> …` lambda:

| Long form | Shortcut |
|-----------|----------|
| `me.handleErrorWith(ma, e -> me.of(value))` | `me.recover(ma, value)` |
| `me.handleErrorWith(ma, e -> fallback)` | `me.recoverWith(ma, fallback)` |

In the chained example above, only the innermost layer is a constant, so just that layer collapses:

```java
// defaults is a plain value → recover; env is a computation we only want on failure → keep handleErrorWith
Kind<EitherKind.Witness<String>, Config> config =
    me.recover(
        me.handleErrorWith(loadConfigFromFile(), e -> loadConfigFromEnv()),
        Config.defaults());
```

Two things to know:

- **`recoverWith` takes a value, not a lambda, so its fallback is evaluated eagerly**, even when `ma` succeeds. Reach for it only when the fallback is already built or cheap; if producing it is expensive or effectful-on-demand (like `loadConfigFromEnv()` above), keep `handleErrorWith` so it runs only on the failure path.
- **The two methods guard different arguments.** `recoverWith` rejects a `null` *fallback* (and a `null` source) eagerly on every `MonadError` instance. A mistyped fallback fails fast at the call site instead of surfacing later from inside `handleErrorWith`. `recover` rejects a `null` *source*, but its *value* is `@Nullable` **by design**: a null value is lifted through `of`, so `recover(failure, null)` is a valid result (`Success(null)`, `Nothing`, or empty, depending on the type), not an error. (One wrinkle: `Validated` overrides `recoverWith` but not `recover`, because `Validated.of` rejects null; a null source to `Validated.recover` still fails fast, just with a message naming the underlying `handleErrorWith`.)

---

## Back to the One-Liner

In the line we keep returning to:

```java
repo.find(id)
    .toEitherPath()                  // <-- raiseError equivalent: absence becomes a typed Left
    .focus().attributes().at(key)
    .modify(spec::validateAndCoerce)
    .flatMap(repo::save);
```

`.toEitherPath()` is the user-facing surface of `raiseError` for the absence case: a missing record becomes a typed `Left` carrying the not-found story. Anywhere downstream we wanted to recover, `handleErrorWith` (or its Effect Path sibling `recoverWith`) is the door we would walk through. We did not need either in this line because the contract of the service method is "fail loudly to the caller", but the moment we want a per-error recovery rule, `MonadError` is the layer that hosts it.

---

## Things People Get Wrong

~~~admonish warning title="Common Misunderstandings"
- **"`raiseError` throws an exception."** It does not. It constructs a value of `Kind<F, A>` that *represents* failure. Nothing is thrown, and the rest of the chain politely skips itself. The thrown-exception equivalent is `IO.delay(() -> { throw ...; })`, and even that does not throw until interpretation.
- **"`handleErrorWith` and a try/catch are the same."** Mechanically similar, semantically different. A try/catch is wired to call-stack unwinding; `handleErrorWith` is just a function call that runs on a value. The latter composes; the former does not.
- **"I have to use `Either`."** Any `MonadError` instance works: `Try`, `Validated` (in its Monad shape), `CompletableFutureMonad`, `IOMonad`, the `*Path` types. Pick the error type that fits the domain; the recovery story is the same.
- **"Recovery is for the end of the chain."** It can be anywhere. Layering `handleErrorWith` between two `flatMap` calls is the way we say "if step three fails, try step three again with a different argument before continuing".
~~~

---

~~~admonish info title="Key Takeaways"
* `raiseError` creates a failed computation declaratively, with no thrown exception
* `handleErrorWith` is effect-level recovery; the handler can itself succeed or fail
* `handleError` is value-level recovery; the handler returns a plain value that is auto-lifted
* Recovery composes by stacking; each layer only fires when the previous one is still failing
* Code written against `MonadError<F, E>` works with `Either`, `Try`, `Validated`, `IO`, or any other error-capable monad
~~~

~~~admonish tip title="See Also"
- [Monad](monad.md) - The base type class that MonadError extends
- [Either](../monads/either_monad.md) - The most common MonadError instance for typed errors
- [Try](../monads/try_monad.md) - MonadError specialised for `Throwable` errors
- [One Line, Six Layers](../hkts/one_line_six_layers.md) - Where this fits in the wider Foundations picture
~~~

~~~admonish tip title="Further Reading"
- **Baeldung**: [Functional Programming in Java](https://www.baeldung.com/java-functional-programming) - Practical guide to functional patterns in Java
- **Mark Seemann**: [An Either Functor](https://blog.ploeh.dk/2019/01/07/either-bifunctor/) - Step-by-step introduction to Either as a functional error-handling tool
~~~

~~~admonish info title="Hands-On Learning"
Practice error handling in [Tutorial 05: Monad Error Handling](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial05_MonadErrorHandling.java) (7 exercises, ~10 minutes).
~~~

---

**Previous:** [Monad](monad.md)
**Next:** [Semigroup and Monoid](semigroup_and_monoid.md)
