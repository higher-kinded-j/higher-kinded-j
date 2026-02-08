# Migration Cookbook

You have imperative Java code that works. You want to make it more composable, more testable, and more explicit about failure modes. This cookbook provides pattern-by-pattern translations from common imperative idioms to their Effect Path equivalents.

Each recipe shows the imperative code you have today, the Effect Path code you could write instead, and a one-sentence explanation of what you gain.

~~~admonish info title="What You'll Learn"
- How to translate `try/catch` blocks into `TryPath`
- How to replace `Optional` chains with `MaybePath`
- How to eliminate nested null checks with `MaybePath`
- How to flatten `CompletableFuture` nesting with `CompletableFuturePath`
- How to convert manual error accumulation into `ValidationPath`
- How to simplify deeply nested record updates with `FocusPath` and lenses
~~~

---

## Recipe 1: try/catch to TryPath

**The problem:** exception handling mixed into business logic, with `catch` blocks that obscure the recovery strategy.

### Before

```java
String result;
try {
    String raw = readFile(path);
    result = transform(raw);
} catch (IOException e) {
    log.warn("File read failed, using default", e);
    result = DEFAULT_VALUE;
}
return result;
```

### After

```java
String result = Path.tryOf(() -> readFile(path))
    .map(this::transform)
    .recover(e -> {
        log.warn("File read failed, using default", e);
        return DEFAULT_VALUE;
    })
    .getOrElse(DEFAULT_VALUE);
```

**What you gain:** the happy path reads top-to-bottom without interruption. Recovery logic is clearly separated from business logic. The `TryPath` chain is composable; you can insert additional `map` or `via` steps without restructuring the error handling.

---

## Recipe 2: Optional Chains to MaybePath

**The problem:** `Optional.flatMap` chains that grow unwieldy, particularly when each step needs its own error handling or fallback logic.

### Before

```java
String city = findUser(id)
    .flatMap(user -> findAddress(user))
    .map(Address::city)
    .orElse("Unknown");
```

### After

```java
String city = Path.maybe(findUser(id))
    .via(user -> Path.maybe(findAddress(user)))
    .map(Address::city)
    .run()
    .orElse("Unknown");
```

**What you gain:** at first glance the two look similar, and for simple chains they are. The difference appears when the chain grows. `MaybePath` shares the same `via`, `map`, `peek`, and `recover` vocabulary as every other Path type. If you later need to switch from "absent" to "typed error", changing `Path.maybe(...)` to `Path.right(...)` and adding `.toEitherPath(...)` is a one-line change, not a rewrite.

### When the chain needs a typed error

```java
// Seamless transition from MaybePath to EitherPath
Either<AppError, String> city =
    Path.maybe(findUser(id))
        .toEitherPath(new AppError.UserNotFound(id))
        .via(user -> Path.maybe(findAddress(user))
            .toEitherPath(new AppError.NoAddress(user.id())))
        .map(Address::city)
        .run();
```

With `Optional`, this transition requires restructuring the entire chain.

---

## Recipe 3: Nested Null Checks to MaybePath

**The problem:** defensive null checking that creates deeply nested `if` blocks, obscuring the actual data access path.

### Before

```java
String postcode = null;
Order order = findOrder(id);
if (order != null) {
    Customer customer = order.customer();
    if (customer != null) {
        Address address = customer.shippingAddress();
        if (address != null) {
            postcode = address.postcode();
        }
    }
}
return postcode != null ? postcode : "N/A";
```

### After

```java
String postcode = Path.maybe(findOrder(id))
    .map(Order::customer)
    .map(Customer::shippingAddress)
    .map(Address::postcode)
    .run()
    .orElse("N/A");
```

**What you gain:** the nested `if` pyramid collapses into a flat chain. If any step returns `null`, `MaybePath` short-circuits to `Nothing` and the remaining `map` calls are skipped.

