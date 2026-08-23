# Filtered Optics: Predicate-Based Composition

## _Declarative Filtering for Targeted Operations_

~~~admonish info title="What You'll Learn"
- How to filter elements within traversals and folds using predicates
- Using `filtered()` for declarative, composable filtering as part of optic composition
- The difference between filtering during modification vs filtering during queries
- Advanced filtering with `filterBy()` for query-based predicates
- The static `Traversals.filtered()` combinator for affine traversals
- The preserved-versus-excluded semantics: modification keeps the structure, queries drop non-matches
- When to use filtered optics vs Stream API vs conditional logic
- Real-world patterns for customer segmentation and analytics
~~~

~~~admonish example title="See Example Code"
[FilteredTraversalExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/FilteredTraversalExample.java)
~~~

In our journey through optics, we've seen how **Traversal** handles bulk operations on collections and how **Fold** provides read-only queries. But what happens when you need to operate on only *some* elements, those that satisfy a specific condition?

Traditionally, filtering requires breaking out of your optic composition to use streams or loops, mixing the *what* (your transformation logic) with the *how* (iteration and filtering). **Filtered optics** solve this elegantly by making filtering a first-class part of your optic composition.

---

## The Scenario: Customer Segmentation in a SaaS Platform

Imagine you're building a Software-as-a-Service platform where you need to:
- Grant bonuses only to **active** users
- Send notifications to users with **overdue invoices**
- Analyse spending patterns for customers with **high-value orders**
- Update pricing only for products **in specific categories**

**The Data Model:**

```java
@GenerateLenses
public record User(String name, boolean active, int score, SubscriptionTier tier) {
    User grantBonus() {
        return new User(name, active, score + 100, tier);
    }
}

@GenerateLenses
@GenerateFolds
public record Invoice(String id, double amount, boolean overdue) {}

@GenerateLenses
@GenerateFolds
public record Customer(String name, List<Invoice> invoices, SubscriptionTier tier) {}

@GenerateLenses
@GenerateFolds
@GenerateTraversals
public record Platform(List<User> users, List<Customer> customers) {}

public enum SubscriptionTier { FREE, BASIC, PREMIUM, ENTERPRISE }
```

**The Traditional Approach:**

```java
// Verbose: Manual filtering breaks optic composition
List<User> updatedUsers = platform.users().stream()
    .map(user -> user.active() ? user.grantBonus() : user)
    .collect(Collectors.toList());
Platform updatedPlatform = new Platform(updatedUsers, platform.customers());

// Even worse with nested structures
List<Customer> customersWithOverdue = platform.customers().stream()
    .filter(customer -> customer.invoices().stream()
        .anyMatch(Invoice::overdue))
    .collect(Collectors.toList());
```

This approach forces you to abandon the declarative power of optics, manually managing iteration and reconstruction. **Filtered optics** let you express this intent directly within your optic composition.

---

## Think of Filtered Optics Like...

* **A SQL WHERE clause**: `SELECT * FROM users WHERE active = true`
* **A spotlight with a mask**: Illuminates only the items that match your criteria
* **A sieve**: Allows matching elements to pass through whilst blocking others
* **A conditional lens**: Focuses only on elements satisfying a predicate
* **A smart selector**: Like CSS selectors that target specific elements based on attributes

The key insight: filtering becomes part of your optic's *identity*, not an external operation applied afterwards.

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

```java
// Create a traversal for all users
Traversal<List<User>, User> allUsers = Traversals.forList();

// Filter to active users only
Traversal<List<User>, User> activeUsers = allUsers.filtered(User::active);

// Grant bonus ONLY to active users
List<User> result = Traversals.modify(activeUsers, User::grantBonus, users);
// Active users get bonus; inactive users preserved unchanged

// Extract ONLY active users
List<User> actives = Traversals.getAll(activeUsers, users);
// Returns only those matching the predicate
```

**Critical Semantic**: During **modification**, non-matching elements are *preserved unchanged* in the structure. During **queries** (like `getAll`), they are *excluded* from the results. This preserves the overall structure whilst focusing operations on the subset you care about.

