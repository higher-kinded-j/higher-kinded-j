# Focus DSL Reference

~~~admonish info title="What You'll Learn"
- When to use Focus DSL vs manual optic composition
- Common patterns: batch updates, safe deep access, and validation
- Performance considerations and how to optimise hot paths
- Customising generated code: target packages, depth limits, and field filters
- Common pitfalls and how to avoid them
- Troubleshooting compilation errors and runtime issues
~~~

---

## Integration with Free Monad DSL

Focus paths integrate with `OpticPrograms` for complex workflows:

```java
// Build a program using Focus paths
Free<OpticOpKind.Witness, Company> program = OpticPrograms
    .get(company, CompanyFocus.name().toLens())
    .flatMap(name -> {
        if (name.startsWith("Acme")) {
            return OpticPrograms.modifyAll(
                company,
                CompanyFocus.departments().employees().age().toTraversal(),
                age -> age + 1
            );
        } else {
            return OpticPrograms.pure(company);
        }
    });

// Execute with interpreter
Company result = OpticInterpreters.direct().run(program);
```

---

## When to Use Focus DSL vs Manual Composition

### Use Focus DSL When:

- **Navigating deeply nested structures** with many levels
- **IDE autocomplete is important** for discoverability

```java
// Focus DSL - clear intent, discoverable
List<String> emails = CompanyFocus
    .departments()
    .employees()
    .email()
    .getAll(company);
```

### Use Manual Composition When:

- **Custom optics** (computed properties, validated updates)
- **Performance-critical code** (avoid intermediate allocations)
- **Reusable optic libraries** (compose once, use everywhere)
- **Complex conditional logic** in the optic itself

```java
// Manual composition - more control, reusable
public static final Lens<Company, String> CEO_NAME =
    CompanyLenses.ceo()
        .andThen(ExecutiveLenses.person())
        .andThen(PersonLenses.fullName());
```

### Hybrid Approach (Recommended)

Use Focus DSL for navigation, then extract for reuse:

```java
// Use Focus for exploration
var path = CompanyFocus.departments().employees().email();

// Extract and store the composed optic
public static final Traversal<Company, String> ALL_EMAILS =
    path.toTraversal();

// Reuse the extracted optic
List<String> emails = Traversals.getAll(ALL_EMAILS, company);
```

---

## Common Patterns

### Pattern 1: Batch Updates

```java
// Give all employees in Engineering a raise
Company updated = CompanyFocus
    .departments()
    .filter(d -> d.name().equals("Engineering"))
    .employees()
    .salary()
    .modifyAll(s -> s.multiply(new BigDecimal("1.10")), company);
```

### Pattern 2: Safe Deep Access

```java
// Safely access deeply nested optional
Optional<String> managerEmail = CompanyFocus
    .department(0)
    .manager()
    .email()
    .getOptional(company);

// Handle absence gracefully
String email = managerEmail.orElse("no-manager@company.com");
```

### Pattern 3: Validation with Focus

```java
// Validate all employee ages
Validated<List<String>, Company> result = OpticOps.modifyAllValidated(
    company,
    CompanyFocus.departments().employees().age().toTraversal(),
    age -> age >= 18 && age <= 100
        ? Validated.valid(age)
        : Validated.invalid("Invalid age: " + age)
);
```

---

## Performance Considerations

Focus paths add a thin abstraction layer over raw optics:

- **Path creation**: Minimal overhead (simple wrapper objects)
- **Traversal**: Identical to underlying optic performance
- **Memory**: One additional object per path segment

**Best Practice**: For hot paths, extract the underlying optic:

```java
// Cold path - Focus DSL is fine
var result = CompanyFocus.departments().name().getAll(company);

// Hot path - extract and cache the optic
private static final Traversal<Company, String> DEPT_NAMES =
    CompanyFocus.departments().name().toTraversal();

for (Company c : manyCompanies) {
    var names = Traversals.getAll(DEPT_NAMES, c);  // Faster
}
```

