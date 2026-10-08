# Filtered Optics: Predicate-Based Composition

_Change only the elements that match a condition, and keep the others in place, unchanged._

~~~admonish info title="What You'll Learn"
- Filter a traversal or a fold with `filtered`, and chain filters for AND logic
- Predict which elements `modify` keeps unchanged and which `getAll` leaves out
- Insert a reusable filter anywhere in a chain with `Traversals.filtered`
- Select elements by a nested query, such as customers with an overdue invoice, with `filterBy`
- Fix a filtered update whose second run targets fewer elements, by filtering on fields it does not change
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

<!-- verify -->
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

<!-- verify -->
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

A filtered traversal plays the part of the conditional `map` in the traditional approach, `user.active() ? user.grantBonus() : user`: matching elements change and the rest pass through untouched. Unlike that stream, it composes into a longer path and puts the list back for you, and unlike a stream's `filter`, it never drops an element.

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

<!-- verify -->
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

<!-- verify -->
```java
// Compose: list → filtered users → user name
Traversal<List<User>, String> activeUserNames =
    Traversals.<User>forList()
        .filtered(User::active)
        .andThen(UserLenses.name());

List<User> users = List.of(
    new User("alice", true, 100, SubscriptionTier.PREMIUM),
    new User("bob", false, 200, SubscriptionTier.FREE),
    new User("charlie", true, 150, SubscriptionTier.BASIC)
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

<!-- verify -->
```java
// Active users with high scores (AND logic)
Traversal<List<User>, User> activeHighScorers =
    Traversals.<User>forList()
        .filtered(User::active)
        .filtered(user -> user.score() > 120);

// Premium or Enterprise tier users
Traversal<List<User>, User> premiumUsers =
    Traversals.<User>forList()
        .filtered(user -> user.tier() == SubscriptionTier.PREMIUM
            || user.tier() == SubscriptionTier.ENTERPRISE);
```

### Step 4: The Static Combinator `Traversals.filtered()`

The static method creates an **affine traversal**: it focuses zero or one element, depending on the predicate. It is typed `Traversal` rather than [`Affine`](affine.md) so that it slots directly into traversal chains; "affine" here describes how many elements it focuses, not the Java type:

<!-- verify -->
```java
// Create a reusable filter
Traversal<User, User> activeFilter = Traversals.filtered(User::active);

// Use standalone
User user = new User("Alice", true, 100, SubscriptionTier.BASIC);
User result = Traversals.modify(activeFilter, User::grantBonus, user);
// If active, grants bonus; otherwise returns unchanged

// Compose into a pipeline
Traversal<List<User>, String> activeUserNames =
    Traversals.<User>forList()
        .andThen(Traversals.filtered(User::active))  // Static combinator
        .andThen(UserLenses.name());
```

**When to use the static combinator vs instance method:**

- **Static combinator**: When you want a reusable filter that can be inserted into different compositions
- **Instance method**: When filtering is a natural part of a specific traversal's behaviour

Both approaches are semantically equivalent; choose based on readability and reusability:

<!-- verify -->
```java
// These are equivalent:
Traversal<List<User>, User> approach1 = Traversals.<User>forList().filtered(User::active);
Traversal<List<User>, User> approach2 = Traversals.<User>forList().andThen(Traversals.filtered(User::active));
```

### Step 5: Advanced Filtering with `filterBy(Fold, Predicate)`

Sometimes you need to filter based on *nested* properties or aggregated queries. The `filterBy` method accepts a `Fold` that queries each element, including only those where at least one queried value matches the predicate.

**Example: Customers with Overdue Invoices**

<!-- verify -->
```java
Traversal<List<Customer>, Customer> allCustomers = Traversals.forList();
Fold<Customer, Invoice> customerInvoices = Fold.of(Customer::invoices);

// Filter customers who have ANY overdue invoice
Traversal<List<Customer>, Customer> customersWithOverdue =
    allCustomers.filterBy(customerInvoices, Invoice::overdue);

// Update tier for customers with overdue invoices
Lens<Customer, SubscriptionTier> tierLens = CustomerLenses.tier();
List<Customer> updated = Traversals.modify(
    customersWithOverdue.andThen(tierLens),
    tier -> SubscriptionTier.BASIC,  // Downgrade tier
    customers
);
```

**Example: Querying Through a Composed Path**

<!-- verify -->
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
    keyAccounts.andThen(CustomerLenses.name());

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

<!-- verify -->
```java
List<User> users = List.of(
    new User("Alice", true, 100, SubscriptionTier.PREMIUM),  // active
    new User("Bob", false, 200, SubscriptionTier.FREE),      // inactive
    new User("Charlie", true, 150, SubscriptionTier.BASIC)   // active
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

### Use Filtered Optics When

* **Declarative composition**: You want filtering to be part of the optic's definition
* **Selective modifications**: Modify only elements matching criteria
* **Reusable filters**: Define once, compose everywhere
* **Type-safe pipelines**: Filter as part of a larger optic chain
* **Intent clarity**: Express "active users" as a single concept

<!-- verify -->
```java
// Perfect: Declarative, composable, reusable
Traversal<Platform, User> activeEnterpriseUsers =
    PlatformTraversals.users()
        .filtered(User::active)
        .filtered(user -> user.tier() == SubscriptionTier.ENTERPRISE);

Platform updated = Traversals.modify(activeEnterpriseUsers, User::grantBonus, platform);
```

### Use Stream API When

* **Complex transformations**: Multiple map/filter/reduce operations
* **Collecting to different structures**: Need to change the collection type
* **Statistical operations**: Sorting, limiting, grouping
* **One-off queries**: Not building reusable logic

<!-- verify -->
```java
// Better with streams: Complex pipeline with sorting and limiting
List<String> topActiveUserNames = users.stream()
    .filter(User::active)
    .sorted(Comparator.comparing(User::score).reversed())
    .limit(10)
    .map(User::name)
    .collect(toList());
```

### Use Conditional Logic When

* **Control flow**: Early returns, exceptions, complex branching
* **Side effects**: Logging, metrics, external calls based on conditions
* **A hot loop you have measured**: [Production Readiness](production_readiness.md#runtime-cost) says what each call allocates

<!-- verify -->
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

### Don't Do This

<!-- verify -->
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
boolean isActive = isActiveFold.getAll(user).get(0); // Just use user.active()!
```

### Do This Instead

<!-- verify -->
```java
// Efficient: Create filtered optic once, reuse many times
Traversal<List<User>, User> activeUsers = Traversals.<User>forList().filtered(User::active);
for (Platform platform : platforms) {
    Traversals.modify(activeUsers, User::grantBonus, platform.users());
}

// Clear: Filter is part of the optic definition
Traversal<List<User>, User> activeOnly = Traversals.<User>forList().filtered(User::active);
List<User> result = Traversals.getAll(activeOnly, users);
// Returns only active users

// Correct expectation: Use getAll for extraction, modify for transformation
List<User> onlyActives = Traversals.getAll(activeUsers, users);  // Filters results
List<User> allWithActivesBonused = Traversals.modify(activeUsers, User::grantBonus, users);  // Preserves structure

// Simple: Use direct access for trivial cases
boolean isActive = user.active();
```

---

## Real-World Example: Customer Analytics Dashboard

Here's a comprehensive example demonstrating filtered optics in a business context:

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
