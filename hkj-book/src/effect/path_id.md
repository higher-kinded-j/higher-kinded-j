# IdPath

`IdPath<A>` wraps `Id<A>`, the identity monad. It always contains a value
and never fails. This sounds useless until you need it.

~~~admonish info title="What You'll Learn"
- Creating IdPath instances
- Core operations
- Use cases for the identity path
- When to use (and when not to)
~~~

---

## Creation

```java
IdPath<String> id = Path.id("hello");
IdPath<User> fromId = Path.idPath(idUser);
```

---

## Core Operations

```java
IdPath<String> name = Path.id("Alice");

IdPath<Integer> length = name.map(String::length);  // Id(5)
IdPath<String> upper = name.via(s -> Path.id(s.toUpperCase()));
IdPath<String> combined = name.zipWith(Path.id(25), (n, a) -> n + " is " + a);
```

---

## Extraction

```java
IdPath<String> path = Path.id("hello");
String value = path.run().value();  // "hello"
String value = path.get();          // "hello"
```

---

## When to Use

`IdPath` is right when:
- You're writing generic code that works over any Path type
- Testing monadic code with known, predictable values
- You need a "no-op" Path that always succeeds
- Satisfying a type parameter that demands a Path

`IdPath` is wrong when:
- Failure is possible → you need one of the other types

~~~admonish example title="Generic Code Example"
```java
// Works with any Path type
<P extends Path<P, A>, A> P process(P path) {
    return path.map(this::transform);
}

// Test with IdPath (no failures to worry about)
IdPath<String> testPath = Path.id("test");
IdPath<String> result = process(testPath);
```
~~~

~~~admonish tip title="See Also"
- [Identity](../monads/identity.md) - Underlying type for IdPath
~~~

---

**Previous:** [ValidationPath](path_validation.md)
**Next:** [OptionalPath](path_optional.md)
