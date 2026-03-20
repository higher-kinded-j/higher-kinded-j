# Focus DSL: Type Class and Effect Integration

~~~admonish info title="What You'll Learn"
- Effectful modification with `modifyF()` using Applicative and Monad instances
- Monoid-based aggregation with `foldMap()` on traversal paths
- Generic collection traversal with `traverseOver()` for `Kind<F, A>` fields
- Conditional modification with `modifyWhen()` and sum type access with `instanceOf()`
- Path debugging with `traced()`
- Bridging between Focus paths and Effect paths in both directions
~~~

~~~admonish title="Hands On Practice"
[Tutorial13_AdvancedFocusDSL.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial13_AdvancedFocusDSL.java)
~~~

~~~admonish title="Example Code"
[TraverseIntegrationExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/TraverseIntegrationExample.java) | [ValidationPipelineExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/ValidationPipelineExample.java)
~~~

---

## Type Class Integration

The Focus DSL integrates deeply with Higher-Kinded-J type classes, enabling effectful operations, monoid-based aggregation, and generic collection traversal.

### Effectful Modification with `modifyF()`

All path types support `modifyF()` for effectful transformations:

```java
// Validation - accumulate all errors
Kind<ValidatedKind.Witness<List<String>>, User> result = userPath.modifyF(
    email -> EmailValidator.validate(email),
    user,
    ValidatedApplicative.instance()
);

// Async updates with CompletableFuture
Kind<CompletableFutureKind.Witness, Config> asyncResult = configPath.modifyF(
    key -> fetchNewApiKey(key),  // Returns CompletableFuture
    config,
    CompletableFutureApplicative.INSTANCE
);

// IO operations
Kind<IOKind.Witness, User> ioResult = userPath.modifyF(
    name -> IO.of(() -> readFromDatabase(name)),
    user,
    IOMonad.INSTANCE
);
```

### Monoid-Based Aggregation with `foldMap()`

`TraversalPath` supports `foldMap()` for aggregating values:

```java
// Sum all salaries using integer addition monoid
int totalSalary = employeesPath.foldMap(
    Monoids.integerAddition(),
    Employee::salary,
    company
);

// Concatenate all names
String allNames = employeesPath.foldMap(
    Monoids.string(),
    Employee::name,
    company
);

// Custom monoid for set union
Set<String> allSkills = employeesPath.foldMap(
    Monoids.set(),
    e -> e.skills(),
    company
);
```

### Generic Collection Traversal with `traverseOver()`

When working with collections wrapped in `Kind<F, A>`, use `traverseOver()` with a `Traverse` instance:

```java
// Given a field with Kind<ListKind.Witness, Role> type
FocusPath<User, Kind<ListKind.Witness, Role>> rolesPath = UserFocus.roles();

// Traverse into the collection
TraversalPath<User, Role> allRoles =
    rolesPath.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);

// Now operate on individual roles
List<Role> roles = allRoles.getAll(user);
User updated = allRoles.modifyAll(Role::promote, user);
```

**When to use `traverseOver()` vs `each()`:**

| Method | Use Case |
|--------|----------|
| `each()` | Standard `List<T>` or `Set<T>` fields |
| `traverseOver()` | `Kind<F, T>` fields with custom Traverse |

```java
// For List<T> - use each()
TraversalPath<Team, User> members = TeamFocus.membersList().each();

// For Kind<ListKind.Witness, T> - use traverseOver()
TraversalPath<Team, User> members = TeamFocus.membersKind()
    .<ListKind.Witness, User>traverseOver(ListTraverse.INSTANCE);
```

### Conditional Modification with `modifyWhen()`

Modify only elements that match a predicate:

```java
// Give raises only to senior employees
Company updated = CompanyFocus.employees()
    .modifyWhen(
        e -> e.yearsOfService() > 5,
        e -> e.withSalary(e.salary().multiply(1.10)),
        company
    );

// Enable only premium features
Config updated = ConfigFocus.features()
    .modifyWhen(
        f -> f.tier() == Tier.PREMIUM,
        Feature::enable,
        config
    );
```

### Working with Sum Types using `instanceOf()`

Focus on specific variants of sealed interfaces:

```java
sealed interface Shape permits Circle, Rectangle, Triangle {}

// Focus on circles only
AffinePath<Shape, Circle> circlePath = AffinePath.instanceOf(Circle.class);

// Compose with other paths
TraversalPath<Drawing, Double> circleRadii =
    DrawingFocus.shapes()
        .via(AffinePath.instanceOf(Circle.class))
        .via(CircleFocus.radius());

// Modify only circles
Drawing updated = DrawingFocus.shapes()
    .via(AffinePath.instanceOf(Circle.class))
    .modifyAll(c -> c.withRadius(c.radius() * 2), drawing);
```

### Path Debugging with `traced()`

Debug complex path navigation by observing values:

```java
// Add tracing to see what values are accessed
FocusPath<Company, String> debugPath = CompanyFocus.ceo().name()
    .traced((company, name) ->
        System.out.println("Accessing CEO name: " + name + " from " + company.name()));

// Every get() call now logs the accessed value
String name = debugPath.get(company);

// For TraversalPath, observe all values
TraversalPath<Company, Employee> tracedEmployees = CompanyFocus.employees()
    .traced((company, employees) ->
        System.out.println("Found " + employees.size() + " employees"));
```

---

## Bridging to Effect Paths

Focus paths and Effect paths share the same `via` composition operator but navigate different
domains. The bridge API enables seamless transitions between them.

```
                    FOCUS-EFFECT BRIDGE

    ┌─────────────────────────────────────────────────────┐
    │                   Optics Domain                     │
    │  FocusPath<S, A> ───────────────────────────────────│
    │  AffinePath<S, A> ──────────────────────────────────│
    │  TraversalPath<S, A> ───────────────────────────────│
    └──────────────────────────┬──────────────────────────┘
                               │
                               │ toMaybePath(source)
                               │ toEitherPath(source, error)
                               │ toTryPath(source, supplier)
                               ▼
    ┌─────────────────────────────────────────────────────┐
    │                   Effects Domain                    │
    │  MaybePath<A> ──────────────────────────────────────│
    │  EitherPath<E, A> ──────────────────────────────────│
    │  TryPath<A> ────────────────────────────────────────│
    │  IOPath<A> ─────────────────────────────────────────│
    │  ValidationPath<E, A> ──────────────────────────────│
    └──────────────────────────┬──────────────────────────┘
                               │
                               │ focus(FocusPath)
                               │ focus(AffinePath, error)
                               ▼
    ┌─────────────────────────────────────────────────────┐
    │           Back to Optics (within effect)            │
    │  EffectPath<B> ─────────────────────────────────────│
    └─────────────────────────────────────────────────────┘
```

### Direction 1: FocusPath to EffectPath

Extract a value using optics and wrap it in an effect for further processing:

```java
// FocusPath always has a value, so these always succeed
FocusPath<User, String> namePath = UserFocus.name();
MaybePath<String> maybeName = namePath.toMaybePath(user);          // -> Just(name)
EitherPath<E, String> eitherName = namePath.toEitherPath(user);    // -> Right(name)
TryPath<String> tryName = namePath.toTryPath(user);                // -> Success(name)

// AffinePath may not have a value
AffinePath<User, String> emailPath = UserFocus.email();  // Optional<String> -> String
MaybePath<String> maybeEmail = emailPath.toMaybePath(user);        // -> Just or Nothing
EitherPath<String, String> eitherEmail =
    emailPath.toEitherPath(user, "Email not configured");          // -> Right or Left
```

**Bridge Methods on FocusPath:**

| Method | Return Type | Description |
|--------|-------------|-------------|
| `toMaybePath(S)` | `MaybePath<A>` | Always `Just(value)` |
| `toEitherPath(S)` | `EitherPath<E, A>` | Always `Right(value)` |
| `toTryPath(S)` | `TryPath<A>` | Always `Success(value)` |
| `toIdPath(S)` | `IdPath<A>` | Trivial effect wrapper |

**Bridge Methods on AffinePath:**

| Method | Return Type | Description |
|--------|-------------|-------------|
| `toMaybePath(S)` | `MaybePath<A>` | `Just` if present, `Nothing` otherwise |
| `toEitherPath(S, E)` | `EitherPath<E, A>` | `Right` if present, `Left(error)` otherwise |
| `toTryPath(S, Supplier<Throwable>)` | `TryPath<A>` | `Success` or `Failure` |
| `toOptionalPath(S)` | `OptionalPath<A>` | Wraps in Java `Optional` effect |

