# Indexed Optics: Advanced Patterns

_Compose indexed optics through nested lists so every value arrives with the full path that reached it._

~~~admonish info title="What You'll Learn"
- Compose indexed traversals with `iandThen`, and log the full index path to each value a nested update changes
- Turn zero-based positions into display numbers inside `imodify`, with no re-indexing combinator
- Narrow by position, value or both by layering `filterIndex`, `filtered` and `filteredWithIndex`
- Write an audit trail that records each changed field's name and old value with an `IndexedLens`
~~~

This page collects the advanced composition patterns and reference material that follow on from [Indexed Optics](indexed_optics.md). The narrative introduction, mental model, and step-by-step walkthrough live there; this page is for the deeper compositions and the cross-language background.

---

## Advanced Composition Patterns

### Composing Indexed Optics with Paired Indices

When you compose two indexed optics, the indices form a **pair** representing the path through nested structures.

```mermaid
flowchart TD
    A(["IndexedTraversal&lt;Integer, List&lt;Order&gt;, Order&gt;"])
    B(["IndexedTraversal&lt;Integer, List&lt;Item&gt;, Item&gt;"])
    R["Pair&lt;Pair&lt;Integer, Integer&gt;, Item&gt;<br/>the outer index and the inner one, kept together"]
    A -->|"iandThen"| B --> R

    classDef tier fill:#a6d189,stroke:#40a02b,color:#232634
    classDef out fill:#e5c890,stroke:#df8e1d,color:#232634
    class A,B tier
    class R out
```

Each item arrives carrying the whole path that reached it, outer index first:

| Path | Item |
|---|---|
| `(0, 0)` | `Laptop` |
| `(0, 1)` | `Mouse` |
| `(1, 0)` | `Keyboard` |
| `(1, 1)` | `Monitor` |
| `(1, 2)` | `Cable` |

<!-- verify -->
```java
import org.higherkindedj.optics.indexed.Pair;

// Nested structure: List of Orders, each with List of Items
record Order(String id, List<LineItem> items) {}

// First level: indexed traversal for orders
IndexedTraversal<Integer, List<Order>, Order> ordersIndexed =
    IndexedTraversals.forList();

// Second level: lens to items field
Lens<Order, List<LineItem>> itemsLens =
    Lens.of(Order::items, (order, items) -> new Order(order.id(), items));

// Third level: indexed traversal for items
IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed =
    IndexedTraversals.forList();

// Compose: orders → items field → each item with PAIRED indices
IndexedTraversal<Pair<Integer, Integer>, List<Order>, LineItem> composed =
    ordersIndexed
        .andThen(itemsLens.asTraversal())
        .iandThen(itemsIndexed);

List<Order> orders = List.of(
    new Order("ORD-1", List.of(
        new LineItem("Laptop", 1, 999.99),
        new LineItem("Mouse", 1, 24.99)
    )),
    new Order("ORD-2", List.of(
        new LineItem("Keyboard", 1, 79.99),
        new LineItem("Monitor", 1, 299.99)
    ))
);

// Access with paired indices: (order index, item index)
List<Pair<Pair<Integer, Integer>, LineItem>> all =
    IndexedTraversals.toIndexedList(composed, orders);

for (Pair<Pair<Integer, Integer>, LineItem> entry : all) {
    Pair<Integer, Integer> indices = entry.first();
    LineItem item = entry.second();
    System.out.printf("Order %d, Item %d: %s%n",
        indices.first(), indices.second(), item.productName());
}
// Output:
// Order 0, Item 0: Laptop
// Order 0, Item 1: Mouse
// Order 1, Item 0: Keyboard
// Order 1, Item 1: Monitor
```

**Use case**: Generating globally unique identifiers like "Order 3, Item 5" or "Row 2, Column 7".

---

### Index Transformation

There is no separate re-indexing combinator; transform the index inside the `imodify` function. Converting zero-based positions to one-based display numbers looks like this:

<!-- verify -->
```java
IndexedTraversal<Integer, List<LineItem>, LineItem> zeroIndexed =
    IndexedTraversals.forList();

List<LineItem> numbered = IndexedTraversals.imodify(zeroIndexed, (zeroBasedIndex, item) -> {
    int oneBasedIndex = zeroBasedIndex + 1;
    return new LineItem("Item " + oneBasedIndex + ": " + item.productName(),
                        item.quantity(), item.price());
}, items);
// productNames: ["Item 1: Laptop", "Item 2: Mouse", "Item 3: Keyboard"]
```

---

### Combining Index Filtering with Value Filtering

You can layer multiple filters for precise control.

