# MonadError: Handling Errors Gracefully

> *"The test of a first-rate intelligence is the ability to hold two opposed ideas in mind at the same time."*
>
> -- F. Scott Fitzgerald, *The Crack-Up*

A resilient workflow must hold two paths in mind simultaneously: the path where everything succeeds, and the path where things go wrong. `MonadError` gives you the tools to express both cleanly.

~~~admonish info title="What You'll Learn"
- How MonadError extends Monad with explicit error handling capabilities
- Using `raiseError` to create failed computations
- Recovering from errors with `handleErrorWith` and `handleError`
- Building multi-step workflows with typed errors and layered recovery
- Writing generic, resilient code that works with any error-capable monad
~~~

~~~admonish example title="Hands-On Practice"
[Tutorial05_MonadErrorHandling.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial05_MonadErrorHandling.java)
~~~

## The Problem: Scattered Try-Catch Blocks

Consider a configuration loading workflow: parse a file, validate settings, then connect to a database. Each step can fail with a meaningful error, and you want to handle each failure differently. With imperative Java, you end up with scattered, nested try-catch blocks:

```java
// Imperative error handling: scattered and hard to compose
try {
    Config config = parseConfigFile(path);
    try {
        Settings settings = validateSettings(config);
        try {
            Connection conn = connectToDatabase(settings);
            return conn;
        } catch (DbException e) {
            return connectToFallbackDb(settings); // recovery
        }
    } catch (ValidationException e) {
        log.error("Bad config: " + e.getMessage());
        throw e; // no recovery possible
    }
} catch (ParseException e) {
    return loadDefaultConfig(); // fallback to defaults
}
```

The error handling logic is tangled with the business logic. Recovery strategies are buried inside catch blocks. And composing or reusing this code is painful.

---

## The Solution: `raiseError` and `handleErrorWith`

**`MonadError`** extends `Monad` with two fundamental operations that formalise the "try-catch" pattern in a purely functional way:

1. **`raiseError(E error)`** -- Constructs a failed computation by lifting an error into the monadic context.
2. **`handleErrorWith(fa, handler)`** -- Inspects a potential failure and provides a fallback computation.

```
  raiseError("config not found")
       |
       v
  Kind<F, A> = Left("config not found")
       |
       +-- handleErrorWith --> recovery function --> Kind<F, A> = Right(defaults)
       |
       +-- (if no handler) --> propagates as Left("config not found")
```

Here is the same configuration workflow, rewritten with `MonadError` using `Either<String, A>`:

```java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherMonad;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

MonadError<Either.Witness<String>, String> me = EitherMonad.instance();

// Each step returns Either<String, T>, using raiseError for failures
public Kind<Either.Witness<String>, Config> parseConfig(String path) {
    if (!Files.exists(Path.of(path))) {
        return me.raiseError("Config file not found: " + path);
    }
    return me.of(Config.parse(path));
}

public Kind<Either.Witness<String>, Settings> validate(Config config) {
    if (config.dbHost().isBlank()) {
        return me.raiseError("Missing required setting: db.host");
    }
    return me.of(Settings.from(config));
}

public Kind<Either.Witness<String>, Connection> connect(Settings settings) {
    if (!settings.isReachable()) {
        return me.raiseError("Cannot reach database: " + settings.dbHost());
    }
    return me.of(Connection.open(settings));
}

// Compose the workflow with flatMap, then layer recovery on top
Kind<Either.Witness<String>, Connection> workflow =
    me.flatMap(config ->
        me.flatMap(settings ->
            connect(settings),
            validate(config)),
        parseConfig("/etc/app.conf"));

// Recover from connection failures by trying a fallback database
Kind<Either.Witness<String>, Connection> resilient = me.handleErrorWith(
    workflow,
    error -> {
        if (error.startsWith("Cannot reach database")) {
            return connect(Settings.fallback());
        }
        return me.raiseError(error); // re-raise other errors
    }
);
```

The business logic reads top-to-bottom. Recovery strategies are layered on separately, and the whole thing composes cleanly.

