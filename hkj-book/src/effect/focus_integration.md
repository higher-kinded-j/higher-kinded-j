# Focus-Effect Integration

## _Bridging Structural Navigation and Effect Composition_

> *"The knife had two edges: one was tempered for common use, but the other was keener than any blade that had ever existed before. It could cut through anything, even the fabric of the universe itself... With the right touch, you could open a window to another world."*
>
> -- Philip Pullman, *The Subtle Knife*

---

The Focus DSL and Effect Paths inhabit different worlds. One navigates the structure of data, drilling into records, unwrapping optionals, traversing collections. The other navigates the shape of computation, handling absence, failure, side effects, accumulated errors. Both worlds have their own grammar, their own rules, their own power.

But like the subtle knife, the bridge API lets you cut cleanly between them.

When you hold structured data and need the railway semantics of effects, the `toXxxPath` methods open a window from optics into effects. When you're deep in an effect pipeline and need to navigate the contained structure, `focus()` opens a window back. The passage is seamless. The types guide you. Each world remains itself, but now they connect.

~~~admonish info title="What You'll Learn"
- How Focus paths and Effect paths complement each other
- Converting between optics and effects with bridge methods
- Using `focus()` to navigate within effect contexts
- Building validation pipelines that combine both domains
- Common patterns and when to use each approach
~~~

~~~admonish title="Hands On Practice"
[Tutorial14_FocusEffectBridge.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial14_FocusEffectBridge.java)
~~~

---

## The Two Domains

```
    ┌─────────────────────────────────────────────────────────────────┐
    │                       OPTICS DOMAIN                             │
    │                                                                 │
    │   FocusPath<S, A>    ───── exactly one focus (Lens)             │
    │   AffinePath<S, A>   ───── zero or one focus (Affine)           │
    │   TraversalPath<S, A> ──── zero or more focuses (Traversal)     │
    │                                                                 │
    │   Navigation: get, set, modify, via(optic)                      │
    └─────────────────────────────────────────────────────────────────┘
                                    │
                                    │  Bridge API
                                    ▼
    ┌─────────────────────────────────────────────────────────────────┐
    │                       EFFECTS DOMAIN                            │
    │                                                                 │
    │   MaybePath<A>         ───── optional value                     │
    │   EitherPath<E, A>     ───── success or typed error             │
    │   TryPath<A>           ───── success or exception               │
    │   IOPath<A>            ───── deferred side effect               │
    │   ValidationPath<E, A> ───── accumulating errors                │
    │                                                                 │
    │   Navigation: map, via(effect), run, recover                    │
    └─────────────────────────────────────────────────────────────────┘
```

---

## Direction 1: Optics → Effects

When you have a data structure and want to start an effect pipeline, use the `toXxxPath` methods
on Focus paths.

### FocusPath Bridge Methods

`FocusPath` always has a value, so these methods always produce successful effects:

```java
FocusPath<User, String> namePath = UserFocus.name();
User alice = new User("Alice", Optional.of("alice@example.com"));

// Lift into different effect types
MaybePath<String> maybeName = namePath.toMaybePath(alice);     // → Just("Alice")
EitherPath<E, String> eitherName = namePath.toEitherPath(alice); // → Right("Alice")
TryPath<String> tryName = namePath.toTryPath(alice);           // → Success("Alice")
IdPath<String> idName = namePath.toIdPath(alice);              // → Id("Alice")
```

### AffinePath Bridge Methods

`AffinePath` may not have a value, so these methods require error handling:

```java
AffinePath<User, String> emailPath = UserFocus.email(); // Optional<String> → String

User withEmail = new User("Alice", Optional.of("alice@example.com"));
User withoutEmail = new User("Bob", Optional.empty());

// MaybePath: absence becomes Nothing
emailPath.toMaybePath(withEmail);    // → Just("alice@example.com")
emailPath.toMaybePath(withoutEmail); // → Nothing

// EitherPath: provide error for absence
emailPath.toEitherPath(withEmail, "No email");    // → Right("alice@example.com")
emailPath.toEitherPath(withoutEmail, "No email"); // → Left("No email")

// TryPath: provide exception supplier for absence
emailPath.toTryPath(withEmail, () -> new MissingEmailException());    // → Success
emailPath.toTryPath(withoutEmail, () -> new MissingEmailException()); // → Failure
```

### TraversalPath Bridge Methods

`TraversalPath` focuses on multiple values:

