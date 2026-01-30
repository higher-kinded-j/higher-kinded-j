# Database Records with JOOQ

## _Copy Strategies for Builder-Based Types_

> "First, solve the problem. Then, write the code."
> -- John Johnson

Johnson's advice is deceptively simple, yet it cuts to the heart of what we're doing here. The *problem* isn't "how do I update a field in a JOOQ record." The problem is: "how do I express my domain transformations clearly while respecting immutability?" Builders solve immutability; optics solve composition. Together, they let us solve the real problem first (describing what our business logic does) and then write concise code that does exactly that.

When we use `@ViaBuilder`, we're encoding our understanding of the problem (this type uses a builder pattern) so the processor can generate code that lets us focus on business logic rather than mechanical copying.

~~~admonish info title="What You'll Learn"
- How `@ViaBuilder` creates lenses for builder-pattern types
- Working with JOOQ-generated immutable POJOs
- When to use each copy strategy annotation
- Building a complete database-to-domain transformation layer
~~~

---

## The JOOQ Pattern

JOOQ generates immutable record classes with a fluent builder pattern. If we're using JOOQ for database access, we've seen code like this:

```java
// JOOQ-generated record
CustomerRecord customer = ctx.newRecord(CUSTOMER);
customer.setName("Alice");
customer.setEmail("alice@example.com");
customer.setCreatedAt(LocalDateTime.now());

// To "update" a field immutably, we use the builder
CustomerRecord updated = customer.into(CustomerRecord.class)
    .with(CUSTOMER.NAME, "Alicia");
```

Or with JOOQ's immutable POJO generation:

```java
// Generated immutable POJO with builder
@Immutable
public class Customer {
    private final String name;
    private final String email;
    private final BigDecimal creditLimit;

    public Builder toBuilder() { ... }

    public static class Builder {
        public Builder name(String name) { ... }
        public Builder email(String email) { ... }
        public Builder creditLimit(BigDecimal limit) { ... }
        public Customer build() { ... }
    }
}
```

This builder pattern is everywhere: Lombok's `@Builder`, Immutables, AutoValue, Protocol Buffers, and hand-written immutable classes. But `@ImportOptics` cannot auto-detect it because there is no standard naming convention the processor can rely on.

**This is where `@ViaBuilder` comes in.**

---

## Our First Builder Lens

Let's create optics for a JOOQ-style customer record:

```java
// The external type (imagine JOOQ generated this)
public class Customer {
    private final Long id;
    private final String name;
    private final String email;
    private final BigDecimal creditLimit;

    public Long id() { return id; }
    public String name() { return name; }
    public String email() { return email; }
    public BigDecimal creditLimit() { return creditLimit; }

    public Builder toBuilder() {
        return new Builder()
            .id(id).name(name).email(email).creditLimit(creditLimit);
    }

    public static class Builder {
        private Long id;
        private String name;
        private String email;
        private BigDecimal creditLimit;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder creditLimit(BigDecimal limit) {
            this.creditLimit = limit; return this;
        }
        public Customer build() { return new Customer(id, name, email, creditLimit); }
    }
}
```

The spec interface tells the processor how to use this builder:

```java
@ImportOptics
public interface CustomerOptics extends OpticsSpec<Customer> {

    @ViaBuilder
    Lens<Customer, Long> id();

    @ViaBuilder
    Lens<Customer, String> name();

    @ViaBuilder
    Lens<Customer, String> email();

    @ViaBuilder
    Lens<Customer, BigDecimal> creditLimit();
}
```

The `@ViaBuilder` annotation with no parameters uses sensible defaults:
- **getter**: method name matches the optic name (`name()` for the `name` lens)
- **toBuilder**: `"toBuilder"`
- **setter**: method name matches the optic name on the builder
- **build**: `"build"`

The processor generates:

```java
public final class CustomerOptics {

    public static Lens<Customer, String> name() {
        return Lens.of(
            customer -> customer.name(),
            (customer, newName) -> customer.toBuilder()
                .name(newName)
                .build()
        );
    }

    // ... similar for id, email, creditLimit
}
```

Now we can update customer records functionally:

```java
// Apply a loyalty discount
Customer discounted = CustomerOptics.creditLimit()
    .modify(limit -> limit.multiply(BigDecimal.valueOf(1.1)), customer);

// Update email
Customer updated = CustomerOptics.email()
    .set("newemail@example.com", customer);
```

---

## Handling Non-Standard Naming

Not all builders follow the same conventions. JOOQ's own conventions sometimes differ, and libraries like Lombok allow customisation. The `@ViaBuilder` annotation handles this:

```java
// Lombok with @Builder(toBuilder = true, setterPrefix = "with")
@Builder(toBuilder = true, setterPrefix = "with")
public class Order {
    private final String orderId;
    private final List<LineItem> items;
    private final OrderStatus status;

    // Lombok generates:
    // - getOrderId(), getItems(), getStatus() (standard getters)
    // - toBuilder() returning OrderBuilder
    // - OrderBuilder.withOrderId(), withItems(), withStatus()
    // - OrderBuilder.build()
}
```

```java
@ImportOptics
public interface OrderOptics extends OpticsSpec<Order> {

    @ViaBuilder(getter = "getOrderId", setter = "withOrderId")
    Lens<Order, String> orderId();

    @ViaBuilder(getter = "getItems", setter = "withItems")
    Lens<Order, List<LineItem>> items();

    @ViaBuilder(getter = "getStatus", setter = "withStatus")
    Lens<Order, OrderStatus> status();

    // Traversal into items - traversal type is auto-detected from List<LineItem>
    @ThroughField(field = "items")
    Traversal<Order, LineItem> eachItem();
}
```

### @ThroughField Auto-Detection

The `@ThroughField` annotation creates traversals that compose a lens to a container field with a standard traversal for that container type. **The processor automatically detects which traversal to use based on the field's type.**

| Field Type | Auto-Detected Traversal |
|------------|------------------------|
| `List<A>` | `Traversals.forList()` |
| `Set<A>` | `Traversals.forSet()` |
| `Optional<A>` | `Traversals.forOptional()` |
| `A[]` | `Traversals.forArray()` |
| `Map<K, V>` | `Traversals.forMapValues()` |

**Subtypes are supported.** If your field is `ArrayList<String>`, `HashSet<Integer>`, or `HashMap<String, BigDecimal>`, the processor correctly detects the parent container type:

```java
// This class uses concrete collection types
public class Order {
    private final ArrayList<LineItem> items;  // ArrayList, not List
    public ArrayList<LineItem> getItems() { return items; }
    // ...
}

@ImportOptics
public interface OrderOptics extends OpticsSpec<Order> {
    @ViaBuilder(getter = "getItems", setter = "withItems")
    Lens<Order, ArrayList<LineItem>> items();

    // Auto-detects ArrayList as List, uses Traversals.forList()
    @ThroughField(field = "items")
    Traversal<Order, LineItem> eachItem();
}
```

**When explicit traversal is needed:** If you have a custom container type that doesn't extend `List`, `Set`, `Map`, or `Optional`, you can specify the traversal explicitly:

```java
@ThroughField(
    field = "entries",
    traversal = "com.example.CustomTraversals.forMyContainer()"
)
Traversal<MyType, Entry> eachEntry();
```

We can customise any part of the builder interaction:

```java
@ViaBuilder(
    getter = "getName",           // How to get the current value
    toBuilder = "newBuilder",     // How to get a builder (some use "builder()")
    setter = "setName",           // How to set on the builder
    build = "create"              // How to build (some use "finish()")
)
Lens<LegacyType, String> name();
```

---

## A Real Workflow: Order Processing

Let's build a complete order processing pipeline using JOOQ-style records:

```java
// Domain types (imagine JOOQ generated these)
public class Order {
    public String orderId() { ... }
    public Customer customer() { ... }
    public List<LineItem> items() { ... }
    public OrderStatus status() { ... }
    public LocalDateTime createdAt() { ... }
    public Builder toBuilder() { ... }
}

public class LineItem {
    public String productId() { ... }
    public int quantity() { ... }
    public BigDecimal unitPrice() { ... }
    public Builder toBuilder() { ... }
}
```

