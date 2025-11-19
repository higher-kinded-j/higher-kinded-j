# Traversal Extensions: Bulk Operations with Error Handling

![traversals.jpg](../images/optics.jpg)

Traversals are optics that focus on **zero or more elements** in a structure—perfect for working with collections. But what happens when you need to validate all items in a list, accumulate errors, or selectively update elements?

Traditional traversal operations work well with clean, valid data. Real-world applications, however, require **bulk validation**, **error accumulation**, and **partial updates**. **Traversal Extensions** provide these capabilities whilst maintaining the elegance of functional composition.

Think of traversal extensions as **quality control for production lines**—they can inspect all items, reject the batch at the first defect (fail-fast), collect all defects for review (error accumulation), or fix what's fixable and flag the rest (selective modification).

## The Problem: Bulk Operations Without Error Handling

Let's see the traditional approach to processing collections with validation:

~~~admonish failure title="❌ Traditional Collection Processing"
```java
public List<OrderItem> validateAndUpdatePrices(List<OrderItem> items) {
    List<OrderItem> result = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    for (OrderItem item : items) {
        BigDecimal price = item.price();

        // Validation
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Invalid price for " + item.sku());
            // Now what? Skip this item? Throw exception? Continue?
        } else if (price.compareTo(new BigDecimal("10000")) > 0) {
            errors.add("Price too high for " + item.sku());
        } else {
            // Apply discount
            BigDecimal discounted = price.multiply(new BigDecimal("0.9"));
            result.add(new OrderItem(
                item.sku(),
                item.name(),
                discounted,
                item.quantity(),
                item.status()
            ));
        }
    }

    if (!errors.isEmpty()) {
        // What do we do with the errors?
        // Throw exception and lose all progress?
        // Log them and continue with partial results?
    }

    return result;
}
```

Problems:
- Validation and transformation logic intertwined
- Error handling strategy unclear
- Manual loop with mutable state
- Unclear what happens on partial failure
- Imperative, hard to test
~~~

~~~admonish success title="✅ With Traversal Extensions"
```java
public Validated<List<String>, List<OrderItem>> validateAndUpdatePrices(
    List<OrderItem> items
) {
    Lens<OrderItem, BigDecimal> priceLens = OrderItemLenses.price();
    Traversal<List<OrderItem>, BigDecimal> allPrices =
        Traversals.<OrderItem>forList().andThen(priceLens.asTraversal());

    return modifyAllValidated(
        allPrices,
        price -> validateAndDiscount(price),
        items
    );
}

private Validated<String, BigDecimal> validateAndDiscount(BigDecimal price) {
    if (price.compareTo(BigDecimal.ZERO) < 0) {
        return Validated.invalid("Price cannot be negative");
    }
    if (price.compareTo(new BigDecimal("10000")) > 0) {
        return Validated.invalid("Price exceeds maximum");
    }
    return Validated.valid(price.multiply(new BigDecimal("0.9")));
}
```

Clean separation: the traversal defines **where** (all prices), the validator defines **what** (validation rules), and `Validated` accumulates **all** errors or returns **all** results.
~~~

## Available Traversal Extensions

Higher-Kinded-J provides traversal extensions in the `TraversalExtensions` utility class. All methods are static, designed for import with `import static`:

```java
import static org.higherkindedj.optics.extensions.TraversalExtensions.*;
```

~~~admonish note title="💡 Alternative: Fluent API"
These extension methods are also available through the [Fluent API](fluent_api.md), providing method chaining and better discoverability. For example, `modifyAllEither(traversal, f, source)` can also be written using `OpticOps` for a more fluent syntax.
~~~

### Extraction Methods

#### `getAllMaybe` — Extract All Values

```java
public static <S, A> Maybe<List<A>> getAllMaybe(Traversal<S, A> traversal, S source)
```

Extract all focused values into a list. Returns `Maybe.just(values)` if any elements exist, `Maybe.nothing()` for empty collections.

