# Fluent API for Optics: Java-Friendly Optic Operations

![Illustration of fluent API patterns for Java-friendly optic operations](../images/lens2.jpg)

~~~admonish info title="What You'll Learn"
- Two styles of optic operations: static methods and fluent builders
- When to use each style for maximum clarity and productivity
- How to perform common optic operations with Java-friendly syntax
- Validation-aware modifications with `Either`, `Maybe`, and `Validated`
- Four validation strategies for different error-handling scenarios
- Effectful modifications using type classes
- Practical patterns for real-world Java applications
~~~

~~~admonish title="Example Code"
[FluentOpticOpsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/fluent/FluentOpticOpsExample.java)
~~~

## Introduction: Making Optics Feel Natural in Java

While optics provide immense power for working with immutable data structures, their traditional functional programming syntax can feel foreign to Java developers. Method names like `view`, `over`, and `preview` don't match Java conventions, and the order of parameters can be unintuitive.

The `OpticOps` fluent API bridges this gap, providing two complementary styles that make optics feel natural in Java:

1. **Static methods** - Concise, direct operations for simple cases
2. **Fluent builders** - Method chaining with IDE-discoverable operations

Both styles operate on the same underlying optics, so you can mix and match based on what feels most natural for each situation.

---

## The Two Styles: A Quick Comparison

Let's see both styles in action with a simple example:

```java
@GenerateLenses
public record Person(String name, int age, String status) {}

Person person = new Person("Alice", 25, "ACTIVE");
Lens<Person, Integer> ageLens = PersonLenses.age();
```

### Static Method Style (Concise)

```java
// Get a value
int age = OpticOps.get(person, ageLens);

// Set a value
Person updated = OpticOps.set(person, ageLens, 30);

// Modify a value
Person modified = OpticOps.modify(person, ageLens, a -> a + 1);
```

### Fluent Builder Style (Explicit)

```java
// Get a value
int age = OpticOps.getting(person).through(ageLens);

// Set a value
Person updated = OpticOps.setting(person).through(ageLens, 30);

// Modify a value
Person modified = OpticOps.modifying(person).through(ageLens, a -> a + 1);
```

Both produce identical results. The choice is about **readability** and **discoverability** for your specific use case.

---

## Part 1: Static Methods - Simple and Direct

Static methods provide the most concise syntax. They follow a consistent pattern: operation name, source object, optic, and optional parameters.

### Getting Values

#### Basic Get Operations

```java
// Get a required value (Lens or Getter)
String name = OpticOps.get(person, PersonLenses.name());

// Get an optional value (Prism or Traversal)
Optional<Address> address = OpticOps.preview(person, PersonPrisms.homeAddress());

// Get all values (Traversal or Fold)
List<String> playerNames = OpticOps.getAll(team, TeamTraversals.playerNames());
```

~~~admonish example title="Practical Example: Extracting Data"
```java
@GenerateLenses
@GenerateTraversals
public record Team(String name, List<Player> players) {}

@GenerateLenses
public record Player(String name, int score) {}

Team team = new Team("Wildcats",
    List.of(
        new Player("Alice", 100),
        new Player("Bob", 85)
    ));

// Get all player names
List<String> names = OpticOps.getAll(
    team,
    TeamTraversals.players().andThen(PlayerLenses.name().asTraversal())
);
// Result: ["Alice", "Bob"]
```
~~~

### Setting Values

```java
// Set a single value (Lens)
Person updated = OpticOps.set(person, PersonLenses.age(), 30);

// Set all values (Traversal)
Team teamWithBonuses = OpticOps.setAll(
    team,
    TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
    100  // Everyone gets 100 points!
);
```

### Modifying Values

The `modify` operations are particularly powerful because they transform existing values rather than replacing them:

```java
// Modify a single value
Person olderPerson = OpticOps.modify(
    person,
    PersonLenses.age(),
    age -> age + 1
);

// Modify all values
Team teamWithDoubledScores = OpticOps.modifyAll(
    team,
    TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
    score -> score * 2
);
```

### Querying Data

These operations work with `Fold` and `Traversal` to query data without modification:

```java
// Check if any element matches
boolean hasHighScorer = OpticOps.exists(
    team,
    TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
    score -> score > 90
);

// Check if all elements match
boolean allPassed = OpticOps.all(
    team,
    TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
    score -> score >= 50
);

// Count elements
int playerCount = OpticOps.count(team, TeamTraversals.players());

// Check if empty
boolean noPlayers = OpticOps.isEmpty(team, TeamTraversals.players());

// Find first matching element
Optional<Player> topScorer = OpticOps.find(
    team,
    TeamTraversals.players(),
    player -> player.score() > 90
);
```

### Effectful Modifications

These are the most powerful operations, allowing modifications that can fail, accumulate errors, or execute asynchronously:

```java
// Modify with an effect (e.g., validation)
// Note: Error should be your application's error type (e.g., String, List<String>, or a custom error class)
Functor<Validated.Witness<Error>> validatedFunctor =
    ValidatedApplicative.instance(ErrorSemigroup.instance());

Validated<Error, Person> result = OpticOps.modifyF(
    person,
    PersonLenses.age(),
    age -> validateAge(age + 1),  // Returns Validated<Error, Integer>
    validatedFunctor
);

// Modify all with effects (e.g., async operations)
Applicative<CompletableFutureKind.Witness> cfApplicative =
    CompletableFutureMonad.instance();

CompletableFuture<Team> asyncResult = OpticOps.modifyAllF(
    team,
    TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
    score -> fetchBonusAsync(score),  // Returns CompletableFuture<Integer>
    cfApplicative
).thenApply(CompletableFutureKind::narrow);
```

---

## Part 2: Fluent Builders - Explicit and Discoverable

Fluent builders provide excellent IDE support through method chaining. They make the intent of your code crystal clear.

### The GetBuilder Pattern

```java
// Start with getting(source), then specify the optic
int age = OpticOps.getting(person).through(PersonLenses.age());

Optional<Address> addr = OpticOps.getting(person)
    .maybeThrough(PersonPrisms.homeAddress());

List<String> names = OpticOps.getting(team)
    .allThrough(TeamTraversals.playerNames());
```

### The SetBuilder Pattern

```java
// Start with setting(source), then specify optic and value
Person updated = OpticOps.setting(person)
    .through(PersonLenses.age(), 30);

Team updatedTeam = OpticOps.setting(team)
    .allThrough(
        TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
        100
    );
```

### The ModifyBuilder Pattern

```java
// Start with modifying(source), then specify optic and function
Person modified = OpticOps.modifying(person)
    .through(PersonLenses.age(), age -> age + 1);

Team modifiedTeam = OpticOps.modifying(team)
    .allThrough(
        TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
        score -> score * 2
    );

// Effectful modifications
Validated<Error, Person> result = OpticOps.modifying(person)
    .throughF(
        PersonLenses.age(),
        age -> validateAge(age + 1),
        validatedFunctor
    );
```

### The QueryBuilder Pattern

```java
// Start with querying(source), then specify checks
boolean hasHighScorer = OpticOps.querying(team)
    .anyMatch(
        TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
        score -> score > 90
    );

boolean allPassed = OpticOps.querying(team)
    .allMatch(
        TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
        score -> score >= 50
    );

Optional<Player> found = OpticOps.querying(team)
    .findFirst(TeamTraversals.players(), player -> player.score() > 90);

int count = OpticOps.querying(team)
    .count(TeamTraversals.players());

boolean empty = OpticOps.querying(team)
    .isEmpty(TeamTraversals.players());
```

---

## Part 2.5: Validation-Aware Modifications

~~~admonish tip title="Core Types Integration"
This section demonstrates **Phase 2** of the optics core types integration, which brings validation-aware modifications directly into `OpticOps`. These methods integrate seamlessly with higher-kinded-j's core types (`Either`, `Maybe`, `Validated`) to provide type-safe, composable validation workflows.
~~~

~~~admonish title="Comprehensive Example"
[FluentValidationExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/fluent/FluentValidationExample.java)
~~~

### Think of Validation-Aware Modifications Like...

- **A quality control checkpoint** 🔍 - Every modification must pass validation before being applied
- **Airport security screening** 🛂 - Some checks stop at the first issue (fast-track), others collect all problems (thorough inspection)
- **Form validation on a website** 📋 - You can show either the first error or all errors at once
- **Code review process** ✅ - Accumulate all feedback rather than stopping at the first comment

