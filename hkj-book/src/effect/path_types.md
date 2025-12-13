# Path Types

This chapter provides detailed coverage of each Path type, their specific operations, and when to use them.

~~~admonish info title="What You'll Learn"
- Detailed API for `MaybePath`, `EitherPath`, `TryPath`, `IOPath`, `ValidationPath`, `IdPath`, `OptionalPath`, and `GenericPath`
- Creation methods, core operations, and extraction patterns for each type
- Recovery and error handling specific to each Path type
- Error accumulation with `ValidationPath`
- Guidelines for choosing the right Path type for your use case
~~~

## MaybePath

`MaybePath<A>` wraps `Maybe<A>` for computations that may produce a value or nothing.

### Creation

```java
// From a value
MaybePath<String> just = Path.just("hello");

// Empty
MaybePath<String> nothing = Path.nothing();

// From existing Maybe
MaybePath<User> userPath = Path.maybe(userRepository.findById(id));

// From nullable
MaybePath<String> fromNull = Path.fromNullable(nullableValue);
```

### Core Operations

```java
MaybePath<String> name = Path.just("Alice");

// Transform
MaybePath<Integer> length = name.map(String::length);  // Just(5)

// Chain
MaybePath<String> upper = name.via(s -> Path.just(s.toUpperCase()));

// Combine
MaybePath<Integer> age = Path.just(25);
MaybePath<String> combined = name.zipWith(age, (n, a) -> n + " is " + a);
// Just("Alice is 25")
```

### Extraction

```java
MaybePath<String> path = Path.just("hello");

// Get underlying Maybe
Maybe<String> maybe = path.run();

// Get with default
String value = path.getOrElse("default");  // "hello"

// Get or throw
String value = path.getOrThrow(() -> new NoSuchElementException());
```

### Recovery

```java
MaybePath<User> user = Path.maybe(findUser(id))
    .orElse(() -> Path.just(User.guest()));

// Filter (returns Nothing if predicate fails)
MaybePath<Integer> positive = Path.just(42).filter(n -> n > 0);  // Just(42)
MaybePath<Integer> filtered = Path.just(-1).filter(n -> n > 0); // Nothing
```

### When to Use

Use `MaybePath` when:
- Absence is a normal, expected case (not an error)
- You don't need error information
- Working with optional data (nullable in other systems)

---

## EitherPath

`EitherPath<E, A>` wraps `Either<E, A>` for computations with typed errors.

### Creation

```java
// Success (Right)
EitherPath<Error, Integer> success = Path.right(42);

// Failure (Left)
EitherPath<Error, Integer> failure = Path.left(new ValidationError("invalid"));

// From existing Either
EitherPath<Error, User> userPath = Path.either(validateUser(input));
```

### Core Operations

```java
EitherPath<String, Integer> number = Path.right(42);

// Transform success value
EitherPath<String, String> formatted = number.map(n -> "Value: " + n);

// Chain computations
EitherPath<String, Integer> result = number.via(n ->
    n > 0 ? Path.right(n * 2) : Path.left("Must be positive"));

// Combine independent computations
EitherPath<String, String> name = Path.right("Alice");
EitherPath<String, Integer> age = Path.right(25);
EitherPath<String, Person> person = name.zipWith(age, Person::new);
```

### Error Handling

```java
EitherPath<String, Config> config = Path.either(loadConfig())
    // Recover with a value
    .recover(error -> Config.defaults())

    // Transform error type
    .mapError(e -> new ConfigError(e))

    // Recover with another computation
    .recoverWith(error -> Path.either(loadBackupConfig()))

    // Provide alternative
    .orElse(() -> Path.right(Config.defaults()));
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

// Check and extract
if (either.isRight()) {
    Integer value = either.getRight();
}
```

### Bifunctor Operations

`EitherPath` supports bifunctor operations to transform both sides:

```java
EitherPath<String, Integer> original = Path.right(42);

// Transform both error and success types
EitherPath<Integer, String> transformed = original.bimap(
    String::length,      // Transform error
    n -> "Value: " + n   // Transform success
);
```

### When to Use

Use `EitherPath` when:
- Errors carry meaningful information
- You want typed error handling
- Different error types need different handling
- Building validation pipelines

---

## TryPath

`TryPath<A>` wraps `Try<A>` for computations that may throw exceptions.

