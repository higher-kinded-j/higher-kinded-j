# Optics Extensions: Validated Operations

_Get a `null` field, or an update that can fail, back as a `Maybe`, `Either`, `Validated` or `Try` value._

~~~admonish info title="What You'll Learn"
- Read a field that may be `null` as a `Maybe`, `Either` or `Validated` with `getMaybe`, `getEither` and `getValidated`
- Modify one field with a step that can fail, using `modifyEither`, `modifyMaybe`, `modifyTry` or `setIfValid`
- Choose an all-or-nothing, first-error or accumulating bulk update: `modifyAllMaybe`, `modifyAllEither` or `modifyAllValidated`
- Update only the elements that qualify with `modifyWherePossible`, leaving the rest unchanged
- Check a traversal without writing to it, using `countValid` and `collectErrors`
~~~

~~~admonish example title="See Example Code"
- [LensExtensionsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/LensExtensionsExample.java)
- [TraversalExtensionsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/TraversalExtensionsExample.java)
~~~

Traditional optics work brilliantly with clean, valid data. Real-world applications, however, deal with nullable fields, validation requirements, and operations that might throw exceptions. **Optics Extensions** bridge this gap by integrating lenses and traversals with Higher-Kinded-J's core types.

Each extension plays the part of a null check, or an `if` on a validation result, written once around a lens or traversal call. A `null` or a failed step comes back as a `Maybe`, `Either`, `Validated` or `Try` value. The extensions catch nothing themselves: for `modifyTry`, wrap the throwing call in `Try.of`, and its exception arrives as a `Try` failure.

---

## Part 1: Lens Extensions

### Importing Lens Extensions

<!-- verify -->
```java
import static org.higherkindedj.optics.extensions.LensExtensions.*;
```

~~~admonish note title="Alternative: Fluent API"
These extension methods are also available through the [Fluent API](fluent_api.md#the-two-styles), which provides method chaining and a more discoverable interface.
~~~

### Safe Access Methods

Where an example in this part reads `profile`, it is Alice's `UserProfile`, holding the id `u1`, the name `Alice`, the email `alice@example.com`, the age `30` and the bio `Software Engineer`.

#### `getMaybe`: Null-Safe Field Access

Returns `Maybe.just(value)` if the field is non-null, `Maybe.nothing()` otherwise.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/extensions/ExtensionsBook.java:get_maybe}}
```

#### `getEither`: Access with Default Error

Returns `Either.right(value)` if non-null, `Either.left(error)` if null.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/extensions/ExtensionsBook.java:get_either}}
```

#### `getValidated`: Access with Validation Error

Like `getEither`, but returns `Validated` for consistency with validation workflows.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/extensions/ExtensionsBook.java:get_validated}}
```

### Modification Methods

#### `modifyMaybe`: Optional Modifications

Apply a modification that might not succeed. Returns `Maybe.just(updated)` if successful, `Maybe.nothing()` if it fails.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/extensions/ExtensionsBook.java:modify_maybe}}
```

#### `modifyEither`: Fail-Fast Validation

Apply a modification with validation. Returns `Either.right(updated)` if valid, `Either.left(error)` if invalid.

<!-- verify -->
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

#### `modifyTry`: Exception-Safe Modifications

Apply a modification that might throw exceptions. Returns `Try.success(updated)` or `Try.failure(exception)`.

<!-- verify -->
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

#### `setIfValid`: Conditional Updates

Set a new value **only if it passes validation**. Unlike `modifyEither`, you provide the new value directly.

<!-- verify -->
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