---

## Customising Generated Code

### Target Package

```java
@GenerateFocus(targetPackage = "com.myapp.optics.focus")
record User(String name) {}
// Generates: com.myapp.optics.focus.UserFocus
```

### Navigator Generation

Enable fluent cross-type navigation with generated navigator classes:

```java
@GenerateFocus(generateNavigators = true)
record Company(String name, Address headquarters) {}

@GenerateFocus(generateNavigators = true)
record Address(String street, String city) {}

// Now navigate fluently without .via():
String city = CompanyFocus.headquarters().city().get(company);
```

### Navigator Depth Limiting

Control how deep navigator generation goes with `maxNavigatorDepth`:

```java
@GenerateFocus(generateNavigators = true, maxNavigatorDepth = 2)
record Organisation(Division division) {}

// Depth 1: Returns DivisionNavigator
// Depth 2: Returns FocusPath (not a navigator)
// Beyond: Use .via() for further navigation
```

### Field Filtering

Control which fields get navigator generation:

```java
// Only generate navigators for specific fields
@GenerateFocus(generateNavigators = true, includeFields = {"homeAddress"})
record Person(String name, Address homeAddress, Address workAddress) {}

// Or exclude specific fields
@GenerateFocus(generateNavigators = true, excludeFields = {"backup"})
record Config(Settings main, Settings backup) {}
```

---

## Lens Fallback for Non-Annotated Types

When navigating to a type without `@GenerateFocus`, you can continue with `.via()`:

```java
// ThirdPartyRecord doesn't have @GenerateFocus
@GenerateLenses
@GenerateFocus
record MyRecord(ThirdPartyRecord external) {}

// Navigate as far as Focus allows, then use .via() with existing lens
FocusPath<MyRecord, ThirdPartyRecord> externalPath = MyRecordFocus.external();
FocusPath<MyRecord, String> deepPath = externalPath.via(ThirdPartyLenses.someField());
```

---

## Common Pitfalls

### Don't: Recreate paths in loops

```java
// Bad - creates new path objects each iteration
for (Company c : companies) {
    var names = CompanyFocus.departments().name().getAll(c);
}
```

### Do: Extract and reuse

```java
// Good - create path once
var deptNames = CompanyFocus.departments().name();
for (Company c : companies) {
    var names = deptNames.getAll(c);
}
```

### Don't: Ignore the path type

```java
// Confusing - what does this return?
var result = somePath.get(source);  // Might fail if AffinePath!
```

### Do: Use the appropriate method

```java
// Clear - FocusPath always has a value
String name = namePath.get(employee);

// Clear - AffinePath might be empty
Optional<String> email = emailPath.getOptional(employee);

// Clear - TraversalPath has multiple values
List<String> names = namesPath.getAll(department);
```

---

## Troubleshooting and FAQ

### Compilation Errors

#### "Cannot infer type arguments for traverseOver"

**Problem:**
```java
// This fails to compile
TraversalPath<User, Role> allRoles = rolesPath.traverseOver(ListTraverse.INSTANCE);
```

**Solution:** Provide explicit type parameters:
```java
// Add explicit type witnesses
TraversalPath<User, Role> allRoles =
    rolesPath.<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);
```

Java's type inference struggles with higher-kinded types. Explicit type parameters help the compiler.

#### "Incompatible types when chaining .each().via()"

**Problem:**
```java
// Type inference fails on long chains
TraversalPath<Company, Integer> salaries =
    FocusPath.of(companyDeptLens).each().via(deptEmployeesLens).each().via(salaryLens);
```

**Solution:** Break the chain into intermediate variables:
```java
// Use intermediate variables
TraversalPath<Company, Department> depts = FocusPath.of(companyDeptLens).each();
TraversalPath<Company, Employee> employees = depts.via(deptEmployeesLens).each();
TraversalPath<Company, Integer> salaries = employees.via(salaryLens);
```