```java
TraversalPath<Company, User> employeesPath = CompanyFocus.employees();
Company company = new Company("TechCorp", List.of(alice, bob, charlie));

// ListPath: all values as a list
ListPath<User> allEmployees = employeesPath.toListPath(company);

// StreamPath: lazy stream
StreamPath<User> employeeStream = employeesPath.toStreamPath(company);

// MaybePath: first value (or Nothing)
MaybePath<User> firstEmployee = employeesPath.toMaybePath(company);
```

### Bridge Method Summary

| Path Type | Method | Result |
|-----------|--------|--------|
| `FocusPath<S, A>` | `toMaybePath(S)` | Always `Just(a)` |
| `FocusPath<S, A>` | `toEitherPath(S)` | Always `Right(a)` |
| `FocusPath<S, A>` | `toTryPath(S)` | Always `Success(a)` |
| `FocusPath<S, A>` | `toIdPath(S)` | Always `Id(a)` |
| `AffinePath<S, A>` | `toMaybePath(S)` | `Just(a)` or `Nothing` |
| `AffinePath<S, A>` | `toEitherPath(S, E)` | `Right(a)` or `Left(e)` |
| `AffinePath<S, A>` | `toTryPath(S, Supplier)` | `Success(a)` or `Failure` |
| `AffinePath<S, A>` | `toOptionalPath(S)` | `Optional.of(a)` or `Optional.empty()` |
| `TraversalPath<S, A>` | `toListPath(S)` | All values as list |
| `TraversalPath<S, A>` | `toStreamPath(S)` | Lazy stream of values |
| `TraversalPath<S, A>` | `toMaybePath(S)` | First value or `Nothing` |

---

## Direction 2: Effects → Optics

When you're already in an effect context and need to navigate into the contained value, use
the `focus()` method.

### Basic focus() Usage

```java
// Start with an effect containing structured data
EitherPath<Error, User> userResult = fetchUser(userId);

// Navigate into the structure
FocusPath<User, String> namePath = UserFocus.name();
EitherPath<Error, String> nameResult = userResult.focus(namePath);

// The effect semantics are preserved
// If userResult was Left(error), nameResult is also Left(error)
// If userResult was Right(user), nameResult is Right(user.name())
```

### focus() with AffinePath

When the navigation might fail (AffinePath), you must provide an error for the absent case:

```java
AffinePath<User, String> emailPath = UserFocus.email();

// EitherPath requires an error value
EitherPath<Error, String> emailResult =
    userResult.focus(emailPath, new Error("Email not configured"));

// TryPath requires an exception supplier
TryPath<String> emailTry =
    userTryPath.focus(emailPath, () -> new MissingEmailException());

// MaybePath just becomes Nothing (no error needed)
MaybePath<String> emailMaybe = userMaybePath.focus(emailPath);
```

### Effect-Specific focus() Behaviour

| Effect Type | FocusPath Result | AffinePath (absent) Result |
|-------------|------------------|---------------------------|
| `MaybePath<A>` | `MaybePath<B>` (Just) | `MaybePath<B>` (Nothing) |
| `EitherPath<E, A>` | `EitherPath<E, B>` (Right) | `EitherPath<E, B>` (Left with provided error) |
| `TryPath<A>` | `TryPath<B>` (Success) | `TryPath<B>` (Failure with provided exception) |
| `IOPath<A>` | `IOPath<B>` | `IOPath<B>` (throws on run) |
| `ValidationPath<E, A>` | `ValidationPath<E, B>` (Valid) | `ValidationPath<E, B>` (Invalid with error) |
| `IdPath<A>` | `IdPath<B>` | `MaybePath<B>` (path type changes) |

---

## Practical Patterns

### Pattern 1: Extract and Validate

**The problem:** You have nested data and need to extract and validate specific fields.

**The solution:**

```java
EitherPath<List<String>, User> validateUser(User user) {
    FocusPath<User, String> namePath = UserFocus.name();
    AffinePath<User, String> emailPath = UserFocus.email();

    return Path.<List<String>, User>right(user)
        .via(u -> {
            // Validate name length
            String name = namePath.get(u);
            if (name.length() < 2) {
                return Path.left(List.of("Name too short"));
            }
            return Path.right(u);
        })
        .via(u -> {
            // Validate email if present
            return emailPath.toEitherPath(u, List.of("Email required"))
                .via(email -> email.contains("@")
                    ? Path.right(u)
                    : Path.left(List.of("Invalid email format")));
        });
}
```

