# EffectBoundary: Composable Effects for Spring Applications
## _Bridging Free Monads into the Spring Ecosystem_

~~~admonish info title="What You'll Learn"
- How EffectBoundary bridges Free monad programs into the existing *Path handler ecosystem
- The progressive adoption ladder, from a single `@Bean` to full `@EnableEffectBoundary` auto-wiring
- Writing effect algebras as Spring-managed `@Interpreter` beans with dependency injection
- Returning `FreePath` and `IOPath` from controllers with automatic HTTP response conversion
- Testing effect programs purely with `TestBoundary` and stub interpreters
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

The existing hkj-spring module has nine return value handlers for *Path types (`FreePath` makes ten). `EffectBoundary.runIO()` returns `IOPath<A>`, which the **existing** `IOPathReturnValueHandler` already handles:

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
| **0** | `Either<E,A>`, `IOPath<A>` from controllers | N/A | None (today) |
| **1** | `EffectBoundary` bean + `boundary.runIO()` | Any `@Bean` | None (core only) |
| **2** | Return `FreePath<F,A>` from controller | `CompletableFuture<T>` return | Handler #10 |
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

An interpreter with a `profile` attribute is only **eligible** when that profile is active (the registrar filters candidates with `Environment.acceptsProfiles`); one with no `profile` is always eligible. A profile-restricted interpreter is more **specific** and shadows an unrestricted one — run with `--spring.profiles.active=test` and the stub is selected for `PaymentGatewayOp` even though the profile-less `StripeGatewayInterpreter` is still eligible. Only two or more interpreters at the *same* specificity (two unrestricted beans, or two active profiled beans) fail startup with a hard error naming the ambiguous algebra.

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
3. Scans for `@Interpreter`-annotated beans matching each algebra, filtering by active profile
4. Constructs the `EitherF` nesting order automatically (left-to-right = outer-to-inner)
5. Calls `Interpreters.combine()` with the discovered interpreters
6. Registers the single `effectBoundary` bean (`EffectBoundary<ComposedWitness>`) as a singleton
7. Validates at startup: a missing interpreter — or two equally-specific eligible interpreters for one algebra — produces a clear error naming the offending algebra

The registrar honours the `hkj.effect-boundary.enabled` master switch (default `true`); when set to `false`, no boundary bean is registered.

This replaces `PaymentEffectsWiring.java` (284 lines) with **one annotation**.

---

## Testing with TestBoundary

`TestBoundary` interprets programs using the Id monad: pure, synchronous, deterministic. No IO, no Spring context, no network calls.

### Pure Service Tests

A stub interpreter targets `IdKind.Witness` and records what it was asked to do. This mirrors `OrderServicePureTest` in the effect-example module:

```java
@DisplayName("OrderService Pure Tests (No Spring Context)")
class OrderServicePureTest {

    /** In-memory Id-targeting interpreter for pure testing. */
    static class StubOrderInterpreter implements Natural<OrderOpKind.Witness, IdKind.Witness> {
        private int ordersPlaced = 0;

        @Override
        public <A> Kind<IdKind.Witness, A> apply(Kind<OrderOpKind.Witness, A> fa) {
            OrderOp<A> op = OrderOpKindHelper.ORDER_OP.narrow(fa);
            return switch (op) {
                case OrderOp.PlaceOrder<A> place -> {
                    ordersPlaced++;
                    yield Id.of(place.k().apply(OrderResult.confirmed("ORD-TEST-0001")));
                }
                case OrderOp.GetStatus<A> get ->
                    Id.of(get.k().apply(OrderStatus.CONFIRMED));
            };
        }

        int ordersPlaced() {
            return ordersPlaced;
        }
    }

    private final StubOrderInterpreter interpreter = new StubOrderInterpreter();
    private final TestBoundary<OrderOpKind.Witness> boundary = TestBoundary.of(interpreter);
    private final OrderService service = new OrderService();

    @Test
    @DisplayName("Should place order and return confirmed result")
    void shouldPlaceOrder() {
        OrderResult result = boundary.run(
            service.placeOrder(new OrderRequest("C001", "ITEM-42", 2)));

        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(interpreter.ordersPlaced()).isEqualTo(1);
    }
}
```

These tests run in milliseconds. The same `OrderService` program that runs against real infrastructure in production runs against in-memory stubs here, with no code changes. Composing several algebras works the same way — build the boundary with `TestBoundary.of(Interpreters.combine(orderStub, inventoryStub, notifyStub))`.

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

    # FreePath handler
    free-path-enabled: true
    free-path-failure-status: 500
    free-path-include-exception-details: false

  effect-boundary:
    enabled: true                     # master switch consulted by the registrar (default: true)
```

Setting `hkj.effect-boundary.enabled: false` prevents the registrar from creating the boundary bean at all. Interpreter validation is always fail-fast: a missing or ambiguous interpreter is a startup error, not a warning — there is no property to relax it. Environment-specific interpreter switching is done with the `@Interpreter(profile = ...)` attribute (see [Level 3](#level-3-interpreters-as-spring-beans)), not with configuration keys.

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

~~~admonish info title="Key Takeaways"
* **EffectBoundary bridges Free monads into existing infrastructure.** `runIO()` returns `IOPath`, which the existing handler converts to HTTP responses. No parallel universe.
* **Adoption is progressive.** Start with a single `@Bean`, then add `@Interpreter`, then `@EnableEffectBoundary`. Each level adds value independently.
* **Interpreters become Spring beans.** They can inject repositories, HTTP clients, and configuration. Profile-based switching replaces manual test/prod wiring.
* **Testing is pure and fast.** `TestBoundary` with stub interpreters runs programs in milliseconds with no Spring context, no IO, and full observability into what effects were executed.
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
