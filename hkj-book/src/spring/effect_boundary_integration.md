# EffectBoundary: Composable Effects for Spring Applications
## _Bridging Free Monads into the Spring Ecosystem_

~~~admonish info title="What You'll Learn"
- How EffectBoundary bridges Free monad programs into the existing *Path handler ecosystem
- The progressive adoption ladder, from a single `@Bean` to full `@EnableEffectBoundary` auto-wiring
- Writing effect algebras as Spring-managed `@Interpreter` beans with dependency injection
- Returning `FreePath` and `IOPath` from controllers with automatic HTTP response conversion
- Testing effect programs purely with `TestBoundary` and recording interpreters
- How this integrates with existing hkj-spring features (actuator, Jackson, error status mapping)
~~~

~~~admonish example title="Example Application"
A complete working example is available in the [hkj-spring effect-example module](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-spring/effect-example). Run it with:
```bash
./gradlew :hkj-spring:effect-example:bootRun
```
~~~

## Overview

The previous pages showed how to return `Either`, `Validated`, and `IOPath` directly from Spring controllers. That approach works well for individual operations, but enterprise applications often need to compose multiple effects: check inventory, place an order, send a notification, all as a single program that can be interpreted differently for production, testing, and auditing.

The **effect handler system** (Free monads, `@EffectAlgebra`, `Interpreters.combine()`) solves the composition problem. But wiring it into Spring has been manual and verbose. `PaymentEffectsWiring.java` in the examples module is 284 lines of ceremony: nested functor composition, `@SuppressWarnings("unchecked")` inject chains, and explicit `boundTo()` calls.

`EffectBoundary` eliminates that ceremony. It encapsulates the interpret-and-execute pattern, and the Spring integration auto-discovers interpreters, combines them, and registers the boundary as a bean. The key insight is that **EffectBoundary does not create new infrastructure; it bridges Free monads into the existing *Path type ecosystem** that hkj-spring already handles.

---

## The Bridge: How Free Connects to Existing Handlers

The existing hkj-spring module has 8 return value handlers for *Path types. `EffectBoundary.runIO()` returns `IOPath<A>`, which the **existing** `IOPathReturnValueHandler` already handles:

```
FreePath<F, A>  ──foldMap──▸  GenericPath<IO.Witness, A>  ──narrow──▸  IOPath<A>
                                                                          │
                                              existing IOPathReturnValueHandler
                                                                          │
                                                                    HTTP 200 JSON
```

This means you can use `EffectBoundary` on **day one** without any new Spring infrastructure. The existing Jackson serialisation, actuator metrics, and error status mapping all apply automatically.

---

## The Adoption Ladder

Each level uses patterns Spring developers already know. No level requires the previous one.

| Level | What You Write | Spring Analogy | Change Required |
|-------|---------------|----------------|-----------------|
| **0** | `Either<E,A>`, `IOPath<A>` from controllers | — | None (today) |
| **1** | `EffectBoundary` bean + `boundary.runIO()` | Any `@Bean` | None (core only) |
| **2** | Return `FreePath<F,A>` from controller | `CompletableFuture<T>` return | Handler #9 |
| **3** | `@Interpreter(MyOp.class)` on interpreter classes | `@Repository`, `@Service` | Stereotype |
| **4** | `@EnableEffectBoundary({...})` on app class | `@EnableCaching` | Auto-config |
| **5** | `@EffectTest(effects={...})` on test class | `@WebMvcTest` | Test slice |
| **6** | Metrics appear automatically | Existing actuator | Extends metrics |

---

## Level 1: EffectBoundary as a Bean

The simplest adoption path. Create an `EffectBoundary` bean manually and use it in your service or controller.

### Step 1: Define the Boundary Bean

```java
@Configuration
public class PaymentConfig {

    @Bean
    public EffectBoundary<PaymentEffects> paymentBoundary() {
        return EffectBoundary.of(Interpreters.combine(
            new ProductionGatewayInterpreter(),
            new ProductionFraudInterpreter(),
            new ProductionLedgerInterpreter(),
            new ProductionNotificationInterpreter()
        ));
    }
}
```

### Step 2: Use It in a Controller