### Pattern 2: Nested Service Results

**The problem:** Service calls return effects, and you need to extract nested data.

**The solution:**

```java
EitherPath<Error, String> getOrderCustomerCity(OrderId orderId) {
    return orderService.findById(orderId)           // → EitherPath<Error, Order>
        .focus(OrderFocus.customer())               // → EitherPath<Error, Customer>
        .focus(CustomerFocus.address())             // → EitherPath<Error, Address>
        .focus(AddressFocus.city());                // → EitherPath<Error, String>
}
```

### Pattern 3: Safe Deep Access

**The problem:** Accessing deeply nested optional data without null checks.

**The solution:**

```java
// Traditional approach (pyramid of doom)
String getManagerEmail(Company company) {
    Department dept = company.getDepartment(0);
    if (dept == null) return "unknown";
    Employee manager = dept.getManager();
    if (manager == null) return "unknown";
    String email = manager.getEmail();
    return email != null ? email : "unknown";
}

// Focus-Effect approach
String getManagerEmail(Company company) {
    return CompanyFocus.department(0)
        .toMaybePath(company)                       // → MaybePath<Department>
        .focus(DepartmentFocus.manager())           // → MaybePath<Employee>
        .focus(EmployeeFocus.email())               // → MaybePath<String>
        .getOrElse("unknown");
}
```

### Pattern 4: Batch Processing with Traversals

**The problem:** Apply an effectful operation to all items in a collection.

**The solution:**

```java
// Validate all employee emails
ValidationPath<List<String>, Company> validateAllEmails(Company company) {
    return CompanyFocus.employees()
        .toListPath(company)
        .via(employees -> {
            // Validate each employee's email
            var results = employees.stream()
                .map(e -> EmployeeFocus.email()
                    .toEitherPath(e, List.of("Missing email for " + e.name())))
                .toList();
            // Combine results...
        });
}
```

---

## When to Use Which

| Scenario | Approach |
|----------|----------|
| Have data, need effect pipeline | `path.toXxxPath(source)` |
| In effect, need to navigate | `effectPath.focus(opticPath)` |
| Simple extraction | `focusPath.get(source)` |
| Chained effect operations | `effectPath.via(f)` |
| Transform without effect change | `effectPath.map(f)` |

### Decision Flow

```
Start with...
    │
    ├─► Concrete data?
    │       │
    │       └─► Use FocusPath.toXxxPath(data) to enter effect domain
    │
    └─► Effect containing data?
            │
            ├─► Need to navigate structure?
            │       │
            │       └─► Use effectPath.focus(opticPath)
            │
            ├─► Need to chain effects?
            │       │
            │       └─► Use effectPath.via(f)
            │
            └─► Need to transform value?
                    │
                    └─► Use effectPath.map(f)
```

---

## Composing Both Directions

The most powerful patterns combine both directions fluently:

```java
// Complete workflow: fetch → navigate → validate → transform → save
EitherPath<Error, SaveResult> processUserUpdate(UserId userId, UpdateRequest request) {
    return userService.findById(userId)                    // Effect: fetch user
        .focus(UserFocus.profile())                        // Optics: navigate to profile
        .via(profile -> {                                  // Effect: validate
            return ProfileFocus.email()
                .toEitherPath(profile, Error.missingEmail())
                .via(email -> validateEmail(email));
        })
        .via(validEmail -> {                               // Effect: apply update
            return applyUpdate(request, validEmail);
        })
        .via(updated -> {                                  // Effect: save
            return userService.save(updated);
        });
}
```

---

~~~admonish info title="Hands-On Learning"
Practice Focus-Effect bridging in [Tutorial 14: Focus-Effect Bridge](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial14_FocusEffectBridge.java) (13 exercises, ~15 minutes).
~~~

~~~admonish tip title="See Also"
- [Capstone: Effects Meet Optics](capstone_focus_effect.md) - Complete before/after example combining both domains
- [Focus DSL](../optics/focus_dsl.md) - Complete guide to Focus paths
- [Effect Path Overview](effect_path_overview.md) - Railway model and effect basics
- [Capability Interfaces](capabilities.md) - Type class foundations
~~~

---

**Previous:** [Type Conversions](conversions.md)
**Next:** [Capstone: Effects Meet Optics](capstone_focus_effect.md)
