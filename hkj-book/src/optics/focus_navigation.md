# Focus DSL: Navigation and Composition

~~~admonish info title="What You'll Learn"
- Collection navigation with `.each()`, `.each(Each)`, `.at()`, `.atKey()`, `.some()`, `.some(Affine)`, and `.nullable()`
- List decomposition with `.via()` and `ListPrisms`
- Composing Focus paths with existing Lenses, Prisms, and Traversals
- Fluent cross-type navigation with generated navigators (no `.via()` needed)
- SPI-aware path widening and compound widening rules
- Controlling navigator generation with depth limits and field filters
~~~

~~~admonish title="Hands On Practice"
[Tutorial12_FocusDSL.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial12_FocusDSL.java) | [Tutorial19_NavigatorGeneration.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial19_NavigatorGeneration.java)
~~~

~~~admonish title="Example Code"
[NavigatorExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/NavigatorExample.java) | [ContainerNavigationExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/ContainerNavigationExample.java)
~~~

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

### `.each(Each)` - Traverse with Custom Each Instance

For containers that aren't automatically recognised by `.each()`, provide an explicit `Each` instance:

```java
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.extensions.EachExtensions;

// Custom container with Each instance
record Container(Map<String, Value> values) {}

Lens<Container, Map<String, Value>> valuesLens =
    Lens.of(Container::values, (c, v) -> new Container(v));

// Use mapValuesEach() to traverse map values
TraversalPath<Container, Value> allValues =
    FocusPath.of(valuesLens).each(EachInstances.mapValuesEach());

// HKT types via EachExtensions
record Wrapper(Maybe<Config> config) {}

TraversalPath<Wrapper, Config> maybeConfig =
    FocusPath.of(configLens).each(EachExtensions.maybeEach());
```

This method is available on `FocusPath`, `AffinePath`, and `TraversalPath`, enabling fluent navigation through any container type with an `Each` instance.

~~~admonish tip title="See Also"
For available `Each` instances and how to create custom ones, see [Each Typeclass](each_typeclass.md).
~~~

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

### `.some(Affine)` - Navigate SPI Container Types

For container types registered via the `TraversableGenerator` SPI that hold zero or one element, provide an `Affine` instance to focus on the contained value:

```java
import org.higherkindedj.optics.util.Affines;

// Either<String, Integer> field - focus on the Right value
record Config(int port, Either<String, Integer> timeout) {}

Lens<Config, Either<String, Integer>> timeoutLens =
    Lens.of(Config::timeout, (c, t) -> new Config(c.port(), t));

AffinePath<Config, Integer> timeoutPath =
    FocusPath.of(timeoutLens).some(Affines.eitherRight());

// Get: returns Optional.of(30) for Right(30), Optional.empty() for Left("disabled")
Optional<Integer> value = timeoutPath.getOptional(config);

// Set: replaces the Right value, no-op if Left
Config updated = timeoutPath.set(60, config);
```

The following `Affine` instances are provided for built-in SPI types:

| Container type | Affine instance | Focuses on |
|----------------|-----------------|------------|
| `Either<L, R>` | `Affines.eitherRight()` | The `Right` value |
| `Try<A>` | `Affines.trySuccess()` | The `Success` value |
| `Validated<E, A>` | `Affines.validatedValid()` | The `Valid` value |
| `Maybe<A>` | `Affines.just()` | The `Just` value |

When `@GenerateFocus` is used on a record with these field types, the generated Focus methods automatically call `.some(...)` with the appropriate `Affine`, producing an `AffinePath`.

~~~admonish tip title="See Also"
For a runnable example covering all container types, see [ContainerNavigationExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/ContainerNavigationExample.java).
~~~

### List Decomposition with `.via()`

For list decomposition patterns (cons/snoc), compose with `ListPrisms` using `.via()`:

```java
import org.higherkindedj.optics.util.ListPrisms;

// Focus on the first item in a container
TraversalPath<Container, Item> items = ContainerFocus.items();
AffinePath<Container, Item> firstItem = items.via(ListPrisms.head());
Optional<Item> first = firstItem.getOptional(container);

// Focus on the last element
AffinePath<Container, Item> lastItem = items.via(ListPrisms.last());

// Pattern match with cons (head, tail)
TraversalPath<Container, Pair<Item, List<Item>>> consPath = items.via(ListPrisms.cons());
consPath.preview(container).ifPresent(pair -> {
    Item head = pair.first();
    List<Item> tail = pair.second();
    // Process head and tail...
});

// Alternative: use headOption() for first element access
AffinePath<Container, Item> firstViaHeadOption = items.headOption();
```

| ListPrisms Method | Type | Description |
|-------------------|------|-------------|
| `ListPrisms.head()` | `Affine<List<A>, A>` | Focus on first element |
| `ListPrisms.last()` | `Affine<List<A>, A>` | Focus on last element |
| `ListPrisms.tail()` | `Affine<List<A>, List<A>>` | Focus on all but first |
| `ListPrisms.init()` | `Affine<List<A>, List<A>>` | Focus on all but last |
| `ListPrisms.cons()` | `Prism<List<A>, Pair<A, List<A>>>` | Decompose as (head, tail) |
| `ListPrisms.snoc()` | `Prism<List<A>, Pair<List<A>, A>>` | Decompose as (init, last) |

~~~admonish tip title="See Also"
For comprehensive documentation on list decomposition patterns, including stack-safe operations for large lists, see [List Decomposition](list_decomposition.md).
~~~

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

### SPI-Aware Navigator Path Widening

The path widening table above covers `Optional`, `Maybe`, `List`, `Set`, and `Collection` types, which are recognised by hardcoded checks. However, many useful container types are registered through the `TraversableGenerator` SPI, and these are also recognised for navigator widening.

Each SPI generator declares a `Cardinality` value that determines the navigator path type:

| Cardinality | Navigator Path | Types |
|-------------|---------------|-------|
| `ZERO_OR_ONE` | `AffinePath` | `Either<L,R>`, `Try<A>`, `Validated<E,A>`, `Optional<A>`, `Maybe<A>` |
| `ZERO_OR_MORE` | `TraversalPath` | `Map<K,V>`, arrays, Eclipse Collections, Guava, Vavr, Apache Commons |

For example, a record with a `Map` field and an `Either` field:

```java
@GenerateFocus(generateNavigators = true)
record Warehouse(String name, Map<String, Integer> inventory, Either<String, String> verifiedName) {}

// inventory -> TraversalPath (Map is ZERO_OR_MORE via MapValueGenerator SPI)
List<Integer> quantities = WarehouseFocus.inventory().getAll(warehouse);

// verifiedName -> AffinePath (Either is ZERO_OR_ONE via EitherGenerator SPI)
Optional<String> verified = WarehouseFocus.verifiedName().getOptional(warehouse);
```

#### Compound Widening

When navigating through multiple container types, the path widens according to lattice rules:

| Current | + Field | = Result |
|---------|---------|----------|
| FOCUS | AFFINE | AFFINE |
| FOCUS | TRAVERSAL | TRAVERSAL |
| AFFINE | AFFINE | AFFINE |
| AFFINE | TRAVERSAL | TRAVERSAL |
| TRAVERSAL | anything | TRAVERSAL |

For instance, navigating through `Optional<Address>` (AFFINE) where `Address` has a `Map<String, String>` field (TRAVERSAL via SPI) produces a `TraversalPath`:

```java
@GenerateFocus(generateNavigators = true)
record Company(String name, Optional<Address> backup) {}

@GenerateFocus(generateNavigators = true)
record Address(String street, Map<String, String> metadata) {}

// Optional (AFFINE) + Map (TRAVERSAL via SPI) = TRAVERSAL
TraversalPath<Company, String> metadataValues =
    CompanyFocus.backup().metadata();  // Returns TraversalPath
```

~~~admonish note title="Custom Generators"
If you write a custom `TraversableGenerator` for your own container type, override `getCardinality()` to return `ZERO_OR_ONE` for optional-like types. The default is `ZERO_OR_MORE`, which is correct for collection-like types. See [Traversal Generator Plugins](../tooling/generator_plugins.md) for details.
~~~

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

**Previous:** [Focus DSL](focus_dsl.md)
**Next:** [Type Class and Effect Integration](focus_effects.md)