```java
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final EffectBoundary<PaymentEffects> boundary;
    private final PaymentService<PaymentEffects> service;

    public PaymentController(
            EffectBoundary<PaymentEffects> boundary,
            PaymentService<PaymentEffects> service) {
        this.boundary = boundary;
        this.service = service;
    }

    @PostMapping
    public IOPath<PaymentResult> processPayment(@RequestBody PaymentRequest req) {
        return boundary.runIO(
            service.processPayment(req.customer(), req.amount(), req.method()));
    }
}
```

The controller returns `IOPath<PaymentResult>`, which the **existing** `IOPathReturnValueHandler` converts to an HTTP 200 response with a JSON body. No new handler needed.

### What Changed from Manual Wiring

| Aspect | Before (Manual) | After (EffectBoundary) |
|--------|-----------------|----------------------|
| Wiring | 284 lines in PaymentEffectsWiring.java | One `@Bean` definition |
| Execution | `program.foldMap(interp, monad)` then `narrow().unsafeRunSync()` | `boundary.runIO(program)` |
| Controller return | Manual conversion to HTTP response | Return `IOPath`, handler does the rest |
| Type signatures | `EitherFKind.Witness<F, EitherFKind.Witness<G, ...>>` | Hidden inside the boundary |

---

## Level 2: FreePath as a Controller Return Type

Once the `FreePathReturnValueHandler` is registered (auto-configured by the starter), controllers can return `FreePath` directly:

```java
@GetMapping("/{id}/status")
public FreePath<PaymentEffects, PaymentStatus> getStatus(@PathVariable String id) {
    return service.getPaymentStatus(id);
    // The handler interprets the program and serialises the result
}
```

The handler detects the `FreePath` return type, looks up the `EffectBoundary` bean from the application context, calls `boundary.run()`, and writes the JSON response.

Configure it like any other handler:

```yaml
hkj:
  web:
    free-path-enabled: true             # default: true
    free-path-failure-status: 500       # default: 500
    free-path-include-exception-details: false
```

---

## Level 3: Interpreters as Spring Beans

Mark interpreter classes with `@Interpreter` to make them Spring-managed beans:

```java
@Interpreter(PaymentGatewayOp.class)
public class StripeGatewayInterpreter
        extends PaymentGatewayOpInterpreter<IOKind.Witness> {

    private final StripeClient client;

    public StripeGatewayInterpreter(StripeClient client) {
        this.client = client;  // Spring constructor injection
    }

    @Override
    protected <A> Kind<IOKind.Witness, A> onCharge(Money amount, PaymentMethod method,
                                                    Function<ChargeResult, A> k) {
        return IO.of(() -> k.apply(client.charge(amount, method)));
    }
}
```

**Profile-based switching** replaces interpreters for different environments:

```java
@Interpreter(value = PaymentGatewayOp.class, profile = "test")
public class StubGatewayInterpreter
        extends PaymentGatewayOpInterpreter<IOKind.Witness> {

    @Override
    protected <A> Kind<IOKind.Witness, A> onCharge(Money amount, PaymentMethod method,
                                                    Function<ChargeResult, A> k) {
        return IO.of(() -> k.apply(ChargeResult.approved("STUB-TXN-001")));
    }
}
```

Run with `--spring.profiles.active=test` and the stub interpreter is used automatically.

---

## Level 4: Full Auto-Wiring

One annotation on your application class replaces all manual wiring:

```java
@SpringBootApplication
@EnableEffectBoundary({PaymentGatewayOp.class, FraudCheckOp.class,
                       LedgerOp.class, NotificationOp.class})
public class PaymentApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
```

The `@EnableEffectBoundary` registrar:
1. Reads the effect algebra class list from the annotation
2. Resolves each to its generated `*Kind.Witness` type
3. Scans for `@Interpreter`-annotated beans matching each algebra
4. Constructs the `EitherF` nesting order automatically (left-to-right = outer-to-inner)
5. Calls `Interpreters.combine()` with the discovered interpreters
6. Registers `EffectBoundary<ComposedWitness>` as a singleton bean
7. Registers individual `Bound<ComposedWitness>` beans for constructor injection in services
8. Validates at startup: missing interpreter produces a clear error naming the unimplemented algebra

