# EffectPath End-User API Perspective

> **Status**: Living Document (v1.0)
> **Audience**: Java developers consuming the EffectPath API
> **Focus**: What "good" looks like from a user's perspective

## Philosophy

The EffectPath API is designed with a simple principle: **make the common case trivial and the complex case possible**.

Java developers should feel at home immediately. No category theory knowledge required. No HKT ceremony. Just intuitive, fluent composition.

## The Single Entry Point: `Path`

Everything starts with the `Path` factory class:

```java
import org.higherkindedj.hkt.path.Path;

// That's it. One import for most use cases.
```

## User Journey by Experience Level

### Level 1: Beginner Java Developer

**Goal**: Handle optional values and errors without null checks or try-catch.

```java
// Before: Null checks everywhere
User user = userRepo.findById(id);
if (user != null) {
    Profile profile = user.getProfile();
    if (profile != null) {
        return profile.getDisplayName();
    }
}
return "Anonymous";

// After: Fluent composition
String name = Path.maybe(userRepo.findById(id))
    .via(User::getProfile)         // Returns Maybe<Profile>
    .map(Profile::getDisplayName)
    .getOrElse("Anonymous");
```

**Key Methods at This Level**:
- `Path.maybe(value)` - Start with a nullable value
- `Path.attempt(() -> ...)` - Wrap code that might throw
- `.map(f)` - Transform the value
- `.via(f)` - Chain to another Maybe/Either
- `.getOrElse(default)` - Extract with fallback
- `.run()` - Get the underlying Maybe/Either/Try

### Level 2: Intermediate Developer

**Goal**: Handle multiple failure modes, convert between path types.

```java
// Validation pipeline with multiple error types
EitherPath<ValidationError, User> validatedUser = Path.maybe(userId)
    .via(id -> userRepo.findById(id))
    .toEitherPath(ValidationError.notFound("User not found"))
    .via(user -> validateEmail(user))
    .via(user -> validateAge(user))
    .via(user -> validatePermissions(user));

// Handle the result
validatedUser.run().fold(
    error -> handleValidationError(error),
    user -> processUser(user)
);
```

**Key Methods at This Level**:
- `.toEitherPath(error)` - Convert Maybe to Either
- `.toMaybePath()` - Convert Either to Maybe (lossy)
- `.recover(handler)` - Recover from errors
- `.recoverWith(handler)` - Recover with another path
- `.mapError(f)` - Transform error type
- `.fold(onError, onSuccess)` - Pattern match on result

### Level 3: Advanced Developer

**Goal**: Generic programming, reusable composition patterns.

```java
// Generic helper that works with any Chainable path
public <P extends Chainable<User>> P enrichUser(P userPath) {
    return userPath
        .via(user -> enrichWithPreferences(user))
        .via(user -> enrichWithHistory(user));
}

// Use with any path type
MaybePath<User> maybeUser = enrichUser(Path.maybe(user));
EitherPath<Error, User> eitherUser = enrichUser(Path.right(user));
```

**Key Concepts at This Level**:
- Capability interfaces: `Composable`, `Chainable`, `Recoverable`
- Generic constraints: `<P extends Recoverable<E, A>>`
- `GenericPath<F, A>` for custom monads

## API Quick Reference

### Factory Methods

```java
// Maybe paths
Path.maybe(value)                    // Maybe from value
Path.maybeNullable(nullableValue)    // Maybe from nullable
Path.nothing()                       // Empty Maybe

// Either paths
Path.right(value)                    // Success Either
Path.left(error)                     // Failure Either
Path.<Error>right(value)             // When type inference needs help

// Try paths
Path.success(value)                  // Successful Try
Path.failure(throwable)              // Failed Try
Path.attempt(() -> riskyCode())      // Wrap throwing code

// IO paths
Path.io(() -> sideEffect())          // Lazy side effect
Path.delay(() -> expensiveCalc())    // Deferred computation

// From existing types
Path.from(maybe)                     // MaybePath from Maybe
Path.from(either)                    // EitherPath from Either
Path.from(tryValue)                  // TryPath from Try

// From Java stdlib
Path.fromOptional(optional)          // MaybePath from Optional
Path.fromCompletableFuture(future)   // TryPath from CompletableFuture
```

### Composition Methods

```java
// Functor (transform value)
path.map(a -> transform(a))          // A -> B

// Monad (chain effects)
path.via(a -> nextEffect(a))         // A -> Path<B>
path.then(a -> nextEffect(a))        // Alias for via
path.flatMap(a -> nextPath(a))       // A -> Path<B> (returns same path type)

// Sequencing
path.andThen(nextPath)               // Ignore this value, use next
```

