---
name: hkj-bridge
description: "Integrate HKJ effects with optics: .focus() on EitherPath/MaybePath/TryPath/IOPath/ValidationPath, FocusPath.toEitherPath(), AffinePath.toMaybePath(), TraversalPath.toListPath(), validation pipelines combining Focus DSL with Effect Path error handling"
---

# Higher-Kinded-J Bridge: Effects + Optics Integration

You are helping a developer combine HKJ's Effect Path API with the Focus DSL / Optics system. The bridge API lets you cut between two domains: structural navigation (optics) and computational effects (paths).

## When to load supporting files

- If the user wants a **complete before/after example**, load `reference/capstone-example.md`
- For **Effect Path API basics** (choosing paths, operators), suggest `/hkj-guide`
- For **optics annotation setup** (@GenerateFocus, @GenerateLenses), suggest `/hkj-optics`

---

## Two Directions

```
    OPTICS DOMAIN                         EFFECTS DOMAIN
    FocusPath<S, A>     ──toXxxPath()──>  MaybePath<A>
    AffinePath<S, A>    ──toXxxPath()──>  EitherPath<E, A>
    TraversalPath<S, A> ──toXxxPath()──>  ListPath<A>

    MaybePath<A>        <──.focus()────   FocusPath<S, A>
    EitherPath<E, A>    <──.focus()────   AffinePath<S, A>
```

---

## Direction 1: Optics -> Effects

Use `toXxxPath()` methods on Focus paths to start an effect pipeline from data navigation.

### FocusPath (always has a value -> always successful)

```java
FocusPath<User, String> namePath = UserFocus.name();

MaybePath<String> name     = namePath.toMaybePath(user);      // Just("Alice")
EitherPath<E, String> name = namePath.toEitherPath(user);     // Right("Alice")
TryPath<String> name       = namePath.toTryPath(user);        // Success("Alice")
IdPath<String> name        = namePath.toIdPath(user);         // Id("Alice")
```

### AffinePath (may not have a value -> may fail)

```java
AffinePath<User, String> emailPath = UserFocus.email();

// MaybePath: absence becomes Nothing (no error needed)
emailPath.toMaybePath(user);                                  // Just("a@b.com") or Nothing

// EitherPath: provide error for absent case
emailPath.toEitherPath(user, new Error("No email"));          // Right("a@b.com") or Left(error)

// TryPath: provide exception supplier
emailPath.toTryPath(user, () -> new MissingEmailException()); // Success or Failure
```

### TraversalPath (zero or more values)

```java
TraversalPath<Company, User> employees = CompanyFocus.employees();

ListPath<User> all    = employees.toListPath(company);    // All values as list
StreamPath<User> lazy = employees.toStreamPath(company);  // Lazy stream
MaybePath<User> first = employees.toMaybePath(company);   // First value or Nothing
```

### Bridge Method Summary

| Source Path | Method | Result |
|-------------|--------|--------|
| `FocusPath<S, A>` | `.toMaybePath(S)` | Always `Just(a)` |
| `FocusPath<S, A>` | `.toEitherPath(S)` | Always `Right(a)` |
| `FocusPath<S, A>` | `.toTryPath(S)` | Always `Success(a)` |
| `FocusPath<S, A>` | `.toIdPath(S)` | Always `Id(a)` |
| `AffinePath<S, A>` | `.toMaybePath(S)` | `Just(a)` or `Nothing` |
| `AffinePath<S, A>` | `.toEitherPath(S, E)` | `Right(a)` or `Left(e)` |
| `AffinePath<S, A>` | `.toTryPath(S, Supplier)` | `Success(a)` or `Failure` |
| `AffinePath<S, A>` | `.toOptionalPath(S)` | `Optional.of(a)` or empty |
| `TraversalPath<S, A>` | `.toListPath(S)` | All values as list |
| `TraversalPath<S, A>` | `.toStreamPath(S)` | Lazy stream |
| `TraversalPath<S, A>` | `.toMaybePath(S)` | First value or `Nothing` |

---

## Direction 2: Effects -> Optics

Use `.focus()` on effect paths to navigate into the contained value's structure.

### Basic focus() with FocusPath

```java
EitherPath<Error, User> userResult = fetchUser(userId);
FocusPath<User, String> namePath = UserFocus.name();

// Navigate into the user's name
EitherPath<Error, String> nameResult = userResult.focus(namePath);
// Left(error) stays Left(error), Right(user) becomes Right(user.name())
```

