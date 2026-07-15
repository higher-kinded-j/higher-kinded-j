---
name: hkj-arch
description: "Functional core imperative shell architecture with Higher-Kinded-J and Java 25: separate pure business logic from side effects, boundary design, records and sealed interfaces for domain models, where to call unsafeRun, testing without mocks, push effects to edges, TimeSource, injecting the clock, deterministic time, testing time without sleeping, SteppableClock"
---

# Functional Core / Imperative Shell with HKJ and Java 25

You are helping a developer structure their Java 25 application using the "functional core, imperative shell" (FCIS) pattern with Higher-Kinded-J.

## When to load supporting files

- If the user wants **before/after architecture examples**, load `reference/architecture-examples.md`
- If the user asks about **domain modelling** (sealed hierarchies, error types, state machines), load `reference/domain-modelling.md`
- For **Effect Path API** details, suggest `/hkj-guide`
- For **optics and Focus DSL**, suggest `/hkj-optics`
- For **effect handlers** (programs as data), suggest `/hkj-effects`
- For **Spring Boot integration**, suggest `/hkj-spring`

---

## The Pattern

```
+---------------------------------------------------+
|              IMPERATIVE SHELL                      |
|              (side effects happen here)            |
|                                                    |
|   Controllers, CLI handlers, scheduled jobs        |
|   HTTP clients, database repositories              |
|   .unsafeRun(), .run(), .fold()                    |
|                                                    |
|   +-------------------------------------------+   |
|   |          FUNCTIONAL CORE                  |   |
|   |          (pure, no side effects)          |   |
|   |                                           |   |
|   |   Domain models (records, sealed ifaces)  |   |
|   |   Business rules (EitherPath, Validation) |   |
|   |   Data navigation (Optics, Focus DSL)     |   |
|   |   Workflows (ForPath, Free monad)         |   |
|   +-------------------------------------------+   |
+---------------------------------------------------+
```

**Core**: Pure functions, immutable data, no side effects. Easy to test, reason about, and compose.
**Shell**: Executes effects, converts between worlds, handles I/O. Thin layer at the edges.

---

## Java 25 Features That Enable FCIS

| Feature | Role in FCIS | Example |
|---------|-------------|---------|
| **Records** | Immutable value types for domain models | `record Order(String id, Money total, List<LineItem> items) {}` |
| **Sealed interfaces** | Exhaustive domain error hierarchies | `sealed interface OrderError permits NotFound, Invalid, PaymentFailed {}` |
| **Pattern matching** | Exhaustive handling at the shell boundary | `switch (result) { case Right(var order) -> ...; case Left(var error) -> ...; }` |
| **Virtual threads** | Cheap concurrency without reactive complexity | `VTaskPath` and `VStreamPath` for concurrent effects |
| **Preview features** | Stable values, flexible constructors | Required by HKJ: `--enable-preview` |

---

## Mapping HKJ Types to the Architecture

### Functional Core (Pure)

| HKJ Type | Purpose in Core |
|----------|----------------|
| `EitherPath<E, A>` | Business rule validation, typed error propagation |
| `ValidationPath<E, A>` | Accumulating all validation errors |
| `MaybePath<A>` | Optional values, lookups that may return nothing |
| Records + `@GenerateLenses` | Immutable domain models with optic navigation |
| `FocusPath` / `AffinePath` | Type-safe data access and transformation |
| `ForPath` comprehensions | Multi-step pure workflows |
| Free monad programs | Domain operations as inspectable data |
| `TimeSource` | The clock as an injected dependency; `now()` returns `IO<Instant>`, so the core defers the read rather than performing it |

### Imperative Shell (Effects)

| HKJ Type | Purpose in Shell |
|----------|-----------------|
| `IOPath<A>` | Deferred side effects (file I/O, network) |
| `VTaskPath<A>` | Virtual thread concurrency |
| `VStreamPath<A>` | Lazy streaming on virtual threads |
| `CompletableFuturePath<A>` | Async operations |
| `EffectBoundary<F>` | Interpret-and-execute boundary for Free monad programs |
| `TestBoundary<F>` | Pure boundary for deterministic testing (Id monad) |
| `.unsafeRun()` / `.run()` | Executing deferred effects at the boundary |
| `.fold(onError, onSuccess)` | Converting results to responses |

---

## Boundary Design Guide

### Where to Call `.run()` and `.unsafeRun()`

| Context | Method | Example |
|---------|--------|---------|
| REST controller | `.run()` on EitherPath | `return orderService.process(req).run();` |
| REST controller | `.fold()` on result | `return result.fold(this::errorResponse, this::successResponse);` |
| CLI entry point | `.unsafeRun()` on IOPath | `pipeline.unsafeRun();` |
| Scheduled job | `.runSafe()` on VTaskPath | `task.runSafe().fold(this::logError, this::logSuccess);` |
| Test | `.run()` then assert | `assertEquals(Either.right(expected), service.process(input).run());` |

**Rule**: `.unsafeRun()` and `.run()` belong in the shell. The core should only build and compose paths, never execute them.

