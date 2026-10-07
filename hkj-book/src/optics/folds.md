# Folds: A Practical Guide

_Query, search and total nested data through a reusable optic whose type says it never writes._

~~~admonish info title="What You'll Learn"
- Generate folds with `@GenerateFolds`, and compose them with other optics into a read-only query path
- Query with `getAll`, `preview`, `find`, `exists`, `all` and `length`, or get a `Maybe` back with `previewMaybe`
- Aggregate the focused values with `foldMap` and a monoid, such as a sum, a maximum or a string join
- Combine folds over several paths with `plus` and `Fold.sum`, and predict the order of the results
- Decide between a fold, a traversal, the Stream API and direct field access
~~~

~~~admonish example title="See Example Code"
[FoldUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/FoldUsageExample.java)
~~~

In previous guides, we explored optics that allow both reading and writing: **`Lens`** for required fields, **`Prism`** for conditional variants, **`Affine`** for zero-or-one focus, **`Iso`** for lossless conversions, and **`Traversal`** for bulk operations on collections.

But what if you need to perform read-only operations? What if you want to query, search, filter, or aggregate data without any possibility of modification? This is where **`Fold`** shines.

---

## The Scenario: Analysing E-Commerce Purchases {#the-scenario-analysing-e-commerce-orders}

A **`Fold`** is a read-only optic designed specifically for querying and data extraction. It plays the part of a `Stream` over the values it reaches, ending in `reduce`, `anyMatch` or `count`. Unlike a stream, it is a reusable value that composes with other optics, and its type says it never writes. [Choosing an optic](optics_intro.md#choosing-an-optic) sets it beside the other optic types.

Consider an e-commerce system where you need to analyse purchases:

**The Data Model:**

<!-- verify -->
```java
@GenerateLenses
public record Product(String name, BigDecimal price, String category, boolean inStock) {}

@GenerateLenses
@GenerateFolds        // Generate Folds for querying
@GenerateTraversals   // And Traversals, for the read-write comparisons below
public record Purchase(String purchaseId, List<Product> items, String customerName) {}

@GenerateLenses
@GenerateFolds
public record PurchaseHistory(List<Purchase> purchases) {}
```

**Common Query Needs:**
* "Find all products in this purchase"
* "Get the first product or empty if none"
* "Check if any product is out of stock"
* "Count how many items are in the purchase"
* "Calculate the total price of all items"
* "Check if all items are under £100"

A `Fold` makes these queries type-safe, composable, and expressive.

---

## Fold vs Traversal: Understanding the Difference

Before we dive deeper, it's crucial to understand how `Fold` relates to `Traversal`:

| Aspect | Traversal | Fold |
|--------|-----------|------|
| **Purpose** | Read and modify collections | Read-only queries |
| **Can modify?** | Yes (via `Traversals.modify`) | No |
| **Query operations** | Yes (via `getAll`, but not primary purpose) | Yes (designed for this) |
| **Intent clarity** | "I might modify this" | "I'm only reading this" |
| **Conversion** | Can be converted to Fold via `asFold()` | Cannot be converted to Traversal |
| **Use cases** | Bulk updates, validation with modifications | Queries, searches, aggregations |

**Key Insight**: Every `Traversal` can be viewed as a `Fold` (read-only subset), but not every `Fold` can be a `Traversal`. By choosing `Fold` when you only need reading, you make your code's intent clear and prevent accidental modifications.

---

## A Step-by-Step Walkthrough

### Step 1: Generating Folds

Just like with other optics, we use annotations to trigger automatic code generation. Annotating a record with **`@GenerateFolds`** creates a companion class (e.g., `PurchaseFolds`) containing a `Fold` for each field.

<!-- verify -->
```java
import org.higherkindedj.optics.annotations.GenerateFolds;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import java.math.BigDecimal;
import java.util.List;

@GenerateLenses
public record Product(String name, BigDecimal price, String category, boolean inStock) {}

@GenerateLenses
@GenerateFolds
@GenerateTraversals
public record Purchase(String purchaseId, List<Product> items, String customerName) {}
```

This generates:
* `PurchaseFolds.items()` → `Fold<Purchase, Product>` (focuses on all products)
* `PurchaseFolds.purchaseId()` → `Fold<Purchase, String>` (focuses on the purchase ID)
* `PurchaseFolds.customerName()` → `Fold<Purchase, String>` (focuses on customer name)

As with every generator in this chapter, a `targetPackage` attribute relocates the generated class; see [Customising the Generated Package](traversals.md#customising-the-generated-package).

### Step 2: The Core Fold Operations

A `Fold<S, A>` provides these essential query operations:

#### **`getAll(source)`**: Extract All Focused Values

Returns a `List<A>` containing all the values the Fold focuses on.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/folds/FoldsBook.java:purchase}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/folds/FoldsBook.java:get_all}}
```

#### **`preview(source)`**: Get the First Value

Returns an `Optional<A>` containing the first focused value, or `Optional.empty()` if none exist.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/folds/FoldsBook.java:preview}}
```

