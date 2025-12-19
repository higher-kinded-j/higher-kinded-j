# Composition Patterns

> *"The world, that understandable and lawful world, was slipping away."*
>
> — William Golding, *Lord of the Flies*

Golding's boys lost their grip on order gradually, one compromised rule at a
time. Code works the same way. A null check here, an uncaught exception there,
a boolean flag that means three different things depending on context, and
suddenly your "understandable and lawful" service layer has become something
you approach with trepidation.

Composition is how you hold the line. Each pattern in this chapter is a way
of connecting operations so that failures propagate predictably, successes
build on each other, and the logic remains visible even as complexity grows.

~~~admonish info title="What You'll Learn"
- Sequential composition with `via` and `then` for dependent computations
- Independent combination with `zipWith` for parallel-style composition
- Debugging techniques using `peek` and logging patterns
- Error handling strategies: recovery, transformation, and fallback chains
- When to mix composition styles, and how to do it cleanly
~~~

---

## Sequential Composition: _One Thing After Another_

The `via` method chains computations where each step depends on the previous
result. It's the workhorse of effect composition.

```java
EitherPath<Error, Invoice> pipeline =
    Path.either(findUser(userId))
        .via(user -> Path.either(getCart(user)))
        .via(cart -> Path.either(calculateTotal(cart)))
        .via(total -> Path.either(createInvoice(total)));
```

Each `via` receives the success value and returns a new Path. The railway
model applies: travel along the success track until something fails, then
skip to the end.

### Short-Circuiting

When a step fails, subsequent steps don't execute:

```java
EitherPath<String, String> result =
    Path.right("start")
        .via(s -> Path.left("failed here"))     // Fails
        .via(s -> Path.right(s + " never"))     // Skipped
        .via(s -> Path.right(s + " reached"));  // Skipped

// result.run() → Left("failed here")
```

This isn't just convenient; it's essential. Without short-circuiting, you'd
need defensive checks at every step. The Path handles it structurally.

### `then`: Sequencing Without the Value

Sometimes you need sequencing but don't care about the previous result:

```java
IOPath<Result> workflow =
    Path.io(() -> log.info("Starting"))
        .then(() -> Path.io(() -> initialise()))
        .then(() -> Path.io(() -> process()))
        .then(() -> Path.io(() -> log.info("Done")));
```

`then` discards the previous value and runs the next computation. Use it for
side effects that must happen in order but don't pass data forward.

---

## Independent Combination: _All Together Now_

`zipWith` combines computations that don't depend on each other. Neither
needs the other's result to proceed.

```java
EitherPath<String, String> name = validateName(input.name());
EitherPath<String, String> email = validateEmail(input.email());
EitherPath<String, Integer> age = validateAge(input.age());

EitherPath<String, User> user = name.zipWith3(email, age, User::new);
```

If all three succeed, `User::new` receives the values. If any fails, the
first failure propagates.

### The Difference Matters

This distinction trips people up, so let's be explicit:

| Operation | What It Expresses |
|-----------|-------------------|
| `via` | "Do this, **then** use the result to decide what's next" |
| `zipWith` | "Do these **independently**, then combine the results" |

```java
// WRONG: using via when computations are independent
Path.right(validateName(input))
    .via(name -> Path.right(validateEmail(input)))  // Doesn't use name!
    .via(email -> Path.right(validateAge(input)));  // Doesn't use email!

// RIGHT: using zipWith for independent computations
validateName(input).zipWith3(
    validateEmail(input),
    validateAge(input),
    User::new
);
```

The first version works but misleads readers into thinking there's a
dependency. The second says what it means.

### Variants

```java
// Two values
pathA.zipWith(pathB, (a, b) -> combine(a, b))

// Three values
pathA.zipWith3(pathB, pathC, (a, b, c) -> combine(a, b, c))

// Four values
pathA.zipWith4(pathB, pathC, pathD, (a, b, c, d) -> combine(a, b, c, d))
```

Beyond four, consider whether your design is asking too much of a single
expression.

---

## Mixed Composition: The Real World

Production code rarely uses just one pattern. You validate independently,
then sequence dependent operations, then combine more independent work.
The key is clarity about which pattern you're using where.