### The Challenge: Validation During Updates

Traditional optic operations assume modifications always succeed. But in real applications, updates often need validation:

```java
// ❌ Problem: No validation during modification
Person updated = OpticOps.modify(person, PersonLenses.age(), age -> age + 1);
// What if the new age is invalid? No way to handle errors!

// ❌ Problem: Manual validation is verbose and error-prone
int currentAge = OpticOps.get(person, PersonLenses.age());
if (currentAge + 1 >= 0 && currentAge + 1 <= 120) {
    person = OpticOps.set(person, PersonLenses.age(), currentAge + 1);
} else {
    // Handle error... but how do we return both success and failure?
}
```

**Validation-aware modifications** solve this by integrating validation directly into the optic operation, returning a result type that represents either success or failure.

### The Solution: Four Validation Strategies

`OpticOps` provides four complementary validation methods, each suited to different scenarios:

| Method | Core Type | Behaviour | Best For |
|--------|-----------|-----------|----------|
| `modifyEither` | `Either<E, S>` | Short-circuit on first error | Sequential validation, fail-fast workflows |
| `modifyMaybe` | `Maybe<S>` | Success or nothing (no error details) | Optional enrichment, silent failure |
| `modifyAllValidated` | `Validated<List<E>, S>` | Accumulate ALL errors | Form validation, comprehensive feedback |
| `modifyAllEither` | `Either<E, S>` | Stop at first error in collection | Performance-critical batch validation |

~~~admonish example title="Quick Comparison"
```java
// Same validation logic, different error handling strategies
Order order = new Order("ORD-123", List.of(
    new BigDecimal("-10.00"),    // Invalid: negative
    new BigDecimal("15000.00")   // Invalid: too high
));

// Strategy 1: Either - stops at FIRST error
Either<String, Order> result1 = OpticOps.modifyAllEither(
    order, orderPricesTraversal, price -> validatePrice(price)
);
// Result: Left("Price cannot be negative: -10.00")

// Strategy 2: Validated - collects ALL errors
Validated<List<String>, Order> result2 = OpticOps.modifyAllValidated(
    order, orderPricesTraversal, price -> validatePrice(price)
);
// Result: Invalid(["Price cannot be negative: -10.00",
//                  "Price exceeds maximum: 15000.00"])
```
~~~

### Static Method Style: Validation Operations

#### Single-Field Validation with `modifyEither`

Perfect for validating and modifying a single field where you want to fail fast with detailed error messages.

```java
@GenerateLenses
public record User(String username, String email, int age) {}

// Validate email format
Either<String, User> result = OpticOps.modifyEither(
    user,
    UserLenses.email(),
    email -> {
        if (email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            return Either.right(email);  // Valid
        } else {
            return Either.left("Invalid email format: " + email);  // Error
        }
    }
);

// Handle the result
result.fold(
    error -> {
        log.error("Validation failed: {}", error);
        return null;
    },
    validUser -> {
        log.info("User updated: {}", validUser.email());
        return null;
    }
);
```

#### Optional Validation with `modifyMaybe`

Useful when validation failure shouldn't produce error messages—either it works or it doesn't.

```java
// Trim and validate bio (silent failure if too long)
Maybe<User> result = OpticOps.modifyMaybe(
    user,
    UserLenses.bio(),
    bio -> {
        String trimmed = bio.trim();
        if (trimmed.length() <= 500) {
            return Maybe.just(trimmed);  // Success
        } else {
            return Maybe.nothing();  // Too long, fail silently
        }
    }
);

// Check if validation succeeded
if (result.isJust()) {
    User validUser = result.get();
    // Proceed with valid user
} else {
    // Validation failed, use fallback logic
}
```

#### Multi-Field Validation with Error Accumulation

The most powerful option: validate multiple fields and collect **all** validation errors, not just the first one.

