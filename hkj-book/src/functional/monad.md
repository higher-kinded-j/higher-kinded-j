# Monad: Sequencing Computations 🔗

You've seen how `Functor` lets you `map` over a value in a context and how `Applicative` lets you combine independent computations within a context. Now, we'll introduce the most powerful of the trio: **`Monad`**.

A `Monad` builds on `Applicative` by adding one crucial ability: sequencing computations that **depend on each other**. If the result of the first operation is needed to determine the second operation, you need a `Monad`.

---

## What is it?

A **`Monad`** is an `Applicative` that provides a new function called **`flatMap`** (also known as `bind` in some languages). This is the powerhouse of monadic composition.

While `map` takes a simple function `A -> B`, `flatMap` takes a function that returns a new value *already wrapped in the monadic context*, i.e., `A -> Kind<F, B>`. `flatMap` then intelligently flattens the nested result `Kind<F, Kind<F, B>>` into a simple `Kind<F, B>`.

This flattening behaviour is what allows you to chain operations together in a clean, readable sequence without creating deeply nested structures.

---

## The `Monad` Interface

The interface for `Monad` in `hkj-api` extends `Applicative` and adds `flatMap` along with several useful default methods for common patterns.

```java
@NullMarked
public interface Monad<M> extends Applicative<M> {
  // Core sequencing method
  <A, B> @NonNull Kind<M, B> flatMap(
      final Function<? super A, ? extends Kind<M, B>> f, final Kind<M, A> ma);

  // Type-safe conditional branching
  default <A, B> @NonNull Kind<M, B> flatMapIfOrElse(
      final Predicate<? super A> predicate,
      final Function<? super A, ? extends Kind<M, B>> ifTrue,
      final Function<? super A, ? extends Kind<M, B>> ifFalse,
      final Kind<M, A> ma) {
    return flatMap(a -> predicate.test(a) ? ifTrue.apply(a) : ifFalse.apply(a), ma);
  }

  // Replace the value while preserving the effect
  default <A, B> @NonNull Kind<M, B> as(final B b, final Kind<M, A> ma) {
    return map(_ -> b, ma);
  }

  // Perform a side-effect without changing the value
  default <A> @NonNull Kind<M, A> peek(final Consumer<? super A> action, final Kind<M, A> ma) {
    return map(a -> {
      action.accept(a);
      return a;
    }, ma);
  }
}
```

### Monad vs. Applicative

The key difference is simple but profound:

* **`Applicative`** is for combining **independent** computations. The shape and structure of all the computations are known upfront. This is why it can accumulate errors from multiple validations—it runs all of them.
* **`Monad`** is for sequencing **dependent** computations. The computation in the second step cannot be known until the first step has completed. This is why it short-circuits on failure—if the first step fails, there is no value to feed into the second step.

---

### Why is it useful?

`Monad` is essential for building any kind of workflow where steps depend on the result of previous steps, especially when those steps might fail or be asynchronous. It allows you to write what looks like a simple sequence of operations while hiding the complexity of error handling, null checks, or concurrency.

This pattern is the foundation for the **for-comprehension** builder in `higher-kinded-j`, which transforms a chain of `flatMap` calls into clean, imperative-style code.

#### Core Method: `flatMap`

This is the primary method for chaining dependent operations.

**Example: A Safe Database Workflow**

Imagine a workflow where you need to fetch a user, then use their ID to fetch their account, and finally use the account details to get their balance. Any of these steps could fail (e.g., return an empty `Optional`). With `flatMap`, the chain becomes clean and safe.