```java
List<OrderItem> items = List.of(
    new OrderItem("SKU001", "Laptop", new BigDecimal("999.99"), 1, "pending"),
    new OrderItem("SKU002", "Mouse", new BigDecimal("29.99"), 2, "pending")
);

Lens<OrderItem, BigDecimal> priceLens = OrderItemLenses.price();
Traversal<List<OrderItem>, BigDecimal> allPrices =
    Traversals.<OrderItem>forList().andThen(priceLens.asTraversal());

Maybe<List<BigDecimal>> prices = getAllMaybe(allPrices, items);
// Maybe.just([999.99, 29.99])

List<OrderItem> empty = List.of();
Maybe<List<BigDecimal>> noPrices = getAllMaybe(allPrices, empty);
// Maybe.nothing()

// Calculate total
BigDecimal total = prices
    .map(list -> list.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
    .orElse(BigDecimal.ZERO);
```

### Bulk Modification Methods

#### `modifyAllMaybe` — All-or-Nothing Modifications

```java
public static <S, A> Maybe<S> modifyAllMaybe(
    Traversal<S, A> traversal,
    Function<A, Maybe<A>> modifier,
    S source
)
```

Apply a modification to **all** elements. Returns `Maybe.just(updated)` if **all** modifications succeed, `Maybe.nothing()` if **any** fail. This is an **atomic** operation—either everything updates or nothing does.

```java
List<OrderItem> items = List.of(
    new OrderItem("SKU001", "Laptop", new BigDecimal("100.00"), 1, "pending"),
    new OrderItem("SKU002", "Mouse", new BigDecimal("20.00"), 2, "pending"),
    new OrderItem("SKU003", "Keyboard", new BigDecimal("50.00"), 1, "pending")
);

Lens<OrderItem, BigDecimal> priceLens = OrderItemLenses.price();
Traversal<List<OrderItem>, BigDecimal> allPrices =
    Traversals.<OrderItem>forList().andThen(priceLens.asTraversal());

// Successful: all prices ≥ £10
Maybe<List<OrderItem>> updated = modifyAllMaybe(
    allPrices,
    price -> price.compareTo(new BigDecimal("10")) >= 0
        ? Maybe.just(price.multiply(new BigDecimal("1.1")))  // 10% increase
        : Maybe.nothing(),
    items
);
// Maybe.just([updated items with 10% price increase])

// Failed: one price < £10
List<OrderItem> withLowPrice = List.of(
    new OrderItem("SKU001", "Laptop", new BigDecimal("100.00"), 1, "pending"),
    new OrderItem("SKU002", "Cheap Item", new BigDecimal("5.00"), 2, "pending")
);

Maybe<List<OrderItem>> failed = modifyAllMaybe(
    allPrices,
    price -> price.compareTo(new BigDecimal("10")) >= 0
        ? Maybe.just(price.multiply(new BigDecimal("1.1")))
        : Maybe.nothing(),
    withLowPrice
);
// Maybe.nothing() - entire update rolled back
```

~~~admonish tip title="💡 When to Use modifyAllMaybe"
Use `modifyAllMaybe` for **atomic updates** where:
- All modifications must succeed or none should apply
- Partial updates would leave data in an inconsistent state
- You want "all-or-nothing" semantics

**Example:** Applying currency conversion to all prices—if the exchange rate service fails for one item, you don't want some prices converted and others not.
~~~

#### `modifyAllEither` — Fail-Fast Validation

```java
public static <S, A, E> Either<E, S> modifyAllEither(
    Traversal<S, A> traversal,
    Function<A, Either<E, A>> modifier,
    S source
)
```

Apply a modification with validation. Returns `Either.right(updated)` if **all** validations pass, `Either.left(firstError)` if **any** fail. **Stops at the first error** (fail-fast).