```java
@GenerateTraversals
public record Order(String orderId, List<BigDecimal> itemPrices) {}

// Validate ALL prices and accumulate errors
Validated<List<String>, Order> result = OpticOps.modifyAllValidated(
    order,
    orderPricesTraversal,
    price -> {
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            return Validated.invalid("Price cannot be negative: " + price);
        } else if (price.compareTo(new BigDecimal("10000")) > 0) {
            return Validated.invalid("Price exceeds maximum: " + price);
        } else {
            return Validated.valid(price);  // Valid price
        }
    }
);

// Handle accumulated errors
result.fold(
    errors -> {
        System.out.println("Validation failed with " + errors.size() + " errors:");
        errors.forEach(error -> System.out.println("  - " + error));
        return null;
    },
    validOrder -> {
        System.out.println("All prices validated successfully!");
        return null;
    }
);
```

#### Multi-Field Validation with Short-Circuiting

When you have many fields to validate but want to stop at the first error (better performance, less detailed feedback):

```java
// Validate all prices, stop at FIRST error
Either<String, Order> result = OpticOps.modifyAllEither(
    order,
    orderPricesTraversal,
    price -> validatePrice(price)  // Returns Either<String, BigDecimal>
);

// Only the first error is reported
result.fold(
    firstError -> System.out.println("Failed: " + firstError),
    validOrder -> System.out.println("Success!")
);
```

### Fluent Builder Style: ModifyingWithValidation

The fluent API provides a dedicated builder for validation-aware modifications, making the intent even clearer:

```java
// Start with modifyingWithValidation(source), then choose validation strategy

// Single field with Either
Either<String, User> result1 = OpticOps.modifyingWithValidation(user)
    .throughEither(UserLenses.email(), email -> validateEmail(email));

// Single field with Maybe
Maybe<User> result2 = OpticOps.modifyingWithValidation(user)
    .throughMaybe(UserLenses.bio(), bio -> validateBio(bio));

// All fields with Validated (error accumulation)
Validated<List<String>, Order> result3 = OpticOps.modifyingWithValidation(order)
    .allThroughValidated(orderPricesTraversal, price -> validatePrice(price));

// All fields with Either (short-circuit)
Either<String, Order> result4 = OpticOps.modifyingWithValidation(order)
    .allThroughEither(orderPricesTraversal, price -> validatePrice(price));
```

### Real-World Scenario: User Registration

Let's see how to use validation-aware modifications for a complete user registration workflow:

```java
@GenerateLenses
public record UserRegistration(String username, String email, int age, String bio) {}

// Scenario: Sequential validation (stop at first error)
Either<String, UserRegistration> validateRegistration(UserRegistration form) {
    return OpticOps.modifyEither(form, UserLenses.username(), this::validateUsername)
        .flatMap(user -> OpticOps.modifyEither(user, UserLenses.email(), this::validateEmail))
        .flatMap(user -> OpticOps.modifyEither(user, UserLenses.age(), this::validateAge))
        .flatMap(user -> OpticOps.modifyEither(user, UserLenses.bio(), this::validateBio));
}

private Either<String, String> validateUsername(String username) {
    if (username.length() < 3) {
        return Either.left("Username must be at least 3 characters");
    }
    if (username.length() > 20) {
        return Either.left("Username must not exceed 20 characters");
    }
    if (!username.matches("^[a-zA-Z0-9_]+$")) {
        return Either.left("Username can only contain letters, numbers, and underscores");
    }
    return Either.right(username);
}

// Usage
validateRegistration(formData).fold(
    error -> {
        System.out.println("Registration failed: " + error);
        // Show error to user, stop processing
        return null;
    },
    validForm -> {
        System.out.println("Registration successful!");
        // Proceed with user creation
        return null;
    }
);
```

### Real-World Scenario: Bulk Data Import

When importing data, you often want to collect **all** validation errors to give comprehensive feedback:

```java
@GenerateTraversals
public record DataImport(List<String> emailAddresses, String importedBy) {}

// Validate all emails, accumulate ALL errors
Validated<List<String>, DataImport> validateImport(DataImport importData) {
    return OpticOps.modifyingWithValidation(importData)
        .allThroughValidated(
            DataImportTraversals.emailAddresses(),
            email -> {
                if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                    return Validated.invalid("Invalid email: " + email);
                } else {
                    return Validated.valid(email.toLowerCase().trim());  // Normalise
                }
            }
        );
}

// Usage
validateImport(importBatch).fold(
    errors -> {
        System.out.println("Import failed with " + errors.size() + " invalid emails:");
        errors.forEach(error -> System.out.println("  - " + error));
        // User can fix ALL errors at once
        return null;
    },
    validImport -> {
        System.out.println("Import successful! " +
                          validImport.emailAddresses().size() +
                          " emails validated.");
        return null;
    }
);
```

