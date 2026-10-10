# Filtered Optics: Predicate-Based Composition

_Change only the elements that match a condition, and keep the others in place, unchanged._

~~~admonish info title="What You'll Learn"
- Filter a traversal or a fold with `filtered`, and chain filters for AND logic
- Predict which elements `modify` keeps unchanged and which `getAll` leaves out
- Insert a reusable filter anywhere in a chain with `Traversals.filtered`
- Select elements by a nested query, such as customers with an unpaid order, with `filterBy`
- Fix a filtered update whose second run targets fewer elements, by filtering on fields it does not change
~~~

~~~admonish example title="See Example Code"
[FilteredTraversalExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/FilteredTraversalExample.java)
~~~

In our journey through optics, we've seen how **Traversal** updates every element of a collection and how **Fold** provides read-only queries. But what happens when you need to operate on only *some* elements, those that satisfy a specific condition?

Traditionally, filtering requires breaking out of your optic composition to use streams or loops, mixing the *what* (your transformation logic) with the *how* (iteration and filtering). **Filtered optics** solve this elegantly by making filtering a first-class part of your optic composition.

---

## The Scenario: Selective Updates in the Order Service

Imagine you're building the order service, where you need to:
- Discount only an order's **bulk lines**: those with a quantity of four or more
- Take the VIP mark off the customers with an **unpaid** order
- Analyse spending patterns for customers with **high-value orders**
- Tag the customers on orders with a line **over £500**

**The Data Model:** the chapter's cast, an `Order` of `LineItem`s placed by a `Customer`, with an `OrderStatus`.

~~~admonish example title="The cast these examples use" collapsible=true
``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/Order.java:order}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/LineItem.java:line_item}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/Customer.java:customer}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/EmailAddress.java:email_address}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/OrderStatus.java:order_status}}
```
~~~

A customer's orders are kept beside them in a history, which also marks whether the customer is a VIP:

<!-- verify -->
```java
@GenerateLenses
public record CustomerHistory(Customer customer, List<Order> orders, boolean vip) {}
```

The examples discount a line with this function:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/filtered/FilteredOpticsBook.java:ten_percent_off}}
```

**The Traditional Approach:**

<!-- verify -->
```java
// Verbose: Manual filtering breaks optic composition
List<LineItem> updatedLines = order.lines().stream()
    .map(line -> line.quantity() >= 4 ? tenPercentOff.apply(line) : line)
    .collect(Collectors.toList());
Order updatedOrder = new Order(order.id(), order.customer(), updatedLines,
    order.placedAt(), order.currency(), order.status());

// Even worse with nested structures: the customers with an unpaid order,
// one still NEW (placed, not yet paid)
List<CustomerHistory> withUnpaid = histories.stream()
    .filter(history -> history.orders().stream()
        .anyMatch(o -> o.status() == OrderStatus.NEW))
    .collect(Collectors.toList());
```

This approach forces you to abandon the declarative power of optics, manually managing iteration and reconstruction. **Filtered optics** let you express this intent directly within your optic composition.

A filtered traversal plays the part of the conditional `map` in the traditional approach, `line.quantity() >= 4 ? tenPercentOff.apply(line) : line`: matching elements change and the rest pass through untouched. Unlike that stream, it composes into a longer path and puts the list back for you, and unlike a stream's `filter`, it never drops an element.

---

## Three Ways to Filter

Higher-Kinded-J provides three complementary approaches to filtered optics:

| Approach | Signature | Use Case |
|----------|-----------|----------|
| **Instance method** | `traversal.filtered(predicate)` | Filter within an existing traversal |
| **Static combinator** | `Traversals.filtered(predicate)` | Create a reusable affine traversal |
| **Query-based filter** | `traversal.filterBy(fold, predicate)` | Filter based on nested properties |

Each serves different needs, and they can be combined for powerful compositions.

---

## A Step-by-Step Walkthrough

### Step 1: The Instance Method `filtered(Predicate)`

The most intuitive approach: call `filtered()` on any `Traversal` or `Fold` to create a new optic that only focuses on matching elements.

#### On Traversals (Read + Write)

<!-- verify -->
```java
// Create a traversal for all lines
Traversal<List<LineItem>, LineItem> allLines = Traversals.forList();

// Filter to bulk lines only
Traversal<List<LineItem>, LineItem> bulkLines = allLines.filtered(line -> line.quantity() >= 4);

// Discount ONLY the bulk lines
List<LineItem> result = Traversals.modify(bulkLines, tenPercentOff, lines);
// Bulk lines are discounted; the other lines are preserved unchanged

// Extract ONLY the bulk lines
List<LineItem> bulk = Traversals.getAll(bulkLines, lines);
// Returns only those matching the predicate
```