~~~admonish warning title="The filtered caveat"
`filtered` behaves predictably only while the modification cannot change the predicate's own verdict. Filter on `score <= 120` and then apply a bonus that lifts a user past 120, and the second run of the very same update targets fewer elements than the first: the operation is not idempotent. (A verdict can flip the other way too, pulling new elements in.) Keep the predicate on fields the modification does not touch, or accept the non-idempotence deliberately.
~~~

#### On Folds (Read-Only)

```java
// Fold from Customer to invoices (generated by @GenerateFolds)
Fold<Customer, Invoice> invoicesFold = CustomerFolds.invoices();

// Filter to overdue invoices only
Fold<Customer, Invoice> overdueInvoices = invoicesFold.filtered(Invoice::overdue);

// Query operations work on the filtered subset
int count = overdueInvoices.length(customer);             // Count overdue invoices
List<Invoice> overdue = overdueInvoices.getAll(customer); // Get overdue invoices
double owed = overdueInvoices.foldMap(
    Monoids.doubleAddition(), Invoice::amount, customer); // Sum overdue amounts
boolean allLarge = overdueInvoices.all(inv -> inv.amount() > 100, customer);
```

### Step 2: Composing Filtered Traversals

The real power emerges when you compose filtered optics with other optics:

```java
// Compose: list → filtered users → user name
Traversal<List<User>, String> activeUserNames =
    Traversals.<User>forList()
        .filtered(User::active)
        .andThen(UserLenses.name().asTraversal());

List<User> users = List.of(
    new User("alice", true, 100, PREMIUM),
    new User("bob", false, 200, FREE),
    new User("charlie", true, 150, BASIC)
);

// Get only active user names
List<String> names = Traversals.getAll(activeUserNames, users);
// Result: ["alice", "charlie"]

// Uppercase only active user names
List<User> result = Traversals.modify(activeUserNames, String::toUpperCase, users);
// Result: [User("ALICE", true, 100), User("bob", false, 200), User("CHARLIE", true, 150)]
// Notice: bob remains unchanged because he's inactive
```

### Step 3: Chaining Multiple Filters

Filters can be chained to create complex predicates:

```java
// Active users with high scores (AND logic)
Traversal<List<User>, User> activeHighScorers =
    Traversals.<User>forList()
        .filtered(User::active)
        .filtered(user -> user.score() > 120);

// Premium or Enterprise tier users
Traversal<List<User>, User> premiumUsers =
    Traversals.<User>forList()
        .filtered(user -> user.tier() == PREMIUM || user.tier() == ENTERPRISE);
```

### Step 4: The Static Combinator `Traversals.filtered()`

The static method creates an **affine traversal**: it focuses zero or one element, depending on the predicate. It is typed `Traversal` rather than [`Affine`](affine.md) so that it slots directly into traversal chains; "affine" here describes how many elements it focuses, not the Java type:

```java
// Create a reusable filter
Traversal<User, User> activeFilter = Traversals.filtered(User::active);

// Use standalone
User user = new User("Alice", true, 100, BASIC);
User result = Traversals.modify(activeFilter, User::grantBonus, user);
// If active, grants bonus; otherwise returns unchanged

// Compose into a pipeline
Traversal<List<User>, String> activeUserNames =
    Traversals.<User>forList()
        .andThen(Traversals.filtered(User::active))  // Static combinator
        .andThen(UserLenses.name().asTraversal());
```

**When to use the static combinator vs instance method:**

- **Static combinator**: When you want a reusable filter that can be inserted into different compositions
- **Instance method**: When filtering is a natural part of a specific traversal's behaviour

Both approaches are semantically equivalent; choose based on readability and reusability:

```java
// These are equivalent:
Traversal<List<User>, User> approach1 = Traversals.<User>forList().filtered(User::active);
Traversal<List<User>, User> approach2 = Traversals.<User>forList().andThen(Traversals.filtered(User::active));
```

### Step 5: Advanced Filtering with `filterBy(Fold, Predicate)`

