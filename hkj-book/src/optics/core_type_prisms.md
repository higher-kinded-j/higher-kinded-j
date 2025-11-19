# Core Type Prisms: Safe Extraction and Pattern Matching

![prisms.jpg](../images/optics.jpg)

Prisms are optics that focus on **one case** of a sum type. They're perfect for safely extracting values from `Maybe`, `Either`, `Validated`, and `Try` without verbose pattern matching or null checks.

Think of a prism like a quality inspector at a factory sorting line. It can:
- **Identify** whether an item matches a specific case (`matches()`)
- **Extract** the value if it matches (`getOptional()`)
- **Construct** a new value of that case (`build()`)

This chapter shows you how to use prisms to work elegantly with Higher-Kinded-J's core types.

## The Problem: Verbose Pattern Matching

Before we dive into prisms, let's see the traditional approach:

~~~admonish failure title="❌ Traditional Pattern Matching"
```java
// Extracting from Maybe
Maybe<User> maybeUser = getUserById("u123");
if (maybeUser.isJust()) {
    User user = maybeUser.get();
    processUser(user);
} else {
    handleMissingUser();
}

// Extracting from Either
Either<String, Order> result = createOrder(request);
if (result.isRight()) {
    Order order = result.fold(err -> null, ord -> ord);
    saveOrder(order);
} else {
    String error = result.fold(err -> err, ord -> null);
    logError(error);
}

// Extracting from Try
Try<Connection> tryConnection = connectToDatabase();
if (tryConnection.isSuccess()) {
    Connection conn = tryConnection.fold(c -> c, ex -> null);
    useConnection(conn);
} else {
    Throwable error = tryConnection.fold(c -> null, ex -> ex);
    handleError(error);
}
```

This code is repetitive, error-prone, and hard to compose with other operations.
~~~

~~~admonish success title="✅ With Prisms"
```java
// Extracting from Maybe
Prism<Maybe<User>, User> justPrism = Prisms.just();
getUserById("u123")
    .flatMap(justPrism::getOptional)
    .ifPresent(this::processUser);

// Extracting from Either
Prism<Either<String, Order>, Order> rightPrism = Prisms.right();
Prism<Either<String, Order>, String> leftPrism = Prisms.left();

Either<String, Order> result = createOrder(request);
rightPrism.getOptional(result).ifPresent(this::saveOrder);
leftPrism.getOptional(result).ifPresent(this::logError);

// Extracting from Try
Prism<Try<Connection>, Connection> successPrism = Prisms.success();
Prism<Try<Connection>, Throwable> failurePrism = Prisms.failure();

Try<Connection> tryConnection = connectToDatabase();
successPrism.getOptional(tryConnection).ifPresent(this::useConnection);
failurePrism.getOptional(tryConnection).ifPresent(this::handleError);
```

Clean, composable, and type-safe. The prisms handle the pattern matching internally.
~~~

## Available Prisms

Higher-Kinded-J provides prisms for all core types in the `Prisms` utility class:

~~~admonish note title="💡 Alternative: Fluent API"
Prisms can also be used through the [Fluent API](fluent_api.md) for method chaining and better discoverability. For example, prism operations like `getOptional` and `modify` can be accessed through `OpticOps` methods for a more fluent interface.
~~~

### Maybe Prisms

```java
// Extract value from Just, returns empty Optional for Nothing
Prism<Maybe<A>, A> justPrism = Prisms.just();

Maybe<String> present = Maybe.just("Hello");
Maybe<String> absent = Maybe.nothing();

Optional<String> value = justPrism.getOptional(present);  // Optional["Hello"]
Optional<String> empty = justPrism.getOptional(absent);   // Optional.empty()

// Construct Maybe.just() from a value
Maybe<String> built = justPrism.build("World");  // Maybe.just("World")

// Check if it's a Just
boolean isJust = justPrism.matches(present);  // true
boolean isNothing = justPrism.matches(absent); // false
```

~~~admonish tip title="💡 When to Use Maybe Prisms"
Use `Prisms.just()` when:
- Extracting optional API response data
- Composing with other optics to navigate nested structures
- Converting `Maybe` to `Optional` for interop with Java APIs
- Filtering collections of `Maybe` values
~~~

### Either Prisms