```java
List<OrderItem> items = List.of(
    new OrderItem("SKU001", "Laptop", new BigDecimal("999.99"), 1, "pending"),
    new OrderItem("SKU002", "Mouse", new BigDecimal("-10.00"), 2, "pending"),  // Invalid!
    new OrderItem("SKU003", "Keyboard", new BigDecimal("79.99"), 1, "pending")
);

Lens<OrderItem, BigDecimal> priceLens = OrderItemLenses.price();
Traversal<List<OrderItem>, BigDecimal> allPrices =
    Traversals.<OrderItem>forList().andThen(priceLens.asTraversal());

// Fail-fast: stops at first invalid price
Either<String, List<OrderItem>> result = modifyAllEither(
    allPrices,
    price -> {
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            return Either.left("Price cannot be negative");
        }
        if (price.compareTo(new BigDecimal("10000")) > 0) {
            return Either.left("Price exceeds maximum");
        }
        return Either.right(price);
    },
    items
);
// Either.left("Price cannot be negative")
// Stopped at SKU002, didn't check SKU003

result.match(
    error -> System.out.println("❌ Validation failed: " + error),
    updated -> System.out.println("✅ All items valid")
);
```

~~~admonish tip title="💡 When to Use modifyAllEither"
Use `modifyAllEither` for **fail-fast validation** where:
- You want to stop immediately at the first error
- Subsequent validations depend on earlier ones passing
- You want efficient rejection of invalid data
- The first error is sufficient feedback

**Example:** API request validation—reject the request immediately if any field is invalid.
~~~

#### `modifyAllValidated` — Error Accumulation

```java
public static <S, A, E> Validated<List<E>, S> modifyAllValidated(
    Traversal<S, A> traversal,
    Function<A, Validated<E, A>> modifier,
    S source
)
```

Apply a modification with validation. Returns `Validated.valid(updated)` if **all** validations pass, `Validated.invalid(allErrors)` if **any** fail. **Collects all errors** (error accumulation).

```java
List<OrderItem> items = List.of(
    new OrderItem("SKU001", "Laptop", new BigDecimal("-100.00"), 1, "pending"),  // Error 1
    new OrderItem("SKU002", "Mouse", new BigDecimal("29.99"), -5, "pending"),
    new OrderItem("SKU003", "Keyboard", new BigDecimal("-50.00"), 1, "pending")  // Error 2
);

Lens<OrderItem, BigDecimal> priceLens = OrderItemLenses.price();
Traversal<List<OrderItem>, BigDecimal> allPrices =
    Traversals.<OrderItem>forList().andThen(priceLens.asTraversal());

// Accumulate ALL errors
Validated<List<String>, List<OrderItem>> result = modifyAllValidated(
    allPrices,
    price -> {
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            return Validated.invalid("Price cannot be negative: " + price);
        }
        if (price.compareTo(new BigDecimal("10000")) > 0) {
            return Validated.invalid("Price exceeds maximum: " + price);
        }
        return Validated.valid(price);
    },
    items
);
// Validated.invalid(["Price cannot be negative: -100.00", "Price cannot be negative: -50.00"])
// Checked ALL items and collected ALL errors

result.match(
    errors -> {
        System.out.println("❌ Validation failed with " + errors.size() + " errors:");
        errors.forEach(err -> System.out.println("   • " + err));
    },
    updated -> System.out.println("✅ All items valid")
);
```

~~~admonish tip title="💡 When to Use modifyAllValidated"
Use `modifyAllValidated` for **error accumulation** where:
- You want to collect **all** errors, not just the first one
- Better user experience (show all problems at once)
- Form validation where users need to fix all fields
- Batch processing where you want a complete error report

**Example:** User registration form—show all validation errors (invalid email, weak password, missing fields) rather than one at a time.
~~~

~~~admonish example title="Fail-Fast vs Error Accumulation: When to Use Each"
**Fail-Fast (`modifyAllEither`):**
```java
// API request validation - reject immediately
Either<String, List<Item>> result = modifyAllEither(
    allPrices,
    price -> validatePrice(price),
    items
);
return result.fold(
    error -> ResponseEntity.badRequest().body(error),
    valid -> ResponseEntity.ok(processOrder(valid))
);
```

**Error Accumulation (`modifyAllValidated`):**
```java
// Form validation - show all errors
Validated<List<String>, List<Item>> result = modifyAllValidated(
    allPrices,
    price -> validatePrice(price),
    items
);
return result.fold(
    errors -> showFormErrors(errors),  // Display ALL errors to user
    valid -> submitForm(valid)
);
```
~~~

#### `modifyWherePossible` — Selective Modification

