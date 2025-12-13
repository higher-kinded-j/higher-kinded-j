# Capability Interfaces

The Effect Path API is built on a hierarchy of capability interfaces. Each interface adds specific operations, and Path types implement the capabilities appropriate to their semantics.

~~~admonish info title="What You'll Learn"
- The capability interface hierarchy: Composable, Combinable, Chainable, Recoverable, and Effectful
- How each capability maps to functional programming concepts (Functor, Applicative, Monad, MonadError)
- Which operations each capability provides
- Which Path types implement which capabilities
~~~

## The Capability Hierarchy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      EFFECT PATH CAPABILITY HIERARCHY                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│                           ┌────────────────┐                                │
│                           │   Composable   │   map(), peek()                │
│                           │    (Functor)   │                                │
│                           └───────┬────────┘                                │
│                                   │                                         │
│                           ┌───────┴────────┐                                │
│                           │   Combinable   │   zipWith(), map2()            │
│                           │ (Applicative)  │                                │
│                           └───────┬────────┘                                │
│                                   │                                         │
│                           ┌───────┴────────┐                                │
│                           │   Chainable    │   via(), flatMap(), then()     │
│                           │    (Monad)     │                                │
│                           └───────┬────────┘                                │
│                                   │                                         │
│               ┌───────────────────┴───────────────────┐                     │
│               │                                       │                     │
│       ┌───────┴────────┐                     ┌────────┴───────┐             │
│       │   Recoverable  │                     │    Effectful   │             │
│       │ (MonadError)   │                     │      (IO)      │             │
│       └───────┬────────┘                     └────────┬───────┘             │
│               │                                       │                     │
│    ┌──────────┼──────────┐                            │                     │
│    │          │          │                            │                     │
│ ┌──┴───┐ ┌────┴────┐ ┌───┴───┐                   ┌────┴────┐                │
│ │Maybe │ │ Either  │ │  Try  │                   │   IO    │                │
│ │ Path │ │  Path   │ │ Path  │                   │  Path   │                │
│ └──────┘ └─────────┘ └───────┘                   └─────────┘                │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Composable (Functor)

The `Composable` interface provides functor operations - the ability to transform values inside a context.

```java
public interface Composable<A> {
    /**
     * Transform the value inside this context.
     * @param f the transformation function
     * @return a new Composable with the transformed value
     */
    <B> Composable<B> map(Function<? super A, ? extends B> f);

    /**
     * Execute a side effect without changing the value.
     * @param action the side effect to execute
     * @return this Composable unchanged
     */
    Composable<A> peek(Consumer<? super A> action);
}
```

### Functor Laws

All Path types satisfy the functor laws:

1. **Identity**: `path.map(x -> x)` equals `path`
2. **Composition**: `path.map(f).map(g)` equals `path.map(f.andThen(g))`

### Example

```java
MaybePath<String> name = Path.just("alice");

// Identity
name.map(Function.identity())  // equals name

// Composition
Function<String, String> upper = String::toUpperCase;
Function<String, Integer> length = String::length;

name.map(upper).map(length)           // Just(5)
name.map(upper.andThen(length))       // Just(5) - same result
```

---

## Combinable (Applicative)

The `Combinable` interface provides applicative operations - the ability to combine independent computations.

```java
public interface Combinable<A> extends Composable<A> {
    /**
     * Combine this value with another using a function.
     * Both computations are independent.
     */
    <B, C> Combinable<C> zipWith(Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> f);

    /**
     * Combine with two other values.
     */
    <B, C, D> Combinable<D> zipWith3(
        Combinable<B> second,
        Combinable<C> third,
        TriFunction<? super A, ? super B, ? super C, ? extends D> f
    );
}
```

### Key Property: Independence

Unlike `via`/`flatMap`, `zipWith` combines *independent* computations. Neither depends on the other's result:

```java
// Independent: name and age don't depend on each other
MaybePath<String> name = validateName(input.name());
MaybePath<Integer> age = validateAge(input.age());

MaybePath<Person> person = name.zipWith(age, Person::new);
```

### Example: Validation

```java
EitherPath<String, String> validName = validateNameE("Alice");
EitherPath<String, String> validEmail = validateEmailE("alice@example.com");
EitherPath<String, Integer> validAge = validateAgeE(25);

// Combine all validations (fail-fast on first error)
EitherPath<String, User> user = validName.zipWith3(validEmail, validAge, User::new);
```

---

## Chainable (Monad)

The `Chainable` interface provides monadic operations - the ability to sequence dependent computations.

```java
public interface Chainable<A> extends Combinable<A> {
    /**
     * Chain a computation that depends on this value.
     * The name "via" mirrors the optics Focus DSL.
     */
    <B> Chainable<B> via(Function<? super A, ? extends Chainable<B>> f);

    /**
     * Alias for via - traditional monadic bind.
     */
    <B> Chainable<B> flatMap(Function<? super A, ? extends Chainable<B>> f);

    /**
     * Execute another computation after this one, ignoring this value.
     */
    <B> Chainable<B> then(Supplier<? extends Chainable<B>> next);
}
```

### Monad Laws

All Path types satisfy the monad laws:

1. **Left Identity**: `Path.just(a).via(f)` equals `f.apply(a)`
2. **Right Identity**: `path.via(Path::just)` equals `path`
3. **Associativity**: `path.via(f).via(g)` equals `path.via(x -> f.apply(x).via(g))`

### Example: Dependent Computations

```java
// Each step depends on the previous result
EitherPath<Error, Invoice> invoice =
    Path.either(findUser(userId))
        .via(user -> Path.either(getShoppingCart(user)))  // needs user
        .via(cart -> Path.either(calculateTotal(cart)))   // needs cart
        .via(total -> Path.either(createInvoice(total))); // needs total
```

### `via` vs `zipWith`

Use `via` when the next computation depends on the previous result:
```java
// ✓ Correct: getOrders depends on user
Path.maybe(findUser(id)).via(user -> Path.maybe(getOrders(user)))
```

Use `zipWith` when computations are independent:
```java
// ✓ Correct: name and email validations are independent
validateName(n).zipWith(validateEmail(e), User::new)
```

---

## Recoverable (MonadError)

The `Recoverable` interface provides error handling operations for types that can represent failure.

```java
public interface Recoverable<E, A> extends Chainable<A> {
    /**
     * Provide a fallback value if this computation failed.
     */
    Recoverable<E, A> recover(Function<? super E, ? extends A> handler);

    /**
     * Provide a fallback computation if this failed.
     */
    Recoverable<E, A> recoverWith(Function<? super E, ? extends Recoverable<E, A>> handler);

    /**
     * Provide an alternative if this failed.
     */
    Recoverable<E, A> orElse(Supplier<? extends Recoverable<E, A>> alternative);

    /**
     * Transform the error type.
     */
    <F> Recoverable<F, A> mapError(Function<? super E, ? extends F> f);
}
```

### MaybePath Recovery

For `MaybePath`, the "error" is `Nothing` (absence):

```java
MaybePath<User> user = Path.maybe(findUser(id))
    .recover(nothing -> User.guest())  // Note: MaybePath.recover takes a Supplier
    .orElse(() -> Path.just(User.anonymous()));
```

### EitherPath Recovery

For `EitherPath`, errors are typed values:

```java
EitherPath<Error, Config> config =
    Path.either(loadConfig())
        .recover(error -> Config.defaults())
        .mapError(e -> new ConfigError("Failed to load", e));
```

### TryPath Recovery

For `TryPath`, errors are exceptions:

```java
TryPath<Integer> parsed = Path.tryOf(() -> Integer.parseInt(input))
    .recover(ex -> 0)  // Default on any exception
    .recoverWith(ex ->
        ex instanceof NumberFormatException
            ? Path.success(-1)
            : Path.failure(ex));
```

---

## Effectful (IO)

The `Effectful` interface provides operations for deferred side-effectful computations.

```java
public interface Effectful<A> extends Chainable<A> {
    /**
     * Execute the effect and return the result.
     * May throw exceptions.
     */
    A unsafeRun();

    /**
     * Execute safely, capturing exceptions in a Try.
     */
    Try<A> runSafe();

    /**
     * Handle exceptions that occur during execution.
     */
    Effectful<A> handleError(Function<? super Throwable, ? extends A> handler);

    /**
     * Recover from exceptions with another effect.
     */
    Effectful<A> handleErrorWith(Function<? super Throwable, ? extends Effectful<A>> handler);

    /**
     * Ensure a cleanup action runs regardless of success/failure.
     */
    Effectful<A> ensuring(Runnable cleanup);
}
```

### Example: Resource Management

```java
IOPath<String> readFile = Path.io(() -> {
        var reader = new BufferedReader(new FileReader("data.txt"));
        return reader.readLine();
    })
    .handleError(ex -> "default content")
    .ensuring(() -> log.debug("Read operation completed"));

// Nothing executes until:
String content = readFile.unsafeRun();
```

---

## Which Capabilities Each Path Type Has

| Path Type | Composable | Combinable | Chainable | Recoverable | Effectful |
|-----------|:----------:|:----------:|:---------:|:-----------:|:---------:|
| MaybePath | ✓ | ✓ | ✓ | ✓ | - |
| EitherPath | ✓ | ✓ | ✓ | ✓ | - |
| TryPath | ✓ | ✓ | ✓ | ✓ | - |
| IOPath | ✓ | ✓ | ✓ | - | ✓ |

---

## Summary

- **Composable** (Functor): `map`, `peek` - transform values
- **Combinable** (Applicative): `zipWith` - combine independent computations
- **Chainable** (Monad): `via`, `flatMap`, `then` - sequence dependent computations
- **Recoverable** (MonadError): `recover`, `mapError` - handle failures
- **Effectful** (IO): `unsafeRun`, `runSafe`, `handleError` - execute effects

Continue to [Path Types](path_types.md) for detailed coverage of each type.

~~~admonish tip title="Further Reading"
- **Cats Documentation**: [Type Classes](https://typelevel.org/cats/typeclasses.html) - Scala's type class hierarchy
- **Functional Programming in Scala**: Chapter 11 covers Monads and Chapter 12 covers Applicative
~~~

---

**Previous:** [Effect Path Overview](effect_path_overview.md)
**Next:** [Path Types](path_types.md)