<!-- verify -->
```java
IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed =
    IndexedTraversals.forList();

// Filter: even positions AND expensive items
IndexedTraversal<Integer, List<LineItem>, LineItem> targeted =
    itemsIndexed
        .filterIndex(i -> i % 2 == 0)              // Even positions only
        .filtered(item -> item.price() > 50);       // Expensive items only

List<LineItem> items = List.of(
    new LineItem("Laptop", 1, 999.99),    // Index 0, expensive ✓
    new LineItem("Pen", 1, 2.99),         // Index 1, cheap ✗
    new LineItem("Keyboard", 1, 79.99),   // Index 2, expensive ✓
    new LineItem("Mouse", 1, 24.99),      // Index 3, cheap ✗
    new LineItem("Monitor", 1, 299.99)    // Index 4, expensive ✓
);

List<Pair<Integer, LineItem>> results =
    IndexedTraversals.toIndexedList(targeted, items);
// Returns: [(0, Laptop), (2, Keyboard), (4, Monitor)]
// All at even positions AND expensive
```

---

### Audit Trail Pattern: Field Change Tracking

A powerful real-world pattern is tracking *which* fields change in your domain objects.

<!-- verify -->
```java
// Generic field audit logger
public class AuditLog {
    public record FieldChange<A>(
        String fieldName,
        A oldValue,
        A newValue,
        Instant timestamp
    ) {}

    public static <A> BiFunction<String, A, A> loggedModification(
        Function<A, A> transformation,
        List<FieldChange<?>> auditLog
    ) {
        return (fieldName, oldValue) -> {
            A newValue = transformation.apply(oldValue);

            if (!oldValue.equals(newValue)) {
                auditLog.add(new FieldChange<>(
                    fieldName,
                    oldValue,
                    newValue,
                    Instant.now()
                ));
            }

            return newValue;
        };
    }
}

// Usage with indexed lens
IndexedLens<String, Customer, String> emailLens = IndexedLens.of(
    "email",
    Customer::email,
    (c, email) -> new Customer(c.name(), email)
);

List<AuditLog.FieldChange<?>> audit = new ArrayList<>();

Customer customer = new Customer("Alice", "alice@old.com");

Customer updated = emailLens.imodify(
    AuditLog.loggedModification(
        email -> "alice@new.com",
        audit
    ),
    customer
);

// Check audit log
for (AuditLog.FieldChange<?> change : audit) {
    System.out.printf("Field '%s' changed from %s to %s at %s%n",
        change.fieldName(),
        change.oldValue(),
        change.newValue(),
        change.timestamp()
    );
}
// Output: Field 'email' changed from alice@old.com to alice@new.com at 2025-01-15T10:30:00Z
```

---

### Debugging Pattern: Path Tracking in Nested Updates

When debugging complex nested updates, indexed optics reveal the complete path to each modification.

<!-- verify -->
```java
// Nested structure with multiple levels
record Item(String name, double price) {}
record Order(List<Item> items) {}
record Buyer(String name, List<Order> orders) {}

// Build an indexed path through the structure
IndexedTraversal<Integer, List<Buyer>, Buyer> buyersIdx =
    IndexedTraversals.forList();

Lens<Buyer, List<Order>> ordersLens =
    Lens.of(Buyer::orders, (b, o) -> new Buyer(b.name(), o));

IndexedTraversal<Integer, List<Order>, Order> ordersIdx =
    IndexedTraversals.forList();

Lens<Order, List<Item>> itemsLens =
    Lens.of(Order::items, (order, items) -> new Order(items));

IndexedTraversal<Integer, List<Item>, Item> itemsIdx =
    IndexedTraversals.forList();

Lens<Item, Double> priceLens =
    Lens.of(Item::price, (item, price) -> new Item(item.name(), price));

// Compose the full indexed path
IndexedTraversal<Pair<Pair<Integer, Integer>, Integer>, List<Buyer>, Double> fullPath =
    buyersIdx
        .andThen(ordersLens.asTraversal())
        .iandThen(ordersIdx)
        .andThen(itemsLens.asTraversal())
        .iandThen(itemsIdx)
        .andThen(priceLens.asTraversal());

List<Buyer> buyers = List.of(/* ... */);

// Modify with full path visibility
List<Buyer> updated = IndexedTraversals.imodify(fullPath,
    (indices, price) -> {
        int buyerIdx = indices.first().first();
        int orderIdx = indices.first().second();
        int itemIdx = indices.second();

        System.out.printf(
            "Updating price at [buyer=%d, order=%d, item=%d]: %.2f -> %.2f%n",
            buyerIdx, orderIdx, itemIdx, price, price * 1.1
        );

        return price * 1.1;  // 10% increase
    },
    buyers
);
// Output shows complete path to every modified price:
// Updating price at [buyer=0, order=0, item=0]: 999.99 -> 1099.99
// Updating price at [buyer=0, order=0, item=1]: 24.99 -> 27.49
// Updating price at [buyer=0, order=1, item=0]: 79.99 -> 87.99
// ...
```

