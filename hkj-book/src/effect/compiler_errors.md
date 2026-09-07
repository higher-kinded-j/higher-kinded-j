# Common Compiler Errors

Java's type inference works well for most Effect Path usage, but generic-heavy code occasionally produces confusing compiler messages. This page documents the five things that most often go wrong, what causes them, and how to fix them.

Three of the five are not compile errors at all on the supported toolchain. `Path.right` with nothing to constrain `E`, a step returning the wrong kind of Path, and an error type that differs from the chain's all compile cleanly, and each is marked here as compiling so that a change making one of them loud again shows up as a failure rather than as silence.

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

<!-- verify -->
```java
EitherPath<AppError, User> findUser(String id) {
    User user = repository.findById(id);
    return Path.right(user);  // E bound to AppError from the return type - fine
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

<!-- verify -->
```java
EitherPath<AppError, User> pinned = Path.<AppError, User>right(user);
```

~~~admonish note title="When the witness matters"
With a clear target type the witness is optional:

<!-- verify -->
```java
EitherPath<AppError, User> path = Path.right(user);   // E = AppError (from the variable)

EitherPath<AppError, User> chained =
    Path.<AppError, String>right(userId)
        .via(id -> Path.right(loadUser(id)));         // E from the previous step
```

Without one, `E` becomes `Object` silently:

<!-- verify -->
```java
var path = Path.right(user);                          // E = Object - add the witness
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

## 2. A step that returns the wrong kind of Path

~~~admonish warning title="This is not a compile error either"
Older write-ups described javac rejecting this:

```
error: incompatible types: MaybePath<User> cannot be converted to EitherPath<AppError,User>
    .via(id -> Path.maybe(findUser(id)))
                   ^
```

It does not. `via` takes a `Function<? super A, ? extends Chainable<B>>`,
and every Path type is a `Chainable`, so *which* Path a step returns is
not part of the signature. A step returning the wrong kind compiles;
`via` checks the kind at runtime and throws `IllegalArgumentException`.

With the HKJ build plugin the `path-type-mismatch` check reports it at
compile time, with an actionable message at the call site. See
[Compile-Time Checks](../tooling/compile_checks.md) for the full
catalogue and configuration. Without the plugin, the first sign is the
exception.
~~~

**The trigger:**

<!-- verify -->
```java
MaybePath<User> findUser(String id) {
    return Path.maybe(loadUser(id));
}

EitherPath<AppError, String> result =
    Path.<AppError, String>right(userId)
        .via(id -> findUser(id))               // returns MaybePath, not EitherPath
        .map(User::name);
```

`via` expects the function to return the *same* Path kind. An `EitherPath` chain requires `via` to return an `EitherPath`, not a `MaybePath` - but nothing in the signature says so, which is why this reaches runtime.

**The fix:** convert at the boundary using `toEitherPath`.

<!-- verify -->
```java
EitherPath<AppError, String> result =
    Path.<AppError, String>right(userId)
        .via(id -> Path.maybe(loadUser(id))
            .<AppError>toEitherPath(new AppError.UserNotFound(id)))  // MaybePath -> EitherPath
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

<!-- verify:rejects "cannot be applied to given types" -->
```java
// processOrder returns the wrong type
String processOrder(Order order) {    // returns String, not a Path
    return order.id();
}

EitherPath<AppError, String> chained =
    Path.<AppError, Order>right(order)
        .via(this::processOrder);     // via needs a Path-returning function
```

`via` (the Effect Path equivalent of `flatMap`) requires the function to return a `Chainable`, which all Path types implement. If your function returns a plain value, use `map` instead.

**The fix:** use `map` for plain transformations, `via` for Path-returning functions.

<!-- verify -->
```java
// For plain transformations: use map
EitherPath<AppError, String> mapped =
    Path.<AppError, Order>right(order)
        .map(this::processOrder);              // map: A -> B

// For Path-returning functions: use via
EitherPath<AppError, Order> chained =
    Path.<AppError, Order>right(order)
        .via(this::validateAndProcessOrder);   // via: A -> Path<B>
