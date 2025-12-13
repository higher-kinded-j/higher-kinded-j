# Composition Patterns

This chapter covers patterns for composing Effect Paths: sequential composition, independent combination, debugging, and error handling strategies.

~~~admonish info title="What You'll Learn"
- Sequential composition with `via` and `then` for dependent computations
- Independent combination with `zipWith` for parallel-style composition
- Debugging techniques using `peek` and logging patterns
- Error handling strategies: recovery, transformation, and fallback chains
~~~

## Sequential Composition with `via`

The `via` method chains computations where each step depends on the previous result:

```java
// User → Cart → Total → Invoice
EitherPath<Error, Invoice> pipeline =
    Path.either(findUser(userId))
        .via(user -> Path.either(getCart(user)))
        .via(cart -> Path.either(calculateTotal(cart)))
        .via(total -> Path.either(createInvoice(total)));
```

### Error Short-Circuiting

When using `via`, the first error stops the chain:

```java
EitherPath<String, String> result =
    Path.right("start")
        .via(s -> Path.left("error here"))    // Fails
        .via(s -> Path.right(s + " never"))   // Never executes
        .via(s -> Path.right(s + " reached")); // Never executes

// result.run() → Left("error here")
```

### The `then` Method

Use `then` when you need sequencing but don't need the previous value:

```java
IOPath<Result> workflow =
    Path.io(() -> log("Starting"))
        .then(() -> Path.io(() -> initialize()))
        .then(() -> Path.io(() -> process()))
        .then(() -> Path.io(() -> log("Done")));
```

---

## Independent Combination with `zipWith`

