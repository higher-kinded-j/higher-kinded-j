# Focus DSL: Path-Based Optic Syntax

## _Type-Safe Navigation Through Nested Data_

~~~admonish info title="What You'll Learn"
- How to navigate deeply nested data structures with type-safe paths
- Using `@GenerateFocus` to generate path builders automatically
- **Fluent cross-type navigation** with generated navigators (no `.via()` needed)
- The difference between `FocusPath`, `AffinePath`, and `TraversalPath`
- Collection navigation with `.each()`, `.at()`, `.some()`, `.nullable()`, and `.traverseOver()`
- **Seamless nullable field handling** with `@Nullable` annotation detection
- Type class integration: effectful operations, monoid aggregation, and Traverse support
- Working with sum types using `instanceOf()` and conditional modification with `modifyWhen()`
- Composing Focus paths with existing optics
- Debugging paths with `traced()`
- When to use Focus DSL vs manual lens composition
~~~

~~~admonish title="Example Code"
[NavigatorExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/NavigatorExample.java) | [TraverseIntegrationExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/TraverseIntegrationExample.java) | [ValidationPipelineExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/ValidationPipelineExample.java)
~~~

The Focus DSL provides a fluent, path-based syntax for working with optics. Instead of manually composing lenses, prisms, and traversals, you can navigate through your data structures using intuitive method chains that mirror the shape of your data.

---

## The Problem: Verbose Manual Composition

When working with deeply nested data structures, manual optic composition becomes verbose:

```java
// Manual composition - verbose and repetitive
Lens<Company, String> employeeNameLens =
    CompanyLenses.departments()
        .andThen(DepartmentLenses.employees())
        .andThen(EmployeeLenses.name());

// Must compose at each use site
String name = employeeNameLens.get(company);
```

With the Focus DSL, the same operation becomes:

```java
// Focus DSL - fluent and intuitive
String name = CompanyFocus.departments().employees().name().get(company);
```

---

## Think of Focus Paths Like...

- **File system paths**: `/company/departments/employees/name`
- **JSON pointers**: `$.departments[*].employees[*].name`
- **XPath expressions**: `//department/employee/name`
- **IDE navigation**: Click through nested fields with autocomplete

The key difference: Focus paths are fully type-safe, with compile-time checking at every step.

---

## A Step-by-Step Walkthrough

### Step 1: Annotate Your Records

Add `@GenerateFocus` alongside `@GenerateLenses` to generate path builders:

```java
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateFocus;

@GenerateLenses
@GenerateFocus
public record Company(String name, List<Department> departments) {}

@GenerateLenses
@GenerateFocus
public record Department(String name, List<Employee> employees) {}

@GenerateLenses
@GenerateFocus
public record Employee(String name, int age, Optional<String> email) {}
```

### Step 2: Use Generated Focus Classes

The annotation processor generates companion Focus classes with path builders:

```java
// Generated: CompanyFocus.java
// Navigate to company name
FocusPath<Company, String> namePath = CompanyFocus.name();
String companyName = namePath.get(company);

// Navigate through collections
TraversalPath<Company, Department> deptPath = CompanyFocus.departments();
List<Department> allDepts = deptPath.getAll(company);

// Navigate to specific index
AffinePath<Company, Department> firstDeptPath = CompanyFocus.department(0);
Optional<Department> firstDept = firstDeptPath.getOptional(company);
```

### Step 3: Chain Navigation Methods

Focus paths support fluent chaining for deep navigation:

```java
// Deep path through collections
TraversalPath<Company, String> allEmployeeNames =
    CompanyFocus.departments()     // TraversalPath<Company, Department>
        .employees()               // TraversalPath<Company, Employee>
        .name();                   // TraversalPath<Company, String>

// Get all employee names across all departments
List<String> names = allEmployeeNames.getAll(company);

// Modify all employee names
Company updated = allEmployeeNames.modifyAll(String::toUpperCase, company);
```

---

## The Three Path Types

Focus DSL provides three path types, mirroring the optic hierarchy:

```
         FocusPath<S, A>
        (exactly one focus)
               |
        AffinePath<S, A>
      (zero or one focus)
               |
      TraversalPath<S, A>
      (zero or more focus)
```

### FocusPath: Exactly One Element

`FocusPath<S, A>` wraps a `Lens<S, A>` and guarantees exactly one focused element:

```java
// Always succeeds - the field always exists
FocusPath<Employee, String> namePath = EmployeeFocus.name();

String name = namePath.get(employee);           // Always returns a value
Employee updated = namePath.set("Bob", employee);  // Always succeeds
Employee modified = namePath.modify(String::toUpperCase, employee);
```

**Key Operations:**

| Method | Return Type | Description |
|--------|-------------|-------------|
| `get(S)` | `A` | Extract the focused value |
| `set(A, S)` | `S` | Replace the focused value |
| `modify(Function<A,A>, S)` | `S` | Transform the focused value |
| `toLens()` | `Lens<S, A>` | Extract underlying optic |

### AffinePath: Zero or One Element

`AffinePath<S, A>` wraps an `Affine<S, A>` for optional access:

```java
// May or may not have a value
AffinePath<Employee, String> emailPath = EmployeeFocus.email();

Optional<String> email = emailPath.getOptional(employee);  // May be empty
Employee updated = emailPath.set("new@email.com", employee);  // Always succeeds
Employee modified = emailPath.modify(String::toLowerCase, employee);  // No-op if absent
```

**Key Operations:**

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getOptional(S)` | `Optional<A>` | Extract if present |
| `set(A, S)` | `S` | Replace (creates if structure allows) |
| `modify(Function<A,A>, S)` | `S` | Transform if present |
| `matches(S)` | `boolean` | Check if value exists |
| `toAffine()` | `Affine<S, A>` | Extract underlying optic |

### TraversalPath: Zero or More Elements

`TraversalPath<S, A>` wraps a `Traversal<S, A>` for collection access:

```java
// Focuses on multiple elements
TraversalPath<Department, Employee> employeesPath = DepartmentFocus.employees();

List<Employee> all = employeesPath.getAll(department);    // All employees
Department updated = employeesPath.setAll(defaultEmployee, department);
Department modified = employeesPath.modifyAll(e -> promote(e), department);
```

**Key Operations:**

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getAll(S)` | `List<A>` | Extract all focused values |
| `setAll(A, S)` | `S` | Replace all focused values |
| `modifyAll(Function<A,A>, S)` | `S` | Transform all focused values |
| `filter(Predicate<A>)` | `TraversalPath<S, A>` | Filter focused elements |
| `toTraversal()` | `Traversal<S, A>` | Extract underlying optic |

---

## Collection Navigation

The Focus DSL provides intuitive methods for navigating collections:

### `.each()` - Traverse All Elements

Converts a collection field to a traversal over its elements:

```java
// List<Department> -> traversal over Department
TraversalPath<Company, Department> allDepts = CompanyFocus.departments();

// Equivalent to calling .each() on a FocusPath to List<T>
```

### `.at(index)` - Access by Index

Focuses on a single element at a specific index:

```java
// Focus on first department
AffinePath<Company, Department> firstDept = CompanyFocus.department(0);

// Focus on third employee in second department
AffinePath<Company, Employee> specificEmployee =
    CompanyFocus.department(1).employee(2);

// Returns empty if index out of bounds
Optional<Department> maybe = firstDept.getOptional(emptyCompany);
```

### `.atKey(key)` - Access Map Values

For `Map<K, V>` fields, access values by key:

```java
@GenerateLenses
@GenerateFocus
record Config(Map<String, Setting> settings) {}

// Focus on specific setting
AffinePath<Config, Setting> dbSetting = ConfigFocus.setting("database");

Optional<Setting> setting = dbSetting.getOptional(config);
```

### `.some()` - Unwrap Optional

For `Optional<T>` fields, unwrap to the inner value:

```java
// Email is Optional<String>
AffinePath<Employee, String> emailPath = EmployeeFocus.email();

// Internally uses .some() to unwrap the Optional
Optional<String> email = emailPath.getOptional(employee);
```

### `.nullable()` - Handle Null Values

For fields that may be null, use `.nullable()` to treat null as absent:

```java
record LegacyUser(String name, @Nullable String nickname) {}

// If @Nullable is detected, the processor generates this automatically
// Otherwise, chain with .nullable() manually:
FocusPath<LegacyUser, String> rawPath = LegacyUserFocus.nickname();
AffinePath<LegacyUser, String> safePath = rawPath.nullable();

// Null is treated as absent (empty Optional)
LegacyUser user = new LegacyUser("Alice", null);
Optional<String> result = safePath.getOptional(user);  // Optional.empty()

// Non-null values work normally
LegacyUser withNick = new LegacyUser("Bob", "Bobby");
Optional<String> present = safePath.getOptional(withNick);  // Optional.of("Bobby")
```