#### **`find(predicate, source)`**: Find First Matching Value

Returns an `Optional<A>` containing the first value that matches the predicate.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/folds/FoldsBook.java:find}}
```

#### **`exists(predicate, source)`**: Check If Any Match

Returns `true` if at least one focused value matches the predicate.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/folds/FoldsBook.java:exists}}
```

#### **`all(predicate, source)`**: Check If All Match

Returns `true` if all focused values match the predicate (returns `true` for empty collections).

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/folds/FoldsBook.java:all}}
```

#### **`isEmpty(source)`**: Check for Empty

Returns `true` if there are zero focused values.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/folds/FoldsBook.java:is_empty}}
```

#### **`length(source)`**: Count Values

Returns the number of focused values as an `int`.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/folds/FoldsBook.java:length}}
```

Of the operations above, only `preview` and `find` speak `Optional`; the rest hand back a `List`, a `boolean`, or an `int`. If your codebase prefers the library's `Maybe` type, the [Maybe-based extensions](#maybe-based-fold-extensions) later on this page mirror `preview`, `find`, and `getAll`.

### Step 3: Composing Folds for Deep Queries

Folds can be composed with other optics to create deep query paths. When composing with `Lens`, `Prism`, or other `Fold` instances, use `andThen()`.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/folds/FoldsBook.java:compose}}
```

### Step 4: Aggregation with `foldMap` and Monoids

The most powerful feature of `Fold` is its ability to aggregate data using **monoids**. This is where Fold truly shines for combining values in flexible, reusable ways.

~~~admonish tip title="Why this matters"
`foldMap` is one method that replaces a family of hand-written loops: sum, count, any-match, all-match, and string joining are all the same fold with a different `Monoid`. Because monoids compose, so do the queries; and because a `Fold` cannot write, the compiler guarantees your reporting layer never mutates the domain it reports on.
~~~

#### Understanding Monoids: The Simple Explanation

A monoid is the pair you pass to `Stream.reduce(identity, accumulator)`, held as one value. It has two parts:

1. **A starting value** (called `empty`): the `identity`, such as 0 when adding numbers, or "" when joining strings
2. **A combining rule** (called `combine`): the `accumulator`, such as "add these two numbers" or "concatenate these two strings"

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

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/folds/FoldsBook.java:total}}
```

**What's happening here?**
1. For each `Product` in the purchase, extract its `price` → `[999.99, 25.00, 350.00]`
2. Start with `BigDecimal.ZERO` (the empty value)
3. Combine them: `0 + 999.99 + 25.00 + 350.00 = 1374.99`

`Monoids` has ready-made sums for `Integer`, `Long` and `Double`, and none for `BigDecimal`, so a sum of money is a monoid you write once, as here.

#### Common Monoid Patterns

Here are the most useful monoid patterns for everyday use. **Best Practice**: Use the standard implementations from the `Monoids` utility class whenever possible:

<!-- verify -->
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
Monoid<List<String>> listConcat = Monoids.list();
Monoid<Set<String>> setUnion = Monoids.set();
Monoid<Optional<String>> firstWins = Monoids.firstOptional();
Monoid<Optional<String>> lastWins = Monoids.lastOptional();
Monoid<Optional<BigDecimal>> maxValue = Monoids.maximum();
Monoid<Optional<BigDecimal>> minValue = Monoids.minimum();
```

The collection and `Optional` monoids work for any element type; `String` and `BigDecimal` stand in for yours. `maximum()` and `minimum()` need a `Comparable` element, and take a `Comparator` otherwise.

**Sum (Adding Numbers)**
<!-- verify -->
```java
// Monoids has no BigDecimal sum, so money reuses the sumMonoid from Calculate Total Price
BigDecimal revenue = itemsFold.foldMap(sumMonoid, Product::price, purchase);
```

**Product (Multiplying Numbers)**
<!-- verify -->
```java
Monoid<Double> productMonoid = Monoids.doubleMultiplication();

// Calculate compound discount (e.g., 0.9 * 0.95 * 0.85)
double finalMultiplier = discountsFold.foldMap(productMonoid, d -> d, discounts);
```

