# Folds: A Practical Guide

## _Querying Immutable Data_

~~~admonish info title="What You'll Learn"
- How to query and extract data from complex structures without modification
- Using `@GenerateFolds` to create type-safe query optics automatically
- Understanding the relationship between Fold and the Foldable type class
- Aggregating data with monoids for sums, products, and custom combiners
- Composing folds with other optics for deep, conditional queries
- The difference between `getAll`, `preview`, `find`, `exists`, `all`, and `length`
- Maybe-based extensions for functional optional handling (`previewMaybe`, `findMaybe`, `getAllMaybe`)
- When to use Fold vs Traversal vs direct field access vs Stream API
- Building read-only data processing pipelines with clear intent
~~~

~~~admonish title="Example Code"
[FoldUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/FoldUsageExample.java)
~~~

In previous guides, we explored optics that allow both reading and writing: **`Lens`** for required fields, **`Prism`** for conditional variants, **`Iso`** for lossless conversions, and **`Traversal`** for bulk operations on collections.

But what if you need to perform read-only operations? What if you want to query, search, filter, or aggregate data without any possibility of modification? This is where **`Fold`** shines.

---

## The Scenario: Analysing E-Commerce Orders

A **`Fold`** is a read-only optic designed specifically for querying and data extraction. Think of it as a **database query tool** 🔍 or a **telescope** 🔭 that lets you peer into your data structures, extract information, and aggregate results—all without the ability to modify anything.

Consider an e-commerce system where you need to analyse orders:

**The Data Model:**

```java
@GenerateLenses
public record Product(String name, double price, String category, boolean inStock) {}

@GenerateLenses
@GenerateFolds  // Generate Folds for querying
public record Order(String orderId, List<Product> items, String customerName) {}

@GenerateLenses
@GenerateFolds
public record OrderHistory(List<Order> orders) {}
```

**Common Query Needs:**
* "Find all products in this order"
* "Get the first product or empty if none"
* "Check if any product is out of stock"
* "Count how many items are in the order"
* "Calculate the total price of all items"
* "Check if all items are under £100"

A `Fold` makes these queries type-safe, composable, and expressive.

---

## Think of Folds Like...

* **A database query**: Extracting specific data from complex structures
* **A read-only telescope**: Magnifying and examining data without touching it
* **A search engine**: Finding and collecting information efficiently
* **An aggregation pipeline**: Combining values according to rules (via monoids)
* **A reporter**: Summarising data into useful metrics

---

## Fold vs Traversal: Understanding the Difference

Before we dive deeper, it's crucial to understand how `Fold` relates to `Traversal`:

| Aspect | Traversal | Fold |
|--------|-----------|------|
| **Purpose** | Read and modify collections | Read-only queries |
| **Can modify?** | ✅ Yes (`set`, `modify`) | ❌ No |
| **Query operations** | ✅ Yes (via `getAll`, but not primary purpose) | ✅ Yes (designed for this) |
| **Intent clarity** | "I might modify this" | "I'm only reading this" |
| **Conversion** | Can be converted to Fold via `asFold()` | Cannot be converted to Traversal |
| **Use cases** | Bulk updates, validation with modifications | Queries, searches, aggregations |

**Key Insight**: Every `Traversal` can be viewed as a `Fold` (read-only subset), but not every `Fold` can be a `Traversal`. By choosing `Fold` when you only need reading, you make your code's intent clear and prevent accidental modifications.

---

## A Step-by-Step Walkthrough

### Step 1: Generating Folds

Just like with other optics, we use annotations to trigger automatic code generation. Annotating a record with **`@GenerateFolds`** creates a companion class (e.g., `OrderFolds`) containing a `Fold` for each field.

```java
import org.higherkindedj.optics.annotations.GenerateFolds;
import org.higherkindedj.optics.annotations.GenerateLenses;
import java.util.List;

@GenerateLenses
public record Product(String name, double price, String category, boolean inStock) {}

@GenerateLenses
@GenerateFolds
public record Order(String orderId, List<Product> items, String customerName) {}
```

This generates:
* `OrderFolds.items()` → `Fold<Order, Product>` (focuses on all products)
* `OrderFolds.orderId()` → `Fold<Order, String>` (focuses on the order ID)
* `OrderFolds.customerName()` → `Fold<Order, String>` (focuses on customer name)

#### Customising the Generated Package

By default, generated classes are placed in the same package as the annotated record. You can specify a different package using the `targetPackage` attribute:

```java
// Generated class will be placed in org.example.generated.optics
@GenerateFolds(targetPackage = "org.example.generated.optics")
public record Order(String orderId, List<Product> items, String customerName) {}
```

This is useful when you need to avoid name collisions or organise generated code separately.

### Step 2: The Core Fold Operations

A `Fold<S, A>` provides these essential query operations:

#### **`getAll(source)`**: Extract All Focused Values

Returns a `List<A>` containing all the values the Fold focuses on.

```java
Fold<Order, Product> itemsFold = OrderFolds.items();
Order order = new Order("ORD-123", List.of(
    new Product("Laptop", 999.99, "Electronics", true),
    new Product("Mouse", 25.00, "Electronics", true),
    new Product("Desk", 350.00, "Furniture", false)
), "Alice");

List<Product> allProducts = itemsFold.getAll(order);
// Result: [Product[Laptop, 999.99, ...], Product[Mouse, 25.00, ...], Product[Desk, 350.00, ...]]
```

#### **`preview(source)`**: Get the First Value

Returns an `Optional<A>` containing the first focused value, or `Optional.empty()` if none exist.