~~~admonish note title="When Intermediate Nulls Are Expected"
`MaybePath.map` propagates `null` return values as `Nothing`. If `order.customer()` returns `null`, the chain stops. You do not need explicit null checks at each step.

If the *initial* value might be `null`, use `Path.maybe(value)` which wraps `null` as `Nothing` and non-null as `Just`.
~~~

---

## Recipe 4: CompletableFuture Nesting to CompletableFuturePath

**The problem:** `CompletableFuture` chains with `thenCompose` and `thenApply` that become difficult to read, particularly when error handling is involved.

### Before

```java
CompletableFuture<OrderConfirmation> result =
    userService.findUser(userId)
        .thenCompose(user ->
            inventoryService.checkStock(user.cartItems())
                .thenCompose(stock ->
                    paymentService.charge(user, stock.total())
                        .thenApply(receipt ->
                            new OrderConfirmation(user, stock, receipt))));
```

Error handling requires a separate `exceptionally` or `handle` call, often chained awkwardly at the end.

### After

```java
CompletableFuturePath<OrderConfirmation> result =
    CompletableFuturePath.fromFuture(userService.findUser(userId))
        .via(user -> CompletableFuturePath.fromFuture(
            inventoryService.checkStock(user.cartItems()))
            .via(stock -> CompletableFuturePath.fromFuture(
                paymentService.charge(user, stock.total()))
                .map(receipt -> new OrderConfirmation(user, stock, receipt))))
        .recover(ex -> OrderConfirmation.failed(ex.getMessage()));
```

**What you gain:** `recover` integrates directly into the chain instead of dangling at the end. `peek` lets you add logging at any step. And if you need to convert the result to a synchronous `EitherPath`, `.toEitherPath()` is one call away.

### Alternative: parallel independent fetches

When fetches are independent, use `parZipWith` instead of nested `via`:

```java
CompletableFuturePath<Dashboard> dashboard =
    CompletableFuturePath.fromFuture(fetchMetrics())
        .parZipWith(
            CompletableFuturePath.fromFuture(fetchAlerts()),
            Dashboard::new);
```

Both fetches run concurrently; the result is available when both complete.

---

## Recipe 5: Accumulating Validation Errors to ValidationPath

**The problem:** you need to report *all* validation failures at once, not just the first. Manual error accumulation is error-prone and mixes validation logic with error collection.

### Before

```java
List<String> errors = new ArrayList<>();

if (name == null || name.length() < 2) {
    errors.add("Name must be at least 2 characters");
}
if (email == null || !email.contains("@")) {
    errors.add("Invalid email format");
}
if (age < 0 || age > 150) {
    errors.add("Age must be between 0 and 150");
}

if (!errors.isEmpty()) {
    return ResponseEntity.badRequest().body(errors);
}
// proceed with validated data (but the types don't prove it's valid)
Registration reg = new Registration(name, email, age);
```

### After

```java
Semigroup<List<String>> errors = Semigroups.list();

ValidationPath<List<String>, String> validateName(String name) {
    return name != null && name.length() >= 2
        ? Path.valid(name, errors)
        : Path.invalid(List.of("Name must be at least 2 characters"), errors);
}

ValidationPath<List<String>, String> validateEmail(String email) {
    return email != null && email.contains("@")
        ? Path.valid(email, errors)
        : Path.invalid(List.of("Invalid email format"), errors);
}

ValidationPath<List<String>, Integer> validateAge(int age) {
    return age >= 0 && age <= 150
        ? Path.valid(age, errors)
        : Path.invalid(List.of("Age must be between 0 and 150"), errors);
}

// All three run; errors accumulate
ValidationPath<List<String>, Registration> result =
    validateName(name)
        .zipWith3Accum(
            validateEmail(email),
            validateAge(age),
            Registration::new);
```

**What you gain:** each validator is independent, reusable, and testable. `zipWith3Accum` runs all three and accumulates every error, not just the first. The result is either a valid `Registration` or a complete list of all failures. The types prove that if you have a `Registration`, it passed all validations.