```java
EitherPath<Error, Order> createOrder(OrderInput input) {
    // Phase 1: Independent validation
    EitherPath<Error, String> name = validateName(input.name());
    EitherPath<Error, String> email = validateEmail(input.email());
    EitherPath<Error, Address> address = validateAddress(input.address());

    EitherPath<Error, CustomerInfo> customer =
        name.zipWith3(email, address, CustomerInfo::new);

    // Phase 2: Sequential operations that depend on customer
    return customer
        .via(info -> Path.either(checkInventory(input.items())))
        .via(inventory -> Path.either(calculatePricing(inventory)))
        .via(pricing -> Path.either(createOrder(customer, pricing)));
}
```

The phases are distinct: independent validation first, then a sequential
pipeline that threads through the validated data. Readers can see the
structure at a glance.

---

## Parallel Composition

> *"The machine didn't think about one thing at a time. It thought about
> many things, all at once, in parallel streams that only converged when
> they had to."*
>
> — Neal Stephenson, *Cryptonomicon*

Sequential composition with `via` is appropriate when each step depends on
the previous. But when computations are genuinely independent, running them
in parallel can dramatically reduce total execution time.

### Expressing Parallelism with parZipWith

`parZipWith` is `zipWith` with explicit parallel execution:

```java
IOPath<User> fetchUser = IOPath.delay(() -> userService.get(id));
IOPath<Preferences> fetchPrefs = IOPath.delay(() -> prefService.get(id));

// Sequential: ~200ms (100ms + 100ms)
IOPath<Profile> sequential = fetchUser.zipWith(fetchPrefs, Profile::new);

// Parallel: ~100ms (max of both)
IOPath<Profile> parallel = fetchUser.parZipWith(fetchPrefs, Profile::new);
```

The operations are the same; the execution strategy differs. Use `parZipWith`
when you want to make the parallel intent explicit.

### N-ary Parallel Composition

For three or four independent paths, use `PathOps` utilities:

```java
IOPath<Dashboard> dashboard = PathOps.parZip3(
    fetchMetrics(),
    fetchAlerts(),
    fetchUsers(),
    Dashboard::new
);

IOPath<Report> report = PathOps.parZip4(
    fetchSales(),
    fetchInventory(),
    fetchCustomers(),
    fetchTrends(),
    Report::new
);
```

### List Parallelism with parSequenceIO

When you have a dynamic number of independent operations:

```java
List<IOPath<Product>> fetches = productIds.stream()
    .map(id -> IOPath.delay(() -> productService.get(id)))
    .toList();

// All fetches run concurrently
IOPath<List<Product>> products = PathOps.parSequenceIO(fetches);
```

### Racing Computations

Sometimes you want whichever completes first:

```java
IOPath<Config> primary = IOPath.delay(() -> fetchFromPrimary());
IOPath<Config> backup = IOPath.delay(() -> fetchFromBackup());

// Returns whichever config arrives first
IOPath<Config> fastest = primary.race(backup);
```

### Sequential vs Parallel: The Decision

| Scenario | Use |
|----------|-----|
| B needs A's result | `via` (sequential) |
| A and B independent, need both | `parZipWith` |
| 3-4 independent operations | `parZip3`, `parZip4` |
| List of independent operations | `parSequenceIO` |
| Want fastest of alternatives | `race` |

The wrong choice doesn't break correctness—just performance. When in doubt,
prefer sequential; parallelise when profiling shows it matters.

~~~admonish tip title="See Also"
See [Advanced Effect Topics](advanced_topics.md) for comprehensive coverage of
parallel execution patterns including `parSequenceFuture` and `raceIO`.
~~~

---

## Debugging with `peek`

Effect chains can frustrate debugging. When something fails mid-pipeline,
you know *that* it failed but not *where*. Traditional print debugging
would break the chain. Debugger breakpoints are awkward with lambdas.

`peek` solves this by letting you observe values without disrupting the flow:

```java
EitherPath<Error, User> result =
    Path.either(validateInput(input))
        .peek(valid -> log.debug("Validated: {}", valid))
        .via(valid -> Path.either(createUser(valid)))
        .peek(user -> log.info("Created user: {}", user.getId()))
        .via(user -> Path.either(sendWelcomeEmail(user)))
        .peek(email -> log.debug("Email sent"));
```

