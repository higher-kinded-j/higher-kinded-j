# Indexed Optics: Position-Aware Operations

## _Tracking Indices During Transformations_

> *"Give me a place to stand, and I shall move the earth."*
>
> -- Archimedes

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

```
               STANDARD OPTICS vs INDEXED OPTICS

  ┌─────────────────────────────────────────────────────────────┐
  │  STANDARD TRAVERSAL                                         │
  │  ═══════════════════                                        │
  │                                                             │
  │    List<Item>  ──▶  Traversal  ──▶  Item, Item, Item        │
  │                                                             │
  │    You get: "Laptop", "Mouse", "Keyboard"                   │
  │    You lose: Where each item is in the list                 │
  │                                                             │
  ├─────────────────────────────────────────────────────────────┤
  │  INDEXED TRAVERSAL                                          │
  │  ══════════════════                                         │
  │                                                             │
  │    List<Item>  ──▶  IndexedTraversal  ──▶  (0, Item),       │
  │                                            (1, Item),       │
  │                                            (2, Item)        │
  │                                                             │
  │    You get: (0, "Laptop"), (1, "Mouse"), (2, "Keyboard")    │
  │    Position becomes a first-class citizen                   │
  │                                                             │
  └─────────────────────────────────────────────────────────────┘

                    ┌───────────────────────┐
                    │   Pair<Index, A>      │
                    │  ┌───────┬──────────┐ │
                    │  │ Index │ Value    │ │
                    │  ├───────┼──────────┤ │
                    │  │   0   │ Laptop   │ │
                    │  │   1   │ Mouse    │ │
                    │  │   2   │ Keyboard │ │
                    │  └───────┴──────────┘ │
                    └───────────────────────┘
```

Archimedes understood that position is power. With the right fulcrum point, a lever can move the world. Similarly, with the right index, an optic can transform data in ways that value-only access cannot. Position-based discounts, numbered lists, audit trails showing *which* field changed: all require knowing *where* you are, not just *what* you have.

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
```

The `forList()` factory creates a reusable traversal where each element is paired with its zero-based index. You supply the actual data when you *use* the traversal (see Step 2 below).

#### For Maps: Key-Based Indices

```java
// Create an indexed traversal for Map values
IndexedTraversal<String, Map<String, String>, String> metadataWithKeys =
    IndexedTraversals.forMap();
```

The `forMap()` factory creates a traversal where each value is paired with its key.

~~~admonish tip title="Alternative: EachIndexed.indexedTraversal()"
You can also obtain indexed traversals through the [Each typeclass](each_typeclass.md). If a container's `Each` instance supports indexed access it is an `EachIndexed`, whose `indexedTraversal()` returns the `IndexedTraversal` directly — the index type is fixed at compile time, with no `Optional` to unwrap:

```java
EachIndexed<Integer, List<String>, String> listEach = EachInstances.listEach();
IndexedTraversal<Integer, List<String>, String> indexed = listEach.indexedTraversal();
// Use the indexed traversal
```

This is useful when working with custom containers that implement `EachIndexed` or when integrating with the Focus DSL.

The older `Each.eachWithIndex()`, which returned an `Optional<IndexedTraversal>` and let the caller pick the index type freely, is **deprecated (for removal in 0.5.0)** in favour of `EachIndexed.indexedTraversal()`.
~~~

---

### Step 2: Accessing Index-Value Pairs

Indexed optics provide specialised methods that give you access to both the index and the value.

#### Extracting All Index-Value Pairs

```java
import org.higherkindedj.optics.indexed.Pair;

List<LineItem> items = List.of(
    new LineItem("Laptop", 1, 999.99),
    new LineItem("Mouse", 2, 24.99),
    new LineItem("Keyboard", 1, 79.99)
);

// Get list of (index, item) pairs - optic meets data
List<Pair<Integer, LineItem>> indexedItems =
    IndexedTraversals.toIndexedList(itemsWithIndex, items);

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

Map<String, String> metadata = Map.of(
    "priority", "express",
    "gift-wrap", "true",
    "delivery-note", "Leave at door"
);

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
    IndexedTraversals.toIndexedList(deliveryMetadata, metadata);
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
Traversal<List<LineItem>, LineItem> standard = indexed.asTraversal();

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

List<Product> prioritised = IndexedTraversals.imodify(
    productsIndexed,
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

~~~admonish tip title="See Also"
- [Indexed Optics: Advanced Patterns](indexed_optics_advanced.md), advanced composition with paired indices, the Haskell heritage, and the trade-off summary.
~~~

---

**Previous:** [Filtered Optics](filtered_optics.md)
**Next:** [Indexed Optics: Advanced Patterns](indexed_optics_advanced.md)
