# Type Conversions

This chapter covers converting between different Path types, lifting values into paths, and extracting values with terminal operations.

~~~admonish info title="What You'll Learn"
- Converting between Path types: `MaybePath` ↔ `EitherPath` ↔ `TryPath`
- Lifting values into Path types with factory methods
- Terminal operations for extracting results
- Best practices for conversion at service boundaries
~~~

## Conversion Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          PATH TYPE CONVERSIONS                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│    MaybePath ─────────────────────────────────────────────> EitherPath      │
│       │            toEitherPath(errorSupplier)                  │           │
│       │                                                         │           │
│       │ <───────────────────────────────────────────────────────│           │
│       │                  toMaybePath()                          │           │
│       │                                                         │           │
│       │                                                         │           │
│    TryPath ──────────────────────────────────────────────> EitherPath       │
│       │            toEitherPath(exMapper)                       │           │
│       │                                                         │           │
│       │ <───────────────────────────────────────────────────────│           │
│       │                  toTryPath()                            │           │
│       │                                                         │           │
│       │                                                         │           │
│    TryPath ─────────────────────────────────────────────> MaybePath         │
│       │            toMaybePath()                                │           │
│       │                                                         │           │
│       │                                                         │           │
│    IOPath ──────────────────────────────────────────────> TryPath           │
│                    toTryPath()                                              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## MaybePath Conversions

### MaybePath → EitherPath

Convert absence to a typed error:

```java
MaybePath<User> maybeUser = Path.maybe(findUser(id));

// Provide error for Nothing case
EitherPath<String, User> withError =
    maybeUser.toEitherPath("User not found");

// With lazy error
EitherPath<UserError, User> withLazyError =
    maybeUser.toEitherPath(() -> new UserError("User " + id + " not found"));
```

This is useful when:
- An optional value becomes a required value
- You need to propagate error information downstream

```java
// Service that returns Maybe internally but Either externally
public EitherPath<Error, User> getUserOrError(String id) {
    return Path.maybe(userRepository.findById(id))
        .toEitherPath(() -> new Error.UserNotFound(id));
}
```

### EitherPath → MaybePath

Discard error information:

```java
EitherPath<String, User> eitherUser = Path.either(validateUser(input));

// Errors become Nothing
MaybePath<User> maybeUser = eitherUser.toMaybePath();
```

This is useful when:
- You only care about success/failure, not the error details
- Integrating with APIs that expect Maybe

---

## TryPath Conversions

### TryPath → EitherPath

Convert exceptions to typed errors:

```java
TryPath<Config> tryConfig = Path.tryOf(() -> loadConfig());

// Keep the exception as the error type
EitherPath<Throwable, Config> withException =
    tryConfig.toEitherPath(ex -> ex);

// Transform exception to your error type
EitherPath<ConfigError, Config> withTypedError =
    tryConfig.toEitherPath(ex -> new ConfigError("Failed to load: " + ex.getMessage()));

// Extract just the message
EitherPath<String, Config> withMessage =
    tryConfig.toEitherPath(Throwable::getMessage);
```

### TryPath → MaybePath

Failures become Nothing:

```java
TryPath<Integer> parsed = Path.tryOf(() -> Integer.parseInt(input));

// Failure → Nothing, Success → Just
MaybePath<Integer> maybeParsed = parsed.toMaybePath();

// Use case: optional parsing
MaybePath<Integer> port = Path.tryOf(() -> Integer.parseInt(config.get("port")))
    .toMaybePath()
    .orElse(() -> Path.just(8080));  // Default port
```

### EitherPath → TryPath

Wrap error as exception:

```java
EitherPath<String, User> eitherUser = validateUser(input);

// Error becomes RuntimeException
TryPath<User> tryUser = eitherUser.toTryPath();
```

---

## IOPath Conversions

### IOPath → TryPath

Execute the IO and capture the result:

```java
IOPath<Data> ioData = Path.io(() -> fetchFromNetwork());

// Execute and capture in Try
TryPath<Data> tryData = ioData.toTryPath();
// The IO has been executed at this point!
```

~~~admonish warning title="IO Execution"
`toTryPath()` executes the IO immediately. The result is no longer deferred.
~~~

### IOPath Safe Execution

For explicit control over execution:

```java
IOPath<Data> io = Path.io(() -> fetchData());

// Execute safely (captures exceptions)
Try<Data> result = io.runSafe();

// Then convert to path if needed
TryPath<Data> tryPath = Path.of(result);
```

---

## Lifting Values

### Lifting to MaybePath

```java
// From a value
MaybePath<String> just = Path.just("hello");

// From Nothing
MaybePath<String> nothing = Path.nothing();

// From nullable
String nullable = possiblyNullValue();
MaybePath<String> maybe = Path.fromNullable(nullable);

// Conditional lifting
MaybePath<Integer> validated = value > 0
    ? Path.just(value)
    : Path.nothing();
```

### Lifting to EitherPath

