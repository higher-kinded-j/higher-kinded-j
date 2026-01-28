# Optics for External Types

## _Extending Your Reach Beyond Your Own Code_

> "The real voyage of discovery consists not in seeking new landscapes, but in having new eyes."
> -- Marcel Proust

Proust's insight captures exactly what we're doing here. The landscape (JDK classes, database libraries, JSON parsers) already exists. What changes is how we *see* it. With `@ImportOptics`, we gain new eyes: the ability to view `LocalDate`, `JsonNode`, or any external type through the lens (pun intended) of functional optics. We're not adding code to these libraries; we're changing our perspective on them, making them participants in compositional, type-safe transformations.

This shift in perspective is powerful. Types that once felt like immutable black boxes become malleable structures we can navigate, query, and transform, all without sacrificing the immutability guarantees we value.

~~~admonish info title="What You'll Learn"
- How to generate optics for types you cannot modify (JDK classes, third-party libraries)
- When simple auto-detection works and when you need more control
- A practical workflow for integrating external types into your optics pipelines
~~~

---

## The Frustration

We've been using optics throughout our codebase. Updating nested records feels natural. Traversing collections is elegant. Then we hit a wall:

```java
// Our domain model - optics work beautifully
@GenerateLenses
record Order(String id, Customer customer, LocalDate orderDate, List<LineItem> items) {}

// But wait... how do we modify just the year in orderDate?
// LocalDate is a JDK class. We can't annotate it.
```

We want to write something like:

```java
// Dream code - adjust order dates to next year
var nextYearOrder = orderDateLens
    .andThen(yearLens)  // ← Where does this come from?
    .modify(y -> y + 1, order);
```

But `LocalDate` lives in `java.time`. We can't add `@GenerateLenses` to it. The same problem hits us with Jackson's `JsonNode`, JOOQ query results, Protobuf messages, and dozens of other library types we use daily.

**This is what `@ImportOptics` solves.**

---

## The Quick Win: LocalDate in 30 Seconds

Create a `package-info.java` in your optics package:

```java
@ImportOptics(java.time.LocalDate.class)
package com.myapp.optics;

import org.higherkindedj.optics.annotations.ImportOptics;
```

The processor analyses `LocalDate`, discovers its wither methods (`withYear`, `withMonth`, `withDayOfMonth`), and generates:

```java
// Generated: LocalDateLenses.java
public final class LocalDateLenses {
    public static Lens<LocalDate, Integer> year() { ... }
    public static Lens<LocalDate, Integer> monthValue() { ... }
    public static Lens<LocalDate, Integer> dayOfMonth() { ... }
}
```

Now we have lenses the we can use:

```java
import static com.myapp.optics.LocalDateLenses.year;
import static com.myapp.optics.OrderLenses.orderDate;

// Bump all orders to next year
var nextYearOrder = orderDate()
    .andThen(year())
    .modify(y -> y + 1, order);
```

**That's it.** One annotation, and JDK types participate in our optics pipelines.

---

## How Auto-Detection Works

The processor examines each imported type and applies rules based on what it finds:

```
┌─────────────────────────────────────────────────────────────┐
│                    @ImportOptics(Type.class)                │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │  Analyse Type   │
                    └─────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
   ┌─────────┐          ┌──────────┐         ┌──────────┐
   │ Record? │          │ Sealed?  │         │ Wither?  │
   └─────────┘          └──────────┘         └──────────┘
        │                     │                     │
        ▼                     ▼                     ▼
  ┌───────────┐       ┌─────────────┐       ┌───────────┐
  │  Lenses   │       │   Prisms    │       │  Lenses   │
  │   via     │       │  for each   │       │   via     │
  │Constructor│       │   variant   │       │  Withers  │
  └───────────┘       └─────────────┘       └───────────┘
```

### Records → Lenses via Constructor

```java
// External library has:
public record Coordinate(double lat, double lon) {}

// We write:
@ImportOptics(Coordinate.class)
package com.myapp.optics;

// We get:
CoordinateLenses.lat()  // Lens<Coordinate, Double>
CoordinateLenses.lon()  // Lens<Coordinate, Double>
```

