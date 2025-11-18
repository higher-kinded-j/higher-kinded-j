# Limiting Traversals: Focusing on List Portions

## _Declarative Slicing for Targeted Operations_

~~~admonish info title="What You'll Learn"
- How to focus on specific portions of lists (first n, last n, slices)
- Using `ListTraversals` factory methods for index-based operations
- The difference between limiting traversals and Stream's `limit()`/`skip()`
- Composing limiting traversals with lenses, prisms, and filtered optics
- Understanding edge case handling (negative indices, bounds exceeding list size)
- Real-world patterns for pagination, batch processing, and time-series windowing
- When to use limiting traversals vs Stream API vs manual loops
~~~

~~~admonish title="Example Code"
[ListTraversalsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/ListTraversalsExample.java)

[PaginationExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/PaginationExample.java)

[BatchProcessingExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/BatchProcessingExample.java)

[TimeSeriesWindowingExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/TimeSeriesWindowingExample.java)

[PredicateListTraversalsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/PredicateListTraversalsExample.java)
~~~

In our journey through optics, we've seen how **Traversal** handles bulk operations on all elements of a collection, and how **filtered optics** let us focus on elements matching a predicate. But what about focusing on elements by *position*—the first few items, the last few, or a specific slice?

Traditionally, working with list portions requires breaking out of your optic composition to use streams or manual index manipulation. **Limiting traversals** solve this elegantly by making positional focus a first-class part of your optic composition.

---

## The Scenario: Product Catalogue Management

Imagine you're building an e-commerce platform where you need to:
- Display only the **first 10 products** on a landing page
- Apply discounts to **all except the last 3** featured items
- Process customer orders in **chunks of 50** for batch shipping
- Analyse **the most recent 7 days** of time-series sales data
- Update metadata for products **between positions 5 and 15** in a ranked list

**The Data Model:**

```java
@GenerateLenses
public record Product(String sku, String name, double price, int stock) {
    Product applyDiscount(double percentage) {
        return new Product(sku, name, price * (1 - percentage), stock);
    }
}

@GenerateLenses
public record Catalogue(String name, List<Product> products) {}

@GenerateLenses
public record Order(String id, List<LineItem> items, LocalDateTime created) {}

@GenerateLenses
public record LineItem(Product product, int quantity) {}

@GenerateLenses
public record SalesMetric(LocalDate date, double revenue, int transactions) {}
```

**The Traditional Approach:**

```java
// Verbose: Manual slicing breaks optic composition
List<Product> firstTen = catalogue.products().subList(0, Math.min(10, catalogue.products().size()));
List<Product> discounted = firstTen.stream()
    .map(p -> p.applyDiscount(0.1))
    .collect(Collectors.toList());
// Now reconstruct the full list... tedious!
List<Product> fullList = new ArrayList<>(discounted);
fullList.addAll(catalogue.products().subList(Math.min(10, catalogue.products().size()), catalogue.products().size()));
Catalogue updated = new Catalogue(catalogue.name(), fullList);

// Even worse with nested structures
List<Order> chunk = orders.subList(startIndex, Math.min(startIndex + chunkSize, orders.size()));
// Process chunk... then what? How do we put it back?
```

This approach forces you to abandon the declarative power of optics, manually managing indices, bounds checking, and list reconstruction. **Limiting traversals** let you express this intent directly within your optic composition.

---

## Think of Limiting Traversals Like...

* **Java Stream's `limit()` and `skip()`**: Like `stream.limit(n)` and `stream.skip(n)`, but composable with immutable data transformations and integrated into optic pipelines
* **SQL's LIMIT and OFFSET clauses**: Like database pagination (`LIMIT 10 OFFSET 20`), but for in-memory immutable structures—enabling declarative pagination logic
* **Spring Batch chunk processing**: Similar to Spring Batch's chunk-oriented processing—divide a list into manageable segments for targeted transformation whilst preserving the complete dataset
* **ArrayList.subList() but better**: Like `List.subList(from, to)`, but instead of a mutable view, you get an immutable optic that composes with lenses, prisms, and filtered traversals

The key insight: positional focus becomes part of your optic's *identity*, not an external slicing operation applied afterwards.

---

## Five Ways to Limit Focus

Higher-kinded-j's `ListTraversals` utility class provides five complementary factory methods:

| Method | Description | SQL Equivalent |
|--------|-------------|----------------|
| **`taking(n)`** | Focus on first n elements | `LIMIT n` |
| **`dropping(n)`** | Skip first n, focus on rest | `OFFSET n` (then all) |
| **`takingLast(n)`** | Focus on last n elements | `ORDER BY id DESC LIMIT n` |
| **`droppingLast(n)`** | Focus on all except last n | `LIMIT (size - n)` |
| **`slicing(from, to)`** | Focus on range [from, to) | `LIMIT (to-from) OFFSET from` |

Each serves different needs, and they can be combined with other optics for powerful compositions.

---

## A Step-by-Step Walkthrough

### Step 1: Basic Usage — `taking(int n)`

The most intuitive method: focus on at most the first `n` elements.

```java
import org.higherkindedj.optics.util.ListTraversals;
import org.higherkindedj.optics.util.Traversals;

// Create a traversal for first 3 products
Traversal<List<Product>, Product> first3 = ListTraversals.taking(3);

List<Product> products = List.of(
    new Product("SKU001", "Widget", 10.0, 100),
    new Product("SKU002", "Gadget", 25.0, 50),
    new Product("SKU003", "Gizmo", 15.0, 75),
    new Product("SKU004", "Doohickey", 30.0, 25),
    new Product("SKU005", "Thingamajig", 20.0, 60)
);

// Apply 10% discount to ONLY first 3 products
List<Product> result = Traversals.modify(first3, p -> p.applyDiscount(0.1), products);
// First 3 discounted; last 2 preserved unchanged

// Extract ONLY first 3 products
List<Product> firstThree = Traversals.getAll(first3, products);
// Returns: [Widget, Gadget, Gizmo]
```

**Critical Semantic**: During **modification**, non-focused elements are *preserved unchanged* in the structure. During **queries** (like `getAll`), they are *excluded* from the results. This preserves the overall structure whilst focusing operations on the subset you care about.

### Step 2: Skipping Elements — `dropping(int n)`

Focus on all elements *after* skipping the first `n`:

```java
// Skip first 2, focus on the rest
Traversal<List<Product>, Product> afterFirst2 = ListTraversals.dropping(2);

List<Product> result = Traversals.modify(afterFirst2, p -> p.applyDiscount(0.15), products);
// First 2 unchanged; last 3 get 15% discount

List<Product> skipped = Traversals.getAll(afterFirst2, products);
// Returns: [Gizmo, Doohickey, Thingamajig]
```

### Step 3: Focusing on the End — `takingLast(int n)`

Focus on the last `n` elements—perfect for "most recent" scenarios:

```java
// Focus on last 2 products
Traversal<List<Product>, Product> last2 = ListTraversals.takingLast(2);

List<Product> result = Traversals.modify(last2, p -> p.applyDiscount(0.2), products);
// First 3 unchanged; last 2 get 20% discount

List<Product> lastTwo = Traversals.getAll(last2, products);
// Returns: [Doohickey, Thingamajig]
```

### Step 4: Excluding from the End — `droppingLast(int n)`

Focus on all elements *except* the last `n`:

```java
// Focus on all except last 2
Traversal<List<Product>, Product> exceptLast2 = ListTraversals.droppingLast(2);

List<Product> result = Traversals.modify(exceptLast2, p -> p.applyDiscount(0.05), products);
// First 3 get 5% discount; last 2 unchanged

List<Product> allButLastTwo = Traversals.getAll(exceptLast2, products);
// Returns: [Widget, Gadget, Gizmo]
```

### Step 5: Precise Slicing — `slicing(int from, int to)`

Focus on elements within a half-open range `[from, to)`, exactly like `List.subList()`:

```java
// Focus on indices 1, 2, 3 (0-indexed, exclusive end)
Traversal<List<Product>, Product> slice = ListTraversals.slicing(1, 4);

List<Product> result = Traversals.modify(slice, p -> p.applyDiscount(0.12), products);
// Index 0 unchanged; indices 1-3 discounted; index 4 unchanged

List<Product> sliced = Traversals.getAll(slice, products);
// Returns: [Gadget, Gizmo, Doohickey]
```

---

## Predicate-Based Focusing: Beyond Fixed Indices

Whilst index-based limiting is powerful, many real-world scenarios require **conditional focusing**—stopping when a condition is met rather than at a fixed position. `ListTraversals` provides three predicate-based methods that complement the fixed-index approaches:

| Method | Description | Use Case |
|--------|-------------|----------|
| **`takingWhile(Predicate)`** | Focus on longest prefix where predicate holds | Processing ordered data until threshold |
| **`droppingWhile(Predicate)`** | Skip prefix whilst predicate holds | Ignoring header/preamble sections |
| **`element(int)`** | Focus on single element at index (0-1 cardinality) | Safe indexed access without exceptions |

These methods enable **runtime-determined focusing**—the number of elements in focus depends on the data itself, not a predetermined count.

### Step 6: Conditional Prefix with `takingWhile(Predicate)`

The `takingWhile()` method focuses on the **longest prefix** of elements satisfying a predicate. Once an element fails the test, traversal stops—even if later elements would pass.

```java
// Focus on products whilst price < 20
Traversal<List<Product>, Product> affordablePrefix =
    ListTraversals.takingWhile(p -> p.price() < 20.0);

List<Product> products = List.of(
    new Product("SKU001", "Widget", 10.0, 100),
    new Product("SKU002", "Gadget", 15.0, 50),
    new Product("SKU003", "Gizmo", 25.0, 75),   // Stops here
    new Product("SKU004", "Thing", 12.0, 25)    // Not included despite < 20
);

// Apply discount only to initial affordable items
List<Product> result = Traversals.modify(
    affordablePrefix,
    p -> p.applyDiscount(0.1),
    products
);
// Widget and Gadget discounted; Gizmo and Thing unchanged

// Extract the affordable prefix
List<Product> affordable = Traversals.getAll(affordablePrefix, products);
// Returns: [Widget, Gadget]  (stops at first expensive item)
```

**Key Semantic**: Unlike `filtered()`, which tests all elements, `takingWhile()` is **sequential and prefix-oriented**. It's the optics equivalent of Stream's `takeWhile()`.

**Real-World Use Cases**:
- **Time-series data**: Process events before a timestamp threshold
- **Sorted lists**: Extract items below a value boundary
- **Log processing**: Capture startup messages before first error
- **Priority queues**: Handle high-priority items before switching logic

```java
// Time-series: Process transactions before cutoff
LocalDateTime cutoff = LocalDateTime.of(2025, 1, 1, 0, 0);
Traversal<List<Transaction>, Transaction> beforeCutoff =
    ListTraversals.takingWhile(t -> t.timestamp().isBefore(cutoff));

List<Transaction> processed = Traversals.modify(
    beforeCutoff,
    t -> t.withStatus("PROCESSED"),
    transactions
);
```

### Step 7: Skipping Prefix with `droppingWhile(Predicate)`

The `droppingWhile()` method is the complement to `takingWhile()`—it **skips the prefix** whilst the predicate holds, then focuses on all remaining elements.

```java
// Skip low-stock products, focus on well-stocked ones
Traversal<List<Product>, Product> wellStocked =
    ListTraversals.droppingWhile(p -> p.stock() < 50);

List<Product> products = List.of(
    new Product("SKU001", "Widget", 10.0, 20),
    new Product("SKU002", "Gadget", 25.0, 30),
    new Product("SKU003", "Gizmo", 15.0, 75),   // First to pass
    new Product("SKU004", "Thing", 12.0, 25)    // Included despite < 50
);

// Restock only well-stocked items (and everything after)
List<Product> restocked = Traversals.modify(
    wellStocked,
    p -> new Product(p.sku(), p.name(), p.price(), p.stock() + 50),
    products
);
// Widget and Gadget unchanged; Gizmo and Thing restocked

List<Product> focused = Traversals.getAll(wellStocked, products);
// Returns: [Gizmo, Thing]
```

**Real-World Use Cases**:
- **Skipping headers**: Process CSV data after metadata rows
- **Log analysis**: Ignore initialisation messages, focus on runtime
- **Pagination**: Skip already-processed records in batch jobs
- **Protocol parsing**: Discard handshake, process payload

```java
// Skip configuration lines in log file
Traversal<String, String> runtimeLogs =
    StringTraversals.lined()
        .filtered(line -> !line.startsWith("[CONFIG]"));

// Apply to log data
String logs = "[CONFIG] Database URL\n[CONFIG] Port\nINFO: System started\nERROR: Connection failed";
String result = Traversals.modify(runtimeLogs, String::toUpperCase, logs);
// Result: "[CONFIG] Database URL\n[CONFIG] Port\nINFO: SYSTEM STARTED\nERROR: CONNECTION FAILED"
```

