# Effect Path Overview

This chapter covers the fundamental operations of the Effect Path API: creating paths, transforming values, chaining computations, and extracting results.

~~~admonish info title="What You'll Learn"
- How to create Path types using factory methods (`Path.just`, `Path.right`, `Path.tryOf`, `Path.io`)
- Transforming values inside paths with `map`
- Chaining dependent computations with `via` and `flatMap`
- Extracting results with `run`, `getOrElse`, and other terminal operations
- Debugging pipelines with `peek`
~~~

## Creating Paths

The `Path` class provides factory methods for creating all path types:

### MaybePath

```java
// From a value (Just)
MaybePath<String> greeting = Path.just("Hello");

// Empty (Nothing)
MaybePath<String> empty = Path.nothing();

// From an existing Maybe
Maybe<User> maybeUser = userRepository.findById(id);
MaybePath<User> userPath = Path.maybe(maybeUser);

// From a nullable value
MaybePath<String> fromNullable = Path.fromNullable(possiblyNullValue);
```

### EitherPath

```java
// Success value (Right)
EitherPath<String, Integer> success = Path.right(42);

// Error value (Left)
EitherPath<String, Integer> failure = Path.left("Something went wrong");

// From an existing Either
Either<Error, User> either = validateUser(input);
EitherPath<Error, User> userPath = Path.either(either);
```

### TryPath

```java
// Successful value
TryPath<Integer> success = Path.success(42);

// Failed value
TryPath<Integer> failure = Path.failure(new RuntimeException("error"));

// From a computation that may throw
TryPath<Integer> parsed = Path.tryOf(() -> Integer.parseInt(input));

// From an existing Try
Try<Config> tryConfig = loadConfig();
TryPath<Config> configPath = Path.of(tryConfig);
```

### IOPath

```java
// Pure value (no side effects)
IOPath<Integer> pure = Path.ioPure(42);

// Deferred computation
IOPath<String> readFile = Path.io(() -> Files.readString(path));

// From an existing IO
IO<Connection> ioConn = connectToDatabase();
IOPath<Connection> connPath = Path.ioOf(ioConn);
```

---

## Basic Transformations with `map`

All path types support `map` for transforming the success value:

```java
// MaybePath
MaybePath<String> greeting = Path.just("hello");
MaybePath<String> upper = greeting.map(String::toUpperCase);
// → Just("HELLO")

MaybePath<Integer> length = greeting.map(String::length);
// → Just(5)

// Empty paths remain empty
MaybePath<String> empty = Path.nothing();
MaybePath<Integer> emptyLength = empty.map(String::length);
// → Nothing

// EitherPath
EitherPath<String, Integer> number = Path.right(42);
EitherPath<String, String> formatted = number.map(n -> "Value: " + n);
// → Right("Value: 42")

// Errors short-circuit
EitherPath<String, Integer> error = Path.left("error");
EitherPath<String, String> mapped = error.map(n -> "Value: " + n);
// → Left("error")

// TryPath
TryPath<Integer> parsed = Path.tryOf(() -> Integer.parseInt("42"));
TryPath<Integer> doubled = parsed.map(n -> n * 2);
// → Success(84)

// IOPath
IOPath<Integer> io = Path.ioPure(10);
IOPath<Integer> computed = io.map(n -> n * n);
// → IOPath (deferred: 100)
```

---

## Chaining Computations with `via`

The `via` method chains computations where the next step depends on the previous result:

```java
// MaybePath chaining
MaybePath<Order> order = Path.maybe(userRepository.findById(userId))
    .via(user -> Path.maybe(user.getPrimaryAddress()))
    .via(address -> Path.maybe(orderService.getLastOrder(address.getZipCode())));

// Each step only executes if the previous succeeded
// Nothing propagates through the chain

// EitherPath chaining
EitherPath<Error, Invoice> invoice =
    Path.either(validateOrder(input))
        .via(order -> Path.either(calculateTotal(order)))
        .via(total -> Path.either(createInvoice(total)));

// Errors short-circuit - first error propagates

// TryPath chaining
TryPath<Data> result =
    Path.tryOf(() -> readFile(path))
        .via(content -> Path.tryOf(() -> parseJson(content)))
        .via(json -> Path.tryOf(() -> transform(json)));

// Exceptions are captured and propagate

// IOPath chaining
IOPath<Response> response =
    Path.io(() -> establishConnection())
        .via(conn -> Path.io(() -> sendRequest(conn, request)))
        .via(req -> Path.io(() -> readResponse(req)));

// Effects are deferred until run
```

