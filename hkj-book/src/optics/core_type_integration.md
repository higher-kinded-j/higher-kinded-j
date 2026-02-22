# Working with Core Types and Optics

![Diagram illustrating optics integration with functional core types like Maybe, Either, and Validated](../images/optics.jpg)

As you've learnt from the previous chapters, optics provide a powerful way to focus on and modify immutable data structures. But what happens when the data you're working with is wrapped in Higher-Kinded-J's core types (`Maybe`, `Either`, `Validated`, or `Try`)?

Traditional optics work brilliantly with straightforward, deterministic data. However, real-world applications rarely deal with such certainty. Fields might be `null`, operations might fail, validation might produce errors, and database calls might throw exceptions. Handling these scenarios whilst maintaining clean, composable optics code requires a bridge between these two powerful abstractions.

This is where **Core Type Integration** comes in.

~~~admonish info title="What You'll Learn"
- How to use Core Type Prisms to extract values from `Maybe`, `Either`, `Validated`, and `Try` without verbose pattern matching
- How Optics Extensions add null safety, validation, and exception handling to lenses and traversals
- Composing core type optics with lenses for deep navigation into nested structures
- Processing collections of core type values using prisms for filtering and extraction
- When to use Core Type Prisms versus Optics Extensions based on your use case
~~~

---

## The Challenge

Consider a typical scenario: updating a user profile where some fields are optional, validation might fail, and the database operation might throw an exception.

~~~admonish failure title="The Traditional Approach"
```java
public User updateUserProfile(User user, String newEmail) {
    // Null checking
    if (user == null || user.getProfile() == null) {
        return null; // Or throw exception?
    }

    // Validation
    if (newEmail == null || !newEmail.contains("@")) {
        throw new ValidationException("Invalid email");
    }

    // Try to update
    try {
        String validated = validateEmailFormat(newEmail);
        Profile updated = user.getProfile().withEmail(validated);
        return user.withProfile(updated);
    } catch (Exception e) {
        // Now what? Log and return null? Re-throw?
        log.error("Failed to update email", e);
        return null;
    }
}
```

This code is a mess of concerns: null handling, validation logic, exception management, and the actual update logic are all tangled together.
~~~

~~~admonish success title="The Functional Approach"
```java
public Either<String, User> updateUserProfile(User user, String newEmail) {
    Lens<User, Profile> profileLens = UserLenses.profile();
    Lens<Profile, String> emailLens = ProfileLenses.email();
    Lens<User, String> userToEmail = profileLens.andThen(emailLens);

    return modifyEither(
        userToEmail,
        email -> validateEmail(email),
        user
    );
}

private Either<String, String> validateEmail(String email) {
    if (email == null || !email.contains("@")) {
        return Either.left("Invalid email format");
    }
    return Either.right(email.toLowerCase());
}
```

Clean separation of concerns:
- Optics define the path to the data
- Core types handle the errors
- Business logic stays pure and testable
~~~

---

## Two Complementary Approaches

Higher-Kinded-J provides two integrated solutions for working with core types and optics:

### 1. Core Type Prisms – Pattern Matching on Functional Types

Extract values from `Maybe`, `Either`, `Validated`, and `Try` using prisms, just as you would with sealed interfaces.

```java
Prism<Maybe<User>, User> justPrism = Prisms.just();
Prism<Try<Order>, Order> successPrism = Prisms.success();

// Extract user if present
Optional<User> user = justPrism.getOptional(maybeUser);

// Extract order if successful
Optional<Order> order = successPrism.getOptional(tryOrder);
```

**Best for:** Safe extraction and pattern matching on core types, composing with other optics.

### 2. Optics Extensions – Safety Rails for Lens and Traversal

Augment lenses and traversals with built-in null safety, validation, and exception handling.

```java
Lens<User, String> emailLens = UserLenses.email();

// Null-safe access
Maybe<String> email = getMaybe(emailLens, user);

// Validated modification
Either<String, User> updated = modifyEither(
    emailLens,
    email -> validateEmail(email),
    user
);

// Bulk validation with error accumulation
Validated<List<String>, List<Order>> result = modifyAllValidated(
    allPrices,
    price -> validatePrice(price),
    orders
);
```

**Best for:** Individual field operations with validation, bulk operations, exception handling.

[Learn more about Optics Extensions →](optics_extensions.md)

---

## Core Type Prisms in Detail

Prisms focus on **one case** of a sum type. They're perfect for safely extracting values from `Maybe`, `Either`, `Validated`, and `Try` without verbose pattern matching or null checks.

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
```

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
Optional<String> error = leftPrism.getOptional(failure);     // Optional["Error"]

// Construct Either values
Either<String, Integer> newSuccess = rightPrism.build(100);  // Either.right(100)
```

### Validated Prisms

```java
// Extract from Valid and Invalid cases
Prism<Validated<E, A>, A> validPrism = Prisms.valid();
Prism<Validated<E, A>, E> invalidPrism = Prisms.invalid();

Validated<String, Integer> valid = Validated.valid(30);
Validated<String, Integer> invalid = Validated.invalid("Age must be positive");

// Extract valid value
Optional<Integer> age = validPrism.getOptional(valid);       // Optional[30]

// Extract validation error
Optional<String> error = invalidPrism.getOptional(invalid);  // Optional["Age must be positive"]
```

