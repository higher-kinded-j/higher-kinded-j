# ForPath: For-Comprehensions with Effect Paths

> *"Though this be madness, yet there is method in't."*
>
> -- William Shakespeare, *Hamlet*

And so it is with for-comprehensions: what appears to be arcane syntax hides a
deeply methodical approach to composing sequential operations.

~~~admonish info title="What You'll Learn"
- How ForPath bridges the For comprehension system and the Effect Path API
- Creating comprehensions directly with Path types (no manual `Kind` extraction)
- Using generators (`.from()`), bindings (`.let()`), guards (`.when()`), and projections (`.yield()`)
- Integrating optics with `.focus()` and `.match()` for structural navigation
- Choosing between ForPath and the standard For class
~~~

~~~ admonish example title="See Example Code:"
[ForPathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ForPathExample.java)
~~~

---

## The Problem: Bridging Two Worlds

The standard [For](../functional/for_comprehension.md) class provides powerful for-comprehension
syntax, but it operates on raw `Kind<M, A>` values and requires explicit `Monad` instances.
When working with the Effect Path API, this creates friction:

```java
// Using standard For with Path types requires extraction and rewrapping
Kind<MaybeKind.Witness, Integer> kindResult = For.from(maybeMonad, path1.run().kind())
    .from(a -> path2.run().kind())
    .yield((a, b) -> a + b);

MaybePath<Integer> result = Path.maybe(MAYBE.narrow(kindResult));
```

The intent is clear, but the ceremony obscures it. `ForPath` eliminates this friction:

```java
// ForPath works directly with Path types
MaybePath<Integer> result = ForPath.from(path1)
    .from(a -> path2)
    .yield((a, b) -> a + b);
```

The comprehension accepts Path types and returns Path types. No manual extraction,
no rewrapping, no boilerplate.

---

## Entry Points

`ForPath` provides entry points for each supported Path type:

| Path Type | Entry Point | Supports `when()` |
|-----------|-------------|-------------------|
| `MaybePath<A>` | `ForPath.from(maybePath)` | Yes |
| `OptionalPath<A>` | `ForPath.from(optionalPath)` | Yes |
| `EitherPath<E, A>` | `ForPath.from(eitherPath)` | No |
| `TryPath<A>` | `ForPath.from(tryPath)` | No |
| `IOPath<A>` | `ForPath.from(ioPath)` | No |
| `VTaskPath<A>` | `ForPath.from(vtaskPath)` | No |
| `IdPath<A>` | `ForPath.from(idPath)` | No |
| `NonDetPath<A>` | `ForPath.from(nonDetPath)` | Yes |
| `GenericPath<F, A>` | `ForPath.from(genericPath)` | Optional |

The `when()` guard operation is only available for Path types backed by `MonadZero`,
which can represent emptiness or failure.

---

## Core Operations

### Generators: `.from()`

The `.from()` operation extracts a value from the current step and chains to a new
Path-producing computation. This is the monadic bind (`flatMap`) in disguise.

```java
MaybePath<String> result = ForPath.from(Path.just("Alice"))
    .from(name -> Path.just(name.length()))         // a = "Alice", b = 5
    .from(t -> Path.just(t._1() + ":" + t._2()))    // t is Tuple2<String, Integer>
    .yield((name, len, combined) -> combined);      // "Alice:5"
```

Each `.from()` adds a new value to the accumulating tuple, making all previous
values available to subsequent steps.

### Value Bindings: `.let()`

The `.let()` operation computes a pure value from accumulated results without
introducing a new effect. It's equivalent to `map` that carries the value forward.

```java
MaybePath<String> result = ForPath.from(Path.just(10))
    .let(a -> a * 2)                    // b = 20 (pure calculation)
    .let(t -> t._1() + t._2())          // c = 30 (can access tuple)
    .yield((a, b, c) -> "Sum: " + c);   // "Sum: 30"
```

### Guards: `.when()`