#### "Sealed or non-sealed local classes are not allowed"

**Problem:** Defining sealed interfaces inside test methods fails:
```java
@Test void myTest() {
    sealed interface Wrapper permits A, B {}  // Compilation error!
    record A() implements Wrapper {}
}
```

**Solution:** Move sealed interfaces to class level:
```java
class MyTest {
    sealed interface Wrapper permits A, B {}
    record A() implements Wrapper {}

    @Test void myTest() {
        // Use Wrapper here
    }
}
```

#### "Method reference ::new doesn't work with single-field records as BiFunction"

**Problem:**
```java
// This fails for single-field records
Lens<Outer, Inner> lens = Lens.of(Outer::inner, Outer::new);  // Error!
```

**Solution:** Use explicit lambda:
```java
Lens<Outer, Inner> lens = Lens.of(Outer::inner, (o, i) -> new Outer(i));
```

### Runtime Issues

#### "getAll() returns empty unexpectedly"

**Checklist:**
1. Check if the AffinePath in the chain has focus (use `matches()` to verify)
2. Verify `instanceOf()` matches the actual runtime type
3. Ensure the source data actually contains elements

```java
// Debug with traced()
TraversalPath<User, Role> traced = rolesPath.traced(
    (user, roles) -> System.out.println("Found " + roles.size() + " roles")
);
List<Role> roles = traced.getAll(user);
```

#### "modifyAll() doesn't change anything"