<!-- verify -->
```java
Lens<UserProfile, String> nameLens = UserProfileLenses.name();
Lens<UserProfile, String> emailLens = UserProfileLenses.email();

Either<String, UserProfile> capitalised = modifyEither(
    nameLens,
    name -> Either.right(capitalize(name)),
    original
);

Either<String, UserProfile> result = capitalised.flatMap(user ->
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

<!-- verify -->
```java
import static org.higherkindedj.optics.extensions.TraversalExtensions.*;
```

### Extraction Methods

Where an example in this part reads `items`, it is a list of two `OrderItem` lines: `SKU-1`, one at 999.99, pending, and `SKU-2`, two at 29.99, shipped.

#### `getAllMaybe`: Extract All Values

Returns `Maybe.just(values)` if any elements exist, `Maybe.nothing()` for empty collections.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/extensions/ExtensionsBook.java:get_all_maybe}}
```

### Bulk Modification Methods

#### `modifyAllMaybe`: All-or-Nothing Modifications

Returns `Maybe.just(updated)` if **all** modifications succeed, `Maybe.nothing()` if **any** fail. Atomic operation.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/extensions/ExtensionsBook.java:modify_all_maybe}}
```

~~~admonish tip title="When to Use modifyAllMaybe"
Use for **atomic updates** where all modifications must succeed or none should apply, for example, applying currency conversion where partial conversion would leave data inconsistent.
~~~

#### `modifyAllEither`: First Error Only

Returns `Either.right(updated)` if **all** validations pass, `Either.left(firstError)` if **any** fail. The result keeps only the first error; the traversal still visits every element.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/extensions/ExtensionsBook.java:modify_all_either}}
```

~~~admonish tip title="When to Use modifyAllEither"
Use when **one error is all the caller will act on**, for example an API request you reject as soon as it is known to be invalid. Note this shapes the answer, not the work: every element is still validated before the `Either` collapses.
~~~

#### `modifyAllValidated`: Error Accumulation

Returns `Validated.valid(updated)` if **all** validations pass, `Validated.invalid(allErrors)` if **any** fail. **Collects all errors**.

<!-- verify -->
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

// Validated exposes fold, not match
if (result.isInvalid()) {
    List<String> errors = result.getError();
    System.out.println("Validation failed with " + errors.size() + " errors:");
    errors.forEach(err -> System.out.println("   - " + err));
} else {
    System.out.println("All items valid");
}
```

~~~admonish tip title="When to Use modifyAllValidated"
Use for **error accumulation** where you want to collect all errors, for example, form validation where users need to see all problems at once rather than one at a time.
~~~

#### `modifyWherePossible`: Selective Modification

Modifies elements where the function returns `Maybe.just(value)`, leaves others unchanged. Best-effort operation that always succeeds.

<!-- verify -->
```java
Lens<OrderItem, String> statusLens = OrderItemLenses.status();
Traversal<List<OrderItem>, String> allStatuses =
    Traversals.<OrderItem>forList().andThen(statusLens);

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
Use for **selective updates** where only some elements should be modified, for example, status transitions that only affect items in a certain state.
~~~

### Analysis Methods

#### `countValid`: Count Passing Validation

Count how many elements pass validation without modifying anything.

<!-- verify -->
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

#### `collectErrors`: Gather Validation Failures

Collect all validation errors without modifying anything. Returns empty list if all valid.

<!-- verify -->
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

<!-- verify -->
```java
public sealed interface ValidationResult permits OrderApproved, OrderRejected {}
record OrderApproved(Order order) implements ValidationResult {}
record OrderRejected(List<String> errors) implements ValidationResult {}

public ValidationResult validateOrder(Order order) {
    Lens<OrderItem, BigDecimal> priceLens = OrderItemLenses.price();
    Lens<OrderItem, Integer> quantityLens = OrderItemLenses.quantity();

    Traversal<List<OrderItem>, BigDecimal> allPrices =
        Traversals.<OrderItem>forList().andThen(priceLens);
    Traversal<List<OrderItem>, Integer> allQuantities =
        Traversals.<OrderItem>forList().andThen(quantityLens);

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
            ? Maybe.just(price.multiply(new BigDecimal("0.9")).setScale(2, RoundingMode.HALF_EVEN))
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
**First error only (`modifyAllEither`):**
- API requests rejected with one reason
- Internal callers that act on the first problem
- Every element is still validated, so this does not save the work

**Error accumulation (`modifyAllValidated`):**
- Form validation (show all errors)
- Batch processing (complete error report)
- Better user experience
~~~

~~~admonish tip title="Keep Validation Functions Pure"
<!-- verify -->
```java
// Good: Pure validator
private Either<String, String> validateEmail(String email) {
    if (!email.contains("@")) {
        return Either.left("Invalid email");
    }
    return Either.right(email.toLowerCase());
}

