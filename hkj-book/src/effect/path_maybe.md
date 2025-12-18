# MaybePath

`MaybePath<A>` wraps `Maybe<A>` for computations that might produce nothing.
It's the simplest failure mode: either you have a value, or you don't.

~~~admonish info title="What You'll Learn"
- Creating MaybePath instances
- Core operations: map, via, zipWith
- Recovery and filtering
- Extraction patterns
- When to use (and when not to)
~~~

---

## Creation

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

---

## Core Operations

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

---

## Recovery

```java
MaybePath<User> user = Path.maybe(findUser(id))
    .orElse(() -> Path.just(User.guest()));

// Filter (returns Nothing if predicate fails)
MaybePath<Integer> positive = Path.just(42).filter(n -> n > 0);  // Just(42)
MaybePath<Integer> rejected = Path.just(-1).filter(n -> n > 0);  // Nothing
```

---

## Extraction

```java
MaybePath<String> path = Path.just("hello");

Maybe<String> maybe = path.run();
String value = path.getOrElse("default");
String value = path.getOrThrow(() -> new NoSuchElementException());
```

---

## When to Use

`MaybePath` is right when:
- Absence is **normal**, not exceptional
- You don't need to explain *why* the value is missing
- You're modelling optional data (configuration, nullable fields, lookups)

`MaybePath` is wrong when:
- Callers need to know the reason for failure → use [EitherPath](path_either.md)
- You're wrapping code that throws → use [TryPath](path_try.md)

~~~admonish tip title="See Also"
- [Maybe Monad](../monads/maybe_monad.md) - Underlying type for MaybePath
- [OptionalPath](path_optional.md) - Similar, but wraps Java's `Optional`
~~~

---

**Previous:** [Path Types Overview](path_types.md)
**Next:** [EitherPath](path_either.md)