```java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.optional.OptionalMonad;
import java.util.Optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

// Mock data records and repository functions from the previous example...
record User(int id, String name) {}
record Account(int userId, String accountId) {}

public Kind<Optional.Witness, User> findUser(int id) { /* ... */ }
public Kind<Optional.Witness, Account> findAccount(User user) { /* ... */ }
public Kind<Optional.Witness, Double> getBalance(Account account) { /* ... */ }

// --- Get the Monad instance for Optional ---
Monad<Optional.Witness> monad = OptionalMonad.INSTANCE;

// --- Scenario 1: Successful workflow ---
Kind<Optional.Witness, Double> balanceSuccess = monad.flatMap(user ->
    monad.flatMap(account ->
        getBalance(account),
        findAccount(user)),
    findUser(1));

// Result: Optional[1000.0]
System.out.println(OPTIONAL.narrow(balanceSuccess));

// --- Scenario 2: Failing workflow (user not found) ---
Kind<Optional.Witness, Double> balanceFailure = monad.flatMap(user ->
    /* this part is never executed */
    monad.flatMap(account -> getBalance(account), findAccount(user)),
    findUser(2)); // This returns Optional.empty()

// The chain short-circuits immediately.
// Result: Optional.empty
System.out.println(OPTIONAL.narrow(balanceFailure));
```

The `flatMap` chain elegantly handles the "happy path" while also providing robust, short-circuiting logic for the failure cases, all without a single null check.

---

## Utility Methods 🛠️

`Monad` also provides default methods for common tasks like debugging, conditional logic, and transforming results.

### `flatMapIfOrElse`

This is the type-safe way to perform conditional branching in a monadic chain. It applies one of two functions based on a predicate, ensuring that both paths result in the same final type and avoiding runtime errors.

Let's imagine we only want to fetch accounts for "standard" users (ID < 100).

```java
// --- Get the Monad instance for Optional ---
Monad<Optional.Witness> monad = OptionalMonad.INSTANCE;

// A user who meets the condition
Kind<Optional.Witness, User> standardUser = OPTIONAL.widen(Optional.of(new User(1, "Alice")));
// A user who does not
Kind<Optional.Witness, User> premiumUser = OPTIONAL.widen(Optional.of(new User(101, "Bob")));

// --- Scenario 1: Predicate is true ---
Kind<Optional.Witness, Account> resultSuccess = monad.flatMapIfOrElse(
    user -> user.id() < 100,      // Predicate: user is standard
    user -> findAccount(user),    // Action if true: find their account
    user -> OPTIONAL.widen(Optional.empty()), // Action if false: return empty
    standardUser
);
// Result: Optional[Account[userId=1, accountId=acc-123]]
System.out.println(OPTIONAL.narrow(resultSuccess));


// --- Scenario 2: Predicate is false ---
Kind<Optional.Witness, Account> resultFailure = monad.flatMapIfOrElse(
    user -> user.id() < 100,
    user -> findAccount(user),
    user -> OPTIONAL.widen(Optional.empty()), // This path is taken
    premiumUser
);
// Result: Optional.empty
System.out.println(OPTIONAL.narrow(resultFailure));
```

### `as`

Replaces the value inside a monad while preserving its effect (e.g., success or failure). This is useful when you only care *that* an operation succeeded, not what its result was.

```java
// After finding a user, we just want a confirmation message.
Kind<Optional.Witness, String> successMessage = monad.as("User found successfully", findUser(1));

// Result: Optional["User found successfully"]
System.out.println(OPTIONAL.narrow(successMessage));

// If the user isn't found, the effect (empty Optional) is preserved.
Kind<Optional.Witness, String> failureMessage = monad.as("User found successfully", findUser(99));

// Result: Optional.empty
System.out.println(OPTIONAL.narrow(failureMessage));
```

### `peek`

Allows you to perform a side-effect (like logging) on the value inside a monad without altering the flow. The original monadic value is always returned.

```java
// Log the user's name if they are found
Kind<Optional.Witness, User> peekSuccess = monad.peek(
    user -> System.out.println("LOG: Found user -> " + user.name()),
    findUser(1)
);
// Console output: LOG: Found user -> Alice
// Result: Optional[User[id=1, name=Alice]] (The original value is unchanged)
System.out.println("Return value: " + OPTIONAL.narrow(peekSuccess));


// If the user isn't found, the action is never executed.
Kind<Optional.Witness, User> peekFailure = monad.peek(
    user -> System.out.println("LOG: Found user -> " + user.name()),
    findUser(99)
);
// Console output: (nothing)
// Result: Optional.empty
System.out.println("Return value: " + OPTIONAL.narrow(peekFailure));
```
