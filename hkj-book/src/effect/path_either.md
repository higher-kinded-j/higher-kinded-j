# EitherPath

`EitherPath<E, A>` wraps `Either<E, A>` for computations with typed errors.
The left side carries failure; the right side carries success. (Right is
right, as the mnemonic goes.)

~~~admonish info title="What You'll Learn"
- Creating EitherPath instances
- Core operations and error handling
- Bifunctor operations
- Extraction patterns
- When to use (and when not to)
~~~

---

## Creation

```java
// Success
EitherPath<Error, Integer> success = Path.right(42);

// Failure
EitherPath<Error, Integer> failure = Path.left(new ValidationError("invalid"));

// From existing Either
EitherPath<Error, User> user = Path.either(validateUser(input));
```

---

## Core Operations

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

---

## Error Handling

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

---

## Bifunctor Operations

Transform both sides simultaneously:

```java
EitherPath<String, Integer> original = Path.right(42);

EitherPath<Integer, String> transformed = original.bimap(
    String::length,      // Transform error
    n -> "Value: " + n   // Transform success
);
```

---

## Extraction

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

---

## When to Use

`EitherPath` is right when:
- Errors carry meaningful, typed information
- Different errors need different handling
- You're building validation pipelines (with short-circuit semantics)
- You want to transform errors as they propagate

`EitherPath` is wrong when:
- You need to collect *all* errors → use [ValidationPath](path_validation.md)
- Absence isn't really an error → use [MaybePath](path_maybe.md)

~~~admonish tip title="See Also"
- [Either Monad](../monads/either_monad.md) - Underlying type for EitherPath
- [ValidationPath](path_validation.md) - For accumulating errors
~~~

---

**Previous:** [MaybePath](path_maybe.md)
**Next:** [TryPath](path_try.md)
