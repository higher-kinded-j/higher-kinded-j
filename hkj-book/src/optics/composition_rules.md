# Optic Composition Rules

## _Understanding How Different Optics Compose_

~~~admonish info title="What You'll Learn"
- The mathematical rules governing optic composition
- When composition returns the same optic type vs a more general one
- Practical implications for your code
- Quick reference table for all composition patterns
~~~

When composing optics, the resulting optic type follows precise mathematical rules. Understanding these rules helps you predict what type of optic you'll get and why.

---

## The Optic Hierarchy

Optics form a hierarchy from most specific (most operations available) to most general (fewest operations available):

```
Iso ──────────────────────────────────────────┐
 │                                            │
 ├──> Lens ──> Getter                         │
 │       │                                    │
 │       └──────────────────────┐             │
 │                              │             │
 └──> Prism ──> Affine* ────> Fold            │
          │         │                         │
          │         └──────────────┐          │
          │                        v          │
          └──────────────────> Traversal ─────┘
                                    │
                                    └──> Setter

* Affine (0-1 targets) is represented as Traversal in this library
```

**Key insight**: When composing two different optic types, the result is always the **least general common ancestor** that can represent both operations.

---

## Composition Rules Table

| First Optic | >>> | Second Optic | = | Result Optic | Reason |
|-------------|-----|--------------|---|--------------|--------|
| **Iso** | >>> | Iso | = | **Iso** | Both directions preserved |
| **Iso** | >>> | Lens | = | **Lens** | Lens is more restrictive |
| **Iso** | >>> | Prism | = | **Prism** | Prism is more restrictive |
| **Iso** | >>> | Traversal | = | **Traversal** | Traversal is most general |
| **Lens** | >>> | Lens | = | **Lens** | Same type |
| **Lens** | >>> | Prism | = | **Traversal** | May not match (0-1 targets) |
| **Lens** | >>> | Traversal | = | **Traversal** | Traversal is more general |
| **Lens** | >>> | Iso | = | **Lens** | Iso subsumes Lens |
| **Prism** | >>> | Prism | = | **Prism** | Same type |
| **Prism** | >>> | Lens | = | **Traversal** | May not match + field access |
| **Prism** | >>> | Traversal | = | **Traversal** | Traversal is more general |
| **Prism** | >>> | Iso | = | **Prism** | Iso subsumes Prism |
| **Traversal** | >>> | any | = | **Traversal** | Traversal is already general |

---

## Why Lens >>> Prism = Traversal

This is perhaps the most important composition rule to understand.

### The Intuition

A **Lens** guarantees exactly one focus. A **Prism** provides zero-or-one focuses (it may not match).

When you compose them:
- The Lens always gets you to `A`
- The Prism may or may not get you from `A` to `B`

Result: **zero-or-one** focuses, which is a `Traversal` (specifically an "affine" traversal).

### Example

```java
// Domain model
record Config(Optional<DatabaseSettings> database) {}
record DatabaseSettings(String host, int port) {}

// The Lens always gets the Optional<DatabaseSettings>
Lens<Config, Optional<DatabaseSettings>> databaseLens =
    Lens.of(Config::database, (c, db) -> new Config(db));

// The Prism may or may not extract the DatabaseSettings
Prism<Optional<DatabaseSettings>, DatabaseSettings> somePrism = Prisms.some();

// Composition: Lens >>> Prism = Traversal
Traversal<Config, DatabaseSettings> databaseTraversal =
    databaseLens.andThen(somePrism);

// Usage
Config config1 = new Config(Optional.of(new DatabaseSettings("localhost", 5432)));
List<DatabaseSettings> result1 = Traversals.getAll(databaseTraversal, config1);
// result1 = [DatabaseSettings[host=localhost, port=5432]]

Config config2 = new Config(Optional.empty());
List<DatabaseSettings> result2 = Traversals.getAll(databaseTraversal, config2);
// result2 = [] (empty - the prism didn't match)
```

---

## Why Prism >>> Lens = Traversal

Similarly, composing a Prism first and then a Lens also yields a Traversal.

### The Intuition

A **Prism** may or may not match. If it matches, the **Lens** always gets you to the field.

Result: **zero-or-one** focuses, depending on whether the Prism matched.

### Example

