# Common Compiler Errors

Java's type inference works well for most Effect Path usage, but generic-heavy code occasionally produces confusing compiler messages. This page documents the five most common errors, what causes them, and how to fix them.

~~~admonish info title="What You'll Learn"
- How to fix type inference failures when creating Path instances
- How to resolve type mismatches when mixing Path types in a chain
- How to handle `via` signature errors and lambda type issues
- How to fix error type mismatches across chain steps
~~~

---

## 1. "Cannot infer type arguments for Path.right(...)"

**The error:**

```
error: cannot infer type arguments for right(A)
    return Path.right(user);
               ^
  reason: cannot infer type-variable(s) E
```

**The trigger:**

```java
EitherPath<AppError, User> findUser(String id) {
    User user = repository.findById(id);
    return Path.right(user);  // compiler cannot infer E
}
```

Java can infer the success type `A` from the argument, but the error type `E` has no value to infer from. The return type provides a hint, but inference sometimes fails, particularly inside lambdas or when the return type is not directly visible.

**The fix:** add explicit type parameters.

```java
return Path.<AppError, User>right(user);
```

This is the most common compiler error with Effect Path. It happens any time you create a "happy path" value where the error type has no corresponding argument.

~~~admonish note title="When Inference Succeeds"
Inference works when the context is unambiguous:

```java
// Direct assignment: return type provides the hint
EitherPath<AppError, User> path = Path.right(user);  // usually works

// Inside via: previous step constrains E
.via(id -> Path.right(lookupUser(id)))  // usually works

// Inside a lambda with unclear return type
.via(id -> {
    if (found) return Path.right(user);     // may fail
    return Path.left(new NotFound(id));     // E inferred here
})
```

When in doubt, add the type witness. It costs nothing at runtime.
~~~

---

## 2. "Incompatible types: MaybePath cannot be converted to EitherPath"

**The error:**

```
error: incompatible types: MaybePath<User> cannot be converted to EitherPath<AppError,User>
    .via(id -> Path.maybe(findUser(id)))
                   ^
```

**The trigger:**

```java
EitherPath<AppError, User> result =
    Path.<AppError, String>right(userId)
        .via(id -> Path.maybe(findUser(id)))   // returns MaybePath, not EitherPath
        .map(User::name);
```

`via` expects the function to return the *same* Path kind. An `EitherPath` chain requires `via` to return an `EitherPath`, not a `MaybePath`.

**The fix:** convert at the boundary using `toEitherPath`.

```java
EitherPath<AppError, User> result =
    Path.<AppError, String>right(userId)
        .via(id -> Path.maybe(findUser(id))
            .toEitherPath(new AppError.UserNotFound(id)))   // MaybePath -> EitherPath
        .map(User::name);
```

The `toEitherPath` method converts `Nothing` to a `Left` with the error you provide.

### Common conversions

| From | To | Method |
|------|----|--------|
| `MaybePath<A>` | `EitherPath<E, A>` | `.toEitherPath(errorValue)` |
| `TryPath<A>` | `EitherPath<E, A>` | `.toEitherPath(exceptionMapper)` |
| `EitherPath<E, A>` | `MaybePath<A>` | `.toMaybePath()` |
| `ValidationPath<E, A>` | `EitherPath<E, A>` | `.toEitherPath()` |

---

## 3. "Method via is not applicable for the arguments"

**The error:**

```
error: method via in class EitherPath<E,A> cannot be applied to given types;
    .via(this::processOrder)
         ^
  required: Function<? super Order, ? extends Chainable<B>>
  found: method reference this::processOrder
```

**The trigger:**

```java
// processOrder returns the wrong type
String processOrder(Order order) {    // returns String, not a Path
    return order.id();
}

Path.<AppError, Order>right(order)
    .via(this::processOrder);         // via needs a Path-returning function
```

`via` (the Effect Path equivalent of `flatMap`) requires the function to return a `Chainable`, which all Path types implement. If your function returns a plain value, use `map` instead.

