---
name: hkj-bridge
description: "Integrate HKJ effects with optics: .focus() on EitherPath/MaybePath/TryPath/IOPath/ValidationPath, FocusPath.toEitherPath(), AffinePath.toMaybePath(), TraversalPath.toListPath(), ValidatedPrism parse/parsePath smart constructors (parse-don't-validate), Edits.accumulate().applyPath() and Edits.combine() for sparse multi-edits and REST PATCH, validation pipelines combining Focus DSL with Effect Path error handling"
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

    ACCUMULATING CROSSINGS (Directions 3 and 4)
    ValidatedPrism<S,A> ──.parse(s)─────> Validated<NEL<FieldError>, A>
    ValidatedPrism<S,A> ──.parsePath(s)─> ValidationPath<NEL<FieldError>, A>
    Edits.accumulate()  ──.applyPath(s)─> ValidationPath<NEL<FieldError>, S>
    Edits.combine()     ───stays here───  Update<S>          (never crosses)
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
| `ValidationPath<E, S>` | `.focus(fp)` -> `ValidationPath<E, A>` | `.focus(ap, error)` -> `ValidationPath<E, A>` |

---

## Direction 3: ValidatedPrism, the Smart-Constructor Bridge

`org.higherkindedj.optics.validated.ValidatedPrism<S, A>` is the parse-don't-validate optic: `S` is the
raw/wire shape, `A` is the parsed domain type. It crosses **both ways**, and both crossings land in the
accumulating world:

- `parse(S)` -> `Validated<NonEmptyList<FieldError>, A>`: optics -> accumulating effect
- `parsePath(S)` -> `ValidationPath<NonEmptyList<FieldError>, A>`: optics -> Path world, straight into a pipeline
- `build(A)` -> `S`: **total**, cannot fail (a parsed value can always be rendered back)

```java
ValidatedPrism<String, EmailAddress> emailPrism = ValidatedPrism.of(
    raw -> raw.contains("@")
        ? Validated.validNel(new EmailAddress(raw))
        : Validated.invalidNel(FieldError.of("must contain @")),
    EmailAddress::value);                                  // the total build side

Validated<NonEmptyList<FieldError>, EmailAddress> v = emailPrism.parse(rawEmail);
ValidationPath<NonEmptyList<FieldError>, EmailAddress> p = emailPrism.parsePath(rawEmail);
String wire = emailPrism.build(email);                     // total
```

### Member Summary

| Member | Result | Notes |
|--------|--------|-------|
| `ValidatedPrism.of(parse, build)` | `ValidatedPrism<S, A>` | `parse: S -> Validated<NEL<FieldError>, A>`, `build: A -> S` |
| `ValidatedPrism.fromIso(Iso<S, A>)` | `ValidatedPrism<S, A>` | An `Iso` never fails; lifts for free |
| `ValidatedPrism.fromPrism(Prism<S, A>, FieldError)` | `ValidatedPrism<S, A>` | Supply the reason a non-match reports |
| `.parse(S)` | `Validated<NEL<FieldError>, A>` | Accumulates every reason |
| `.parsePath(S)` | `ValidationPath<NEL<FieldError>, A>` | The bridge into the effects world |
| `.build(A)` | `S` | Total |
| `.parseAll(List<? extends S>)` | `Validated<NEL<FieldError>, List<A>>` | Failures across elements accumulate |
| `.parseValues(Map<K, ? extends S>)` | `Validated<NEL<FieldError>, Map<K, A>>` | Failures located by key |
| `.buildAll(List<? extends A>)` | `List<S>` | Total |
| `.toPrism()` / `.toAffine()` | `Prism<S, A>` / `Affine<S, A>` | Drop back to plain optics (reasons discarded) |

### Composition (and the one deliberate hole)

| Compose with | Method | Behaviour |
|--------------|--------|-----------|
| `ValidatedPrism<A, B>` | `.andThen(other)` | **Short-circuits**: the second parse never sees a failed first |
| `Iso<A, B>` | `.andThen(iso)` | Free: an `Iso` cannot fail |
| `Prism<A, B>` | `.andThen(prism, FieldError reason)` | Non-match reports `reason` |
| `Lens<A, B>` | **not provided** | Deliberate: a `Lens` has no total `B -> S` build, so the result could not honour `build` |

That hole is a design constraint, not an omission. If you want to combine *sibling* fields (rather than
narrow deeper), do not reach for prism composition; assemble them with `Validated.fields()`, which is
the accumulating construct:

```java
Validated<NonEmptyList<FieldError>, Customer> customer =
    Validated.fields()
        .field("name",  namePrism.parse(dto.name()))
        .field("email", emailPrism.parse(dto.email()))
        .apply(Customer::new);       // both failures reported, not just the first
```

