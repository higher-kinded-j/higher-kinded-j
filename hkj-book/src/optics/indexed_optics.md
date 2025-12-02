# Indexed Optics: Position-Aware Operations

## _Tracking Indices During Transformations_

![indexed-optics.jpg](../images/indexed-optics.jpg)

~~~admonish info title="What You'll Learn"
- How to access both index and value during optic operations
- Using IndexedTraversal for position-aware bulk updates
- Using IndexedFold for queries that need position information
- Using IndexedLens for field name tracking and debugging
- Creating indexed traversals for Lists and Maps with IndexedTraversals utility
- Composing indexed optics with paired indices (Pair<I, J>)
- Converting between indexed and non-indexed optics
- When to use indexed optics vs standard optics
- Real-world patterns for debugging, audit trails, and position-based logic
~~~

~~~admonish title="Example Code"
[IndexedOpticsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/IndexedOpticsExample.java)
~~~

In our journey through optics, we've mastered how to focus on parts of immutable data structures, whether it's a single field with **Lens**, an optional value with **Prism**, or multiple elements with **Traversal**. But sometimes, knowing *where* you are is just as important as knowing *what* you're looking at.

Consider these scenarios:
- **Numbering items** in a packing list: "Item 1: Laptop, Item 2: Mouse..."
- **Tracking field names** for audit logs: "User modified field 'email' from..."
- **Processing map entries** where both key and value matter: "For metadata key 'priority', set value to..."
- **Debugging nested updates** by seeing the complete path: "Changed scores[2] from 100 to 150"

Standard optics give you the *value*. **Indexed optics** give you both the *index* and the *value*.

---

## The Scenario: E-Commerce Order Processing

Imagine building an order fulfilment system where position information drives business logic.

**The Data Model:**

```java
@GenerateLenses
public record LineItem(String productName, int quantity, double price) {}

@GenerateLenses
@GenerateTraversals
public record Order(String orderId, List<LineItem> items, Map<String, String> metadata) {}

@GenerateLenses
public record Customer(String name, String email) {}
```

**Business Requirements:**

1. **Generate packing slips** with numbered items: "Item 1: Laptop (£999.99)"
2. **Process metadata** with key awareness: "Set shipping method based on 'priority' key"
3. **Audit trail** showing which fields were modified: "Updated Customer.email at 2025-01-15 10:30"
4. **Position-based pricing** for bulk orders: "Items at even positions get 10% discount"

**The Traditional Approach:**

```java
// Verbose: Manual index tracking
List<String> packingSlip = new ArrayList<>();
for (int i = 0; i < order.items().size(); i++) {
    LineItem item = order.items().get(i);
    packingSlip.add("Item " + (i + 1) + ": " + item.productName());
}

// Or with streams, losing type-safety
AtomicInteger counter = new AtomicInteger(1);
order.items().stream()
    .map(item -> "Item " + counter.getAndIncrement() + ": " + item.productName())
    .collect(toList());

// Map processing requires breaking into entries
order.metadata().entrySet().stream()
    .map(entry -> processWithKey(entry.getKey(), entry.getValue()))
    .collect(toMap(Entry::getKey, Entry::getValue));
```

This approach forces manual index management, mixing the *what* (transformation logic) with the *how* (index tracking). **Indexed optics** provide a declarative, type-safe solution.

---

## Think of Indexed Optics Like...

* **GPS coordinates**: Not just the destination, but the latitude and longitude
* **Line numbers in an editor**: Every line knows its position in the file
* **Map.Entry**: Provides both key and value instead of just the value
* **Breadcrumbs in a file system**: Showing the complete path to each file
* **A numbered list**: Each element has both content and a position
* **Spreadsheet cells**: Both the cell reference (A1, B2) and the value

The key insight: indexed optics make *position* a first-class citizen, accessible during every operation.

---

## Part I: The Basics

### The Three Indexed Optics

Higher-kinded-j provides three indexed optics that mirror their standard counterparts:

| Standard Optic | Indexed Variant | Index Type | Use Case |
|----------------|-----------------|------------|----------|
| **Traversal\<S, A>** | **IndexedTraversal\<I, S, A>** | `I` (any type) | Position-aware bulk updates (List indices, Map keys) |
| **Fold\<S, A>** | **IndexedFold\<I, S, A>** | `I` (any type) | Position-aware read-only queries |
| **Lens\<S, A>** | **IndexedLens\<I, S, A>** | `I` (any type) | Field name tracking for single-field access |

The additional type parameter `I` represents the **index type**:
- For `List<A>`: `I` is `Integer` (position 0, 1, 2...)
- For `Map<K, V>`: `I` is `K` (the key type)
- For record fields: `I` is `String` (field name)
- Custom: Any type that makes sense for your domain

---

## A Step-by-Step Walkthrough

### Step 1: Creating Indexed Traversals

The `IndexedTraversals` utility class provides factory methods for common cases.

#### For Lists: Integer Indices

```java
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.util.IndexedTraversals;

// Create an indexed traversal for List elements
IndexedTraversal<Integer, List<LineItem>, LineItem> itemsWithIndex =
    IndexedTraversals.forList();

List<LineItem> items = List.of(
    new LineItem("Laptop", 1, 999.99),
    new LineItem("Mouse", 2, 24.99),
    new LineItem("Keyboard", 1, 79.99)
);
```

The `forList()` factory creates a traversal where each element is paired with its zero-based index.

#### For Maps: Key-Based Indices

```java
// Create an indexed traversal for Map values
IndexedTraversal<String, Map<String, String>, String> metadataWithKeys =
    IndexedTraversals.forMap();

Map<String, String> metadata = Map.of(
    "priority", "express",
    "gift-wrap", "true",
    "delivery-note", "Leave at door"
);
```

The `forMap()` factory creates a traversal where each value is paired with its key.

---

### Step 2: Accessing Index-Value Pairs

Indexed optics provide specialized methods that give you access to both the index and the value.

#### Extracting All Index-Value Pairs

```java
import org.higherkindedj.optics.indexed.Pair;

// Get list of (index, item) pairs
List<Pair<Integer, LineItem>> indexedItems = itemsWithIndex.toIndexedList(items);

for (Pair<Integer, LineItem> pair : indexedItems) {
    int position = pair.first();
    LineItem item = pair.second();
    System.out.println("Position " + position + ": " + item.productName());
}
// Output:
// Position 0: Laptop
// Position 1: Mouse
// Position 2: Keyboard
```

#### Using IndexedFold for Queries

```java
import org.higherkindedj.optics.indexed.IndexedFold;

// Convert to read-only indexed fold
IndexedFold<Integer, List<LineItem>, LineItem> itemsFold =
    itemsWithIndex.asIndexedFold();

// Find item at a specific position
Pair<Integer, LineItem> found = itemsFold.findWithIndex(
    (index, item) -> index == 1,
    items
).orElse(null);

System.out.println("Item at index 1: " + found.second().productName());
// Output: Item at index 1: Mouse

// Check if any even-positioned item is expensive
boolean hasExpensiveEven = itemsFold.existsWithIndex(
    (index, item) -> index % 2 == 0 && item.price() > 500,
    items
);
```

---

### Step 3: Position-Aware Modifications

The real power emerges when you modify elements based on their position.

#### Numbering Items in a Packing Slip

```java
// Modify product names to include position numbers
List<LineItem> numbered = IndexedTraversals.imodify(
    itemsWithIndex,
    (index, item) -> new LineItem(
        "Item " + (index + 1) + ": " + item.productName(),
        item.quantity(),
        item.price()
    ),
    items
);

for (LineItem item : numbered) {
    System.out.println(item.productName());
}
// Output:
// Item 1: Laptop
// Item 2: Mouse
// Item 3: Keyboard
```

#### Position-Based Discount Logic

```java
// Apply 10% discount to items at even positions (0, 2, 4...)
List<LineItem> discounted = IndexedTraversals.imodify(
    itemsWithIndex,
    (index, item) -> {
        if (index % 2 == 0) {
            double discountedPrice = item.price() * 0.9;
            return new LineItem(item.productName(), item.quantity(), discountedPrice);
        }
        return item;
    },
    items
);

// Position 0 (Laptop): £999.99 → £899.99
// Position 1 (Mouse): £24.99 (unchanged)
// Position 2 (Keyboard): £79.99 → £71.99
```

