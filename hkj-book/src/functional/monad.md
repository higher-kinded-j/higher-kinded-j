# Monad: Sequencing Dependent Computations

~~~admonish info title="What We'll Learn"
- Why `flatMap` is the operation we reach for whenever the next step depends on the previous result
- How short-circuiting comes for free, without writing a single conditional
- The utility methods (`as`, `peek`, `flatMapIfOrElse`) that turn common idioms into one-liners
- How `flatMapN` combines several monadic values when the combiner is itself effectful
- Where `Monad` shows up inside the Foundations one-liner
~~~

~~~admonish example title="Hands-On Practice"
[Tutorial04_MonadChaining.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial04_MonadChaining.java)
~~~

## Why `flatMap` Earns Its Keep

`Functor` lets us transform a value inside a container. `Applicative` lets us combine several independent containers. Neither of those answers the question that comes up in roughly every other method we write: *what if the next step needs to look at the previous result before it can even decide what to do?*

Consider the classic three-step lookup: fetch a user, then their account, then the balance on it. Without `flatMap`, the imperative version drowns in pyramids:

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

Three steps, three checks, and the meaningful code is hiding in the bottom-right corner like a frightened mouse.

---

## The Solution: `flatMap`

A `Monad` builds on `Applicative` by adding one operation. Whereas `map` takes `A -> B`, `flatMap` takes `A -> Kind<F, B>`: a function that *itself* produces a new container, which `flatMap` then quietly flattens for us.

```java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import java.util.Optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

record User(int id, String name) {}
record Account(int userId, String accountId) {}

public Kind<OptionalKind.Witness, User> findUser(int id) { /* ... */ }
public Kind<OptionalKind.Witness, Account> findAccount(User user) { /* ... */ }
public Kind<OptionalKind.Witness, Double> getBalance(Account account) { /* ... */ }

Monad<OptionalKind.Witness> monad = Instances.monadError(optional());

// Happy path
Kind<OptionalKind.Witness, Double> balance =
    monad.flatMap(user ->
        monad.flatMap(account ->
            getBalance(account),
        findAccount(user)),
    findUser(1));

OPTIONAL.narrow(balance);   // Optional[1000.0]

// Missing user
Kind<OptionalKind.Witness, Double> missing =
    monad.flatMap(user ->
        monad.flatMap(account -> getBalance(account), findAccount(user)),
    findUser(2));

OPTIONAL.narrow(missing);   // Optional.empty
```

The chain handles the happy path inline and short-circuits the moment any step is empty. We never asked for that behaviour; it falls out of how `Optional`'s `flatMap` is defined. Nested `flatMap` calls are still a bit ugly to read, which is why [For comprehensions](for_comprehension.md) exist; the *capability* lives here.

```
  flatMap chain; each step depends on the previous:

  findUser(1) -> User -> findAccount(user) -> Account -> getBalance(account)
       |                       |                              |
       v                       v                              v
  Optional<User>        Optional<Account>             Optional<Double>

  If any step returns empty, the rail breaks and the chain stops:

  findUser(2) -> empty -X (chain halts)
       |
       v
  Optional.empty
```

---

~~~admonish note title="Interface Signature"
```java
@NullMarked
public interface Monad<M extends WitnessArity<TypeArity.Unary>> extends Applicative<M> {
  // Core sequencing
  <A, B> @NonNull Kind<M, B> flatMap(
      Function<? super A, ? extends Kind<M, B>> f, Kind<M, A> ma);

  // Type-safe conditional branching
  default <A, B> @NonNull Kind<M, B> flatMapIfOrElse(
      Predicate<? super A> predicate,
      Function<? super A, ? extends Kind<M, B>> ifTrue,
      Function<? super A, ? extends Kind<M, B>> ifFalse,
      Kind<M, A> ma) {
    return flatMap(a -> predicate.test(a) ? ifTrue.apply(a) : ifFalse.apply(a), ma);
  }

  // Replace the value while preserving the effect
  default <A, B> @NonNull Kind<M, B> as(B b, Kind<M, A> ma) {
    return map(_ -> b, ma);
  }

  // Side-effect without changing the value
  default <A> @NonNull Kind<M, A> peek(Consumer<? super A> action, Kind<M, A> ma) {
    return map(a -> { action.accept(a); return a; }, ma);
  }

  // Combine multiple monadic values; flatMap3 / flatMap4 / flatMap5 build similarly
  default <A, B, R> @NonNull Kind<M, R> flatMap2(
      Kind<M, A> ma, Kind<M, B> mb,
      BiFunction<? super A, ? super B, ? extends Kind<M, R>> f) {
    return flatMap(a -> flatMap(b -> f.apply(a, b), mb), ma);
  }
}
```
~~~

