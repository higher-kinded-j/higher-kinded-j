# Effect Boundary Example

This example application demonstrates how to use `EffectBoundary` to bridge Free monad programs into a Spring Boot REST API.

It is a companion to the existing `hkj-spring/example/` module, which shows how to return *Path types (Either, Validated, IOPath, etc.) directly from controllers. This module goes a step further: it shows how to **describe** business logic as composable Free monad programs, then **interpret** them at a clean boundary using `EffectBoundary`.

## The Problem

Enterprise applications often compose multiple effects: check inventory, place an order, send a notification. With traditional Spring, this means scattered try-catch blocks, manual transaction management, and tightly coupled service layers. The effect handler system solves the composition problem with Free monads, but wiring interpreters into Spring has historically been verbose (see `PaymentEffectsWiring.java` in hkj-examples: 284 lines of ceremony for 4 effects).

## The Solution

`EffectBoundary` encapsulates the interpret-and-execute pattern. A service builds a pure `Free<F, A>` program describing what to do. A controller passes that program to `boundary.runIO()`, which returns an `IOPath<A>`. The existing `IOPathReturnValueHandler` (already part of hkj-spring) converts the result to an HTTP response. No new infrastructure is needed.

## What This Example Shows

The example models an order processing system with three effect algebras:

- **OrderOp** — place orders and query their status
- **InventoryOp** — check and reserve stock
- **NotifyOp** — send order confirmation notifications

Each effect algebra is annotated with `@EffectAlgebra`, which triggers code generation for the HKT wrappers (Kind, KindHelper, Functor classes). Each has an in-memory interpreter annotated with `@Interpreter`, making it a Spring bean with full dependency injection support.

| Layer | File | Role |
|-------|------|------|
| Effect algebras | `effect/OrderOp.java`, `InventoryOp.java`, `NotifyOp.java` | Define operations using continuation-passing style |
| Interpreters | `interpreter/InMemoryOrderInterpreter.java`, etc. | Implement operations targeting the IO monad, discovered via `@Interpreter` |
| Service | `service/OrderService.java` | Builds `Free<F, A>` programs without executing them |
| Controller | `controller/OrderController.java` | Calls `boundary.runIO(program)` and returns `IOPath<A>` |
| Application | `EffectExampleApplication.java` | Defines the `EffectBoundary` and `ObservableEffectBoundary` beans |
| Metrics | `ObservableEffectBoundary` | Wraps boundary with success/error/duration metrics via actuator |
| Pure tests | `OrderServicePureTest.java` | `TestBoundary` with Id monad — no Spring, no IO, millisecond execution |
| Integration tests | `EffectExampleIntegrationTest.java` | Full MockMvc HTTP tests with Spring context |

## Running

```bash
./gradlew :hkj-spring:effect-example:bootRun
```

The application starts on port 8081.

## Endpoints

### Place an Order

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"C001","itemId":"ITEM-42","quantity":2}'
```

Response:
```json
{"orderId":"ORD-a1b2c3d4","status":"CONFIRMED","message":"Order confirmed"}
```

### Get Order Status

```bash
curl http://localhost:8081/api/orders/ORD-a1b2c3d4/status
```

Response:
```json
"CONFIRMED"
```

Unknown orders return `"PENDING"`.

## How It Works

```
OrderRequest
     │
     ▼
OrderService.placeOrder(request)           ← builds a pure program
     │
     ▼
Free<OrderOpKind.Witness, OrderResult>     ← program description (no side effects)
     │
     ▼
boundary.runIO(program)                    ← interprets via Natural<F, IO.Witness>
     │
     ▼
IOPath<OrderResult>                        ← deferred IO (not yet executed)
     │
     ▼
IOPathReturnValueHandler                   ← existing hkj-spring handler
     │
     ▼
HTTP 200 JSON                              ← result serialised by Jackson
```

The key insight is that `EffectBoundary` does not create new Spring infrastructure. It bridges Free monads into the existing *Path handler ecosystem that hkj-spring already provides. The same Jackson serialisation, actuator metrics, and error status mapping all apply automatically.

## Metrics with ObservableEffectBoundary

The application registers an `ObservableEffectBoundary` bean that wraps the boundary with Micrometer metrics. When actuator is on the classpath, every boundary execution records:

- `hkj.effect.boundary.invocations{result="success"}` — successful executions
- `hkj.effect.boundary.invocations{result="error"}` — failed executions
- `hkj.effect.boundary.duration` — execution time
- `hkj.effect.boundary.errors{error_type="..."}` — error type distribution

Query metrics after placing some orders:

```bash
curl http://localhost:8081/actuator/metrics/hkj.effect.boundary.invocations
```

The `ObservableEffectBoundary` is defined as a `@Bean` in `EffectExampleApplication`. Controllers can inject it instead of the plain `EffectBoundary` when instrumented execution is desired.

## Testing

The example includes two styles of testing, demonstrating the testing story for effect programs.

### Pure Tests with TestBoundary (No Spring)

`OrderServicePureTest` demonstrates the `TestBoundary` pattern. It uses the Id monad instead of IO, so tests execute purely, synchronously, and in milliseconds. No Spring context, no network, no IO.

```java
// Stub interpreter targets Id monad — pure values, no side effects
var interpreter = new StubOrderInterpreter();
var boundary = TestBoundary.of(interpreter);

