# Effect Path Overview

> *"There is no real direction here, neither lines of power nor cooperation.
> Decisions are never really made; at best they manage to emerge, from a chaos
> of peeves, whims, hallucinations and all-round assholery."*
>
> -- Thomas Pynchon, *Gravity's Rainbow*

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

<pre style="line-height:1.5;font-size:0.95em">
                         <b>THE EFFECT RAILWAY</b>

    <span style="color:#4CAF50"><b>Success</b> ═══●═══●═══●═══●═══●═══════════════════▶  Result</span>
    <span style="color:#4CAF50">           │   │   │   │   │</span>
    <span style="color:#4CAF50">          map via map via run</span>
                   │       │
                   ╳       │         error occurs, switch tracks
                   │       │
    <span style="color:#F44336"><b>Failure</b>  ──────●───────┼───────────────────────▶  Error</span>
    <span style="color:#F44336">               │       │</span>
    <span style="color:#F44336">            mapError</span>  <span style="color:#4CAF50">recover</span>
                           │
                           ╳                         recovery, switch back
</pre>

Your data travels along the **success track**. Operations like `map` and `via`
transform it as it goes. If something fails, the data switches to the
**failure track** and subsequent operations are skipped, no explicit checks
required, no nested conditionals. Recovery operations (`recover`, `recoverWith`)
can switch the data back to the success track if you have a sensible fallback.

This is what Path types implement. The railway is the model; Paths are the
rolling stock.

### Operators as Railway Switches

Each Path operator has a specific role on the railway:

| Operator | Railway Role | What Happens |
|----------|-------------|-------------|
| `map(fn)` | <span style="color:#4CAF50">**Green track**</span> transform | Transforms data on the success track; failures pass through |
| `mapError(fn)` | <span style="color:#F44336">**Red track**</span> transform | Transforms data on the error track; successes pass through |
| `via(fn)` / `flatMap(fn)` | <span style="color:#4CAF50">**Green**</span> → <span style="color:#F44336">**Red**</span> switch | Chains an operation that may divert success to failure |
| `recover(fn)` | <span style="color:#F44336">**Red**</span> → <span style="color:#4CAF50">**Green**</span> switch | Converts a failure back into a success value |
| `recoverWith(fn)` | <span style="color:#F44336">**Red**</span> → <span style="color:#4CAF50">**Green**</span> switch | Converts a failure into a new Path (which itself may fail) |
| `orElse(supplier)` | Fallback junction | Tries an alternative path if the first failed |
| `peek(fn)` | <span style="color:#4CAF50">**Green track**</span> siding | Observes the success value without changing tracks |
| `focus(lens)` | <span style="color:#4CAF50">**Green track**</span> wormhole | Navigates into nested data without leaving the track |

### Visualising Each Operator

The table above gives you the vocabulary; these diagrams show you the geometry.
Each one illustrates what happens to data on the success and failure tracks when
the operator executes.

#### map(fn)

Transforms the value on the success track. Failures pass through untouched.

<pre style="line-height:1.5;font-size:0.95em">
    <span style="color:#4CAF50"><b>Success</b> ═══●═══ map(fn) ═══●═══▶  transformed value</span>
    <span style="color:#4CAF50">           A              f(A)</span>

    <span style="color:#F44336"><b>Failure</b> ═══●══════════════●═══▶  unchanged error</span>
    <span style="color:#F44336">           E              E          (skipped)</span>
</pre>

#### mapError(fn)

The mirror of `map`: transforms the value on the failure track. Successes pass through untouched.

<pre style="line-height:1.5;font-size:0.95em">
    <span style="color:#4CAF50"><b>Success</b> ═══●══════════════●═══▶  unchanged value</span>
    <span style="color:#4CAF50">           A              A          (skipped)</span>

    <span style="color:#F44336"><b>Failure</b> ═══●═══ mapError(fn) ═══●═══▶  transformed error</span>
    <span style="color:#F44336">           E                  f(E)</span>
</pre>

#### via(fn) / flatMap(fn)

Chains an operation that may itself fail. The function receives the success value and returns
a new Path; if that Path fails, the data switches to the failure track.

<pre style="line-height:1.5;font-size:0.95em">
    <span style="color:#4CAF50"><b>Success</b> ═══●═══ via(fn) ═══●═══▶  continues on success</span>
    <span style="color:#4CAF50">           A           fn(A)</span>  ╲
                                   ╲
                                    <span style="color:#F44336">╲═══▶  diverts to failure (fn returned Left/Nothing)</span>

    <span style="color:#F44336"><b>Failure</b> ═══●════════════════●═══▶  unchanged error (skipped)</span>
