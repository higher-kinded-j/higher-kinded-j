# Monad: Sequencing Computations

~~~admonish info title="What You'll Learn"
- How to sequence computations where each step depends on previous results
- The power of `flatMap` for chaining operations that return wrapped values
- Essential utility methods: `as`, `peek`, `flatMapIfOrElse`, and `flatMapN`
- How to combine multiple monadic values with `flatMap2`, `flatMap3`, etc.
- How monadic short-circuiting works in practice
~~~

~~~admonish example title="Hands-On Practice"
[Tutorial04_MonadChaining.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial04_MonadChaining.java)
~~~

## The Problem: When Each Step Depends on the Last

You've seen how `Functor` lets you `map` over a value in a context and how `Applicative` lets you combine independent computations. But what happens when the result of one step determines what happens next?

Consider fetching a user, then their account, then their balance. Each step depends on the previous result, and any step could fail. Without `flatMap`, you end up with deeply nested checks:

```java
Optional<User> user = findUser(1);
if (user.isPresent()) {
    Optional<Account> account = findAccount(user.get());
    if (account.isPresent()) {
        Optional<Double> balance = getBalance(account.get());
        if (balance.isPresent()) {
            System.out.println("Balance: " + balance.get());
        }
    }
}
```

Three levels of nesting for three steps, and it only gets worse as the workflow grows.

---

## The Solution: `flatMap`

A **`Monad`** builds on `Applicative` by adding one crucial ability: **`flatMap`**. Whilst `map` takes a simple function `A -> B`, `flatMap` takes a function that returns a new value *already wrapped in the monadic context*, i.e., `A -> Kind<F, B>`. It then flattens the nested result into a simple `Kind<F, B>`.

Here is the same workflow, rewritten with `flatMap`:

```java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.optional.OptionalMonad;
import java.util.Optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

record User(int id, String name) {}
record Account(int userId, String accountId) {}

public Kind<Optional.Witness, User> findUser(int id) { /* ... */ }
public Kind<Optional.Witness, Account> findAccount(User user) { /* ... */ }
public Kind<Optional.Witness, Double> getBalance(Account account) { /* ... */ }

Monad<Optional.Witness> monad = OptionalMonad.INSTANCE;

// --- Successful workflow ---
Kind<Optional.Witness, Double> balance = monad.flatMap(user ->
    monad.flatMap(account ->
        getBalance(account),
        findAccount(user)),
    findUser(1));

// Result: Optional[1000.0]
System.out.println(OPTIONAL.narrow(balance));

// --- Failing workflow (user not found) ---
Kind<Optional.Witness, Double> missing = monad.flatMap(user ->
    monad.flatMap(account -> getBalance(account), findAccount(user)),
    findUser(2)); // Returns Optional.empty()

// The chain short-circuits immediately.
// Result: Optional.empty
System.out.println(OPTIONAL.narrow(missing));
```

No null checks, no nesting. The chain elegantly handles the "happy path" whilst short-circuiting on failure.

```
  flatMap chain; each step depends on the previous:

  findUser(1) ──> User ──> findAccount(user) ──> Account ──> getBalance(account)
       |                         |                                  |
       v                         v                                  v
  Optional<User>          Optional<Account>                  Optional<Double>

  If ANY step returns empty, the chain short-circuits:

  findUser(2) ──> empty ──X (chain stops)
       |
       v
  Optional.empty
```

This pattern is the foundation for the **for-comprehension** builder in Higher-Kinded-J, which transforms a chain of `flatMap` calls into clean, imperative-style code.

---

~~~admonish note title="Interface Signature"
```java
@NullMarked
public interface Monad<M extends WitnessArity<TypeArity.Unary>> extends Applicative<M> {
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

  // Combine multiple monadic values (flatMapN methods)
  default <A, B, R> @NonNull Kind<M, R> flatMap2(
      Kind<M, A> ma, Kind<M, B> mb,
      BiFunction<? super A, ? super B, ? extends Kind<M, R>> f) {
    return flatMap(a -> flatMap(b -> f.apply(a, b), mb), ma);
  }

  // flatMap3, flatMap4, and flatMap5 build similarly...
}
```
~~~