For Path types with `MonadZero` (MaybePath, OptionalPath, NonDetPath), the `.when()`
operation filters results. When the predicate returns false, the computation
short-circuits to the monad's zero value (Nothing, empty, etc.).

```java
MaybePath<Integer> evenOnly = ForPath.from(Path.just(4))
    .when(n -> n % 2 == 0)              // passes: 4 is even
    .yield(n -> n * 10);                // Just(40)

MaybePath<Integer> filtered = ForPath.from(Path.just(3))
    .when(n -> n % 2 == 0)              // fails: 3 is odd
    .yield(n -> n * 10);                // Nothing
```

### Projection: `.yield()`

Every comprehension ends with `.yield()`, which maps the accumulated values to a
final result. You can access values individually or as a tuple:

```java
// Individual parameters
.yield((a, b, c) -> a + b + c)

// Or as a tuple for many values
.yield(t -> t._1() + t._2() + t._3())
```

---

## Optics Integration

ForPath integrates with the [Focus DSL](../optics/focus_dsl.md) for structural
navigation within comprehensions.

### Extracting with `.focus()`

The `.focus()` operation uses a `FocusPath` to extract a nested value:

```java
record User(String name, Address address) {}
record Address(String city, String postcode) {}

// Create lenses for each field
Lens<User, Address> addressLens = Lens.of(
    User::address, (u, a) -> new User(u.name(), a));
Lens<Address, String> cityLens = Lens.of(
    Address::city, (a, c) -> new Address(c, a.postcode()));

// Compose paths with via() for nested access
FocusPath<User, Address> addressPath = FocusPath.of(addressLens);
FocusPath<User, String> userCityPath = addressPath.via(FocusPath.of(cityLens));

MaybePath<String> result = ForPath.from(Path.just(user))
    .focus(userCityPath)                        // extract city directly
    .yield((user, city) -> city.toUpperCase());
```

Alternatively, chain focus operations where the second takes a function:

```java
FocusPath<User, Address> addressPath = FocusPath.of(addressLens);

MaybePath<String> result = ForPath.from(Path.just(user))
    .focus(addressPath)                         // extract address -> Steps2
    .focus(t -> t._2().city())                  // extract city from tuple
    .yield((user, address, city) -> city.toUpperCase());
```

### Pattern Matching with `.match()`

The `.match()` operation uses an `AffinePath` for optional extraction. When the
focus is absent, the comprehension short-circuits for `MonadZero` types:

```java
sealed interface Result permits Success, Failure {}
record Success(String value) implements Result {}
record Failure(String error) implements Result {}

AffinePath<Result, Success> successPath = AffinePath.of(
    Affine.of(
        r -> r instanceof Success s ? Optional.of(s) : Optional.empty(),
        (r, s) -> s
    )
);

MaybePath<String> result = ForPath.from(Path.just((Result) new Success("data")))
    .match(successPath)                         // extract Success
    .yield((r, success) -> success.value().toUpperCase());
// Just("DATA")

MaybePath<String> empty = ForPath.from(Path.just((Result) new Failure("error")))
    .match(successPath)                         // fails to match
    .yield((r, success) -> success.value());
// Nothing
```

---

## EitherPath Example

For error-handling scenarios, EitherPath comprehensions propagate failures automatically:

```java
record User(String id, String name) {}
record Order(String orderId, User user) {}

Function<String, EitherPath<String, User>> findUser = id ->
    id.equals("user-1")
        ? Path.right(new User("user-1", "Alice"))
        : Path.left("User not found: " + id);

Function<User, EitherPath<String, Order>> createOrder = user ->
    Path.right(new Order("order-123", user));

EitherPath<String, String> result = ForPath.from(findUser.apply("user-1"))
    .from(user -> createOrder.apply(user))
    .yield((user, order) -> "Created " + order.orderId() + " for " + user.name());
// Right("Created order-123 for Alice")

EitherPath<String, String> failed = ForPath.from(findUser.apply("unknown"))
    .from(user -> createOrder.apply(user))
    .yield((user, order) -> "Created " + order.orderId());
// Left("User not found: unknown")
```