</pre>

#### recover(fn)

Switches from the failure track back to the success track by converting the error into a value.

<pre style="line-height:1.5;font-size:0.95em">
    <span style="color:#4CAF50"><b>Success</b> ═══●══════════════════●═══▶  unchanged value (skipped)</span>

    <span style="color:#F44336"><b>Failure</b> ═══●═══ recover(fn) </span><span style="color:#4CAF50">═══●═══▶  recovered value</span>
    <span style="color:#F44336">           E             </span><span style="color:#4CAF50">   fn(E)</span>
</pre>

#### recoverWith(fn)

Like `recover`, but the function returns a new Path rather than a raw value. The recovery
itself may fail, keeping the data on the failure track.

<pre style="line-height:1.5;font-size:0.95em">
    <span style="color:#4CAF50"><b>Success</b> ═══●══════════════════════●═══▶  unchanged value (skipped)</span>

    <span style="color:#F44336"><b>Failure</b> ═══●═══ recoverWith(fn) </span><span style="color:#4CAF50">═══●═══▶  recovered value</span>
    <span style="color:#F44336">           E                fn(E)</span>  ╲
                                        ╲
                                         <span style="color:#F44336">╲═══▶  still failed (fn returned Left/Nothing)</span>
</pre>

#### orElse(supplier)

A fallback junction: if the first path failed, try a completely different path.

<pre style="line-height:1.5;font-size:0.95em">
    <span style="color:#4CAF50"><b>Success</b> ═══●══════════════════════════▶  original value</span>

    <span style="color:#F44336"><b>Failure</b> ═══●═══ orElse(supplier)</span>
    <span style="color:#F44336">           E         │</span>
                       ▼
              <span style="color:#4CAF50">supplier.get() ═══●═══▶  alternative value</span>
                                    ╲
                                     <span style="color:#F44336">╲═══▶  alternative also failed</span>
</pre>

#### peek(fn)

A siding: the function observes the success value but does not change it or the track.
Commonly used for logging and debugging.

<pre style="line-height:1.5;font-size:0.95em">
    <span style="color:#4CAF50"><b>Success</b> ═══●═══ peek(fn) ═══●═══▶  same value, same track</span>
    <span style="color:#4CAF50">           A      │         A</span>
                    ▼
              <i>side effect</i>       (e.g. log, metric, debug)

    <span style="color:#F44336"><b>Failure</b> ═══●════════════════●═══▶  unchanged error (skipped)</span>
</pre>

#### focus(lens)

A wormhole into nested structure: the lens extracts a field from the success value,
and the result stays on the same track. If the optic is an `AffinePath` and the
field is absent, the data switches to the failure track.

<pre style="line-height:1.5;font-size:0.95em">
    <span style="color:#4CAF50"><b>Success</b> ═══●═══ focus(lens) ═══●═══▶  focused field</span>
    <span style="color:#4CAF50">        Order              Address       (lens extracted nested value)</span>

    <span style="color:#F44336"><b>Failure</b> ═══●════════════════════●═══▶  unchanged error (skipped)</span>

    <i>With AffinePath (optional field):</i>
    <span style="color:#4CAF50"><b>Success</b> ═══●═══ focus(affine) </span>
    <span style="color:#4CAF50">        Order</span>         ╲
                          ╲  field absent
                           <span style="color:#F44336">╲═══▶  Left(providedError) / Nothing</span>
</pre>

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

## Structural Navigation with `focus`

Effect paths integrate with the Focus DSL, enabling structural navigation within effect contexts.
Where `map` transforms values and `via` chains effects, `focus` drills into nested structures
using optics.

```
                         FOCUS WITHIN EFFECTS

    EitherPath<Error, User>
            │
            │  focus(namePath)         ← optic navigation
            ▼
    EitherPath<Error, String>
            │
            │  map(String::toUpperCase)  ← value transformation
            ▼
    EitherPath<Error, String>
            │
            │  via(validateName)         ← effect chaining
            ▼
    EitherPath<Error, ValidName>
```

### Basic Usage

```java
// Given a FocusPath from the optics domain
FocusPath<User, String> namePath = UserFocus.name();

// Apply within an effect
EitherPath<Error, User> userResult = fetchUser(userId);
EitherPath<Error, String> nameResult = userResult.focus(namePath);
```