~~~admonish tip title="Automatic @Nullable Detection"
When a field is annotated with `@Nullable` (from JSpecify, JSR-305, JetBrains, etc.), the `@GenerateFocus` processor automatically generates an `AffinePath` with `.nullable()` applied. No manual chaining required.
~~~

---

## Composition with Existing Optics

Focus paths compose seamlessly with existing optics using `.via()`:

### Path + Lens = Path (or Affine)

```java
// Existing lens for a computed property
Lens<Employee, String> fullNameLens = Lens.of(
    e -> e.firstName() + " " + e.lastName(),
    (e, name) -> { /* setter logic */ }
);

// Compose Focus path with existing lens
FocusPath<Department, String> employeeFullName =
    DepartmentFocus.manager().via(fullNameLens);
```

### Path + Prism = AffinePath

```java
// Prism for sealed interface variant
Prism<Shape, Circle> circlePrism = ShapePrisms.circle();

// Compose to get AffinePath
AffinePath<Drawing, Circle> firstCircle =
    DrawingFocus.shape(0).via(circlePrism);
```

### Path + Traversal = TraversalPath

```java
// Custom traversal
Traversal<String, Character> charsTraversal = StringTraversals.chars();

// Compose for character-level access
TraversalPath<Employee, Character> nameChars =
    EmployeeFocus.name().via(charsTraversal);
```

---

## Fluent Navigation with Generated Navigators

~~~admonish tip title="Zero-Boilerplate Cross-Type Navigation"
When navigating across multiple record types, the standard approach requires explicit `.via()` calls at each boundary. Generated navigators eliminate this boilerplate, enabling chains like `CompanyFocus.headquarters().city()` directly.
~~~

### The Problem: Explicit Composition

Without navigators, cross-type navigation requires `.via()` at each type boundary:

```java
// Without navigators - explicit .via() at each step
String city = CompanyFocus.headquarters()
    .via(AddressFocus.city().toLens())
    .get(company);

// Deep navigation becomes verbose
String managerCity = CompanyFocus.departments()
    .each()
    .via(DepartmentFocus.manager().toLens())
    .via(PersonFocus.address().toLens())
    .via(AddressFocus.city().toLens())
    .get(company);
```

### The Solution: Generated Navigators

Enable navigator generation with `generateNavigators = true`:

```java
@GenerateFocus(generateNavigators = true)
record Company(String name, Address headquarters) {}

@GenerateFocus(generateNavigators = true)
record Address(String street, String city) {}

// With navigators - fluent chaining
String city = CompanyFocus.headquarters().city().get(company);

// Navigators chain naturally
Company updated = CompanyFocus.headquarters().city()
    .modify(String::toUpperCase, company);
```

### How Navigators Work

When `generateNavigators = true`, the processor generates navigator wrapper classes for fields whose types are also annotated with `@GenerateFocus`. The generated code looks like:

```java
// Generated in CompanyFocus.java
public static HeadquartersNavigator<Company> headquarters() {
    return new HeadquartersNavigator<>(
        FocusPath.of(Lens.of(Company::headquarters, ...))
    );
}

// Generated inner class
public static final class HeadquartersNavigator<S> {
    private final FocusPath<S, Address> delegate;

    // Delegate methods - same as FocusPath
    public Address get(S source) { return delegate.get(source); }
    public S set(Address value, S source) { return delegate.set(value, source); }
    public S modify(Function<Address, Address> f, S source) { ... }

    // Navigation methods for Address fields
    public FocusPath<S, String> street() {
        return delegate.via(AddressFocus.street().toLens());
    }

    public FocusPath<S, String> city() {
        return delegate.via(AddressFocus.city().toLens());
    }

    // Access underlying path
    public FocusPath<S, Address> toPath() { return delegate; }
}
```

### Path Type Widening

Navigators automatically widen path types when navigating through optional or collection fields:

| Source Field Type | Navigator Returns |
|-------------------|-------------------|
| Regular field (`Address address`) | `FocusPath` methods |
| Optional field (`Optional<Address>`) | `AffinePath` methods |
| Collection field (`List<Address>`) | `TraversalPath` methods |