---

## IOPath Example

IOPath comprehensions compose deferred side-effectful computations:

```java
IOPath<String> readConfig = Path.io(() -> "production");
IOPath<Integer> readPort = Path.io(() -> 8080);

IOPath<String> serverInfo = ForPath.from(readConfig)
    .from(env -> readPort)
    .let(t -> t._1().toUpperCase())
    .yield((env, port, upperEnv) -> upperEnv + " server on port " + port);

// Nothing executes until:
String result = serverInfo.unsafeRun();  // "PRODUCTION server on port 8080"
```

---

## VTaskPath Example

VTaskPath comprehensions compose virtual thread-based concurrent computations:

```java
VTaskPath<User> fetchUser = Path.vtask(() -> userService.fetch(userId));
VTaskPath<Profile> fetchProfile = Path.vtask(() -> profileService.fetch(profileId));

VTaskPath<String> greeting = ForPath.from(fetchUser)
    .from(user -> fetchProfile)
    .let(t -> t._1().name().toUpperCase())
    .yield((user, profile, upperName) ->
        "Hello " + upperName + " from " + profile.city());

// Nothing executes until:
String result = greeting.unsafeRun();  // "Hello ALICE from London"
```

VTaskPath comprehensions are ideal for orchestrating multiple service calls:

```java
VTaskPath<OrderSummary> orderWorkflow = ForPath.from(Path.vtask(() -> validateOrder(order)))
    .from(validated -> Path.vtask(() -> reserveInventory(validated)))
    .from(t -> Path.vtask(() -> processPayment(t._2())))
    .from(t -> Path.vtask(() -> sendConfirmation(t._3())))
    .yield((validated, reserved, payment, confirmation) ->
        new OrderSummary(validated.id(), payment.transactionId(), confirmation.sentAt()));

// Execute the entire workflow
Try<OrderSummary> result = orderWorkflow.runSafe();
```

---

## NonDetPath Example

NonDetPath (backed by List) generates all combinations:

```java
NonDetPath<String> combinations = ForPath.from(Path.list("red", "blue"))
    .from(c -> Path.list("S", "M", "L"))
    .when(t -> !t._1().equals("blue") || !t._2().equals("S"))  // filter out blue-S
    .yield((colour, size) -> colour + "-" + size);

List<String> result = combinations.run();
// ["red-S", "red-M", "red-L", "blue-M", "blue-L"]
```

---

## When to Use ForPath vs For

| Scenario | Use |
|----------|-----|
| Working with Effect Path API | `ForPath` |
| Need Path types as output | `ForPath` |
| Working with raw `Kind<M, A>` | `For` |
| Using monad transformers (StateT, EitherT) | `For` |
| Custom monads without Path wrappers | `For` with `GenericPath` adapter |

For monad transformer stacks, the standard `For` class remains the appropriate choice
as it works directly with the transformer's `Kind` representation.

---

~~~admonish info title="Key Takeaways"
* **ForPath eliminates boilerplate** when composing Path types in for-comprehension style
* **Entry points accept Path types directly** and return Path types
* **All For operations are supported**: `from()`, `let()`, `when()` (where applicable), `yield()`
* **Optics integration** via `focus()` and `match()` enables structural navigation
* **Type safety is preserved** throughout the comprehension
~~~

~~~admonish tip title="See Also"
- [For Comprehension](../functional/for_comprehension.md) - The underlying For class for raw `Kind` values
- [Effect Path Overview](effect_path_overview.md) - Introduction to the Effect Path API
- [VTaskPath](path_vtask.md) - Virtual thread-based Effect Path for concurrent workloads
- [Focus DSL](../optics/focus_dsl.md) - Optics integration for structural navigation
~~~

---

**Previous:** [Composition Patterns](composition.md)
**Next:** [Type Conversions](conversions.md)
