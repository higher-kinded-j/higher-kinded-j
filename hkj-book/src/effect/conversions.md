# Type Conversions

This chapter covers converting between different Path types, lifting values into paths, and extracting values with terminal operations.

~~~admonish info title="What You'll Learn"
- Converting between Path types: `MaybePath` ↔ `EitherPath` ↔ `TryPath` ↔ `ValidationPath`
- Converting to and from `IdPath`, `OptionalPath`, and `GenericPath`
- Lifting values into Path types with factory methods
- Terminal operations for extracting results
- Best practices for conversion at service boundaries
~~~

## Conversion Overview

The Path API supports rich conversions between all path types. Some conversions preserve all information; others require additional context (like an error value when converting from `MaybePath` to `EitherPath`).

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                           PATH TYPE CONVERSIONS                                  │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ERROR-HANDLING PATHS                                                            │
│  ────────────────────                                                            │
│    MaybePath ←──────────────────────────────────────────────→ EitherPath         │
│       │     toEitherPath(error)  /  toMaybePath()                  │             │
│       │                                                            │             │
│    TryPath ←────────────────────────────────────────────────→ EitherPath         │
│       │     toEitherPath(mapper) /  toTryPath()                    │             │
│       │                                                            │             │
│    TryPath ←────── toMaybePath() ────────────────────────────→ MaybePath         │
│       │                                                                          │
│    IOPath ──────── toTryPath() ──────────────────────────────→ TryPath           │
│                                                                                  │
│  VALIDATION PATHS                                                                │
│  ────────────────                                                                │
│    EitherPath ←─────────────────────────────────────────────→ ValidationPath     │
│              toValidationPath() / toEitherPath()                                 │
│                                                                                  │
│    TryPath ─────── toValidationPath(mapper) ────────────────→ ValidationPath     │
│                                                                                  │
│  UTILITY PATHS                                                                   │
│  ─────────────                                                                   │
│    IdPath ←──────── toIdPath() / toMaybePath() ─────────────→ MaybePath          │
│                                                                                  │
│    OptionalPath ←── toOptionalPath() / toMaybePath() ───────→ MaybePath          │
│                                                                                  │
│    GenericPath ←─── Wraps any Kind<F, A> with Monad instance                     │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
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

## ValidationPath Conversions

### EitherPath → ValidationPath

Convert to accumulating validation mode:

```java
EitherPath<String, Integer> eitherValue = Path.right(42);

// Convert to ValidationPath (preserves success/failure)
ValidationPath<String, Integer> validationValue = eitherValue.toValidationPath();

// Now can use accumulating operations
ValidationPath<String, Integer> other = Path.valid(10);
ValidationPath<String, Integer> combined = validationValue.zipWithAccum(
    other,
    Integer::sum,
    (e1, e2) -> e1 + "; " + e2
);
```

### ValidationPath → EitherPath

Convert back to short-circuiting mode:

```java
ValidationPath<List<String>, User> validated = validateUser(input);

// Convert to EitherPath for chaining
EitherPath<List<String>, User> either = validated.toEitherPath();

// Now can use via() for dependent operations
EitherPath<List<String>, Order> order = either
    .via(user -> createOrder(user));
```

### TryPath → ValidationPath

Convert exceptions to validation errors:

```java
TryPath<Config> tryConfig = Path.tryOf(() -> loadConfig());

// Transform exception to error type
ValidationPath<String, Config> validConfig =
    tryConfig.toValidationPath(ex -> "Config error: " + ex.getMessage());
```

### When to Convert

Convert `EitherPath` to `ValidationPath` when:

- You need to combine multiple independent validations
- You want to accumulate all errors, not just the first

Convert `ValidationPath` to `EitherPath` when:

- You need to chain dependent operations with `via`
- You want fail-fast behaviour for the next step

---

## IdPath Conversions

`IdPath` wraps pure values with no failure case. Conversions are straightforward:

### IdPath → MaybePath

```java
IdPath<String> idValue = Path.id("hello");

// Always becomes Just (IdPath cannot fail)
MaybePath<String> maybe = idValue.toMaybePath();
// → Just("hello")
```

### MaybePath → IdPath

```java
MaybePath<String> maybe = Path.just("hello");

// Requires a default for Nothing case
IdPath<String> id = maybe.toIdPath("default");
// → Id("hello")

MaybePath<String> nothing = Path.nothing();
IdPath<String> idDefault = nothing.toIdPath("default");
// → Id("default")
```

### IdPath Use Cases

`IdPath` is useful when:

- Working with generic code that expects a path type
- You have a pure value but need path operations (`map`, `via`)
- Testing monadic code with known values

---

## OptionalPath Conversions

`OptionalPath` bridges Java's `java.util.Optional` with the Path API.

### OptionalPath ↔ MaybePath

```java
// From Optional
Optional<String> javaOpt = Optional.of("hello");
OptionalPath<String> optPath = Path.optional(javaOpt);

// To MaybePath
MaybePath<String> maybe = optPath.toMaybePath();

// From MaybePath
MaybePath<String> maybe2 = Path.just("world");
OptionalPath<String> optPath2 = maybe2.toOptionalPath();

// To Optional
Optional<String> javaOpt2 = optPath2.run();
```

### OptionalPath → EitherPath

```java
OptionalPath<User> optUser = Path.optional(findUser(id));

// Provide error for empty case
EitherPath<String, User> either = optUser.toEitherPath("User not found");
```

### When to Use OptionalPath

Use `OptionalPath` when:

- Integrating with Java APIs that return `Optional`
- You want path operations on Optional values
- Bridging between Java stdlib and higher-kinded-j

---