```java
// Extract from Left and Right cases
Prism<Either<L, R>, L> leftPrism = Prisms.left();
Prism<Either<L, R>, R> rightPrism = Prisms.right();

Either<String, Integer> success = Either.right(42);
Either<String, Integer> failure = Either.left("Error");

// Extract success value
Optional<Integer> value = rightPrism.getOptional(success);   // Optional[42]
Optional<Integer> noValue = rightPrism.getOptional(failure); // Optional.empty()

// Extract error value
Optional<String> noError = leftPrism.getOptional(success);   // Optional.empty()
Optional<String> error = leftPrism.getOptional(failure);     // Optional["Error"]

// Construct Either values
Either<String, Integer> newSuccess = rightPrism.build(100);  // Either.right(100)
Either<String, Integer> newFailure = leftPrism.build("Oops"); // Either.left("Oops")

// Check which case
boolean isRight = rightPrism.matches(success);  // true
boolean isLeft = leftPrism.matches(failure);    // true
```

~~~admonish example title="Real-World Example: Processing Validation Results"
```java
List<Either<String, User>> validationResults = validateUsers(requests);

Prism<Either<String, User>, User> validPrism = Prisms.right();
Prism<Either<String, User>, String> errorPrism = Prisms.left();

// Collect all successful users
List<User> validUsers = validationResults.stream()
    .flatMap(result -> validPrism.getOptional(result).stream())
    .toList();

// Collect all error messages
List<String> errors = validationResults.stream()
    .flatMap(result -> errorPrism.getOptional(result).stream())
    .toList();

System.out.println("Successfully validated: " + validUsers.size() + " users");
System.out.println("Validation errors: " + errors);
```
~~~

### Validated Prisms

```java
// Extract from Valid and Invalid cases
Prism<Validated<E, A>, A> validPrism = Prisms.valid();
Prism<Validated<E, A>, E> invalidPrism = Prisms.invalid();

Validated<String, Integer> valid = Validated.valid(30);
Validated<String, Integer> invalid = Validated.invalid("Age must be positive");

// Extract valid value
Optional<Integer> age = validPrism.getOptional(valid);       // Optional[30]
Optional<Integer> noAge = validPrism.getOptional(invalid);   // Optional.empty()

// Extract validation error
Optional<String> noError = invalidPrism.getOptional(valid);  // Optional.empty()
Optional<String> error = invalidPrism.getOptional(invalid);  // Optional["Age must be positive"]

// Construct Validated values
Validated<String, Integer> newValid = validPrism.build(25);           // Validated.valid(25)
Validated<String, Integer> newInvalid = invalidPrism.build("Error");  // Validated.invalid("Error")
```

~~~admonish tip title="💡 Validated vs Either Prisms"
`Validated` and `Either` have similar prisms, but serve different purposes:

- **Either prisms**: Use for fail-fast validation (stop at first error)
- **Validated prisms**: Use with error accumulation (collect all errors)

The prisms themselves work identically—the difference is in how you combine multiple validations.
~~~

### Try Prisms

```java
// Extract from Success and Failure cases
Prism<Try<A>, A> successPrism = Prisms.success();
Prism<Try<A>, Throwable> failurePrism = Prisms.failure();

Try<Integer> success = Try.success(42);
Try<Integer> failure = Try.failure(new RuntimeException("Database error"));

// Extract success value
Optional<Integer> value = successPrism.getOptional(success);    // Optional[42]
Optional<Integer> noValue = successPrism.getOptional(failure);  // Optional.empty()

// Extract exception
Optional<Throwable> noEx = failurePrism.getOptional(success);   // Optional.empty()
Optional<Throwable> ex = failurePrism.getOptional(failure);     // Optional[RuntimeException]

// Construct Try values
Try<Integer> newSuccess = successPrism.build(100);  // Try.success(100)
Try<Integer> newFailure = failurePrism.build(new IllegalStateException("Oops"));
```

~~~admonish example title="Real-World Example: Database Operation Results"
```java
List<Try<User>> dbResults = List.of(
    Try.of(() -> fetchUser("u1")),
    Try.of(() -> fetchUser("u2")),
    Try.of(() -> fetchUser("u3"))
);

Prism<Try<User>, User> successPrism = Prisms.success();
Prism<Try<User>, Throwable> failurePrism = Prisms.failure();

// Count successful loads
long successCount = dbResults.stream()
    .filter(successPrism::matches)
    .count();

// Log all errors
dbResults.stream()
    .flatMap(result -> failurePrism.getOptional(result).stream())
    .forEach(error -> logger.error("Database error: {}", error.getMessage()));

System.out.println("Loaded " + successCount + "/" + dbResults.size() + " users");
```
~~~