### Switching to short-circuit after validation

Once validation passes, switch to `EitherPath` for sequential processing:

```java
EitherPath<List<String>, Confirmation> pipeline =
    result
        .toEitherPath()
        .via(reg -> processRegistration(reg));
```

---

## Recipe 6: Deeply Nested Record Updates to FocusPath

**The problem:** updating a field buried three levels deep in immutable records requires manually reconstructing every intermediate record.

### Before

```java
// Update the postcode of the shipping address of an order
Order updatePostcode(Order order, String newPostcode) {
    Address oldAddress = order.customer().shippingAddress();
    Address newAddress = new Address(
        oldAddress.street(),
        oldAddress.city(),
        newPostcode,
        oldAddress.country()
    );
    Customer newCustomer = new Customer(
        order.customer().id(),
        order.customer().name(),
        newAddress
    );
    return new Order(
        order.id(),
        newCustomer,
        order.items(),
        order.status()
    );
}
```

Every intermediate record must be reconstructed. Adding a field to any record means updating every reconstruction site.

### After

```java
// With generated lenses (via @GenerateLenses annotation)
Order updatePostcode(Order order, String newPostcode) {
    return OrderLenses.customer()
        .andThen(CustomerLenses.shippingAddress())
        .andThen(AddressLenses.postcode())
        .set(order, newPostcode);
}
```

**What you gain:** the lens composition describes the *path* to the field; the library handles the reconstruction. When you add a field to `Address`, this code needs no change. The lens is type-safe; the compiler rejects mismatched types.

### Combining with Effect Path

When the update is part of an effectful pipeline, `focus()` integrates lenses directly:

```java
var postcodeLens = OrderLenses.customer()
    .andThen(CustomerLenses.shippingAddress())
    .andThen(AddressLenses.postcode());

EitherPath<AppError, Order> result =
    Path.<AppError, Order>right(order)
        .via(currentOrder -> {
            String postcode = postcodeLens.get(currentOrder);
            return validatePostcode(postcode)
                .map(valid -> postcodeLens.set(currentOrder, valid));
        });
```

The lens extracts and updates the postcode within the same `via` block, so the update always applies to the current order in the pipeline, not a stale outer reference.

---

## Quick Reference: Which Recipe Do I Need?

| I have this... | I want this... | Recipe |
|----------------|---------------|--------|
| `try/catch` block | Composable error handling | [Recipe 1: TryPath](#recipe-1-trycatch-to-trypath) |
| `Optional.flatMap` chain | Uniform composition vocabulary | [Recipe 2: MaybePath](#recipe-2-optional-chains-to-maybepath) |
| Nested `if (x != null)` | Flat, null-safe chain | [Recipe 3: MaybePath](#recipe-3-nested-null-checks-to-maybepath) |
| `CompletableFuture.thenCompose` nesting | Readable async composition | [Recipe 4: CompletableFuturePath](#recipe-4-completablefuture-nesting-to-completablefuturepath) |
| Manual `List<String> errors` | Automatic error accumulation | [Recipe 5: ValidationPath](#recipe-5-accumulating-validation-errors-to-validationpath) |
| Manual record reconstruction | Lens-based deep updates | [Recipe 6: FocusPath](#recipe-6-deeply-nested-record-updates-to-focuspath) |

~~~admonish tip title="See Also"
- [Cheat Sheet](../cheatsheet.md) - One-page operator reference for all Path types
- [Path Types](path_types.md) - Detailed reference for when to use each Path type
- [Patterns and Recipes](patterns.md) - More real-world patterns and composition strategies
- [Stack Archetypes](../transformers/archetypes.md) - Named patterns for common enterprise problems
~~~

---

**Previous:** [Patterns and Recipes](patterns.md)
**Next:** [Common Compiler Errors](compiler_errors.md)