```java
// Domain model with sealed interface
sealed interface Shape permits Circle, Rectangle {}
record Circle(double radius, String colour) implements Shape {}
record Rectangle(double width, double height, String colour) implements Shape {}

// The Prism may or may not match Circle
Prism<Shape, Circle> circlePrism = Prism.of(
    shape -> shape instanceof Circle c ? Optional.of(c) : Optional.empty(),
    c -> c
);

// The Lens always gets the radius from a Circle
Lens<Circle, Double> radiusLens =
    Lens.of(Circle::radius, (c, r) -> new Circle(r, c.colour()));

// Composition: Prism >>> Lens = Traversal
Traversal<Shape, Double> circleRadiusTraversal = circlePrism.andThen(radiusLens);

// Usage
Shape circle = new Circle(5.0, "red");
List<Double> radii = Traversals.getAll(circleRadiusTraversal, circle);
// radii = [5.0]

Shape rectangle = new Rectangle(10.0, 20.0, "blue");
List<Double> empty = Traversals.getAll(circleRadiusTraversal, rectangle);
// empty = [] (prism didn't match)

// Modification only affects circles
Shape modified = Traversals.modify(circleRadiusTraversal, r -> r * 2, circle);
// modified = Circle[radius=10.0, colour=red]

Shape unchanged = Traversals.modify(circleRadiusTraversal, r -> r * 2, rectangle);
// unchanged = Rectangle[width=10.0, height=20.0, colour=blue] (unchanged)
```

---

## Available Composition Methods

### Direct Composition (Recommended)

higher-kinded-j provides direct `andThen` methods that automatically return the correct type:

```java
// Lens >>> Lens = Lens
Lens<A, C> result = lensAB.andThen(lensBC);

// Lens >>> Prism = Traversal
Traversal<A, C> result = lensAB.andThen(prismBC);

// Prism >>> Prism = Prism
Prism<A, C> result = prismAB.andThen(prismBC);

// Prism >>> Lens = Traversal
Traversal<A, C> result = prismAB.andThen(lensBC);

// Traversal >>> Traversal = Traversal
Traversal<A, C> result = traversalAB.andThen(traversalBC);
```

### Via asTraversal (Universal Fallback)

When you need to compose optics in a generic way, convert everything to Traversal:

```java
// Any optic composition via Traversal
Traversal<A, D> result =
    optic1.asTraversal()
        .andThen(optic2.asTraversal())
        .andThen(optic3.asTraversal());
```

This approach always works but loses type information (you get a Traversal even when a more specific type would be possible).

---

## Practical Guidelines

### 1. Use Direct Composition When Possible

```java
// Preferred: uses direct andThen for correct return type
Traversal<Config, String> hostTraversal =
    databaseLens.andThen(somePrism).andThen(hostLens.asTraversal());
```

### 2. Chain Multiple Compositions

```java
// Multiple compositions
Traversal<Order, String> customerEmail =
    orderCustomerLens           // Lens<Order, Customer>
        .andThen(customerContactPrism)   // Prism<Customer, ContactInfo>
        .andThen(contactEmailLens.asTraversal());  // Lens<ContactInfo, String>
```

### 3. Store Complex Compositions as Constants

```java
public final class OrderOptics {
    // Reusable compositions
    public static final Traversal<Order, String> CUSTOMER_EMAIL =
        OrderLenses.customer()
            .andThen(CustomerPrisms.activeCustomer())
            .andThen(CustomerLenses.email().asTraversal());

    public static final Traversal<Order, Money> LINE_ITEM_PRICES =
        OrderTraversals.lineItems()
            .andThen(LineItemLenses.price().asTraversal());
}
```

---

## Common Patterns

### Pattern 1: Optional Field Access

Navigate to an optional field that may not exist:

```java
record User(String name, Optional<Address> address) {}
record Address(String street, String city) {}

// Lens to Optional, Prism to extract, Lens to field
Traversal<User, String> userCity =
    UserLenses.address()           // Lens<User, Optional<Address>>
        .andThen(Prisms.some())    // Prism<Optional<Address>, Address>
        .andThen(AddressLenses.city().asTraversal()); // Lens<Address, String>
```

### Pattern 2: Sum Type Field Access

Navigate into a specific case of a sealed interface:

```java
sealed interface Payment permits CreditCard, BankTransfer {}
record CreditCard(String number, String expiry) implements Payment {}
record BankTransfer(String iban, String bic) implements Payment {}

// Prism to case, Lens to field
Traversal<Payment, String> creditCardNumber =
    PaymentPrisms.creditCard()     // Prism<Payment, CreditCard>
        .andThen(CreditCardLenses.number()); // Lens<CreditCard, String>
```

### Pattern 3: Conditional Collection Access

Navigate into items that match a condition:

```java
// Traversal over list, filter by predicate
Traversal<List<Order>, Order> activeOrders =
    Traversals.<Order>forList()
        .andThen(Traversals.filtered(Order::isActive));
```

---

## Summary

| Composition | Result | Use Case |
|-------------|--------|----------|
| Lens >>> Lens | Lens | Nested product types (records) |
| Lens >>> Prism | Traversal | Product containing sum type |
| Prism >>> Lens | Traversal | Sum type containing product |
| Prism >>> Prism | Prism | Nested sum types |
| Any >>> Traversal | Traversal | Collection access |
| Iso >>> Any | Same as second | Type conversion first |

Understanding these rules helps you:
- Predict the type of composed optics
- Choose the right composition approach
- Design your domain model with optics in mind

---

**Previous:** [Traversals](traversals.md)
**Next:** [Optics Cookbook](cookbook.md)