---

## When Should You Use Monad vs Applicative?

The short answer: use `Monad` when the next step **depends on** the previous result; use `Applicative` when computations are **independent**.

For a detailed comparison with worked examples and a decision flowchart, see [Choosing Your Abstraction Level](abstraction_levels.md).

---

## Utility Methods

`Monad` provides default methods for common tasks like debugging, conditional logic, and transforming results.

### `flatMapIfOrElse`

**The problem:** You need conditional branching in a monadic chain, but using `if/else` inside a `flatMap` lambda is error-prone and hard to read.

**The solution:** `flatMapIfOrElse` applies one of two functions based on a predicate, ensuring both paths result in the same type.

```java
Monad<Optional.Witness> monad = OptionalMonad.INSTANCE;

Kind<Optional.Witness, User> standardUser = OPTIONAL.widen(Optional.of(new User(1, "Alice")));
Kind<Optional.Witness, User> premiumUser = OPTIONAL.widen(Optional.of(new User(101, "Bob")));

// Only fetch accounts for standard users (ID < 100)
Kind<Optional.Witness, Account> result = monad.flatMapIfOrElse(
    user -> user.id() < 100,
    user -> findAccount(user),
    user -> OPTIONAL.widen(Optional.empty()),
    standardUser
);
// Result: Optional[Account[userId=1, accountId=acc-123]]

Kind<Optional.Witness, Account> empty = monad.flatMapIfOrElse(
    user -> user.id() < 100,
    user -> findAccount(user),
    user -> OPTIONAL.widen(Optional.empty()),
    premiumUser
);
// Result: Optional.empty
```

### `as`

**The problem:** After a monadic operation, you care *that* it succeeded, not *what* it returned.

**The solution:** `as` replaces the value inside a monad whilst preserving its effect (success or failure).

```java
Kind<Optional.Witness, String> message = monad.as("User found successfully", findUser(1));
// Result: Optional["User found successfully"]

Kind<Optional.Witness, String> missing = monad.as("User found successfully", findUser(99));
// Result: Optional.empty (effect preserved, value irrelevant)
```

### `peek`

**The problem:** You need to log or inspect a value mid-chain without altering the flow.

**The solution:** `peek` performs a side-effect on the value and returns the original monadic value unchanged.

```java
Kind<Optional.Witness, User> logged = monad.peek(
    user -> System.out.println("LOG: Found user -> " + user.name()),
    findUser(1)
);
// Console: LOG: Found user -> Alice
// Result: Optional[User[id=1, name=Alice]] (unchanged)

Kind<Optional.Witness, User> notFound = monad.peek(
    user -> System.out.println("LOG: Found user -> " + user.name()),
    findUser(99)
);
// Console: (nothing; the action is never executed)
// Result: Optional.empty
```

---

## Combining Multiple Monadic Values: `flatMapN`

Just as `Applicative` provides `map2`, `map3`, etc. for combining independent computations with a pure function, `Monad` provides `flatMap2`, `flatMap3`, `flatMap4`, and `flatMap5` for combining multiple monadic values where the combining function itself returns a monadic value.

### `flatMap2`

**The problem:** You need to fetch data from two sources and then perform an effectful validation that might fail.

**The solution:**