**String Concatenation**
<!-- verify -->
```java
Monoid<String> stringMonoid = Monoids.string();

// Join all product names
String allNames = itemsFold.foldMap(stringMonoid, Product::name, purchase);
```

**List Accumulation**
<!-- verify -->
```java
Monoid<List<String>> listMonoid = Monoids.list();

// Collect all categories (with duplicates)
List<String> categories = itemsFold.foldMap(listMonoid,
    p -> List.of(p.category()), purchase);
```

**Boolean AND (All Must Be True)**
<!-- verify -->
```java
Monoid<Boolean> andMonoid = Monoids.booleanAnd();

// Check if all products are in stock
boolean allInStock = itemsFold.foldMap(andMonoid, Product::inStock, purchase);
```

**Boolean OR (Any Can Be True)**
<!-- verify -->
```java
Monoid<Boolean> orMonoid = Monoids.booleanOr();

// Check if any product is expensive
boolean hasExpensive = itemsFold.foldMap(orMonoid,
    p -> p.price().compareTo(new BigDecimal("1000")) > 0, purchase);
```

**Maximum Value**
<!-- verify -->
```java
// Use Optional-based maximum from Monoids
Monoid<Optional<BigDecimal>> maxMonoid = Monoids.maximum();

// Find highest price (returns Optional to handle empty collections)
Optional<BigDecimal> maxPrice = itemsFold.foldMap(maxMonoid,
    p -> Optional.of(p.price()), purchase);

// Or, since a price is never negative, a custom one that starts from zero:
Monoid<BigDecimal> rawMaxMonoid = new Monoid<>() {
    @Override public BigDecimal empty() { return BigDecimal.ZERO; }
    @Override public BigDecimal combine(BigDecimal a, BigDecimal b) { return a.max(b); }
};

BigDecimal maxPriceRaw = itemsFold.foldMap(rawMaxMonoid, Product::price, purchase);
```

#### Why Monoids Matter

Monoids give you:
* **Composability**: Combine complex aggregations from simple building blocks
* **Reusability**: Define a monoid once, use it everywhere
* **Correctness**: The monoid laws guarantee consistent behaviour
* **Flexibility**: Create custom aggregations for your domain

**Pro Tip**: You can create custom monoids for any domain-specific aggregation logic, like calculating weighted averages, combining validation results, or merging configuration objects.

---

## Maybe-Based Fold Extensions

~~~admonish note title="Maybe-Based Extensions"
Higher-Kinded-J provides extension methods that integrate `Fold` with [`Maybe`](../monads/maybe_monad.md), its functional optional type, offering a more functional approach to handling absent values compared to Java's `Optional`. These extensions are available via static imports from `FoldExtensions`.
~~~

#### The Challenge: Working with Nullable Values

Standard Fold operations use `Optional<A>` for operations that might not find a value (like `preview` and `find`). While `Optional` works well, functional programming often prefers `Maybe` because it:

* Integrates seamlessly with Higher-Kinded Types (HKT)
* Works consistently with other monadic operations (`flatMap`, `map`, `fold`)
* Provides better composition with validation and error handling types
* Offers a more principled functional API


#### The Three Extension Methods

All three methods are static imports from `org.higherkindedj.optics.extensions.FoldExtensions`:

<!-- verify -->
```java
import static org.higherkindedj.optics.extensions.FoldExtensions.*;
```

##### 1. `previewMaybe(fold, source)` - Get First Value as Maybe

The `previewMaybe` method is the `Maybe`-based equivalent of `preview()`. It returns the first focused value wrapped in `Maybe`, or `Maybe.nothing()` if none exist.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/folds/FoldsBook.java:preview_maybe}}
```

**When to use `previewMaybe` vs `preview`:**

* Use `previewMaybe` when working in a functional pipeline with other `Maybe` values
* Use `preview` when interoperating with standard Java code expecting `Optional`
* Use `previewMaybe` when you need HKT compatibility for generic functional abstractions

##### 2. `findMaybe(fold, predicate, source)` - Find First Match as Maybe

The `findMaybe` method is the `Maybe`-based equivalent of `find()`. It returns the first focused value matching the predicate, or `Maybe.nothing()` if no match is found.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/folds/FoldsBook.java:find_maybe}}
```

**Common Use Cases:**

* **Product search**: Find first available item matching criteria
* **Validation**: Locate the first invalid field in a form
* **Configuration**: Find the first matching configuration option
* **Inventory**: Locate first in-stock item in a category

##### 3. `getAllMaybe(fold, source)` - Get All Values as Maybe-Wrapped List