```java
// Spec interfaces
@ImportOptics
public interface OrderOptics extends OpticsSpec<Order> {

    @ViaBuilder Lens<Order, String> orderId();
    @ViaBuilder Lens<Order, Customer> customer();
    @ViaBuilder Lens<Order, List<LineItem>> items();
    @ViaBuilder Lens<Order, OrderStatus> status();
    @ViaBuilder Lens<Order, LocalDateTime> createdAt();

    @ThroughField(field = "items")
    Traversal<Order, LineItem> eachItem();
}

@ImportOptics
public interface LineItemOptics extends OpticsSpec<LineItem> {

    @ViaBuilder Lens<LineItem, String> productId();
    @ViaBuilder Lens<LineItem, Integer> quantity();
    @ViaBuilder Lens<LineItem, BigDecimal> unitPrice();
}
```

Now we build the order processing service:

```java
public class OrderService {

    /**
     * Calculate the total value of an order.
     */
    public BigDecimal calculateTotal(Order order) {
        return OrderOptics.eachItem()
            .toListOf(order)
            .stream()
            .map(item -> LineItemOptics.unitPrice().get(item)
                .multiply(BigDecimal.valueOf(LineItemOptics.quantity().get(item))))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Apply a percentage discount to all items.
     */
    public Order applyDiscount(Order order, BigDecimal discountPercent) {
        BigDecimal multiplier = BigDecimal.ONE.subtract(
            discountPercent.divide(BigDecimal.valueOf(100))
        );

        return OrderOptics.eachItem()
            .andThen(LineItemOptics.unitPrice())
            .modify(price -> price.multiply(multiplier), order);
    }

    /**
     * Update the customer's credit limit based on order history.
     */
    public Order updateCustomerCredit(Order order, BigDecimal newLimit) {
        return OrderOptics.customer()
            .andThen(CustomerOptics.creditLimit())
            .set(newLimit, order);
    }

    /**
     * Mark order as shipped and record timestamp.
     */
    public Order shipOrder(Order order) {
        return OrderOptics.status().set(OrderStatus.SHIPPED,
            OrderOptics.createdAt().set(LocalDateTime.now(), order)
        );
    }

    /**
     * Increase quantity for a specific product.
     */
    public Order increaseQuantity(Order order, String productId, int additionalQty) {
        return OrderOptics.eachItem()
            .filter(item -> LineItemOptics.productId().get(item).equals(productId))
            .andThen(LineItemOptics.quantity())
            .modify(qty -> qty + additionalQty, order);
    }
}
```

Every method is a composition of optics. The business logic is clear and the immutable updates are handled automatically.

---

## Working with JOOQ Result Sets

JOOQ's `Result<R>` type implements `List<R>`, so we do not need a spec interface at all. Just use the standard list traversal:

```java
// Fetch customers from database
Result<CustomerRecord> customers = ctx
    .selectFrom(CUSTOMER)
    .where(CUSTOMER.ACTIVE.isTrue())
    .fetch();

// Result implements List, so Traversals.list() works directly
Traversal<Result<CustomerRecord>, CustomerRecord> eachCustomer =
    Traversals.list();

// Apply credit increase to all customers
Result<CustomerRecord> updated = eachCustomer
    .andThen(CustomerOptics.creditLimit())
    .modify(limit -> limit.multiply(BigDecimal.valueOf(1.05)), customers);
```

This is a key insight: **not every external type needs a spec interface**. When the type already implements a standard interface (`List`, `Map`, `Optional`), the existing traversals work perfectly.

---

## Beyond Builders: Other Copy Strategies

While `@ViaBuilder` handles the most common case, some types need different approaches:

### @Wither - For Types with withX() Methods

JDK types like `LocalDate` and many immutable libraries use the wither pattern:

```java
@ImportOptics
public interface LocalDateOptics extends OpticsSpec<LocalDate> {

    @Wither(value = "withYear", getter = "getYear")
    Lens<LocalDate, Integer> year();

    @Wither(value = "withMonth", getter = "getMonthValue")
    Lens<LocalDate, Integer> month();

    @Wither(value = "withDayOfMonth", getter = "getDayOfMonth")
    Lens<LocalDate, Integer> day();
}
```