```java
Optional<Product> firstProduct = itemsFold.preview(order);
// Result: Optional[Product[Laptop, 999.99, ...]]

Order emptyOrder = new Order("ORD-456", List.of(), "Bob");
Optional<Product> noProduct = itemsFold.preview(emptyOrder);
// Result: Optional.empty
```

#### **`find(predicate, source)`**: Find First Matching Value

Returns an `Optional<A>` containing the first value that matches the predicate.

```java
Optional<Product> expensiveProduct = itemsFold.find(
    product -> product.price() > 500.00,
    order
);
// Result: Optional[Product[Laptop, 999.99, ...]]
```

#### **`exists(predicate, source)`**: Check If Any Match

Returns `true` if at least one focused value matches the predicate.

```java
boolean hasOutOfStock = itemsFold.exists(
    product -> !product.inStock(),
    order
);
// Result: true (Desk is out of stock)
```

#### **`all(predicate, source)`**: Check If All Match

Returns `true` if all focused values match the predicate (returns `true` for empty collections).

```java
boolean allInStock = itemsFold.all(
    product -> product.inStock(),
    order
);
// Result: false (Desk is out of stock)
```

#### **`isEmpty(source)`**: Check for Empty

Returns `true` if there are zero focused values.

```java
boolean hasItems = !itemsFold.isEmpty(order);
// Result: true
```

#### **`length(source)`**: Count Values

Returns the number of focused values as an `int`.

```java
int itemCount = itemsFold.length(order);
// Result: 3
```

### Step 2.5: Maybe-Based Fold Extensions

~~~admonish title="Extension Methods"
Higher-kinded-j provides extension methods that integrate `Fold` with the `Maybe` type, offering a more functional approach to handling absent values compared to Java's `Optional`. These extensions are available via static imports from `FoldExtensions`.
~~~

#### The Challenge: Working with Nullable Values

Standard Fold operations use `Optional<A>` for operations that might not find a value (like `preview` and `find`). While `Optional` works well, functional programming often prefers `Maybe` because it:

* Integrates seamlessly with Higher-Kinded Types (HKT)
* Works consistently with other monadic operations (`flatMap`, `map`, `fold`)
* Provides better composition with validation and error handling types
* Offers a more principled functional API

Think of `Maybe` as `Optional`'s more functional cousin—they both represent "a value or nothing", but `Maybe` plays more nicely with the rest of the functional toolkit.

#### Think of Maybe-Based Extensions Like...

* **A search that returns "found" or "not found"** - `Maybe` explicitly models presence or absence
* **A safe lookup in a dictionary** - Either you get the value wrapped in `Just`, or you get `Nothing`
* **A nullable pointer that can't cause NPE** - You must explicitly check before unwrapping
* **Optional's functional sibling** - Same concept, better integration with functional patterns

#### The Three Extension Methods

All three methods are static imports from `org.higherkindedj.optics.extensions.FoldExtensions`:

```java
import static org.higherkindedj.optics.extensions.FoldExtensions.*;
```

##### 1. `previewMaybe(fold, source)` - Get First Value as Maybe

The `previewMaybe` method is the `Maybe`-based equivalent of `preview()`. It returns the first focused value wrapped in `Maybe`, or `Maybe.nothing()` if none exist.

```java
import org.higherkindedj.hkt.maybe.Maybe;
import static org.higherkindedj.optics.extensions.FoldExtensions.previewMaybe;

Fold<Order, Product> itemsFold = OrderFolds.items();
Order order = new Order("ORD-123", List.of(
    new Product("Laptop", 999.99, "Electronics", true),
    new Product("Mouse", 25.00, "Electronics", true)
), "Alice");

Maybe<Product> firstProduct = previewMaybe(itemsFold, order);
// Result: Just(Product[Laptop, 999.99, ...])

Order emptyOrder = new Order("ORD-456", List.of(), "Bob");
Maybe<Product> noProduct = previewMaybe(itemsFold, emptyOrder);
// Result: Nothing
```

**When to use `previewMaybe` vs `preview`:**

* Use `previewMaybe` when working in a functional pipeline with other `Maybe` values
* Use `preview` when interoperating with standard Java code expecting `Optional`
* Use `previewMaybe` when you need HKT compatibility for generic functional abstractions

##### 2. `findMaybe(fold, predicate, source)` - Find First Match as Maybe

The `findMaybe` method is the `Maybe`-based equivalent of `find()`. It returns the first focused value matching the predicate, or `Maybe.nothing()` if no match is found.

```java
import static org.higherkindedj.optics.extensions.FoldExtensions.findMaybe;

Fold<Order, Product> itemsFold = OrderFolds.items();

Maybe<Product> expensiveProduct = findMaybe(
    itemsFold,
    product -> product.price() > 500.00,
    order
);
// Result: Just(Product[Laptop, 999.99, ...])

Maybe<Product> luxuryProduct = findMaybe(
    itemsFold,
    product -> product.price() > 5000.00,
    order
);
// Result: Nothing
```

**Common Use Cases:**

* **Product search**: Find first available item matching criteria
* **Validation**: Locate the first invalid field in a form
* **Configuration**: Find the first matching configuration option
* **Inventory**: Locate first in-stock item in a category

##### 3. `getAllMaybe(fold, source)` - Get All Values as Maybe-Wrapped List

The `getAllMaybe` method returns all focused values as `Maybe<List<A>>`. If the Fold finds at least one value, you get `Just(List<A>)`. If it finds nothing, you get `Nothing`.

This is particularly useful when you want to distinguish between "found an empty collection" and "found no results".