```

**Rule of thumb:**
- `map`: your function takes `A` and returns `B`
- `via`: your function takes `A` and returns a `Path<B>` (any Path type that matches the chain)

---

## 4. "Lambda body is neither value nor void compatible"

**The error:**

```
error: lambda body is neither value nor void compatible
    .map(o -> {
         ^
error: method map in class EitherPath<E,A> cannot be applied to given types;
  required: Function<? super Order,? extends B>
  reason: cannot infer type-variable(s) B
    (argument mismatch; bad return type in lambda expression
      missing return value)
```

**The trigger:**

<!-- verify:rejects "lambda body is neither value nor void compatible" -->
```java
EitherPath<AppError, Double> total =
    Path.<AppError, Order>right(order)
        .map(o -> {
            if (o.isValid()) {
                return o.total();    // returns Double
            }
            // missing return: compiler cannot determine B
        });
```

This typically happens when:
- A lambda has branches with different return types (or a missing branch)
- The lambda parameter type cannot be inferred in a complex chain

**The fix:** ensure all branches return the same type, or add explicit parameter types.

<!-- verify -->
```java
// Fix 1: ensure all branches return the same type
EitherPath<AppError, Double> total =
    Path.<AppError, Order>right(order)
        .map(o -> {
            if (o.isValid()) {
                return o.total();
            }
            return 0.0;              // all branches return Double
        });

// Fix 2: add explicit types when inference fails
EitherPath<AppError, Double> explicit =
    Path.<AppError, Order>right(order)
        .map((Order o) -> o.total());
```

In long chains where inference struggles, extracting the lambda into a named method often resolves the issue:

<!-- verify -->
```java
private Double extractTotal(Order order) {
    return order.isValid() ? order.total() : 0.0;
}

// Method reference: no inference needed
EitherPath<AppError, Double> total =
    Path.<AppError, Order>right(order)
        .map(this::extractTotal);
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

<!-- verify -->
```java
// lookupUser returns EitherPath<String, User> -- wrong error type
EitherPath<String, User> lookupUser(String id) {
    return id.isEmpty()
        ? Path.<String, User>left("User not found")   // error type is String
        : Path.<String, User>right(new User(id));
}

EitherPath<AppError, String> validated = validateInput(input);

// String erased; the result is typed AppError, and is not
EitherPath<AppError, User> wrong = validated.via(id -> lookupUser(id));
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

<!-- verify -->
```java
// Option 1: change lookupUser to use AppError
EitherPath<AppError, User> lookupUser(String id) {
    return id.isEmpty()
        ? Path.<AppError, User>left(new AppError.NotFound("User not found"))
        : Path.<AppError, User>right(new User(id));
}

// Option 2: convert the error type at the boundary
EitherPath<String, User> lookupByName(String id) {
    return Path.<String, User>right(new User(id));
}

EitherPath<AppError, String> validated = validateInput(input);

EitherPath<AppError, User> converted =
    validated.via(id -> lookupByName(id)
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
| Runtime `IllegalArgumentException` from `via` (no compile error) | A step returns a different Path kind | Convert at the boundary with `toEitherPath()` / `toMaybePath()`; the `path-type-mismatch` check reports it (§2) |
| "method via is not applicable" | Function returns plain value, not a Path | Use `map` instead of `via` |
| "lambda body is neither value nor void compatible" | Lambda branches with different types | Ensure consistent return types |
| Wrong error type at runtime, no compile error | `E` silently erased across a chain step | Unify `E` / `mapError`; the `error-type-mismatch` check warns (§5) |

~~~admonish tip title="See Also"
- [Optics Compiler Errors](../optics/compiler_errors.md#generatepathbridge-and-pathvia) - What `@GeneratePathBridge` and `@PathVia` report, and why
- [Type Conversions](conversions.md) - Full reference for converting between Path types
- [Troubleshooting](../tutorials/troubleshooting.md) - Tutorial-specific issues (Kind types, annotation processors, IDE setup)
- [Cheat Sheet](../cheatsheet.md) - Quick reference for Path types and operators
~~~

---

**Previous:** [Type Conversions](conversions.md)
**Next:** [Production Readiness](production_readiness.md)
