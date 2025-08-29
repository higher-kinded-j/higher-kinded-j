# Monad: Sequencing Computations 🔗

You've seen how `Functor` lets you `map` over a value in a context and how `Applicative` lets you combine independent computations within a context. Now, we'll introduce the most powerful of the trio: **`Monad`**.

A `Monad` builds on `Applicative` by adding one crucial ability: sequencing computations that **depend on each other**. If the result of the first operation is needed to determine the second operation, you need a `Monad`.

---

## What is it?

A **`Monad`** is an `Applicative` that provides a new function called **`flatMap`** (also known as `bind` in some languages). This is the powerhouse of monadic composition.

While `map` takes a simple function `A -> B`, `flatMap` takes a function that returns a new value *already wrapped in the monadic context*, i.e., `A -> Kind<F, B>`. `flatMap` then intelligently flattens the nested result `Kind<F, Kind<F, B>>` into a simple `Kind<F, B>`.

This flattening behaviour is what allows you to chain operations together in a clean, readable sequence without creating deeply nested structures.

The interface for `Monad` in `hkj-api` extends `Applicative`:

``` java
@NullMarked
public interface Monad<M> extends Applicative<M> {
  <A, B> @NonNull Kind<M, B> flatMap(
      final Function<? super A, ? extends Kind<M, B>> f, final Kind<M, A> ma);
}
```

---

### Monad vs. Applicative

The key difference is simple but profound:

* **`Applicative`** is for combining **independent** computations. The shape and structure of all the computations are known upfront. This is why it can accumulate errors from multiple validations—it runs all of them.
* **`Monad`** is for sequencing **dependent** computations. The computation in the second step cannot be known until the first step has completed. This is why it short-circuits on failure—if the first step fails, there is no value to feed into the second step.

---

### Why is it useful?

`Monad` is essential for building any kind of workflow where steps depend on the result of previous steps, especially when those steps might fail or be asynchronous. It allows you to write what looks like a simple sequence of operations while hiding the complexity of error handling, null checks, or concurrency.

This pattern is the foundation for the **for-comprehension** builder in `higher-kinded-j`, which transforms a chain of `flatMap` calls into clean, imperative-style code.

**Example: A Safe Database Workflow**

Imagine a workflow where you need to fetch a user, then use their ID to fetch their account, and finally use the account details to get their balance. Any of these steps could fail (e.g., return `null` or an empty `Optional`).

Without `Monad`, you'd end up with a nested pyramid of `if`-`else` or `Optional.ifPresent` calls. With `Monad`, it becomes a clean, readable chain.


``` java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import java.util.Optional;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

// Mock data records
record User(int id, String name) {}
record Account(int userId, String accountId) {}

// Mock repository functions that can fail
public Kind<Optional.Witness, User> findUser(int id) {
    return id == 1 ? OPTIONAL.widen(Optional.of(new User(1, "Alice"))) : OPTIONAL.widen(Optional.empty());
}

public Kind<Optional.Witness, Account> findAccount(User user) {
    return user.id == 1 ? OPTIONAL.widen(Optional.of(new Account(1, "acc-123"))) : OPTIONAL.widen(Optional.empty());
}

public Kind<Optional.Witness, Double> getBalance(Account account) {
    return account.accountId.equals("acc-123") ? OPTIONAL.widen(Optional.of(1000.0)) : OPTIONAL.widen(Optional.empty());
}


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
    monad.flatMap(account ->
        getBalance(account),
        findAccount(user)),
    findUser(2)); // This step will return Optional.empty()

// The chain short-circuits immediately. The other functions are never called.
// Result: Optional.empty
System.out.println(OPTIONAL.narrow(balanceFailure));
```

The `flatMap` chain elegantly handles the "happy path" while also providing robust, short-circuiting logic for the failure cases, all without a single null check. This clean, declarative style is the primary benefit of using Monads.