**Causes:**
- The traversal has no focus (AffinePath didn't match)
- The predicate in `modifyWhen()` never matches
- The source collection is empty

```java
// Check focus exists
int count = path.count(source);
System.out.println("Path focuses on " + count + " elements");
```

### FAQ

#### Q: When should I use `each()` vs `traverseOver()`?

| Scenario | Use |
|----------|-----|
| Field is `List<T>` | `each()` |
| Field is `Set<T>` | `each()` |
| Field is `Kind<ListKind.Witness, T>` | `traverseOver(ListTraverse.INSTANCE)` |
| Field is `Kind<MaybeKind.Witness, T>` | `traverseOver(MaybeTraverse.INSTANCE)` |
| Custom traversable type | `traverseOver(YourTraverse.INSTANCE)` |

#### Q: Why use `MaybeMonad.INSTANCE` for `modifyF()` instead of a dedicated Applicative?

`MaybeMonad` extends `Applicative`, so it works for `modifyF()`. Higher-Kinded-J doesn't provide a separate `MaybeApplicative` because:
- Monad already provides all Applicative operations
- Having one instance simplifies the API
- Most effects you'll use with `modifyF()` are monadic anyway

```java
// Use MaybeMonad for Maybe-based validation
Kind<MaybeKind.Witness, Config> result =
    keyPath.modifyF(validateKey, config, MaybeMonad.INSTANCE);
```

#### Q: Can I use Focus DSL with third-party types?

Yes, use `.via()` to compose with manually created optics:

```java
// Create lens for third-party type
Lens<ThirdPartyType, String> fieldLens = Lens.of(
    ThirdPartyType::getField,
    (obj, value) -> obj.toBuilder().field(value).build()
);

// Compose with Focus path
FocusPath<MyRecord, String> path = MyRecordFocus.external().via(fieldLens);
```

#### Q: How do I handle nullable fields?

The Focus DSL provides four approaches for handling nullable fields, from most to least automated:

**Option 1: Use `@Nullable` annotation (Recommended)**

Annotate nullable fields with `@Nullable` from JSpecify, JSR-305, or similar. The processor automatically generates `AffinePath` with null-safe access:

```java
import org.jspecify.annotations.Nullable;

@GenerateFocus
record User(String name, @Nullable String nickname) {}

// Generated: AffinePath that handles null automatically
AffinePath<User, String> nicknamePath = UserFocus.nickname();

User user = new User("Alice", null);
Optional<String> result = nicknamePath.getOptional(user);  // Optional.empty()

User withNick = new User("Bob", "Bobby");
Optional<String> present = nicknamePath.getOptional(withNick);  // Optional.of("Bobby")
```

Supported nullable annotations:
- `org.jspecify.annotations.Nullable`
- `javax.annotation.Nullable`
- `jakarta.annotation.Nullable`
- `org.jetbrains.annotations.Nullable`
- `androidx.annotation.Nullable`
- `edu.umd.cs.findbugs.annotations.Nullable`

**Option 2: Use `.nullable()` method**

For existing `FocusPath` instances, chain with `.nullable()` to handle nulls:

```java
// If you have a FocusPath to a nullable field
FocusPath<LegacyUser, String> rawPath = LegacyUserFocus.nickname();

// Chain with nullable() for null-safe access
AffinePath<LegacyUser, String> safePath = rawPath.nullable();

Optional<String> result = safePath.getOptional(user);  // Empty if null
```

**Option 3: Use `AffinePath.ofNullable()` factory**

For manual creation without code generation:

```java
// Create a nullable-aware AffinePath directly
AffinePath<User, String> nicknamePath = AffinePath.ofNullable(
    User::nickname,
    (user, nickname) -> new User(user.name(), nickname)
);
```

**Option 4: Wrap in `Optional` (Alternative design)**

If you control the data model, consider using `Optional<T>` instead of nullable fields:

```java
// Model absence explicitly with Optional
record User(String name, Optional<String> email) {}

// Focus DSL handles it naturally with .some()
AffinePath<User, String> emailPath = UserFocus.email();  // Uses .some() internally
```

#### Q: What's the performance overhead of Focus DSL?

- **Path creation**: Negligible (thin wrapper objects)
- **Operations**: Same as underlying optics
- **Hot paths**: Extract and cache the optic

```java
// For performance-critical code, cache the extracted optic
private static final Traversal<Company, String> EMPLOYEE_NAMES =
    CompanyFocus.departments().employees().name().toTraversal();

// Use the cached optic in hot loops
for (Company c : companies) {
    List<String> names = Traversals.getAll(EMPLOYEE_NAMES, c);
}
```

#### Q: Can I create Focus paths programmatically (at runtime)?

Focus paths are designed for compile-time type safety. For runtime-dynamic paths, use the underlying optics directly:

```java
// Build optics dynamically
Traversal<JsonNode, String> dynamicPath = buildTraversalFromJsonPath(jsonPathString);

// Wrap in a path if needed
TraversalPath<JsonNode, String> path = TraversalPath.of(dynamicPath);
```

---

~~~admonish tip title="Further Reading"
- **Monocle**: [Focus DSL](https://www.optics.dev/Monocle/docs/focus) - Scala's equivalent, inspiration for this design
~~~

~~~admonish tip title="See Also"
- [Focus DSL](focus_dsl.md) - Core concepts and path types
- [Lenses](lenses.md) - Foundation concepts
- [Fluent API](fluent_api.md) - Alternative fluent patterns
- [Free Monad DSL](free_monad_dsl.md) - Composable optic programs
~~~

~~~admonish info title="Hands-On Learning"
Practice the Focus DSL in:
- [Tutorial 12: Focus DSL](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial12_FocusDSL.java) (10 exercises, ~10 minutes)
- [Tutorial 13: Advanced Focus DSL](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial13_AdvancedFocusDSL.java) (8 exercises, ~10 minutes)
- [Tutorial 19: Navigator Generation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial19_NavigatorGeneration.java) (7 exercises, ~10 minutes)
- [Tutorial 20: Custom Container Navigation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial20_ContainerNavigation.java) (4 exercises, ~10 minutes).
~~~

---

**Previous:** [Custom Containers and Code Generation](focus_containers.md)
**Next:** [Optics for External Types](importing_optics.md)