```java
record User(int id, String name) {}
record Order(int userId, String item) {}
record UserOrder(User user, Order order) {}

public Kind<Optional.Witness, User> findUser(int id) { /* ... */ }
public Kind<Optional.Witness, Order> findOrder(int orderId) { /* ... */ }

public Kind<Optional.Witness, UserOrder> validateAndCombine(User user, Order order) {
    if (order.userId() != user.id()) {
        return OPTIONAL.widen(Optional.empty());
    }
    return OPTIONAL.widen(Optional.of(new UserOrder(user, order)));
}

Monad<Optional.Witness> monad = OptionalMonad.INSTANCE;

Kind<Optional.Witness, UserOrder> result = monad.flatMap2(
    findUser(1),
    findOrder(100),
    (user, order) -> validateAndCombine(user, order)
);
// Result: Optional[UserOrder[...]] if valid, Optional.empty if any step fails
```

### `flatMap3` and Higher Arities

For more complex scenarios, you can combine three, four, or five monadic values:

```java
record Product(int id, String name, double price) {}
record Inventory(int productId, int quantity) {}

public Kind<Optional.Witness, Product> findProduct(int id) { /* ... */ }
public Kind<Optional.Witness, Inventory> checkInventory(int productId) { /* ... */ }

Kind<Optional.Witness, String> orderResult = monad.flatMap3(
    findUser(1),
    findProduct(100),
    checkInventory(100),
    (user, product, inventory) -> {
        if (inventory.quantity() <= 0) {
            return OPTIONAL.widen(Optional.empty());
        }
        String confirmation = String.format(
            "Order confirmed for %s: %s (qty: %d)",
            user.name(), product.name(), inventory.quantity()
        );
        return OPTIONAL.widen(Optional.of(confirmation));
    }
);
```

### `flatMapN` vs `mapN`

| Method | From | Combining Function Returns | Use When |
|--------|------|----------------------------|----------|
| `mapN` | Applicative | A pure value: `(A, B) -> C` | Combination is guaranteed to succeed |
| `flatMapN` | Monad | A monadic value: `(A, B) -> Kind<M, C>` | Combination itself may fail or produce effects |

```java
// mapN: Pure combination (cannot fail)
Kind<Optional.Witness, String> pure = monad.map2(
    findUser(1),
    findOrder(100),
    (user, order) -> user.name() + " ordered " + order.item()
);

// flatMapN: Effectful combination (may fail)
Kind<Optional.Witness, String> effectful = monad.flatMap2(
    findUser(1),
    findOrder(100),
    (user, order) -> validateAndProcess(user, order)
);
```

---

~~~admonish info title="Key Takeaways"
* **`flatMap` is the core operation** that enables sequencing dependent computations
* **Short-circuiting** happens automatically; if any step fails, the chain stops
* **Utility methods** (`as`, `peek`, `flatMapIfOrElse`) cover common patterns without manual lambda wrangling
* **`flatMapN` methods** combine multiple monadic values when the combining function itself produces effects
* **Choose the least powerful abstraction** that fits your problem; see [Choosing Your Abstraction Level](abstraction_levels.md) for guidance
~~~

~~~admonish tip title="See Also"
- [Applicative](applicative.md) - For combining independent computations
- [For Comprehension](for_comprehension.md) - Readable syntax for `flatMap` chains
- [Choosing Your Abstraction Level](abstraction_levels.md) - When to use Applicative vs Selective vs Monad
~~~

~~~admonish tip title="Further Reading"
- **Scott Logic**: [Functors and Monads with Java and Scala](https://blog.scottlogic.com/2025/03/31/functors-monads-with-java-and-scala.html) - Practical guide to functors and monads in Java
- **Bartosz Milewski**: [Monads: Programmer's Definition](https://bartoszmilewski.com/2016/11/21/monads-programmers-definition/) - Practical explanation of monads for programmers
- **Mark Seemann**: [Monads for the Rest of Us](https://blog.ploeh.dk/2022/03/28/monads/) - Step-by-step monad explanation with Java-style examples
~~~

~~~admonish info title="Hands-On Learning"
Practice Monad chaining in [Tutorial 04: Monad Chaining](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial04_MonadChaining.java) (7 exercises, ~10 minutes).
~~~

---

**Previous:** [Alternative](alternative.md)
**Next:** [MonadError](monad_error.md)