```java
public static <S, A> S modifyWherePossible(
    Traversal<S, A> traversal,
    Function<A, Maybe<A>> modifier,
    S source
)
```

Apply a modification **selectively**. Modifies elements where the function returns `Maybe.just(value)`, leaves others unchanged. This is a **best-effort** operation—always succeeds, modifying what it can.

```java
List<OrderItem> items = List.of(
    new OrderItem("SKU001", "Laptop", new BigDecimal("999.99"), 1, "pending"),
    new OrderItem("SKU002", "Mouse", new BigDecimal("29.99"), 2, "shipped"),    // Don't modify
    new OrderItem("SKU003", "Keyboard", new BigDecimal("79.99"), 1, "pending")
);

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
// [
//   OrderItem(..., "processing"),  // SKU001 updated
//   OrderItem(..., "shipped"),     // SKU002 unchanged
//   OrderItem(..., "processing")   // SKU003 updated
// ]

System.out.println("Updated statuses:");
updated.forEach(item ->
    System.out.println("  " + item.sku() + ": " + item.status())
);
```

~~~admonish example title="Real-World Example: Conditional Price Adjustment"
```java
// Apply 10% discount to items over £100 (premium items only)
Lens<OrderItem, BigDecimal> priceLens = OrderItemLenses.price();
Traversal<List<OrderItem>, BigDecimal> allPrices =
    Traversals.<OrderItem>forList().andThen(priceLens.asTraversal());

List<OrderItem> discounted = modifyWherePossible(
    allPrices,
    price -> price.compareTo(new BigDecimal("100")) > 0
        ? Maybe.just(price.multiply(new BigDecimal("0.9")))
        : Maybe.nothing(),  // Leave cheaper items at full price
    items
);
```
~~~

~~~admonish tip title="💡 When to Use modifyWherePossible"
Use `modifyWherePossible` for **selective updates** where:
- Only some elements should be modified based on a condition
- Partial updates are acceptable and expected
- You want to "fix what's fixable"
- The operation should never fail

**Example:** Status transitions—update items in "pending" status to "processing", but leave "shipped" items unchanged.
~~~

### Analysis Methods

#### `countValid` — Count Passing Validation

```java
public static <S, A, E> int countValid(
    Traversal<S, A> traversal,
    Function<A, Either<E, A>> validator,
    S source
)
```

Count how many elements pass validation without modifying anything.

```java
List<OrderItem> items = List.of(
    new OrderItem("SKU001", "Laptop", new BigDecimal("999.99"), 1, "pending"),
    new OrderItem("SKU002", "Mouse", new BigDecimal("-10.00"), 2, "pending"),   // Invalid
    new OrderItem("SKU003", "Keyboard", new BigDecimal("79.99"), 1, "pending"),
    new OrderItem("SKU004", "Monitor", new BigDecimal("-50.00"), 1, "pending")  // Invalid
);

Lens<OrderItem, BigDecimal> priceLens = OrderItemLenses.price();
Traversal<List<OrderItem>, BigDecimal> allPrices =
    Traversals.<OrderItem>forList().andThen(priceLens.asTraversal());

int validCount = countValid(
    allPrices,
    price -> price.compareTo(BigDecimal.ZERO) >= 0
        ? Either.right(price)
        : Either.left("Negative price"),
    items
);
// 2

System.out.println("Valid items: " + validCount + " out of " + items.size());
System.out.println("Invalid items: " + (items.size() - validCount));
```

~~~admonish tip title="💡 When to Use countValid"
Use `countValid` for **reporting and metrics** where:
- You need to know how many items are valid without modifying them
- Generating validation reports or dashboards
- Pre-checking before bulk operations
- Displaying progress to users

**Example:** Show user "3 out of 5 addresses are valid" before allowing checkout.
~~~

#### `collectErrors` — Gather Validation Failures

```java
public static <S, A, E> List<E> collectErrors(
    Traversal<S, A> traversal,
    Function<A, Either<E, A>> validator,
    S source
)
```

Collect all validation errors without modifying anything. Returns empty list if all valid.