```java
import static org.higherkindedj.optics.extensions.FoldExtensions.getAllMaybe;

Fold<Order, Product> itemsFold = OrderFolds.items();

Maybe<List<Product>> allProducts = getAllMaybe(itemsFold, order);
// Result: Just([Product[Laptop, ...], Product[Mouse, ...]])

Order emptyOrder = new Order("ORD-456", List.of(), "Bob");
Maybe<List<Product>> noProducts = getAllMaybe(itemsFold, emptyOrder);
// Result: Nothing
```

**When to use `getAllMaybe` vs `getAll`:**

| Scenario | Use `getAll()` | Use `getAllMaybe()` |
|----------|----------------|---------------------|
| You need the list regardless of emptiness | ✅ Returns `List<A>` (possibly empty) | ❌ Overkill |
| You want to treat empty results as a failure case | ❌ Must check `isEmpty()` manually | ✅ Returns `Nothing` for empty results |
| You're chaining functional operations with Maybe | ❌ Requires conversion | ✅ Directly composable |
| Performance-critical batch processing | ✅ Direct list access | ❌ Extra Maybe wrapping |

#### Real-World Scenario: Product Search with Maybe

Here's a practical example showing how Maybe-based extensions simplify null-safe querying:

```java
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.annotations.GenerateFolds;
import org.higherkindedj.hkt.maybe.Maybe;
import static org.higherkindedj.optics.extensions.FoldExtensions.*;

@GenerateFolds
public record ProductCatalog(List<Product> products) {}

public class ProductSearchService {
    private static final Fold<ProductCatalog, Product> ALL_PRODUCTS =
        ProductCatalogFolds.products();

    // Find the cheapest in-stock product in a category
    public Maybe<Product> findCheapestInCategory(
        ProductCatalog catalog,
        String category
    ) {
        return getAllMaybe(ALL_PRODUCTS, catalog)
            .map(products -> products.stream()
                .filter(p -> category.equals(p.category()))
                .filter(Product::inStock)
                .min(Comparator.comparing(Product::price))
                .orElse(null)
            )
            .flatMap(Maybe::fromNullable);  // Convert null to Nothing
    }

    // Get first premium product (>£1000)
    public Maybe<Product> findPremiumProduct(ProductCatalog catalog) {
        return findMaybe(
            ALL_PRODUCTS,
            product -> product.price() > 1000.00,
            catalog
        );
    }

    // Check if any products are available
    public boolean hasAvailableProducts(ProductCatalog catalog) {
        return getAllMaybe(ALL_PRODUCTS, catalog)
            .map(products -> products.stream().anyMatch(Product::inStock))
            .getOrElse(false);
    }

    // Extract all product names (or empty message)
    public String getProductSummary(ProductCatalog catalog) {
        return getAllMaybe(ALL_PRODUCTS, catalog)
            .map(products -> products.stream()
                .map(Product::name)
                .collect(Collectors.joining(", "))
            )
            .getOrElse("No products available");
    }
}
```

#### Optional vs Maybe: A Comparison

Understanding when to use each type helps you make informed decisions:

| Aspect | `Optional<A>` | `Maybe<A>` |
|--------|---------------|------------|
| **Purpose** | Standard Java optional values | Functional optional values with HKT support |
| **Package** | `java.util.Optional` | `org.higherkindedj.hkt.maybe.Maybe` |
| **HKT Support** | ❌ No | ✅ Yes (integrates with `Kind<F, A>`) |
| **Monadic Operations** | Limited (`map`, `flatMap`, `filter`) | Full (`map`, `flatMap`, `filter`, `fold`, `getOrElse`, etc.) |
| **Java Interop** | ✅ Native support | ❌ Requires conversion |
| **Functional Composition** | Basic | ✅ Excellent (works with Applicative, Monad, etc.) |
| **Pattern Matching** | `ifPresent()`, `orElse()` | `isJust()`, `isNothing()`, `fold()` |
| **Use Cases** | Standard Java APIs, interop | Functional pipelines, HKT abstractions |
| **Conversion** | `Maybe.fromOptional(opt)` | `maybe.toOptional()` |

**Best Practice**: Use `Optional` at API boundaries (public methods, external libraries) and `Maybe` internally in functional pipelines.

#### When to Use Each Extension Method

Here's a decision matrix to help you choose the right method:

**Use `previewMaybe` when:**
* You need the first value from a Fold
* You're working in a functional pipeline with other `Maybe` values
* You want to chain operations (`map`, `flatMap`, `fold`) on the result
* You need HKT compatibility

```java
// Example: Get first expensive product and calculate discount
Maybe<Double> discountedPrice = previewMaybe(productsFold, order)
    .filter(p -> p.price() > 100)
    .map(p -> p.price() * 0.9);
```

**Use `findMaybe` when:**
* You need to locate a specific value matching a predicate
* You want to avoid the verbosity of `getAll().stream().filter().findFirst()`
* You're building search functionality
* You want to short-circuit on the first match (performance)

```java
// Example: Find first out-of-stock item
Maybe<Product> outOfStock = findMaybe(
    productsFold,
    p -> !p.inStock(),
    order
);
```

**Use `getAllMaybe` when:**
* You want to treat empty results as a "nothing" case
* You want to chain functional operations on the entire result set
* You're building batch processing pipelines
* You need to propagate "nothing found" through your computation

```java
// Example: Process all products or provide default behaviour
String report = getAllMaybe(productsFold, order)
    .map(products -> generateReport(products))
    .getOrElse("No products to report");
```

#### Integration with Existing Fold Operations

Maybe-based extensions work seamlessly alongside standard Fold operations. You can mix and match based on your needs:

```java
Fold<Order, Product> itemsFold = OrderFolds.items();

// Standard Fold operations
List<Product> allItems = itemsFold.getAll(order);           // Always returns list
Optional<Product> firstOpt = itemsFold.preview(order);     // Optional-based
int count = itemsFold.length(order);                        // Primitive int

// Maybe-based extensions
Maybe<Product> firstMaybe = previewMaybe(itemsFold, order);     // Maybe-based
Maybe<Product> matchMaybe = findMaybe(itemsFold, p -> ..., order);  // Maybe-based
Maybe<List<Product>> allMaybe = getAllMaybe(itemsFold, order);      // Maybe-wrapped list
```

**Conversion Between Optional and Maybe:**

```java
// Convert Optional to Maybe
Optional<Product> optional = itemsFold.preview(order);
Maybe<Product> maybe = Maybe.fromOptional(optional);

// Convert Maybe to Optional
Maybe<Product> maybe = previewMaybe(itemsFold, order);
Optional<Product> optional = maybe.toOptional();
```

#### Performance Considerations

Maybe-based extensions have minimal overhead:

* **`previewMaybe`**: Same performance as `preview()`, just wraps in `Maybe` instead of `Optional`
* **`findMaybe`**: Identical to `find()` - short-circuits on first match
* **`getAllMaybe`**: Adds one extra `Maybe` wrapping over `getAll()` - negligible cost

**Optimisation Tip**: For performance-critical code, prefer `getAll()` if you don't need the Maybe semantics. The extra wrapping and pattern matching adds a small but measurable cost in tight loops.

#### Practical Example: Safe Navigation with Maybe

Combining `getAllMaybe` with composed folds creates powerful null-safe query pipelines:

```java
import org.higherkindedj.optics.Fold;
import org.higherkindedj.hkt.maybe.Maybe;
import static org.higherkindedj.optics.extensions.FoldExtensions.*;

@GenerateFolds
public record OrderHistory(List<Order> orders) {}

public class OrderAnalytics {
    private static final Fold<OrderHistory, Order> ORDERS =
        OrderHistoryFolds.orders();
    private static final Fold<Order, Product> PRODUCTS =
        OrderFolds.items();

    // Calculate total revenue, handling empty history gracefully
    public double calculateRevenue(OrderHistory history) {
        return getAllMaybe(ORDERS, history)
            .flatMap(orders -> {
                List<Double> prices = orders.stream()
                    .flatMap(order -> getAllMaybe(PRODUCTS, order)
                        .map(products -> products.stream().map(Product::price))
                        .getOrElse(Stream.empty()))
                    .toList();
                return prices.isEmpty() ? Maybe.nothing() : Maybe.just(prices);
            })
            .map(prices -> prices.stream().mapToDouble(Double::doubleValue).sum())
            .getOrElse(0.0);
    }

    // Find most expensive product across all orders
    public Maybe<Product> findMostExpensive(OrderHistory history) {
        return getAllMaybe(ORDERS, history)
            .flatMap(orders -> {
                List<Product> allProducts = orders.stream()
                    .flatMap(order -> getAllMaybe(PRODUCTS, order)
                        .map(List::stream)
                        .getOrElse(Stream.empty()))
                    .toList();
                return allProducts.isEmpty()
                    ? Maybe.nothing()
                    : Maybe.fromNullable(allProducts.stream()
                        .max(Comparator.comparing(Product::price))
                        .orElse(null));
            });
    }
}
```

~~~admonish title="Complete Example"
See [FoldExtensionsExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/extensions/FoldExtensionsExample.java) for a runnable demonstration of all Maybe-based Fold extensions.
~~~

---

### Step 3: Composing Folds for Deep Queries

Folds can be composed with other optics to create deep query paths. When composing with `Lens`, `Prism`, or other `Fold` instances, use `andThen()`.

```java
// Get all product names from all orders in history
Fold<OrderHistory, Order> historyToOrders = OrderHistoryFolds.orders();
Fold<Order, Product> orderToProducts = OrderFolds.items();
Lens<Product, String> productToName = ProductLenses.name();

Fold<OrderHistory, String> historyToAllProductNames =
    historyToOrders
        .andThen(orderToProducts)
        .andThen(productToName.asFold());

OrderHistory history = new OrderHistory(List.of(order1, order2, order3));
List<String> allProductNames = historyToAllProductNames.getAll(history);
// Result: ["Laptop", "Mouse", "Desk", "Keyboard", "Monitor", ...]
```

### Step 4: Aggregation with `foldMap` and Monoids

The most powerful feature of `Fold` is its ability to aggregate data using **monoids**. This is where Fold truly shines for combining values in flexible, reusable ways.

#### Understanding Monoids: The Simple Explanation

Think of a monoid as a recipe for combining things. It needs two ingredients:

1. **A starting value** (called `empty`) - like starting with 0 when adding numbers, or "" when joining strings
2. **A combining rule** (called `combine`) - like "add these two numbers" or "concatenate these two strings"

**Simple Examples:**
* **Adding numbers**: Start with 0, combine by adding → `0 + 5 + 10 + 3 = 18`
* **Joining strings**: Start with "", combine by concatenating → `"" + "Hello" + " " + "World" = "Hello World"`
* **Finding maximum**: Start with negative infinity, combine by taking larger value
* **Checking all conditions**: Start with `true`, combine with AND (&&) → all must be true

#### The Power of `foldMap`

The `foldMap` method lets you:
1. Transform each focused value into a "combinable" type
2. Automatically merge all those values using a monoid

**Example: Calculate Total Price**

```java
import org.higherkindedj.hkt.Monoid;

Fold<Order, Product> products = OrderFolds.items();

// Define how to combine prices (addition)
Monoid<Double> sumMonoid = new Monoid<>() {
    @Override
    public Double empty() { return 0.0; }  // Start with zero
    @Override
    public Double combine(Double a, Double b) { return a + b; }  // Add them
};

// Extract each product's price and sum them all
double totalPrice = products.foldMap(
    sumMonoid,
    product -> product.price(),  // Extract price from each product
    order
);
// Result: 1374.99 (999.99 + 25.00 + 350.00)
```