---

### Working with Pair Utilities

The `Pair<A, B>` type provides utility methods for manipulation.

<!-- verify -->
```java
import org.higherkindedj.optics.indexed.Pair;

Pair<Integer, String> pair = new Pair<>(1, "Hello");

// Access components
int first = pair.first();       // 1
String second = pair.second();  // "Hello"

// Transform components
Pair<Integer, String> modified = pair.withSecond("World");
// Result: Pair(1, "World")

Pair<String, String> transformed = pair.withFirst("One");
// Result: Pair("One", "Hello")

// Swap
Pair<String, Integer> swapped = pair.swap();
// Result: Pair("Hello", 1)

// Factory method
Pair<String, Integer> created = Pair.of("Key", 42);
```

For converting to/from `Tuple2` (when working with hkj-core utilities):

<!-- verify -->
```java
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.optics.util.IndexedTraversals;

Pair<String, Integer> pair = Pair.of("key", 100);

// Convert to Tuple2
Tuple2<String, Integer> tuple = IndexedTraversals.pairToTuple2(pair);

// Convert back to Pair
Pair<String, Integer> converted = IndexedTraversals.tuple2ToPair(tuple);
```

---

### Real-World Example: Order Fulfilment Dashboard

Here's a comprehensive example demonstrating indexed optics in a business context.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/indexed/OrderFulfilmentDashboard.java:imports}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/indexed/OrderFulfilmentDashboard.java:dashboard}}
```

**Expected Output:**

```
{{#include ../../../hkj-examples/src/test/resources/golden/optics-indexed-dashboard-output.txt.golden}}
```

---

## The Relationship to Haskell's Lens Library

For those familiar with functional programming, Higher-Kinded-J's indexed optics are inspired by Haskell's [lens library](https://hackage.haskell.org/package/lens), specifically indexed traversals and indexed folds.

In Haskell:
```haskell
itraversed :: IndexedTraversal Int ([] a) a
```

This creates an indexed traversal over lists where the index is an integer: exactly what our `IndexedTraversals.forList()` provides.

**Key differences:**
- Higher-Kinded-J uses explicit `Applicative` instances rather than implicit type class resolution
- Java's type system requires explicit `Pair<I, A>` for index-value pairs
- The `imodify` and `iget` methods provide a more Java-friendly API
- Map-based traversals (`forMap`) are a practical extension for Java's collection library

---

## Before and after {#summary-the-power-of-indexed-optics}

What each manual pattern becomes with indexed optics:

| Before (Manual Index Tracking) | After (Declarative Indexed Optics) |
|-------------------------------|-----------------------------------|
| Manual loop counters | Built-in index access |
| AtomicInteger for streams | Type-safe `imodify` |
| Breaking into Map.entrySet() | Direct key-value processing |
| Complex audit logging logic | Field tracking with `IndexedLens` |
| Scattered position logic | Composable indexed transformations |

~~~admonish info title="Key Takeaways"
* **`iandThen` pairs the indices**: composing indexed traversals yields `Pair<I, J>` paths, so "customer 0, order 1, item 2" is a value, not a log line
* **Transform indices inside `imodify`**: there is no separate re-indexing combinator, and none is needed
* **Layered filters compose**: `filterIndex` for position, `filtered` for value, `filteredWithIndex` for both at once
* **`IndexedLens` powers audit trails**: the field name arrives with the old value, so change logging needs no reflection
* **`Pair` is a first-class utility**: `withFirst`/`withSecond`/`swap`, plus `pairToTuple2`/`tuple2ToPair` to bridge into hkj-core
~~~

~~~admonish tip title="See Also"
- [Indexed Optics](indexed_optics.md): the basics this page builds on
- [Position-Aware Traversals with ForIndexed](../functional/for_optics.md#position-aware-traversals-with-forindexed): comprehension-style position-aware filtering, modifying, and collecting
- [Indexed Access](indexed_access.md): At and Ixed for single-key operations
~~~

~~~admonish tip title="Further Reading"
- **Haskell**: [Lens Tutorial: Indexed Optics](https://hackage.haskell.org/package/lens-tutorial-1.0.4/docs/Control-Lens-Tutorial.html): original inspiration
- **Chris Penner**: [Optics By Example](https://leanpub.com/optics-by-example): chapter on indexed optics
- **Scala**: [Monocle](https://www.optics.dev/Monocle/): similar indexed optics for Scala
~~~

---

**Previous:** [Indexed Optics](indexed_optics.md)
**Next:** [Each Type Class](each_typeclass.md)