### @ViaConstructor - For Constructor-Only Types

Some immutable types have no builder or withers, just a constructor:

```java
// Simple immutable type
public class Point {
    private final int x, y;
    public Point(int x, int y) { this.x = x; this.y = y; }
    public int x() { return x; }
    public int y() { return y; }
}
```

```java
@ImportOptics
public interface PointOptics extends OpticsSpec<Point> {

    @ViaConstructor(parameterOrder = {"x", "y"})
    Lens<Point, Integer> x();

    @ViaConstructor(parameterOrder = {"x", "y"})
    Lens<Point, Integer> y();
}
```

The `parameterOrder` tells the processor which getters to call and in what order to pass them to the constructor.

### @ViaCopyAndSet - For Copy Constructor + Setter Types

Legacy types sometimes have a copy constructor and setters:

```java
public class Config {
    public Config(Config other) { /* copy fields */ }
    public void setHost(String host) { this.host = host; }
}
```

```java
@ImportOptics
public interface ConfigOptics extends OpticsSpec<Config> {

    @ViaCopyAndSet(setter = "setHost")
    Lens<Config, String> host();
}
```

~~~admonish warning title="Lens Law Caution"
`@ViaCopyAndSet` creates mutable intermediate objects. Ensure the copy constructor truly copies all fields, or modifications may affect the original.
~~~

---

## Taking It Further: Focus DSL Bridging

When our local domain records reference external types (JOOQ records, Immutables values, etc.), we can **bridge** Focus DSL navigation into those types using spec interface optics:

```java
// Focus for local navigation, spec optics for external types
Lens<Company, String> hqCity = CompanyFocus.headquarters().toLens()
    .andThen(AddressOptics.city());

// Now we get IDE autocomplete through the entire chain
String city = hqCity.get(company);
Company relocated = hqCity.set("New York", company);
```

The `.toLens()` method converts a Focus path into a composable optic, which we can then chain with spec interface optics for external types.

**For a complete guide** including Immutables integration, deep traversals across boundaries, and domain service patterns, see **[Focus DSL with External Libraries](focus_external_bridging.md)**.

---

## Choosing the Right Strategy

```
              ┌─────────────────────────────────────────────┐
              │     Which copy strategy should I use?       │
              └─────────────────────────────────────────────┘
                                   │
                                   ▼
               ┌───────────────────────────────────────┐
               │ Does it have toBuilder().build()?    │
               └───────────────────────────────────────┘
                      │ YES                    │ NO
                      ▼                        ▼
            ┌──────────────────┐    ┌───────────────────────┐
            │   @ViaBuilder    │    │ Does it have withX()? │
            │                  │    └───────────────────────┘
            │ JOOQ, Lombok,    │           │ YES       │ NO
            │ Immutables,      │           ▼           ▼
            │ AutoValue        │    ┌──────────┐  ┌─────────────────────┐
            └──────────────────┘    │ @Wither  │  │ Does it have an     │
                                    │          │  │ all-args constructor?│
                                    │LocalDate,│  └─────────────────────┘
                                    │java.time │       │ YES       │ NO
                                    └──────────┘       ▼           ▼
                                             ┌───────────────┐ ┌──────────────┐
                                             │@ViaConstructor│ │@ViaCopyAndSet│
                                             │               │ │              │
                                             │ Simple value  │ │ Legacy types │
                                             │ objects       │ │ with setters │
                                             └───────────────┘ └──────────────┘
```

| Type Pattern | Annotation | Example Types |
|--------------|------------|---------------|
| Has `toBuilder().field(x).build()` | `@ViaBuilder` | JOOQ POJOs, Lombok @Builder, Immutables |
| Has `withField(x)` methods | `@Wither` | java.time, Guava Immutables |
| Has all-args constructor only | `@ViaConstructor` | Simple value objects |
| Has copy constructor + setters | `@ViaCopyAndSet` | Legacy mutable types |
| Implements List/Map/Optional | None needed | JOOQ Result, standard collections |

