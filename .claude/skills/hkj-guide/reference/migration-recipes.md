# Migration Recipes: Imperative Java -> Effect Paths

Quick-lookup reference for converting common imperative Java patterns to Higher-Kinded-J Effect Paths.

**Decision table:**

| Imperative Pattern | Target Path | Key Benefit |
|--------------------|-------------|-------------|
| `try/catch` | `TryPath` | Composable error recovery, no control-flow jumps |
| `Optional.flatMap` chains | `MaybePath` | Uniform vocabulary, easy upgrade to typed errors |
| Nested `if (x != null)` | `MaybePath` | Flat chain, auto short-circuit on null |
| `CompletableFuture.thenCompose` | `CompletableFuturePath` | Integrated recovery, parallel combinators |
| Manual `List<String> errors` | `ValidationPath` | Automatic accumulation, type-safe result |
| Nested record reconstruction | `FocusPath` + lenses | Declarative path, immune to field additions |

---

## Recipe 1: try/catch -> TryPath

**Pattern replaced:** Exception handling interleaved with business logic.

### Before
```java
String result;
try {
    String raw = readFile(path);
    result = transform(raw);
} catch (IOException e) {
    log.warn("Read failed", e);
    result = DEFAULT_VALUE;
}
```

### After
```java
String result = Path.tryOf(() -> readFile(path))
    .map(this::transform)
    .recover(e -> {
        log.warn("Read failed", e);
        return DEFAULT_VALUE;
    })
    .getOrElse(DEFAULT_VALUE);
```

**Gain:** Happy path reads top-to-bottom. Recovery separated from business logic. Additional `.map`/`.via` steps insert without restructuring error handling.

---

## Recipe 2: Optional Chains -> MaybePath

**Pattern replaced:** `Optional.flatMap` chains that lack uniform composition vocabulary.

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

**Gain:** Shares `via`, `map`, `peek`, `recover` vocabulary with all other Path types. Upgrading to typed errors is a one-line change:

```java
// MaybePath -> EitherPath upgrade
Path.maybe(findUser(id))
    .toEitherPath(new AppError.UserNotFound(id))
    .via(user -> Path.maybe(findAddress(user))
        .toEitherPath(new AppError.NoAddress(user.id())))
    .map(Address::city)
    .run();
```

---

## Recipe 3: Nested Null Checks -> MaybePath

**Pattern replaced:** Deeply nested `if (x != null)` pyramids for defensive navigation.

### Before
```java
String postcode = null;
Order order = findOrder(id);
if (order != null) {
    Customer c = order.customer();
    if (c != null) {
        Address a = c.shippingAddress();
        if (a != null) postcode = a.postcode();
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

**Gain:** Nested `if` pyramid collapses to flat chain. Any step returning `null` short-circuits to `Nothing` automatically. No explicit null checks needed.

**Note:** `MaybePath.map` propagates null returns as `Nothing`. `Path.maybe(value)` wraps null as `Nothing`, non-null as `Just`.

---

## Recipe 4: CompletableFuture Nesting -> CompletableFuturePath

**Pattern replaced:** `thenCompose`/`thenApply` nesting with awkward error handling.

### Before
```java
CompletableFuture<OrderConfirmation> result =
    userService.findUser(userId)
        .thenCompose(user -> inventoryService.checkStock(user.cartItems())
            .thenCompose(stock -> paymentService.charge(user, stock.total())
                .thenApply(receipt ->
                    new OrderConfirmation(user, stock, receipt))));
```

### After
```java
var result = CompletableFuturePath.fromFuture(userService.findUser(userId))
    .via(user -> CompletableFuturePath.fromFuture(
        inventoryService.checkStock(user.cartItems()))
        .via(stock -> CompletableFuturePath.fromFuture(
            paymentService.charge(user, stock.total()))
            .map(receipt -> new OrderConfirmation(user, stock, receipt))))
    .recover(ex -> OrderConfirmation.failed(ex.getMessage()));
```

**Gain:** `recover` integrates into the chain (not dangling at the end). `peek` adds logging at any step.

### Parallel independent fetches
```java
CompletableFuturePath.fromFuture(fetchMetrics())
    .parZipWith(
        CompletableFuturePath.fromFuture(fetchAlerts()),
        Dashboard::new);