---

## When Should We Reach for `Monad` Instead of `Applicative`?

The short answer: use `Monad` when the next step *depends on* the previous result, and `Applicative` when the steps are independent. The longer answer, with a worked decision flow, lives in [Choosing Your Abstraction Level](abstraction_levels.md).

A useful rule of thumb: if we find ourselves writing `applicative.map3(a, b, c, ...)` and one of `a`, `b`, `c` is computed from the result of another, we have crossed into Monad territory whether we admit it or not.

---

## Utility Methods Worth Knowing

`Monad` provides default helpers for things we end up writing by hand otherwise. None of them are clever; they just save us from re-inventing the same lambda for the tenth time.

### `flatMapIfOrElse`

**The problem.** We need conditional branching inside a chain, and stuffing `if`/`else` into a `flatMap` lambda is the kind of thing that makes a future maintainer file a bug.

**The solution.**

```java
Monad<OptionalKind.Witness> monad = Instances.monadError(optional());

Kind<OptionalKind.Witness, User> standardUser = OPTIONAL.widen(Optional.of(new User(1, "Alice")));
Kind<OptionalKind.Witness, User> premiumUser  = OPTIONAL.widen(Optional.of(new User(101, "Bob")));

Kind<OptionalKind.Witness, Account> result = monad.flatMapIfOrElse(
    user -> user.id() < 100,
    user -> findAccount(user),
    user -> OPTIONAL.widen(Optional.empty()),
    standardUser);
// Optional[Account[userId=1, accountId=acc-123]]

Kind<OptionalKind.Witness, Account> empty = monad.flatMapIfOrElse(
    user -> user.id() < 100,
    user -> findAccount(user),
    user -> OPTIONAL.widen(Optional.empty()),
    premiumUser);
// Optional.empty
```

### `as`

**The problem.** After a monadic operation we care *that* it succeeded, not *what* it returned.

**The solution.**

```java
Kind<OptionalKind.Witness, String> message = monad.as("User found successfully", findUser(1));
// Optional["User found successfully"]

Kind<OptionalKind.Witness, String> missing = monad.as("User found successfully", findUser(99));
// Optional.empty (effect preserved, value irrelevant)
```

### `peek`

**The problem.** We want to log or inspect mid-chain, without altering the flow.

**The solution.**

```java
Kind<OptionalKind.Witness, User> logged = monad.peek(
    user -> System.out.println("LOG: Found user -> " + user.name()),
    findUser(1));
// Console: LOG: Found user -> Alice
// Result: Optional[User[id=1, name=Alice]] (unchanged)

Kind<OptionalKind.Witness, User> notFound = monad.peek(
    user -> System.out.println("LOG: Found user -> " + user.name()),
    findUser(99));
// Console: (nothing; the action never fires)
// Result: Optional.empty
```

The unspoken pleasure here is that `peek` only runs on the success rail. We do not have to remember to wrap it in an `isPresent` check; the `Monad` instance does the right thing.

---

## Combining Several Monadic Values: `flatMapN`

`Applicative` gives us `map2`, `map3`, and so on for combining independent values with a *pure* function. `Monad` adds `flatMap2` through `flatMap5` for the case where the combiner is *itself* effectful.

### `flatMap2`

**The problem.** Fetch two pieces of data from independent sources, then perform a validation that might fail.

**The solution.**

```java
record User(int id, String name) {}
record Order(int userId, String item) {}
record UserOrder(User user, Order order) {}

public Kind<OptionalKind.Witness, User>  findUser(int id) { /* ... */ }
public Kind<OptionalKind.Witness, Order> findOrder(int orderId) { /* ... */ }

public Kind<OptionalKind.Witness, UserOrder> validateAndCombine(User user, Order order) {
    if (order.userId() != user.id()) {
        return OPTIONAL.widen(Optional.empty());
    }
    return OPTIONAL.widen(Optional.of(new UserOrder(user, order)));
}

Monad<OptionalKind.Witness> monad = Instances.monadError(optional());

Kind<OptionalKind.Witness, UserOrder> result = monad.flatMap2(
    findUser(1),
    findOrder(100),
    (user, order) -> validateAndCombine(user, order));
// Optional[UserOrder[...]] if valid, Optional.empty if any step fails
```

### `flatMap3` and Higher Arities

For richer scenarios, three, four, or five inputs are equally well behaved.