### focus() with AffinePath (may fail)

```java
AffinePath<User, String> emailPath = UserFocus.email();

// EitherPath: provide error for absent case
EitherPath<Error, String> email =
    userResult.focus(emailPath, new Error("Email not configured"));

// MaybePath: absence just becomes Nothing
MaybePath<String> email = userMaybe.focus(emailPath);

// TryPath: provide exception supplier
TryPath<String> email =
    userTry.focus(emailPath, () -> new MissingEmailException());
```

### Effect-Specific Behaviour Table

| Effect Path | FocusPath (always present) | AffinePath (may be absent) |
|------------|--------------------------|--------------------------|
| `EitherPath<E, S>` | `.focus(fp)` -> `EitherPath<E, A>` | `.focus(ap, error)` -> `EitherPath<E, A>` |
| `MaybePath<S>` | `.focus(fp)` -> `MaybePath<A>` | `.focus(ap)` -> `MaybePath<A>` |
| `TryPath<S>` | `.focus(fp)` -> `TryPath<A>` | `.focus(ap, exSupplier)` -> `TryPath<A>` |
| `IOPath<S>` | `.focus(fp)` -> `IOPath<A>` | `.focus(ap, exSupplier)` -> `IOPath<A>` |
| `ValidationPath<E, S>` | `.focus(fp)` -> `ValidationPath<E, A>` | `.focus(ap, error, sg)` -> `ValidationPath<E, A>` |

---

## When to Use focus() vs via() vs map()

| Operator | Use When | Example |
|----------|----------|---------|
| `.map(fn)` | Transforming the value with a plain function | `.map(User::name)` |
| `.focus(optic)` | Navigating into a nested structure | `.focus(UserFocus.address())` |
| `.via(fn)` | Chaining to another effectful computation | `.via(user -> validateUser(user))` |

**Key difference**: `.map()` works with any function. `.focus()` preserves the optic's type-safety and composes naturally for deep navigation chains. `.via()` chains to another Path.

---

## Practical Patterns

### Pattern 1: Extract and Validate

```java
EitherPath<Error, String> validatedPostcode =
    fetchUser(userId)                              // EitherPath<Error, User>
        .focus(UserFocus.address())                // EitherPath<Error, Address>
        .focus(AddressFocus.postcode())            // EitherPath<Error, String>
        .via(code -> validatePostcode(code));      // EitherPath<Error, String>
```

### Pattern 2: Safe Deep Access

```java
MaybePath<String> managerEmail =
    Path.maybe(department)
        .focus(DepartmentFocus.manager())          // AffinePath: manager may not exist
        .focus(EmployeeFocus.email());             // AffinePath: email may be Optional
```

### Pattern 3: Batch Processing with Traversals

```java
ListPath<String> allEmails =
    CompanyFocus.departments()                     // TraversalPath
        .employees()                               // TraversalPath
        .email()                                   // TraversalPath (Optional unwrapped)
        .toListPath(company);
```

### Pattern 4: Complete Pipeline (Fetch -> Navigate -> Validate -> Save)

```java
EitherPath<Error, Order> result =
    fetchUser(userId)                              // EitherPath<Error, User>
        .focus(UserFocus.address())                // navigate to address
        .via(addr -> validateAddress(addr))        // validate
        .via(addr -> geocodeAddress(addr))         // enrich
        .map(geo -> createOrder(geo, items));      // transform
```

---

## ForPath + Focus DSL

Focus paths integrate with ForPath comprehensions via `.focus()`:

```java
EitherPath<Error, OrderResult> result = ForPath.from(fetchUser(id))
    .focus(UserFocus.address())                    // navigate within comprehension
    .from((user, address) -> validateAddress(address))
    .yield((user, address, validated) -> createOrder(user, validated));
```

---

## Common Mistakes

1. **Using `map` for structural navigation**: `.map(User::address)` works but loses optic composability. Prefer `.focus(UserFocus.address())` to enable further `.focus()` chaining.
2. **Forgetting error parameter for AffinePath**: `eitherPath.focus(affinePath)` won't compile; you need `eitherPath.focus(affinePath, errorValue)` because EitherPath needs an error for the absent case.
3. **Wrong direction**: If you have data and want an effect, use `optic.toXxxPath(source)`. If you have an effect and want to navigate, use `effectPath.focus(optic)`.