The `getAllMaybe` method returns all focused values as `Maybe<List<A>>`. If the Fold finds at least one value, you get `Just(List<A>)`. If it finds nothing, you get `Nothing`.

This is particularly useful when you want to distinguish between "found an empty collection" and "found no results".

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/folds/FoldsBook.java:get_all_maybe}}
```

**When to use `getAllMaybe` vs `getAll`:**

| Scenario | Use `getAll()` | Use `getAllMaybe()` |
|----------|----------------|---------------------|
| You need the list regardless of emptiness | Returns `List<A>` (possibly empty) | Overkill |
| You want to treat empty results as a failure case | Must check `isEmpty()` manually | Returns `Nothing` for empty results |
| You're chaining functional operations with Maybe | Requires conversion | Directly composable |
| Performance-critical batch processing | Direct list access | Extra Maybe wrapping |

#### Real-World Scenario: Product Search with Maybe

Here's a practical example showing how Maybe-based extensions simplify null-safe querying:

<!-- verify -->
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
            product -> product.price().compareTo(new BigDecimal("1000")) > 0,
            catalog
        );
    }

    // Check if any products are available
    public boolean hasAvailableProducts(ProductCatalog catalog) {
        return getAllMaybe(ALL_PRODUCTS, catalog)
            .map(products -> products.stream().anyMatch(Product::inStock))
            .orElse(false);
    }

    // Extract all product names (or empty message)
    public String getProductSummary(ProductCatalog catalog) {
        return getAllMaybe(ALL_PRODUCTS, catalog)
            .map(products -> products.stream()
                .map(Product::name)
                .collect(Collectors.joining(", "))
            )
            .orElse("No products available");
    }
}
```

#### Optional vs Maybe: A Comparison

Understanding when to use each type helps you make informed decisions:

| Aspect | `Optional<A>` | `Maybe<A>` |
|--------|---------------|------------|
| **Purpose** | Standard Java optional values | Functional optional values with HKT support |
| **Package** | `java.util.Optional` | `org.higherkindedj.hkt.maybe.Maybe` |
| **HKT Support** | No | Yes (integrates with `Kind<F, A>`) |
| **Monadic Operations** | `map`, `flatMap`, `filter` | `map`, `flatMap`, `orElse`, `orElseGet`, `toEither`, plus the full type class instances |
| **Java Interop** | Native support | Requires conversion |
| **Functional Composition** | Basic | Excellent (works with Applicative, Monad, etc.) |
| **Pattern Matching** | `ifPresent()`, `orElse()` | `isJust()`, `isNothing()` |
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

<!-- verify -->
```java
// Example: Get first expensive product and calculate discount
Maybe<BigDecimal> discountedPrice = previewMaybe(itemsFold, purchase)
    .flatMap(p -> p.price().compareTo(new BigDecimal("100")) > 0
        ? Maybe.just(p.price().multiply(new BigDecimal("0.9")).setScale(2, RoundingMode.HALF_EVEN))
        : Maybe.nothing());
```

**Use `findMaybe` when:**
* You need to locate a specific value matching a predicate
* You want to avoid the verbosity of `getAll().stream().filter().findFirst()`
* You're building search functionality
* You want the first match delivered as a `Maybe` rather than an `Optional`

<!-- verify -->
```java
// Example: Find first out-of-stock item
Maybe<Product> outOfStock = findMaybe(
    itemsFold,
    p -> !p.inStock(),
    purchase
);
```

**Use `getAllMaybe` when:**
* You want to treat empty results as a "nothing" case
* You want to chain functional operations on the entire result set
* You're building batch processing pipelines
* You need to propagate "nothing found" through your computation

<!-- verify -->
```java
// Example: Process all products or provide default behaviour
String report = getAllMaybe(itemsFold, purchase)
    .map(products -> generateReport(products))
    .orElse("No products to report");
```

#### Integration with Existing Fold Operations

Maybe-based extensions work seamlessly alongside standard Fold operations. You can mix and match based on your needs:

<!-- verify -->
```java
Fold<Purchase, Product> itemsFold = PurchaseFolds.items();

// Standard Fold operations
List<Product> allItems = itemsFold.getAll(purchase);           // Always returns list
Optional<Product> firstOpt = itemsFold.preview(purchase);     // Optional-based
int count = itemsFold.length(purchase);                        // Primitive int

// Maybe-based extensions
Maybe<Product> firstMaybe = previewMaybe(itemsFold, purchase);     // Maybe-based
Maybe<Product> matchMaybe =
    findMaybe(itemsFold, p -> p.price().compareTo(new BigDecimal("500")) > 0, purchase);  // Maybe-based
Maybe<List<Product>> allMaybe = getAllMaybe(itemsFold, purchase);      // Maybe-wrapped list
```

