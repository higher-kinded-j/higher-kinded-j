# Common Compiler Errors

Java's type inference works well for most Effect Path usage, but generic-heavy code occasionally produces confusing compiler messages. This page documents the five most common errors, what causes them, and how to fix them.

~~~admonish info title="What You'll Learn"
- How to fix type inference failures when creating Path instances
- How to resolve type mismatches when mixing Path types in a chain
- How to handle `via` signature errors and lambda type issues
- How to fix error type mismatches across chain steps
~~~

---

## 1. The phantom error type `E` on `Path.right(...)`

~~~admonish warning title="This is no longer a compile error on the supported toolchain"
Older write-ups described a `cannot infer type-variable(s) E` error here:

```
error: cannot infer type arguments for right(A)
  reason: cannot infer type-variable(s) E
```

On the supported compiler (modern `javac`, the toolchain the HKJ build
plugin targets) this **does not happen**. `Path.right(value)` carries
`E` only in its return type, so when the context constrains `E` (a
typed variable, a return type, the previous chain step) `javac` binds
it; when nothing constrains it `javac` silently resolves `E` to
`java.lang.Object` and the code **compiles**. It does not error.

This is the more dangerous outcome: the mistake is now *silent*, not
loud.
~~~

**The trigger:**

```java
EitherPath<AppError, User> findUser(String id) {
    User user = repository.findById(id);
    return Path.right(user);  // E bound to AppError from the return type — fine
}

var p = Path.right(user);     // nothing constrains E -> E = Object, compiles silently
```

`Path.right`/`Path.left` put `E` only in the result. When `E` defaults
to `Object`, later code that expects a specific error type either fails
to type-check at the *consumer* (a normal incompatible-types error,
elsewhere) or, inside a chain, has its real error type erased; see
[§5](#5-the-error-type-is-silently-erased-across-a-chain).

**The fix:** pin `E` explicitly so intent is recorded and `Object`
never leaks in:

```java
return Path.<AppError, User>right(user);
```

~~~admonish note title="When the witness matters"
With a clear target type the witness is optional:

```java
EitherPath<AppError, User> path = Path.right(user);   // E = AppError (from the variable)
.via(id -> Path.right(lookupUser(id)))                // E from the previous step
```

Without one, `E` becomes `Object` silently:

```java
var path = Path.right(user);                          // E = Object — add the witness
```

The witness costs nothing at runtime and keeps the error type honest.
~~~

~~~admonish info title="Tooling"
The HKJ compile-time checker does **not** flag the bare `E = Object`
default (it is not reliably distinguishable from intentionally
`EitherPath<Object, …>` code). It *does* flag the related silent hazard
in [§5](#5-the-error-type-is-silently-erased-across-a-chain) via the
`error-type-mismatch` check.
~~~

---

## 2. "Incompatible types: MaybePath cannot be converted to EitherPath"

~~~admonish tip title="The HKJ checker catches this"
With the HKJ build plugin this is flagged at compile time by the
`path-type-mismatch` check, with an actionable message at the call
site (otherwise it is a runtime `IllegalArgumentException`). See
[Compile-Time Checks](../tooling/compile_checks.md) for the full
catalogue and configuration.
~~~

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

~~~admonish tip title="The HKJ checker catches this"
Flagged at compile time by the `via-non-path` check (companion to
javac's own error), with the actionable "use `map` for a plain
transformation" message. See
[Compile-Time Checks](../tooling/compile_checks.md).
~~~

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

## 5. The error type is silently erased across a chain

~~~admonish danger title="This compiles, and that is the bug"
This case was previously documented as a compile error:

```
error: incompatible types: EitherPath<String,User> cannot be converted to
    EitherPath<AppError,User>
```

On the supported compiler it is **not** an error. `via`/`flatMap`/
`then` accept `Function/Supplier<? extends Chainable<B>>` and `zipWith`
a `Combinable<B>`, none of which carry the error type. A step whose
`E` differs from the chain's compiles cleanly; the wrong error type is
**carried at runtime**, surfacing later as a `ClassCastException` when
the error is consumed. The compiler does not catch this.
~~~

**The trigger:**

```java
EitherPath<AppError, String> validated = validateInput(input);

// lookupUser returns EitherPath<String, User> -- wrong error type
EitherPath<String, User> lookupUser(String id) {
    return id.isEmpty()
        ? Path.left("User not found")     // error type is String
        : Path.right(new User(id));
}

validated.via(id -> lookupUser(id));       // String erased; result typed AppError, isn't
```

Every step in an `EitherPath` chain is *meant* to share one error type
`E`, but the chain signatures erase it through `Chainable<B>`, so the
compiler will not enforce it for you.

~~~admonish info title="Tooling catches this"
The HKJ compile-time checker's `error-type-mismatch` check reports this
silent mismatch (as a **warning** by default, since the compiler itself
accepts the code). See [Compile-Time Checks](../tooling/compile_checks.md).
It fires when the receiver and the step are the same error-typed Path
category and the step's `E` is not assignable to the chain's `E`.
~~~

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

## Compile-Time Path Type Mismatch Detection

~~~admonish tip title="Automated Detection"
The HKJ Gradle plugin includes a compile-time checker that catches
Path type mismatches before runtime. Rather than debugging an
`IllegalArgumentException` in production, the checker reports the
error during compilation. See [Compile-Time Checks](../tooling/compile_checks.md)
for setup and details.
~~~

---

## Quick Diagnostic Table

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| `Path.right`/`left` silently gets `E = Object` (no error) | `E` unconstrained; modern javac defaults it | Add the witness `Path.<E, A>right(...)` (§1) |
| "incompatible types: XPath cannot be converted to YPath" | Mixing Path types in `via` | Use `toEitherPath()` or `toMaybePath()` |
| "method via is not applicable" | Function returns plain value, not a Path | Use `map` instead of `via` |
| "no suitable method found for map" | Lambda branches with different types | Ensure consistent return types |
| Wrong error type at runtime, no compile error | `E` silently erased across a chain step | Unify `E` / `mapError`; the `error-type-mismatch` check warns (§5) |

~~~admonish tip title="See Also"
- [Type Conversions](conversions.md) - Full reference for converting between Path types
- [Troubleshooting](../tutorials/troubleshooting.md) - Tutorial-specific issues (Kind types, annotation processors, IDE setup)
- [Cheat Sheet](../cheatsheet.md) - Quick reference for Path types and operators
~~~

---

**Previous:** [Type Conversions](conversions.md)
**Next:** [Production Readiness](production_readiness.md)