#### Map Processing with Key Awareness

```java
IndexedTraversal<String, Map<String, String>, String> metadataTraversal =
    IndexedTraversals.forMap();

Map<String, String> processed = IndexedTraversals.imodify(
    metadataTraversal,
    (key, value) -> {
        // Add key prefix to all values for debugging
        return "[" + key + "] " + value;
    },
    metadata
);

// Results:
// "priority" → "[priority] express"
// "gift-wrap" → "[gift-wrap] true"
// "delivery-note" → "[delivery-note] Leave at door"
```

---

### Step 4: Filtering with Index Awareness

Indexed traversals support filtering, allowing you to focus on specific positions or keys.

#### Filter by Index

```java
// Focus only on even-positioned items
IndexedTraversal<Integer, List<LineItem>, LineItem> evenPositions =
    itemsWithIndex.filterIndex(index -> index % 2 == 0);

List<Pair<Integer, LineItem>> evenItems =
    IndexedTraversals.toIndexedList(evenPositions, items);
// Returns: [(0, Laptop), (2, Keyboard)]

// Modify only even-positioned items
List<LineItem> result = IndexedTraversals.imodify(
    evenPositions,
    (index, item) -> new LineItem(
        item.productName() + " [SALE]",
        item.quantity(),
        item.price()
    ),
    items
);
// Laptop and Keyboard get "[SALE]" suffix, Mouse unchanged
```

#### Filter by Value with Index Available

```java
// Focus on expensive items, but still track their original positions
IndexedTraversal<Integer, List<LineItem>, LineItem> expensiveItems =
    itemsWithIndex.filteredWithIndex((index, item) -> item.price() > 50);

List<Pair<Integer, LineItem>> expensive =
    IndexedTraversals.toIndexedList(expensiveItems, items);
// Returns: [(0, Laptop), (2, Keyboard)]
// Notice: indices are preserved (0 and 2), not renumbered
```

#### Filter Map by Key Pattern

```java
// Focus on metadata keys starting with "delivery"
IndexedTraversal<String, Map<String, String>, String> deliveryMetadata =
    metadataTraversal.filterIndex(key -> key.startsWith("delivery"));

List<Pair<String, String>> deliveryEntries =
    deliveryMetadata.toIndexedList(metadata);
// Returns: [("delivery-note", "Leave at door")]
```

---

### Step 5: IndexedLens for Field Tracking

An `IndexedLens` focuses on exactly one field whilst providing its name or identifier.

```java
import org.higherkindedj.optics.indexed.IndexedLens;

// Create an indexed lens for the customer email field
IndexedLens<String, Customer, String> emailLens = IndexedLens.of(
    "email",                 // The index: field name
    Customer::email,         // Getter
    (customer, newEmail) -> new Customer(customer.name(), newEmail)  // Setter
);

Customer customer = new Customer("Alice", "alice@example.com");

// Get both field name and value
Pair<String, String> fieldInfo = emailLens.iget(customer);
System.out.println("Field: " + fieldInfo.first());      // email
System.out.println("Value: " + fieldInfo.second());     // alice@example.com

// Modify with field name awareness
Customer updated = emailLens.imodify(
    (fieldName, oldValue) -> {
        System.out.println("Updating field '" + fieldName + "' from " + oldValue);
        return "alice.smith@example.com";
    },
    customer
);
// Output: Updating field 'email' from alice@example.com
```

**Use case**: Audit logging that records *which* field changed, not just the new value.

---

### Step 6: Converting Between Indexed and Non-Indexed

Every indexed optic can be converted to its standard (non-indexed) counterpart.

```java
import org.higherkindedj.optics.Traversal;

// Start with indexed traversal
IndexedTraversal<Integer, List<LineItem>, LineItem> indexed =
    IndexedTraversals.forList();

// Drop the index to get a standard traversal
Traversal<List<LineItem>, LineItem> standard = indexed.unindexed();

// Now you can use standard traversal methods
List<LineItem> uppercased = Traversals.modify(
    standard.andThen(Lens.of(
        LineItem::productName,
        (item, name) -> new LineItem(name, item.quantity(), item.price())
    ).asTraversal()),
    String::toUpperCase,
    items
);
```