Sometimes you need to filter based on *nested* properties or aggregated queries. The `filterBy` method accepts a `Fold` that queries each element, including only those where at least one queried value matches the predicate.

**Example: Customers with Overdue Invoices**

```java
Traversal<List<Customer>, Customer> allCustomers = Traversals.forList();
Fold<Customer, Invoice> customerInvoices = Fold.of(Customer::invoices);

// Filter customers who have ANY overdue invoice
Traversal<List<Customer>, Customer> customersWithOverdue =
    allCustomers.filterBy(customerInvoices, Invoice::overdue);

// Update tier for customers with overdue invoices
Lens<Customer, SubscriptionTier> tierLens = CustomerLenses.tier();
List<Customer> updated = Traversals.modify(
    customersWithOverdue.andThen(tierLens.asTraversal()),
    tier -> SubscriptionTier.BASIC,  // Downgrade tier
    customers
);
```

**Example: Querying Through a Composed Path**

```java
Traversal<List<Customer>, Customer> allCustomers = Traversals.forList();

// Fold from Customer to every invoice amount
Fold<Customer, Double> invoiceAmounts =
    CustomerFolds.invoices().andThen(InvoiceLenses.amount().asFold());

// Customers with any invoice over £1,000
Traversal<List<Customer>, Customer> keyAccounts =
    allCustomers.filterBy(invoiceAmounts, amount -> amount > 1_000);

// Tag them in the name
Traversal<List<Customer>, String> keyAccountNames =
    keyAccounts.andThen(CustomerLenses.name().asTraversal());

List<Customer> result = Traversals.modify(
    keyAccountNames,
    name -> name + " [KEY ACCOUNT]",
    customers
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

```java
List<User> users = List.of(
    new User("Alice", true, 100, PREMIUM),  // active
    new User("Bob", false, 200, FREE),      // inactive
    new User("Charlie", true, 150, BASIC)   // active
);

Traversal<List<User>, User> activeUsers =
    Traversals.<User>forList().filtered(User::active);

// MODIFY: Structure preserved, only matching modified
List<User> modified = Traversals.modify(activeUsers, User::grantBonus, users);
// [User(Alice, true, 200, PREMIUM), User(Bob, false, 200, FREE), User(Charlie, true, 250, BASIC)]
//  ^ modified                        ^ UNCHANGED                  ^ modified

// QUERY: Only matching elements returned
List<User> gotten = Traversals.getAll(activeUsers, users);
// [User(Alice, true, 100, PREMIUM), User(Charlie, true, 150, BASIC)]
// Bob is EXCLUDED entirely
```

This behaviour is intentional: it allows you to **transform selectively** whilst maintaining referential integrity, and **query selectively** without polluting results.

---

## When to Use Filtered Optics vs Other Approaches

### Use Filtered Optics When:

* **Declarative composition** - You want filtering to be part of the optic's definition
* **Selective modifications** - Modify only elements matching criteria
* **Reusable filters** - Define once, compose everywhere
* **Type-safe pipelines** - Filter as part of a larger optic chain
* **Intent clarity** - Express "active users" as a single concept

```java
// Perfect: Declarative, composable, reusable
Traversal<Platform, User> activeEnterpriseUsers =
    PlatformTraversals.users()
        .filtered(User::active)
        .filtered(user -> user.tier() == ENTERPRISE);

Platform updated = Traversals.modify(activeEnterpriseUsers, User::grantBonus, platform);
```

### Use Stream API When:

* **Complex transformations** - Multiple map/filter/reduce operations
* **Collecting to different structures** - Need to change the collection type
* **Statistical operations** - Sorting, limiting, grouping
* **One-off queries** - Not building reusable logic

```java
// Better with streams: Complex pipeline with sorting and limiting
List<String> topActiveUserNames = users.stream()
    .filter(User::active)
    .sorted(Comparator.comparing(User::score).reversed())
    .limit(10)
    .map(User::name)
    .collect(toList());
