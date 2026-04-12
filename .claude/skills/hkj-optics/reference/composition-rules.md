# Optic Composition Rules

## Optic Hierarchy (most specific to most general)

```
Iso
 ├──> Lens ──> Getter
 │       └──────────────────┐
 └──> Prism ──> Affine ──> Fold
          │         └──────────────┐
          └──────────────────> Traversal
                                    └──> Setter
```

**Key rule**: composing two different optic types yields the **least general common ancestor**.

## Composition Table

| First       | Second    | Result        | Reason                          |
|-------------|-----------|---------------|---------------------------------|
| Iso         | Iso       | **Iso**       | Both directions preserved       |
| Iso         | Lens      | **Lens**      | Lens is more restrictive        |
| Iso         | Prism     | **Prism**     | Prism is more restrictive       |
| Iso         | Affine    | **Affine**    | Affine is more restrictive      |
| Iso         | Traversal | **Traversal** | Traversal is most general       |
| Lens        | Lens      | **Lens**      | Same type                       |
| Lens        | Prism     | **Affine**    | May not match (0-1 targets)     |
| Lens        | Affine    | **Affine**    | Affine preserves partiality     |
| Lens        | Traversal | **Traversal** | Traversal is more general       |
| Lens        | Iso       | **Lens**      | Iso subsumes Lens               |
| Prism       | Prism     | **Prism**     | Same type                       |
| Prism       | Lens      | **Affine**    | May not match + field access    |
| Prism       | Affine    | **Affine**    | Affine preserves partiality     |
| Prism       | Traversal | **Traversal** | Traversal is more general       |
| Prism       | Iso       | **Prism**     | Iso subsumes Prism              |
| Affine      | Affine    | **Affine**    | Same type                       |
| Affine      | Lens      | **Affine**    | Affine preserves partiality     |
| Affine      | Prism     | **Affine**    | Both may not match              |
| Affine      | Traversal | **Traversal** | Traversal is more general       |
| Affine      | Iso       | **Affine**    | Iso subsumes Affine             |
| Traversal   | any       | **Traversal** | Traversal is already general    |

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

```java
// Pattern 1: Optional field access (Lens + Prism + Lens = Traversal)
Traversal<User, String> userCity =
    UserLenses.address()           // Lens<User, Optional<Address>>
        .andThen(Prisms.some())    // Prism<Optional<Address>, Address>
        .andThen(AddressLenses.city().asTraversal());

// Pattern 2: Sum type field access (Prism + Lens)
Traversal<Payment, String> creditCardNumber =
    PaymentPrisms.creditCard()
        .andThen(CreditCardLenses.number());

// Pattern 3: Conditional collection access
Traversal<List<Order>, Order> activeOrders =
    Traversals.<Order>forList()
        .andThen(Traversals.filtered(Order::isActive));
```

## Best Practice: Store Complex Compositions as Constants

```java
public final class OrderOptics {
    public static final Traversal<Order, String> CUSTOMER_EMAIL =
        OrderLenses.customer()
            .andThen(CustomerPrisms.activeCustomer())
            .andThen(CustomerLenses.email().asTraversal());

    public static final Traversal<Order, Money> LINE_ITEM_PRICES =
        OrderTraversals.lineItems()
            .andThen(LineItemLenses.price().asTraversal());
}
```