### When to Use Each Validation Strategy

#### Use `modifyEither` When:

✅ **Sequential workflows** where you want to stop at the first error
```java
// Login validation - stop at first failure
OpticOps.modifyEither(credentials, CredentialsLenses.username(), this::validateUsername)
    .flatMap(c -> OpticOps.modifyEither(c, CredentialsLenses.password(), this::checkPassword))
```

✅ **Single-field validation** with detailed error messages

✅ **Early exit is beneficial** (no point continuing if a critical field is invalid)

#### Use `modifyMaybe` When:

✅ **Optional enrichment** where failure is acceptable
```java
// Try to geocode address, but it's okay if it fails
OpticOps.modifyMaybe(order, OrderLenses.address(), addr -> geocodeAddress(addr))
```

✅ **Error details aren't needed** (just success/failure)

✅ **Silent failures are acceptable**

#### Use `modifyAllValidated` When:

✅ **Form validation** where users need to see all errors at once
```java
// Show all validation errors on a registration form
OpticOps.modifyAllValidated(form, formFieldsTraversal, this::validateField)
```

✅ **Comprehensive feedback is important**

✅ **User experience matters** (fixing all errors in one go)

#### Use `modifyAllEither` When:

✅ **Performance is critical** and you have many fields to validate

✅ **First error is sufficient** for debugging or logging

✅ **Resource-intensive validation** where stopping early saves time

### Comparison with Traditional `modifyF`

The validation methods simplify common patterns that previously required manual `Applicative` wiring:

**Before (using `modifyF`):**
```java
// Manual applicative construction with explicit error type conversion
Applicative<Validated.Witness<List<String>>> app =
    ValidatedApplicative.instance(ListSemigroup.instance());

Validated<List<String>, Order> result = OpticOps.modifyAllF(
    order,
    orderPricesTraversal,
    price -> {
        Validated<String, BigDecimal> validatedPrice = validatePrice(price);
        // Must convert error type from String to List<String>
        return ValidatedKindHelper.VALIDATED.widen(
            validatedPrice.bimap(List::of, Function.identity())
        );
    },
    app
).narrow();
```

**After (using `modifyAllValidated`):**
```java
// Clean, concise, and clear intent
Validated<List<String>, Order> result = OpticOps.modifyAllValidated(
    order,
    orderPricesTraversal,
    price -> validatePrice(price)
);
```

~~~admonish info title="When to Use modifyF"
The traditional `modifyF` methods are still valuable for:
- Custom effect types beyond `Either`, `Maybe`, and `Validated`
- Advanced applicative scenarios with custom combinators
- Asynchronous validation (e.g., `CompletableFuture`)
- Integration with third-party effect systems

For standard validation scenarios, the dedicated methods are clearer and more concise.
~~~

### Performance Considerations

- **`Either` short-circuiting**: Stops at first error, potentially faster for large collections
- **`Validated` accumulation**: Checks all elements, more work but better UX
- **`Maybe`**: Minimal overhead, just success/nothing
- **Object allocation**: All methods create new result objects (standard immutable pattern)

~~~admonish tip title="Optimisation Strategy"
For performance-critical code with large collections:
1. Use `modifyAllEither` if first-error is acceptable
2. Use `modifyAllValidated` if comprehensive errors are required
3. Consider pre-filtering with `Stream` API before validation
4. Cache compiled validators (e.g., compiled regex patterns)
~~~

### Integration with Existing Validation

Validation-aware modifications work seamlessly with existing validation libraries:

```java
// Jakarta Bean Validation integration
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;

Either<List<String>, User> validateWithJakarta(User user, Validator validator) {
    return OpticOps.modifyEither(
        user,
        UserLenses.email(),
        email -> {
            Set<ConstraintViolation<String>> violations =
                validator.validate(email);

            if (violations.isEmpty()) {
                return Either.right(email);
            } else {
                return Either.left(
                    violations.stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.toList())
                );
            }
        }
    );
}
```