```

### Use Conditional Logic When:

* **Control flow** - Early returns, exceptions, complex branching
* **Side effects** - Logging, metrics, external calls based on conditions
* **Performance critical** - Minimal abstraction overhead needed

```java
// Sometimes explicit logic is clearest
for (User user : users) {
    if (user.active() && user.score() < 0) {
        throw new IllegalStateException("Active user with negative score: " + user);
    }
}
```

---

## Common Pitfalls

### Don't Do This:

```java
// Inefficient: Recreating filtered traversals in loops
for (Platform platform : platforms) {
    var activeUsers = Traversals.<User>forList().filtered(User::active);
    Traversals.modify(activeUsers, User::grantBonus, platform.users());
}

// Confusing: Mixing filtering approaches
List<User> activeUsers = Traversals.getAll(userTraversal, users).stream()
    .filter(User::active)  // Filtering AFTER optic extraction defeats the purpose
    .collect(toList());

// Wrong mental model: Expecting structure change
Traversal<List<User>, User> active = Traversals.<User>forList().filtered(User::active);
List<User> result = Traversals.modify(active, User::grantBonus, users);
// result still has same LENGTH as users! Non-matching preserved, not removed

// Over-engineering: Filtering for trivial cases
Fold<User, Boolean> isActiveFold = UserLenses.active().asFold();
boolean active = isActiveFold.getAll(user).get(0); // Just use user.active()!
```

### Do This Instead:

```java
// Efficient: Create filtered optic once, reuse many times
Traversal<List<User>, User> activeUsers = Traversals.<User>forList().filtered(User::active);
for (Platform platform : platforms) {
    Traversals.modify(activeUsers, User::grantBonus, platform.users());
}

// Clear: Filter is part of the optic definition
Traversal<List<User>, User> activeUsers = Traversals.<User>forList().filtered(User::active);
List<User> result = Traversals.getAll(activeUsers, users);
// Returns only active users

// Correct expectation: Use getAll for extraction, modify for transformation
List<User> onlyActives = Traversals.getAll(activeUsers, users);  // Filters results
List<User> allWithActivesBonused = Traversals.modify(activeUsers, User::grantBonus, users);  // Preserves structure

// Simple: Use direct access for trivial cases
boolean isActive = user.active();
```

---

## Performance Notes

Filtered optics are optimised for efficiency:

* **No pre-filtering pass**: the predicate is applied during the traversal, not in a separate pass beforehand
* **Element reuse**: elements the predicate rejects are carried over by reference; the list container itself is rebuilt by `ListTraverse.traverse`
* **Single pass**: both filtering and transformation occur in one traversal (every element is visited; the predicate decides whether the function applies)

**Best Practice**: Store frequently-used filtered traversals as constants:

```java
public class PlatformOptics {
    public static final Traversal<Platform, User> ACTIVE_USERS =
        PlatformTraversals.users().filtered(User::active);

    public static final Traversal<Platform, User> PREMIUM_ACTIVE_USERS =
        ACTIVE_USERS.filtered(user -> user.tier() == PREMIUM);

    public static final Traversal<Platform, Customer> CUSTOMERS_WITH_OVERDUE =
        PlatformTraversals.customers()
            .filterBy(CustomerFolds.invoices(), Invoice::overdue);

    public static final Fold<Platform, Invoice> ALL_OVERDUE_INVOICES =
        PlatformFolds.customers()
            .andThen(CustomerFolds.invoices())
            .filtered(Invoice::overdue);
}
```

---

## Real-World Example: Customer Analytics Dashboard

Here's a comprehensive example demonstrating filtered optics in a business context:

```java
package org.higherkindedj.example.optics;

import org.higherkindedj.optics.*;
import org.higherkindedj.optics.util.Traversals;
import org.higherkindedj.hkt.Monoids;
import java.util.*;

public class CustomerAnalytics {

    public record Item(String name, int price, String category, boolean premium) {}
    public record Order(String id, List<Item> items, double total) {}
    public record Customer(String name, List<Order> orders, boolean vip) {}

