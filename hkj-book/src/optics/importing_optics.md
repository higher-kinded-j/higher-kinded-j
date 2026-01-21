# Importing Optics for External Types

## _Generating Lenses and Prisms for Types You Don't Own_

~~~admonish info title="What You'll Learn"
- How to generate optics for types you cannot annotate directly
- Auto-detection rules for records, sealed types, enums, and wither-based classes
- How to use generated optics with third-party library types
- Handling mutable classes and edge cases
~~~

When working with external libraries, you often encounter types that would benefit from optics but cannot be annotated directly. The `@ImportOptics` annotation solves this problem by allowing you to generate optics for types you don't own.

---

## The Problem

Normally, to generate optics for a type, you annotate it with `@GenerateLenses`, `@GeneratePrisms`, or similar annotations:

```java
@GenerateLenses
public record Customer(String name, int age) {}
```

But what about types from external libraries like `java.time.LocalDate` or a third-party domain model? You cannot modify those source files.

---

## The Solution: @ImportOptics

The `@ImportOptics` annotation lets you generate optics for any accessible type. Apply it to a `package-info.java` file or a configuration class:

```java
// In package-info.java
@ImportOptics({
    java.time.LocalDate.class,
    com.external.CustomerRecord.class
})
package com.mycompany.optics;

import org.higherkindedj.optics.annotations.ImportOptics;
```

The processor analyses each class and generates appropriate optics:

| Source Type | Generated Optics |
|-------------|------------------|
| Record | Lenses via canonical constructor |
| Sealed interface | Prisms for each permitted subtype |
| Enum | Prisms for each constant |
| Class with withers | Lenses via wither methods |

---

## What Gets Generated

### Records → Lenses

For external records, the processor generates lenses for each component:

```java
// External record
package com.external;
public record Point(int x, int y) {}

// Your package-info.java
@ImportOptics({com.external.Point.class})
package com.myapp.optics;
```

This generates `PointLenses` with:

```java
// Generated: com.myapp.optics.PointLenses
public static Lens<Point, Integer> x() { ... }
public static Lens<Point, Integer> y() { ... }
public static Point withX(Point source, int newX) { ... }
public static Point withY(Point source, int newY) { ... }
```

### Sealed Interfaces → Prisms

For sealed interfaces, prisms are generated for each permitted subtype:

```java
// External sealed interface
package com.external;
public sealed interface Shape permits Circle, Rectangle {}
public record Circle(double radius) implements Shape {}
public record Rectangle(double width, double height) implements Shape {}

// Your package-info.java
@ImportOptics({com.external.Shape.class})
package com.myapp.optics;
```

This generates `ShapePrisms` with:

```java
// Generated: com.myapp.optics.ShapePrisms
public static Prism<Shape, Circle> circle() { ... }
public static Prism<Shape, Rectangle> rectangle() { ... }
```

### Enums → Prisms

For enums, prisms are generated for each constant:

```java
// External enum
package com.external;
public enum Status { PENDING, ACTIVE, COMPLETED }

// Your package-info.java
@ImportOptics({com.external.Status.class})
package com.myapp.optics;
```

This generates `StatusPrisms` with:

```java
// Generated: com.myapp.optics.StatusPrisms
public static Prism<Status, Status> pending() { ... }
public static Prism<Status, Status> active() { ... }
public static Prism<Status, Status> completed() { ... }
```

### Classes with Withers → Lenses

For immutable classes that use the wither pattern (like `java.time.LocalDate`), lenses are generated via the wither methods:

```java
// java.time.LocalDate has:
// - getYear() / withYear(int)
// - getMonth() / withMonth(Month)
// - getDayOfMonth() / withDayOfMonth(int)

@ImportOptics({java.time.LocalDate.class})
package com.myapp.optics;
```

This generates `LocalDateLenses` with:

```java
// Generated: com.myapp.optics.LocalDateLenses
public static Lens<LocalDate, Integer> year() { ... }
public static Lens<LocalDate, Integer> dayOfMonth() { ... }
// etc.
```

---

## Container Field Traversals

When a record has container fields (List, Set, Optional, arrays, Map), the processor automatically generates traversal methods:

```java
// External record with container field
public record Order(String id, List<String> items) {}

// Your package-info.java
@ImportOptics({Order.class})
package com.myapp.optics;
```

This generates both lenses and traversals:

```java
// Generated: com.myapp.optics.OrderLenses
public static Lens<Order, String> id() { ... }
public static Lens<Order, List<String>> items() { ... }
public static Traversal<Order, String> itemsTraversal() { ... }
```

---

## Generic Type Support

Generic types like `Pair<A, B>` generate parameterised optics:

```java
// External generic record
public record Pair<A, B>(A first, B second) {}

@ImportOptics({Pair.class})
package com.myapp.optics;
```

This generates:

```java
// Generated: com.myapp.optics.PairLenses
public static <A, B> Lens<Pair<A, B>, A> first() { ... }
public static <A, B> Lens<Pair<A, B>, B> second() { ... }
```

---

## Handling Mutable Classes

By default, the processor refuses to generate lenses for mutable classes (those with setter methods) because lens laws may not hold when the source object can be mutated:

```java
// This will produce a compile-time error
@ImportOptics({MutablePerson.class})  // Error: has mutable fields
package com.myapp.optics;
```

To acknowledge this limitation and generate anyway, use `allowMutable = true`:

```java
@ImportOptics(
    value = {MutablePerson.class},
    allowMutable = true  // Acknowledge lens law limitations
)
package com.myapp.optics;
```

---

## Target Package Configuration

By default, generated classes are placed in the annotated package. Use `targetPackage` to specify a different location:

```java
@ImportOptics(
    value = {java.time.LocalDate.class},
    targetPackage = "com.myapp.generated.optics"
)
package com.myapp.config;
```

---

## Type-Level Annotation

You can also apply `@ImportOptics` to a class instead of package-info:

```java
@ImportOptics({java.time.LocalDate.class, java.time.LocalTime.class})
public class TimeOptics {
    // Generated optics appear in the same package
}
```

---

## Complete Example

```java
// package-info.java
@ImportOptics({
    java.time.LocalDate.class,
    java.time.LocalTime.class,
    java.util.UUID.class
})
package com.mycompany.optics.external;

import org.higherkindedj.optics.annotations.ImportOptics;
```

```java
// Usage
import com.mycompany.optics.external.LocalDateLenses;

LocalDate date = LocalDate.of(2024, 6, 15);

// Use generated lens to update year
LocalDate nextYear = LocalDateLenses.year().modify(y -> y + 1, date);
// Result: 2025-06-15

// Or use the convenience method
LocalDate specificYear = LocalDateLenses.withYear(date, 2030);
// Result: 2030-06-15
```

---

~~~admonish info title="Key Takeaways"
* `@ImportOptics` generates optics for external types you cannot annotate directly
* Records, sealed interfaces, enums, and wither-based classes are auto-detected
* Container fields automatically get traversal methods
* Mutable classes require explicit `allowMutable = true` acknowledgement
* Generic types generate parameterised optics preserving type safety
~~~

~~~admonish tip title="See Also"
- [@ImportOptics Reference](import_optics_reference.md) - Complete annotation reference
- [Focus DSL](focus_dsl.md) - Using generated optics with Focus paths
- [Composing Optics](composing_optics.md) - Combining generated optics
~~~

---

**Next:** [@ImportOptics Reference](import_optics_reference.md)