```java
// Success
EitherPath<Error, Integer> success = Path.right(42);

// Failure
EitherPath<Error, Integer> failure = Path.left(new Error("failed"));

// Conditional lifting
EitherPath<String, Integer> validated = value > 0
    ? Path.right(value)
    : Path.left("Value must be positive");
```

### Lifting to TryPath

```java
// Success
TryPath<Integer> success = Path.success(42);

// Failure
TryPath<Integer> failure = Path.failure(new RuntimeException("error"));

// From computation
TryPath<Config> config = Path.tryOf(() -> loadConfig());
```

### Lifting to IOPath

```java
// Pure value (no side effects)
IOPath<Integer> pure = Path.ioPure(42);

// Deferred computation
IOPath<String> deferred = Path.io(() -> readFile());
```

---

## Terminal Operations

### MaybePath Extraction

```java
MaybePath<String> path = Path.just("hello");

// Get underlying Maybe
Maybe<String> maybe = path.run();

// Get or default
String value = path.getOrElse("default");

// Get or compute default
String value = path.getOrElse(() -> computeDefault());

// Get or throw
String value = path.getOrThrow(() -> new NoSuchElementException());

// Check presence
boolean hasValue = path.run().isJust();
```

### EitherPath Extraction

```java
EitherPath<String, Integer> path = Path.right(42);

// Get underlying Either
Either<String, Integer> either = path.run();

// Pattern match with fold
String result = either.fold(
    error -> "Error: " + error,
    value -> "Value: " + value
);

// Get success (throws if Left)
Integer value = either.getRight();

// Get error (throws if Right)
String error = either.getLeft();

// Check state
boolean isSuccess = either.isRight();
```

### TryPath Extraction

```java
TryPath<Integer> path = Path.success(42);

// Get underlying Try
Try<Integer> tryValue = path.run();

// Get or default
Integer value = path.getOrElse(-1);

// Get or compute
Integer value = path.getOrElse(() -> computeDefault());

// Get (may throw)
Integer value = tryValue.get();

// Check state
boolean succeeded = tryValue.isSuccess();

// Get exception (if failure)
Throwable cause = tryValue.getCause();
```

### IOPath Extraction

```java
IOPath<String> path = Path.io(() -> readFile());

// Execute (may throw)
String result = path.unsafeRun();

// Execute safely
Try<String> result = path.runSafe();

// Convert to Try for further composition
TryPath<String> tryPath = path.toTryPath();
```

---

## Conversion Chains

Real code often chains multiple conversions:

```java
// Start with Maybe, end with Either with error handling
EitherPath<ServiceError, Order> processOrder(String userId, OrderInput input) {
    return Path.maybe(userRepository.findById(userId))         // MaybePath<User>
        .toEitherPath(() -> new ServiceError.UserNotFound())   // EitherPath<ServiceError, User>
        .via(user -> Path.tryOf(() -> validateOrder(input))    // Chain TryPath
            .toEitherPath(ServiceError.ValidationFailed::new)) // Convert to EitherPath
        .via(validated -> Path.either(createOrder(user, validated)));
}
```

---

## Best Practices

### Convert at Boundaries

Convert at service boundaries, not throughout:

```java
// Good: Convert once at the boundary
public EitherPath<Error, User> getUser(String id) {
    return Path.maybe(repository.findById(id))  // Internal Maybe
        .toEitherPath(() -> Error.notFound(id)); // Convert at boundary
}

// Avoid: Converting back and forth
public EitherPath<Error, User> getUser(String id) {
    return Path.maybe(repository.findById(id))
        .toEitherPath(() -> Error.notFound(id))
        .toMaybePath()  // Why convert back?
        .toEitherPath(() -> Error.notFound(id)); // And forth again?
}
```

### Match Error Granularity

Choose the right error type for the layer:

```java
// Repository: Maybe (absence is normal)
public Maybe<User> findById(String id) { ... }

// Service: Either with domain errors
public EitherPath<UserError, User> getUserById(String id) {
    return Path.maybe(repository.findById(id))
        .toEitherPath(() -> UserError.NOT_FOUND);
}

// Controller: Either with HTTP-friendly errors
public EitherPath<HttpError, UserDto> getUser(String id) {
    return userService.getUserById(id)
        .mapError(this::toHttpError)
        .map(UserDto::from);
}
```

---

## Summary

| From | To | Method | Notes |
|------|-----|--------|-------|
| MaybePath | EitherPath | `toEitherPath(error)` | Nothing → Left |
| EitherPath | MaybePath | `toMaybePath()` | Left → Nothing |
| TryPath | EitherPath | `toEitherPath(mapper)` | Exception → Left |
| TryPath | MaybePath | `toMaybePath()` | Failure → Nothing |
| EitherPath | TryPath | `toTryPath()` | Left → RuntimeException |
| IOPath | TryPath | `toTryPath()` | Executes the IO |

Continue to [Patterns and Recipes](patterns.md) for real-world usage patterns.

~~~admonish tip title="See Also"
- [Natural Transformation](../functional/natural_transformation.md) - The concept behind converting between type constructors
~~~

---

**Previous:** [Composition Patterns](composition.md)
**Next:** [Patterns and Recipes](patterns.md)