This replaces `PaymentEffectsWiring.java` (284 lines) with **one annotation**.

---

## Testing with TestBoundary

`TestBoundary` interprets programs using the Id monad: pure, synchronous, deterministic. No IO, no Spring context, no network calls.

### Pure Service Tests

```java
@DisplayName("OrderService Pure Tests")
class OrderServicePureTest {

    private final RecordingOrderInterpreter orderInterp =
        new RecordingOrderInterpreter();
    private final RecordingInventoryInterpreter inventoryInterp =
        new RecordingInventoryInterpreter();
    private final RecordingNotificationInterpreter notifInterp =
        new RecordingNotificationInterpreter();

    private final TestBoundary<OrderEffects> boundary = TestBoundary.of(
        Interpreters.combine(orderInterp, inventoryInterp, notifInterp));

    @Test
    @DisplayName("Should reserve inventory before placing order")
    void shouldReserveInventoryBeforePlacingOrder() {
        OrderResult result = boundary.run(
            service.placeOrder(new OrderRequest("C001", "ITEM-42", 2)));

        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(inventoryInterp.reservations()).hasSize(1);
        assertThat(notifInterp.sentNotifications()).hasSize(1);
    }

    @Test
    @DisplayName("Should not place order when inventory unavailable")
    void shouldNotPlaceOrderWhenInventoryUnavailable() {
        inventoryInterp.setAvailableStock("ITEM-42", 0);

        OrderResult result = boundary.run(
            service.placeOrder(new OrderRequest("C001", "ITEM-42", 2)));

        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(orderInterp.placedOrders()).isEmpty();
    }
}
```

These tests run in milliseconds. The same `OrderService` program that runs against Stripe and PostgreSQL in production runs against in-memory stubs here, with no code changes.

### @EffectTest Test Slice

For integration tests that need Spring auto-configuration but not the web layer, `@EffectTest(effects={...})` auto-discovers `@Interpreter` beans, combines them, and registers an `EffectBoundary` in the test context:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EffectTest(effects = {OrderOp.class})
class OrderServiceSpringTest {

    @Autowired EffectBoundary<OrderOpKind.Witness> boundary;
    @Autowired OrderService service;

