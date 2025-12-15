# Capability Interfaces

The Effect Path API is built on a hierarchy of capability interfaces. Each interface adds specific operations, and Path types implement the capabilities appropriate to their semantics.

~~~admonish info title="What You'll Learn"
- The capability interface hierarchy: Composable, Combinable, Chainable, Recoverable, Effectful, and Accumulating
- How each capability maps to functional programming concepts (Functor, Applicative, Monad, MonadError)
- Which operations each capability provides
- Which Path types implement which capabilities
~~~

## The Capability Hierarchy

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│                        EFFECT PATH CAPABILITY HIERARCHY                           │
├───────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│                             ┌────────────────┐                                    │
│                             │   Composable   │   map(), peek()                    │
│                             │    (Functor)   │                                    │
│                             └───────┬────────┘                                    │
│                                     │                                             │
│                             ┌───────┴────────┐                                    │
│                             │   Combinable   │   zipWith(), map2()                │
│                             │ (Applicative)  │                                    │
│                             └───────┬────────┘                                    │
│                                     │                                             │
│                             ┌───────┴────────┐                                    │
│                             │   Chainable    │   via(), flatMap(), then()         │
│                             │    (Monad)     │                                    │
│                             └───────┬────────┘                                    │
│                                     │                                             │
│         ┌───────────────────────────┼───────────────────────────┐                 │
│         │                           │                           │                 │
│ ┌───────┴────────┐          ┌───────┴────────┐          ┌───────┴────────┐        │
│ │   Recoverable  │          │    Effectful   │          │  Accumulating  │        │
│ │ (MonadError)   │          │      (IO)      │          │  (Validated)   │        │
│ └───────┬────────┘          └───────┬────────┘          └───────┬────────┘        │
│         │                           │                           │                 │
│    ┌────┴────────────┐              │                           │                 │
│    │    │    │       │              │                           │                 │
│ ┌──┴──┐ │ ┌──┴──┐ ┌──┴──────┐  ┌────┴────┐              ┌───────┴───────┐         │
│ │Maybe│ │ │ Try │ │Validate │  │   IO    │              │   Validate    │         │
│ │Path │ │ │Path │ │  Path   │  │  Path   │              │     Path      │         │
│ └─────┘ │ └─────┘ └─────────┘  └─────────┘              └───────────────┘         │
│    ┌────┴────┐                                                                    │
│    │ Either  │     Also: IdPath, OptionalPath, GenericPath (via Chainable)        │
│    │  Path   │                                                                    │
│    └─────────┘                                                                    │
│                                                                                   │
└───────────────────────────────────────────────────────────────────────────────────┘
```

Note: `ValidationPath` implements both `Recoverable` (for short-circuit chaining) and `Accumulating` (for error accumulation).

This hierarchy is not arbitrary. Each level builds on the previous, adding new operations while preserving the guarantees of lower levels. A `Chainable` type can do everything a `Combinable` can do, plus sequential operations. This layering lets you write code at the appropriate level of abstraction: use `map` when simple transformation suffices, reach for `via` only when you need sequencing.

---

## Composable (Functor)

Every Path type can transform values inside its context. This is the most basic operation: apply a function to the success value without changing the structure of the path.

The `Composable` interface provides functor operations:

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

Transforming single values is useful, but real applications often need to combine multiple values. What if you have several validations that are all independent? You could sequence them with `via`, but that imposes unnecessary ordering and stops at the first error.

The `Combinable` interface provides applicative operations for combining independent computations:

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

Sometimes computations are not independent. You need the result of one operation to decide what to do next: fetch a user, then load their preferences, then apply those preferences to a computation. The next step depends on the previous step's value.

The `Chainable` interface provides monadic operations for sequencing dependent computations:

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

Errors happen. Networks fail, files go missing, inputs violate constraints. The previous capabilities let you compose success paths, but real code needs to handle failures gracefully: provide defaults, transform errors, or try alternative approaches.

The `Recoverable` interface provides error handling operations for types that can represent failure:

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

All the paths discussed so far evaluate immediately: when you call `.map()`, it runs. But some effects should be deferred: reading a file, making a network call, writing to a database. These operations have side effects and should only execute when you explicitly request them.

The `Effectful` interface provides operations for deferred side-effectful computations:

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

## Accumulating (Validated)

The `Accumulating` interface provides error-accumulating combination, parallel to `Combinable` but collecting all errors rather than short-circuiting on the first.

```java
public interface Accumulating<E, A> extends Composable<A> {
    /**
     * Combine this value with another, accumulating errors.
     * The Semigroup for combining errors is provided at construction time.
     */
    <B, C> Accumulating<E, C> zipWithAccum(
        Accumulating<E, B> other,
        BiFunction<? super A, ? super B, ? extends C> combiner
    );

    /**
     * Combine with two other values, accumulating all errors.
     */
    <B, C, D> Accumulating<E, D> zipWith3Accum(
        Accumulating<E, B> second,
        Accumulating<E, C> third,
        Function3<? super A, ? super B, ? super C, ? extends D> combiner
    );

