# Optics Extensions: Validated Operations

## _Error Handling for Lens and Traversal_

~~~admonish info title="What You'll Learn"
- Safe field access with `getMaybe`, `getEither`, and `getValidated`
- Validated modifications with `modifyEither`, `modifyMaybe`, and `modifyValidated`
- Exception-safe operations with `modifyTry`
- Bulk operations with fail-fast (`modifyAllEither`) or error accumulation (`modifyAllValidated`)
- Selective updates with `modifyWherePossible`
- Analysis methods: `countValid` and `collectErrors`
~~~

~~~admonish title="Example Code"
- [LensExtensionsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/LensExtensionsExample.java)
- [TraversalExtensionsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/TraversalExtensionsExample.java)
~~~

Traditional optics work brilliantly with clean, valid data. Real-world applications, however, deal with nullable fields, validation requirements, and operations that might throw exceptions. **Optics Extensions** bridge this gap by integrating lenses and traversals with Higher-Kinded-J's core types.

Think of optics extensions as **safety rails**—they catch null values, validate modifications, and handle exceptions whilst maintaining the elegance of functional composition.

---

## Part 1: Lens Extensions

### Importing Lens Extensions

```java
import static org.higherkindedj.optics.extensions.LensExtensions.*;
```

~~~admonish note title="Alternative: Fluent API"
These extension methods are also available through the [Fluent API](fluent_api.md), which provides method chaining and a more discoverable interface.
~~~

### Safe Access Methods

#### `getMaybe` — Null-Safe Field Access

Returns `Maybe.just(value)` if the field is non-null, `Maybe.nothing()` otherwise.

```java
Lens<UserProfile, String> bioLens = UserProfileLenses.bio();

UserProfile withBio = new UserProfile("u1", "Alice", "alice@example.com", 30, "Software Engineer");
Maybe<String> bio = getMaybe(bioLens, withBio);  // Maybe.just("Software Engineer")

UserProfile withoutBio = new UserProfile("u2", "Bob", "bob@example.com", 25, null);
Maybe<String> noBio = getMaybe(bioLens, withoutBio);  // Maybe.nothing()

// Use with default
String displayBio = bio.orElse("No bio provided");
```

#### `getEither` — Access with Default Error

Returns `Either.right(value)` if non-null, `Either.left(error)` if null.

```java
Lens<UserProfile, Integer> ageLens = UserProfileLenses.age();

Either<String, Integer> age = getEither(ageLens, "Age not provided", profile);
// Either.right(30) or Either.left("Age not provided")

String message = age.fold(
    error -> "Error: " + error,
    a -> "Age: " + a
);
```

#### `getValidated` — Access with Validation Error

Like `getEither`, but returns `Validated` for consistency with validation workflows.

```java
Lens<UserProfile, String> emailLens = UserProfileLenses.email();

Validated<String, String> email = getValidated(emailLens, "Email is required", profile);
// Validated.valid("alice@example.com") or Validated.invalid("Email is required")
```

### Modification Methods

#### `modifyMaybe` — Optional Modifications

Apply a modification that might not succeed. Returns `Maybe.just(updated)` if successful, `Maybe.nothing()` if it fails.

```java
Lens<UserProfile, String> nameLens = UserProfileLenses.name();

Maybe<UserProfile> updated = modifyMaybe(
    nameLens,
    name -> name.length() >= 2 ? Maybe.just(name.toUpperCase()) : Maybe.nothing(),
    profile
);
// Maybe.just(UserProfile with name "ALICE") or Maybe.nothing()
```

#### `modifyEither` — Fail-Fast Validation

Apply a modification with validation. Returns `Either.right(updated)` if valid, `Either.left(error)` if invalid.

```java
Lens<UserProfile, Integer> ageLens = UserProfileLenses.age();

Either<String, UserProfile> updated = modifyEither(
    ageLens,
    age -> {
        if (age < 0) return Either.left("Age cannot be negative");
        if (age > 150) return Either.left("Age must be realistic");
        return Either.right(age + 1);  // Birthday!
    },
    profile
);
```

#### `modifyTry` — Exception-Safe Modifications

Apply a modification that might throw exceptions. Returns `Try.success(updated)` or `Try.failure(exception)`.

```java
Lens<UserProfile, String> emailLens = UserProfileLenses.email();

Try<UserProfile> updated = modifyTry(
    emailLens,
    email -> Try.of(() -> updateEmailInDatabase(email)),
    profile
);

updated.match(
    user -> logger.info("Email updated: {}", user.email()),
    error -> logger.error("Update failed", error)
);
```

#### `setIfValid` — Conditional Updates

Set a new value **only if it passes validation**. Unlike `modifyEither`, you provide the new value directly.

```java
Lens<UserProfile, String> nameLens = UserProfileLenses.name();

Either<String, UserProfile> updated = setIfValid(
    nameLens,
    name -> {
        if (name.length() < 2) return Either.left("Name must be at least 2 characters");
        if (!name.matches("[A-Z][a-z]+")) return Either.left("Name must start with capital letter");
        return Either.right(name);
    },
    "Robert",
    profile
);
```