**Critical Semantic**: During **modification**, non-matching elements are *preserved unchanged* in the structure. During **queries** (like `getAll`), they are *excluded* from the results. This preserves the overall structure whilst focusing operations on the subset you care about.

~~~admonish warning title="The filtered caveat"
`filtered` behaves predictably only while the modification cannot change the predicate's own verdict. Filter on a price under £5, apply a rise that lifts a line past £5, and the second run of the same update targets fewer elements than the first: the operation is not idempotent. (A verdict can flip the other way too, pulling new elements in.) Keep the predicate on fields the modification does not touch, as the bulk discount does, or accept the non-idempotence deliberately.
~~~

#### On Folds (Read-Only)

<!-- verify -->
```java
// Fold from an Order to its lines
Fold<Order, LineItem> linesFold = Fold.of(Order::lines);

// Filter to bulk lines only
Fold<Order, LineItem> bulkLines = linesFold.filtered(line -> line.quantity() >= 4);

// Monoids has no BigDecimal sum, so write one: zero, and add
Monoid<BigDecimal> sum = new Monoid<>() {
    public BigDecimal empty() { return BigDecimal.ZERO; }
    public BigDecimal combine(BigDecimal a, BigDecimal b) { return a.add(b); }
};

// Query operations work on the filtered subset
int count = bulkLines.length(order);           // Count bulk lines
List<LineItem> bulk = bulkLines.getAll(order); // Get bulk lines
BigDecimal bulkValue = bulkLines.foldMap(      // Sum what the bulk lines cost
    sum, line -> line.price().multiply(BigDecimal.valueOf(line.quantity())), order);
boolean allCheap = bulkLines.all(
    line -> line.price().compareTo(new BigDecimal("10")) < 0, order);
```

### Step 2: Composing Filtered Traversals

Composed with the price lens, a filtered traversal over the bulk lines reads and changes only those lines' prices:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/filtered/FilteredOpticsBook.java:compose}}
```

### Step 3: Chaining Multiple Filters

Filters can be chained to create complex predicates:

<!-- verify -->
```java
// Bulk lines priced under £5 (AND logic)
Traversal<List<LineItem>, LineItem> cheapBulkLines =
    Traversals.<LineItem>forList()
        .filtered(line -> line.quantity() >= 4)
        .filtered(line -> line.price().compareTo(new BigDecimal("5")) < 0);

// Paid or shipped orders
Traversal<List<Order>, Order> settledOrders =
    Traversals.<Order>forList()
        .filtered(order -> order.status() == OrderStatus.PAID
            || order.status() == OrderStatus.SHIPPED);
```

### Step 4: The Static Combinator `Traversals.filtered()`

The static method creates an **affine traversal**: it focuses zero or one element, depending on the predicate. It is typed `Traversal` rather than [`Affine`](affine.md) so that it slots directly into traversal chains; "affine" here describes how many elements it focuses, not the Java type:

<!-- verify -->
```java
// Create a reusable filter
Traversal<LineItem, LineItem> bulkFilter = Traversals.filtered(line -> line.quantity() >= 4);

// Use standalone
LineItem bulbs = new LineItem("BULB", 4, new BigDecimal("2.50"));
LineItem result = Traversals.modify(bulkFilter, tenPercentOff, bulbs);
// If bulk, takes ten per cent off; otherwise returns it unchanged

// Compose into a pipeline
Traversal<List<LineItem>, String> bulkSkus =
    Traversals.<LineItem>forList()
        .andThen(Traversals.filtered(line -> line.quantity() >= 4))  // Static combinator
        .andThen(LineItemLenses.sku());
```

**When to use the static combinator vs instance method:**

- **Static combinator**: When you want a reusable filter that can be inserted into different compositions
- **Instance method**: When filtering is a natural part of a specific traversal's behaviour

Both approaches are semantically equivalent; choose based on readability and reusability:

<!-- verify -->
```java
// These are equivalent:
Traversal<List<LineItem>, LineItem> approach1 =
    Traversals.<LineItem>forList().filtered(line -> line.quantity() >= 4);
Traversal<List<LineItem>, LineItem> approach2 =
    Traversals.<LineItem>forList().andThen(Traversals.filtered(line -> line.quantity() >= 4));
```

### Step 5: Advanced Filtering with `filterBy(Fold, Predicate)`

Sometimes you need to filter based on *nested* properties or aggregated queries. The `filterBy` method accepts a `Fold` that queries each element, including only those where at least one queried value matches the predicate.

**Example: Customers with Unpaid Orders**

<!-- verify -->
```java
Traversal<List<CustomerHistory>, CustomerHistory> allHistories = Traversals.forList();
Fold<CustomerHistory, Order> historyOrders = Fold.of(CustomerHistory::orders);

