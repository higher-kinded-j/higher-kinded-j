# Path Types

> *"It is not down on any map; true places never are."*
>
> — Herman Melville, *Moby-Dick*

Melville was speaking of Queequeg's island home, but the observation applies
to software: the territory you're navigating (nullable returns, network
failures, validation errors, deferred effects) isn't marked on any class
diagram. You need to choose your vessel before setting sail.

This chapter covers each Path type in detail. But before cataloguing the
fleet, a more pressing question: *which one do you need?*

~~~admonish info title="What You'll Learn"
- How to choose the right Path type for your situation
- Detailed API for each type: `MaybePath`, `EitherPath`, `TryPath`, `IOPath`, `ValidationPath`, `IdPath`, `OptionalPath`, and `GenericPath`
- Creation methods, core operations, and extraction patterns
- Recovery and error handling specific to each type
- When each type is the right tool, and when it isn't
~~~

---

## Choosing Your Path

Before diving into specifics, orient yourself by the problem you're solving:

### "The value might not exist"

You're dealing with absence: a lookup that returns nothing, an optional
configuration, a field that might be null.

**Reach for `MaybePath`** if absence is normal and expected, not an error
condition. Nobody needs to know *why* the value is missing.

**Reach for `OptionalPath`** if you're bridging to Java's `Optional` ecosystem
and want to stay close to the standard library.

### "The operation might fail, and I need to know why"

Something can go wrong, and the error carries information: a validation
message, a typed error code, a domain-specific failure.

**Reach for `EitherPath`** when you control the error type and want typed,
structured errors.

**Reach for `TryPath`** when you're wrapping code that throws exceptions and
want to stay in exception-land (with `Throwable` as the error type).

### "I need ALL the errors, not just the first"

Multiple independent validations, and stopping at the first failure would
be unkind to your users.

**Reach for `ValidationPath`** with `zipWithAccum` to accumulate every error.

### "The operation has side effects I want to defer"

You're reading files, calling APIs, writing to databases, effects that
shouldn't happen until you're ready.

**Reach for `IOPath`** to describe the effect without executing it. Nothing
runs until you call `unsafeRun()`.

### "The operation always succeeds"

No failure case, no absence; you just want Path operations on a pure value.

**Reach for `IdPath`** when you need a trivial Path for generic code or testing.

### "None of the above"

You have a custom monad, or you're writing highly generic code.

**Reach for `GenericPath`** as the escape hatch; it wraps any `Kind<F, A>`
with a `Monad` instance.

---

## Quick Reference

| Path Type | Wraps | Error Type | Evaluation | Key Use Case |
|-----------|-------|------------|------------|--------------|
| `MaybePath<A>` | `Maybe<A>` | None (absence) | Immediate | Optional values |
| `EitherPath<E, A>` | `Either<E, A>` | `E` (typed) | Immediate | Typed error handling |
| `TryPath<A>` | `Try<A>` | `Throwable` | Immediate | Exception wrapping |
| `IOPath<A>` | `IO<A>` | `Throwable` | **Deferred** | Side effects |
| `ValidationPath<E, A>` | `Validated<E, A>` | `E` (accumulated) | Immediate | Form validation |
| `IdPath<A>` | `Id<A>` | None (always succeeds) | Immediate | Pure values |
| `OptionalPath<A>` | `Optional<A>` | None (absence) | Immediate | Java stdlib bridge |
| `GenericPath<F, A>` | `Kind<F, A>` | Depends on monad | Depends | Custom monads |

---

## MaybePath

`MaybePath<A>` wraps `Maybe<A>` for computations that might produce nothing.
It's the simplest failure mode: either you have a value, or you don't.

### Creation

```java
// From a value
MaybePath<String> greeting = Path.just("hello");

// Absence
MaybePath<String> nothing = Path.nothing();

// From existing Maybe
MaybePath<User> user = Path.maybe(repository.findById(id));

// From nullable (null becomes Nothing)
MaybePath<String> fromNullable = Path.fromNullable(possiblyNull);
```

### Core Operations

```java
MaybePath<String> name = Path.just("Alice");

// Transform
MaybePath<Integer> length = name.map(String::length);  // Just(5)

// Chain
MaybePath<String> upper = name.via(s -> Path.just(s.toUpperCase()));

// Combine independent values
MaybePath<Integer> age = Path.just(25);
MaybePath<String> summary = name.zipWith(age, (n, a) -> n + " is " + a);
// Just("Alice is 25")
```