**Conversion Between Optional and Maybe:**

<!-- verify -->
```java
// Convert Optional to Maybe
Optional<Product> firstOptional = itemsFold.preview(purchase);
Maybe<Product> liftedToMaybe = Maybe.fromOptional(firstOptional);

// Convert Maybe to Optional
Maybe<Product> firstMaybe = previewMaybe(itemsFold, purchase);
Optional<Product> loweredToOptional = firstMaybe.toOptional();
```

#### Practical Example: Safe Navigation with Maybe

Combining `getAllMaybe` with composed folds creates powerful null-safe query pipelines:

<!-- verify -->
```java
import org.higherkindedj.optics.Fold;
import org.higherkindedj.hkt.maybe.Maybe;
import static org.higherkindedj.optics.extensions.FoldExtensions.*;

@GenerateFolds
public record PurchaseHistory(List<Purchase> purchases) {}

public class PurchaseAnalytics {
    private static final Fold<PurchaseHistory, Purchase> PURCHASES =
        PurchaseHistoryFolds.purchases();
    private static final Fold<Purchase, Product> PRODUCTS =
        PurchaseFolds.items();

    // Calculate total revenue, handling empty history gracefully
    public BigDecimal calculateRevenue(PurchaseHistory history) {
        return getAllMaybe(PURCHASES, history)
            .flatMap(purchases -> {
                List<BigDecimal> prices = purchases.stream()
                    .flatMap(purchase -> getAllMaybe(PRODUCTS, purchase)
                        .map(products -> products.stream().map(Product::price))
                        .orElse(Stream.empty()))
                    .toList();
                return prices.isEmpty() ? Maybe.nothing() : Maybe.just(prices);
            })
            .map(prices -> prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
            .orElse(BigDecimal.ZERO);
    }

    // Find most expensive product across all purchases
    public Maybe<Product> findMostExpensive(PurchaseHistory history) {
        return getAllMaybe(PURCHASES, history)
            .flatMap(purchases -> {
                List<Product> allProducts = purchases.stream()
                    .flatMap(purchase -> getAllMaybe(PRODUCTS, purchase)
                        .map(List::stream)
                        .orElse(Stream.empty()))
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

~~~admonish example title="See Example Code"
See [FoldExtensionsExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/extensions/FoldExtensionsExample.java) for a runnable demonstration of all Maybe-based Fold extensions.
~~~

---

## Combining Folds

### The Problem: Extracting Values from Multiple Paths

Consider a data structure with values scattered across different fields or branches. Without fold combination, you would need to call `getAll` on each fold separately and concatenate the results manually:

<!-- verify -->
```java
record Team(String name, Employee lead, List<Employee> members) {}
record Employee(String name, String email) {}

// Fold.of builds a fold from a function that lists the targets;
// asFold() turns a lens into a single-target fold
Lens<Team, Employee> teamLeadLens =
    Lens.of(Team::lead, (t, l) -> new Team(t.name(), l, t.members()));
Lens<Employee, String> employeeEmailLens =
    Lens.of(Employee::email, (e, m) -> new Employee(e.name(), m));

// Separate folds for different paths
Fold<Team, String> leadEmail = teamLeadLens.asFold()
    .andThen(employeeEmailLens.asFold());
Fold<Team, String> memberEmails = Fold.<Team, Employee>of(Team::members)
    .andThen(employeeEmailLens.asFold());

// Manual combination - verbose and error-prone
List<String> allEmails = new ArrayList<>();
allEmails.addAll(leadEmail.getAll(team));
allEmails.addAll(memberEmails.getAll(team));
```

### The Solution: `Fold.plus()`

`Fold.plus()` combines two folds into one that returns results from both:

<!-- verify -->
```java
// Clean, composable combination
Fold<Team, String> allEmails = leadEmail.plus(memberEmails);

// Use like any other fold
List<String> emails = allEmails.getAll(team);
boolean hasGmail = allEmails.exists(e -> e.endsWith("@gmail.com"), team);
int emailCount = allEmails.length(team);
```

### The Monoid Structure

Folds under `plus` form a monoid, meaning they satisfy three laws:

| Law | Meaning |
|-----|---------|
| **Left identity** | `Fold.empty().plus(f)` behaves like `f` |
| **Right identity** | `f.plus(Fold.empty())` behaves like `f` |
| **Associativity** | `(a.plus(b)).plus(c)` behaves like `a.plus(b.plus(c))` |

`Fold.empty()` is the identity element, a fold that focuses on nothing:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/folds/FoldsBook.java:empty}}
```