```java
List<OrderItem> items = List.of(
    new OrderItem("SKU001", "Laptop", new BigDecimal("999.99"), 1, "pending"),
    new OrderItem("SKU002", "Mouse", new BigDecimal("-10.00"), 2, "pending"),
    new OrderItem("SKU003", "Keyboard", new BigDecimal("79.99"), 1, "pending"),
    new OrderItem("SKU004", "Monitor", new BigDecimal("-50.00"), -1, "pending")
);

Lens<OrderItem, BigDecimal> priceLens = OrderItemLenses.price();
Traversal<List<OrderItem>, BigDecimal> allPrices =
    Traversals.<OrderItem>forList().andThen(priceLens.asTraversal());

List<String> errors = collectErrors(
    allPrices,
    price -> price.compareTo(BigDecimal.ZERO) >= 0
        ? Either.right(price)
        : Either.left("Negative price: " + price),
    items
);
// ["Negative price: -10.00", "Negative price: -50.00"]

if (errors.isEmpty()) {
    System.out.println("✅ All prices valid");
} else {
    System.out.println("❌ Found " + errors.size() + " invalid prices:");
    errors.forEach(err -> System.out.println("   • " + err));
}
```

~~~admonish tip title="💡 When to Use collectErrors"
Use `collectErrors` for **error reporting** where:
- You want a list of all problems without modifying data
- Generating validation reports
- Pre-flight checks before expensive operations
- Displaying errors to users

**Example:** Validate uploaded CSV file and show all errors before importing.
~~~

## Complete Real-World Example

Let's see a complete order validation pipeline combining multiple traversal extensions:

~~~admonish example title="E-Commerce Order Validation Pipeline"
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

    Order finalOrder = new Order(
        order.orderId(),
        discounted,
        order.customerEmail()
    );

    return new OrderApproved(finalOrder);
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

// Usage
ValidationResult result = validateOrder(order);
switch (result) {
    case OrderApproved approved -> processOrder(approved.order());
    case OrderRejected rejected -> displayErrors(rejected.errors());
}
```
~~~

## Before/After Comparison

**Scenario:** Validating and updating prices for all items in a shopping cart.

~~~admonish failure title="❌ Traditional Approach"
```java
public class CartValidator {
    public ValidationResult validateCart(List<CartItem> items) {
        List<String> errors = new ArrayList<>();
        List<CartItem> validated = new ArrayList<>();
        boolean hasErrors = false;

        for (int i = 0; i < items.size(); i++) {
            CartItem item = items.get(i);
            BigDecimal price = item.price();

            // Validate price
            if (price == null) {
                errors.add("Item " + i + ": Price is required");
                hasErrors = true;
                continue;
            }

            if (price.compareTo(BigDecimal.ZERO) < 0) {
                errors.add("Item " + i + ": Price cannot be negative");
                hasErrors = true;
                continue;
            }

            if (price.compareTo(new BigDecimal("10000")) > 0) {
                errors.add("Item " + i + ": Price too high");
                hasErrors = true;
                continue;
            }

            // Apply tax
            BigDecimal withTax = price.multiply(new BigDecimal("1.2"));
            CartItem updated = new CartItem(
                item.id(),
                item.name(),
                withTax,
                item.quantity()
            );
            validated.add(updated);
        }

        if (hasErrors) {
            return new ValidationFailure(errors);
        }
        return new ValidationSuccess(validated);
    }
}
```

Problems:
- Manual loop with index tracking
- Mutable state (`errors`, `validated`, `hasErrors`)
- Validation and transformation intertwined
- Hard to test validation logic separately
- Imperative, hard to reason about
~~~

~~~admonish success title="✅ With Traversal Extensions"
```java
public class CartValidator {
    public Validated<List<String>, List<CartItem>> validateCart(List<CartItem> items) {
        Lens<CartItem, BigDecimal> priceLens = CartItemLenses.price();
        Traversal<List<CartItem>, BigDecimal> allPrices =
            Traversals.<CartItem>forList().andThen(priceLens.asTraversal());

        return modifyAllValidated(
            allPrices,
            price -> validateAndApplyTax(price),
            items
        );
    }