    @Test
    void shouldPlaceOrder() {
        OrderResult result = boundary.run(
            service.placeOrder(new OrderRequest("C001", "ITEM-42", 2)));

        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
```

---

## The Full Stack

```
@EnableEffectBoundary({Order, Inventory, Notification})
          │
          ▼
    EffectBoundaryRegistrar               (auto-discovers @Interpreter beans)
          │
          ▼
    Interpreters.combine(...)             (auto-composed Natural<F, IO.Witness>)
          │
          ▼
    EffectBoundary<ComposedWitness> bean  (registered as singleton)
          │
          ├──▸ OrderService (injected with Bound<> instances)
          │         │
          │         ▼
          │    Free<G, OrderResult>       (pure program description)
          │         │
          ▼         ▼
    boundary.runIO(program)  ──▸  IOPath<OrderResult>
                                       │
                          IOPathReturnValueHandler (EXISTING)
                                       │
                                  HTTP 200 JSON

    OR:

    controller returns FreePath<G, OrderResult>
                                       │
                        FreePathReturnValueHandler (NEW)
                                       │
                          boundary.run(program)
                                       │
                                  HTTP 200 JSON
```

---

## Configuration

All configuration is optional. EffectBoundary works with sensible defaults.

```yaml
hkj:
  web:
    # Existing handlers (unchanged)
    either-path-enabled: true
    maybe-path-enabled: true

    # New: FreePath handler
    free-path-enabled: true
    free-path-failure-status: 500
    free-path-include-exception-details: false

  effect-boundary:
    enabled: true                     # master switch
    startup-validation: true          # fail-fast if interpreters missing
    interpreter-selection:            # config-driven interpreter switching
      payment-gateway: stripe
      fraud-check: ml-model
      notification: email
```

---

## Transactions

`EffectBoundary.run()` is a synchronous call, so it participates naturally in Spring's transaction lifecycle:

```java
@Service
public class OrderService {

    private final EffectBoundary<OrderEffects> boundary;

    @Transactional
    public OrderResult placeOrder(OrderRequest request) {
        return boundary.run(orderProgram(request));
        // Transaction commits only if all effects succeed
        // Rolls back on any interpreter failure
    }
}
```

No special integration needed. This works with the basic `EffectBoundary`.

---

## Metrics with ObservableEffectBoundary

`ObservableEffectBoundary` wraps an `EffectBoundary` with Micrometer metrics. When actuator is on the classpath, every boundary execution records success/error counters and execution duration:

```java
@Bean
public ObservableEffectBoundary<OrderEffects> observableBoundary(
        EffectBoundary<OrderEffects> boundary,
        @Nullable HkjMetricsService metricsService) {
    if (metricsService == null) {
        return null;
    }
    return new ObservableEffectBoundary<>(boundary, metricsService);
}
```

The recorded metrics follow the same naming conventions as existing hkj-spring actuator metrics:

| Metric | Description |
|--------|-------------|
| `hkj.effect.boundary.invocations{result="success"}` | Successful boundary executions |
| `hkj.effect.boundary.invocations{result="error"}` | Failed boundary executions |
| `hkj.effect.boundary.duration` | Execution time per invocation |

These appear automatically in the `/actuator/metrics` endpoint alongside existing hkj metrics for Either, Validated, VTask, and VStream handlers.

---

## Pure Tests with TestBoundary

For unit tests that verify business logic without Spring or IO, `TestBoundary` interprets programs using the Id monad. Tests execute in milliseconds and are fully deterministic:

```java
@DisplayName("OrderService Pure Tests")
class OrderServicePureTest {

    private final StubOrderInterpreter interpreter = new StubOrderInterpreter();
    private final TestBoundary<OrderEffects> boundary = TestBoundary.of(interpreter);

    @Test
    void shouldPlaceOrder() {
        OrderResult result = boundary.run(
            service.placeOrder(new OrderRequest("C001", "ITEM-42", 2)));

        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(interpreter.ordersPlaced()).isEqualTo(1);
    }
}
```

The stub interpreter targets `IdKind.Witness` instead of `IOKind.Witness`, returning pure `Id` values. The same `Free<F, A>` program that runs against real services in production runs against stubs here. Only the interpreter and boundary change; the program is identical.

For integration tests that need Spring auto-configuration but not the web layer, use `@EffectTest(effects={...})` to auto-discover interpreters and register an `EffectBoundary` bean:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EffectTest(effects = {OrderOp.class})
class OrderServiceSpringTest {

    @Autowired EffectBoundary<OrderOpKind.Witness> boundary;
    @Autowired OrderService service;

    @Test
    void shouldProcessOrder() {
        OrderResult result = boundary.run(service.placeOrder(request));
        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
```

`@EffectTest` discovers `@Interpreter` beans matching each listed effect algebra, combines them, and registers the boundary. No manual wiring needed. When the `effects` parameter is empty, no automatic boundary registration occurs.

---

~~~admonish info title="Key Takeaways"
* **EffectBoundary bridges Free monads into existing infrastructure.** `runIO()` returns `IOPath`, which the existing handler converts to HTTP responses. No parallel universe.
* **Adoption is progressive.** Start with a single `@Bean`, then add `@Interpreter`, then `@EnableEffectBoundary`. Each level adds value independently.
* **Interpreters become Spring beans.** They can inject repositories, HTTP clients, and configuration. Profile-based switching replaces manual test/prod wiring.
* **Testing is pure and fast.** `TestBoundary` with recording interpreters runs programs in milliseconds with no Spring context, no IO, and full observability into what effects were executed.
* **The same program runs everywhere.** Production interprets into IO with real services. Tests interpret into Id with stubs. Auditing wraps interpreters with logging. The program itself never changes.
~~~

~~~admonish tip title="See Also"
- [Spring Boot Integration](spring_boot_integration.md) - Using Either, Validated, and *Path types in controllers
- [Migrating to Functional Errors](migrating_to_functional_errors.md) - Incremental migration from exceptions
- [FreePath](../effect/path_free.md) - The Free monad fluent API
- [Effect Handlers](../effect/effect_handlers.md) - Effect algebra reference
~~~

---

**Previous:** [Migrating to Functional Errors](migrating_to_functional_errors.md)
