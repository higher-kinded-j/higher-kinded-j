# TryPath

`TryPath<A>` wraps `Try<A>` for computations that might throw exceptions.
It bridges the gap between Java's exception-based world and functional
composition.

~~~admonish info title="What You'll Learn"
- Creating TryPath instances
- Core operations
- Exception-based error handling
- Extraction patterns
- When to use (and when not to)
~~~

---

## Creation

```java
// Successful value
TryPath<Integer> success = Path.success(42);

// Failed value
TryPath<Integer> failure = Path.failure(new RuntimeException("oops"));

// From computation that may throw
TryPath<Integer> parsed = Path.tryOf(() -> Integer.parseInt(input));

// From existing Try
TryPath<Config> config = Path.tryPath(loadConfigTry());
```

---

## Core Operations

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

---

## Error Handling

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

---

## Extraction

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

---

## When to Use

`TryPath` is right when:
- You're wrapping APIs that throw exceptions
- The specific exception type matters for recovery
- You want exception-safe composition without try-catch blocks
- Interoperating with legacy code

`TryPath` is wrong when:
- You want typed errors (not just `Throwable`) → use [EitherPath](path_either.md)
- The code doesn't throw → use [MaybePath](path_maybe.md) or [EitherPath](path_either.md)

~~~admonish tip title="See Also"
- [Try Monad](../monads/try_monad.md) - Underlying type for TryPath
- [EitherPath](path_either.md) - For typed errors
~~~

---

**Previous:** [EitherPath](path_either.md)
**Next:** [IOPath](path_io.md)