Use `zipWith` when computations are independent (neither needs the other's result):

```java
// These validations don't depend on each other
EitherPath<String, String> name = validateName(input.name());
EitherPath<String, String> email = validateEmail(input.email());
EitherPath<String, Integer> age = validateAge(input.age());

// Combine all three
EitherPath<String, User> user = name.zipWith3(email, age, User::new);
```

### Fail-Fast Behavior

With EitherPath and standard zipWith, the first error is returned:

```java
EitherPath<String, String> invalid1 = Path.left("Error 1");
EitherPath<String, String> invalid2 = Path.left("Error 2");
EitherPath<String, String> valid = Path.right("ok");

EitherPath<String, String> result = invalid1.zipWith(invalid2, (a, b) -> a + b);
// result.run() → Left("Error 1")
```

~~~admonish tip title="Accumulating All Errors"
Need to collect *all* validation errors rather than stopping at the first? Use `ValidationPath` with `zipWithAccum`:

```java
ValidationPath<List<String>, User> user = nameV.zipWith3Accum(emailV, ageV, User::new);
// Returns ALL errors: ["Name too short", "Invalid email", "Age negative"]
```

See [ValidationPath](path_types.md#validationpath) for the complete error accumulation API.
~~~

### zipWith Variants

```java
// Combine 2 values
pathA.zipWith(pathB, (a, b) -> combine(a, b))

// Combine 3 values
pathA.zipWith3(pathB, pathC, (a, b, c) -> combine(a, b, c))

// Combine 4 values
pathA.zipWith4(pathB, pathC, pathD, (a, b, c, d) -> combine(a, b, c, d))
```

---

## Mixed Composition

Real-world code often mixes sequential and parallel composition:

```java
EitherPath<Error, Order> createOrder(OrderInput input) {
    // Step 1: Validate all fields independently (parallel)
    EitherPath<Error, String> name = validateName(input.name());
    EitherPath<Error, String> email = validateEmail(input.email());
    EitherPath<Error, Address> address = validateAddress(input.address());

    EitherPath<Error, CustomerInfo> customerInfo =
        name.zipWith3(email, address, CustomerInfo::new);

    // Step 2: Sequential - need customer to check inventory
    return customerInfo
        .via(info -> Path.either(checkInventory(input.items())))
        .via(items -> Path.either(calculatePricing(items)))
        .via(pricing -> Path.either(createOrder(customerInfo.run().getRight(), pricing)));
}
```

---

## Debugging with `peek`

Effect chains can be frustrating to debug. When something fails partway through a pipeline, the error propagates silently to the end. You know *that* it failed, but not *where*. Traditional debugging techniques like print statements would break the chain, and debugger breakpoints can be awkward with lambdas.

The `peek` method solves this by letting you observe values as they flow through the pipeline without disrupting the computation.

### The `peek` Method

`peek` executes a side effect without changing the value:

```java
EitherPath<Error, User> result =
    Path.either(validateInput(input))
        .peek(valid -> log.debug("Input validated: {}", valid))
        .via(valid -> Path.either(createUser(valid)))
        .peek(user -> log.info("User created: {}", user.getId()))
        .via(user -> Path.either(sendWelcomeEmail(user)))
        .peek(email -> log.debug("Welcome email sent"));
```

For error paths, `peek` only executes on success:

```java
Path.left("error")
    .peek(v -> log.info("Value: {}", v))  // Never executes
    .map(String::toUpperCase);
```

### Debugging Pipelines

Create a helper for detailed logging:

```java
<A> EitherPath<Error, A> debug(EitherPath<Error, A> path, String step) {
    return path.peek(v -> log.debug("[{}] Success: {}", step, v));
}

EitherPath<Error, Invoice> pipeline =
    debug(Path.either(findUser(id)), "findUser")
        .via(user -> debug(Path.either(getCart(user)), "getCart"))
        .via(cart -> debug(Path.either(checkout(cart)), "checkout"));
```

---

## Error Handling Patterns

Not every error should halt processing. Sometimes a failure is expected and you have a sensible fallback. Sometimes you want to transform the error into something more meaningful for the caller. Sometimes you want to try several approaches before giving up.

The Path API provides a toolkit for these scenarios. Each pattern addresses a different question: "What should happen when this operation fails?"

### Pattern 1: Recover with Default

**The problem:** An operation might fail, but you have a reasonable fallback value that lets processing continue.

**The solution:**

```java
MaybePath<Config> config = Path.maybe(loadConfig())
    .orElse(() -> Path.just(Config.defaults()));

EitherPath<Error, User> user = Path.either(findUser(id))
    .recover(error -> User.guest());
```

### Pattern 2: Transform and Rethrow

**The problem:** Low-level errors (API failures, database exceptions) leak implementation details to callers. You need errors that make sense at your abstraction level.

**The solution:**

```java
EitherPath<ServiceError, Data> result =
    Path.either(externalApi.fetch())
        .mapError(apiError -> new ServiceError("API failed", apiError));
```

This wraps the raw API error in a domain-appropriate type while preserving the original cause for debugging.

### Pattern 3: Retry Logic

**The problem:** Network calls and external services fail transiently. A single failure should not necessarily mean permanent failure.

**The solution:**

```java
EitherPath<Error, Data> withRetry(Supplier<Either<Error, Data>> operation, int maxRetries) {
    EitherPath<Error, Data> result = Path.either(operation.get());

    for (int i = 0; i < maxRetries && result.run().isLeft(); i++) {
        result = Path.either(operation.get());
    }

    return result;
}
```

### Pattern 4: Fallback Chain

**The problem:** You have multiple sources for the same data, each with different trade-offs. Try the preferred source first, then fall back to alternatives.

**The solution:**

```java
EitherPath<Error, Config> config =
    Path.either(loadFromFile())
        .recoverWith(e1 -> Path.either(loadFromEnv()))
        .recoverWith(e2 -> Path.either(loadFromDefaults()));
```

Each `recoverWith` only triggers if the previous step failed. The first successful result short-circuits the chain.

### Pattern 5: Error Accumulation

**The problem:** Standard short-circuit behaviour means users only see one error at a time. For form validation, you want to show *all* problems at once.

**The solution:** Use `ValidationPath` with `zipWithAccum`:

```java
ValidationPath<List<String>, User> validateUser(Input input) {
    return validateName(input.name())
        .zipWith3Accum(
            validateEmail(input.email()),
            validateAge(input.age()),
            User::new
        );
}
// If name, email, and age all fail, returns ALL THREE errors
```

See [ValidationPath](path_types.md#validationpath) for the complete error accumulation API.

~~~admonish note title="Manual Accumulation"
Before `ValidationPath`, you would need to manually collect errors into a list and check at the end. This works but loses the compositional benefits of the Path API. Prefer `ValidationPath.zipWithAccum` for new code.
~~~

---

## Conversion Between Paths

Convert between path types as your needs change:

### MaybePath → EitherPath

```java
MaybePath<User> maybeUser = Path.maybe(findUser(id));
EitherPath<String, User> eitherUser = maybeUser.toEitherPath("User not found");
```

### TryPath → EitherPath

```java
TryPath<Config> tryConfig = Path.tryOf(() -> loadConfig());
EitherPath<Throwable, Config> eitherConfig = tryConfig.toEitherPath(ex -> ex);

// With error transformation
EitherPath<String, Config> withMessage = tryConfig.toEitherPath(Throwable::getMessage);
```

### TryPath → MaybePath

```java
TryPath<Integer> parsed = Path.tryOf(() -> Integer.parseInt(input));
MaybePath<Integer> maybeInt = parsed.toMaybePath();  // Nothing on failure
```

### IOPath → TryPath

```java
IOPath<Data> io = Path.io(() -> fetchData());
TryPath<Data> tryData = io.toTryPath();  // Executes the IO and captures result
```

---

## Practical Example: Service Layer

```java
public class OrderService {
    private final UserRepository users;
    private final InventoryService inventory;
    private final PaymentService payments;

    public EitherPath<OrderError, Order> placeOrder(OrderRequest request) {
        // Validate request
        return validateRequest(request)
            // Get user (convert Maybe → Either)
            .via(valid -> Path.maybe(users.findById(valid.userId()))
                .toEitherPath(() -> new OrderError.UserNotFound(valid.userId())))
            // Check inventory
            .via(user -> Path.either(inventory.checkAvailability(request.items()))
                .mapError(OrderError.InventoryError::new))
            // Process payment
            .via(available -> Path.either(payments.charge(user, available.total()))
                .mapError(OrderError.PaymentError::new))
            // Create order
            .via(payment -> Path.either(createOrder(user, request.items(), payment)));
    }

    private EitherPath<OrderError, ValidatedRequest> validateRequest(OrderRequest request) {
        if (request.items().isEmpty()) {
            return Path.left(new OrderError.EmptyCart());
        }
        return Path.right(new ValidatedRequest(request));
    }
}
```

---

## Summary

| Pattern | Method | Use Case |
|---------|--------|----------|
| Sequential | `via` | Each step depends on previous |
| Sequential (ignore value) | `then` | Sequencing, don't need result |
| Independent | `zipWith` | Combine independent computations |
| Accumulate errors | `zipWithAccum` | Collect all validation errors |
| Debug | `peek` | Logging without changing value |
| Default value | `recover` | Provide fallback on error |
| Error transform | `mapError` | Change error type |
| Fallback chain | `recoverWith` | Try alternatives |
| Type conversion | `toEitherPath` etc. | Change path type |

Continue to [Type Conversions](conversions.md) for detailed conversion patterns.

~~~admonish tip title="See Also"
- [Monad](../functional/monad.md) - The type class behind `via` and `flatMap`
- [Applicative](../functional/applicative.md) - The type class behind `zipWith`
- [For Comprehension](../functional/for_comprehension.md) - Alternative syntax for monadic composition
~~~

---

**Previous:** [Path Types](path_types.md)
**Next:** [Type Conversions](conversions.md)