### Step 8: Single Element Access with `element(int)`

The `element()` method creates an **affine traversal** (0-1 cardinality) focusing on a single element at the given index. Unlike direct array access, it never throws `IndexOutOfBoundsException`.

```java
// Focus on element at index 2
Traversal<List<Product>, Product> thirdProduct = ListTraversals.element(2);

List<Product> products = List.of(
    new Product("SKU001", "Widget", 10.0, 100),
    new Product("SKU002", "Gadget", 25.0, 50),
    new Product("SKU003", "Gizmo", 15.0, 75)
);

// Modify only the third product
List<Product> updated = Traversals.modify(
    thirdProduct,
    p -> p.applyDiscount(0.2),
    products
);
// Only Gizmo discounted

// Extract the element (if present)
List<Product> element = Traversals.getAll(thirdProduct, products);
// Returns: [Gizmo]

// Out of bounds: gracefully returns empty
List<Product> outOfBounds = Traversals.getAll(
    ListTraversals.element(10),
    products
);
// Returns: [] (no exception)
```

**When to Use `element()` vs `Ixed`**:
- **`element()`**: For composition with other traversals, when index is known at construction time
- **`Ixed`**: For dynamic indexed access, more general type class approach

```java
// Compose element() with nested structures
Traversal<List<List<Product>>, Product> secondListThirdProduct =
    ListTraversals.element(1)  // Second list
        .andThen(ListTraversals.element(2));  // Third product in that list

// Ixed for dynamic access
IxedInstances.listIxed().ix(userProvidedIndex).getOptional(products);
```

### Combining Predicate-Based and Index-Based Traversals

The real power emerges when mixing approaches:

```java
// Take first 10 products where stock > 0, then filter by price
Traversal<List<Product>, Product> topAffordableInStock =
    ListTraversals.taking(10)
        .andThen(ListTraversals.takingWhile(p -> p.stock() > 0))
        .filtered(p -> p.price() < 30.0);

// Skip warmup period, then take next 100 events
Traversal<List<Event>, Event> steadyState =
    ListTraversals.droppingWhile(e -> e.isWarmup())
        .andThen(ListTraversals.taking(100));
```

---

## Edge Case Handling

All limiting traversal methods handle edge cases gracefully and consistently:

| Edge Case | Behaviour | Rationale |
|-----------|-----------|-----------|
| **`n < 0`** | Treated as 0 (identity traversal) | Graceful degradation, no exceptions |
| **`n > list.size()`** | Clamped to list bounds | Focus on all available elements |
| **Empty list** | Returns empty list unchanged | No elements to focus on |
| **`from >= to` in slicing** | Identity traversal (no focus) | Empty range semantics |
| **Negative `from` in slicing** | Clamped to 0 | Start from beginning |

```java
// Examples of edge case handling
List<Integer> numbers = List.of(1, 2, 3);

// n > size: focuses on all elements
List<Integer> result1 = Traversals.getAll(ListTraversals.taking(100), numbers);
// Returns: [1, 2, 3]

// Negative n: identity (no focus)
List<Integer> result2 = Traversals.getAll(ListTraversals.taking(-5), numbers);
// Returns: []

// Inverted range: no focus
List<Integer> result3 = Traversals.getAll(ListTraversals.slicing(3, 1), numbers);
// Returns: []

// Empty list: safe operation
List<Integer> result4 = Traversals.modify(ListTraversals.taking(3), x -> x * 2, List.of());
// Returns: []
```

This philosophy ensures **no runtime exceptions** from index bounds, making limiting traversals safe for dynamic data.

---

## Composing Limiting Traversals

The real power emerges when you compose limiting traversals with other optics:

### With Lenses — Deep Updates

```java
Traversal<List<Product>, Product> first5 = ListTraversals.taking(5);
Lens<Product, Double> priceLens = ProductLenses.price();

// Compose: first 5 products → their prices
Traversal<List<Product>, Double> first5Prices =
    first5.andThen(priceLens.asTraversal());

// Increase prices of first 5 products by 10%
List<Product> result = Traversals.modify(first5Prices, price -> price * 1.1, products);
```

### With Filtered Traversals — Conditional Slicing