### Try Prisms

```java
// Extract from Success and Failure cases
Prism<Try<A>, A> successPrism = Prisms.success();
Prism<Try<A>, Throwable> failurePrism = Prisms.failure();

Try<Integer> success = Try.success(42);
Try<Integer> failure = Try.failure(new RuntimeException("Database error"));

// Extract success value
Optional<Integer> value = successPrism.getOptional(success);    // Optional[42]

// Extract exception
Optional<Throwable> ex = failurePrism.getOptional(failure);     // Optional[RuntimeException]
```

---

## Core Type Traversals

Whilst prisms *extract* values, traversals *modify* values inside core types:

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

// Error enrichment with Left traversal
Either<String, Integer> error = Either.left("Connection failed");
Either<String, Integer> enriched = Traversals.modify(
    leftTraversal,
    msg -> "[ERROR] " + msg,
    error
);
// Result: Either.left("[ERROR] Connection failed")
```

~~~admonish tip title="Error Enrichment"
The `EitherTraversals.left()` traversal is excellent for adding context to error messages without unwrapping the Either.
~~~

---

## Composition: The Real Power

Prisms compose seamlessly with lenses and other optics to navigate deeply nested structures:

```java
@GenerateLenses
record ApiResponse(int statusCode, Maybe<Order> data, List<String> warnings) {}

@GenerateLenses
record Order(String orderId, Customer customer, List<OrderItem> items) {}

@GenerateLenses
record Customer(String customerId, String name, String email) {}

// Full composition: ApiResponse -> Maybe<Order> -> Order -> Customer -> email
Lens<ApiResponse, Maybe<Order>> dataLens = ApiResponseLenses.data();
Traversal<Maybe<Order>, Order> orderTraversal = MaybeTraversals.just();
Lens<Order, Customer> customerLens = OrderLenses.customer();
Lens<Customer, String> emailLens = CustomerLenses.email();

Traversal<ApiResponse, String> emailPath = dataLens
    .andThen(orderTraversal)
    .andThen(customerLens.asTraversal())
    .andThen(emailLens.asTraversal());

List<String> emails = Traversals.toListOf(emailPath, response);
// Result: ["customer@example.com"] or [] if no order data
```

---

## Processing Collections of Core Types

Prisms excel at filtering and extracting from collections:

```java
List<Try<User>> dbResults = loadUsersFromDatabase(userIds);

Prism<Try<User>, User> successPrism = Prisms.success();
Prism<Try<User>, Throwable> failurePrism = Prisms.failure();

// Get all successfully loaded users
List<User> users = dbResults.stream()
    .flatMap(result -> successPrism.getOptional(result).stream())
    .toList();

// Count successes
long successCount = dbResults.stream()
    .filter(successPrism::matches)
    .count();

// Log all errors
dbResults.stream()
    .flatMap(result -> failurePrism.getOptional(result).stream())
    .forEach(error -> logger.error("Database error: {}", error.getMessage()));
```

---

## When to Use Each Approach

### Use Core Type Prisms when:
- Extracting values from `Maybe`, `Either`, `Validated`, or `Try`
- Pattern matching on functional types without `instanceof`
- Composing core types with other optics for deep navigation
- Processing collections of core type values

### Use Optics Extensions when:
- Accessing potentially null fields
- Validating single field or bulk updates
- Performing operations that might throw exceptions
- Choosing between fail-fast and error accumulation strategies

---

## Example Code

~~~admonish example title="Runnable Examples"
- [CoreTypePrismsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/CoreTypePrismsExample.java) – API response processing
- [LensExtensionsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/LensExtensionsExample.java) – User profile validation
- [TraversalExtensionsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/TraversalExtensionsExample.java) – Bulk order processing
- [IntegrationPatternsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/IntegrationPatternsExample.java) – Complete e-commerce workflow
~~~

---

## Common Pitfalls

~~~admonish warning title="Don't Mix Effect Types Carelessly"
Whilst all core type families work with optics, mixing them inappropriately can lead to confusing code:

```java
// Confusing: Mixing Maybe and Either unnecessarily
Maybe<Either<String, User>> confusing = ...;

// Better: Choose one based on your needs
Either<String, User> clear = ...; // If you have an error message
Maybe<User> simple = ...;          // If it's just presence/absence
```
~~~

~~~admonish tip title="Start with Either"
When in doubt, start with `Either`. It's the most versatile:
- Carries error information (unlike `Maybe`)
- Fails fast (unlike `Validated`)
- Doesn't catch exceptions automatically (unlike `Try`)

You can always switch to `Validated` for error accumulation or `Try` for exception handling when needed.
~~~

~~~admonish warning title="Prisms Return Optional"
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

---

## Summary

Core Type Integration provides:

**Safe Extraction** – Extract values from `Maybe`, `Either`, `Validated`, and `Try` without null checks or verbose pattern matching

**Pattern Matching** – Use `matches()` to check cases, `getOptional()` to extract values

**Composability** – Combine with lenses and traversals for deep navigation

**Collection Processing** – Filter, extract, and count different cases in collections

**Type Safety** – The compiler ensures you handle all cases correctly

---

**Previous:** [Composing Optics](composing_optics.md)
**Next:** [Optics Extensions](optics_extensions.md)