Laws: `ValidatedPrismLaws.assertValidatedPrismLaws(prism, matching, nonMatching)` in hkj-test.

---

## Direction 4: Edits for Sparse Multi-Edit Across the Boundary

`org.higherkindedj.optics.edit.Edits` applies a *set* of edits to one immutable value, where each edit
may be absent (a `null` incoming field is a no-op, not an overwrite). It is the REST-PATCH building block,
and its two terminals are the bridge:

| Terminal | Returns | Domain |
|----------|---------|--------|
| `Edits.combine(Edit<S>...)` | `Update<S>` (a named `S -> S`) | **Stays in optics**: pure, infallible, composable |
| `Edits.accumulate(FallibleEdit<S>...).apply(S)` | `Validated<NEL<FieldError>, S>` | Crosses into the accumulating effect |
| `Edits.accumulate(...).applyPath(S)` | `ValidationPath<NEL<FieldError>, S>` | Crosses into the **Path** world |
| `Edits.accumulate(...).toValidated()` | `Validated<NEL<FieldError>, Update<S>>` | The validated *function*, applied later |

**That split is the bridge.** `combine` is the answer when nothing can fail; `accumulate` is the answer
when a leaf parses, because parsing can fail and failures must accumulate. `Edit<S> extends
FallibleEdit<S>`, so infallible leaves mix freely into `accumulate(...)`. The fold is homogeneous, so
unlike the assembly builders there is **no arity ceiling**.

### The Leaf Factories: Read This Twice

The leaf factories take a **`FocusPath<S, A>` or a `Setter<S, A>`, never a raw `Lens`.** The idiomatic
source is a generated `@GenerateFocus` path (`OrderFocus.email()`): the path carries a **label**, and that
label is what lets a parse failure locate itself in the output without a manual `.at(...)`.

| Factory | Returns | Behaviour |
|---------|---------|-----------|
| `Edit.set(FocusPath\|Setter, A)` | `Edit<S>` | Always writes |
| `Edit.modify(FocusPath\|Setter, Function<A, A>)` | `Edit<S>` | Always transforms |
| `Edit.setIfPresent(FocusPath\|Setter, @Nullable A)` | `Edit<S>` | `null` -> no-op |
| `Edit.modifyIfPresent(FocusPath\|Setter, @Nullable B, BiFunction<B, A, A>)` | `Edit<S>` | `null` -> no-op |
| `Edit.parseIfPresent(FocusPath\|Setter, @Nullable B, Function<B, Validated<NEL<FieldError>, A>>)` | `FallibleEdit<S>` | `null` -> no-op; otherwise parse, and a failure is located |

```java
// PATCH body: every field nullable. Absent means "leave alone".
ValidationPath<NonEmptyList<FieldError>, Order> patched =
    Edits.accumulate(
            Edit.setIfPresent(OrderFocus.notes(), req.notes()),
            Edit.parseIfPresent(OrderFocus.email(), req.email(), emailPrism::parse),
            Edit.parseIfPresent(OrderFocus.postcode(), req.postcode(), postcodePrism::parse))
        .applyPath(order);          // <- lands in the effects world, accumulating

// Shell: fold the Path into a response. Both bad fields are reported, not just the first.
return patched.fold(this::badRequest, repository::save);
```

Pure counterpart (nothing can fail, so it never leaves the optics world):

```java
Update<Order> confirm = Edits.combine(
    Edit.set(OrderFocus.status(), OrderStatus.CONFIRMED),
    Edit.modify(OrderFocus.total(), total -> total.multiply(0.9)));

Order confirmed = confirm.apply(order);     // pure S -> S
```

Only `FocusPath` carries segments, so only the `FocusPath` overloads auto-locate a failure; the `Setter`
overloads produce an unlabelled `FieldError`. Re-label a fallible leaf explicitly with
`FallibleEdit.at(String)`. Hand-rolling a labelled path when you have a bare `Lens`:
`FocusPath.of(lens, "email")`.

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
4. **Passing a raw `Lens` to an `Edit` factory**: the leaves take a `FocusPath<S, A>` or a `Setter<S, A>`. Use the generated `@GenerateFocus` path (`OrderFocus.email()`). A bare `Lens` has no label, so a parse failure cannot locate itself. If you only have a `Lens`, wrap it: `FocusPath.of(lens, "email")`.
5. **Reaching for `Edits.combine()` when a leaf can fail**: `combine` returns `Update<S>` and has no failure channel. The moment one leaf is a `parseIfPresent`, you want `Edits.accumulate(...).applyPath(s)`.
6. **Trying to compose a `ValidatedPrism` with a `Lens`**: not provided, by design (no total `B -> S` build). To combine sibling fields, assemble with `Validated.fields()` instead of composing optics.