```java
// First 10 products that are also low stock
Traversal<List<Product>, Product> first10LowStock =
    ListTraversals.taking(10).filtered(p -> p.stock() < 50);

// Restock only first 10 low-stock products
List<Product> restocked = Traversals.modify(
    first10LowStock,
    p -> new Product(p.sku(), p.name(), p.price(), p.stock() + 100),
    products
);
```

### With Nested Structures — Batch Processing

```java
// Focus on first 50 orders
Traversal<List<Order>, Order> first50Orders = ListTraversals.taking(50);

// Focus on all line items in those orders
Traversal<List<Order>, LineItem> first50OrderItems =
    first50Orders.andThen(OrderTraversals.items());

// Apply bulk discount to items in first 50 orders
List<Order> processed = Traversals.modify(
    first50OrderItems,
    item -> new LineItem(item.product().applyDiscount(0.05), item.quantity()),
    orders
);
```

---

## When to Use Limiting Traversals vs Other Approaches

### Use Limiting Traversals When:

* **Positional focus** - You need to operate on elements by index position
* **Structural preservation** - Non-focused elements must remain in the list
* **Composable pipelines** - Building complex optic chains with lenses and prisms
* **Immutable updates** - Transforming portions whilst keeping data immutable
* **Reusable logic** - Define once, compose everywhere

```java
// Perfect: Declarative, composable, reusable
Traversal<Catalogue, Double> first10Prices =
    CatalogueLenses.products().asTraversal()
        .andThen(ListTraversals.taking(10))
        .andThen(ProductLenses.price().asTraversal());

Catalogue updated = Traversals.modify(first10Prices, p -> p * 0.9, catalogue);
```

### Use Stream API When:

* **Terminal operations** - Counting, finding, collecting to new structures
* **Complex transformations** - Multiple chained operations with sorting/grouping
* **No structural preservation needed** - You're extracting data, not updating in place
* **Performance-critical paths** - Minimal abstraction overhead

```java
// Better with streams: Complex aggregation
int totalStock = products.stream()
    .limit(100)
    .mapToInt(Product::stock)
    .sum();
```

### Use Manual Loops When:

* **Early termination with side effects** - Need to break out of loop
* **Index-dependent logic** - Processing depends on knowing the exact index
* **Imperative control flow** - Complex branching based on position

```java
// Sometimes explicit indexing is clearest
for (int i = 0; i < Math.min(10, products.size()); i++) {
    if (products.get(i).stock() == 0) {
        notifyOutOfStock(products.get(i), i);
        break;
    }
}
```

---

## Common Pitfalls

### ❌ Don't Do This:

```java
// Inefficient: Recreating traversals in loops
for (int page = 0; page < totalPages; page++) {
    var slice = ListTraversals.slicing(page * 10, (page + 1) * 10);
    processPage(Traversals.getAll(slice, products));
}

// Confusing: Mixing with Stream operations unnecessarily
List<Product> result = Traversals.getAll(ListTraversals.taking(5), products)
    .stream()
    .limit(3)  // Why limit again? Already took 5!
    .collect(toList());

// Wrong expectation: Thinking it removes elements
Traversal<List<Product>, Product> first3 = ListTraversals.taking(3);
List<Product> modified = Traversals.modify(first3, Product::applyDiscount, products);
// modified.size() == products.size()! Structure preserved, not truncated

// Over-engineering: Using slicing for single element
Traversal<List<Product>, Product> atIndex5 = ListTraversals.slicing(5, 6);
// Consider using Ixed type class for single-element access instead
```

### ✅ Do This Instead:

```java
// Efficient: Create traversal once, vary parameters
Traversal<List<Product>, Product> takeN(int n) {
    return ListTraversals.taking(n);
}
// Or store commonly used ones as constants
static final Traversal<List<Product>, Product> FIRST_PAGE = ListTraversals.taking(10);

// Clear: Keep operations at appropriate abstraction level
List<Product> firstFive = Traversals.getAll(ListTraversals.taking(5), products);
// If you need further processing, do it separately

// Correct expectation: Use getAll for extraction, modify for transformation
List<Product> onlyFirst5 = Traversals.getAll(first5, products);  // Extracts subset
List<Product> allWithFirst5Updated = Traversals.modify(first5, p -> p.applyDiscount(0.1), products);  // Updates in place

// Right tool: Use Ixed for single indexed access
Optional<Product> fifth = IxedInstances.listIxed().ix(4).getOptional(products);
```

---

## Performance Notes

Limiting traversals are optimised for efficiency:

* **Single pass**: No intermediate list creation—slicing happens during traversal
* **Structural sharing**: Unchanged portions of the list are reused, not copied
* **Lazy bounds checking**: Index calculations are minimal and performed once
* **No boxing overhead**: Direct list operations without stream intermediaries
* **Composable without penalty**: Chaining with other optics adds no extra iteration

**Best Practice**: Store frequently-used limiting traversals as constants:

```java
public class CatalogueOptics {
    // Pagination constants
    public static final int PAGE_SIZE = 20;

    public static Traversal<List<Product>, Product> page(int pageNum) {
        return ListTraversals.slicing(pageNum * PAGE_SIZE, (pageNum + 1) * PAGE_SIZE);
    }

    // Featured products (first 5)
    public static final Traversal<Catalogue, Product> FEATURED =
        CatalogueLenses.products().asTraversal()
            .andThen(ListTraversals.taking(5));

    // Latest additions (last 10)
    public static final Traversal<Catalogue, Product> LATEST =
        CatalogueLenses.products().asTraversal()
            .andThen(ListTraversals.takingLast(10));

    // Exclude promotional items at end
    public static final Traversal<Catalogue, Product> NON_PROMOTIONAL =
        CatalogueLenses.products().asTraversal()
            .andThen(ListTraversals.droppingLast(3));
}
```

---

## Real-World Example: E-Commerce Pagination

Here's a comprehensive example demonstrating limiting traversals in a business context:

```java
package org.higherkindedj.example.optics;

import org.higherkindedj.optics.*;
import org.higherkindedj.optics.util.*;
import java.util.*;

public class PaginationExample {

    public record Product(String sku, String name, double price, boolean featured) {
        Product applyDiscount(double pct) {
            return new Product(sku, name, price * (1 - pct), featured);
        }
    }

    public static void main(String[] args) {
        List<Product> catalogue = createCatalogue();

        System.out.println("=== E-COMMERCE PAGINATION WITH LIMITING TRAVERSALS ===\n");

        // --- Scenario 1: Basic Pagination ---
        System.out.println("--- Scenario 1: Paginated Product Display ---");

        int pageSize = 3;
        int totalPages = (int) Math.ceil(catalogue.size() / (double) pageSize);

        for (int page = 0; page < totalPages; page++) {
            Traversal<List<Product>, Product> pageTraversal =
                ListTraversals.slicing(page * pageSize, (page + 1) * pageSize);

            List<Product> pageProducts = Traversals.getAll(pageTraversal, catalogue);
            System.out.printf("Page %d: %s%n", page + 1,
                pageProducts.stream().map(Product::name).toList());
        }

        // --- Scenario 2: Featured Products ---
        System.out.println("\n--- Scenario 2: Featured Products (First 3) ---");

        Traversal<List<Product>, Product> featured = ListTraversals.taking(3);
        List<Product> featuredProducts = Traversals.getAll(featured, catalogue);
        featuredProducts.forEach(p ->
            System.out.printf("  ⭐ %s - £%.2f%n", p.name(), p.price()));

        // --- Scenario 3: Apply Discount to Featured ---
        System.out.println("\n--- Scenario 3: 10% Discount on Featured ---");

        List<Product> withDiscount = Traversals.modify(featured, p -> p.applyDiscount(0.1), catalogue);
        System.out.println("After discount on first 3:");
        withDiscount.forEach(p -> System.out.printf("  %s: £%.2f%n", p.name(), p.price()));

        // --- Scenario 4: Exclude Last Items ---
        System.out.println("\n--- Scenario 4: All Except Last 2 (Clearance) ---");

        Traversal<List<Product>, Product> nonClearance = ListTraversals.droppingLast(2);
        List<Product> regularStock = Traversals.getAll(nonClearance, catalogue);
        System.out.println("Regular stock: " + regularStock.stream().map(Product::name).toList());

        System.out.println("\n=== PAGINATION COMPLETE ===");
    }

    private static List<Product> createCatalogue() {
        return List.of(
            new Product("SKU001", "Laptop", 999.99, true),
            new Product("SKU002", "Mouse", 29.99, false),
            new Product("SKU003", "Keyboard", 79.99, true),
            new Product("SKU004", "Monitor", 349.99, true),
            new Product("SKU005", "Webcam", 89.99, false),
            new Product("SKU006", "Headset", 149.99, false),
            new Product("SKU007", "USB Hub", 39.99, false),
            new Product("SKU008", "Desk Lamp", 44.99, false)
        );
    }
}
```