For combining three or more folds, use `Fold.sum()`:

<!-- verify -->
```java
Fold<Team, String> allText = Fold.sum(
    teamNameFold,
    leadNameFold,
    memberNamesFold
);
// Equivalent to: teamNameFold.plus(leadNameFold).plus(memberNamesFold)
```

### Combining Different Optic Types

Any optic can participate in fold combination via its `asFold()` method:

<!-- verify -->
```java
// Lens-derived fold (exactly one element)
Fold<Config, String> hostFold = hostLens.asFold();

// Prism-derived fold (zero or one element)
Fold<Config, String> optionalPortFold = portPrism.asFold();

// Affine-derived fold (zero or one element)
Fold<Config, String> optionalDbFold = dbAffine.asFold();

// Combine them all
Fold<Config, String> allConfigStrings = Fold.sum(
    hostFold, optionalPortFold, optionalDbFold
);
```

### Ordering Guarantees

Elements from the first fold always appear before elements from the second:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/folds/FoldsBook.java:ordering}}
```

~~~admonish note title="Each fold makes its own pass"
Each fold in a `plus` combination makes its own pass over the source, which `FoldPlusBenchmark` in `hkj-benchmarks` measures. To combine many folds over a very large collection in one pass, write a single `foldMap` instead.
~~~

---

## When to Use Folds vs Other Approaches

### Use Fold When

* **Read-only queries**: You only need to extract or check data
* **Intent matters**: You want to express "this is a query, not a modification"
* **Composable searches**: Building reusable query paths
* **Aggregations**: Using monoids for custom combining logic
* **CQRS patterns**: Separating queries from commands

<!-- verify -->
```java
// Perfect for read-only analysis
Fold<PurchaseHistory, Product> allProducts =
    PurchaseHistoryFolds.purchases()
        .andThen(PurchaseFolds.items());

boolean hasElectronics = allProducts.exists(
    p -> "Electronics".equals(p.category()),
    history
);
```

### Use Traversal When

* **Modifications needed**: You need to update the data
* **Effectful updates**: Using `modifyF` for validation or async operations
* **Bulk transformations**: Changing multiple values at once

<!-- verify -->
```java
// Use Traversal for modifications
Traversal<Purchase, Product> productTraversal = PurchaseTraversals.items();
Purchase discountedPurchase = Traversals.modify(
    productTraversal.andThen(ProductLenses.price()),
    price -> price.multiply(new BigDecimal("0.9")).setScale(2, RoundingMode.HALF_EVEN),
    purchase
);
```

### Use Stream API When

* **Complex filtering**: Multiple filter/map/reduce operations
* **Parallel processing**: Taking advantage of parallel streams
* **Standard Java collections**: Working with flat collections
* **Stateful operations**: Operations that require maintaining state

<!-- verify -->
```java
// Better with streams for complex pipelines
List<String> topExpensiveItems = purchase.items().stream()
    .filter(p -> p.price().compareTo(new BigDecimal("100")) > 0)
    .sorted(Comparator.comparing(Product::price).reversed())
    .limit(5)
    .map(Product::name)
    .toList();
```

### Use Direct Field Access When

* **Simple cases**: Single, straightforward field read
* **A hot loop you have measured**: [Production Readiness](production_readiness.md#runtime-cost) says what each call allocates
* **One-off operations**: Not building reusable logic

<!-- verify -->
```java
// Just use direct access for simple cases
String customerName = purchase.customerName();
```

---

## Common Pitfalls

### Don't Do This

<!-- verify -->
```java
// Inefficient: Creating folds repeatedly in loops
for (Purchase purchase : purchases) {
    Fold<Purchase, Product> fold = PurchaseFolds.items();
    List<Product> products = fold.getAll(purchase);
    // ... process products
}

// Over-engineering: Using Fold for trivial single-field access
Fold<Purchase, String> customerFold = PurchaseFolds.customerName();
String name = customerFold.getAll(purchase).get(0); // Just use purchase.customerName()!

// Wrong tool: Trying to modify data with a Fold
// Folds are read-only - this won't compile
// Fold<Purchase, Product> items = PurchaseFolds.items();
// Purchase updated = items.set(newProduct, purchase); // ❌ No 'set' method!

// Verbose: Unnecessary conversion when you only need getAll
Traversal<Purchase, Product> traversal = PurchaseTraversals.items();
Fold<Purchase, Product> fold = traversal.asFold();
List<Product> products = fold.getAll(purchase); // Just use Traversals.getAll() directly!
```

### Do This Instead

<!-- verify -->
```java
// Efficient: Create fold once, reuse many times
Fold<Purchase, Product> itemsFold = PurchaseFolds.items();
for (Purchase purchase : purchases) {
    List<Product> products = itemsFold.getAll(purchase);
    // ... process products
}

