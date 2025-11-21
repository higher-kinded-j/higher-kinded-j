# Working with Core Types and Optics

![Diagram illustrating optics integration with functional core types like Maybe, Either, and Validated](../images/optics.jpg)

As you've learnt from the previous chapters, optics provide a powerful way to focus on and modify immutable data structures. But what happens when the data you're working with is wrapped in Higher-Kinded-J's core types—`Maybe`, `Either`, `Validated`, or `Try`?

Traditional optics work brilliantly with straightforward, deterministic data. However, real-world applications rarely deal with such certainty. Fields might be `null`, operations might fail, validation might produce errors, and database calls might throw exceptions. Handling these scenarios whilst maintaining clean, composable optics code requires a bridge between these two powerful abstractions.

This is where **Core Type Integration** comes in.

## The Challenge

Consider a typical scenario: updating a user profile where some fields are optional, validation might fail, and the database operation might throw an exception.

~~~admonish failure title="❌ The Traditional Approach"
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

~~~admonish success title="✅ The Functional Approach"
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

## Three Complementary Approaches

Higher-Kinded-J provides three integrated solutions for working with core types and optics:

~~~admonish note title="💡 Using with the Fluent API"
All the extension methods shown here can also be accessed through Higher-Kinded-J's [Fluent API](fluent_api.md), which provides a more Java-friendly syntax for optic operations. The examples below use static imports for conciseness, but you can also use `OpticOps` methods for a more discoverable API.
~~~

### 1. **Core Type Prisms** 🔬 — Pattern Matching on Functional Types

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

[Learn more →](core_type_prisms.md)

### 2. **Lens Extensions** 🛡️ — Safety Rails for Lens Operations

Augment lenses with built-in null safety, validation, and exception handling.

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

// Exception-safe database operation
Try<User> saved = modifyTry(
    emailLens,
    email -> Try.of(() -> updateInDatabase(email)),
    user
);
```

**Best for:** Individual field operations with validation, null-safe access, exception handling.

[Learn more →](lens_extensions.md)

### 3. **Traversal Extensions** 🗺️ — Bulk Operations with Error Handling

Process collections using traversals whilst accumulating errors or failing fast.

```java
Traversal<List<Order>, BigDecimal> allPrices =
    Traversals.forList().andThen(OrderLenses.price().asTraversal());

// Validate all prices (accumulate errors)
Validated<List<String>, List<Order>> result = modifyAllValidated(
    allPrices,
    price -> validatePrice(price),
    orders
);

// Or fail fast at first error
Either<String, List<Order>> fastResult = modifyAllEither(
    allPrices,
    price -> validatePrice(price),
    orders
);
```

**Best for:** Bulk validation, batch processing, error accumulation vs fail-fast strategies.

[Learn more →](traversal_extensions.md)

## When to Use Each Approach

### Use **Core Type Prisms** when:
- ✅ Extracting values from `Maybe`, `Either`, `Validated`, or `Try`
- ✅ Pattern matching on functional types without `instanceof`
- ✅ Composing core types with other optics for deep navigation
- ✅ Safely handling optional API responses or database results

### Use **Lens Extensions** when:
- ✅ Accessing potentially null fields
- ✅ Validating single field updates
- ✅ Performing operations that might throw exceptions
- ✅ Implementing form validation with immediate feedback

### Use **Traversal Extensions** when:
- ✅ Validating collections of data
- ✅ Batch processing with error accumulation
- ✅ Applying bulk updates with validation
- ✅ Counting valid items or collecting errors

## The Power of Composition

The real magic happens when you combine these approaches:

```java
// Complete order processing pipeline
Order order = ...;

// 1. Extract customer using prism (Maybe)
Prism<Maybe<Customer>, Customer> justPrism = Prisms.just();
Maybe<Customer> maybeCustomer = order.getCustomer();

// 2. Validate customer email using lens extension
Lens<Customer, String> emailLens = CustomerLenses.email();
Either<String, Customer> validatedCustomer =
    maybeCustomer.map(customer ->
        modifyEither(emailLens, email -> validateEmail(email), customer)
    ).orElse(Either.left("No customer"));

// 3. Validate all order items using traversal extension
Traversal<List<OrderItem>, BigDecimal> allPrices =
    Traversals.forList().andThen(OrderItemLenses.price().asTraversal());

Validated<List<String>, List<OrderItem>> validatedItems =
    modifyAllValidated(
        allPrices,
        price -> validatePrice(price),
        order.getItems()
    );

// Combine results...
```

## Real-World Examples

All three approaches are demonstrated with comprehensive, runnable examples:

~~~admonish example title="Example Code"
- [CoreTypePrismsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/CoreTypePrismsExample.java) — API response processing
- [LensExtensionsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/LensExtensionsExample.java) — User profile validation
- [TraversalExtensionsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/TraversalExtensionsExample.java) — Bulk order processing
- [IntegrationPatternsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/IntegrationPatternsExample.java) — Complete e-commerce workflow
~~~

## Key Benefits

### 🎯 **Separation of Concerns**
Business logic, validation, and error handling remain cleanly separated. Optics define the structure, core types handle the effects.

### 🔄 **Composability**
All three approaches compose seamlessly with each other and with standard optics operations.

### 📊 **Error Accumulation**
Choose between fail-fast (stop at first error) or error accumulation (collect all errors) based on your requirements.

### 🛡️ **Type Safety**
The compiler ensures you handle all cases. No silent failures, no unexpected nulls.

### 📖 **Readability**
Code reads like the business logic it implements, without defensive programming clutter.

## Understanding the Core Types

Before diving into the integration patterns, ensure you're familiar with Higher-Kinded-J's core types:

- [**Maybe**](../monads/maybe_monad.md) — Represents optional values (similar to `Optional`)
- [**Either**](../monads/either_monad.md) — Represents a value that can be one of two types (success or error)
- [**Validated**](../monads/validated_monad.md) — Like `Either`, but accumulates errors
- [**Try**](../monads/try_monad.md) — Represents a computation that may throw an exception

## Common Pitfalls

~~~admonish warning title="⚠️ Don't Mix Effect Types Carelessly"
Whilst all three core type families work with optics, mixing them inappropriately can lead to confusing code:

```java
// ❌ Confusing: Mixing Maybe and Either unnecessarily
Maybe<Either<String, User>> confusing = ...;

// ✅ Better: Choose one based on your needs
Either<String, User> clear = ...; // If you have an error message
Maybe<User> simple = ...;          // If it's just presence/absence
```
~~~

~~~admonish tip title="💡 Start with Either"
When in doubt, start with `Either`. It's the most versatile:
- Carries error information (unlike `Maybe`)
- Fails fast (unlike `Validated`)
- Doesn't catch exceptions automatically (unlike `Try`)

You can always switch to `Validated` for error accumulation or `Try` for exception handling when needed.
~~~

## Next Steps

Now that you understand the three complementary approaches, dive into each one:

1. **[Core Type Prisms](core_type_prisms.md)** — Start here to learn safe extraction
2. **[Lens Extensions](lens_extensions.md)** — Master validated field operations
3. **[Traversal Extensions](traversal_extensions.md)** — Handle bulk operations

Each guide includes detailed examples, best practices, and common patterns you'll use every day.

---

**Next:** [Core Type Prisms: Safe Extraction](core_type_prisms.md)