OrderResult result = boundary.run(service.placeOrder(request));

assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
assertThat(interpreter.ordersPlaced()).isEqualTo(1);
```

The same `OrderService` program that runs against real databases and HTTP services in production runs against in-memory stubs here. Only the interpreter changes; the program itself is identical.

### @EffectTest Slice (Auto-Wired Boundary, No Web Layer)

`EffectTestSliceTest` demonstrates the `@EffectTest(effects={...})` test slice. The annotation auto-discovers the `@Interpreter(OrderOp.class)` bean, combines interpreters, and registers an `EffectBoundary` bean in the test context. No manual boundary wiring needed.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EffectTest(effects = {OrderOp.class})
class EffectTestSliceTest {

    @Autowired EffectBoundary<OrderOpKind.Witness> boundary;
    @Autowired OrderService service;

    @Test
    void shouldPlaceOrder() {
        OrderResult result = boundary.run(service.placeOrder(request));
        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
```

This is faster than full MockMvc tests because no web layer is loaded, while still using Spring's dependency injection to discover and wire interpreters.

### Integration Tests with MockMvc

`EffectExampleIntegrationTest` verifies the full HTTP request/response cycle, including EffectBoundary interpretation, IOPath execution, and JSON serialisation. It uses `@SpringBootTest` with `@AutoConfigureMockMvc`.

Run all tests:

```bash
./gradlew :hkj-spring:effect-example:test
```

## Project Structure

```
effect-example/
├── build.gradle.kts
├── src/main/
│   ├── java/org/higherkindedj/spring/effect/example/
│   │   ├── EffectExampleApplication.java       # @SpringBootApplication + @Bean boundary
│   │   ├── effect/
│   │   │   ├── OrderOp.java                    # @EffectAlgebra: place/query orders
│   │   │   ├── InventoryOp.java                # @EffectAlgebra: check/reserve stock
│   │   │   └── NotifyOp.java                   # @EffectAlgebra: send confirmations
│   │   ├── interpreter/
│   │   │   ├── InMemoryOrderInterpreter.java   # @Interpreter(OrderOp.class)
│   │   │   ├── InMemoryInventoryInterpreter.java
│   │   │   └── LoggingNotifyInterpreter.java
│   │   ├── service/
│   │   │   └── OrderService.java               # Builds Free<G, OrderResult> programs
│   │   ├── controller/
│   │   │   └── OrderController.java            # boundary.runIO() → IOPath<A>
│   │   └── domain/
│   │       ├── OrderRequest.java
│   │       ├── OrderResult.java
│   │       └── OrderStatus.java
│   └── resources/
│       └── application.yml
└── src/test/
    └── java/.../
        ├── EffectExampleIntegrationTest.java   # MockMvc integration tests
        ├── EffectTestSliceTest.java            # @EffectTest slice (auto-wired, no web)
        └── OrderServicePureTest.java           # TestBoundary pure tests (no Spring)
```

## The Adoption Ladder

This example demonstrates **Level 1** of the progressive adoption ladder. Each level builds on familiar Spring patterns, and no level requires the previous one.

| Level | What You Write | Spring Analogy | This Example |
|-------|---------------|----------------|:------------:|
| **0** | Return `Either`, `IOPath` directly from controllers | — | — |
| **1** | `EffectBoundary` bean + `boundary.runIO()` → `IOPath` | Any `@Bean` | **Yes** |
| **2** | Return `FreePath` from controller (auto-interpreted by handler) | `CompletableFuture<T>` return | — |
| **3** | `@Interpreter` beans with Spring DI and profile switching | `@Repository`, `@Service` | **Yes** |
| **4** | `@EnableEffectBoundary` auto-wiring on application class | `@EnableCaching` | — |

To upgrade this example to Level 4, replace the manual `@Bean` in `EffectExampleApplication` with:

```java
@SpringBootApplication
@EnableEffectBoundary({OrderOp.class})
public class EffectExampleApplication { }
```

The `@EnableEffectBoundary` registrar discovers the `@Interpreter` bean automatically, combines interpreters, and registers the `EffectBoundary` as a singleton.

## Testing

```bash
./gradlew :hkj-spring:effect-example:test
```

The integration test uses MockMvc to verify the full HTTP request/response cycle, including Free monad interpretation via the boundary.

## Configuration

All configuration in `application.yml` is optional. Defaults are designed to work out of the box. See [EFFECT_BOUNDARY.md](../EFFECT_BOUNDARY.md) for the full properties reference.

## Related

- [EffectBoundary Reference](../EFFECT_BOUNDARY.md) — Spring module configuration reference
- [EffectBoundary Book Chapter](../../hkj-book/src/spring/effect_boundary_integration.md) — tutorial-style guide
- [Existing Example](../example/) — *Path return value handlers (Level 0)
