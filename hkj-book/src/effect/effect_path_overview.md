# Effect Path Overview

> *"There is no real direction here, neither lines of power nor cooperation.
> Decisions are never really made; at best they manage to emerge, from a chaos
> of peeves, whims, hallucinations and all-round assholery."*
>
> — Thomas Pynchon, *Gravity's Rainbow*

Pynchon was describing wartime bureaucracy, but he might as well have been
reading a poorly implemented service layer on a Monday morning.

~~~admonish info title="What You'll Learn"
- Why traditional Java error handling creates pyramids of nested chaos
- The railway model: effects as tracks, errors as switching points
- Creating Path types with factory methods
- Transforming values with `map`, chaining with `via`, extracting with `run`
- Debugging pipelines with `peek`
~~~

---

## The Pyramid of Doom

You've seen this shape before. You may have even written it, promising yourself to refactor it later:

```java
public OrderResult processOrder(String userId, OrderRequest request) {
    User user = userRepository.findById(userId);
    if (user == null) {
        return OrderResult.error("User not found");
    }

    try {
        ValidationResult validation = validator.validate(request);
        if (!validation.isValid()) {
            return OrderResult.error(validation.getErrors().get(0));
        }

        InventoryCheck inventory = inventoryService.check(request.getItems());
        if (!inventory.isAvailable()) {
            return OrderResult.error("Items unavailable");
        }

        try {
            PaymentResult payment = paymentService.charge(user, inventory.getTotal());
            if (payment.isFailed()) {
                return OrderResult.error(payment.getFailureReason());
            }

            return OrderResult.success(createOrder(user, request, payment));
        } catch (PaymentException e) {
            return OrderResult.error("Payment failed: " + e.getMessage());
        }
    } catch (ValidationException e) {
        return OrderResult.error("Validation error: " + e.getMessage());
    }
}
```

Five levels of nesting. Three different error-handling idioms. The actual
business logic, *create an order*, buried at the bottom like a punchline
nobody can find. And this is a simple example. I've witnessed far worse in Production.

The problem isn't any single technique. Null checks are sometimes appropriate.
Exceptions have their place. The problem is that they don't *compose*. Each
approach speaks its own dialect, demands its own syntax, follows its own rules
for propagating failure. String enough of them together, and you get Pynchon's
chaos: decisions that don't so much get made as reluctantly emerge.

---

## The Railway Model

Functional programmers solved this problem decades ago with a simple model:
the **railway**.

```
                         THE EFFECT RAILWAY

    Success ═══●═══●═══●═══●═══●═══════════════════▶  Result
               │   │   │   │   │
              map via map via run
                   │       │
                   ╳       │         error occurs, switch tracks
                   │       │
    Failure  ──────●───────┼───────────────────────▶  Error
                   │       │
                mapError  recover
                           │
                           ╳                         recovery, switch back
```

Your data travels along the **success track**. Operations like `map` and `via`
transform it as it goes. If something fails, the data switches to the
**failure track** and subsequent operations are skipped, no explicit checks
required, no nested conditionals. Recovery operations (`recover`, `recoverWith`)
can switch the data back to the success track if you have a sensible fallback.

This is what Path types implement. The railway is the model; Paths are the
rolling stock.

---

## The Same Logic, Flattened

Here's that order processing code rewritten with Effect Paths:

```java
public EitherPath<OrderError, Order> processOrder(String userId, OrderRequest request) {
    return Path.maybe(userRepository.findById(userId))
        .toEitherPath(() -> new OrderError.UserNotFound(userId))
        .via(user -> Path.either(validator.validate(request))
            .mapError(OrderError.ValidationFailed::new))
        .via(validated -> Path.either(inventoryService.check(request.getItems()))
            .mapError(OrderError.InventoryError::new))
        .via(inventory -> Path.tryOf(() -> paymentService.charge(user, inventory.getTotal()))
            .toEitherPath(OrderError.PaymentFailed::new))
        .via(payment -> Path.right(createOrder(user, request, payment)));
}
```

The nesting has gone. Each step follows the same pattern: transform or chain,
handle errors consistently, let failures propagate automatically. The business
logic reads top-to-bottom instead of outside-in.

This isn't magic. The underlying complexity hasn't vanished; you still need
to handle the same failure cases. But the *accidental* complexity (the
pyramids, the repeated null checks, the catch blocks that just wrap and
rethrow) are gone. What remains is the essential shape of your logic.

---

## Path Types at a Glance

| Path Type | Underlying Effect | When to Reach for It |
|-----------|-------------------|----------------------|
| `MaybePath<A>` | `Maybe<A>` | Absence is normal, not an error |
| `EitherPath<E, A>` | `Either<E, A>` | Errors carry typed information |
| `TryPath<A>` | `Try<A>` | Wrapping code that throws exceptions |
| `IOPath<A>` | `IO<A>` | Side effects you want to defer |
| `ValidationPath<E, A>` | `Validated<E, A>` | Collecting *all* errors, not just the first |
| `IdPath<A>` | `Id<A>` | The trivial case: always succeeds |
| `OptionalPath<A>` | `Optional<A>` | Bridging to Java's standard library |
| `GenericPath<F, A>` | `Kind<F, A>` | Custom monads, when nothing else fits |