Start with `@ViaBuilder` as it is the most common pattern. Fall back to others when the type does not fit.

---

## Quick Reference

~~~admonish tip title="@ViaBuilder Parameters"
```java
@ViaBuilder(
    getter = "getName",      // Default: optic method name
    toBuilder = "toBuilder", // Default: "toBuilder"
    setter = "name",         // Default: optic method name
    build = "build"          // Default: "build"
)
```
~~~

~~~admonish tip title="@Wither Parameters"
```java
@Wither(
    value = "withName",      // Required: wither method name
    getter = "getName"       // Default: optic method name
)
```
~~~

~~~admonish tip title="@ViaConstructor Parameters"
```java
@ViaConstructor(
    parameterOrder = {"x", "y", "z"}  // Effectively required: constructor parameter order
)
```
**Note:** While `parameterOrder` has a default empty value in the annotation definition, you must provide it for the processor to generate working code. Omitting it results in a runtime `UnsupportedOperationException`. Future versions may support auto-detection of constructor parameters.
~~~

~~~admonish tip title="@ThroughField Parameters"
```java
@ThroughField(
    field = "items",                  // Required: name of the container field
    traversal = ""                    // Optional: auto-detected from field type
)
```
**Auto-detection:** The `traversal` parameter is automatically determined based on the field's type:
- `List<A>` (including `ArrayList`, `LinkedList`, etc.) → `Traversals.forList()`
- `Set<A>` (including `HashSet`, `TreeSet`, etc.) → `Traversals.forSet()`
- `Optional<A>` → `Traversals.forOptional()`
- `A[]` → `Traversals.forArray()`
- `Map<K, V>` (including `HashMap`, `TreeMap`, etc.) → `Traversals.forMapValues()`
~~~

---

~~~admonish info title="Key Takeaways"
* `@ViaBuilder` is your go-to for JOOQ, Lombok, and most immutable types
* `@ThroughField` auto-detects traversals for `List`, `Set`, `Optional`, arrays, and `Map` fields (including subtypes)
* JOOQ's `Result<R>` implements `List` - use standard traversals directly
* Match the copy strategy to the type's API - builder, wither, constructor, or copy+set
* Compose optics freely across different strategies - they all produce standard `Lens` instances
~~~

---

## Making Your JOOQ Integration Even Better

~~~admonish tip title="Extending the Integration"
Consider these opportunities to enhance your database optics:

- **Generate spec interfaces from JOOQ schema**: Write a build step that generates `*Optics` interfaces alongside JOOQ's code generation
- **Batch updates**: Compose traversals for bulk modifications that can be converted back to SQL updates
- **Audit trails**: Wrap optics with logging to track all field modifications before persistence
- **Validation pipelines**: Combine with `Validated` to accumulate all validation errors before database writes
- **Optimistic locking**: Create optics that automatically handle version field increments
~~~

---

## Further Reading

**JOOQ:**
- [JOOQ Official Site](https://www.jooq.org/) - Type-safe SQL in Java
- [JOOQ Immutables Integration](https://www.jooq.org/doc/latest/manual/code-generation/codegen-pojos/) - Generating immutable POJOs
- [JOOQ Record Patterns](https://www.jooq.org/doc/latest/manual/sql-execution/fetching/record-vs-pojo/) - Records vs POJOs

**Builder Pattern Libraries:**
- [Lombok](https://projectlombok.org/) - `@Builder`, `@Value`, and more
- [Immutables](https://immutables.github.io/) - Annotation-driven immutable objects
- [AutoValue](https://github.com/google/auto/tree/main/value) - Google's immutable value types
- [Protocol Buffers](https://protobuf.dev/reference/java/java-generated/) - Generated builders for message types

**Related Higher-Kinded-J Features:**
- Focus DSL Bridging - See [next chapter](focus_external_bridging.md)
- Traversal utilities - Batch operations on collections
- Monoid-based aggregation - Combine values across traversals

---

**Previous:** [Taming JSON with Jackson](optics_spec_interfaces.md)
**Next:** [Focus DSL with External Libraries](focus_external_bridging.md)