### Where to Convert Between Path Types

```java
// Service layer (core): returns EitherPath
public EitherPath<OrderError, Order> processOrder(OrderRequest req) {
    return validateRequest(req)             // EitherPath<OrderError, ValidatedReq>
        .via(v -> checkInventory(v))        // EitherPath<OrderError, InventoryCheck>
        .via(inv -> calculateTotal(inv))    // EitherPath<OrderError, Order>
}

// Controller (shell): converts to HTTP response
@PostMapping("/orders")
public Either<OrderError, Order> createOrder(@RequestBody OrderRequest req) {
    return orderService.processOrder(req).run();  // .run() at the shell boundary
}
```

### How Optics Keep the Core Pure

Optics navigate and transform immutable data without mutation:

```java
// Pure: navigate and transform (no side effects)
Order discounted = OrderFocus.items()          // TraversalPath<Order, LineItem>
    .filter(item -> item.quantity() > 10)      // only bulk items
    .price()                                    // TraversalPath to price
    .modifyAll(p -> p.multiply(0.9), order);   // 10% discount, returns new Order
```

---

## Testing Benefits

### Pure Core = No Mocks

```java
// Core function: pure, deterministic, no dependencies
EitherPath<OrderError, Price> calculateDiscount(Order order, Customer customer) {
    return Path.right(order)
        .via(o -> validateOrder(o))
        .map(o -> applyLoyaltyDiscount(o, customer.tier()));
}

// Test: just call it with data
@Test
void testDiscount() {
    var order = new Order("1", List.of(item1, item2));
    var customer = new Customer("Alice", LoyaltyTier.GOLD);

    var result = calculateDiscount(order, customer).run();

    assertEquals(Right(new Price(90.0)), result);
}
```

### Effect Handlers: Id Monad for Tests

```java
// Production: IO interpreter (real side effects)
var ioResult = program.foldMap(productionInterpreter, IOMonad.INSTANCE);

// Test: Id interpreter (pure, no mocks)
var testResult = program.foldMap(testInterpreter, IdMonad.INSTANCE);
assertEquals(expected, IdKindHelper.ID_OP.narrow(testResult).value());
```

---

## TimeSource: The Clock Is an Effect

`Instant.now()` in the core is a side effect hiding in plain sight: it reads the outside world, so the
function is no longer deterministic and no longer testable by data alone. `TimeSource`
(`org.higherkindedj.hkt.time.TimeSource`) lifts `java.time.Clock` into IO/VTask so "now" becomes a
lazy, composable, testable value that the core *builds* and the shell *runs*.

**Why the name**: it is `TimeSource`, not `Clock`, so it never clashes with `java.time.Clock`, which
it wraps (as a record component) rather than replaces.

| Member | Returns | Where |
|--------|---------|-------|
| `TimeSource.system()` | `TimeSource` | Shell: the production default (`Clock.systemUTC()`) |
| `TimeSource.of(Clock)` | `TimeSource` | Lift any JDK clock: offset, fixed, or a test clock |
| `TimeSource.fixed(Instant)` | `TimeSource` | Tests: a frozen instant, UTC |
| `ts.now()` | `IO<Instant>` | Core: the clock read, deferred |
| `ts.nowAsync()` | `VTask<Instant>` | Core: the same read on a virtual thread |
| `ts.clock()` | `Clock` | Shell: synchronous edge code that just needs a `Clock` |

### The Injection Pattern

Take a `TimeSource` in the constructor; default it to the system clock. The dependency is explicit,
and the only place that decides *which* clock is the composition root.

```java
public class InMemoryInventoryService implements InventoryService {

    private static final Duration RESERVATION_HOLD = Duration.ofMinutes(15);

    private final TimeSource timeSource;

    public InMemoryInventoryService() {          // production default, chosen at the edge
        this(TimeSource.system());
    }

    public InMemoryInventoryService(TimeSource timeSource) {   // the seam
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource must not be null");
    }

    // Core: builds an effect. Nothing has read the clock yet.
    public IO<Reservation> reserve(OrderId id, List<LineItem> lines) {
        return timeSource.now()
            .map(now -> new Reservation(id, lines, now.plus(RESERVATION_HOLD)));
    }
}
```

The shell runs it (`.unsafeRunSync()`); the core never does. Same rule as every other effect.

### Testing: Advance Time Instead of Sleeping

hkj-test ships `SteppableClock`, a real `java.time.Clock` you move by hand. Combined with
`TimeSource.of(clock)`, a fifteen-minute expiry test costs microseconds and no mock:

```java
var clock = SteppableClock.startingAt(Instant.parse("2026-07-07T00:00:00Z"));
var service = new InMemoryInventoryService(TimeSource.of(clock));

// reserve() returns a lazy IO; the shell runs it, exactly as the section above says
service.reserve(OrderId.generate(), List.of(line("PROD-001", 10))).unsafeRunSync();

clock.advance(Duration.ofMinutes(16));   // past the 15-minute hold - no Thread.sleep

// the expired hold is now reclaimed, deterministically
```