// Filter customers that have ANY unpaid order
Traversal<List<CustomerHistory>, CustomerHistory> withUnpaid =
    allHistories.filterBy(historyOrders, order -> order.status() == OrderStatus.NEW);

// Take the VIP mark off customers with an unpaid order
Lens<CustomerHistory, Boolean> vipLens = CustomerHistoryLenses.vip();
List<CustomerHistory> updated = Traversals.modify(
    withUnpaid.andThen(vipLens),
    vip -> false,  // Until they pay
    histories
);
```

**Example: Querying Through a Composed Path**

<!-- verify -->
```java
Traversal<List<Order>, Order> allOrders = Traversals.forList();

// Fold from an Order to every line's price
Fold<Order, BigDecimal> linePrices =
    OrderTraversals.lines().asFold().andThen(LineItemLenses.price().asFold());

// Orders with any line priced over £500
Traversal<List<Order>, Order> bigTicketOrders =
    allOrders.filterBy(linePrices, price -> price.compareTo(new BigDecimal("500")) > 0);

// Tag their customer's name
Traversal<List<Order>, String> bigTicketNames =
    bigTicketOrders.andThen(OrderLenses.customer()).andThen(CustomerLenses.name());

List<Order> result = Traversals.modify(
    bigTicketNames,
    name -> name + " [BIG TICKET]",
    orders
);
```

---

## Understanding the Semantics: Preserved vs Excluded

A crucial aspect of filtered optics is understanding what happens to non-matching elements:

| Operation | Non-Matching Elements |
|-----------|----------------------|
| **`modify`** / **`modifyF`** | Preserved unchanged in the structure |
| **`getAll`** | Excluded from results |
| **`foldMap`** / **`exists`** / **`all`** | Excluded from aggregation |
| **`length`** | Not counted |

**Visual Example:**

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/filtered/FilteredOpticsBook.java:semantics}}
```

This behaviour is intentional: it allows you to **transform selectively** whilst maintaining referential integrity, and **query selectively** without polluting results.

---

## When to Use Filtered Optics vs Other Approaches

### Use Filtered Optics When

* **Declarative composition**: You want filtering to be part of the optic's definition
* **Selective modifications**: Modify only elements matching criteria
* **Reusable filters**: Define once, compose everywhere
* **Type-safe pipelines**: Filter as part of a larger optic chain
* **Intent clarity**: Express "bulk lines" as a single concept

<!-- verify -->
```java
// Perfect: Declarative, composable, reusable
Traversal<Order, LineItem> bulkBulbLines =
    OrderTraversals.lines()
        .filtered(line -> line.quantity() >= 4)
        .filtered(line -> line.sku().equals("BULB"));

Order updated = Traversals.modify(bulkBulbLines, tenPercentOff, order);
```

### Use Stream API When

* **Complex transformations**: Multiple map/filter/reduce operations
* **Collecting to different structures**: Need to change the collection type
* **Statistical operations**: Sorting, limiting, grouping
* **One-off queries**: Not building reusable logic

<!-- verify -->
```java
// Better with streams: Complex pipeline with sorting and limiting
List<String> topBulkSkus = lines.stream()
    .filter(line -> line.quantity() >= 4)
    .sorted(Comparator.comparing(LineItem::quantity).reversed())
    .limit(10)
    .map(LineItem::sku)
    .collect(toList());
```

### Use Conditional Logic When