---

~~~admonish note title="Interface Signature"
```java
@NullMarked
public interface MonadError<F extends WitnessArity<TypeArity.Unary>, E> extends Monad<F> {

  <A> @NonNull Kind<F, A> raiseError(@Nullable final E error);

  <A> @NonNull Kind<F, A> handleErrorWith(
      final Kind<F, A> ma,
      final Function<? super E, ? extends Kind<F, A>> handler);

  // Value-level recovery (unwraps the error into a plain value)
  default <A> @NonNull Kind<F, A> handleError(
      final Kind<F, A> ma,
      final Function<? super E, ? extends A> handler) {
    return handleErrorWith(ma, error -> of(handler.apply(error)));
  }
}
```
~~~

---

## Recovery Patterns

### Value-Level Recovery with `handleError`

**The problem:** An operation might fail, but you have a sensible default value.

**The solution:** `handleError` unwraps the error and maps it to a plain value, automatically lifting the result back into the monadic context.

```java
MonadError<Either.Witness<String>, String> me = EitherMonad.instance();

Kind<Either.Witness<String>, Integer> safeDivide(int a, int b) {
    return b == 0
        ? me.raiseError("Cannot divide by zero")
        : me.of(a / b);
}

// Value-level recovery: error -> default value
Kind<Either.Witness<String>, Integer> result = me.handleError(
    safeDivide(10, 0),
    error -> 0  // plain value, not wrapped in Either
);
// Result: Right(0)
```

### Effect-Level Recovery with `handleErrorWith`

**The problem:** Recovery itself might fail (e.g., trying a fallback service that could also be unavailable).

**The solution:** `handleErrorWith` lets the recovery function return a new monadic value, which can be either a success or another failure.

```java
// Effect-level recovery: error -> new monadic computation
Kind<Either.Witness<String>, Integer> result = me.handleErrorWith(
    safeDivide(10, 0),
    error -> {
        log.warn("Division failed: " + error + ", trying alternative");
        return safeDivide(10, 2); // fallback computation that could also fail
    }
);
// Result: Right(5)
```

### Chained Recovery

**The problem:** You have multiple fallback strategies, each of which might fail.

**The solution:** Chain `handleErrorWith` calls to try each fallback in order.

```java
Kind<Either.Witness<String>, Config> config =
    me.handleErrorWith(
        me.handleErrorWith(
            loadConfigFromFile(),
            err1 -> loadConfigFromEnv()),
        err2 -> me.of(Config.defaults())
    );
// Tries file first, then environment, then defaults
```

---

~~~admonish info title="Key Takeaways"
* **`raiseError`** creates a failed computation declaratively, without throwing exceptions
* **`handleErrorWith`** provides effect-level recovery where the fallback can itself fail
* **`handleError`** provides value-level recovery with a plain default value
* **Recovery composes** by chaining handlers; each layer catches what the previous one missed
* **Generic code** written against `MonadError<F, E>` works with `Either`, `Try`, or any error-capable monad
~~~

~~~admonish tip title="See Also"
- [Monad](monad.md) - The base type class that MonadError extends
- [Either](../monads/either_monad.md) - The most common MonadError instance for typed errors
- [Try](../monads/try_monad.md) - MonadError specialised for `Throwable` errors
~~~

~~~admonish tip title="Further Reading"
- **Baeldung**: [Functional Error Handling in Java](https://www.baeldung.com/java-functional-programming) - Practical guide to functional patterns in Java
- **Mark Seemann**: [An Either Functor](https://blog.ploeh.dk/2019/01/07/either-bifunctor/) - Step-by-step introduction to Either as a functional error-handling tool
~~~

~~~admonish info title="Hands-On Learning"
Practice error handling in [Tutorial 05: Monad Error Handling](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial05_MonadErrorHandling.java) (7 exercises, ~10 minutes).
~~~

---

**Previous:** [Monad](monad.md)
**Next:** [Semigroup and Monoid](semigroup_and_monoid.md)