### Chaining Multiple Lens Updates

```java
Lens<UserProfile, String> nameLens = UserProfileLenses.name();
Lens<UserProfile, String> emailLens = UserProfileLenses.email();

Either<String, UserProfile> result = modifyEither(
    nameLens,
    name -> Either.right(capitalize(name)),
    original
).flatMap(user ->
    modifyEither(
        emailLens,
        email -> Either.right(email.toLowerCase()),
        user
    )
);
```

---

## Part 2: Traversal Extensions

### Importing Traversal Extensions

```java
import static org.higherkindedj.optics.extensions.TraversalExtensions.*;
```

### Extraction Methods

#### `getAllMaybe` — Extract All Values

Returns `Maybe.just(values)` if any elements exist, `Maybe.nothing()` for empty collections.

```java
Lens<OrderItem, BigDecimal> priceLens = OrderItemLenses.price();
Traversal<List<OrderItem>, BigDecimal> allPrices =
    Traversals.<OrderItem>forList().andThen(priceLens.asTraversal());

Maybe<List<BigDecimal>> prices = getAllMaybe(allPrices, items);
// Maybe.just([999.99, 29.99]) or Maybe.nothing()
```

### Bulk Modification Methods

#### `modifyAllMaybe` — All-or-Nothing Modifications

Returns `Maybe.just(updated)` if **all** modifications succeed, `Maybe.nothing()` if **any** fail. Atomic operation.

```java
Maybe<List<OrderItem>> updated = modifyAllMaybe(
    allPrices,
    price -> price.compareTo(new BigDecimal("10")) >= 0
        ? Maybe.just(price.multiply(new BigDecimal("1.1")))  // 10% increase
        : Maybe.nothing(),
    items
);
// Maybe.just([updated items]) or Maybe.nothing() if any price < 10
```

~~~admonish tip title="When to Use modifyAllMaybe"
Use for **atomic updates** where all modifications must succeed or none should apply—for example, applying currency conversion where partial conversion would leave data inconsistent.
~~~

#### `modifyAllEither` — Fail-Fast Validation

Returns `Either.right(updated)` if **all** validations pass, `Either.left(firstError)` if **any** fail. **Stops at first error**.

```java
Either<String, List<OrderItem>> result = modifyAllEither(
    allPrices,
    price -> {
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            return Either.left("Price cannot be negative");
        }
        return Either.right(price);
    },
    items
);
// Stops at first invalid price
```

~~~admonish tip title="When to Use modifyAllEither"
Use for **fail-fast validation** where you want to stop immediately at the first error—for example, API request validation where you reject immediately if any field is invalid.
~~~

#### `modifyAllValidated` — Error Accumulation

Returns `Validated.valid(updated)` if **all** validations pass, `Validated.invalid(allErrors)` if **any** fail. **Collects all errors**.

```java
Validated<List<String>, List<OrderItem>> result = modifyAllValidated(
    allPrices,
    price -> {
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            return Validated.invalid("Price cannot be negative: " + price);
        }
        return Validated.valid(price);
    },
    items
);
// Checks ALL items and collects ALL errors

result.match(
    errors -> {
        System.out.println("Validation failed with " + errors.size() + " errors:");
        errors.forEach(err -> System.out.println("   - " + err));
    },
    updated -> System.out.println("All items valid")
);
```

~~~admonish tip title="When to Use modifyAllValidated"
Use for **error accumulation** where you want to collect all errors—for example, form validation where users need to see all problems at once rather than one at a time.
~~~

#### `modifyWherePossible` — Selective Modification

Modifies elements where the function returns `Maybe.just(value)`, leaves others unchanged. Best-effort operation that always succeeds.

```java
Lens<OrderItem, String> statusLens = OrderItemLenses.status();
Traversal<List<OrderItem>, String> allStatuses =
    Traversals.<OrderItem>forList().andThen(statusLens.asTraversal());

// Update only "pending" items
List<OrderItem> updated = modifyWherePossible(
    allStatuses,
    status -> status.equals("pending")
        ? Maybe.just("processing")
        : Maybe.nothing(),  // Leave non-pending unchanged
    items
);
```

~~~admonish tip title="When to Use modifyWherePossible"
Use for **selective updates** where only some elements should be modified—for example, status transitions that only affect items in a certain state.
~~~

### Analysis Methods

#### `countValid` — Count Passing Validation

Count how many elements pass validation without modifying anything.

```java
int validCount = countValid(
    allPrices,
    price -> price.compareTo(BigDecimal.ZERO) >= 0
        ? Either.right(price)
        : Either.left("Negative price"),
    items
);

System.out.println("Valid items: " + validCount + " out of " + items.size());
```

#### `collectErrors` — Gather Validation Failures

Collect all validation errors without modifying anything. Returns empty list if all valid.