```java
record Product(int id, String name, double price) {}
record Inventory(int productId, int quantity) {}

public Kind<OptionalKind.Witness, Product>   findProduct(int id) { /* ... */ }
public Kind<OptionalKind.Witness, Inventory> checkInventory(int productId) { /* ... */ }

Kind<OptionalKind.Witness, String> orderResult = monad.flatMap3(
    findUser(1),
    findProduct(100),
    checkInventory(100),
    (user, product, inventory) -> {
        if (inventory.quantity() <= 0) {
            return OPTIONAL.widen(Optional.empty());
        }
        String confirmation = String.format(
            "Order confirmed for %s: %s (qty: %d)",
            user.name(), product.name(), inventory.quantity());
        return OPTIONAL.widen(Optional.of(confirmation));
    });
```

### `flatMapN` vs `mapN`

| Method | From | Combiner returns | Use when |
|--------|------|------------------|----------|
| `mapN` | Applicative | Pure value `(A, B) -> C` | Combination is guaranteed to succeed |
| `flatMapN` | Monad | Monadic value `(A, B) -> Kind<M, C>` | Combination itself may fail or produce effects |

```java
// Pure combination, cannot fail
Kind<OptionalKind.Witness, String> pure = monad.map2(
    findUser(1),
    findOrder(100),
    (user, order) -> user.name() + " ordered " + order.item());

// Effectful combination, may fail
Kind<OptionalKind.Witness, String> effectful = monad.flatMap2(
    findUser(1),
    findOrder(100),
    (user, order) -> validateAndProcess(user, order));
```

---

## Back to the One-Liner

The line we keep coming back to is:

```java
repo.find(id)
    .toEitherPath()
    .focus().attributes().at(key)
    .modify(spec::validateAndCoerce)
    .flatMap(repo::save);   // <-- Monad at work
```

The closing `.flatMap(repo::save)` is `EitherMonad.flatMap`, dispatched at compile time the moment we asked for an `EitherPath`. `repo::save` returns *another* `EitherPath`, and `flatMap` flattens it into the outer chain. If validation in the previous step left us on the `Left` rail, `save` is never called; the same `Left` is propagated through unchanged. That short-circuit is not a feature we asked for; it is what `Monad` *is*.

The whole expression is a `Kind<EitherPathKind.Witness<Error>, Node>` flowing from one type-class method to the next. `Monad` is the thing that lets us spell that flow as a sequence rather than a pyramid.

---

## Things People Get Wrong

~~~admonish warning title="Common Misunderstandings"
- **"`flatMap` runs the function immediately."** Not necessarily. For `IO` and `Lazy`, `flatMap` builds a deferred description; nothing runs until interpretation. The *order* is fixed; the *timing* is up to the container.
- **"Monads always short-circuit."** They short-circuit when the container has a notion of failure or absence (`Optional`, `Either`, `Try`, `Maybe`). `List`'s `flatMap` does not short-circuit; it produces the cross-product of results. The behaviour comes from the instance, not from the word "Monad".
- **"`flatMap` is just `flatMap` from `Stream`."** It is the same idea, but specialised. `Stream.flatMap` flattens streams of streams into a single stream. `Optional.flatMap` flattens an optional of optional into one optional. Each `Monad` instance defines its own flattening rule.
- **"Nested `flatMap` is the cleanest we can do."** It is the most explicit. For more than two steps, [For comprehensions](for_comprehension.md) read better and avoid the indentation creep.
- **"I can call `.flatMap` on a `Kind` directly."** `Kind` has no methods. Always go through the `Monad<F>` instance, which is the thing that knows how to flatten inside `F`.
~~~

---

~~~admonish info title="Key Takeaways"
* `flatMap` is the operation we use whenever the next step depends on the previous result
* Short-circuiting comes from the *instance*, not from `Monad` itself; `Optional`, `Either`, `Try` give it for free
* The default helpers (`as`, `peek`, `flatMapIfOrElse`, `flatMapN`) cover most of the small idioms we would otherwise hand-roll
* For chains beyond two or three steps, [For comprehensions](for_comprehension.md) keep the code readable
* When in doubt, [pick the least powerful abstraction that fits the problem](abstraction_levels.md)
~~~

~~~admonish tip title="See Also"
- [Applicative](applicative.md) - For combining independent computations
- [For Comprehension](for_comprehension.md) - Readable syntax for `flatMap` chains
- [Choosing Your Abstraction Level](abstraction_levels.md) - When to use Applicative vs Selective vs Monad
- [One Line, Six Layers](../hkts/one_line_six_layers.md) - Where this fits in the wider Foundations picture
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