    // Reusable optics
    private static final Fold<Customer, Order> CUSTOMER_ORDERS = Fold.of(Customer::orders);
    private static final Fold<Order, Item> ORDER_ITEMS = Fold.of(Order::items);
    private static final Fold<Customer, Item> ALL_CUSTOMER_ITEMS =
        CUSTOMER_ORDERS.andThen(ORDER_ITEMS);

    public static void main(String[] args) {
        List<Customer> customers = createSampleData();

        System.out.println("=== CUSTOMER ANALYTICS WITH FILTERED OPTICS ===\n");

        // --- Analysis 1: High-Value Customer Identification ---
        System.out.println("--- Analysis 1: High-Value Customers ---");

        Traversal<List<Customer>, Customer> allCustomers = Traversals.forList();
        Fold<Customer, Double> orderTotals = CUSTOMER_ORDERS.andThen(
            Getter.of(Order::total).asFold()
        );

        // Customers with any order over £500
        Traversal<List<Customer>, Customer> bigSpenders =
            allCustomers.filterBy(orderTotals, total -> total > 500);

        List<Customer> highValue = Traversals.getAll(bigSpenders, customers);
        System.out.println("Customers with orders over £500: " +
            highValue.stream().map(Customer::name).toList());

        // --- Analysis 2: Premium Product Buyers ---
        System.out.println("\n--- Analysis 2: Premium Product Buyers ---");

        Fold<Customer, Item> premiumItems = ALL_CUSTOMER_ITEMS.filtered(Item::premium);

        for (Customer customer : customers) {
            int premiumCount = premiumItems.length(customer);
            if (premiumCount > 0) {
                double premiumSpend = premiumItems.foldMap(Monoids.doubleAddition(),
                    item -> (double) item.price(), customer);
                System.out.printf("%s: %d premium items, £%.2f total%n",
                    customer.name(), premiumCount, premiumSpend);
            }
        }

        // --- Analysis 3: Category-Specific Queries ---
        System.out.println("\n--- Analysis 3: Electronics Spending ---");

        Fold<Customer, Item> electronicsItems =
            ALL_CUSTOMER_ITEMS.filtered(item -> "Electronics".equals(item.category()));

        for (Customer customer : customers) {
            double electronicsSpend = electronicsItems.foldMap(Monoids.doubleAddition(),
                item -> (double) item.price(), customer);
            if (electronicsSpend > 0) {
                System.out.printf("%s spent £%.2f on Electronics%n",
                    customer.name(), electronicsSpend);
            }
        }

        // --- Analysis 4: Mark VIP Customers ---
        System.out.println("\n--- Analysis 4: Auto-Mark VIP Customers ---");

        // Customers who bought premium items AND have any order over £300
        Traversal<List<Customer>, Customer> potentialVIPs =
            allCustomers
                .filterBy(ALL_CUSTOMER_ITEMS, Item::premium)  // Has premium items
                .filterBy(orderTotals, total -> total > 300); // Has high-value orders

        Lens<Customer, Boolean> vipLens =
            Lens.of(Customer::vip, (c, v) -> new Customer(c.name(), c.orders(), v));

        List<Customer> updatedCustomers = Traversals.modify(
            potentialVIPs.andThen(vipLens.asTraversal()),
            _ -> true,
            customers
        );

        for (Customer c : updatedCustomers) {
            if (c.vip()) {
                System.out.println(c.name() + " is now VIP");
            }
        }

        // --- Analysis 5: Aggregated Statistics ---
        System.out.println("\n--- Analysis 5: Platform Statistics ---");

        Fold<List<Customer>, Customer> customerFold = Fold.of(list -> list);
        Fold<List<Customer>, Item> allItems = customerFold.andThen(ALL_CUSTOMER_ITEMS);

        Fold<List<Customer>, Item> expensiveItems = allItems.filtered(i -> i.price() > 100);
        Fold<List<Customer>, Item> cheapItems = allItems.filtered(i -> i.price() <= 100);

        int totalExpensive = expensiveItems.length(customers);
        int totalCheap = cheapItems.length(customers);
        double expensiveRevenue = expensiveItems.foldMap(Monoids.doubleAddition(),
            i -> (double) i.price(), customers);

        System.out.printf("Expensive items (>£100): %d items, £%.2f revenue%n",
            totalExpensive, expensiveRevenue);
        System.out.printf("Budget items (≤£100): %d items%n", totalCheap);

        System.out.println("\n=== END OF ANALYTICS ===");
    }