```

Both fetches run concurrently; result available when both complete.

---

## Recipe 5: Manual Error Accumulation -> ValidationPath

**Pattern replaced:** Manual `List<String> errors` collection with imperative if-checks.

### Before
```java
List<String> errors = new ArrayList<>();
if (name == null || name.length() < 2)
    errors.add("Name must be >= 2 chars");
if (email == null || !email.contains("@"))
    errors.add("Invalid email");
if (age < 0 || age > 150)
    errors.add("Age out of range");
if (!errors.isEmpty())
    return badRequest(errors);
Registration reg = new Registration(name, email, age);
```

### After
```java
Semigroup<List<String>> sg = Semigroups.list();

ValidationPath<List<String>, String> vName = name != null && name.length() >= 2
    ? Path.valid(name, sg)
    : Path.invalid(List.of("Name must be >= 2 chars"), sg);

ValidationPath<List<String>, String> vEmail = email != null && email.contains("@")
    ? Path.valid(email, sg)
    : Path.invalid(List.of("Invalid email"), sg);

ValidationPath<List<String>, Integer> vAge = age >= 0 && age <= 150
    ? Path.valid(age, sg)
    : Path.invalid(List.of("Age out of range"), sg);

ValidationPath<List<String>, Registration> result =
    vName.zipWith3Accum(vEmail, vAge, Registration::new);
```

**Gain:** All validators run independently. `zipWith3Accum` accumulates every error, not just the first. The result type proves validation passed if you get a `Registration`.

### Switch to short-circuit after validation
```java
result.toEitherPath()
    .via(reg -> processRegistration(reg));
```

---

## Recipe 6: Deeply Nested Record Updates -> FocusPath with Lenses

**Pattern replaced:** Manual reconstruction of every intermediate record for a deeply nested field update.

### Before
```java
Order updatePostcode(Order order, String newPostcode) {
    Address old = order.customer().shippingAddress();
    Address newAddr = new Address(old.street(), old.city(),
        newPostcode, old.country());
    Customer newCust = new Customer(order.customer().id(),
        order.customer().name(), newAddr);
    return new Order(order.id(), newCust, order.items(), order.status());
}
```

### After
```java
// Uses @GenerateLenses annotation on records
Order updatePostcode(Order order, String newPostcode) {
    return OrderLenses.customer()
        .andThen(CustomerLenses.shippingAddress())
        .andThen(AddressLenses.postcode())
        .set(order, newPostcode);
}
```

**Gain:** Lens composition describes the path; the library handles reconstruction. Adding fields to `Address` requires zero changes here. Fully type-safe.

### Combining lenses with Effect Paths
```java
var postcodeLens = OrderLenses.customer()
    .andThen(CustomerLenses.shippingAddress())
    .andThen(AddressLenses.postcode());

EitherPath<AppError, Order> result =
    Path.<AppError, Order>right(order)
        .via(curr -> validatePostcode(postcodeLens.get(curr))
            .map(valid -> postcodeLens.set(curr, valid)));
```

---

## Conversion Cheat Sheet

When migrating incrementally, convert between Path types at boundaries:

| From | To | Method |
|------|----|--------|
| `MaybePath` | `EitherPath` | `.toEitherPath(error)` |
| `MaybePath` | `TryPath` | `.toTryPath(exceptionSupplier)` |
| `EitherPath` | `MaybePath` | `.toMaybePath()` |
| `EitherPath` | `TryPath` | `.toTryPath(errorToException)` |
| `TryPath` | `EitherPath` | `.toEitherPath(exceptionToError)` |
| `TryPath` | `MaybePath` | `.toMaybePath()` |
| `ValidationPath` | `EitherPath` | `.toEitherPath()` |

## Key Rules

- `map` = your function returns a plain value (`A -> B`)
- `via` = your function returns a Path (`A -> Path<B>`)
- `peek` = observe without changing (logging, metrics)
- `recover` = convert error track back to success track
- `mapError` = transform error type (for unifying error types across `via` boundaries)