`peek` only executes on the success track. Failures pass through silently,
which is usually what you want when tracing the happy path.

### A Debugging Helper

For detailed tracing, wrap the pattern:

```java
<A> EitherPath<Error, A> traced(EitherPath<Error, A> path, String step) {
    return path.peek(v -> log.debug("[{}] → {}", step, v));
}

EitherPath<Error, Invoice> pipeline =
    traced(Path.either(findUser(id)), "findUser")
        .via(user -> traced(Path.either(getCart(user)), "getCart"))
        .via(cart -> traced(Path.either(checkout(cart)), "checkout"));
```

When something goes wrong, the logs show exactly how far you got.

---

## Error Handling Strategies

Not every error should halt processing. Sometimes you have a sensible
fallback. Sometimes you need to transform the error for the next layer.
Sometimes you want to try several approaches before giving up.

### Strategy 1: _Recover with a Default_

The operation might fail, but you have a reasonable fallback:

```java
MaybePath<Config> config = Path.maybe(loadConfig())
    .orElse(() -> Path.just(Config.defaults()));

EitherPath<Error, User> user = Path.either(findUser(id))
    .recover(error -> User.guest());
```

Use this when the fallback is genuinely acceptable, not when you're
papering over problems you should be handling properly.

### Strategy 2: _Transform the Error_

Low-level errors leak implementation details. Transform them at layer
boundaries:

```java
EitherPath<ServiceError, Data> result =
    Path.either(externalApi.fetch())
        .mapError(apiError -> new ServiceError("API unavailable", apiError));
```

The original error is preserved as the cause; callers see a domain-appropriate
type.

### Strategy 3: _Fallback Chain_

Multiple sources for the same data, each with trade-offs:

```java
EitherPath<Error, Config> config =
    Path.either(loadFromFile())
        .recoverWith(e1 -> {
            log.warn("File config failed: {}", e1);
            return Path.either(loadFromEnvironment());
        })
        .recoverWith(e2 -> {
            log.warn("Env config failed: {}", e2);
            return Path.right(Config.defaults());
        });
```

Each `recoverWith` only triggers if the previous step failed. The first
success short-circuits the chain.

### Strategy 4: _Accumulate All Errors_

For validation where users should see everything wrong at once:

```java
ValidationPath<List<String>, User> user =
    validateName(input.name())
        .zipWith3Accum(
            validateEmail(input.email()),
            validateAge(input.age()),
            User::new
        );

// All three validations run; all errors collected
```