```java
List<String> errors = collectErrors(
    allPrices,
    price -> price.compareTo(BigDecimal.ZERO) >= 0
        ? Either.right(price)
        : Either.left("Negative price: " + price),
    items
);

if (errors.isEmpty()) {
    System.out.println("All prices valid");
} else {
    System.out.println("Found " + errors.size() + " invalid prices:");
    errors.forEach(err -> System.out.println("   - " + err));
}
```

---

## Complete Example: Order Validation Pipeline

```java
public sealed interface ValidationResult permits OrderApproved, OrderRejected {}
record OrderApproved(Order order) implements ValidationResult {}
record OrderRejected(List<String> errors) implements ValidationResult {}

public ValidationResult validateOrder(Order order) {
    Lens<OrderItem, BigDecimal> priceLens = OrderItemLenses.price();
    Lens<OrderItem, Integer> quantityLens = OrderItemLenses.quantity();

    Traversal<List<OrderItem>, BigDecimal> allPrices =
        Traversals.<OrderItem>forList().andThen(priceLens.asTraversal());
    Traversal<List<OrderItem>, Integer> allQuantities =
        Traversals.<OrderItem>forList().andThen(quantityLens.asTraversal());

    // Step 1: Validate all prices (accumulate errors)
    List<String> priceErrors = collectErrors(
        allPrices,
        price -> validatePrice(price),
        order.items()
    );

    // Step 2: Validate all quantities (accumulate errors)
    List<String> quantityErrors = collectErrors(
        allQuantities,
        qty -> validateQuantity(qty),
        order.items()
    );

    // Step 3: Combine all errors
    List<String> allErrors = Stream.of(priceErrors, quantityErrors)
        .flatMap(List::stream)
        .toList();

    if (!allErrors.isEmpty()) {
        return new OrderRejected(allErrors);
    }

    // Step 4: Apply discounts to valid items
    List<OrderItem> discounted = modifyWherePossible(
        allPrices,
        price -> price.compareTo(new BigDecimal("100")) > 0
            ? Maybe.just(price.multiply(new BigDecimal("0.9")))
            : Maybe.nothing(),
        order.items()
    );

    return new OrderApproved(new Order(order.orderId(), discounted, order.customerEmail()));
}

private Either<String, BigDecimal> validatePrice(BigDecimal price) {
    if (price.compareTo(BigDecimal.ZERO) < 0) {
        return Either.left("Price cannot be negative");
    }
    if (price.compareTo(new BigDecimal("10000")) > 0) {
        return Either.left("Price exceeds maximum");
    }
    return Either.right(price);
}

private Either<String, Integer> validateQuantity(Integer qty) {
    if (qty <= 0) {
        return Either.left("Quantity must be positive");
    }
    if (qty > 100) {
        return Either.left("Quantity exceeds maximum");
    }
    return Either.right(qty);
}
```

---

## Best Practices

~~~admonish tip title="Choose the Right Strategy"
**Fail-fast (`modifyAllEither`):**
- API requests (reject immediately)
- Critical validations (stop on first error)
- Performance-sensitive operations

**Error accumulation (`modifyAllValidated`):**
- Form validation (show all errors)
- Batch processing (complete error report)
- Better user experience
~~~

~~~admonish tip title="Keep Validation Functions Pure"
```java
// Good: Pure validator
private Either<String, String> validateEmail(String email) {
    if (!email.contains("@")) {
        return Either.left("Invalid email");
    }
    return Either.right(email.toLowerCase());
}

// Avoid: Impure validator with side effects
private Either<String, String> validateEmail(String email) {
    logger.info("Validating email: {}", email);  // Side effect
    // ...
}
```

Pure functions are easier to test, reason about, and compose.
~~~

~~~admonish warning title="Lens Extensions Don't Handle Null Sources"
Lens extensions handle `null` **field values**, but not `null` **source objects**:

```java
UserProfile profile = null;
Maybe<String> bio = getMaybe(bioLens, profile);  // NullPointerException!

// Wrap the source in Maybe first
Maybe<UserProfile> maybeProfile = Maybe.fromNullable(profile);
Maybe<String> safeBio = maybeProfile.flatMap(p -> getMaybe(bioLens, p));
```
~~~

---

## Summary

| Method | Returns | Use Case |
|--------|---------|----------|
| `getMaybe` | `Maybe<A>` | Null-safe field access |
| `getEither` | `Either<E, A>` | Access with error message |
| `modifyMaybe` | `Maybe<S>` | Optional modification |
| `modifyEither` | `Either<E, S>` | Fail-fast single field validation |
| `modifyTry` | `Try<S>` | Exception-safe modifications |
| `modifyAllMaybe` | `Maybe<S>` | All-or-nothing bulk modification |
| `modifyAllEither` | `Either<E, S>` | Fail-fast bulk validation |
| `modifyAllValidated` | `Validated<List<E>, S>` | Error accumulation |
| `modifyWherePossible` | `S` | Selective modification |
| `countValid` | `int` | Count valid elements |
| `collectErrors` | `List<E>` | Gather all errors |

---

[Previous: Core Type Integration](core_type_integration.md) | [Next: Cookbook](cookbook.md)
