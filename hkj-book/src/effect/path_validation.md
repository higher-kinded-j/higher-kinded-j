# ValidationPath

`ValidationPath<E, A>` wraps `Validated<E, A>` for computations that
**accumulate** errors instead of short-circuiting on the first failure.

~~~admonish info title="What You'll Learn"
- Creating ValidationPath instances
- Error accumulation with zipWithAccum
- Semigroup for combining errors
- Extraction patterns
- When to use (and when not to)
~~~

---

## Creation

```java
// Valid value
ValidationPath<List<String>, Integer> valid =
    Path.valid(42, Semigroups.list());

// Invalid value with errors
ValidationPath<List<String>, Integer> invalid =
    Path.invalid(List.of("Error 1", "Error 2"), Semigroups.list());

// From existing Validated
ValidationPath<String, User> user =
    Path.validation(validatedUser, Semigroups.first());
```

The `Semigroup<E>` parameter defines how errors combine when multiple
validations fail. Common choices:
- `Semigroups.list()` - concatenate error lists
- `Semigroups.string("; ")` - join strings with separator

---

## Core Operations

```java
ValidationPath<List<String>, String> name =
    Path.valid("Alice", Semigroups.list());

// Transform (same as other paths)
ValidationPath<List<String>, Integer> length = name.map(String::length);

// Chain with via (short-circuits on first error)
ValidationPath<List<String>, String> upper =
    name.via(s -> Path.valid(s.toUpperCase(), Semigroups.list()));
```

---

## Error Accumulation: The Point of It All

The key operation is `zipWithAccum`, which collects **all** errors:

```java
ValidationPath<List<String>, String> nameV = validateName(input.name());
ValidationPath<List<String>, String> emailV = validateEmail(input.email());
ValidationPath<List<String>, Integer> ageV = validateAge(input.age());

// Accumulate ALL errors (does not short-circuit)
ValidationPath<List<String>, User> userV = nameV.zipWith3Accum(
    emailV,
    ageV,
    User::new
);

// If name and email both fail:
// Invalid(["Name too short", "Invalid email format"])
// NOT just Invalid(["Name too short"])
```

Compare with `zipWith`, which short-circuits:

```java
// Short-circuits: only first error returned
ValidationPath<List<String>, User> shortCircuit =
    nameV.zipWith(emailV, ageV, User::new);
```

---

## Combining Validations

```java
// andAlso runs both, accumulating errors, keeping first value if both valid
ValidationPath<List<String>, String> thorough =
    checkNotEmpty(name)
        .andAlso(checkMaxLength(name, 100))
        .andAlso(checkNoSpecialChars(name));
// All three checks run; all errors collected
```

---

## Extraction

```java
ValidationPath<List<String>, User> path = validateUser(input);
Validated<List<String>, User> validated = path.run();

String result = validated.fold(
    errors -> "Errors: " + String.join(", ", errors),
    user -> "Valid user: " + user.name()
);
```

---

## When to Use

`ValidationPath` is right when:
- You want users to see **all** validation errors at once
- Multiple independent checks must all run
- Form validation, batch processing, comprehensive error reports
- Being kind to users matters (it does)

`ValidationPath` is wrong when:
- You only need the first error → use [EitherPath](path_either.md)
- Subsequent validations depend on earlier ones passing → use [EitherPath](path_either.md) with `via`

~~~admonish tip title="See Also"
- [Validated](../monads/validated_monad.md) - Underlying type for ValidationPath
- [EitherPath](path_either.md) - For short-circuit validation
- [Semigroup and Monoid](../functional/semigroup_and_monoid.md) - How errors combine
~~~

---

**Previous:** [IOPath](path_io.md)
**Next:** [IdPath](path_id.md)