### Recovery

```java
MaybePath<User> user = Path.maybe(findUser(id))
    .orElse(() -> Path.just(User.guest()));

// Filter (returns Nothing if predicate fails)
MaybePath<Integer> positive = Path.just(42).filter(n -> n > 0);  // Just(42)
MaybePath<Integer> rejected = Path.just(-1).filter(n -> n > 0);  // Nothing
```

### Extraction

```java
MaybePath<String> path = Path.just("hello");

Maybe<String> maybe = path.run();
String value = path.getOrElse("default");
String value = path.getOrThrow(() -> new NoSuchElementException());
```

### When to Use

`MaybePath` is right when:
- Absence is **normal**, not exceptional
- You don't need to explain *why* the value is missing
- You're modelling optional data (configuration, nullable fields, lookups)

`MaybePath` is wrong when:
- Callers need to know the reason for failure: use `EitherPath`
- You're wrapping code that throws: use `TryPath`

---

## EitherPath

`EitherPath<E, A>` wraps `Either<E, A>` for computations with typed errors.
The left side carries failure; the right side carries success. (Right is
right, as the mnemonic goes.)

### Creation

```java
// Success
EitherPath<Error, Integer> success = Path.right(42);

// Failure
EitherPath<Error, Integer> failure = Path.left(new ValidationError("invalid"));

// From existing Either
EitherPath<Error, User> user = Path.either(validateUser(input));
```

### Core Operations

```java
EitherPath<String, Integer> number = Path.right(42);

// Transform success
EitherPath<String, String> formatted = number.map(n -> "Value: " + n);

// Chain
EitherPath<String, Integer> doubled = number.via(n ->
    n > 0 ? Path.right(n * 2) : Path.left("Must be positive"));

// Combine independent values
EitherPath<String, String> name = Path.right("Alice");
EitherPath<String, Integer> age = Path.right(25);
EitherPath<String, Person> person = name.zipWith(age, Person::new);
```

### Error Handling

```java
EitherPath<String, Config> config = Path.either(loadConfig())
    // Provide fallback value
    .recover(error -> Config.defaults())

    // Transform error type
    .mapError(e -> new ConfigError(e))

    // Recover with another computation
    .recoverWith(error -> Path.either(loadBackupConfig()))

    // Provide alternative path
    .orElse(() -> Path.right(Config.defaults()));
```

### Bifunctor Operations

Transform both sides simultaneously:

```java
EitherPath<String, Integer> original = Path.right(42);

EitherPath<Integer, String> transformed = original.bimap(
    String::length,      // Transform error
    n -> "Value: " + n   // Transform success
);
```

### Extraction

```java
EitherPath<String, Integer> path = Path.right(42);
Either<String, Integer> either = path.run();

// Pattern match
String result = either.fold(
    error -> "Error: " + error,
    value -> "Value: " + value
);

// Direct access (throws if wrong side)
if (either.isRight()) {
    Integer value = either.getRight();
}
```

### When to Use

`EitherPath` is right when:
- Errors carry meaningful, typed information
- Different errors need different handling
- You're building validation pipelines (with short-circuit semantics)
- You want to transform errors as they propagate

`EitherPath` is wrong when:
- You need to collect *all* errors: use `ValidationPath`
- Absence isn't really an error: use `MaybePath`

---

## TryPath

`TryPath<A>` wraps `Try<A>` for computations that might throw exceptions.
It bridges the gap between Java's exception-based world and functional
composition.

### Creation

```java
// Successful value
TryPath<Integer> success = Path.success(42);

// Failed value
TryPath<Integer> failure = Path.failure(new RuntimeException("oops"));

// From computation that may throw
TryPath<Integer> parsed = Path.tryOf(() -> Integer.parseInt(input));

// From existing Try
TryPath<Config> config = Path.of(loadConfigTry());
```

### Core Operations

```java
TryPath<String> content = Path.tryOf(() -> Files.readString(path));

// Transform
TryPath<Integer> lineCount = content.map(s -> s.split("\n").length);

// Chain
TryPath<Data> data = content.via(c -> Path.tryOf(() -> parseJson(c)));

// Combine
TryPath<String> file1 = Path.tryOf(() -> readFile("a.txt"));
TryPath<String> file2 = Path.tryOf(() -> readFile("b.txt"));
TryPath<String> combined = file1.zipWith(file2, (a, b) -> a + "\n" + b);
```

