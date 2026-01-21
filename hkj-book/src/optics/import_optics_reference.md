# @ImportOptics Reference

## _Complete Annotation Reference for External Type Optics_

~~~admonish info title="What You'll Learn"
- All annotation elements and their usage
- Supported type patterns and requirements
- Error messages and how to resolve them
- Best practices for organising imported optics
~~~

---

## Annotation Definition

```java
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface ImportOptics {
    Class<?>[] value() default {};
    String targetPackage() default "";
    boolean allowMutable() default false;
}
```

---

## Annotation Elements

### value

**Type:** `Class<?>[]`
**Default:** Empty array
**Required:** Yes (at least one class)

The external classes to generate optics for. Each class is analysed and appropriate optics are generated based on its structure.

```java
@ImportOptics({
    java.time.LocalDate.class,
    java.time.LocalTime.class,
    com.external.CustomerRecord.class
})
package com.myapp.optics;
```

### targetPackage

**Type:** `String`
**Default:** Empty string (uses annotated package)

The package where generated classes should be placed. If empty, uses the package of the annotated element.

```java
@ImportOptics(
    value = {java.time.LocalDate.class},
    targetPackage = "com.myapp.generated"
)
package com.myapp.config;

// LocalDateLenses will be generated in com.myapp.generated
```

### allowMutable

**Type:** `boolean`
**Default:** `false`

Whether to allow generation for classes that have setter methods. By default, mutable classes are rejected because lens laws may not hold.

```java
@ImportOptics(
    value = {SomeMutableClass.class},
    allowMutable = true  // Acknowledge lens law limitations
)
package com.myapp.optics;
```

---

## Supported Type Patterns

### Records

**Detection:** `ElementKind.RECORD`
**Generated:** Lenses + convenience with methods
**Naming:** `<RecordName>Lenses`

Requirements:
- Must be a Java record
- All components generate lenses
- Uses canonical constructor for updates

```java
public record Person(String name, int age) {}
// Generates: PersonLenses with name(), age(), withName(), withAge()
```

### Sealed Interfaces

**Detection:** Interface with `sealed` modifier
**Generated:** Prisms for each permitted subtype
**Naming:** `<InterfaceName>Prisms`

Requirements:
- Must be a sealed interface
- All permitted subtypes must be accessible

```java
public sealed interface Result<T> permits Success, Failure {}
// Generates: ResultPrisms with success(), failure()
```

### Enums

**Detection:** `ElementKind.ENUM`
**Generated:** Prisms for each constant
**Naming:** `<EnumName>Prisms`

Requirements:
- Must be an enum type
- Constants are converted to camelCase method names

```java
public enum Status { PENDING, IN_PROGRESS, COMPLETED }
// Generates: StatusPrisms with pending(), inProgress(), completed()
```

### Wither Classes

**Detection:** Class with `withX()` methods matching getters
**Generated:** Lenses for each wither/getter pair
**Naming:** `<ClassName>Lenses`

Requirements:
- Must have at least one wither method
- Wither method pattern: `T withX(V value)` where T is the class type
- Corresponding getter pattern: `V getX()` or `V x()` (record-style)
- Wither must return same type (or subtype) as declaring class

```java
public class ImmutableConfig {
    public String getHost() { ... }
    public ImmutableConfig withHost(String host) { ... }
}
// Generates: ImmutableConfigLenses with host()
```

---

## Container Type Support

Container fields automatically generate additional traversal methods:

| Container Type | Traversal Method Suffix |
|----------------|------------------------|
| `List<E>` | `<field>Traversal()` |
| `Set<E>` | `<field>Traversal()` |
| `Optional<E>` | `<field>Traversal()` |
| `E[]` | `<field>Traversal()` |
| `Map<K, V>` | `<field>Traversal()` (values only) |

---

## Error Messages

### Mutable Class Rejected

```
Type 'com.example.MutablePerson' has mutable fields (setters).
Lens laws may not hold for mutable types.
Either use allowMutable = true to acknowledge this limitation,
or create a spec interface for explicit control.
```

**Resolution:** Add `allowMutable = true` or use a spec interface (Phase 2 feature).

### Unsupported Type

```
Type 'com.example.PlainClass' is not a record, sealed interface, enum,
or class with wither methods. Cannot determine how to generate optics.
```

**Resolution:** The type doesn't match any supported pattern. Consider:
- Adding wither methods to the source if you control it
- Using a spec interface (Phase 2 feature)
- Manual optic creation

### Missing Getter for Wither

Wither methods without corresponding getters are silently skipped. Ensure your class has matching getter methods.

---

## Generated Class Structure

### Lenses Class

```java
@Generated
public final class CustomerLenses {
    private CustomerLenses() {}  // Utility class

    // Lens methods
    public static Lens<Customer, String> name() { ... }
    public static Lens<Customer, Integer> age() { ... }

    // Convenience with methods
    public static Customer withName(Customer source, String newName) { ... }
    public static Customer withAge(Customer source, int newAge) { ... }

    // Traversals for container fields
    public static Traversal<Customer, Order> ordersTraversal() { ... }
}
```

### Prisms Class

```java
@Generated
public final class ShapePrisms {
    private ShapePrisms() {}  // Utility class

    // Prism methods for each subtype
    public static Prism<Shape, Circle> circle() { ... }
    public static Prism<Shape, Rectangle> rectangle() { ... }
}
```

---

## Best Practices

### 1. Organise by Domain

Create separate packages for different external libraries:

```
com.myapp.optics.javatime/    # java.time types
com.myapp.optics.jackson/     # Jackson types
com.myapp.optics.external/    # Other external types
```

### 2. Use package-info.java

Prefer package-info.java over type-level annotations for clarity:

```java
// Preferred: Clear and discoverable
@ImportOptics({...})
package com.myapp.optics.javatime;
```

### 3. Document Mutable Type Usage

When using `allowMutable = true`, document why it's acceptable:

```java
/**
 * Optics for legacy domain types.
 * Note: These types are mutable. Use with caution in concurrent contexts.
 */
@ImportOptics(value = {...}, allowMutable = true)
package com.myapp.optics.legacy;
```

### 4. Combine with Local Optics

Import optics can be composed with locally generated optics:

```java
// Local record with generated optics
@GenerateLenses
public record Order(LocalDate createdDate, Customer customer) {}

// Compose imported and local optics
Lens<Order, Integer> orderYearLens =
    OrderLenses.createdDate().andThen(LocalDateLenses.year());
```

---

~~~admonish info title="Key Takeaways"
* Use `value` to specify classes, `targetPackage` for output location
* Mutable classes require explicit `allowMutable = true`
* Generated classes follow consistent naming: `<Type>Lenses` or `<Type>Prisms`
* Container fields get additional traversal methods
* Organise imports by domain for maintainability
~~~

~~~admonish tip title="See Also"
- [Importing Optics](importing_optics.md) - Usage guide with examples
- [Composing Optics](composing_optics.md) - Combining generated optics
- [Traversals](traversals.md) - Working with generated traversals
~~~

---

**Previous:** [Importing Optics](importing_optics.md)