**What's happening here?**
1. For each `Product` in the order, extract its `price` → `[999.99, 25.00, 350.00]`
2. Start with `0.0` (the empty value)
3. Combine them: `0.0 + 999.99 + 25.00 + 350.00 = 1374.99`

#### Common Monoid Patterns

Here are the most useful monoid patterns for everyday use. **Best Practice**: Use the standard implementations from the `Monoids` utility class whenever possible:

```java
import org.higherkindedj.hkt.Monoids;

// Standard monoids available out of the box:
Monoid<Double> sumDouble = Monoids.doubleAddition();
Monoid<Double> productDouble = Monoids.doubleMultiplication();
Monoid<Integer> sumInt = Monoids.integerAddition();
Monoid<Integer> productInt = Monoids.integerMultiplication();
Monoid<Long> sumLong = Monoids.longAddition();
Monoid<Boolean> andMonoid = Monoids.booleanAnd();
Monoid<Boolean> orMonoid = Monoids.booleanOr();
Monoid<String> stringConcat = Monoids.string();
Monoid<List<A>> listConcat = Monoids.list();
Monoid<Set<A>> setUnion = Monoids.set();
Monoid<Optional<A>> firstWins = Monoids.firstOptional();
Monoid<Optional<A>> lastWins = Monoids.lastOptional();
Monoid<Optional<A>> maxValue = Monoids.maximum();
Monoid<Optional<A>> minValue = Monoids.minimum();
```

**Sum (Adding Numbers)**
```java
// Use standard monoid from Monoids class
Monoid<Double> sumMonoid = Monoids.doubleAddition();

// Calculate total revenue
double revenue = productsFold.foldMap(sumMonoid, ProductItem::price, order);
```

**Product (Multiplying Numbers)**
```java
Monoid<Double> productMonoid = Monoids.doubleMultiplication();

// Calculate compound discount (e.g., 0.9 * 0.95 * 0.85)
double finalMultiplier = discountsFold.foldMap(productMonoid, d -> d, discounts);
```

**String Concatenation**
```java
Monoid<String> stringMonoid = Monoids.string();

// Join all product names
String allNames = productsFold.foldMap(stringMonoid, ProductItem::name, order);
```

**List Accumulation**
```java
Monoid<List<String>> listMonoid = Monoids.list();

// Collect all categories (with duplicates)
List<String> categories = productsFold.foldMap(listMonoid,
    p -> List.of(p.category()), order);
```

**Boolean AND (All Must Be True)**
```java
Monoid<Boolean> andMonoid = Monoids.booleanAnd();

// Check if all products are in stock
boolean allInStock = productsFold.foldMap(andMonoid, ProductItem::inStock, order);
```

**Boolean OR (Any Can Be True)**
```java
Monoid<Boolean> orMonoid = Monoids.booleanOr();

// Check if any product is expensive
boolean hasExpensive = productsFold.foldMap(orMonoid,
    p -> p.price() > 1000.0, order);
```

**Maximum Value**
```java
// Use Optional-based maximum from Monoids
Monoid<Optional<Double>> maxMonoid = Monoids.maximum();

// Find highest price (returns Optional to handle empty collections)
Optional<Double> maxPrice = productsFold.foldMap(maxMonoid,
    p -> Optional.of(p.price()), order);

// Or create a custom one for raw doubles:
Monoid<Double> rawMaxMonoid = new Monoid<>() {
    @Override public Double empty() { return Double.NEGATIVE_INFINITY; }
    @Override public Double combine(Double a, Double b) { return Math.max(a, b); }
};

double maxPriceRaw = productsFold.foldMap(rawMaxMonoid, ProductItem::price, order);
```

#### Why Monoids Matter

Monoids give you:
* **Composability**: Combine complex aggregations from simple building blocks
* **Reusability**: Define a monoid once, use it everywhere
* **Correctness**: The monoid laws guarantee consistent behaviour
* **Flexibility**: Create custom aggregations for your domain

**Pro Tip**: You can create custom monoids for any domain-specific aggregation logic, like calculating weighted averages, combining validation results, or merging configuration objects.

---

## When to Use Folds vs Other Approaches

### Use Fold When:

* **Read-only queries** - You only need to extract or check data
* **Intent matters** - You want to express "this is a query, not a modification"
* **Composable searches** - Building reusable query paths
* **Aggregations** - Using monoids for custom combining logic
* **CQRS patterns** - Separating queries from commands

```java
// Perfect for read-only analysis
Fold<OrderHistory, Product> allProducts =
    OrderHistoryFolds.orders()
        .andThen(OrderFolds.items());

boolean hasElectronics = allProducts.exists(
    p -> "Electronics".equals(p.category()),
    history
);
```

### Use Traversal When:

* **Modifications needed** - You need to update the data
* **Effectful updates** - Using `modifyF` for validation or async operations
* **Bulk transformations** - Changing multiple values at once

```java
// Use Traversal for modifications
Traversal<Order, Product> productTraversal = OrderTraversals.items();
Order discountedOrder = Traversals.modify(
    productTraversal.andThen(ProductLenses.price().asTraversal()),
    price -> price * 0.9,
    order
);
```

### Use Stream API When:

* **Complex filtering** - Multiple filter/map/reduce operations
* **Parallel processing** - Taking advantage of parallel streams
* **Standard Java collections** - Working with flat collections
* **Stateful operations** - Operations that require maintaining state