// Right tool: Direct access for simple cases
String name = purchase.customerName();

// Clear intent: Use Traversal when you need modifications
Traversal<Purchase, Product> itemsTraversal = PurchaseTraversals.items();
Purchase updated = Traversals.modify(
    itemsTraversal,
    p -> new Product(
        p.name(),
        p.price().multiply(new BigDecimal("0.9")).setScale(2, RoundingMode.HALF_EVEN),
        p.category(),
        p.inStock()),
    purchase);

// Clear purpose: Use Fold when expressing query intent
Fold<Purchase, Product> queryItems = PurchaseFolds.items();
boolean hasExpensive =
    queryItems.exists(p -> p.price().compareTo(new BigDecimal("1000")) > 0, purchase);

// Right time for asFold(): when you need foldMap, exists, all, plus, or length
Fold<Purchase, BigDecimal> pricesFold = PurchaseTraversals.items()
    .andThen(ProductLenses.price())
    .asFold();
// sumMonoid is the BigDecimal sum from Step 4's Calculate Total Price
BigDecimal purchaseTotal = pricesFold.foldMap(sumMonoid, p -> p, purchase);
```

---

## Real-World Example: Purchase Analytics {#real-world-example-order-analytics}

Here's a practical example showing comprehensive use of Fold for business analytics:

<!-- verify -->
```java
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFolds;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.hkt.Monoid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@GenerateLenses
@GenerateFolds
public record Product(String name, BigDecimal price, String category, boolean inStock) {}

@GenerateLenses
@GenerateFolds
public record Purchase(String purchaseId, List<Product> items, String customerName) {}

@GenerateLenses
@GenerateFolds
public record PurchaseHistory(List<Purchase> purchases) {}

public class PurchaseAnalytics {
    private static final Fold<Purchase, Product> PURCHASE_ITEMS = PurchaseFolds.items();
    private static final Fold<PurchaseHistory, Purchase> HISTORY_PURCHASES = PurchaseHistoryFolds.purchases();
    private static final Fold<PurchaseHistory, Product> ALL_PRODUCTS =
        HISTORY_PURCHASES.andThen(PURCHASE_ITEMS);

    private static final Monoid<BigDecimal> SUM_MONOID = new Monoid<>() {
        @Override public BigDecimal empty() { return BigDecimal.ZERO; }
        @Override public BigDecimal combine(BigDecimal a, BigDecimal b) { return a.add(b); }
    };

    // Calculate total revenue across all purchases
    public static BigDecimal calculateRevenue(PurchaseHistory history) {
        return ALL_PRODUCTS.foldMap(SUM_MONOID, Product::price, history);
    }

    // Find most expensive product across all purchases
    public static Optional<Product> findMostExpensiveProduct(PurchaseHistory history) {
        return ALL_PRODUCTS.getAll(history).stream()
            .max(Comparator.comparing(Product::price));
    }

    // Check if any purchase has out-of-stock items
    public static boolean hasOutOfStockIssues(PurchaseHistory history) {
        return ALL_PRODUCTS.exists(p -> !p.inStock(), history);
    }

    // Get all unique categories
    public static Set<String> getAllCategories(PurchaseHistory history) {
        Fold<PurchaseHistory, String> categories =
            ALL_PRODUCTS.andThen(ProductLenses.category().asFold());
        return new HashSet<>(categories.getAll(history));
    }

    // Count products in a specific category
    public static int countByCategory(PurchaseHistory history, String category) {
        return (int) ALL_PRODUCTS.getAll(history).stream()
            .filter(p -> category.equals(p.category()))
            .count();
    }

    // Calculate average purchase value, to the penny
    public static BigDecimal calculateAveragePurchaseValue(PurchaseHistory history) {
        List<Purchase> allPurchases = HISTORY_PURCHASES.getAll(history);
        if (allPurchases.isEmpty()) return BigDecimal.ZERO;

        BigDecimal totalRevenue = calculateRevenue(history);
        return totalRevenue.divide(BigDecimal.valueOf(allPurchases.size()), 2, RoundingMode.HALF_EVEN);
    }

