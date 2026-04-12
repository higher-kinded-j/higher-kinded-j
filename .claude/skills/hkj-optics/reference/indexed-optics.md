# Indexed Optics

Position-aware optic operations. Standard optics give you values; indexed optics give you `Pair<Index, Value>`.

## The Three Indexed Optics

| Standard Optic       | Indexed Variant                    | Index Type   | Use Case                          |
|----------------------|------------------------------------|--------------|-----------------------------------|
| `Traversal<S, A>`   | `IndexedTraversal<I, S, A>`        | `I` (any)    | Position-aware bulk updates       |
| `Fold<S, A>`        | `IndexedFold<I, S, A>`             | `I` (any)    | Position-aware read-only queries  |
| `Lens<S, A>`        | `IndexedLens<I, S, A>`             | `I` (any)    | Field name tracking               |

Common index types:
- `List<A>` -> `I = Integer` (position 0, 1, 2...)
- `Map<K, V>` -> `I = K` (the key type)
- Record fields -> `I = String` (field name)

## Creating Indexed Traversals

```java
import org.higherkindedj.optics.indexed.IndexedTraversal;
import org.higherkindedj.optics.util.IndexedTraversals;

// For Lists: integer indices
IndexedTraversal<Integer, List<LineItem>, LineItem> itemsWithIndex =
    IndexedTraversals.forList();

// For Maps: key-based indices
IndexedTraversal<String, Map<String, String>, String> metadataWithKeys =
    IndexedTraversals.forMap();
```

## Accessing Index-Value Pairs

```java
import org.higherkindedj.optics.indexed.Pair;

List<Pair<Integer, LineItem>> indexedItems =
    IndexedTraversals.toIndexedList(itemsWithIndex, items);

for (Pair<Integer, LineItem> pair : indexedItems) {
    System.out.println("Position " + pair.first() + ": " + pair.second().productName());
}
```

## IndexedFold Queries

```java
IndexedFold<Integer, List<LineItem>, LineItem> itemsFold =
    itemsWithIndex.asIndexedFold();

// Find item at specific position
Pair<Integer, LineItem> found = itemsFold.findWithIndex(
    (index, item) -> index == 1, items).orElse(null);

// Check existence with index condition
boolean hasExpensiveEven = itemsFold.existsWithIndex(
    (index, item) -> index % 2 == 0 && item.price() > 500, items);
```

## Position-Aware Modifications (imodify)

```java
// Numbering items
List<LineItem> numbered = IndexedTraversals.imodify(itemsWithIndex,
    (index, item) -> new LineItem(
        "Item " + (index + 1) + ": " + item.productName(),
        item.quantity(), item.price()),
    items);

// Position-based discount (even positions get 10% off)
List<LineItem> discounted = IndexedTraversals.imodify(itemsWithIndex,
    (index, item) -> index % 2 == 0
        ? new LineItem(item.productName(), item.quantity(), item.price() * 0.9)
        : item,
    items);

// Map processing with key awareness
Map<String, String> processed = IndexedTraversals.imodify(metadataTraversal,
    (key, value) -> "[" + key + "] " + value,
    metadata);
```

## Index Filtering

```java
// Filter by index
IndexedTraversal<Integer, List<LineItem>, LineItem> evenPositions =
    itemsWithIndex.filterIndex(index -> index % 2 == 0);

// Filter by value with index available
IndexedTraversal<Integer, List<LineItem>, LineItem> expensiveItems =
    itemsWithIndex.filteredWithIndex((index, item) -> item.price() > 50);
// Note: indices are PRESERVED (0, 2, 4), not renumbered (0, 1, 2)

// Filter map by key pattern
IndexedTraversal<String, Map<String, String>, String> deliveryMetadata =
    metadataTraversal.filterIndex(key -> key.startsWith("delivery"));
```

## IndexedLens for Field Tracking

```java
IndexedLens<String, Customer, String> emailLens = IndexedLens.of(
    "email",                                                    // index: field name
    Customer::email,                                            // getter
    (customer, newEmail) -> new Customer(customer.name(), newEmail) // setter
);

Pair<String, String> fieldInfo = emailLens.iget(customer);
// fieldInfo.first() = "email", fieldInfo.second() = "alice@example.com"

Customer updated = emailLens.imodify(
    (fieldName, oldValue) -> {
        System.out.println("Updating '" + fieldName + "' from " + oldValue);
        return "alice.smith@example.com";
    }, customer);
```

## Converting Between Indexed and Standard

```java
IndexedTraversal<Integer, List<LineItem>, LineItem> indexed =
    IndexedTraversals.forList();

// Drop the index to get a standard traversal
Traversal<List<LineItem>, LineItem> standard = indexed.asTraversal();
```

## Composing Indexed Optics (Paired Indices)

Composing two indexed optics produces `Pair<I, J>` indices representing the path through nested structures.

```java
IndexedTraversal<Integer, List<Order>, Order> ordersIndexed =
    IndexedTraversals.forList();

IndexedTraversal<Integer, List<LineItem>, LineItem> itemsIndexed =
    IndexedTraversals.forList();

// Compose: paired indices (orderIndex, itemIndex)
IndexedTraversal<Pair<Integer, Integer>, List<Order>, LineItem> composed =
    ordersIndexed
        .andThen(itemsLens.asTraversal())
        .iandThen(itemsIndexed);

List<Pair<Pair<Integer, Integer>, LineItem>> all =
    IndexedTraversals.toIndexedList(composed, orders);

for (Pair<Pair<Integer, Integer>, LineItem> entry : all) {
    System.out.printf("Order %d, Item %d: %s%n",
        entry.first().first(), entry.first().second(),
        entry.second().productName());
}
```

## Pair Utilities

```java
Pair<Integer, String> pair = new Pair<>(1, "Hello");

pair.first();                    // 1
pair.second();                   // "Hello"
pair.withSecond("World");        // Pair(1, "World")
pair.withFirst("One");           // Pair("One", "Hello")
pair.swap();                     // Pair("Hello", 1)
Pair.of("Key", 42);             // factory method
```

## When to Use Indexed vs Standard Optics

**Use indexed when:**
- Position-based logic (even/odd, first/last)
- Numbering or labelling elements
- Map operations needing both key and value
- Audit trails recording which field/position changed
- Debugging nested updates (path tracking)

**Use standard when:**
- Pure value transformations (position irrelevant)
- All elements treated identically
- Simpler code preferred

## Best Practice: Store as Constants

```java
public class OrderOptics {
    public static final IndexedTraversal<Integer, List<LineItem>, LineItem>
        ITEMS_WITH_INDEX = IndexedTraversals.forList();

    public static final IndexedTraversal<String, Map<String, String>, String>
        METADATA_WITH_KEYS = IndexedTraversals.forMap();

    public static final IndexedTraversal<Integer, List<LineItem>, LineItem>
        EVEN_POSITIONED_ITEMS = ITEMS_WITH_INDEX.filterIndex(i -> i % 2 == 0);
}
```