Each Path type wraps its underlying effect and provides:
- `map(f)` - Transform the success value
- `via(f)` - Chain to another Path (monadic bind)
- `run()` - Extract the underlying effect
- Type-specific operations for recovery, error transformation, and more

---

## Creating Paths

The `Path` class provides factory methods for all Path types. A small sampler:

```java
// MaybePath: optional values
MaybePath<String> greeting = Path.just("Hello");
MaybePath<String> empty = Path.nothing();
MaybePath<User> user = Path.maybe(repository.findById(id));

// EitherPath: typed errors
EitherPath<String, Integer> success = Path.right(42);
EitherPath<String, Integer> failure = Path.left("Something went wrong");

// TryPath: exception handling
TryPath<Config> config = Path.tryOf(() -> loadConfig());

// IOPath: deferred side effects
IOPath<String> readFile = Path.io(() -> Files.readString(path));
```

---

## Transforming with `map`

All Path types support `map` for transforming the success value:

```java
MaybePath<String> greeting = Path.just("hello");
MaybePath<String> upper = greeting.map(String::toUpperCase);
// → Just("HELLO")

MaybePath<String> empty = Path.nothing();
MaybePath<String> stillEmpty = empty.map(String::toUpperCase);
// → Nothing (map doesn't run on empty paths)
```

The function inside `map` only executes if the Path is on the success track.
Failures pass through unchanged. No defensive checks required.

---

## Chaining with `via`

The `via` method chains computations where each step depends on the previous
result:

```java
EitherPath<Error, Invoice> invoice =
    Path.either(findUser(userId))
        .via(user -> Path.either(getCart(user)))
        .via(cart -> Path.either(calculateTotal(cart)))
        .via(total -> Path.either(createInvoice(total)));
```

Each `via` receives the success value and returns a new Path. If any step
fails, subsequent steps are skipped; the failure propagates to the end.

The name `via` mirrors the Focus DSL from the optics chapters. Where FocusPath
uses `via` to navigate through lenses, EffectPath uses `via` to navigate through
effects. Different territory, same verb.

---

## Extracting Results

Eventually you need to leave the railway and extract a result:

```java
// MaybePath
Maybe<String> maybe = path.run();
String value = path.getOrElse("default");
String value = path.getOrThrow(() -> new NoSuchElementException());

// EitherPath
Either<Error, User> either = path.run();
String result = either.fold(
    error -> "Failed: " + error,
    user -> "Found: " + user.name()
);

// IOPath: actually runs the effect
String content = ioPath.unsafeRun();      // may throw
Try<String> safe = ioPath.runSafe();      // captures exceptions
```

---

## Debugging with `peek`

When a pipeline misbehaves, `peek` lets you observe values mid-flow without
disrupting the computation:

```java
EitherPath<Error, User> result =
    Path.either(validateInput(input))
        .peek(valid -> log.debug("Input validated: {}", valid))
        .via(valid -> Path.either(createUser(valid)))
        .peek(user -> log.info("User created: {}", user.getId()));
```

For failure paths, `peek` only executes on success. Failures pass through
silently, which is usually what you want when debugging the happy path.

---

## Summary

| Operation | What It Does | Railway Metaphor |
|-----------|--------------|------------------|
| `Path.just(x)`, `Path.right(x)`, etc. | Create a Path on the success track | Board the train |
| `map(f)` | Transform the value, stay on same track | Redecorate your carriage |
| `via(f)` | Chain to a new Path | Transfer to connecting service |
| `recover(f)` | Switch from failure to success track | Emergency rescue |
| `mapError(f)` | Transform the error, stay on failure track | Relabel the delay announcement |
| `run()` | Exit the railway, extract the result | Arrive at destination |

Continue to [Capability Interfaces](capabilities.md) to understand the powers
that make this possible.

~~~admonish tip title="See Also"
- [Monad](../functional/monad.md) - The type class powering `via` and `flatMap`
- [Functor](../functional/functor.md) - The type class powering `map`
- [IO Monad](../monads/io_monad.md) - The underlying type for `IOPath`
~~~

~~~admonish tip title="Further Reading"
- **Scott Wlaschin**: [Railway Oriented Programming -- error handling in functional languages](https://vimeo.com/97344498) - video
- **Scott Wlaschin**: [Railway Oriented Programming -- error handling in functional languages](https://www.slideshare.net/slideshow/railway-oriented-programming/32242318#1) - slides)
~~~

---

**Previous:** [Introduction](ch_intro.md)
**Next:** [Capability Interfaces](capabilities.md)