See [ValidationPath](path_types.md#validationpath) for the full API.

### Strategy 5: _Error Enrichment_

Add context as errors propagate:

```java
EitherPath<DetailedError, Data> enriched =
    path.mapError(error -> new DetailedError(
        error,
        "During user lookup",
        Map.of("userId", userId, "timestamp", Instant.now())
    ));
```

When the error surfaces, you know not just *what* failed but *where* and
*with what context*.

---

## Conversion Between Paths

As requirements evolve, you may need to switch Path types:

### MaybePath → EitherPath

Absence becomes a typed error:

```java
MaybePath<User> maybe = Path.maybe(findUser(id));
EitherPath<String, User> either = maybe.toEitherPath("User not found");
```

### TryPath → EitherPath

Exception becomes a typed error:

```java
TryPath<Config> tried = Path.tryOf(() -> loadConfig());
EitherPath<String, Config> either = tried.toEitherPath(Throwable::getMessage);
```

### IOPath → TryPath

Execute the deferred effect and capture the result:

```java
IOPath<Data> io = Path.io(() -> fetchData());
TryPath<Data> tried = io.toTryPath();  // Executes immediately!
```

~~~admonish warning title="IO Execution"
`toTryPath()` on an `IOPath` executes the effect. The result is no longer
deferred. Be intentional about when you cross this boundary.
~~~

### The Full Conversion Map

See [Type Conversions](conversions.md) for comprehensive coverage of all
conversion paths.

---

## A Realistic Example

Bringing the patterns together:

```java
public class OrderService {
    private final UserRepository users;
    private final InventoryService inventory;
    private final PaymentService payments;

    public EitherPath<OrderError, Order> placeOrder(OrderRequest request) {
        // Validate request (fail-fast)
        return validateRequest(request)
            .peek(v -> log.debug("Request validated"))

            // Get user (convert Maybe → Either)
            .via(valid -> Path.maybe(users.findById(valid.userId()))
                .toEitherPath(() -> new OrderError.UserNotFound(valid.userId())))
            .peek(user -> log.debug("Found user: {}", user.getId()))

            // Check inventory
            .via(user -> Path.either(inventory.check(request.items()))
                .mapError(OrderError.InventoryError::new))

            // Process payment
            .via(available -> Path.tryOf(() ->
                    payments.charge(user, available.total()))
                .toEitherPath(OrderError.PaymentFailed::new))
            .peek(payment -> log.info("Payment processed: {}", payment.getId()))

            // Create order
            .via(payment -> Path.right(
                createOrder(user, request.items(), payment)));
    }

    private EitherPath<OrderError, ValidatedRequest> validateRequest(
            OrderRequest request) {
        if (request.items().isEmpty()) {
            return Path.left(new OrderError.EmptyCart());
        }
        return Path.right(new ValidatedRequest(request));
    }
}
```

The structure is visible: validate, fetch, check, charge, create. Errors
propagate with appropriate types. Logging traces the happy path. Each
conversion (`toEitherPath`, `mapError`) happens at a deliberate boundary.

---

## Common Mistakes

### Mistake 1: _Using `via` for Independent Operations_

```java
// Misleading: suggests email validation depends on name
validateName(input)
    .via(name -> validateEmail(input))  // Doesn't use name!

// Clearer: shows independence
validateName(input).zipWith(validateEmail(input), (n, e) -> ...)
```

### Mistake 2: _Side Effects in `map`_

```java
// Wrong: side effect hidden in map
path.map(user -> {
    database.save(user);  // Side effect!
    return user;
});

// Right: use peek for side effects
path.peek(user -> database.save(user));

// Or be explicit with IOPath
path.via(user -> Path.io(() -> {
    database.save(user);
    return user;
}));
```

### Mistake 3: _Forgetting to Run_

```java
// Bug: nothing happens
void processUser(String id) {
    Path.maybe(findUser(id))
        .map(this::process);  // Result discarded!
}

// Fixed: extract the result
void processUser(String id) {
    Path.maybe(findUser(id))
        .map(this::process)
        .run();
}
```

### Mistake 4: _Converting Back and Forth_

```java
// Wasteful: converting repeatedly
Path.maybe(findUser(id))
    .toEitherPath(() -> error)
    .toMaybePath()
    .toEitherPath(() -> error);  // Why?

// Clean: convert once
Path.maybe(findUser(id))
    .toEitherPath(() -> error);
```

---

## Summary

| Pattern | Method | When to Use |
|---------|--------|-------------|
| Sequential | `via` | Each step depends on the previous |
| Sequential (ignore value) | `then` | Sequencing without data flow |
| Independent | `zipWith` | Combine unrelated computations |
| Parallel binary | `parZipWith` | Two independent computations |
| Parallel n-ary | `parZip3`, `parZip4` | 3-4 independent computations |
| Parallel list | `parSequenceIO` | Dynamic number of computations |
| First-to-finish | `race` | Redundant sources, timeouts |
| Accumulate errors | `zipWithAccum` | Collect all validation failures |
| Debug | `peek` | Observe without disrupting |
| Default value | `recover` | Provide fallback on failure |
| Transform error | `mapError` | Change error type at boundaries |
| Fallback chain | `recoverWith` | Try alternatives in order |
| Type conversion | `toEitherPath`, etc. | Change Path type |

The world remains understandable and lawful when each operation has a clear
purpose and failures propagate predictably. Composition is the discipline
that makes this possible.

Continue to [Type Conversions](conversions.md) for detailed coverage of
moving between Path types.

~~~admonish tip title="See Also"
- [Monad](../functional/monad.md) - The type class behind `via`
- [Applicative](../functional/applicative.md) - The type class behind `zipWith`
- [For Comprehension](../functional/for_comprehension.md) - Alternative syntax for monadic composition
~~~

---

**Previous:** [Path Types](path_types.md)
**Next:** [Type Conversions](conversions.md)