    /**
     * Run both validations, keeping this value if both valid.
     */
    Accumulating<E, A> andAlso(Accumulating<E, ?> other);

    /**
     * Run both validations, keeping the other value if both valid.
     */
    <B> Accumulating<E, B> andThen(Accumulating<E, B> other);
}
```

### Key Difference: Accumulation vs Short-Circuit

The fundamental difference between `Combinable.zipWith` and `Accumulating.zipWithAccum`:

| Operation | Behaviour on Multiple Errors |
|-----------|------------------------------|
| `zipWith` | Returns first error only (short-circuits) |
| `zipWithAccum` | Combines all errors using Semigroup |

### The Semigroup Requirement

Error accumulation requires a `Semigroup` to combine error values. Common patterns:

```java
// List semigroup: concatenates error lists
Semigroup<List<String>> listSemigroup = (a, b) -> {
    List<String> combined = new ArrayList<>(a);
    combined.addAll(b);
    return combined;
};

// String semigroup: joins with separator
Semigroup<String> stringSemigroup = (a, b) -> a + "; " + b;
```

### Example: Form Validation

```java
// Semigroup for combining error lists (provided at construction time)
Semigroup<List<String>> errorSemigroup = Semigroups.list();

// Individual field validations (Semigroup passed at creation)
ValidationPath<List<String>, String> nameV =
    Path.validated(validateNameResult(), errorSemigroup);
ValidationPath<List<String>, String> emailV =
    Path.validated(validateEmailResult(), errorSemigroup);
ValidationPath<List<String>, Integer> ageV =
    Path.validated(validateAgeResult(), errorSemigroup);

// Accumulate ALL errors (does not short-circuit)
// Note: Semigroup is already stored in each ValidationPath
ValidationPath<List<String>, User> userV = nameV.zipWith3Accum(
    emailV,
    ageV,
    User::new
);

// Extract result
Validated<List<String>, User> result = userV.run();
result.fold(
    errs -> System.out.println("Errors: " + errs),  // ["Name too short", "Invalid email"]
    user -> System.out.println("Valid: " + user)
);
```

### When to Use Accumulating

Use `Accumulating` (via `ValidationPath`) when:

- You want to show users **all** validation errors at once
- Form validation where each field is validated independently
- Batch processing where you want a complete error report
- Any scenario where partial failure information is valuable

Use `Combinable` (via `EitherPath` or others) when:

- You only need to know about the **first** error
- Subsequent validations depend on earlier ones succeeding
- Performance matters and you want to fail fast

---

## Which Capabilities Each Path Type Has

| Path Type | Composable | Combinable | Chainable | Recoverable | Effectful | Accumulating |
|-----------|:----------:|:----------:|:---------:|:-----------:|:---------:|:------------:|
| MaybePath | ✓ | ✓ | ✓ | ✓ | - | - |
| EitherPath | ✓ | ✓ | ✓ | ✓ | - | - |
| TryPath | ✓ | ✓ | ✓ | ✓ | - | - |
| IOPath | ✓ | ✓ | ✓ | - | ✓ | - |
| ValidationPath | ✓ | ✓ | ✓ | ✓ | - | ✓ |
| IdPath | ✓ | ✓ | ✓ | - | - | - |
| OptionalPath | ✓ | ✓ | ✓ | ✓ | - | - |
| GenericPath | ✓ | ✓ | ✓ | * | - | - |

\* `GenericPath` recovery capabilities depend on the underlying monad instance.

---

## Summary

- **Composable** (Functor): `map`, `peek` - transform values
- **Combinable** (Applicative): `zipWith` - combine independent computations (fail-fast)
- **Chainable** (Monad): `via`, `flatMap`, `then` - sequence dependent computations
- **Recoverable** (MonadError): `recover`, `mapError` - handle failures
- **Effectful** (IO): `unsafeRun`, `runSafe`, `handleError` - execute effects
- **Accumulating** (Validated): `zipWithAccum` - combine independent computations (error-accumulating)

Continue to [Path Types](path_types.md) for detailed coverage of each type.

~~~admonish tip title="See Also"
- [Functor](../functional/functor.md) - The type class behind Composable
- [Applicative](../functional/applicative.md) - The type class behind Combinable
- [Monad](../functional/monad.md) - The type class behind Chainable
- [MonadError](../functional/monad_error.md) - The type class behind Recoverable
- [Validated](../monads/validated_monad.md) - The type behind Accumulating
~~~

~~~admonish tip title="Further Reading"
- **Mateusz Kubuszok**: [The F-words: Functors and Friends](https://kubuszok.com/2018/the-f-words-functors-and-friends/#functor) - An accessible introduction to Functor, Applicative, and Monad with practical examples and law explanations
~~~

---

**Previous:** [Effect Path Overview](effect_path_overview.md)
**Next:** [Path Types](path_types.md)