```java
// Better with streams for complex pipelines
List<String> topExpensiveItems = order.items().stream()
    .filter(p -> p.price() > 100)
    .sorted(Comparator.comparing(Product::price).reversed())
    .limit(5)
    .map(Product::name)
    .collect(toList());
```

### Use Direct Field Access When:

* **Simple cases** - Single, straightforward field read
* **Performance critical** - Minimal abstraction overhead
* **One-off operations** - Not building reusable logic

```java
// Just use direct access for simple cases
String customerName = order.customerName();
```

---

## Common Pitfalls

### ❌ Don't Do This:

```java
// Inefficient: Creating folds repeatedly in loops
for (Order order : orders) {
    Fold<Order, Product> fold = OrderFolds.items();
    List<Product> products = fold.getAll(order);
    // ... process products
}

// Over-engineering: Using Fold for trivial single-field access
Fold<Order, String> customerFold = OrderFolds.customerName();
String name = customerFold.getAll(order).get(0); // Just use order.customerName()!

// Wrong tool: Trying to modify data with a Fold
// Folds are read-only - this won't compile
// Fold<Order, Product> items = OrderFolds.items();
// Order updated = items.set(newProduct, order); // ❌ No 'set' method!

// Verbose: Unnecessary conversion when Traversal is already available
Traversal<Order, Product> traversal = OrderTraversals.items();
Fold<Order, Product> fold = traversal.asFold();
List<Product> products = fold.getAll(order); // Just use Traversals.getAll() directly!
```

### ✅ Do This Instead:

```java
// Efficient: Create fold once, reuse many times
Fold<Order, Product> itemsFold = OrderFolds.items();
for (Order order : orders) {
    List<Product> products = itemsFold.getAll(order);
    // ... process products
}

// Right tool: Direct access for simple cases
String name = order.customerName();

// Clear intent: Use Traversal when you need modifications
Traversal<Order, Product> itemsTraversal = OrderTraversals.items();
Order updated = Traversals.modify(itemsTraversal, this::applyDiscount, order);

// Clear purpose: Use Fold when expressing query intent
Fold<Order, Product> queryItems = OrderFolds.items();
boolean hasExpensive = queryItems.exists(p -> p.price() > 1000, order);
```

---

## Performance Notes

Folds are optimised for query operations:

* **Memory efficient**: Uses iterators internally, no intermediate collections for most operations
* **Lazy evaluation**: Short-circuits on operations like `find` and `exists` (stops at first match)
* **Reusable**: Composed folds can be stored and reused across your application
* **Type-safe**: All operations checked at compile time
* **Zero allocation**: `foldMap` with monoids avoids creating intermediate collections

**Best Practice**: For frequently used query paths, create them once and store as constants:

```java
public class OrderQueries {
    public static final Fold<OrderHistory, Product> ALL_PRODUCTS =
        OrderHistoryFolds.orders()
            .andThen(OrderFolds.items());

    public static final Fold<OrderHistory, Double> ALL_PRICES =
        ALL_PRODUCTS.andThen(ProductLenses.price().asFold());

    public static final Fold<Order, Product> ELECTRONICS =
        OrderFolds.items(); // Can filter with exists/find/getAll + stream filter
}
```

---

## Real-World Example: Order Analytics

Here's a practical example showing comprehensive use of Fold for business analytics:

```java
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFolds;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.hkt.Monoid;
import java.time.LocalDate;
import java.util.*;

@GenerateLenses
@GenerateFolds
public record Product(String name, double price, String category, boolean inStock) {}

@GenerateLenses
@GenerateFolds
public record Order(String orderId, List<Product> items, String customerName, LocalDate orderDate) {}

@GenerateLenses
@GenerateFolds
public record OrderHistory(List<Order> orders) {}

public class OrderAnalytics {
    private static final Fold<Order, Product> ORDER_ITEMS = OrderFolds.items();
    private static final Fold<OrderHistory, Order> HISTORY_ORDERS = OrderHistoryFolds.orders();
    private static final Fold<OrderHistory, Product> ALL_PRODUCTS =
        HISTORY_ORDERS.andThen(ORDER_ITEMS);

    private static final Monoid<Double> SUM_MONOID = new Monoid<>() {
        @Override public Double empty() { return 0.0; }
        @Override public Double combine(Double a, Double b) { return a + b; }
    };

    // Calculate total revenue across all orders
    public static double calculateRevenue(OrderHistory history) {
        return ALL_PRODUCTS.foldMap(SUM_MONOID, Product::price, history);
    }

    // Find most expensive product across all orders
    public static Optional<Product> findMostExpensiveProduct(OrderHistory history) {
        return ALL_PRODUCTS.getAll(history).stream()
            .max(Comparator.comparing(Product::price));
    }

    // Check if any order has out-of-stock items
    public static boolean hasOutOfStockIssues(OrderHistory history) {
        return ALL_PRODUCTS.exists(p -> !p.inStock(), history);
    }

    // Get all unique categories
    public static Set<String> getAllCategories(OrderHistory history) {
        Fold<OrderHistory, String> categories =
            ALL_PRODUCTS.andThen(ProductLenses.category().asFold());
        return new HashSet<>(categories.getAll(history));
    }

    // Count products in a specific category
    public static int countByCategory(OrderHistory history, String category) {
        return (int) ALL_PRODUCTS.getAll(history).stream()
            .filter(p -> category.equals(p.category()))
            .count();
    }

    // Calculate average order value
    public static double calculateAverageOrderValue(OrderHistory history) {
        List<Order> allOrders = HISTORY_ORDERS.getAll(history);
        if (allOrders.isEmpty()) return 0.0;

        double totalRevenue = calculateRevenue(history);
        return totalRevenue / allOrders.size();
    }

    // Find orders with specific product
    public static List<Order> findOrdersContaining(OrderHistory history, String productName) {
        return HISTORY_ORDERS.getAll(history).stream()
            .filter(order -> ORDER_ITEMS.exists(
                p -> productName.equals(p.name()),
                order
            ))
            .toList();
    }
}
```