**When to convert**: When you need the index for *some* operations but not others, start indexed and convert as needed.

---

### When to Use Indexed Optics vs Standard Optics

Understanding when indexed optics add value is crucial for writing clear, maintainable code.

#### Use Indexed Optics When:

* **Position-based logic** - Different behaviour for even/odd indices, first/last elements
* **Numbering or labelling** - Adding sequence numbers, prefixes, or position markers
* **Map operations** - Both key and value are needed during transformation
* **Audit trails** - Recording which field or position was modified
* **Debugging complex updates** - Tracking the path to each change
* **Index-based filtering** - Operating on specific positions or key patterns

```java
// Perfect: Position drives the logic
IndexedTraversal<Integer, List<Product>, Product> productsIndexed =
    IndexedTraversals.forList();

List<Product> prioritised = productsIndexed.imodify(
    (index, product) -> {
        // First 3 products get express shipping
        String shipping = index < 3 ? "express" : "standard";
        return product.withShipping(shipping);
    },
    products
);
```

#### Use Standard Optics When:

* **Position irrelevant** - Pure value transformations
* **Simpler code** - Index tracking adds unnecessary complexity
* **Performance critical** - Minimal overhead needed (though indexed optics are optimised)
* **No positional logic** - All elements treated identically

```java
// Better with standard optics: Index not needed
Traversal<List<Product>, Double> prices =
    Traversals.<Product>forList()
        .andThen(ProductLenses.price().asTraversal());

List<Product> inflated = Traversals.modify(prices, price -> price * 1.1, products);
// All prices increased by 10%, position doesn't matter
```

---

### Common Patterns: Position-Based Operations

#### Pattern 1: Adding Sequence Numbers

```java
// Generate a numbered list for display
IndexedTraversal<Integer, List<String>, String> indexed = IndexedTraversals.forList();

List<String> tasks = List.of("Review PR", "Update docs", "Run tests");

List<String> numbered = IndexedTraversals.imodify(
    indexed,
    (i, task) -> (i + 1) + ". " + task,
    tasks
);
// ["1. Review PR", "2. Update docs", "3. Run tests"]
```

#### Pattern 2: First/Last Element Special Handling

```java
IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed =
    IndexedTraversals.forList();

List<LineItem> items = List.of(/* ... */);
int lastIndex = items.size() - 1;

List<LineItem> marked = IndexedTraversals.imodify(
    itemsIndexed,
    (index, item) -> {
        String marker = "";
        if (index == 0) marker = "[FIRST] ";
        if (index == lastIndex) marker = "[LAST] ";
        return new LineItem(
            marker + item.productName(),
            item.quantity(),
            item.price()
        );
    },
    items
);
```

#### Pattern 3: Map Key-Value Transformations

```java
IndexedTraversal<String, Map<String, Integer>, Integer> mapIndexed =
    IndexedTraversals.forMap();

Map<String, Integer> scores = Map.of(
    "alice", 100,
    "bob", 85,
    "charlie", 92
);

// Create display strings incorporating both key and value
List<String> results = IndexedTraversals.toIndexedList(mapIndexed, scores).stream()
    .map(pair -> pair.first() + " scored " + pair.second())
    .toList();
// ["alice scored 100", "bob scored 85", "charlie scored 92"]
```

#### Pattern 4: Position-Based Filtering

```java
IndexedTraversal<Integer, List<String>, String> indexed = IndexedTraversals.forList();

List<String> values = List.of("a", "b", "c", "d", "e", "f");

// Take only odd positions (1, 3, 5)
IndexedTraversal<Integer, List<String>, String> oddPositions =
    indexed.filterIndex(i -> i % 2 == 1);

List<String> odd = IndexedTraversals.getAll(oddPositions, values);
// ["b", "d", "f"]
```

---

### Common Pitfalls

#### Don't Do This:

```java
// Inefficient: Recreating indexed traversals in loops
for (Order order : orders) {
    var indexed = IndexedTraversals.<LineItem>forList();
    IndexedTraversals.imodify(indexed, (i, item) -> numberItem(i, item), order.items());
}

// Over-engineering: Using indexed optics when index isn't needed
IndexedTraversal<Integer, List<String>, String> indexed = IndexedTraversals.forList();
List<String> upper = IndexedTraversals.imodify(indexed, (i, s) -> s.toUpperCase(), list);
// Index parameter 'i' is never used! Use standard Traversals.modify()

// Confusing: Manual index tracking alongside indexed optics
AtomicInteger counter = new AtomicInteger(0);
IndexedTraversals.imodify(indexed, (i, item) -> {
    int myIndex = counter.getAndIncrement(); // Redundant!
    return process(myIndex, item);
}, items);

// Wrong: Expecting indices to be renumbered after filtering
IndexedTraversal<Integer, List<String>, String> evenOnly =
    indexed.filterIndex(i -> i % 2 == 0);
List<Pair<Integer, String>> pairs = IndexedTraversals.toIndexedList(evenOnly, list);
// Indices are [0, 2, 4], NOT [0, 1, 2] - original positions preserved!
```

#### Do This Instead:

```java
// Efficient: Create indexed traversal once, reuse many times
IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed =
    IndexedTraversals.forList();

for (Order order : orders) {
    IndexedTraversals.imodify(itemsIndexed, (i, item) -> numberItem(i, item), order.items());
}

// Simple: Use standard traversals when index isn't needed
Traversal<List<String>, String> standard = Traversals.forList();
List<String> upper = Traversals.modify(standard, String::toUpperCase, list);

// Clear: Trust the indexed optic to provide correct indices
IndexedTraversals.imodify(indexed, (providedIndex, item) -> {
    // Use providedIndex directly, it's correct
    return process(providedIndex, item);
}, items);

// Understand: Filtered indexed traversals preserve original indices
IndexedTraversal<Integer, List<String>, String> evenOnly =
    indexed.filterIndex(i -> i % 2 == 0);
List<Pair<Integer, String>> pairs = IndexedTraversals.toIndexedList(evenOnly, list);
// If you need renumbered indices, transform after extraction:
List<Pair<Integer, String>> renumbered = IntStream.range(0, pairs.size())
    .mapToObj(newIndex -> new Pair<>(newIndex, pairs.get(newIndex).second()))
    .toList();
```

---

### Performance Notes

Indexed optics are designed to be efficient:

* **No additional traversals** - Index computed during normal iteration
* **Lazy index creation** - `Pair<I, A>` objects only created when needed
* **Minimal overhead** - Index tracking adds negligible cost
* **Reusable compositions** - Indexed optics can be composed and cached
* **No boxing for primitives** - When using integer indices directly

**Best Practice**: Create indexed optics once and store as constants:

```java
public class OrderOptics {
    public static final IndexedTraversal<Integer, List<LineItem>, LineItem>
        ITEMS_WITH_INDEX = IndexedTraversals.forList();

    public static final IndexedTraversal<String, Map<String, String>, String>
        METADATA_WITH_KEYS = IndexedTraversals.forMap();

    // Compose with filtering
    public static final IndexedTraversal<Integer, List<LineItem>, LineItem>
        EVEN_POSITIONED_ITEMS = ITEMS_WITH_INDEX.filterIndex(i -> i % 2 == 0);
}
```

---

## Part II: Advanced Topics

### Composing Indexed Optics with Paired Indices

When you compose two indexed optics, the indices form a **pair** representing the path through nested structures.

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
        .iandThen(itemsLens)
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
List<Pair<Pair<Integer, Integer>, LineItem>> all = composed.toIndexedList(orders);

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

### Index Transformation and Mapping

You can transform indices whilst preserving the optic composition.

```java
// Start with integer indices (0, 1, 2...)
IndexedTraversal<Integer, List<LineItem>, LineItem> zeroIndexed =
    IndexedTraversals.forList();

// Transform to 1-based indices (1, 2, 3...)
IndexedTraversal<Integer, List<LineItem>, LineItem> oneIndexed =
    zeroIndexed.reindex(i -> i + 1);

List<LineItem> items = List.of(/* ... */);

List<String> numbered = oneIndexed.imodify(
    (index, item) -> "Item " + index + ": " + item.productName(),
    items
).stream()
    .map(LineItem::productName)
    .toList();
// ["Item 1: Laptop", "Item 2: Mouse", "Item 3: Keyboard"]
```