### Error Handling Methods

```java
// Recovery
path.recover(error -> defaultValue)           // Recover with value
path.recoverWith(error -> alternativePath)    // Recover with another path
path.orElse(fallbackPath)                     // Use fallback if this fails

// Error transformation
path.mapError(e -> newError)                  // Transform error type

// Filtering
path.filter(predicate)                        // Convert to Nothing/Left if fails
path.filterOrElse(predicate, error)           // Convert with specific error
```

### Extraction Methods (Terminal Operations)

```java
// Get with fallback
path.getOrElse(defaultValue)           // Value or default
path.getOrElse(() -> compute())        // Value or computed default

// Get or throw
path.getOrThrow()                      // Value or RuntimeException
path.getOrThrow(e -> new MyException(e)) // Value or custom exception

// Pattern matching
path.fold(onEmpty, onValue)            // Maybe: Nothing/Just
path.fold(onLeft, onRight)             // Either: Left/Right

// Unwrap to underlying type
path.run()                             // Get the Maybe/Either/Try/IO

// Convert to Java types
path.toOptional()                      // To Optional
path.stream()                          // To Stream (0 or 1 element)
```

### Applicative Methods (Parallel Combination)

```java
// Combine two paths
path1.map2(path2, (a, b) -> combine(a, b))

// Combine three paths
path1.map3(path2, path3, (a, b, c) -> combine(a, b, c))

// Combine many paths (static method)
Path.zip(path1, path2, path3)
    .map((a, b, c) -> combine(a, b, c))
```

### Debugging Methods

```java
// Observe without modifying
path.traced(state -> log.debug("State: {}", state))
path.peek(value -> log.debug("Value: {}", value))

// Pretty printing
path.toString()  // "MaybePath(Just(User(id=123)))"
```

## Common Patterns

### Pattern 1: Null-Safe Navigation

```java
// Navigate through nullable fields
String city = Path.maybe(user)
    .via(User::getAddress)           // Maybe<Address>
    .via(Address::getCity)           // Maybe<String>
    .getOrElse("Unknown");
```

### Pattern 2: Validation Pipeline

```java
// Chain validations, fail fast on first error
Either<ValidationError, User> result = Path.right(formData)
    .via(this::validateUsername)
    .via(this::validateEmail)
    .via(this::validatePassword)
    .via(this::createUser)
    .run();
```

### Pattern 3: Error Accumulation

```java
// Collect all validation errors (use ValidatedPath)
ValidatedPath<List<Error>, User> validated = Path.validated(formData)
    .zipWith(validateUsername(formData), User::withUsername)
    .zipWith(validateEmail(formData), User::withEmail)
    .zipWith(validatePassword(formData), User::withPassword);
```

### Pattern 4: Resource Handling

```java
// Safe resource management
TryPath<String> content = Path.attempt(() -> Files.newInputStream(path))
    .via(stream -> Path.attempt(() -> {
        try (var reader = new BufferedReader(new InputStreamReader(stream))) {
            return reader.lines().collect(joining("\n"));
        }
    }).run());
```

### Pattern 5: Fallback Chain

```java
// Try multiple sources
MaybePath<Config> config = Path.maybe(loadFromEnv())
    .orElse(Path.maybe(loadFromFile()))
    .orElse(Path.maybe(loadFromDefaults()));
```

### Pattern 6: Type Conversion Pipeline

```java
// Start with Maybe, convert to Either, then to Try
Path.maybe(userId)
    .via(id -> userRepo.findById(id))
    .toEitherPath(new UserNotFoundError(userId))
    .via(user -> validateUser(user))
    .toTryPath()
    .via(user -> saveAuditLog(user))
    .fold(
        error -> handleError(error),
        user -> respond(user)
    );
```

### Pattern 7: Parallel Composition

```java
// Fetch data in parallel (semantically)
UserDashboard dashboard = Path.zip(
    Path.attempt(() -> fetchProfile(id)),
    Path.attempt(() -> fetchOrders(id)),
    Path.attempt(() -> fetchPreferences(id))
).map((profile, orders, prefs) ->
    new UserDashboard(profile, orders, prefs)
).getOrThrow();
```

## IDE Experience

### Autocomplete Discoverability

When you type `Path.`, your IDE shows:
```
Path.maybe(...)
Path.maybeNullable(...)
Path.nothing()
Path.right(...)
Path.left(...)
Path.attempt(...)
Path.success(...)
Path.failure(...)
Path.io(...)
Path.from(...)
Path.fromOptional(...)
Path.zip(...)
```