## Traversals for Core Types

Whilst prisms *extract* values, traversals *modify* values inside core types. Higher-Kinded-J provides traversal utilities for all core types:

### Maybe Traversals

```java
import org.higherkindedj.optics.util.MaybeTraversals;

Traversal<Maybe<String>, String> justTraversal = MaybeTraversals.just();

// Modify value inside Just
Maybe<String> original = Maybe.just("hello");
Maybe<String> modified = Traversals.modify(justTraversal, String::toUpperCase, original);
// Result: Maybe.just("HELLO")

// No effect on Nothing
Maybe<String> nothing = Maybe.nothing();
Maybe<String> unchanged = Traversals.modify(justTraversal, String::toUpperCase, nothing);
// Result: Maybe.nothing()
```

### Either Traversals

```java
import org.higherkindedj.optics.util.EitherTraversals;

Traversal<Either<String, Integer>, Integer> rightTraversal = EitherTraversals.right();
Traversal<Either<String, Integer>, String> leftTraversal = EitherTraversals.left();

// Modify Right value
Either<String, Integer> success = Either.right(100);
Either<String, Integer> doubled = Traversals.modify(rightTraversal, n -> n * 2, success);
// Result: Either.right(200)

// Enrich Left value (error enrichment)
Either<String, Integer> error = Either.left("Connection failed");
Either<String, Integer> enriched = Traversals.modify(
    leftTraversal,
    msg -> "[ERROR] " + msg,
    error
);
// Result: Either.left("[ERROR] Connection failed")
```

~~~admonish tip title="💡 Error Enrichment with Left Traversal"
The `EitherTraversals.left()` traversal is excellent for error enrichment—adding context or formatting to error messages without unwrapping the Either:

```java
Either<String, Order> result = processOrder(request);

// Add request ID to all errors
Either<String, Order> enriched = Traversals.modify(
    EitherTraversals.left(),
    error -> String.format("[Request %s] %s", requestId, error),
    result
);
```
~~~

### Validated Traversals

```java
import org.higherkindedj.optics.util.ValidatedTraversals;

Traversal<Validated<String, Integer>, Integer> validTraversal = ValidatedTraversals.valid();
Traversal<Validated<String, Integer>, String> invalidTraversal = ValidatedTraversals.invalid();

// Modify valid value
Validated<String, Integer> valid = Validated.valid(30);
Validated<String, Integer> incremented = Traversals.modify(validTraversal, age -> age + 1, valid);
// Result: Validated.valid(31)

// Modify error
Validated<String, Integer> invalid = Validated.invalid("Age required");
Validated<String, Integer> formatted = Traversals.modify(
    invalidTraversal,
    err -> "Validation Error: " + err,
    invalid
);
// Result: Validated.invalid("Validation Error: Age required")
```

### Try Traversals

```java
import org.higherkindedj.optics.util.TryTraversals;

Traversal<Try<Integer>, Integer> successTraversal = TryTraversals.success();
Traversal<Try<Integer>, Throwable> failureTraversal = TryTraversals.failure();

// Modify success value
Try<Integer> success = Try.success(42);
Try<Integer> doubled = Traversals.modify(successTraversal, n -> n * 2, success);
// Result: Try.success(84)

// Wrap exceptions
Try<Integer> failure = Try.failure(new SQLException("Connection lost"));
Try<Integer> wrapped = Traversals.modify(
    failureTraversal,
    cause -> new DatabaseException("Database error", cause),
    failure
);
// Result: Try.failure(DatabaseException wrapping SQLException)
```

## Composition: The Real Power

Prisms compose seamlessly with lenses and other optics to navigate deeply nested structures:

~~~admonish example title="Example: Extracting Nested Optional Data"
```java
@GenerateLenses
record ApiResponse(int statusCode, Maybe<Order> data, List<String> warnings) {}

@GenerateLenses
record Order(String orderId, Customer customer, List<OrderItem> items) {}

@GenerateLenses
record Customer(String customerId, String name, String email) {}

// Get customer email from API response (if present)
ApiResponse response = fetchOrder("ORD-123");

// Method 1: Using prism directly
Prism<Maybe<Order>, Order> justPrism = Prisms.just();
Optional<String> email = justPrism.getOptional(response.data())
    .map(order -> order.customer().email());

// Method 2: Compose with lenses for a complete path
Lens<ApiResponse, Maybe<Order>> dataLens = ApiResponseLenses.data();
Traversal<Maybe<Order>, Order> orderTraversal = MaybeTraversals.just();
Lens<Order, Customer> customerLens = OrderLenses.customer();
Lens<Customer, String> emailLens = CustomerLenses.email();

// Full composition: ApiResponse -> Maybe<Order> -> Order -> Customer -> email
Traversal<ApiResponse, String> emailPath = dataLens
    .andThen(orderTraversal)
    .andThen(customerLens.asTraversal())
    .andThen(emailLens.asTraversal());

List<String> emails = Traversals.toListOf(emailPath, response);
// Result: ["customer@example.com"] or [] if no order data
```
~~~

## Processing Collections of Core Types

Prisms excel at filtering and extracting from collections of `Maybe`, `Either`, `Validated`, or `Try`:

### Extracting Successes

```java
List<Try<User>> dbResults = loadUsersFromDatabase(userIds);

Prism<Try<User>, User> successPrism = Prisms.success();

// Get all successfully loaded users
List<User> users = dbResults.stream()
    .flatMap(result -> successPrism.getOptional(result).stream())
    .toList();
```

### Extracting Failures

```java
List<Either<ValidationError, Order>> validations = validateOrders(requests);

Prism<Either<ValidationError, Order>, ValidationError> errorPrism = Prisms.left();

// Collect all validation errors
List<ValidationError> errors = validations.stream()
    .flatMap(result -> errorPrism.getOptional(result).stream())
    .toList();

if (!errors.isEmpty()) {
    displayErrorsToUser(errors);
}
```

### Counting Cases

```java
List<Validated<List<String>, Product>> validations = validateProducts(products);

Prism<Validated<List<String>, Product>, Product> validPrism = Prisms.valid();

long validCount = validations.stream()
    .filter(validPrism::matches)
    .count();

System.out.println(validCount + "/" + validations.size() + " products valid");
```

## Common Patterns

### Pattern 1: Optional Chaining with Maybe

Instead of nested `if (isJust())` checks:

```java
// ❌ Traditional
Maybe<User> maybeUser = findUser(id);
if (maybeUser.isJust()) {
    User user = maybeUser.get();
    Maybe<Address> maybeAddress = user.getAddress();
    if (maybeAddress.isJust()) {
        Address address = maybeAddress.get();
        processAddress(address);
    }
}

// ✅ With prisms
Prism<Maybe<User>, User> justUserPrism = Prisms.just();
Prism<Maybe<Address>, Address> justAddressPrism = Prisms.just();

findUser(id)
    .flatMap(justUserPrism::getOptional)
    .flatMap(user -> user.getAddress())
    .flatMap(justAddressPrism::getOptional)
    .ifPresent(this::processAddress);
```

### Pattern 2: Error Handling with Either

Extracting specific error types:

```java
sealed interface AppError permits ValidationError, DatabaseError, NetworkError {}

Either<AppError, User> result = createUser(request);

Prism<Either<AppError, User>, User> successPrism = Prisms.right();
Prism<Either<AppError, User>, AppError> errorPrism = Prisms.left();

// Handle success
successPrism.getOptional(result).ifPresent(user -> {
    logger.info("User created: {}", user.id());
    sendWelcomeEmail(user);
});

// Handle errors
errorPrism.getOptional(result).ifPresent(error -> {
    switch (error) {
        case ValidationError ve -> displayFormErrors(ve);
        case DatabaseError de -> retryOrFallback(de);
        case NetworkError ne -> scheduleRetry(ne);
    }
});
```

### Pattern 3: Exception Recovery with Try

