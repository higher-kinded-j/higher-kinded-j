# Path Types

This chapter provides detailed coverage of each Path type, their specific operations, and when to use them.

~~~admonish info title="What You'll Learn"
- Detailed API for `MaybePath`, `EitherPath`, `TryPath`, and `IOPath`
- Creation methods, core operations, and extraction patterns for each type
- Recovery and error handling specific to each Path type
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

## Choosing the Right Path Type

| Scenario | Path Type | Reason |
|----------|-----------|--------|
| Optional value, absence OK | `MaybePath` | Simple, no error info needed |
| Validation with error messages | `EitherPath` | Typed errors |
| Exception-throwing APIs | `TryPath` | Captures exceptions |
| Side effects (I/O, network) | `IOPath` | Deferred execution |
| Need error info | `EitherPath` or `TryPath` | Both preserve error details |
| Need referential transparency | `IOPath` | Effects are values |

---

## Summary

| Path Type | Wraps | Error Type | Evaluation |
|-----------|-------|------------|------------|
| `MaybePath<A>` | `Maybe<A>` | None (absence) | Immediate |
| `EitherPath<E, A>` | `Either<E, A>` | `E` (typed) | Immediate |
| `TryPath<A>` | `Try<A>` | `Throwable` | Immediate |
| `IOPath<A>` | `IO<A>` | `Throwable` | Deferred |

Continue to [Composition Patterns](composition.md) for advanced composition techniques.

~~~admonish tip title="See Also"
- [Maybe Monad](../monads/maybe_monad.md) - The underlying type for MaybePath
- [Either Monad](../monads/either_monad.md) - The underlying type for EitherPath
- [Try Monad](../monads/try_monad.md) - The underlying type for TryPath
- [IO Monad](../monads/io_monad.md) - The underlying type for IOPath
~~~

---

**Previous:** [Capability Interfaces](capabilities.md)
**Next:** [Composition Patterns](composition.md)