## GenericPath Conversions

`GenericPath` wraps any `Kind<F, A>` with a `Monad` instance, providing an escape hatch for custom types.

### Creating GenericPath

```java
// Wrap any Kind with its Monad instance
Kind<MaybeKind.Witness, String> maybeKind = MaybeKind.widen(Maybe.just("hello"));
GenericPath<MaybeKind.Witness, String> generic = Path.generic(
    maybeKind,
    MaybeMonad.INSTANCE
);
```

### Using GenericPath

```java
// All standard path operations work
GenericPath<MaybeKind.Witness, Integer> mapped = generic.map(String::length);

GenericPath<MaybeKind.Witness, String> chained = generic.via(s ->
    Path.generic(MaybeKind.widen(Maybe.just(s.toUpperCase())), MaybeMonad.INSTANCE)
);

// Extract the underlying Kind
Kind<MaybeKind.Witness, String> underlying = generic.runKind();
```

### When to Use GenericPath

Use `GenericPath` when:

- Working with custom monad types not covered by specific Path types
- Writing generic code that works with any monad
- You need path operations for a third-party `Kind` type

~~~admonish note title="GenericPath Limitations"
`GenericPath` provides `Chainable` operations but recovery operations depend on the underlying monad supporting error handling.
~~~

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

### Lifting to ValidationPath

```java
// Valid value
ValidationPath<String, Integer> valid = Path.valid(42);

// Invalid value
ValidationPath<String, Integer> invalid = Path.invalid("Must be positive");

// From existing Validated
Validated<String, User> validated = validateUser(input);
ValidationPath<String, User> path = Path.validation(validated);
```

### Lifting to IdPath

```java
// Wrap a pure value
IdPath<String> id = Path.id("hello");

// From existing Id
Id<Integer> idValue = Id.of(42);
IdPath<Integer> idPath = Path.idOf(idValue);
```

### Lifting to OptionalPath

```java
// From Optional
OptionalPath<String> present = Path.optional(Optional.of("hello"));
OptionalPath<String> empty = Path.optional(Optional.empty());

// From nullable value
OptionalPath<String> fromNullable = Path.optionalOfNullable(possiblyNull);
```

### Lifting to GenericPath

```java
// Wrap any Kind with its Monad
Kind<ListKind.Witness, Integer> listKind = ListKind.widen(List.of(1, 2, 3));
GenericPath<ListKind.Witness, Integer> genericList = Path.generic(listKind, ListMonad.INSTANCE);
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

### ValidationPath Extraction

```java
ValidationPath<List<String>, User> path = validateUser(input);

// Get underlying Validated
Validated<List<String>, User> validated = path.run();

// Pattern match with fold
String result = validated.fold(
    errors -> "Errors: " + errors,
    user -> "Valid: " + user.name()
);

// Check state
boolean isValid = validated.isValid();
boolean isInvalid = validated.isInvalid();
```

### IdPath Extraction

```java
IdPath<String> path = Path.id("hello");

// Get underlying Id
Id<String> id = path.run();

// Get the value (always succeeds)
String value = id.value();
// or
String value = path.get();
```

### OptionalPath Extraction

```java
OptionalPath<String> path = Path.optional(Optional.of("hello"));

// Get underlying Optional
Optional<String> opt = path.run();

// Get or default
String value = opt.orElse("default");

// Get or throw
String value = opt.orElseThrow(() -> new NoSuchElementException());
```

### GenericPath Extraction

```java
GenericPath<MaybeKind.Witness, String> path = Path.generic(
    MaybeKind.widen(maybe), MaybeMonad.INSTANCE);

// Get underlying Kind
Kind<MaybeKind.Witness, String> kind = path.runKind();

// Narrow to concrete type
Maybe<String> maybe = MaybeKind.narrow(kind);
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

### Error-Handling Path Conversions

| From | To | Method | Notes |
|------|-----|--------|-------|
| MaybePath | EitherPath | `toEitherPath(error)` | Nothing → Left |
| EitherPath | MaybePath | `toMaybePath()` | Left → Nothing |
| TryPath | EitherPath | `toEitherPath(mapper)` | Exception → Left |
| TryPath | MaybePath | `toMaybePath()` | Failure → Nothing |
| EitherPath | TryPath | `toTryPath()` | Left → RuntimeException |
| IOPath | TryPath | `toTryPath()` | Executes the IO |

### Validation Path Conversions

| From | To | Method | Notes |
|------|-----|--------|-------|
| EitherPath | ValidationPath | `toValidationPath()` | Preserves success/failure |
| ValidationPath | EitherPath | `toEitherPath()` | Preserves valid/invalid |
| TryPath | ValidationPath | `toValidationPath(mapper)` | Exception → Invalid |

### Utility Path Conversions

| From | To | Method | Notes |
|------|-----|--------|-------|
| IdPath | MaybePath | `toMaybePath()` | Always Just |
| MaybePath | IdPath | `toIdPath(default)` | Nothing → default value |
| OptionalPath | MaybePath | `toMaybePath()` | Empty → Nothing |
| MaybePath | OptionalPath | `toOptionalPath()` | Nothing → Empty |
| OptionalPath | EitherPath | `toEitherPath(error)` | Empty → Left |
| Any Kind | GenericPath | `Path.generic(kind, monad)` | Universal wrapper |

Continue to [Patterns and Recipes](patterns.md) for real-world usage patterns.

~~~admonish tip title="See Also"
- [Natural Transformation](../functional/natural_transformation.md) - The concept behind converting between type constructors
~~~

---

**Previous:** [Composition Patterns](composition.md)
**Next:** [Patterns and Recipes](patterns.md)