**Bridge Methods on TraversalPath:**

| Method | Return Type | Description |
|--------|-------------|-------------|
| `toListPath(S)` | `ListPath<A>` | All focused values as list |
| `toStreamPath(S)` | `StreamPath<A>` | Lazy stream of values |
| `toMaybePath(S)` | `MaybePath<A>` | First value if any |

### Direction 2: EffectPath.focus()

Apply structural navigation inside an effect context:

```java
// Start with an effect containing a complex structure
EitherPath<Error, User> userPath = Path.right(user);

// Navigate within the effect using optics
EitherPath<Error, String> namePath = userPath.focus(UserFocus.name());

// AffinePath requires an error for the absent case
EitherPath<Error, String> emailPath =
    userPath.focus(UserFocus.email(), new Error("Email required"));
```

**focus() Method Signatures:**

| Effect Type | FocusPath Signature | AffinePath Signature |
|-------------|---------------------|----------------------|
| `MaybePath<A>` | `focus(FocusPath<A, B>)` -> `MaybePath<B>` | `focus(AffinePath<A, B>)` -> `MaybePath<B>` |
| `EitherPath<E, A>` | `focus(FocusPath<A, B>)` -> `EitherPath<E, B>` | `focus(AffinePath<A, B>, E)` -> `EitherPath<E, B>` |
| `TryPath<A>` | `focus(FocusPath<A, B>)` -> `TryPath<B>` | `focus(AffinePath<A, B>, Supplier<Throwable>)` -> `TryPath<B>` |
| `IOPath<A>` | `focus(FocusPath<A, B>)` -> `IOPath<B>` | `focus(AffinePath<A, B>, Supplier<RuntimeException>)` -> `IOPath<B>` |
| `ValidationPath<E, A>` | `focus(FocusPath<A, B>)` -> `ValidationPath<E, B>` | `focus(AffinePath<A, B>, E)` -> `ValidationPath<E, B>` |
| `IdPath<A>` | `focus(FocusPath<A, B>)` -> `IdPath<B>` | `focus(AffinePath<A, B>)` -> `MaybePath<B>` |

### When to Use Each Direction

**Use FocusPath to EffectPath when:**
- You have data and want to start an effect pipeline
- Extracting values that need validation or async processing
- Converting optic results into monadic workflows

```java
// Extract and validate
EitherPath<ValidationError, String> validated =
    UserFocus.email()
        .toEitherPath(user, new ValidationError("Email required"))
        .via(email -> validateEmailFormat(email));
```

**Use EffectPath.focus() when:**
- You're already in an effect context (e.g., after a service call)
- Drilling down into effect results
- Building validation pipelines that extract and check nested fields

```java
// Service returns effect, then navigate
EitherPath<Error, Order> orderResult = orderService.findById(orderId);
EitherPath<Error, String> customerName =
    orderResult
        .focus(OrderFocus.customer())
        .focus(CustomerFocus.name());
```

### Practical Example: Validation Pipeline

Combining both directions for a complete validation workflow:

```java
// Domain model
record RegistrationForm(String username, Optional<String> email, Address address) {}
record Address(String street, Optional<String> postcode) {}

// Validation using Focus-Effect bridge
EitherPath<List<String>, RegistrationForm> validateForm(RegistrationForm form) {
    var formPath = Path.<List<String>, RegistrationForm>right(form);

    // Validate username (always present)
    var usernameValid = formPath
        .focus(FormFocus.username())
        .via(name -> name.length() >= 3
            ? Path.right(name)
            : Path.left(List.of("Username too short")));

    // Validate email if present
    var emailValid = formPath
        .focus(FormFocus.email(), List.of("Email required for notifications"))
        .via(email -> email.contains("@")
            ? Path.right(email)
            : Path.left(List.of("Invalid email format")));

    // Combine validations
    return usernameValid.via(u -> emailValid.map(e -> form));
}
```

~~~admonish tip title="See Also"
- [Effect Path Overview](../effect/effect_path_overview.md) - Railway model and effect composition
- [Focus-Effect Integration](../effect/focus_integration.md) - Complete bridging guide
- [Capability Interfaces](../effect/capabilities.md) - Powers behind effect operations
~~~

---

**Previous:** [Navigation and Composition](focus_navigation.md)
**Next:** [Custom Containers and Code Generation](focus_containers.md)