### `via` vs `flatMap`

`via` and `flatMap` are equivalent operations - both perform monadic bind. Use whichever reads better in your context:

```java
// These are equivalent
path.via(x -> nextPath(x))
path.flatMap(x -> nextPath(x))
```

The name `via` was chosen for consistency with the optics Focus DSL, where `via` navigates through lenses and prisms.

---

## Extracting Results

### MaybePath

```java
MaybePath<String> path = Path.just("hello");

// Get the underlying Maybe
Maybe<String> maybe = path.run();

// Get with default
String value = path.getOrElse("default");
// → "hello"

// Get or throw
String value = path.getOrThrow(() -> new NoSuchElementException());
// → "hello"

// Check and handle
if (path.run().isJust()) {
    System.out.println("Got: " + path.run().get());
}
```

### EitherPath

```java
EitherPath<String, Integer> path = Path.right(42);

// Get the underlying Either
Either<String, Integer> either = path.run();

// Pattern match with fold
String result = either.fold(
    error -> "Error: " + error,
    value -> "Value: " + value
);
// → "Value: 42"

// Check and extract
if (either.isRight()) {
    Integer value = either.getRight();
}
```

### TryPath

```java
TryPath<Integer> path = Path.success(42);

// Get the underlying Try
Try<Integer> tryValue = path.run();

// Get with default
Integer value = path.getOrElse(-1);
// → 42

// Check result
if (tryValue.isSuccess()) {
    System.out.println("Success: " + tryValue.get());
} else {
    System.out.println("Failed: " + tryValue.getCause());
}
```

### IOPath

```java
IOPath<String> path = Path.io(() -> Files.readString(Paths.get("file.txt")));

// Execute and get result (may throw)
String content = path.unsafeRun();

// Execute safely, capturing exceptions
Try<String> result = path.runSafe();

// Convert to TryPath for further composition
TryPath<String> tryPath = path.toTryPath();
```

---

## Debugging with `peek`

The `peek` method allows side effects (like logging) without affecting the computation:

```java
EitherPath<Error, User> result =
    Path.either(validateInput(input))
        .peek(valid -> log.debug("Validated input: {}", valid))
        .via(valid -> Path.either(createUser(valid)))
        .peek(user -> log.info("Created user: {}", user.getId()));
```

For MaybePath, peek only executes for Just values:

```java
Path.maybe(findUser(id))
    .peek(user -> log.debug("Found user: {}", user))  // Only logs if found
    .map(User::getEmail);
```

---

## Summary

| Operation | Purpose | Example |
|-----------|---------|---------|
| `Path.just(x)` | Create MaybePath with value | `Path.just("hello")` |
| `Path.nothing()` | Create empty MaybePath | `Path.nothing()` |
| `Path.right(x)` | Create successful EitherPath | `Path.right(42)` |
| `Path.left(e)` | Create failed EitherPath | `Path.left("error")` |
| `Path.success(x)` | Create successful TryPath | `Path.success(42)` |
| `Path.tryOf(f)` | Create TryPath from computation | `Path.tryOf(() -> parse(s))` |
| `Path.ioPure(x)` | Create pure IOPath | `Path.ioPure(42)` |
| `Path.io(f)` | Create IOPath from effect | `Path.io(() -> readFile())` |
| `.map(f)` | Transform success value | `path.map(x -> x * 2)` |
| `.via(f)` | Chain dependent computation | `path.via(x -> nextPath(x))` |
| `.run()` | Extract underlying type | `path.run()` |
| `.getOrElse(d)` | Get with default | `path.getOrElse(0)` |
| `.peek(f)` | Side effect without changing value | `path.peek(x -> log(x))` |

Continue to [Capability Interfaces](capabilities.md) to understand the interface hierarchy.

~~~admonish tip title="Further Reading"
- **Cats Documentation**: [Effect Types](https://typelevel.org/cats-effect/) - Scala's approach to effect management
- **Functional Programming in Scala**: Chapter 13 covers external effects and I/O
~~~

---

**Previous:** [Introduction](ch_intro.md)
**Next:** [Capability Interfaces](capabilities.md)
