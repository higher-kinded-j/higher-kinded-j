# Optic Composition Rules

## Composition Table

What `first.andThen(second)` returns, with the row as `first`. The book's build reads this table from
the `andThen` overloads, and a test holds this copy to it:

| `first.andThen(second)` | Iso | Lens | Prism | Affine | Traversal |
|---|---|---|---|---|---|
| **Iso** | Iso | Lens | Prism | Affine | Traversal |
| **Lens** | Lens | Lens | Affine | Affine | Traversal |
| **Prism** | Prism | Affine | Prism | Affine | Traversal |
| **Affine** | Affine | Affine | Affine | Affine | Traversal |
| **Traversal** | Traversal | Traversal | Traversal | Traversal | Traversal |

Each optic is fixed by how many values it reaches and whether it can build the whole from its part:

| Reaches | Can build the whole | Cannot |
|---|---|---|
| exactly one | `Iso` | `Lens` |
| zero or one | `Prism` | `Affine` |
| zero or more | | `Traversal` |

A composition reaches the wider of its two steps' reaches, and can build only if both steps can. Every
pair of these five composes directly with `andThen`; no `asTraversal()` is needed first.

## Summary by Use Case

| Composition         | Result    | Use Case                         |
|---------------------|-----------|----------------------------------|
| Lens >>> Lens       | Lens      | Nested product types (records)   |
| Lens >>> Prism      | Affine    | Product containing sum type      |
| Prism >>> Lens      | Affine    | Sum type containing product      |
| Prism >>> Prism     | Prism     | Nested sum types                 |
| Affine >>> Affine   | Affine    | Chained optional access          |
| Affine >>> Lens     | Affine    | Optional then field access       |
| Affine >>> Prism    | Affine    | Optional then variant match      |
| Any >>> Traversal   | Traversal | Collection access                |
| Iso >>> Any         | Same as 2nd | Type conversion first          |

## Affine Explained

An **Affine** optic focuses on **zero or one** element. Created whenever:
- A Lens (always 1) composes with a Prism (0 or 1) = 0 or 1
- A Prism (0 or 1) composes with a Lens (always 1) = 0 or 1

Common for: `Optional<T>` fields, nullable properties, optional intermediate structures.

## Direct Composition (andThen)

```java
// Lens >>> Lens = Lens
Lens<A, C> result = lensAB.andThen(lensBC);

// Lens >>> Prism = Affine
Affine<A, C> result = lensAB.andThen(prismBC);

// Prism >>> Prism = Prism
Prism<A, C> result = prismAB.andThen(prismBC);

// Prism >>> Lens = Affine
Affine<A, C> result = prismAB.andThen(lensBC);

// Affine >>> Affine = Affine
Affine<A, C> result = affineAB.andThen(affineBC);

// Traversal >>> Traversal = Traversal
Traversal<A, C> result = traversalAB.andThen(traversalBC);
```

## Universal Fallback (asTraversal)

<!-- verify -->
```java
// Any optic composition via Traversal (loses type info)
Traversal<A, D> result =
    optic1.asTraversal()
        .andThen(optic2.asTraversal())
        .andThen(optic3.asTraversal());
```

## Parallel Composition (Fold.plus)

| Operation  | Type       | Purpose                                    |
|------------|------------|--------------------------------------------|
| `andThen`  | Sequential | Navigate deeper: `A -> B -> C`             |
| `plus`     | Parallel   | Combine results: `A -> B` and `A -> C`     |

<!-- verify -->
```java
// Sequential: navigate deeper
Fold<Customer, Item> items = ordersFold.andThen(itemsFold);

// Parallel: combine results from different paths
Fold<Person, String> allNames = firstNameFold.plus(lastNameFold);

// Both together
Fold<Team, String> allEmails = Fold.sum(
    leadLens.asFold().andThen(emailLens.asFold()),
    membersFold.andThen(emailLens.asFold())
);
```

`plus` always produces a `Fold` (read-only). Convert via `asFold()` before combining.

## Common Patterns

<!-- verify -->
```java
// Pattern 1: Optional field access (Lens + Prism + Lens = Affine)
Affine<User, String> userCity =
    UserLenses.address()           // Lens<User, Optional<Address>>
        .andThen(Prisms.some())    // Prism<Optional<Address>, Address>
        .andThen(AddressLenses.city());

// Pattern 2: Sum type field access (Prism + Lens = Affine)
Affine<Payment, String> creditCardNumber =
    PaymentPrisms.creditCard()
        .andThen(CreditCardLenses.number());

// Pattern 3: Conditional collection access
Traversal<List<Order>, Order> activeOrders =
    Traversals.<Order>forList()
        .andThen(Traversals.filtered(Order::isActive));
```

## Best Practice: Store Complex Compositions as Constants

<!-- verify -->
```java
public final class OrderOptics {
    public static final Affine<Order, String> CUSTOMER_EMAIL =
        OrderLenses.customer()
            .andThen(CustomerPrisms.activeCustomer())
            .andThen(ActiveCustomerLenses.email());

    public static final Traversal<Order, Money> LINE_ITEM_PRICES =
        OrderTraversals.lineItems()
            .andThen(LineItemLenses.price());
}
```