---

## The Relationship to Foldable

### Quick Summary

If you're just getting started, here's what you need to know: A `Fold<S, A>` is closely related to the `Foldable` type class from functional programming. While `Foldable<F>` works with any container type `F` (like `List`, `Optional`, `Tree`), a `Fold<S, A>` lets you treat any structure `S` as if it were a foldable container of `A` values—even when `S` isn't actually a collection.

**Key Connection**: Both use `foldMap` to aggregate values using monoids. The `Fold` optic brings this powerful abstraction to arbitrary data structures, not just collections.

### In-Depth Explanation

For those familiar with functional programming or interested in the deeper theory:

#### The Foldable Type Class

The [`Foldable<F>` type class](../functional/foldable_and_traverse.md) in higher-kinded-j represents any data structure `F` that can be "folded up" or reduced to a summary value. It's defined with this signature:

```java
public interface Foldable<F> {
  <A, M> M foldMap(
      Monoid<M> monoid,
      Function<? super A, ? extends M> f,
      Kind<F, A> fa
  );
}
```

Common instances include:
* `List<A>` - fold over all elements
* `Optional<A>` - fold over zero or one element
* `Either<E, A>` - fold over the right value if present
* `Tree<A>` - fold over all nodes in a tree

#### How Fold Relates to Foldable

A `Fold<S, A>` can be thought of as a **first-class, composable lens into a Foldable structure**. More precisely:

1. **Virtualization**: `Fold<S, A>` lets you "view" any structure `S` as a virtual `Foldable` container of `A` values, even if `S` is not inherently a collection
2. **Composition**: Unlike `Foldable<F>`, which is fixed to a specific container type `F`, `Fold<S, A>` can be composed with other optics to create deep query paths
3. **Reification**: A `Fold` reifies (makes concrete) the act of folding, turning it into a first-class value you can pass around, store, and combine

**Example Comparison**:

```java
// Using Foldable directly on a List
Foldable<ListKind.Witness> listFoldable = ListTraverse.INSTANCE;
List<Integer> numbers = List.of(1, 2, 3, 4, 5);
int sum = listFoldable.foldMap(sumMonoid, Function.identity(), LIST.widen(numbers));

// Using a Fold optic to query nested structure
Fold<Order, Integer> quantities = OrderFolds.items()
    .andThen(ProductLenses.quantity().asFold());
int totalQuantity = quantities.foldMap(sumMonoid, Function.identity(), order);
```

The `Fold` optic gives you the power of `Foldable`, but for **arbitrary access paths** through your domain model, not just direct containers.

#### Fold Laws and Foldable Laws

Both `Fold` and `Foldable` obey the same monoid laws:

1. **Left identity**: `combine(empty, x) = x`
2. **Right identity**: `combine(x, empty) = x`
3. **Associativity**: `combine(combine(x, y), z) = combine(x, combine(y, z))`

This means `foldMap` produces consistent, predictable results regardless of how the fold is internally structured.

#### Practical Implications

Understanding this relationship helps you:

* **Transfer knowledge**: If you learn `Foldable`, you understand the core of `Fold`
* **Recognise patterns**: Monoid aggregation is universal across both abstractions
* **Build intuition**: A `Fold` is like having a custom `Foldable` instance for each access path in your domain
* **Compose freely**: You can convert between optics and type classes when needed (e.g., `Lens.asFold()`)