`SteppableClock.startingAt(Instant)` creates it; `clock.advance(Duration)` steps it forward;
`clock.set(Instant)` jumps to an absolute point. For a clock that never moves, skip it and use
`TimeSource.fixed(instant)`.

This is the same thesis as the rest of this skill: a side effect pushed out to a boundary becomes a
value you pass in, and tests become data rather than choreography. No `Mockito.mockStatic(Instant.class)`,
no injectable `Supplier<Instant>` seam bolted on per class, no sleeping.

> `ErrorEnvelope` factories take a `TimeSource` for exactly this reason (`ErrorEnvelope.of(timeSource,
> code, message, context)`), so error records carry a timestamp that tests can pin.

---

## Data-Oriented Programming with HKJ

### Domain Errors as Sealed Hierarchies

```java
public sealed interface OrderError {
    record NotFound(String orderId) implements OrderError {}
    record InvalidItems(List<String> reasons) implements OrderError {}
    record PaymentFailed(String gateway, String reason) implements OrderError {}
    record InsufficientStock(String itemId, int requested, int available) implements OrderError {}
}
```

Benefits: exhaustive `switch`, no `instanceof` chains, structured error data, JSON-serializable.

### Domain State as Records

```java
@GenerateLenses
@GenerateFocus
public record Order(
    OrderId id,
    Customer customer,
    List<LineItem> items,
    OrderStatus status,
    Optional<ShippingAddress> shippingAddress
) {}

public sealed interface OrderStatus {
    record Draft() implements OrderStatus {}
    record Confirmed(Instant confirmedAt) implements OrderStatus {}
    record Shipped(TrackingNumber tracking) implements OrderStatus {}
    record Delivered(Instant deliveredAt) implements OrderStatus {}
    record Cancelled(String reason) implements OrderStatus {}
}
```

---

## Migration Strategy: Pushing Effects Outward

### Step 1: Identify Side Effects in Business Logic

Look for: database calls, HTTP requests, file I/O, logging, random values, system clock reads.

Clock reads are the easiest to miss and the cheapest to fix: swap `Instant.now()` for an injected
`TimeSource` (see above) and the class becomes deterministic before you touch anything else.

### Step 2: Extract Pure Logic

Move validation, calculation, and transformation into pure functions returning `EitherPath`/`ValidationPath`.

### Step 3: Push Effects to the Boundary

Wrap remaining side effects in `IOPath`/`VTaskPath` and compose them at the controller/entry point level.

### Step 4: Use Effect Handlers for Complex Workflows

When you need multiple interpretation modes, promote to `@EffectAlgebra` + Free monad programs.

---

## EffectBoundary: The Named Boundary

`EffectBoundary` is the literal embodiment of FCIS. It sits at the shell, wrapping the interpret-and-execute ceremony:

```
+-----------------------------------------------+
|  Controller (shell)                            |
|                                                |
|  boundary.runIO(program)  ← execution here     |
|      |                                         |
|  +---v---------------------------------------+ |
|  |  Free monad program (functional core)     | |
|  |  Pure business logic, no side effects     | |
|  +-------------------------------------------+ |
+-----------------------------------------------+
```

```java
// Shell: the boundary executes the program
@PostMapping("/orders")
public IOPath<OrderResult> createOrder(@RequestBody OrderRequest req) {
    return boundary.runIO(orderService.processOrder(req));
}

// Core: the service builds a pure program (no execution)
public Free<OrderEffects, OrderResult> processOrder(OrderRequest req) {
    return inventory.check(req.items(), Function.identity())
        .flatMap(stock -> order.create(req, Function.identity()))
        .flatMap(created -> notify.send(created, Function.identity()));
}
```

For testing, `TestBoundary` replaces the production boundary with pure `Id` monad execution. Same program, different boundary, no mocks.

---

## Decision Matrix: Which HKJ Approach at the Boundary?

| Complexity | Approach | When |
|-----------|---------|------|
| Simple | `EitherPath.run()` in controller | CRUD services, single-mode execution |
| Medium | `IOPath.unsafeRun()` at entry point | Deferred effects, resource management |
| Complex | `EffectBoundary.runIO(program)` | Multi-interpretation, composable effects |

---

## Anti-Patterns

| Anti-Pattern | Why It's Bad | Fix |
|-------------|-------------|-----|
| `.unsafeRun()` in the core | Breaks purity, makes testing hard | Move to shell boundary |
| Mixing IO with validation | Side effects during validation | Validate first (pure), then execute |
| Mutable state in domain models | Breaks referential transparency | Use records + optics |
| Catching exceptions in the core | Exceptions are not composable | Use `TryPath` / `EitherPath` |
| `Instant.now()` / `System.currentTimeMillis()` in the core | A hidden effect: non-deterministic, forces sleeping or static mocking in tests | Inject a `TimeSource`; `now()` -> `IO<Instant>` |
| Giant `switch` in controller | Business logic in the shell | Move to core, return Path from service |