```java
@GenerateFocus(generateNavigators = true)
record User(String name, Optional<Address> homeAddress, List<Address> workAddresses) {}

// homeAddress navigator methods return AffinePath
Optional<String> homeCity = UserFocus.homeAddress().city().getOptional(user);

// workAddresses navigator methods return TraversalPath
List<String> workCities = UserFocus.workAddresses().city().getAll(user);
```

### Controlling Navigator Generation

#### Depth Limiting

Prevent excessive code generation for deeply nested structures:

```java
@GenerateFocus(generateNavigators = true, maxNavigatorDepth = 2)
record Root(Level1 child) {}

// Depth 1: child() returns Level1Navigator
// Depth 2: child().nested() returns FocusPath (not a navigator)
// Beyond depth 2: use .via() for further navigation

FocusPath<Root, String> deepPath = RootFocus.child().nested()
    .via(Level3Focus.value().toLens());
```

#### Field Filtering

Include only specific fields in navigator generation:

```java
@GenerateFocus(generateNavigators = true, includeFields = {"primary"})
record MultiAddress(Address primary, Address secondary, Address backup) {}

// primary() returns navigator with navigation methods
// secondary() and backup() return standard FocusPath<MultiAddress, Address>
```

Or exclude specific fields:

```java
@GenerateFocus(generateNavigators = true, excludeFields = {"internal"})
record Config(Settings user, Settings internal) {}

// user() returns navigator
// internal() returns standard FocusPath (no nested navigation)
```

### When to Use Navigators

**Enable navigators when:**
- Navigating across multiple record types frequently
- Deep navigation is common in your codebase
- You want IDE autocomplete for nested fields
- Teaching or onboarding developers

**Keep navigators disabled when:**
- Fields reference third-party types (not annotated with `@GenerateFocus`)
- You need minimal generated code footprint
- The project has shallow data structures

### Combining Navigators with Other Features

Navigators work seamlessly with all Focus DSL features:

```java
// With type class operations
Company validated = CompanyFocus.headquarters().city()
    .modifyF(this::validateCity, company, EitherMonad.INSTANCE);

// With conditional modification
Company updated = CompanyFocus.departments()
    .each()
    .modifyWhen(d -> d.name().equals("Engineering"), this::promote, company);

// With tracing for debugging
FocusPath<Company, String> traced = CompanyFocus.headquarters().city()
    .traced((company, city) -> System.out.println("City: " + city));
```

---

## Type Class Integration

The Focus DSL integrates deeply with higher-kinded-j type classes, enabling effectful operations, monoid-based aggregation, and generic collection traversal.

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

## Generated Class Structure

For a record like:

```java
@GenerateLenses
@GenerateFocus
record Employee(
    String name,
    int age,
    Optional<String> email,
    @Nullable String nickname,
    List<Skill> skills
) {}
```

The processor generates:

```java
@Generated
public final class EmployeeFocus {
    private EmployeeFocus() {}

    // Required fields -> FocusPath
    public static FocusPath<Employee, String> name() {
        return FocusPath.of(EmployeeLenses.name());
    }

    public static FocusPath<Employee, Integer> age() {
        return FocusPath.of(EmployeeLenses.age());
    }

    // Optional<T> field -> AffinePath (automatically unwraps with .some())
    public static AffinePath<Employee, String> email() {
        return FocusPath.of(EmployeeLenses.email()).some();
    }

    // @Nullable field -> AffinePath (automatically handles null with .nullable())
    public static AffinePath<Employee, String> nickname() {
        return FocusPath.of(EmployeeLenses.nickname()).nullable();
    }

    // List<T> field -> TraversalPath (traverses elements)
    public static TraversalPath<Employee, Skill> skills() {
        return FocusPath.of(EmployeeLenses.skills()).each();
    }

    // Indexed access to List<T> -> AffinePath
    public static AffinePath<Employee, Skill> skill(int index) {
        return FocusPath.of(EmployeeLenses.skills()).at(index);
    }
}
```

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
- **Teaching or onboarding** developers new to optics
- **Prototyping** before optimising for performance

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
- [Lenses](lenses.md) - Foundation concepts
- [Fluent API](fluent_api.md) - Alternative fluent patterns
- [Free Monad DSL](free_monad_dsl.md) - Composable optic programs
~~~

---

**Previous:** [Introduction](ch4_intro.md)
**Next:** [Fluent API](fluent_api.md)
