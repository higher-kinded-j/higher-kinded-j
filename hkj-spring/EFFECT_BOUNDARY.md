# EffectBoundary: Effect Composition for Spring Boot

## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Configuration Properties](#configuration-properties)
- [EffectBoundary API Reference](#effectboundary-api-reference)
- [@EnableEffectBoundary](#enableeffectboundary)
- [@Interpreter Annotation](#interpreter-annotation)
- [FreePathReturnValueHandler](#freepathreturnvaluehandler)
- [Testing with TestBoundary and @EffectTest](#testing)
- [Complete Configuration Example](#complete-configuration-example)
- [Comparison: Existing Handlers vs EffectBoundary](#comparison)
- [Troubleshooting](#troubleshooting)
- [Related Documentation](#related-documentation)

## Overview

EffectBoundary bridges Free monad programs into the existing *Path handler ecosystem. It does not create parallel infrastructure; it feeds into what Spring developers already use:

```
FreePath<F, A>  ──foldMap──>  GenericPath<IO.Witness, A>  ──narrow──>  IOPath<A>
                                                                         │
                                             existing IOPathReturnValueHandler
                                                                         │
                                                                   HTTP 200 JSON
```

Controllers returning `IOPath` or `EitherPath` continue to work unchanged. EffectBoundary adds the ability to compose multiple effects into a single Free monad program, interpreted through the same handler pipeline. Existing Jackson serialisation, actuator metrics, and error status mapping all apply automatically.

## Quick Start

### 1. Add the Dependency

```kotlin
dependencies {
    implementation(project(":hkj-spring:starter"))
}
```

### 2. Define Interpreters with `@Interpreter`

```java
@Interpreter(PaymentGatewayOp.class)
public class StripeGatewayInterpreter extends PaymentGatewayOpInterpreter<IOKind.Witness> {
    private final StripeClient client;
    public StripeGatewayInterpreter(StripeClient client) { this.client = client; }

    @Override
    protected <A> Kind<IOKind.Witness, A> onCharge(
            Money amount, PaymentMethod method, Function<ChargeResult, A> k) {
        return IO.of(() -> k.apply(client.charge(amount, method)));
    }
}
```

### 3. Annotate the Application Class

```java
@SpringBootApplication
@EnableEffectBoundary({PaymentGatewayOp.class, FraudCheckOp.class,
                       LedgerOp.class, NotificationOp.class})
public class PaymentApplication {
    public static void main(String[] args) { SpringApplication.run(PaymentApplication.class, args); }
}
```

### 4. Return IOPath or FreePath from Controllers

```java
@RestController @RequestMapping("/api/payments")
public class PaymentController {
    private final EffectBoundary<PaymentEffects> boundary;
    private final PaymentService<PaymentEffects> service;

    public PaymentController(EffectBoundary<PaymentEffects> boundary,
                             PaymentService<PaymentEffects> service) {
        this.boundary = boundary;
        this.service = service;
    }

    @PostMapping
    public IOPath<PaymentResult> processPayment(@RequestBody PaymentRequest req) {
        return boundary.runIO(service.processPayment(req.customer(), req.amount(), req.method()));
    }

    @GetMapping("/{id}/status")
    public FreePath<PaymentEffects, PaymentStatus> getStatus(@PathVariable String id) {
        return service.getPaymentStatus(id);
    }
}
```

`IOPath` is handled by the existing `IOPathReturnValueHandler`. `FreePath` is handled by `FreePathReturnValueHandler`, which looks up the `EffectBoundary` bean, interprets the program, and serialises the result.

## Configuration Properties

### `hkj.effect-boundary.enabled`

- **Type:** `boolean` | **Default:** `true`
- **Effect:** Master switch for EffectBoundary auto-configuration

### `hkj.effect-boundary.startup-validation`

- **Type:** `boolean` | **Default:** `true`
- **Effect:** Fail fast at startup if any declared effect algebra has no matching `@Interpreter` bean

### `hkj.effect-boundary.interpreter-selection`

- **Type:** `Map<String, String>` | **Default:** empty
- **Effect:** Configuration-driven interpreter selection by qualifier (keys are hyphenated algebra names, values are bean qualifiers)

### `hkj.web.free-path-enabled`

- **Type:** `boolean` | **Default:** `true`
- **Effect:** Enable/disable `FreePathReturnValueHandler`

### `hkj.web.free-path-failure-status`

- **Type:** `int` | **Default:** `500`
- **Effect:** HTTP status code returned when FreePath interpretation fails

### `hkj.web.free-path-include-exception-details`

- **Type:** `boolean` | **Default:** `false`
- **Effect:** Include exception class and message in error responses. Enable for development only.

See the [Complete Configuration Example](#complete-configuration-example) section for all properties together, and [CONFIGURATION.md](CONFIGURATION.md) for the full property reference.

## EffectBoundary API Reference

`EffectBoundary<F extends WitnessArity<TypeArity.Unary>>` is the production boundary. It is thread-safe, immutable, and suitable as a Spring singleton. All execution methods target `IOKind.Witness`.

### Execution Methods

**`run(Free<F, A>)`** -- Blocking synchronous execution. Throws on failure.

```java
PaymentResult result = boundary.run(service.processPayment(req));
```

**`run(FreePath<F, A>)`** -- FreePath convenience overload, identical behaviour.

**`runSafe(Free<F, A>)`** -- Captures exceptions as `Try<A>` instead of throwing.

```java
Try<PaymentResult> result = boundary.runSafe(service.processPayment(req));
```

**`runAsync(Free<F, A>)`** -- Non-blocking via `CompletableFuture<A>` on virtual threads.

```java
CompletableFuture<PaymentResult> future = boundary.runAsync(service.processPayment(req));
```

**`runIO(Free<F, A>)`** -- Returns `IOPath<A>` for deferred execution by the existing `IOPathReturnValueHandler`.

```java
@PostMapping
public IOPath<PaymentResult> process(@RequestBody PaymentRequest req) {
    return boundary.runIO(service.processPayment(req));
}
```

### Lifting Methods

**`embed(IO<A>)`** -- Lift an `IO<A>` into `Free<F, A>` for effect composition.

**`embedPath(IO<A>)`** -- Lift an `IO<A>` into `FreePath<F, A>` for the fluent API.

```java
Free<F, Instant> now = boundary.embed(IO.of(Instant::now));
```

### Factory Methods

**`of(Natural<F, IOKind.Witness>)`** -- Create from a combined interpreter.

```java
@Bean
public EffectBoundary<PaymentEffects> paymentBoundary() {
    return EffectBoundary.of(Interpreters.combine(gateway, fraud, ledger, notification));
}
```

**`builder()`** -- Fluent builder for advanced construction.

```java
EffectBoundary<PaymentEffects> boundary = EffectBoundary.<PaymentEffects>builder()
    .interpreter(Interpreters.combine(gateway, fraud, ledger, notification))
    .build();
```

## @EnableEffectBoundary

Replaces manual wiring with a single declaration. The `value` attribute lists the effect algebra classes that compose your effect stack (see the [Quick Start](#quick-start) for usage).

### What the Registrar Does

1. Reads the effect algebra class list from the annotation
2. Resolves each to its generated `*Kind.Witness` type
3. Scans for `@Interpreter`-annotated beans matching each algebra
4. Constructs the `EitherF` nesting order automatically (left-to-right = outer-to-inner)
5. Calls `Interpreters.combine()` with the discovered interpreters
6. Registers `EffectBoundary<ComposedWitness>` as a singleton bean
7. Registers individual `Bound<ComposedWitness>` beans for constructor injection in services
8. Validates at startup: missing interpreter produces a clear error naming the unimplemented algebra

### Multiple Boundary Beans

Use `@Qualifier` to disambiguate when composing separate effect stacks:

```java
@Bean @Qualifier("payments")
public EffectBoundary<PaymentEffects> paymentBoundary() {
    return EffectBoundary.of(Interpreters.combine(gatewayInterp, fraudInterp));
}

@Bean @Qualifier("orders")
public EffectBoundary<OrderEffects> orderBoundary() {
    return EffectBoundary.of(Interpreters.combine(orderInterp, inventoryInterp));
}
```

Inject with `@Qualifier("payments") EffectBoundary<PaymentEffects> boundary` in the controller constructor.

## @Interpreter Annotation

Marks a class as a Spring-managed interpreter for a specific effect algebra. Because `@Interpreter` includes `@Component`, interpreters are full Spring beans and constructor injection works naturally.

- **`value()`** -- The effect algebra class this interpreter handles. The registrar uses this to match interpreters to algebras declared in `@EnableEffectBoundary`.
- **`profile()`** -- Optional Spring profile. When set, the interpreter is only active in that profile, enabling environment-based switching.

### Profile-Based Switching

```java
@Interpreter(value = PaymentGatewayOp.class, profile = "production")
public class StripeGatewayInterpreter extends PaymentGatewayOpInterpreter<IOKind.Witness> {
    // Real Stripe API calls -- constructor-injected StripeClient, etc.
}

@Interpreter(value = PaymentGatewayOp.class, profile = "test")
public class StubGatewayInterpreter extends PaymentGatewayOpInterpreter<IOKind.Witness> {
    @Override
    protected <A> Kind<IOKind.Witness, A> onCharge(
            Money amount, PaymentMethod method, Function<ChargeResult, A> k) {
        return IO.of(() -> k.apply(ChargeResult.approved("STUB-TXN-001")));
    }
}
```

Run with `--spring.profiles.active=test` and the stub interpreter is used automatically.

## FreePathReturnValueHandler

The ninth handler in the hkj-spring handler family, following the same pattern as the existing eight.

### How It Works

1. **Detects** `FreePath` return type on the controller method
2. **Looks up** the `EffectBoundary` bean from the application context
3. **Calls** `boundary.run(program)` to interpret the Free monad program
4. **Serialises** the result as JSON using `JsonMapper`
5. **Maps** errors to HTTP status codes using the existing `ErrorStatusCodeMapper`

### Bean Resolution Strategy

- **Single bean (default):** If exactly one `EffectBoundary` bean exists, it is used automatically
- **Multiple beans:** Fails with a clear error listing available boundaries
- **Escape hatch:** Use `@Qualifier` on the controller's constructor injection to specify which boundary to use

The handler is registered via `HkjWebMvcAutoConfiguration` using `org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations`. It receives `JsonMapper`, status codes, and an optional `@Nullable HkjMetricsService` via constructor injection.

## Testing

### TestBoundary (Pure, No Spring)

`TestBoundary<F extends WitnessArity<TypeArity.Unary>>` interprets programs using the Id monad (`IdKind.Witness`): pure, synchronous, deterministic. No IO, no Spring context. The same service program that runs against real infrastructure in production runs against in-memory stubs here.

```java
class OrderServicePureTest {
    private final RecordingOrderInterpreter orderInterp = new RecordingOrderInterpreter();
    private final RecordingInventoryInterpreter inventoryInterp = new RecordingInventoryInterpreter();
    private final TestBoundary<OrderEffects> boundary = TestBoundary.of(
        Interpreters.combine(orderInterp, inventoryInterp));

    @Test void shouldReserveInventory() {
        OrderResult result = boundary.run(service.placeOrder(new OrderRequest("C001", "ITEM-42", 2)));
        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(inventoryInterp.reservations()).hasSize(1);
    }

    @Test void shouldRejectWhenUnavailable() {
        inventoryInterp.setAvailableStock("ITEM-42", 0);
        OrderResult result = boundary.run(service.placeOrder(new OrderRequest("C001", "ITEM-42", 2)));
        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
    }
}
```

### @EffectTest Test Slice

For integration tests that need Spring auto-configuration but not the web layer. The `effects` parameter auto-discovers `@Interpreter` beans for each listed algebra, combines them, and registers an `EffectBoundary` bean in the test context. No manual boundary wiring needed.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EffectTest(effects = {OrderOp.class, InventoryOp.class})
class OrderServiceSpringTest {

    @Autowired EffectBoundary<...> boundary;
    @Autowired OrderService service;

    @Test
    void shouldPlaceOrder() {
        OrderResult result = boundary.run(service.placeOrder(request));
        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
```

When `effects` is empty (default), no automatic boundary registration occurs, and the test must wire its own boundary. For pure unit tests without any Spring context, use `TestBoundary` directly (see above).

## Complete Configuration Example

```yaml
hkj:
  web:
    either-path-enabled: true
    maybe-path-enabled: true
    try-path-enabled: true
    io-path-enabled: true
    completable-future-path-enabled: true
    vtask-path-enabled: true
    vstream-path-enabled: true
    free-path-enabled: true
    free-path-failure-status: 500
    free-path-include-exception-details: false

  effect-boundary:
    enabled: true
    startup-validation: true
    interpreter-selection:
      payment-gateway: stripe
      fraud-check: ml-model
      notification: email
```

## Comparison

| Aspect | Existing *Path Handlers | EffectBoundary |
|--------|------------------------|----------------|
| **Pattern** | Single effect per return type (`EitherPath`, `IOPath`, etc.) | Composed multi-effect programs via Free monad |
| **Service layer** | Returns `Either<E, A>`, `IO<A>`, or similar | Returns `Free<F, A>` -- a pure program description |
| **Controller return** | `EitherPath<E, A>`, `IOPath<A>`, etc. | `IOPath<A>` (via `runIO`) or `FreePath<F, A>` |
| **Wiring** | None -- handlers registered automatically | `@EnableEffectBoundary` + `@Interpreter` beans |
| **Testing** | Standard Spring test utilities | `TestBoundary` with Id monad -- pure, no Spring context |
| **Domain** | Individual operations | Composed workflows (check inventory, place order, notify) |
| **Adoption** | Use from day one | Progressive -- start with `@Bean`, add annotations later |

## ObservableEffectBoundary (Metrics)

`ObservableEffectBoundary` wraps an `EffectBoundary` with Micrometer metrics recording. When both an `EffectBoundary` bean and `HkjMetricsService` bean are present, every boundary execution records:

| Metric | Type | Description |
|--------|------|-------------|
| `hkj.effect.boundary.invocations{result="success"}` | Counter | Successful executions |
| `hkj.effect.boundary.invocations{result="error"}` | Counter | Failed executions |
| `hkj.effect.boundary.duration` | Timer | Execution time |
| `hkj.effect.boundary.errors{error_type="..."}` | Counter | Error type distribution |

### Registering the Observable Boundary

```java
@Bean
public ObservableEffectBoundary<OrderOpKind.Witness> observableOrderBoundary(
        EffectBoundary<OrderOpKind.Witness> boundary,
        @Nullable HkjMetricsService metricsService) {
    if (metricsService == null) {
        return null;
    }
    return new ObservableEffectBoundary<>(boundary, metricsService);
}
```

### Querying Metrics

```bash
curl http://localhost:8081/actuator/metrics/hkj.effect.boundary.invocations
curl http://localhost:8081/actuator/metrics/hkj.effect.boundary.duration
```

The `ObservableEffectBoundary` provides the same API as `EffectBoundary` (`run()`, `runSafe()`, `runAsync()`, `runIO()`), so controllers can inject either type.

## Troubleshooting

### "No interpreter found for XxxOp"

**Problem:** Application fails to start with a message indicating a missing interpreter.

**Solution:** Ensure the interpreter class is annotated with `@Interpreter(XxxOp.class)` and is within a package scanned by Spring's component scan. If using profile-based switching, verify the active profile matches the interpreter's `profile` attribute.

### "Multiple EffectBoundary beans found"

**Problem:** `FreePathReturnValueHandler` cannot determine which `EffectBoundary` to use.

**Solution:** Use `@Qualifier` on the controller's constructor parameter to specify which boundary to inject, or remove extra bean definitions if only one is needed.

### "FreePathReturnValueHandler not working"

**Problem:** Controllers returning `FreePath` produce unexpected responses or errors.

**Solution:** Verify that `hkj.web.free-path-enabled` is not set to `false` and that an `EffectBoundary` bean exists in the application context. See [CONFIGURATION.md](CONFIGURATION.md) for the full property reference.

### "Interpreter not being discovered"

**Problem:** An `@Interpreter`-annotated class is not picked up by `@EnableEffectBoundary`.

**Solution:** Ensure the interpreter class is within the component scan base packages. If it is in a separate module, add `@ComponentScan(basePackages = {...})` to include the relevant packages.

> **Important**: With `hkj.effect-boundary.startup-validation` set to `true` (the default), a missing interpreter causes the application to fail at startup with a descriptive error. This is the recommended behaviour for production deployments.

## Related Documentation

- [CONFIGURATION.md](CONFIGURATION.md) -- Complete configuration property reference
- [SECURITY.md](SECURITY.md) -- Spring Security integration with functional error handling
- [ACTUATOR.md](ACTUATOR.md) -- Actuator metrics and health checks for functional constructs
- [README.md](README.md) -- hkj-spring module overview