**Note**: The `reindex` method is conceptual. In practice, you'd transform indices in your `imodify` function:

```java
zeroIndexed.imodify((zeroBasedIndex, item) -> {
    int oneBasedIndex = zeroBasedIndex + 1;
    return new LineItem("Item " + oneBasedIndex + ": " + item.productName(),
                        item.quantity(), item.price());
}, items);
```

---

### Combining Index Filtering with Value Filtering

You can layer multiple filters for precise control.

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

List<Pair<Integer, LineItem>> results = targeted.toIndexedList(items);
// Returns: [(0, Laptop), (2, Keyboard), (4, Monitor)]
// All at even positions AND expensive
```

---

### Audit Trail Pattern: Field Change Tracking

A powerful real-world pattern is tracking *which* fields change in your domain objects.

```java
// Generic field audit logger
public class AuditLog {
    public record FieldChange<A>(
        String fieldName,
        A oldValue,
        A newValue,
        Instant timestamp
    ) {}

    public static <A> Function<Pair<String, A>, A> loggedModification(
        Function<A, A> transformation,
        List<FieldChange<?>> auditLog
    ) {
        return pair -> {
            String fieldName = pair.first();
            A oldValue = pair.second();
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

```java
// Nested structure with multiple levels
record Item(String name, double price) {}
record Order(List<Item> items) {}
record Customer(String name, List<Order> orders) {}

// Build an indexed path through the structure
IndexedTraversal<Integer, List<Customer>, Customer> customersIdx =
    IndexedTraversals.forList();

Lens<Customer, List<Order>> ordersLens =
    Lens.of(Customer::orders, (c, o) -> new Customer(c.name(), o));

IndexedTraversal<Integer, List<Order>, Order> ordersIdx =
    IndexedTraversals.forList();

Lens<Order, List<Item>> itemsLens =
    Lens.of(Order::items, (order, items) -> new Order(items));

IndexedTraversal<Integer, List<Item>, Item> itemsIdx =
    IndexedTraversals.forList();

Lens<Item, Double> priceLens =
    Lens.of(Item::price, (item, price) -> new Item(item.name(), price));

// Compose the full indexed path
IndexedTraversal<Pair<Pair<Integer, Integer>, Integer>, List<Customer>, Double> fullPath =
    customersIdx
        .iandThen(ordersLens)
        .iandThen(ordersIdx)
        .iandThen(itemsLens)
        .iandThen(itemsIdx)
        .iandThen(priceLens);

List<Customer> customers = List.of(/* ... */);

// Modify with full path visibility
List<Customer> updated = fullPath.imodify(
    (indices, price) -> {
        int customerIdx = indices.first().first();
        int orderIdx = indices.first().second();
        int itemIdx = indices.second();

        System.out.printf(
            "Updating price at [customer=%d, order=%d, item=%d]: %.2f → %.2f%n",
            customerIdx, orderIdx, itemIdx, price, price * 1.1
        );

        return price * 1.1;  // 10% increase
    },
    customers
);
// Output shows complete path to every modified price:
// Updating price at [customer=0, order=0, item=0]: 999.99 → 1099.99
// Updating price at [customer=0, order=0, item=1]: 24.99 → 27.49
// Updating price at [customer=0, order=1, item=0]: 79.99 → 87.99
// ...
```

---

### Working with Pair Utilities

The `Pair<A, B>` type provides utility methods for manipulation.

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

```java
import org.higherkindedj.hkt.Tuple2;
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

```java
package org.higherkindedj.example.optics;

import java.time.Instant;
import java.util.*;
import org.higherkindedj.optics.indexed.*;
import org.higherkindedj.optics.util.IndexedTraversals;

public class OrderFulfilmentDashboard {

    public record LineItem(String productName, int quantity, double price) {}

    public record Order(
        String orderId,
        List<LineItem> items,
        Map<String, String> metadata
    ) {}

    public static void main(String[] args) {
        Order order = new Order(
            "ORD-12345",
            List.of(
                new LineItem("Laptop", 1, 999.99),
                new LineItem("Mouse", 2, 24.99),
                new LineItem("Keyboard", 1, 79.99),
                new LineItem("Monitor", 1, 299.99)
            ),
            new LinkedHashMap<>(Map.of(
                "priority", "express",
                "gift-wrap", "true",
                "delivery-note", "Leave at door"
            ))
        );

        System.out.println("=== ORDER FULFILMENT DASHBOARD ===\n");

        // --- Task 1: Generate Packing Slip ---
        System.out.println("--- Packing Slip ---");
        generatePackingSlip(order);

        // --- Task 2: Apply Position-Based Discounts ---
        System.out.println("\n--- Position-Based Discounts ---");
        Order discounted = applyPositionDiscounts(order);
        System.out.println("Original total: £" + calculateTotal(order));
        System.out.println("Discounted total: £" + calculateTotal(discounted));

        // --- Task 3: Process Metadata with Key Awareness ---
        System.out.println("\n--- Metadata Processing ---");
        processMetadata(order);

        // --- Task 4: Identify High-Value Positions ---
        System.out.println("\n--- High-Value Items ---");
        identifyHighValuePositions(order);

        System.out.println("\n=== END OF DASHBOARD ===");
    }

    private static void generatePackingSlip(Order order) {
        IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed =
            IndexedTraversals.forList();

        List<Pair<Integer, LineItem>> indexedItems =
            itemsIndexed.toIndexedList(order.items());

        System.out.println("Order: " + order.orderId());
        for (Pair<Integer, LineItem> pair : indexedItems) {
            int position = pair.first() + 1;  // 1-based for display
            LineItem item = pair.second();
            System.out.printf("  Item %d: %s (Qty: %d) - £%.2f%n",
                position,
                item.productName(),
                item.quantity(),
                item.price() * item.quantity()
            );
        }
    }

    private static Order applyPositionDiscounts(Order order) {
        IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed =
            IndexedTraversals.forList();

        // Every 3rd item gets 15% off (positions 2, 5, 8...)
        List<LineItem> discounted = itemsIndexed.imodify(
            (index, item) -> {
                if ((index + 1) % 3 == 0) {
                    double newPrice = item.price() * 0.85;
                    System.out.printf("  Position %d (%s): £%.2f → £%.2f (15%% off)%n",
                        index + 1, item.productName(), item.price(), newPrice);
                    return new LineItem(item.productName(), item.quantity(), newPrice);
                }
                return item;
            },
            order.items()
        );

        return new Order(order.orderId(), discounted, order.metadata());
    }

    private static void processMetadata(Order order) {
        IndexedTraversal<String, Map<String, String>, String> metadataIndexed =
            IndexedTraversals.forMap();

        IndexedFold<String, Map<String, String>, String> fold =
            metadataIndexed.asIndexedFold();

        List<Pair<String, String>> entries = fold.toIndexedList(order.metadata());

        for (Pair<String, String> entry : entries) {
            String key = entry.first();
            String value = entry.second();

            // Process based on key
            switch (key) {
                case "priority" ->
                    System.out.println("  Shipping priority: " + value.toUpperCase());
                case "gift-wrap" ->
                    System.out.println("  Gift wrapping: " +
                        (value.equals("true") ? "Required" : "Not required"));
                case "delivery-note" ->
                    System.out.println("  Special instructions: " + value);
                default ->
                    System.out.println("  " + key + ": " + value);
            }
        }
    }

    private static void identifyHighValuePositions(Order order) {
        IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed =
            IndexedTraversals.forList();

        // Filter to items over £100
        IndexedTraversal<Integer, List<LineItem>, LineItem> highValue =
            itemsIndexed.filteredWithIndex((index, item) -> item.price() > 100);

        List<Pair<Integer, LineItem>> expensive = highValue.toIndexedList(order.items());

        System.out.println("  Items over £100 (require special handling):");
        for (Pair<Integer, LineItem> pair : expensive) {
            System.out.printf("    Position %d: %s (£%.2f)%n",
                pair.first() + 1,
                pair.second().productName(),
                pair.second().price()
            );
        }
    }

    private static double calculateTotal(Order order) {
        return order.items().stream()
            .mapToDouble(item -> item.price() * item.quantity())
            .sum();
    }
}
```

**Expected Output:**

```
=== ORDER FULFILMENT DASHBOARD ===

--- Packing Slip ---
Order: ORD-12345
  Item 1: Laptop (Qty: 1) - £999.99
  Item 2: Mouse (Qty: 2) - £49.98
  Item 3: Keyboard (Qty: 1) - £79.99
  Item 4: Monitor (Qty: 1) - £299.99

--- Position-Based Discounts ---
  Position 3 (Keyboard): £79.99 → £67.99 (15% off)
Original total: £1429.95
Discounted total: £1417.95

--- Metadata Processing ---
  Shipping priority: EXPRESS
  Gift wrapping: Required
  Special instructions: Leave at door

--- High-Value Items ---
  Items over £100 (require special handling):
    Position 1: Laptop (£999.99)
    Position 4: Monitor (£299.99)

=== END OF DASHBOARD ===
```

---

## The Relationship to Haskell's Lens Library

For those familiar with functional programming, higher-kinded-j's indexed optics are inspired by Haskell's [lens library](https://hackage.haskell.org/package/lens), specifically indexed traversals and indexed folds.

In Haskell:
```haskell
itraversed :: IndexedTraversal Int ([] a) a
```

This creates an indexed traversal over lists where the index is an integer: exactly what our `IndexedTraversals.forList()` provides.

**Key differences:**
- Higher-kinded-j uses explicit `Applicative` instances rather than implicit type class resolution
- Java's type system requires explicit `Pair<I, A>` for index-value pairs
- The `imodify` and `iget` methods provide a more Java-friendly API
- Map-based traversals (`forMap`) are a practical extension for Java's collection library

**Further Reading:**
- [Haskell Lens Tutorial: Indexed Optics](https://hackage.haskell.org/package/lens-tutorial-1.0.4/docs/Control-Lens-Tutorial.html) - Original inspiration
- [Optics By Example](https://leanpub.com/optics-by-example) by Chris Penner - Chapter on indexed optics
- [Monocle (Scala)](https://www.optics.dev/Monocle/) - Similar indexed optics for Scala

~~~admonish tip title="For Comprehension Integration"
For a fluent, comprehension-style API for indexed traversal operations, see [For Comprehensions: Position-Aware Traversals with ForIndexed](../functional/for_comprehension.md#position-aware-traversals-with-forindexed). This provides an alternative syntax for position-based filtering, modifying, and collecting traversal targets.
~~~

---

## Summary: The Power of Indexed Optics

Indexed optics bring **position awareness** into your functional data transformations:

* **IndexedTraversal\<I, S, A>**: Bulk operations with index tracking
* **IndexedFold\<I, S, A>**: Read-only queries with position information
* **IndexedLens\<I, S, A>**: Single-field access with field name tracking

These tools transform how you work with collections and records:

| Before (Manual Index Tracking) | After (Declarative Indexed Optics) |
|-------------------------------|-----------------------------------|
| Manual loop counters | Built-in index access |
| AtomicInteger for streams | Type-safe `imodify` |
| Breaking into Map.entrySet() | Direct key-value processing |
| Complex audit logging logic | Field tracking with `IndexedLens` |
| Scattered position logic | Composable indexed transformations |

By incorporating indexed optics into your toolkit, you gain:

* **Expressiveness**: Say "numbered list items" declaratively
* **Type safety**: Compile-time checked index types
* **Composability**: Chain indexed optics, filter by position, compose with standard optics
* **Debugging power**: Track complete paths through nested structures
* **Audit trails**: Record which fields changed, not just values
* **Performance**: Minimal overhead, lazy index computation

Indexed optics represent the fusion of position awareness with functional composition: enabling you to write code that is simultaneously more declarative, more powerful, and more maintainable than traditional index-tracking approaches.

---

**Previous:** [Common Data Structure Traversals](common_data_structure_traversals.md)
**Next:** [Getters: Read-Only Optics](getters.md)