### Creation

```java
// Successful value
TryPath<Integer> success = Path.success(42);

// Failed value
TryPath<Integer> failure = Path.failure(new RuntimeException("error"));

// From computation that may throw
TryPath<Integer> parsed = Path.tryOf(() -> Integer.parseInt(input));

// From existing Try
TryPath<Config> configPath = Path.of(loadConfigTry());
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
    // Recover with a value
    .recover(ex -> 0)

    // Recover based on exception type
    .recoverWith(ex -> {
        if (ex instanceof NumberFormatException) {
            return Path.success(-1);
        }
        return Path.failure(ex);
    })

    // Provide alternative
    .orElse(() -> Path.success(defaultValue));
```

### Extraction

```java
TryPath<Integer> path = Path.success(42);
Try<Integer> tryValue = path.run();

// Get with default
Integer value = path.getOrElse(-1);  // 42

// Check result
if (tryValue.isSuccess()) {
    System.out.println("Value: " + tryValue.get());
} else {
    System.out.println("Error: " + tryValue.getCause().getMessage());
}
```

### When to Use

Use `TryPath` when:
- Working with APIs that throw exceptions
- You want exception-safe composition
- Interoperating with legacy code
- The specific exception type matters

---

## IOPath

`IOPath<A>` wraps `IO<A>` for deferred side-effectful computations.

### Creation

```java
// Pure value (no effects)
IOPath<Integer> pure = Path.ioPure(42);

// Deferred effect
IOPath<String> readFile = Path.io(() -> Files.readString(Paths.get("data.txt")));

// From existing IO
IOPath<Connection> connPath = Path.ioOf(databaseIO);
```

### Core Operations

```java
IOPath<String> content = Path.io(() -> fetchFromApi(url));

// Transform (deferred)
IOPath<Data> data = content.map(this::parse);

// Chain (deferred)
IOPath<Result> result = content.via(c ->
    Path.io(() -> processContent(c)));

// Combine (deferred)
IOPath<String> header = Path.io(() -> readHeader());
IOPath<String> body = Path.io(() -> readBody());
IOPath<String> combined = header.zipWith(body, (h, b) -> h + "\n" + b);

// Sequence (discard first result)
IOPath<Unit> logging = Path.io(() -> log("Starting..."));
IOPath<Data> withLogging = logging.then(() -> processData());
```

### Execution

```java
IOPath<String> path = Path.io(() -> fetchData());

// Execute (may throw)
String result = path.unsafeRun();

// Execute safely
Try<String> result = path.runSafe();

// Convert to TryPath for further composition
TryPath<String> tryPath = path.toTryPath();
```

### Error Handling

```java
IOPath<Config> config = Path.io(() -> loadConfig())
    // Handle any exception
    .handleError(ex -> Config.defaults())

    // Handle with another effect
    .handleErrorWith(ex -> Path.io(() -> loadBackupConfig()))

    // Ensure cleanup runs
    .ensuring(() -> releaseResources());
```

### Lazy Evaluation

`IOPath` is lazy - nothing executes until you call `unsafeRun()` or `runSafe()`:

```java
IOPath<String> effect = Path.io(() -> {
    System.out.println("Side effect!");  // Not printed yet
    return "result";
});

// Still nothing happens
IOPath<Integer> transformed = effect.map(String::length);

// NOW the effect executes
Integer length = transformed.unsafeRun();  // Prints "Side effect!"
```

### When to Use

Use `IOPath` when:
- Performing side effects (file I/O, network, database)
- You need lazy evaluation
- Composing complex effect pipelines
- You want referential transparency

---

## ValidationPath

`ValidationPath<E, A>` wraps `Validated<E, A>` for computations that accumulate errors instead of short-circuiting on the first failure.

### Creation

```java
// Valid value
ValidationPath<List<String>, Integer> valid = Path.valid(42, Semigroups.list());

// Invalid value with errors
ValidationPath<List<String>, Integer> invalid =
    Path.invalid(List.of("Error 1", "Error 2"), Semigroups.list());

// From existing Validated
ValidationPath<String, User> userPath =
    Path.validation(validatedUser, Semigroups.first());
```

The `Semigroup<E>` parameter defines how errors combine when multiple validations fail.

### Core Operations