Records are the easiest case. The canonical constructor provides the copy mechanism.

### Sealed Types → Prisms for Each Variant

```java
// External library has:
public sealed interface PaymentMethod
    permits CreditCard, BankTransfer, Crypto {}

// We write:
@ImportOptics(PaymentMethod.class)
package com.myapp.optics;

// We get:
PaymentMethodPrisms.creditCard()    // Prism<PaymentMethod, CreditCard>
PaymentMethodPrisms.bankTransfer()  // Prism<PaymentMethod, BankTransfer>
PaymentMethodPrisms.crypto()        // Prism<PaymentMethod, Crypto>
```

### Enums → Prisms for Each Constant

```java
// External library has:
public enum OrderStatus { PENDING, SHIPPED, DELIVERED, CANCELLED }

// We write:
@ImportOptics(OrderStatus.class)
package com.myapp.optics;

// We get:
OrderStatusPrisms.pending()    // Prism<OrderStatus, OrderStatus>
OrderStatusPrisms.shipped()    // etc.
```

### Wither Classes → Lenses via Wither Methods

Types like `LocalDate`, `LocalTime`, and many immutable library classes follow the "wither" pattern:

```java
// The pattern: getX() paired with withX(value)
LocalDate date = ...;
int year = date.getYear();           // getter
LocalDate next = date.withYear(2025); // wither returns modified copy
```

The processor detects these pairs automatically and generates lenses.

---

## A Real Workflow: Date Range Validation

Let's build something practical. We're validating that orders fall within a fiscal quarter. We have a local `Order` record that contains a `LocalDate`:

```java
// Our local record - we own this, so use @GenerateLenses
@GenerateLenses
record Order(String id, Customer customer, LocalDate orderDate, List<LineItem> items) {}

// This generates OrderLenses with:
// - OrderLenses.orderDate() → Lens<Order, LocalDate>
// - OrderLenses.id(), OrderLenses.customer(), etc.
```

Now import optics for the external `LocalDate` type:

```java
@ImportOptics({
    java.time.LocalDate.class,
    java.time.YearMonth.class
})
package com.myapp.optics;
```

With both in place, we can compose across the boundary:

```java
import static com.myapp.optics.LocalDateLenses.*;  // External type optics
import static com.myapp.optics.OrderLenses.*;      // Local record optics

public class FiscalValidator {

    private final int fiscalYear;
    private final int quarter; // 1-4

    public Order normaliseToQuarterStart(Order order) {
        int quarterStartMonth = (quarter - 1) * 3 + 1;

        // orderDate() from OrderLenses, year()/monthValue()/dayOfMonth() from LocalDateLenses
        return orderDate()
            .andThen(year())
            .set(fiscalYear,
                orderDate()
                    .andThen(monthValue())
                    .set(quarterStartMonth,
                        orderDate()
                            .andThen(dayOfMonth())
                            .set(1, order)));
    }

    public boolean isInQuarter(Order order) {
        LocalDate date = orderDate().get(order);
        int month = monthValue().get(date);
        int expectedStart = (quarter - 1) * 3 + 1;
        return year().get(date) == fiscalYear
            && month >= expectedStart
            && month < expectedStart + 3;
    }
}
```

The optics compose naturally. `orderDate().andThen(year())` reads like English: "the year of the order date." Local and external optics work together seamlessly.

---

## Container Fields Get Traversals

When a record contains collections, the processor generates both a lens (to the whole collection) and a traversal (into the elements):

```java
// External:
public record Department(String name, List<Employee> staff) {}

// Generated:
DepartmentLenses.name()           // Lens<Department, String>
DepartmentLenses.staff()          // Lens<Department, List<Employee>>
DepartmentLenses.staffTraversal() // Traversal<Department, Employee>
```

This means we can reach directly into nested collections:

```java
// Give everyone in the department a 10% raise
var updated = staffTraversal()
    .andThen(salaryLens())
    .modify(s -> s * 1.10, department);
```

---