// Avoid: Impure validator with side effects
private Either<String, String> validateEmailAndLog(String email) {
    logger.info("Validating email: {}", email);  // Side effect
    if (!email.contains("@")) {
        return Either.left("Invalid email");
    }
    return Either.right(email.toLowerCase());
}
```

Pure functions are easier to test, reason about, and compose.
~~~

~~~admonish warning title="Lens Extensions Don't Handle Null Sources"
Lens extensions handle `null` **field values**, but not `null` **source objects**:

<!-- verify -->
```java
UserProfile profile = null;
Maybe<String> bio = getMaybe(bioLens, profile);  // NullPointerException!

// Wrap the source in Maybe first
Maybe<UserProfile> maybeProfile = Maybe.fromNullable(profile);
Maybe<String> safeBio = maybeProfile.flatMap(p -> getMaybe(bioLens, p));
```
~~~

---

## Every extension at a glance {#summary}

| Method | Returns | Use Case |
|--------|---------|----------|
| `getMaybe` | `Maybe<A>` | Null-safe field access |
| `getEither` | `Either<E, A>` | Access with error message |
| `modifyMaybe` | `Maybe<S>` | Optional modification |
| `modifyEither` | `Either<E, S>` | Fail-fast single field validation |
| `modifyTry` | `Try<S>` | Exception-safe modifications |
| `modifyAllMaybe` | `Maybe<S>` | All-or-nothing bulk modification |
| `modifyAllEither` | `Either<E, S>` | Bulk validation, first error only |
| `modifyAllValidated` | `Validated<List<E>, S>` | Error accumulation |
| `modifyWherePossible` | `S` | Selective modification |
| `countValid` | `int` | Count valid elements |
| `collectErrors` | `List<E>` | Gather all errors |
| `getValidated` | `Validated<E, A>` | Access, accumulating the failure |
| `modifyValidated` | `Validated<E, S>` | Single-field modification, accumulating |
| `setIfValid` | `Either<String, S>` | Write only when the new value passes |
| `getAllMaybe` | `Maybe<List<A>>` | Extract all, or nothing when empty |

~~~admonish info title="Key Takeaways"
* **The extensions are the error-handling half of an optic.** A `Lens` gets you to a field; `getEither`, `modifyEither` and `modifyTry` decide what happens when getting there, or changing it, can fail.
* **The suffix names the failure shape, not the operation.** `Maybe` for "no detail", `Either` for "first error", `Try` for "it threw", `Validated` for "every error at once". Pick the suffix from what the caller needs to hear.
* **`modifyAll*` is the bulk family.** All-or-nothing, first-error and accumulating are three different answers to one traversal, and the return type states which you chose. All three visit every element: the suffix shapes the answer, not the work.
* **`modifyWherePossible` is deliberately total.** It never fails; elements that cannot be modified are left as they are, which makes it the right tool for best-effort passes and the wrong one for validation.
* **`countValid` and `collectErrors` inspect without writing.** They answer "would this succeed, and why not" before you commit to a modification.
~~~

~~~admonish tip title="See Also"
- [Core Type Integration](core_type_integration.md): the prisms and traversals for the `Maybe`/`Either`/`Validated`/`Try` these methods return
- [Updates That Can Fail](fluent_api.md): the same validation strategies as `OpticOps` statics and builders
- [Deep Validation with `modifyF`](composing_optics.md): the longer example these operations shorten
~~~

---

**Previous:** [Core Type Integration](core_type_integration.md)
**Next:** [Optics for External Types](importing_optics.md)
