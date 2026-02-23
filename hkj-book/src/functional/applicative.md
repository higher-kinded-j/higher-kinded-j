# Applicative: Combining Independent Computations

~~~admonish info title="What You'll Learn"
- How to apply wrapped functions to wrapped values using `ap`
- How to combine multiple validation results and accumulate all errors
- Using `map2`, `map3` and other convenience methods for combining values
- Real-world validation scenarios with the Validated type
~~~

~~~admonish example title="Hands-On Practice"
[Tutorial03_ApplicativeCombining.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial03_ApplicativeCombining.java)
~~~

## The Problem: Combining Results That Don't Depend on Each Other

Whilst a `Functor` excels at applying a *pure* function to a value inside a context, what happens when you have **multiple independent computations** whose results you need to combine? And what if you want to see *all* the errors, not just the first one?

```
  Applicative: independent paths, combined at the end

  validateName("") ────────────┐
                               ├──> map2 ──> Validated<User>
  validateEmail("bad") ────────┘
  (both run, errors accumulate)
```

This is where the **`Applicative`** type class comes in. It's the next step up in power from a `Functor` and allows you to combine multiple computations within a context in a very powerful way.

---

## How Does It Work?

An **`Applicative`** (or Applicative Functor) is a `Functor` that provides two key operations:

1. **`of`** (also known as `pure`): Lifts a regular value into the applicative context. For example, it can take a `String` and wrap it to become an `Optional<String>`.
2. **`ap`**: Takes a function that is wrapped in the context (e.g., an `Optional<Function<A, B>>`) and applies it to a value that is also in the context (e.g., an `Optional<A>`).

This ability to apply a *wrapped function* to a *wrapped value* is what makes `Applicative` so powerful. It's the foundation for combining independent computations.

~~~admonish note title="Interface Signature"
``` java
@NullMarked
public interface Applicative<F extends WitnessArity<TypeArity.Unary>> extends Functor<F> {

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
~~~

---

## The Killer Use Case: Error Accumulation

The primary use case for `Applicative` is data validation where you want to validate multiple fields and accumulate **all** the errors. A `Monad` short-circuits on the first failure; an `Applicative` processes all computations independently and combines the results.

For more on this distinction, see [Choosing Your Abstraction Level](abstraction_levels.md).

**Example: Validating a User Registration Form**

``` java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.hkt.Semigroups;

import java.util.List;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

record User(String username, String password) {}

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


// Get the Applicative for Validated, with a Semigroup that concatenates error lists
Applicative<Validated.Witness<List<String>>> applicative =
    ValidatedMonad.instance(Semigroups.list());

// --- Scenario 1: All validations pass ---
Kind<Validated.Witness<List<String>>, User> validResult =
    applicative.map2(
        VALIDATED.widen(validateUsername("test_user")),
        VALIDATED.widen(validatePassword("password123")),
        User::new
    );
// Result: Valid(User[username=test_user, password=password123])

// --- Scenario 2: Both validations fail ---
Kind<Validated.Witness<List<String>>, User> invalidResult =
    applicative.map2(
        VALIDATED.widen(validateUsername("no")),
        VALIDATED.widen(validatePassword("bad")),
        User::new
    );
// Errors from BOTH validations are accumulated!
// Result: Invalid([Username must be at least 3 characters, Password must contain a number])
```

This error accumulation is impossible with a `Monad` (which short-circuits) and is one of the key features that makes `Applicative` so indispensable for real-world functional programming.

---

~~~admonish info title="Key Takeaways"
* **`Applicative` combines independent computations** where results don't depend on each other
* **Error accumulation** with `Validated` is the killer use case; you see all errors, not just the first
* **`map2`, `map3`, etc.** are the practical workhorses for combining two, three, or more values
* **`of` lifts a plain value** into the applicative context (e.g., wrapping a `String` into an `Optional<String>`)
~~~

~~~admonish tip title="See Also"
- [Functor](functor.md) - The simpler foundation that Applicative builds upon
- [Monad](monad.md) - For dependent computations (but with short-circuiting, not accumulation)
- [Choosing Your Abstraction Level](abstraction_levels.md) - When to use Applicative vs Selective vs Monad
- [Validated](../monads/validated_monad.md) - The type designed for error accumulation
~~~

~~~admonish tip title="Further Reading"
- **Baeldung**: [Functional Programming in Java](https://www.baeldung.com/java-functional-programming) - Practical functional patterns for Java developers
- **Mark Seemann**: [Applicative Functors](https://blog.ploeh.dk/2018/10/01/applicative-functors/) - Accessible introduction with practical examples
~~~

~~~admonish info title="Hands-On Learning"
Practice Applicative combining in [Tutorial 03: Applicative Combining](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial03_ApplicativeCombining.java) (7 exercises, ~10 minutes).
~~~

---

**Previous:** [Functor](functor.md)
**Next:** [Alternative](alternative.md)