---

## Part 3: Real-World Examples

### Example 1: E-Commerce Order Processing

```java
@GenerateLenses
@GenerateTraversals
public record Order(String orderId,
                    OrderStatus status,
                    List<OrderItem> items,
                    ShippingAddress address) {}

@GenerateLenses
public record OrderItem(String productId, int quantity, BigDecimal price) {}

@GenerateLenses
public record ShippingAddress(String street, String city, String postCode) {}

// Scenario: Apply bulk discount and update shipping
Order processOrder(Order order, BigDecimal discountPercent) {
    // Apply discount using fluent API
    Order discountedOrder = OpticOps.modifying(order)
        .allThrough(
            OrderTraversals.items().andThen(OrderItemLenses.price().asTraversal()),
            price -> price.multiply(BigDecimal.ONE.subtract(discountPercent))
        );

    // Update status using static method
    return OpticOps.set(
        discountedOrder,
        OrderLenses.status(),
        OrderStatus.PROCESSING
    );
}
```

### Example 2: Validation with Error Accumulation

```java
// Using Validated to accumulate all validation errors
Validated<List<String>, Order> validateOrder(Order order) {
    Applicative<Validated.Witness<List<String>>> applicative =
        ValidatedApplicative.instance(ListSemigroup.instance());

    // Validate all item quantities
    return OpticOps.modifyAllF(
        order,
        OrderTraversals.items().andThen(OrderItemLenses.quantity().asTraversal()),
        qty -> {
            if (qty > 0 && qty <= 1000) {
                return Validated.valid(qty);
            } else {
                return Validated.invalid(List.of(
                    "Quantity must be between 1 and 1000, got: " + qty
                ));
            }
        },
        applicative
    ).narrow();
}
```

### Example 3: Async Database Updates

```java
// Using CompletableFuture for async operations
CompletableFuture<Team> updatePlayerScoresAsync(
    Team team,
    Function<Player, CompletableFuture<Integer>> fetchNewScore
) {
    Applicative<CompletableFutureKind.Witness> cfApplicative =
        CompletableFutureMonad.instance();

    return OpticOps.modifyAllF(
        team,
        TeamTraversals.players(),
        player -> fetchNewScore.apply(player)
            .thenApply(newScore ->
                OpticOps.set(player, PlayerLenses.score(), newScore)
            )
            .thenApply(CompletableFutureKind::of),
        cfApplicative
    ).thenApply(kind -> CompletableFutureKind.narrow(kind).join());
}
```

---

## When to Use Each Style

### Use Static Methods When:

✅ **Performing simple, one-off operations**
```java
// Clear and concise
String name = OpticOps.get(person, PersonLenses.name());
```

✅ **Chaining is not needed**
```java
// Direct transformation
Person older = OpticOps.modify(person, PersonLenses.age(), a -> a + 1);
```

✅ **Performance is critical** (slightly less object allocation)

### Use Fluent Builders When:

✅ **Building complex workflows**
```java
import static java.util.stream.Collectors.toList;

// Clear intent at each step
return OpticOps.getting(order)
    .allThrough(OrderTraversals.items())
    .stream()
    .filter(item -> item.quantity() > 10)
    .map(OrderItem::productId)
    .collect(toList());
```

✅ **IDE autocomplete is important** (great for discovery)

✅ **Code reviews matter** (explicit intent)

✅ **Teaching or documentation** (self-explanatory)

---

## Common Patterns and Idioms

### Pattern 1: Pipeline Transformations

```java
// Sequential transformations for multi-step pipeline
// Note: Result and Data should be your application's domain types with appropriate lenses
Result processData(Data input) {
    Data afterStage1 = OpticOps.modifying(input)
        .through(DataLenses.stage1(), this::transformStage1);

    Data afterStage2 = OpticOps.modifying(afterStage1)
        .through(DataLenses.stage2(), this::transformStage2);

    return OpticOps.modifying(afterStage2)
        .through(DataLenses.stage3(), this::transformStage3);
}
```

### Pattern 2: Conditional Updates

```java
// Static style for simple conditionals
Person updateIfAdult(Person person) {
    int age = OpticOps.get(person, PersonLenses.age());
    return age >= 18
        ? OpticOps.set(person, PersonLenses.status(), "ADULT")
        : person;
}
```