```java
ValidationPath<List<String>, String> name = Path.valid("Alice", Semigroups.list());

// Transform (same as other paths)
ValidationPath<List<String>, Integer> length = name.map(String::length);

// Chain with via (short-circuits on first error)
ValidationPath<List<String>, String> upper =
    name.via(s -> Path.valid(s.toUpperCase(), Semigroups.list()));
```

### Error Accumulation

The key feature of `ValidationPath` is error accumulation with `zipWithAccum`:

```java
ValidationPath<List<String>, String> nameV = validateName(input.name());
ValidationPath<List<String>, String> emailV = validateEmail(input.email());
ValidationPath<List<String>, Integer> ageV = validateAge(input.age());

// Accumulate ALL errors (does not short-circuit)
ValidationPath<List<String>, User> userV = nameV.zipWithAccum(
    emailV,
    ageV,
    User::new
);

// If name and email both fail, you get BOTH errors
```

Compare with `zipWith` which short-circuits:

```java
// Short-circuits: only first error returned
ValidationPath<List<String>, User> userShortCircuit =
    nameV.zipWith(emailV, ageV, User::new);
```

### Combining Validations

```java
// andAlso combines two validations, accumulating errors
ValidationPath<List<String>, Unit> combined =
    checkNotEmpty(name)
        .andAlso(checkMaxLength(name, 100))
        .andAlso(checkNoSpecialChars(name));

// All three checks run; all errors collected
```

### Extraction

```java
ValidationPath<List<String>, User> path = validateUser(input);
Validated<List<String>, User> validated = path.run();

// Pattern match
String result = validated.fold(
    errors -> "Errors: " + String.join(", ", errors),
    user -> "Valid user: " + user.name()
);
```

### When to Use

Use `ValidationPath` when:
- You need to collect all validation errors, not just the first
- Building form validation or input processing
- Multiple independent checks must all run
- Error messages should be comprehensive

---

## IdPath

`IdPath<A>` wraps `Id<A>`, the identity monad. It always contains a value and never fails.

### Creation

```java
// From a value
IdPath<String> id = Path.id("hello");

// From existing Id
IdPath<User> userPath = Path.idOf(idUser);
```

### Core Operations

```java
IdPath<String> name = Path.id("Alice");

// Transform
IdPath<Integer> length = name.map(String::length);  // Id(5)

// Chain (always succeeds)
IdPath<String> upper = name.via(s -> Path.id(s.toUpperCase()));

// Combine
IdPath<Integer> age = Path.id(25);
IdPath<String> combined = name.zipWith(age, (n, a) -> n + " is " + a);
```

### Extraction

```java
IdPath<String> path = Path.id("hello");

// Get the value directly
String value = path.run().value();  // "hello"

// Or use get()
String value = path.get();  // "hello"
```

### When to Use

Use `IdPath` when:
- You need a trivial path for testing or placeholder purposes
- Writing generic code that works with any path type
- The computation always succeeds with a value

---

## OptionalPath

`OptionalPath<A>` wraps Java's `java.util.Optional<A>`, providing a bridge to the standard library.

### Creation

```java
// From a value
OptionalPath<String> present = Path.present("hello");

// Empty
OptionalPath<String> absent = Path.absent();

// From Java Optional
Optional<User> javaOptional = findUser(id);
OptionalPath<User> userPath = Path.optional(javaOptional);
```

### Core Operations

```java
OptionalPath<String> name = Path.present("Alice");

// Transform
OptionalPath<Integer> length = name.map(String::length);  // Optional[5]

// Chain
OptionalPath<String> upper = name.via(s -> Path.present(s.toUpperCase()));

// Combine
OptionalPath<Integer> age = Path.present(25);
OptionalPath<String> combined = name.zipWith(age, (n, a) -> n + " is " + a);
```

### Extraction

```java
OptionalPath<String> path = Path.present("hello");

// Get underlying Optional
Optional<String> optional = path.run();

// Get with default
String value = path.getOrElse("default");

// Check presence
boolean hasValue = path.isPresent();
```

### Conversion to MaybePath

```java
OptionalPath<User> optPath = Path.optional(findUser(id));

// Convert to MaybePath for richer operations
MaybePath<User> maybePath = optPath.toMaybePath();
```

### When to Use

Use `OptionalPath` when:
- Integrating with Java APIs that return `Optional`
- You prefer `Optional` semantics over `Maybe`
- Bridging between Higher-Kinded-J and standard library code