## When Auto-Detection Isn't Enough

Auto-detection handles the common cases beautifully. But some types resist it:

**Builder Patterns**
```java
// JOOQ-generated records use builders:
CustomerRecord customer = new CustomerRecord()
    .setName("Alice")
    .setEmail("alice@example.com");

CustomerRecord updated = customer.toBuilder()
    .setName("Alicia")
    .build();
```
No wither methods. No public constructor with all fields. The processor can't guess this pattern.

**Non-Standard Naming**
```java
// Some libraries use different conventions:
config.derivedWith(newValue)  // not withX()
node.as(TargetType.class)     // not instanceof
```

**Predicate-Based Type Discrimination**
```java
// Jackson's JsonNode uses methods, not sealed types:
if (node.isObject()) {
    ObjectNode obj = (ObjectNode) node;
}
```

For these cases, we need **spec interfaces** - explicit declarations that tell the processor exactly how to work with the type.

---

## The Path Forward

This page covered the quick wins: importing simple external types with `@ImportOptics`. For the more interesting cases, continue to:

- **[Taming JSON with Jackson](optics_spec_interfaces.md)** - Deep dive into building optics for `JsonNode`, with a complete JSON transformation pipeline
- **[Database Records with JOOQ](copy_strategies.md)** - Working with builder patterns and query results

---

~~~admonish tip title="Quick Reference"
```java
// Simple import - auto-detection handles the rest
@ImportOptics({
    java.time.LocalDate.class,
    java.time.LocalTime.class,
    com.library.SimpleRecord.class
})
package com.myapp.optics;

// Options when you need them
@ImportOptics(
    value = { MutableConfig.class },
    allowMutable = true,  // Acknowledge lens law limitations
    targetPackage = "com.myapp.generated"
)
```
~~~

~~~admonish info title="Key Takeaways"
* `@ImportOptics` brings external types into our optics world
* Records, sealed types, enums, and wither-based classes work automatically
* Container fields get both lenses and traversals
* For builder patterns and non-standard types, use [spec interfaces](optics_spec_interfaces.md)
~~~

---

## General Advice: Integrating Third-Party Libraries

When working with a new external library, follow this decision tree:

1. **Can you annotate the type?** If it's your code, use `@GenerateLenses` directly.

2. **Is it a simple record, sealed type, or wither-based class?** Use `@ImportOptics` and let auto-detection handle it.

3. **Does it use builders, predicates, or non-standard patterns?** Create a spec interface with the appropriate annotations.

4. **Does it already implement List, Map, or Optional?** You may not need any annotations; standard traversals work directly.

~~~admonish tip title="Making the Most of Your Integration"
Consider these opportunities to enhance your Higher-Kinded-J integration:

- **Create domain-specific wrappers**: Layer meaningful names over raw optics (`orderTotal()` instead of `items().andThen(price())`)
- **Build validation pipelines**: Combine optics with `Validated` or `Either` for error-accumulating transformations
- **Centralise your optics**: Keep spec interfaces in a dedicated package for easy discovery
- **Add test coverage**: Verify lens laws hold, especially for `@ViaCopyAndSet` with mutable types
~~~

---

## Further Reading

**JDK Types:**
- [java.time API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/package-summary.html) - LocalDate, LocalTime, Instant, and friends all follow the wither pattern

**Libraries Covered in Later Chapters:**
- [Jackson](https://github.com/FasterXML/jackson) - JSON processing, covered in [Taming JSON](optics_spec_interfaces.md)
- [JOOQ](https://www.jooq.org/) - Database access, covered in [Database Records](copy_strategies.md)
- [Immutables](https://immutables.github.io/) - Value objects, covered in [Focus DSL Bridging](focus_external_bridging.md)
- [Lombok](https://projectlombok.org/) - Code generation with `@Builder`
- [AutoValue](https://github.com/google/auto/tree/main/value) - Google's immutable value types
- [Protocol Buffers](https://protobuf.dev/) - Cross-language serialisation with builders

---

**Next:** [Taming JSON with Jackson](optics_spec_interfaces.md)