### Error Handling

```java
TryPath<Integer> parsed = Path.tryOf(() -> Integer.parseInt(input))
    // Recover with value
    .recover(ex -> 0)

    // Recover based on exception type
    .recoverWith(ex -> {
        if (ex instanceof NumberFormatException) {
            return Path.success(-1);
        }
        return Path.failure(ex);
    })

    // Alternative
    .orElse(() -> Path.success(defaultValue));
```

### Extraction

```java
TryPath<Integer> path = Path.success(42);
Try<Integer> tryValue = path.run();

Integer value = path.getOrElse(-1);

if (tryValue.isSuccess()) {
    System.out.println("Value: " + tryValue.get());
} else {
    System.out.println("Error: " + tryValue.getCause().getMessage());
}
```

### When to Use

`TryPath` is right when:
- You're wrapping APIs that throw exceptions
- The specific exception type matters for recovery
- You want exception-safe composition without try-catch blocks
- Interoperating with legacy code

`TryPath` is wrong when:
- You want typed errors (not just `Throwable`): use `EitherPath`
- The code doesn't throw: use `MaybePath` or `EitherPath`

---

## IOPath

`IOPath<A>` wraps `IO<A>` for **deferred** side-effectful computations.
Unlike other Path types, nothing happens until you explicitly run it.

> *"Buy the ticket, take the ride... and if it occasionally gets a little
> heavier than what you had in mind, well... maybe chalk it up to forced
> consciousness expansion."*
>
> — Hunter S. Thompson, *Fear and Loathing in Las Vegas*

Thompson's advice applies here. When you call `unsafeRun()`, you've bought
the ticket. The effects will happen. There's no going back. Until that moment,
an `IOPath` is just a description, a plan you haven't committed to yet.

### Creation

```java
// Pure value (no effects)
IOPath<Integer> pure = Path.ioPure(42);

// Deferred effect
IOPath<String> readFile = Path.io(() -> Files.readString(Paths.get("data.txt")));

// From existing IO
IOPath<Connection> conn = Path.ioOf(databaseIO);
```

### Core Operations (All Deferred)

```java
IOPath<String> content = Path.io(() -> fetchFromApi(url));

// Transform (deferred)
IOPath<Data> data = content.map(this::parse);

// Chain (deferred)
IOPath<Result> result = content.via(c -> Path.io(() -> process(c)));

// Combine (deferred)
IOPath<String> header = Path.io(() -> readHeader());
IOPath<String> body = Path.io(() -> readBody());
IOPath<String> combined = header.zipWith(body, (h, b) -> h + "\n" + b);

// Sequence (discarding first result)
IOPath<Unit> setup = Path.io(() -> log("Starting..."));
IOPath<Data> withSetup = setup.then(() -> Path.io(() -> loadData()));
```

### Execution: Buying the Ticket

```java
IOPath<String> io = Path.io(() -> fetchData());

// Execute (may throw)
String result = io.unsafeRun();

// Execute safely (captures exceptions)
Try<String> result = io.runSafe();

// Convert to TryPath (executes immediately)
TryPath<String> tryPath = io.toTryPath();
```

The naming is deliberate. `unsafeRun` warns you: referential transparency
ends here. Side effects are about to happen. Call it at the boundaries of
your system (in your `main` method, your HTTP handler, your message consumer),
not scattered throughout your business logic.

### Error Handling

```java
IOPath<Config> config = Path.io(() -> loadConfig())
    // Handle any exception
    .handleError(ex -> Config.defaults())

    // Handle with another effect
    .handleErrorWith(ex -> Path.io(() -> loadBackupConfig()))

    // Ensure cleanup runs regardless of outcome
    .ensuring(() -> releaseResources());
```

### Lazy Evaluation in Action

```java
IOPath<String> effect = Path.io(() -> {
    System.out.println("Side effect!");  // Not printed yet
    return "result";
});

// Still nothing
IOPath<Integer> transformed = effect.map(String::length);

// NOW it runs
Integer length = transformed.unsafeRun();  // Prints "Side effect!"
```

### When to Use