### Pattern 3: Bulk Operations with Filtering

```java
// Combine both styles for clarity
Team updateTopPerformers(Team team, int threshold) {
    // Use fluent for query
    List<Player> topPerformers = OpticOps.querying(team)
        .allThrough(TeamTraversals.players())
        .stream()
        .filter(p -> p.score() >= threshold)
        .toList();

    // Use static for transformation
    return OpticOps.modifyAll(
        team,
        TeamTraversals.players(),
        player -> topPerformers.contains(player)
            ? OpticOps.set(player, PlayerLenses.status(), "STAR")
            : player
    );
}
```

---

## Performance Considerations

### Object Allocation

- **Static methods**: Minimal allocation (just the result)
- **Fluent builders**: Create intermediate builder objects
- **Impact**: Negligible for most applications; avoid in tight loops

### Optic Composition

Both styles benefit from composing optics once and reusing them:

```java
// ✅ Good: Compose once, use many times
Lens<Order, BigDecimal> orderToTotalPrice =
    OrderTraversals.items()
        .andThen(OrderItemLenses.price().asTraversal())
        .andThen(someAggregationLens);

orders.stream()
    .map(order -> OpticOps.getAll(order, orderToTotalPrice))
    .collect(toList());

// ❌ Avoid: Recomposing in loop
orders.stream()
    .map(order -> OpticOps.getAll(
        order,
        OrderTraversals.items()
            .andThen(OrderItemLenses.price().asTraversal())  // Recomposed each time!
    ))
    .collect(toList());
```

---

## Integration with Existing Java Code

### Working with Streams

```java
// Optics integrate naturally with Stream API
List<String> highScorerNames = OpticOps.getting(team)
    .allThrough(TeamTraversals.players())
    .stream()
    .filter(p -> p.score() > 90)
    .map(p -> OpticOps.get(p, PlayerLenses.name()))
    .collect(toList());
```

### Working with Optional

```java
// Optics and Optional work together
Optional<Person> maybePerson = findPerson(id);

Optional<Integer> age = maybePerson
    .map(p -> OpticOps.get(p, PersonLenses.age()));

Person updated = maybePerson
    .map(p -> OpticOps.modify(p, PersonLenses.age(), a -> a + 1))
    .orElse(new Person("Default", 0, "UNKNOWN"));
```

---

## Common Pitfalls

### ❌ Don't: Call `get` then `set`

```java
// Inefficient - two traversals
int age = OpticOps.get(person, PersonLenses.age());
Person updated = OpticOps.set(person, PersonLenses.age(), age + 1);
```

### ✅ Do: Use `modify`

```java
// Efficient - single traversal
Person updated = OpticOps.modify(person, PersonLenses.age(), a -> a + 1);
```

### ❌ Don't: Recompose optics unnecessarily

```java
// Bad - composing in a loop
for (Order order : orders) {
    var itemPrices = OrderTraversals.items()
        .andThen(OrderItemLenses.price().asTraversal());  // Composed each iteration!
    process(OpticOps.getAll(order, itemPrices));
}
```

### ✅ Do: Compose once, reuse

```java
// Good - compose outside loop
var itemPrices = OrderTraversals.items()
    .andThen(OrderItemLenses.price().asTraversal());

for (Order order : orders) {
    process(OpticOps.getAll(order, itemPrices));
}
```

---

## Further Reading

- **Fluent Interfaces**: [Martin Fowler's article](https://martinfowler.com/bliki/FluentInterface.html) on designing fluent APIs
- **Readable and Expressive APIs**: [Fluent Interfaces in Java](https://samurai-developer.com/fluent-interfaces/)
- **Fluent API**: [Practice and Theory](https://blog.sigplan.org/2021/03/02/fluent-api-practice-and-theory/)
- **Lens Tutorial**: [Haskell lens tutorial](https://hackage.haskell.org/package/lens-tutorial) for deeper theoretical understanding

---

**Next Steps:**

- [Free Monad DSL for Optics](free_monad_dsl.md) - Build composable programs
- [Optic Interpreters](interpreters.md) - Multiple execution strategies
- [Advanced Patterns](composing_optics.md) - Complex real-world scenarios