---

## GenericPath

`GenericPath<F, A>` represents one of higher-kinded-j's distinctive capabilities: the ability to work with *any* monad through a unified API. Where other Java libraries force you to choose between specific effect types, `GenericPath` lets you write code that operates over any monadic structure.

### Creation

```java
// Create with a Kind and its Monad instance
Monad<ListKind.Witness> listMonad = ListMonad.INSTANCE;
Kind<ListKind.Witness, Integer> listKind = ListKindHelper.LIST.widen(List.of(1, 2, 3));

GenericPath<ListKind.Witness, Integer> listPath = Path.generic(listKind, listMonad);
```

### Core Operations

```java
GenericPath<ListKind.Witness, Integer> numbers = Path.generic(listKind, listMonad);

// Transform
GenericPath<ListKind.Witness, String> strings = numbers.map(n -> "n" + n);

// Chain
GenericPath<ListKind.Witness, Integer> doubled = numbers.via(n ->
    Path.generic(ListKindHelper.LIST.widen(List.of(n, n * 2)), listMonad));
```

### Extraction

```java
GenericPath<ListKind.Witness, Integer> path = ...;

// Get the underlying Kind
Kind<ListKind.Witness, Integer> kind = path.run();

// Narrow to concrete type
List<Integer> list = ListKindHelper.LIST.narrow(kind);
```

### When to Use

Use `GenericPath` when:
- You have a custom monad not covered by the built-in path types
- Writing highly generic code that works across multiple monad types
- Experimenting with new effect types

~~~admonish tip title="Extensibility by Design"
`GenericPath` demonstrates the power of higher-kinded types in Java: write your algorithm once, and it works with `Maybe`, `Either`, `List`, `IO`, or any custom monad you create. This is the same abstraction power that makes libraries like Cats and ZIO so flexible in Scala, now available in Java through higher-kinded-j.
~~~

---

## Choosing the Right Path Type

| Scenario | Path Type | Reason |
|----------|-----------|--------|
| Optional value, absence OK | `MaybePath` | Simple, no error info needed |
| Validation with error messages | `EitherPath` | Typed errors, short-circuits |
| Collect ALL validation errors | `ValidationPath` | Error accumulation |
| Exception-throwing APIs | `TryPath` | Captures exceptions |
| Side effects (I/O, network) | `IOPath` | Deferred execution |
| Java Optional integration | `OptionalPath` | Standard library bridge |
| Always succeeds, no errors | `IdPath` | Trivial monad |
| Custom monad types | `GenericPath` | Escape hatch |
| Need referential transparency | `IOPath` | Effects are values |

---

## Summary

| Path Type | Wraps | Error Type | Evaluation |
|-----------|-------|------------|------------|
| `MaybePath<A>` | `Maybe<A>` | None (absence) | Immediate |
| `EitherPath<E, A>` | `Either<E, A>` | `E` (typed) | Immediate |
| `TryPath<A>` | `Try<A>` | `Throwable` | Immediate |
| `IOPath<A>` | `IO<A>` | `Throwable` | Deferred |
| `ValidationPath<E, A>` | `Validated<E, A>` | `E` (accumulated) | Immediate |
| `IdPath<A>` | `Id<A>` | None (always succeeds) | Immediate |
| `OptionalPath<A>` | `Optional<A>` | None (absence) | Immediate |
| `GenericPath<F, A>` | `Kind<F, A>` | Depends on monad | Depends on monad |

Continue to [Composition Patterns](composition.md) for advanced composition techniques.

~~~admonish tip title="See Also"
- [Maybe Monad](../monads/maybe_monad.md) - The underlying type for MaybePath
- [Either Monad](../monads/either_monad.md) - The underlying type for EitherPath
- [Try Monad](../monads/try_monad.md) - The underlying type for TryPath
- [IO Monad](../monads/io_monad.md) - The underlying type for IOPath
- [Validated](../monads/validated_monad.md) - The underlying type for ValidationPath
- [Identity](../monads/identity.md) - The underlying type for IdPath
- [Optional](../monads/optional_monad.md) - The underlying type for OptionalPath
~~~

---

**Previous:** [Capability Interfaces](capabilities.md)
**Next:** [Composition Patterns](composition.md)