    // Find purchases with specific product
    public static List<Purchase> findPurchasesContaining(PurchaseHistory history, String productName) {
        return HISTORY_PURCHASES.getAll(history).stream()
            .filter(purchase -> PURCHASE_ITEMS.exists(
                p -> productName.equals(p.name()),
                purchase
            ))
            .toList();
    }
}
```

---

## The Relationship to Foldable

### Quick Summary

If you're just getting started, here's what you need to know: A `Fold<S, A>` is closely related to the `Foldable` type class from functional programming. While `Foldable<F>` works with any container type `F` (like `List`, `Optional`, `Maybe`), a `Fold<S, A>` lets you treat any structure `S` as if it were a foldable container of `A` values: even when `S` isn't actually a collection.

**Key Connection**: Both use `foldMap` to aggregate values using monoids. The `Fold` optic brings this powerful abstraction to arbitrary data structures, not just collections.

### In-Depth Explanation

For those familiar with functional programming or interested in the deeper theory:

#### The Foldable Type Class

The [`Foldable<F>` type class](../functional/foldable_and_traverse.md) in Higher-Kinded-J represents any data structure `F` that can be "folded up" or reduced to a summary value. It's defined with this signature:

```java
public interface Foldable<F extends WitnessArity<TypeArity.Unary>> {
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
* `Maybe<A>` - fold over the value if it is a `Just`

#### How Fold Relates to Foldable

A `Fold<S, A>` can be thought of as a **first-class, composable lens into a Foldable structure**. More precisely:

1. **Virtualisation**: `Fold<S, A>` lets you "view" any structure `S` as a virtual `Foldable` container of `A` values, even if `S` is not inherently a collection
2. **Composition**: Unlike `Foldable<F>`, which is fixed to a specific container type `F`, `Fold<S, A>` can be composed with other optics to create deep query paths
3. **Reification**: A `Fold` reifies (makes concrete) the act of folding, turning it into a first-class value you can pass around, store, and combine

**Example Comparison**:

<!-- verify -->
```java
// Using Foldable directly on a List
Foldable<ListKind.Witness> listFoldable = ListTraverse.INSTANCE;
List<Integer> numbers = List.of(1, 2, 3, 4, 5);
int sum = listFoldable.foldMap(
    Monoids.integerAddition(), Function.identity(), LIST.widen(numbers));

// Using a Fold optic to query nested structure
Fold<Purchase, BigDecimal> prices = PurchaseFolds.items()
    .andThen(ProductLenses.price().asFold());
// sumMonoid is the BigDecimal sum from Step 4's Calculate Total Price
BigDecimal purchaseTotal = prices.foldMap(sumMonoid, Function.identity(), purchase);
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

The type class itself is covered in depth in [Foldable and Traverse](../functional/foldable_and_traverse.md).

---

## Complete, Runnable Example

This example demonstrates all major Fold operations in a single, cohesive application:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/optics/FoldUsageExample.java:complete_example}}
```

**Expected Output:**

```
{{#include ../../../hkj-examples/src/test/resources/golden/optics-folds-example-output.txt.golden}}
```

---

~~~admonish info title="Key Takeaways"
* **A fold is a query the compiler checks**: read-only by construction, so reporting code cannot accidentally modify the domain it reports on
* **One method per question**: `getAll`, `preview`, `find`, `exists`, `all`, and `length` cover the everyday query surface
* **`foldMap` with a `Monoid` replaces the loop**: sums, counts, joins, and custom aggregations all come from the same machinery
* **`plus()` merges paths**: combine folds over different routes into one multi-path query with predictable ordering
* **Traversal downgrades, Fold never upgrades**: `asFold()` is free; there is no way back to writing
~~~

~~~admonish tip title="See Also"
- [Traversals](traversals.md): the read-write counterpart, and `asFold()` for downgrading a path you already have
- [Getters](getters.md): read-only focus on exactly one value
- [Semigroup and Monoid](../functional/semigroup_and_monoid.md): the combining structures behind `foldMap`
- [Foldable and Traverse](../functional/foldable_and_traverse.md): the type class this optic mirrors
- [Production Readiness](production_readiness.md#read-cost): what each fold read visits, and when to cache a composed optic
~~~

~~~admonish tip title="Further Reading"
- **Haskell lens library**: [Control.Lens.Fold](https://hackage.haskell.org/package/lens-5.2.3/docs/Control-Lens-Fold.html): the original inspiration
- **Chris Penner**: [Optics By Example](https://leanpub.com/optics-by-example): comprehensive treatment of folds (Haskell)
~~~

---

~~~admonish info title="Hands-On Learning"
Practise fold combination in [Tutorial 18: Fold Combination](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial18_FoldCombination.java) (8 exercises).
~~~

---

**Previous:** [Traversals](traversals.md)
**Next:** [Getters](getters.md)