`IOPath` is right when:
- You're performing side effects (file I/O, network, database)
- You want lazy evaluation: describe now, execute later
- You want referential transparency throughout your core logic
- You need to compose complex effect pipelines before committing

`IOPath` is wrong when:
- You want immediate execution: use `TryPath`
- There are no side effects: use `EitherPath` or `MaybePath`

---

## ValidationPath

`ValidationPath<E, A>` wraps `Validated<E, A>` for computations that
**accumulate** errors instead of short-circuiting on the first failure.

### Creation

```java
// Valid value
ValidationPath<List<String>, Integer> valid =
    Path.valid(42, Semigroups.list());

// Invalid value with errors
ValidationPath<List<String>, Integer> invalid =
    Path.invalid(List.of("Error 1", "Error 2"), Semigroups.list());

// From existing Validated
ValidationPath<String, User> user =
    Path.validation(validatedUser, Semigroups.first());
```

The `Semigroup<E>` parameter defines how errors combine when multiple
validations fail. Common choices:
- `Semigroups.list()`: concatenate error lists
- `Semigroups.string("; ")`: join strings with separator

### Core Operations

```java
ValidationPath<List<String>, String> name =
    Path.valid("Alice", Semigroups.list());

// Transform (same as other paths)
ValidationPath<List<String>, Integer> length = name.map(String::length);

// Chain with via (short-circuits on first error)
ValidationPath<List<String>, String> upper =
    name.via(s -> Path.valid(s.toUpperCase(), Semigroups.list()));
```

### Error Accumulation: The Point of It All

The key operation is `zipWithAccum`, which collects **all** errors:

```java
ValidationPath<List<String>, String> nameV = validateName(input.name());
ValidationPath<List<String>, String> emailV = validateEmail(input.email());
ValidationPath<List<String>, Integer> ageV = validateAge(input.age());

// Accumulate ALL errors (does not short-circuit)
ValidationPath<List<String>, User> userV = nameV.zipWith3Accum(
    emailV,
    ageV,
    User::new
);

// If name and email both fail:
// Invalid(["Name too short", "Invalid email format"])
// NOT just Invalid(["Name too short"])
```

Compare with `zipWith`, which short-circuits:

```java
// Short-circuits: only first error returned
ValidationPath<List<String>, User> shortCircuit =
    nameV.zipWith(emailV, ageV, User::new);
```

### Combining Validations

```java
// andAlso runs both, accumulating errors, keeping first value if both valid
ValidationPath<List<String>, String> thorough =
    checkNotEmpty(name)
        .andAlso(checkMaxLength(name, 100))
        .andAlso(checkNoSpecialChars(name));
// All three checks run; all errors collected
```

### Extraction

```java
ValidationPath<List<String>, User> path = validateUser(input);
Validated<List<String>, User> validated = path.run();

String result = validated.fold(
    errors -> "Errors: " + String.join(", ", errors),
    user -> "Valid user: " + user.name()
);
```

### When to Use

`ValidationPath` is right when:
- You want users to see **all** validation errors at once
- Multiple independent checks must all run
- Form validation, batch processing, comprehensive error reports
- Being kind to users matters (it does)

`ValidationPath` is wrong when:
- You only need the first error: use `EitherPath`
- Subsequent validations depend on earlier ones passing: use `EitherPath` with `via`

---

## IdPath

`IdPath<A>` wraps `Id<A>`, the identity monad. It always contains a value
and never fails. This sounds useless until you need it.

### Creation

```java
IdPath<String> id = Path.id("hello");
IdPath<User> fromId = Path.idOf(idUser);
```

### Core Operations

```java
IdPath<String> name = Path.id("Alice");

IdPath<Integer> length = name.map(String::length);  // Id(5)
IdPath<String> upper = name.via(s -> Path.id(s.toUpperCase()));
IdPath<String> combined = name.zipWith(Path.id(25), (n, a) -> n + " is " + a);
```

### Extraction

```java
IdPath<String> path = Path.id("hello");
String value = path.run().value();  // "hello"
String value = path.get();          // "hello"
```

### When to Use

`IdPath` is right when:
- You're writing generic code that works over any Path type
- Testing monadic code with known, predictable values
- You need a "no-op" Path that always succeeds
- Satisfying a type parameter that demands a Path

`IdPath` is wrong when:
- Failure is possible: you need one of the other types

---

