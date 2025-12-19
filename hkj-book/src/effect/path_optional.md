# OptionalPath

`OptionalPath<A>` wraps Java's `java.util.Optional<A>`, bridging the
standard library and the Path API.

~~~admonish info title="What You'll Learn"
- Creating OptionalPath instances
- Core operations
- Conversion to other types
- When to use (and when not to)
~~~

---

## Creation

```java
OptionalPath<String> present = Path.present("hello");
OptionalPath<String> absent = Path.absent();
OptionalPath<User> user = Path.optional(repository.findById(id));
```

---

## Core Operations

```java
OptionalPath<String> name = Path.present("Alice");

OptionalPath<Integer> length = name.map(String::length);
OptionalPath<String> upper = name.via(s -> Path.present(s.toUpperCase()));
```

---

## Extraction and Conversion

```java
OptionalPath<String> path = Path.present("hello");

Optional<String> optional = path.run();
String value = path.getOrElse("default");
boolean hasValue = path.isPresent();

// Convert to MaybePath for richer operations
MaybePath<String> maybe = path.toMaybePath();
```

---

## When to Use

`OptionalPath` is right when:
- You're integrating with Java APIs that return `Optional`
- You prefer staying close to standard library semantics
- Bridging between Higher-Kinded-J and existing codebases

`OptionalPath` is wrong when:
- You're not constrained by `Optional` → [MaybePath](path_maybe.md) is slightly richer

~~~admonish tip title="See Also"
- [Optional Monad](../monads/optional_monad.md) - Underlying type for OptionalPath
- [MaybePath](path_maybe.md) - Alternative for optional values
~~~

---

**Previous:** [IdPath](path_id.md)
**Next:** [GenericPath](path_generic.md)