**Expected Output:**

```
=== E-COMMERCE PAGINATION WITH LIMITING TRAVERSALS ===

--- Scenario 1: Paginated Product Display ---
Page 1: [Laptop, Mouse, Keyboard]
Page 2: [Monitor, Webcam, Headset]
Page 3: [USB Hub, Desk Lamp]

--- Scenario 2: Featured Products (First 3) ---
  ⭐ Laptop - £999.99
  ⭐ Mouse - £29.99
  ⭐ Keyboard - £79.99

--- Scenario 3: 10% Discount on Featured ---
After discount on first 3:
  Laptop: £899.99
  Mouse: £26.99
  Keyboard: £71.99
  Monitor: £349.99
  Webcam: £89.99
  Headset: £149.99
  USB Hub: £39.99
  Desk Lamp: £44.99

--- Scenario 4: All Except Last 2 (Clearance) ---
Regular stock: [Laptop, Mouse, Keyboard, Monitor, Webcam, Headset]

=== PAGINATION COMPLETE ===
```

---

## The Relationship to Functional Programming Libraries

For those familiar with functional programming, higher-kinded-j's limiting traversals are inspired by similar patterns in:

### Haskell's Lens Library

The [`Control.Lens.Traversal`](https://hackage.haskell.org/package/lens-5.2.3/docs/Control-Lens-Traversal.html) module provides:

```haskell
taking :: Int -> Traversal' [a] a
dropping :: Int -> Traversal' [a] a
```

These create traversals that focus on the first/remaining elements—exactly what our `ListTraversals.taking()` and `dropping()` do.

### Scala's Monocle Library

[Monocle](https://www.optics.dev/Monocle/) provides similar index-based optics:

```scala
import monocle.function.Index._

// Focus on element at index
val atIndex: Optional[List[A], A] = index(3)

// Take first n (via custom combinator)
val firstN: Traversal[List[A], A] = ...
```

### Key Differences in Higher-Kinded-J

* **Explicit Applicative instances** rather than implicit type class resolution
* **Java's type system** requires more explicit composition steps
* **Additional methods** like `takingLast` and `droppingLast` not standard in Haskell lens
* **Edge case handling** follows Java conventions (no exceptions, graceful clamping)

**Further Reading:**

* [Haskell Lens Tutorial](https://hackage.haskell.org/package/lens-tutorial-1.0.4/docs/Control-Lens-Tutorial.html) - Original inspiration for optics
* [Optics By Example](https://leanpub.com/optics-by-example) by Chris Penner - Comprehensive book on optics in Haskell
* [Monocle Documentation](https://www.optics.dev/Monocle/) - Scala optics library with similar patterns
* [Java Stream API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html) - Comparison with `limit()` and `skip()`

---

## Summary: The Power of Limiting Traversals

Limiting traversals bring **positional focus** into the heart of your optic compositions:

* **`taking(n)`**: Focus on first n elements
* **`dropping(n)`**: Skip first n, focus on rest
* **`takingLast(n)`**: Focus on last n elements
* **`droppingLast(n)`**: Focus on all except last n
* **`slicing(from, to)`**: Focus on index range [from, to)

These tools transform how you work with list portions in immutable data structures:

| Before (Imperative) | After (Declarative) |
|---------------------|---------------------|
| Manual `subList()` with bounds checking | Single limiting traversal |
| Index manipulation breaking composition | Positional focus as part of optic chain |
| Explicit list reconstruction | Automatic structural preservation |
| Mix of "what" and "how" | Pure expression of intent |

By incorporating limiting traversals into your toolkit, you gain:

* **Expressiveness**: Say "first 10 products" once, compose with other optics
* **Safety**: No `IndexOutOfBoundsException`—graceful edge case handling
* **Composability**: Chain with lenses, prisms, filtered traversals seamlessly
* **Immutability**: Structure preserved, only focused elements transformed
* **Clarity**: Business logic separate from index arithmetic

Limiting traversals represent the natural evolution of optics for list manipulation—where Stream's `limit()` and `skip()` meet the composable, type-safe world of functional optics, all whilst maintaining full referential transparency and structural preservation.

---

**Previous:** [Filtered Optics: Predicate-Based Composition](filtered_optics.md)
**Next:** [String Traversals: Declarative Text Processing](string_traversals.md)