## OptionalPath

`OptionalPath<A>` wraps Java's `java.util.Optional<A>`, bridging the
standard library and the Path API.

### Creation

```java
OptionalPath<String> present = Path.present("hello");
OptionalPath<String> absent = Path.absent();
OptionalPath<User> user = Path.optional(repository.findById(id));
```

### Core Operations

```java
OptionalPath<String> name = Path.present("Alice");

OptionalPath<Integer> length = name.map(String::length);
OptionalPath<String> upper = name.via(s -> Path.present(s.toUpperCase()));
```

### Extraction and Conversion

```java
OptionalPath<String> path = Path.present("hello");

Optional<String> optional = path.run();
String value = path.getOrElse("default");
boolean hasValue = path.isPresent();

// Convert to MaybePath for richer operations
MaybePath<String> maybe = path.toMaybePath();
```

### When to Use

`OptionalPath` is right when:
- You're integrating with Java APIs that return `Optional`
- You prefer staying close to standard library semantics
- Bridging between Higher-Kinded-J and existing codebases

`OptionalPath` is wrong when:
- You're not constrained by `Optional`: `MaybePath` is slightly richer

---

## GenericPath

`GenericPath<F, A>` is the escape hatch. It wraps *any* `Kind<F, A>` with
a `Monad` instance, letting you use Path operations on custom types.

### Creation

```java
Monad<ListKind.Witness> listMonad = ListMonad.INSTANCE;
Kind<ListKind.Witness, Integer> listKind =
    ListKindHelper.LIST.widen(List.of(1, 2, 3));

GenericPath<ListKind.Witness, Integer> listPath =
    Path.generic(listKind, listMonad);
```

### Core Operations

```java
GenericPath<ListKind.Witness, Integer> numbers = Path.generic(listKind, listMonad);

GenericPath<ListKind.Witness, String> strings = numbers.map(n -> "n" + n);

GenericPath<ListKind.Witness, Integer> doubled = numbers.via(n ->
    Path.generic(ListKindHelper.LIST.widen(List.of(n, n * 2)), listMonad));
```

### Extraction

```java
Kind<ListKind.Witness, Integer> kind = path.run();
List<Integer> list = ListKindHelper.LIST.narrow(kind);
```

### When to Use

`GenericPath` is right when:
- You have a custom monad not covered by specific Path types
- Writing highly generic code across multiple monad types
- Experimenting with new effect types

~~~admonish tip title="Extensibility by Design"
`GenericPath` demonstrates the power of higher-kinded types in Java: write
your algorithm once, and it works with `Maybe`, `Either`, `List`, `IO`, or
any custom monad. This is the same abstraction power that makes libraries
like Cats and ZIO flexible in Scala, now available in Java.
~~~

---

## Summary: Choosing Your Vessel

| Scenario | Path Type | Why |
|----------|-----------|-----|
| Value might be absent | `MaybePath` | Simple presence/absence |
| Operation might fail with typed error | `EitherPath` | Structured error handling |
| Wrapping exception-throwing code | `TryPath` | Exception → functional bridge |
| Side effects to defer | `IOPath` | Lazy, referential transparency |
| Need ALL validation errors | `ValidationPath` | Error accumulation |
| Bridging Java's Optional | `OptionalPath` | Stdlib compatibility |
| Always succeeds, pure value | `IdPath` | Generic/testing contexts |
| Custom monad | `GenericPath` | Universal escape hatch |

The choice isn't always obvious, and that's fine. You can convert between
types as your needs evolve (`MaybePath` to `EitherPath` when you need error
messages, `TryPath` to `EitherPath` when you want typed errors). The next
chapter covers these conversions in detail.

Continue to [Composition Patterns](composition.md) for techniques on
combining and sequencing Path operations.

~~~admonish tip title="See Also"
- [Maybe Monad](../monads/maybe_monad.md) - Underlying type for MaybePath
- [Either Monad](../monads/either_monad.md) - Underlying type for EitherPath
- [Try Monad](../monads/try_monad.md) - Underlying type for TryPath
- [IO Monad](../monads/io_monad.md) - Underlying type for IOPath
- [Validated](../monads/validated_monad.md) - Underlying type for ValidationPath
~~~

---

**Previous:** [Capability Interfaces](capabilities.md)
**Next:** [Composition Patterns](composition.md)
