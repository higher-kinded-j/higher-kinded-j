# Applicative: Applying Wrapped Functions ✨

~~~admonish info title="What You'll Learn"
- How to apply wrapped functions to wrapped values using `ap`
- The difference between independent computations (Applicative) and dependent ones (Monad)
- How to combine multiple validation results and accumulate all errors
- Using `map2`, `map3` and other convenience methods for combining values
- Real-world validation scenarios with the Validated type
~~~

While a `Functor` is great for applying a *simple* function to a value inside a context, what happens when the function you want to apply is *also* wrapped in a context? This is where the **`Applicative`** type class comes in. It's the next step up in power from a `Functor` and allows you to combine multiple computations within a context in a very powerful way.

---

## What is it?

An **`Applicative`** (or Applicative Functor) is a `Functor` that also provides two key operations:

1. **`of`** (also known as `pure`): Lifts a regular value into the applicative context. For example, it can take a `String` and wrap it to become an `Optional<String>`.
2. **`ap`**: Takes a function that is wrapped in the context (e.g., an `Optional<Function<A, B>>`) and applies it to a value that is also in the context (e.g., an `Optional<A>`).

This ability to apply a *wrapped function* to a *wrapped value* is what makes `Applicative` so powerful. It's the foundation for combining independent computations.

The interface for `Applicative` in `hkj-api` extends `Functor`:


``` java
@NullMarked
public interface Applicative<F> extends Functor<F> {

  <A> @NonNull Kind<F, A> of(@Nullable A value);

  <A, B> @NonNull Kind<F, B> ap(
      Kind<F, ? extends Function<A, B>> ff,
      Kind<F, A> fa
  );

  // Default methods for map2, map3, etc. are also provided
  default <A, B, C> @NonNull Kind<F, C> map2(
      final Kind<F, A> fa,
      final Kind<F, B> fb,
      final BiFunction<? super A, ? super B, ? extends C> f) {
    return ap(map(a -> b -> f.apply(a, b), fa), fb);
  }
}
```

---

### Why is it useful?

The primary use case for `Applicative` is to combine the results of several independent computations that are all inside the same context. The classic example is **data validation**, where you want to validate multiple fields and accumulate all the errors.

While a `Monad` (using `flatMap`) can also combine computations, it can't accumulate errors in the same way. When a monadic chain fails, it short-circuits, giving you only the *first* error. An `Applicative`, on the other hand, can process all computations independently and combine the results.

**Example: Validating a User Registration Form**

Imagine you have a registration form and you need to validate both the username and the password. Each validation can either succeed or return a list of error messages. We can use the `Applicative` for `Validated` to run both validations and get all the errors back at once.

``` java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.hkt.Semigroups;

import java.util.List;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

// A simple User class
record User(String username, String password) {}

// Validation functions
public Validated<List<String>, String> validateUsername(String username) {
    if (username.length() < 3) {
        return Validated.invalid(List.of("Username must be at least 3 characters"));
    }
    return Validated.valid(username);
}

public Validated<List<String>, String> validatePassword(String password) {
    if (!password.matches(".*\\d.*")) {
        return Validated.invalid(List.of("Password must contain a number"));
    }
    return Validated.valid(password);
}


// --- Get the Applicative instance for Validated ---
// We need a Semigroup to tell the Applicative how to combine errors (in this case, by concatenating lists)
Applicative<Validated.Witness<List<String>>> applicative =
    ValidatedMonad.instance(Semigroups.list());

// --- Scenario 1: All validations pass ---
Validated<List<String>, String> validUsername = validateUsername("test_user");
Validated<List<String>, String> validPassword = validatePassword("password123");

Kind<Validated.Witness<List<String>>, User> validResult =
    applicative.map2(
        VALIDATED.widen(validUsername),
        VALIDATED.widen(validPassword),
        User::new // If both are valid, create a new User
    );

// Result: Valid(User[username=test_user, password=password123])
System.out.println(VALIDATED.narrow(validResult));


// --- Scenario 2: Both validations fail ---
Validated<List<String>, String> invalidUsername = validateUsername("no");
Validated<List<String>, String> invalidPassword = validatePassword("bad");

Kind<Validated.Witness<List<String>>, User> invalidResult =
    applicative.map2(
        VALIDATED.widen(invalidUsername),
        VALIDATED.widen(invalidPassword),
        User::new
    );

// The errors from both validations are accumulated!
// Result: Invalid([Username must be at least 3 characters, Password must contain a number])
System.out.println(VALIDATED.narrow(invalidResult));
```

This error accumulation is impossible with `Functor` and is one of the key features that makes `Applicative` so indispensable for real-world functional programming.