**The fix:** use `map` for plain transformations, `via` for Path-returning functions.

```java
// For plain transformations: use map
Path.<AppError, Order>right(order)
    .map(this::processOrder);              // map: A -> B

// For Path-returning functions: use via
Path.<AppError, Order>right(order)
    .via(this::validateAndProcessOrder);   // via: A -> Path<B>
```

**Rule of thumb:**
- `map`: your function takes `A` and returns `B`
- `via`: your function takes `A` and returns a `Path<B>` (any Path type that matches the chain)

---

## 4. "No suitable method found for map(...)"

**The error:**

```
error: no suitable method found for map((<lambda>))
    .map(order -> {
         ^
  method EitherPath.map(Function<? super Order, ? extends B>) is not applicable
```

**The trigger:**

```java
Path.<AppError, Order>right(order)
    .map(order -> {
        if (order.isValid()) {
            return order.total();    // returns Double
        }
        // missing return: compiler cannot determine B
    });
```

This typically happens when:
- A lambda has branches with different return types (or a missing branch)
- The lambda parameter type cannot be inferred in a complex chain

**The fix:** ensure all branches return the same type, or add explicit parameter types.

```java
// Fix 1: ensure all branches return the same type
.map(order -> {
    if (order.isValid()) {
        return order.total();
    }
    return 0.0;                  // all branches return Double
})

// Fix 2: add explicit types when inference fails
.map((Order order) -> order.total())
```

In long chains where inference struggles, extracting the lambda into a named method often resolves the issue:

```java
private Double extractTotal(Order order) {
    return order.isValid() ? order.total() : 0.0;
}

// Method reference: no inference needed
.map(this::extractTotal)
```

---

## 5. "Type argument E is not within bounds"

**The error:**

```
error: incompatible types: EitherPath<String,User> cannot be converted to
    EitherPath<AppError,User>
    .via(id -> lookupUser(id))
               ^
```

**The trigger:**

```java
EitherPath<AppError, String> validated = validateInput(input);

// lookupUser returns EitherPath<String, User> -- wrong error type
EitherPath<String, User> lookupUser(String id) {
    return id.isEmpty()
        ? Path.left("User not found")     // error type is String
        : Path.right(new User(id));
}

validated.via(id -> lookupUser(id));       // AppError vs String mismatch
```

All steps in an `EitherPath` chain must share the same error type `E`. If one step uses `String` and another uses `AppError`, the compiler rejects the chain.

**The fix:** either unify the error type, or use `mapError` to convert.

```java
// Option 1: change lookupUser to use AppError
EitherPath<AppError, User> lookupUser(String id) {
    return id.isEmpty()
        ? Path.left(new AppError.NotFound("User not found"))
        : Path.right(new User(id));
}

// Option 2: convert the error type at the boundary
validated.via(id -> lookupUser(id)
    .mapError(msg -> new AppError.NotFound(msg)));  // String -> AppError
```

Option 1 is preferred for new code. Option 2 is useful when integrating with existing methods you cannot change.

---

## Quick Diagnostic Table

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| "cannot infer type arguments" | Missing error type witness | Add `Path.<E, A>right(...)` |
| "incompatible types: XPath cannot be converted to YPath" | Mixing Path types in `via` | Use `toEitherPath()` or `toMaybePath()` |
| "method via is not applicable" | Function returns plain value, not a Path | Use `map` instead of `via` |
| "no suitable method found for map" | Lambda branches with different types | Ensure consistent return types |
| "incompatible types" with error type | Error type mismatch across steps | Use `mapError` to convert |

~~~admonish tip title="See Also"
- [Type Conversions](conversions.md) - Full reference for converting between Path types
- [Troubleshooting](../tutorials/troubleshooting.md) - Tutorial-specific issues (Kind types, annotation processors, IDE setup)
- [Cheat Sheet](../cheatsheet.md) - Quick reference for Path types and operators
~~~

---

**Previous:** [Migration Cookbook](migration_cookbook.md)
**Next:** [Advanced Effects](advanced_effects.md)