    private Validated<String, BigDecimal> validateAndApplyTax(BigDecimal price) {
        if (price == null) {
            return Validated.invalid("Price is required");
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            return Validated.invalid("Price cannot be negative");
        }
        if (price.compareTo(new BigDecimal("10000")) > 0) {
            return Validated.invalid("Price too high");
        }
        return Validated.valid(price.multiply(new BigDecimal("1.2")));
    }
}
```

Benefits:
- Declarative, functional style
- No mutable state
- Validation logic is pure and testable
- Automatic error accumulation
- Clear separation of concerns
- Composable with other operations
~~~

## Best Practices

~~~admonish tip title="💡 Choose the Right Strategy"
**Use `modifyAllEither`** for fail-fast validation:
- API requests (reject immediately)
- Critical validations (stop on first error)
- When errors are independent

**Use `modifyAllValidated`** for error accumulation:
- Form validation (show all errors)
- Batch processing (complete error report)
- Better user experience

**Use `modifyWherePossible`** for selective updates:
- Conditional modifications
- Best-effort operations
- Status transitions
~~~

~~~admonish tip title="💡 Keep Validators Pure"
Your validation functions should be pure (no side effects):

```java
// ✅ Pure validator
private Validated<String, BigDecimal> validatePrice(BigDecimal price) {
    if (price.compareTo(BigDecimal.ZERO) < 0) {
        return Validated.invalid("Price cannot be negative");
    }
    return Validated.valid(price);
}

// ❌ Impure validator (has side effects)
private Validated<String, BigDecimal> validatePrice(BigDecimal price) {
    logger.info("Validating price: {}", price);  // Side effect!
    database.recordValidation(price);            // Side effect!
    if (price.compareTo(BigDecimal.ZERO) < 0) {
        return Validated.invalid("Price cannot be negative");
    }
    return Validated.valid(price);
}
```

Pure validators are easier to test, compose, and reason about.
~~~

~~~admonish warning title="⚠️ Error Order with modifyAllValidated"
When using `modifyAllValidated`, errors are accumulated in the order elements are traversed:

```java
List<OrderItem> items = List.of(item1, item2, item3);  // item1 and item3 have errors

Validated<List<String>, List<OrderItem>> result = modifyAllValidated(...);
// Errors will be: [error from item1, error from item3]
// Order is preserved
```

This is usually what you want, but be aware if error order matters for your use case.
~~~

~~~admonish tip title="💡 Combine with Analysis Methods"
Use `countValid` and `collectErrors` for pre-flight checks:

```java
// Check before expensive operation
List<String> errors = collectErrors(allPrices, this::validatePrice, items);

if (!errors.isEmpty()) {
    logger.warn("Validation would fail with {} errors", errors.size());
    return Either.left("Pre-flight check failed");
}

// Proceed with expensive operation
return modifyAllEither(allPrices, this::applyComplexTransformation, items);
```
~~~

## Working Example

For a complete, runnable demonstration of all traversal extension patterns, see:

~~~admonish example title="TraversalExtensionsExample.java"
[View the complete example →](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/TraversalExtensionsExample.java)

This example demonstrates:
- All traversal extension methods
- Fail-fast vs error accumulation strategies
- Selective modification patterns
- Counting and error collection
- Complete order validation pipeline
- Real-world e-commerce scenarios
~~~

## Summary

Traversal extensions provide:

🗺️ **Bulk Operations** — Process entire collections with validation and error handling

📊 **Error Strategies** — Choose fail-fast (`Either`) or error accumulation (`Validated`)

🎯 **Selective Updates** — Modify only elements that meet criteria

📈 **Analysis Tools** — Count valid items and collect errors without modification

🔄 **Composability** — Chain with lenses and other optics for complex workflows

🧪 **Testability** — Pure validation functions are easy to test in isolation

## Next Steps

You've now learned all three core type integration approaches! Return to the overview to see how they work together:

**Back:** [Working with Core Types and Optics](core_type_integration.md)

Or explore complete integration patterns:

**See Also:** [Integration Patterns Example](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/IntegrationPatternsExample.java) — Complete e-commerce workflow combining all approaches