The focus preserves the effect's semantics: if `userResult` is `Left`, `nameResult` is also `Left`.
Only `Right` values are navigated.

### Handling Optional Focus

When using `AffinePath` (for optional fields), provide an error for the absent case:

```java
// AffinePath for Optional<String> email
AffinePath<User, String> emailPath = UserFocus.email();

// Must provide error if email is absent
EitherPath<Error, String> emailResult =
    userResult.focus(emailPath, new Error("Email not configured"));
```

| FocusPath | AffinePath |
|-----------|------------|
| Always succeeds (value guaranteed) | May fail (value optional) |
| `focus(path)` | `focus(path, errorIfAbsent)` |

### Effect-Specific Behaviour

Each effect type handles absent focuses differently:

| Effect | FocusPath Result | AffinePath Absent Result |
|--------|------------------|--------------------------|
| `MaybePath` | `Just(focused)` | `Nothing` |
| `EitherPath` | `Right(focused)` | `Left(providedError)` |
| `TryPath` | `Success(focused)` | `Failure(providedException)` |
| `IOPath` | `IO(focused)` | `IO(throw exception)` |
| `ValidationPath` | `Valid(focused)` | `Invalid(providedError)` |

### Chaining Focus with Effects

Focus composes naturally with other path operations:

```java
// Complex pipeline: fetch → navigate → validate → transform
EitherPath<Error, String> result =
    fetchUser(userId)                              // → EitherPath<Error, User>
        .focus(UserFocus.address())                // → EitherPath<Error, Address>
        .focus(AddressFocus.postcode(), noPostcodeError)  // → EitherPath<Error, String>
        .via(code -> validatePostcode(code))       // → EitherPath<Error, ValidPostcode>
        .map(ValidPostcode::formatted);            // → EitherPath<Error, String>
```

### When to Use focus vs via

| Operation | Use When |
|-----------|----------|
| `focus(path)` | Extracting nested fields with optics |
| `via(f)` | Chaining to another effect computation |
| `map(f)` | Transforming the value without changing effect type |

```java
// focus: structural navigation (optics)
path.focus(UserFocus.name())

// via: effect sequencing (monadic bind)
path.via(user -> validateUser(user))

// map: value transformation (functor)
path.map(name -> name.toUpperCase())
```

~~~admonish tip title="See Also"
- [Focus DSL](../optics/focus_dsl.md) - Complete guide to Focus paths and navigation
- [Focus-Effect Integration](focus_integration.md) - Complete bridging guide
- [Capability Interfaces](capabilities.md) - Type class foundations
~~~

---

## Getting Back to Standard Java

You are never locked in. Every Path type unwraps to a standard Java value with `.run()`, and `.fold()` gives you full control over both tracks:

```java
// Extract the underlying type
Maybe<User> maybe = maybePath.run();
Either<AppError, User> either = eitherPath.run();
Try<Config> tried = tryPath.run();

// Convert to java.util.Optional
Optional<User> opt = maybePath.run().toOptional();

// Extract with a default
User user = eitherPath.getOrElse(User.anonymous());

// Handle both tracks explicitly
String message = eitherPath.run().fold(
    error -> "Failed: " + error.message(),
    user  -> "Found: " + user.name()
);
```

For deferred effects (`IOPath`, `VTaskPath`), `.unsafeRun()` executes immediately and `.runSafe()` captures exceptions as `Try`:

```java
String content = ioPath.unsafeRun();    // execute, may throw
Try<String> safe = ioPath.runSafe();    // execute, exceptions captured
```

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
- [Choosing Abstraction Levels](../functional/abstraction_levels.md) - When to use Applicative vs Selective vs Monad
- [Monad](../functional/monad.md) - The type class powering `via` and `flatMap`
- [Functor](../functional/functor.md) - The type class powering `map`
- [IO Monad](../monads/io_monad.md) - The underlying type for `IOPath`
- [Free Applicative](../monads/free_applicative.md) - Building analysable programs
~~~

~~~admonish tip title="Further Reading"
- **Scott Wlaschin**: [Railway Oriented Programming -- error handling in functional languages](https://vimeo.com/97344498) - video
- **Scott Wlaschin**: [Railway Oriented Programming -- error handling in functional languages](https://www.slideshare.net/slideshow/railway-oriented-programming/32242318#1) - slides)
~~~

---

**Previous:** [Introduction](ch_intro.md)
**Next:** [Capability Interfaces](capabilities.md)