    private static List<Customer> createSampleData() {
        return List.of(
            new Customer("Alice", List.of(
                new Order("A1", List.of(
                    new Item("Laptop", 999, "Electronics", true),
                    new Item("Mouse", 25, "Electronics", false)
                ), 1024.0),
                new Order("A2", List.of(
                    new Item("Desk", 350, "Furniture", false)
                ), 350.0)
            ), false),
            new Customer("Bob", List.of(
                new Order("B1", List.of(
                    new Item("Book", 20, "Books", false),
                    new Item("Pen", 5, "Stationery", false)
                ), 25.0)
            ), false),
            new Customer("Charlie", List.of(
                new Order("C1", List.of(
                    new Item("Phone", 800, "Electronics", true),
                    new Item("Case", 50, "Accessories", false)
                ), 850.0),
                new Order("C2", List.of(
                    new Item("Headphones", 250, "Electronics", true)
                ), 250.0)
            ), false)
        );
    }
}
```

**Expected Output:**

```
=== CUSTOMER ANALYTICS WITH FILTERED OPTICS ===

--- Analysis 1: High-Value Customers ---
Customers with orders over £500: [Alice, Charlie]

--- Analysis 2: Premium Product Buyers ---
Alice: 1 premium items, £999.00 total
Charlie: 2 premium items, £1050.00 total

--- Analysis 3: Electronics Spending ---
Alice spent £1024.00 on Electronics
Charlie spent £1050.00 on Electronics

--- Analysis 4: Auto-Mark VIP Customers ---
Alice is now VIP
Charlie is now VIP

--- Analysis 5: Platform Statistics ---
Expensive items (>£100): 4 items, £2399.00 revenue
Budget items (≤£100): 4 items

=== END OF ANALYTICS ===
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

## Summary: The Power of Filtered Optics

Filtered optics bring **declarative filtering** into the heart of your optic compositions:

| Method | Focus |
|--------|-------|
| `filtered(Predicate)` | Elements matching a condition |
| `filterBy(Fold, Predicate)` | Elements where a nested query matches |
| `Traversals.filtered(Predicate)` | A reusable zero-or-one filter to insert anywhere in a chain |

~~~admonish info title="Key Takeaways"
* **The filter is part of the optic's identity**: "active users" becomes a named, reusable path instead of an `if` inside every loop
* **Modification preserves, queries exclude**: `modify` keeps non-matching elements in place; `getAll`, `foldMap`, and `length` skip them
* **`filterBy` reaches into nested data**: filter customers by a query over their invoices without leaving the composition
* **Chained filters are AND logic**: stack `filtered` calls, or write one combined predicate for OR
* **Mind the filtered caveat**: keep the predicate on fields the modification does not change, or the operation is not idempotent
~~~

~~~admonish tip title="See Also"
- [Traversals](traversals.md): the unrestricted bulk-update optic that `filtered` refines
- [Folds](folds.md): the query API and monoid aggregation used with filtered folds
- [Limiting Traversals](limiting_traversals.md): slicing by position rather than by predicate
- [Indexed Optics](indexed_optics.md): when the position should inform the update
~~~

~~~admonish tip title="Further Reading"
- **Haskell**: [Lens Tutorial: Traversal](https://hackage.haskell.org/package/lens-tutorial-1.0.4/docs/Control-Lens-Tutorial.html): original inspiration
- **Chris Penner**: [Optics By Example](https://leanpub.com/optics-by-example): comprehensive book on optics (Haskell)
- **Scala**: [Monocle](https://www.optics.dev/Monocle/): similar library with `filtered` support
~~~

---

**Previous:** [Precision and Filtering](ch3_intro.md)
**Next:** [Indexed Optics: Position-Aware Operations](indexed_optics.md)
