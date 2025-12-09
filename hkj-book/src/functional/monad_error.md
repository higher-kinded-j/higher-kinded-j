# MonadError: Handling Errors Gracefully

~~~admonish info title="What You'll Learn"
- How MonadError extends Monad with explicit error handling capabilities
- Using `raiseError` to create failed computations
- Recovering from errors with `handleErrorWith` and `handleError`
- Writing generic, resilient code that works with any error-capable monad
- Practical examples with Either and Try
~~~

While a `Monad` is excellent for sequencing operations that might fail (like with `Optional` or `Either`), it doesn't provide a standardised way to *inspect* or *recover* from those failures. The **`MonadError`** type class fills this gap.

It's a specialised `Monad` that has a defined error type `E`, giving you a powerful and abstract API for raising and handling errors within any monadic workflow.

---

## What is it?

A **`MonadError`** is a `Monad` that provides two additional, fundamental operations for working with failures:

1. **`raiseError(E error)`**: This allows you to construct a failed computation by lifting an error value `E` directly into the monadic context.
2. **`handleErrorWith(Kind<F, A> fa, ...)`**: This is the recovery mechanism. It allows you to inspect a potential failure and provide a fallback computation to rescue the workflow.

By abstracting over a specific error type `E`, `MonadError` allows you to write generic, resilient code that can work with any data structure capable of representing failure, such as `Either<E, A>`, `Try<A>` (where `E` is `Throwable`), or even custom error-handling monads.

The interface for `MonadError` in `hkj-api` extends `Monad`:


``` java
@NullMarked
public interface MonadError<F, E> extends Monad<F> {

  <A> @NonNull Kind<F, A> raiseError(@Nullable final E error);

  <A> @NonNull Kind<F, A> handleErrorWith(
      final Kind<F, A> ma,
      final Function<? super E, ? extends Kind<F, A>> handler);

  // Default recovery methods like handleError, recover, etc. are also provided
  default <A> @NonNull Kind<F, A> handleError(
      final Kind<F, A> ma,
      final Function<? super E, ? extends A> handler) {
    return handleErrorWith(ma, error -> of(handler.apply(error)));
  }
}
```

---

### Why is it useful?

`MonadError` formalises the pattern of "try-catch" in a purely functional way. It lets you build complex workflows that need to handle specific types of errors without coupling your logic to a concrete implementation like `Either` or `Try`. You can write a function once, and it will work seamlessly with any data type that has a `MonadError` instance.

This is incredibly useful for building robust applications, separating business logic from error-handling logic, and providing sensible fallbacks when operations fail.

**Example: A Resilient Division Workflow**

Let's model a division operation that can fail with a specific error message. We'll use `Either<String, A>` as our data type, which is a perfect fit for `MonadError`.


``` java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherMonad;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

// --- Get the MonadError instance for Either<String, ?> ---
MonadError<Either.Witness<String>, String> monadError = EitherMonad.instance();

// A function that performs division, raising a specific error on failure
public Kind<Either.Witness<String>, Integer> safeDivide(int a, int b) {
    if (b == 0) {
        return monadError.raiseError("Cannot divide by zero!");
    }
    return monadError.of(a / b);
}

// --- Scenario 1: A successful division ---
Kind<Either.Witness<String>, Integer> success = safeDivide(10, 2);

// Result: Right(5)
System.out.println(EITHER.narrow(success));


// --- Scenario 2: A failed division ---
Kind<Either.Witness<String>, Integer> failure = safeDivide(10, 0);

// Result: Left(Cannot divide by zero!)
System.out.println(EITHER.narrow(failure));


// --- Scenario 3: Recovering from the failure ---
// We can use handleErrorWith to catch the error and return a fallback value.
Kind<Either.Witness<String>, Integer> recovered = monadError.handleErrorWith(
    failure,
    errorMessage -> {
        System.out.println("Caught an error: " + errorMessage);
        return monadError.of(0); // Recover with a default value of 0
    }
);

// Result: Right(0)
System.out.println(EITHER.narrow(recovered));
```

In this example, `raiseError` allows us to create the failure case in a clean, declarative way, while `handleErrorWith` provides a powerful mechanism for recovery, making our code more resilient and predictable.

---

~~~admonish tip title="Further Reading"
- **Cats Documentation**: [ApplicativeError](https://typelevel.org/cats/typeclasses/applicativeerror.html) - The foundation that MonadError builds upon
~~~

---

**Previous:** [Monad](monad.md)
**Next:** [Semigroup and Monoid](semigroup_and_monoid.md)