When you have a `MaybePath<User>` and type `.`, you see:
```
.map(...)           // Transform user
.via(...)           // Chain to another Maybe
.then(...)          // Alias for via
.filter(...)        // Filter by predicate
.recover(...)       // Provide fallback
.orElse(...)        // Alternative path
.toEitherPath(...)  // Convert to Either
.getOrElse(...)     // Extract with default
.run()              // Get underlying Maybe
.traced(...)        // Debug observation
```

### Error Messages

Good error messages help developers:

```java
// Type mismatch gives clear guidance
Path.right(user)
    .via(u -> validateEmail(u))  // Returns Either<EmailError, User>
    .via(u -> validateAge(u));   // ERROR: Expected Either<EmailError, ?> but got Either<AgeError, ?>
                                 //        Consider using mapError() to unify error types
```

## What Good Looks Like

### Good: Clear Intent

```java
// Intent is obvious: find user, validate, enrich
EitherPath<AppError, User> result = Path.maybe(userId)
    .via(id -> userRepo.findById(id))
    .toEitherPath(AppError.userNotFound(userId))
    .via(user -> validator.validate(user))
    .via(user -> enricher.enrich(user));
```

### Good: Proper Error Handling

```java
// Errors are handled explicitly, not swallowed
result.fold(
    error -> {
        log.warn("Operation failed: {}", error);
        return errorResponse(error);
    },
    user -> successResponse(user)
);
```

### Good: Type Safety

```java
// Compiler catches mistakes
Path.right(user)
    .map(u -> u.getName())           // OK: String
    .via(name -> validateName(name)) // OK: Either<Error, String>
    .map(name -> name.length());     // OK: Integer
```

### Bad: Losing Error Information

```java
// DON'T: Silent conversion loses error details
MaybePath<User> user = eitherPath.toMaybePath();  // Error is gone!

// DO: Handle error explicitly
MaybePath<User> user = eitherPath
    .traced(either -> either.fold(
        error -> log.warn("Conversion lost error: {}", error),
        _ -> {}
    ))
    .toMaybePath();
```

### Bad: Ignoring Results

```java
// DON'T: Side effect without checking result
Path.attempt(() -> saveToDatabase(user));  // Result ignored!

// DO: Handle the result
Path.attempt(() -> saveToDatabase(user))
    .fold(
        error -> log.error("Save failed", error),
        _ -> log.info("Save succeeded")
    );
```

### Bad: Mixing Paradigms

```java
// DON'T: Break out of path composition
MaybePath<User> userPath = Path.maybe(userId).via(id -> findUser(id));
if (userPath.run().isJust()) {  // Breaking abstraction!
    User user = userPath.run().get();
    // ...
}

// DO: Stay in path composition
Path.maybe(userId)
    .via(id -> findUser(id))
    .fold(
        () -> handleNotFound(),
        user -> handleUser(user)
    );
```

## Migration Guide

### From Raw `Maybe<A>`:

```java
// Before
Maybe<User> maybeUser = userRepo.findById(id);
Maybe<String> maybeName = maybeUser.flatMap(u ->
    u.getProfile().map(Profile::getName));
String name = maybeName.getOrElse("Unknown");

// After
String name = Path.from(userRepo.findById(id))
    .via(User::getProfile)
    .map(Profile::getName)
    .getOrElse("Unknown");
```

### From `try-catch`:

```java
// Before
String content;
try {
    content = Files.readString(path);
} catch (IOException e) {
    content = "default";
}

// After
String content = Path.attempt(() -> Files.readString(path))
    .getOrElse("default");
```

### From `Optional<A>`:

```java
// Before
Optional<User> optUser = userRepo.findById(id);
String name = optUser
    .flatMap(u -> Optional.ofNullable(u.getProfile()))
    .map(Profile::getName)
    .orElse("Unknown");

// After
String name = Path.fromOptional(userRepo.findById(id))
    .viaNullable(User::getProfile)
    .map(Profile::getName)
    .getOrElse("Unknown");
```

## Summary

The EffectPath API provides:

1. **Single import** - `Path` is your entry point
2. **Intuitive methods** - `map`, `via`, `recover`, `getOrElse`
3. **Type safety** - Compiler catches mistakes
4. **IDE-friendly** - Autocomplete guides you
5. **Progressive disclosure** - Simple things first, advanced when needed
6. **Consistent vocabulary** - Same `via`/`then` as FocusPath optics