```java
Try<Config> configResult = Try.of(() -> loadConfig(configPath));

Prism<Try<Config>, Config> successPrism = Prisms.success();
Prism<Try<Config>, Throwable> failurePrism = Prisms.failure();

// Use config if loaded successfully
Config config = successPrism.getOptional(configResult)
    .orElseGet(() -> {
        // Log the failure
        failurePrism.getOptional(configResult).ifPresent(error ->
            logger.error("Failed to load config", error)
        );
        // Return default config
        return Config.defaults();
    });
```

## Before/After Comparison

Let's see a complete real-world scenario comparing traditional approaches with prisms:

**Scenario:** Processing a batch of API responses, each containing optional user data.

~~~admonish failure title="❌ Traditional Approach"
```java
public List<String> extractUserEmails(List<ApiResponse<User>> responses) {
    List<String> emails = new ArrayList<>();

    for (ApiResponse<User> response : responses) {
        if (response.statusCode() == 200) {
            Maybe<User> data = response.data();
            if (data.isJust()) {
                User user = data.get();
                if (user.email() != null) {
                    emails.add(user.email());
                }
            }
        }
    }

    return emails;
}
```

Problems:
- Deeply nested conditionals
- Manual null checking
- Imperative style with mutable list
- Easy to introduce bugs
~~~

~~~admonish success title="✅ With Prisms and Functional Style"
```java
public List<String> extractUserEmails(List<ApiResponse<User>> responses) {
    Prism<Maybe<User>, User> justPrism = Prisms.just();

    return responses.stream()
        .filter(r -> r.statusCode() == 200)
        .flatMap(r -> justPrism.getOptional(r.data()).stream())
        .map(User::email)
        .filter(Objects::nonNull)
        .toList();
}
```

Benefits:
- Flat, readable pipeline
- Prism handles the Maybe extraction
- Declarative, functional style
- Harder to introduce bugs
~~~

## Best Practices

~~~admonish tip title="💡 Choose the Right Tool"
**Use prisms when:**
- Extracting values from core types
- Pattern matching on sum types
- Composing with other optics for deep navigation
- Processing collections of core types

**Use traversals when:**
- Modifying values inside core types
- Applying transformations conditionally
- Error enrichment or exception wrapping
~~~

~~~admonish warning title="⚠️ Prisms Return Optional"
Remember that `prism.getOptional()` returns Java's `Optional`, not `Maybe`:

```java
Prism<Maybe<String>, String> justPrism = Prisms.just();
Maybe<String> maybeValue = Maybe.just("Hello");

// Returns Optional, not Maybe
Optional<String> value = justPrism.getOptional(maybeValue);

// Convert back to Maybe if needed
Maybe<String> backToMaybe = value
    .map(Maybe::just)
    .orElse(Maybe.nothing());
```
~~~

~~~admonish tip title="💡 Compose for Deep Navigation"
Don't extract and re-wrap manually. Compose prisms with lenses and traversals for deep navigation:

```java
// ❌ Manual extraction
Optional<User> user = justPrism.getOptional(maybeUser);
Optional<String> email = user.map(u -> emailLens.get(u));

// ✅ Compose
Traversal<Maybe<User>, String> path = MaybeTraversals.just()
    .andThen(emailLens.asTraversal());
List<String> emails = Traversals.toListOf(path, maybeUser);
```
~~~

## Working Example

For a complete, runnable demonstration of all these patterns, see:

~~~admonish example title="CoreTypePrismsExample.java"
[View the complete example →](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/CoreTypePrismsExample.java)

This example demonstrates:
- All core type prisms (Maybe, Either, Validated, Try)
- All core type traversals
- Composition with lenses
- Processing collections
- Before/after comparisons
- Real-world API response processing
~~~

## Summary

Core type prisms provide:

🎯 **Safe Extraction** — Extract values from `Maybe`, `Either`, `Validated`, and `Try` without null checks or verbose pattern matching

🔍 **Pattern Matching** — Use `matches()` to check cases, `getOptional()` to extract values

🔄 **Composability** — Combine with lenses and traversals for deep navigation

📊 **Collection Processing** — Filter, extract, and count different cases in collections

🛡️ **Type Safety** — The compiler ensures you handle all cases correctly

## Next Steps

Now that you understand core type prisms, learn how to enhance lens operations with validation and error handling:

**Next:** [Lens Extensions: Validated Field Operations](lens_extensions.md)

Or return to the overview:

**Back:** [Working with Core Types and Optics](core_type_integration.md)