* **Control flow**: Early returns, exceptions, complex branching
* **Side effects**: Logging, metrics, external calls based on conditions
* **A hot loop you have measured**: [Production Readiness](production_readiness.md#runtime-cost) says what each call allocates

<!-- verify -->
```java
// Sometimes explicit logic is clearest
for (LineItem line : lines) {
    if (line.price().signum() < 0) {
        throw new IllegalStateException("Line with a negative price: " + line);
    }
}
```

---

## Common Pitfalls

### Don't Do This

<!-- verify -->
```java
// Inefficient: Recreating filtered traversals in loops
for (Order order : orders) {
    var bulkLines = Traversals.<LineItem>forList().filtered(line -> line.quantity() >= 4);
    Traversals.modify(bulkLines, tenPercentOff, order.lines());
}

// Confusing: Mixing filtering approaches
List<LineItem> bulkLines = Traversals.getAll(lineTraversal, lines).stream()
    .filter(line -> line.quantity() >= 4)  // Filtering AFTER optic extraction defeats the purpose
    .collect(toList());

// Wrong mental model: Expecting structure change
Traversal<List<LineItem>, LineItem> bulk = Traversals.<LineItem>forList().filtered(line -> line.quantity() >= 4);
List<LineItem> result = Traversals.modify(bulk, tenPercentOff, lines);
// result still has same LENGTH as lines! Non-matching preserved, not removed

// Over-engineering: Filtering for trivial cases
Fold<Order, OrderStatus> statusFold = OrderLenses.status().asFold();
OrderStatus status = statusFold.getAll(order).get(0); // Just use order.status()!
```

### Do This Instead

<!-- verify -->
```java
// Efficient: Create filtered optic once, reuse many times
Traversal<List<LineItem>, LineItem> bulkLines = Traversals.<LineItem>forList().filtered(line -> line.quantity() >= 4);
for (Order order : orders) {
    Traversals.modify(bulkLines, tenPercentOff, order.lines());
}

// Clear: Filter is part of the optic definition
Traversal<List<LineItem>, LineItem> bulkOnly = Traversals.<LineItem>forList().filtered(line -> line.quantity() >= 4);
List<LineItem> result = Traversals.getAll(bulkOnly, lines);
// Returns only bulk lines

// Correct expectation: Use getAll for extraction, modify for transformation
List<LineItem> onlyBulk = Traversals.getAll(bulkLines, lines);  // Filters results
List<LineItem> allWithBulkDiscounted = Traversals.modify(bulkLines, tenPercentOff, lines);  // Preserves structure

// Simple: Use direct access for trivial cases
OrderStatus status = order.status();
```

---

## Real-World Example: Customer Analytics Dashboard

This dashboard filters three customers' order histories five ways: it finds the big spenders, totals each customer's premium and electronics spending, marks the VIPs, and counts the lines either side of £100:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/filtered/CustomerAnalytics.java:imports}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/filtered/CustomerAnalytics.java:customer_analytics}}
```

**Expected Output:**

```
{{#include ../../../hkj-examples/src/test/resources/golden/optics-filtered-analytics-output.txt.golden}}
```

---

## The Relationship to Haskell's Lens Library

For those familiar with functional programming, Higher-Kinded-J's filtered optics are inspired by Haskell's [lens library](https://hackage.haskell.org/package/lens), specifically the [`filtered`](https://hackage.haskell.org/package/lens-5.2.3/docs/Control-Lens-Traversal.html#v:filtered) combinator.

In Haskell:
```haskell
filtered :: (a -> Bool) -> Traversal' a a
```

This creates a traversal that focuses on the value only if it satisfies the predicate, exactly what our `Traversals.filtered(Predicate)` does.

**Key differences:**
- Higher-Kinded-J uses explicit `Applicative` instances rather than implicit type class resolution
- Java's type system requires more explicit composition steps
- The `filterBy` method is an extension not present in standard lens

---

## The filtering methods at a glance {#summary-the-power-of-filtered-optics}

| Method | Focus |
|--------|-------|
| `filtered(Predicate)` | Elements matching a condition |
| `filterBy(Fold, Predicate)` | Elements where a nested query matches |
| `Traversals.filtered(Predicate)` | A reusable zero-or-one filter to insert anywhere in a chain |

~~~admonish info title="Key Takeaways"
* **The filter is part of the optic's identity**: "bulk lines" becomes a named, reusable path instead of an `if` inside every loop
* **Modification preserves, queries exclude**: `modify` keeps non-matching elements in place; `getAll`, `foldMap`, and `length` skip them
* **`filterBy` reaches into nested data**: filter customers by a query over their orders without leaving the composition
* **Chained filters are AND logic**: stack `filtered` calls, or write one combined predicate for OR
* **Mind the filtered caveat**: keep the predicate on fields the modification does not change, or the operation is not idempotent
~~~

~~~admonish tip title="See Also"
- [Traversals](traversals.md): the optic over every element of a collection, which `filtered` refines
- [Folds](folds.md): the query API and monoid aggregation used with filtered folds
- [Limiting Traversals](limiting_traversals.md): slicing by position rather than by predicate
- [Indexed Optics](indexed_optics.md): when the position should inform the update
- [Production Readiness](production_readiness.md#collection-optics): what a filtered traversal builds, and when to cache a composed optic
~~~

~~~admonish tip title="Further Reading"
- **Haskell**: [Lens Tutorial: Traversal](https://hackage.haskell.org/package/lens-tutorial-1.0.4/docs/Control-Lens-Tutorial.html): original inspiration
- **Chris Penner**: [Optics By Example](https://leanpub.com/optics-by-example): comprehensive book on optics (Haskell)
- **Scala**: [Monocle](https://www.optics.dev/Monocle/): similar library with `filtered` support
~~~

---

**Previous:** [Precision and Filtering](ch3_intro.md)
**Next:** [Indexed Optics](indexed_optics.md)