**Further Reading**:
* [Foldable and Traverse in higher-kinded-j](../functional/foldable_and_traverse.md) - Deep dive into the type class
* [Haskell Lens Library - Folds](https://hackage.haskell.org/package/lens-5.2.3/docs/Control-Lens-Fold.html) - The original inspiration
* [Optics By Example (Book)](https://leanpub.com/optics-by-example) - Comprehensive treatment of folds in Haskell

---

## Complete, Runnable Example

This example demonstrates all major Fold operations in a single, cohesive application:

```java
package org.higherkindedj.example.optics;

import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFolds;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import java.util.*;

public class FoldUsageExample {

    @GenerateLenses
    @GenerateFolds
    public record ProductItem(String name, double price, String category, boolean inStock) {}

    @GenerateLenses
    @GenerateFolds
    public record Order(String orderId, List<ProductItem> items, String customerName) {}

    @GenerateLenses
    @GenerateFolds
    public record OrderHistory(List<Order> orders) {}

    public static void main(String[] args) {
        // Create sample data
        var order1 = new Order("ORD-001", List.of(
            new ProductItem("Laptop", 999.99, "Electronics", true),
            new ProductItem("Mouse", 25.00, "Electronics", true),
            new ProductItem("Desk", 350.00, "Furniture", false)
        ), "Alice");

        var order2 = new Order("ORD-002", List.of(
            new ProductItem("Keyboard", 75.00, "Electronics", true),
            new ProductItem("Monitor", 450.00, "Electronics", true),
            new ProductItem("Chair", 200.00, "Furniture", true)
        ), "Bob");

        var history = new OrderHistory(List.of(order1, order2));

        System.out.println("=== FOLD USAGE EXAMPLE ===\n");

        // --- SCENARIO 1: Basic Query Operations ---
        System.out.println("--- Scenario 1: Basic Query Operations ---");
        Fold<Order, ProductItem> itemsFold = OrderFolds.items();

        List<ProductItem> allItems = itemsFold.getAll(order1);
        System.out.println("All items: " + allItems.size() + " products");

        Optional<ProductItem> firstItem = itemsFold.preview(order1);
        System.out.println("First item: " + firstItem.map(ProductItem::name).orElse("none"));

        int count = itemsFold.length(order1);
        System.out.println("Item count: " + count);

        boolean isEmpty = itemsFold.isEmpty(order1);
        System.out.println("Is empty: " + isEmpty + "\n");

        // --- SCENARIO 2: Conditional Queries ---
        System.out.println("--- Scenario 2: Conditional Queries ---");

        boolean hasOutOfStock = itemsFold.exists(p -> !p.inStock(), order1);
        System.out.println("Has out of stock items: " + hasOutOfStock);

        boolean allInStock = itemsFold.all(ProductItem::inStock, order1);
        System.out.println("All items in stock: " + allInStock);

        Optional<ProductItem> expensiveItem = itemsFold.find(p -> p.price() > 500, order1);
        System.out.println("First expensive item: " + expensiveItem.map(ProductItem::name).orElse("none") + "\n");

        // --- SCENARIO 3: Composition ---
        System.out.println("--- Scenario 3: Composed Folds ---");

        Fold<OrderHistory, ProductItem> allProducts =
            OrderHistoryFolds.orders().andThen(OrderFolds.items());

        List<ProductItem> allProductsFromHistory = allProducts.getAll(history);
        System.out.println("Total products across all orders: " + allProductsFromHistory.size());

        Fold<OrderHistory, String> allCategories =
            allProducts.andThen(ProductItemLenses.category().asFold());

        Set<String> uniqueCategories = new HashSet<>(allCategories.getAll(history));
        System.out.println("Unique categories: " + uniqueCategories + "\n");

        // --- SCENARIO 4: Monoid Aggregation ---
        System.out.println("--- Scenario 4: Monoid-Based Aggregation ---");

        // Use standard monoids from Monoids utility class
        Monoid<Double> sumMonoid = Monoids.doubleAddition();

        double orderTotal = itemsFold.foldMap(sumMonoid, ProductItem::price, order1);
        System.out.println("Order 1 total: £" + String.format("%.2f", orderTotal));

        double historyTotal = allProducts.foldMap(sumMonoid, ProductItem::price, history);
        System.out.println("All orders total: £" + String.format("%.2f", historyTotal));

        // Boolean AND monoid for checking conditions
        Monoid<Boolean> andMonoid = Monoids.booleanAnd();

        boolean allAffordable = itemsFold.foldMap(andMonoid, p -> p.price() < 1000, order1);
        System.out.println("All items under £1000: " + allAffordable);

        // Boolean OR monoid for checking any condition
        Monoid<Boolean> orMonoid = Monoids.booleanOr();

        boolean hasElectronics = allProducts.foldMap(orMonoid,
            p -> "Electronics".equals(p.category()), history);
        System.out.println("Has electronics: " + hasElectronics + "\n");

        // --- SCENARIO 5: Analytics ---
        System.out.println("--- Scenario 5: Real-World Analytics ---");

        // Most expensive product
        Optional<ProductItem> mostExpensive = allProducts.getAll(history).stream()
            .max(Comparator.comparing(ProductItem::price));
        System.out.println("Most expensive product: " +
            mostExpensive.map(p -> p.name() + " (£" + p.price() + ")").orElse("none"));

        // Average price
        List<ProductItem> allProds = allProducts.getAll(history);
        double avgPrice = allProds.isEmpty() ? 0.0 :
            historyTotal / allProds.size();
        System.out.println("Average product price: £" + String.format("%.2f", avgPrice));

        // Count by category
        long electronicsCount = allProducts.getAll(history).stream()
            .filter(p -> "Electronics".equals(p.category()))
            .count();
        System.out.println("Electronics count: " + electronicsCount);

        System.out.println("\n=== END OF EXAMPLE ===");
    }
}
```

**Expected Output:**

```
=== FOLD USAGE EXAMPLE ===

--- Scenario 1: Basic Query Operations ---
All items: 3 products
First item: Laptop
Item count: 3
Is empty: false

--- Scenario 2: Conditional Queries ---
Has out of stock items: true
All items in stock: false
First expensive item: Laptop

--- Scenario 3: Composed Folds ---
Total products across all orders: 6
Unique categories: [Electronics, Furniture]

--- Scenario 4: Monoid-Based Aggregation ---
Order 1 total: £1374.99
All orders total: £2099.99
All items under £1000: true
Has electronics: true

--- Scenario 5: Real-World Analytics ---
Most expensive product: Laptop (£999.99)
Average product price: £349.99
Electronics count: 4

=== END OF EXAMPLE ===
```

---

## Why Folds Are Essential

`Fold` completes the optics toolkit by providing:

* **Clear Intent**: Explicitly read-only operations prevent accidental modifications
* **Composability**: Chain folds with other optics for deep queries
* **Aggregation Power**: Use monoids for flexible, reusable combining logic
* **Type Safety**: All queries checked at compile time
* **Reusability**: Build query libraries tailored to your domain
* **CQRS Support**: Separate query models from command models cleanly
* **Performance**: Optimised for read-only access with short-circuiting and lazy evaluation

By adding `Fold` to your arsenal alongside `Lens`, `Prism`, `Iso`, and `Traversal`, you have complete coverage for both reading and writing immutable data structures in a type-safe, composable way.

The key insight: **Folds make queries first-class citizens in your codebase**, just as valuable and well-designed as the commands that modify state.

---

**Previous:** [Traversals: Handling Bulk Updates](traversals.md)
**Next:** [Filtered Optics: Predicate-Based Composition](filtered_optics.md)
