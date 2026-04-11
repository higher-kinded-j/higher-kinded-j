---
name: hkj-spring
description: "HKJ Spring Boot integration: hkj-spring-boot-starter, Either/Validated as REST responses, Jackson 3.x serialization, auto-configuration, EitherHttpMessageConverter, functional error handling in @RestController, domain error to HTTP status mapping"
---

# Higher-Kinded-J Spring Boot Integration

You are helping a developer integrate HKJ with Spring Boot. The `hkj-spring-boot-starter` enables returning `Either`, `Validated`, `CompletableFuturePath`, `VTaskPath`, and `VStreamPath` directly from Spring controllers with automatic HTTP response conversion.

## When to load supporting files

- If the user wants a **complete working example**, load `reference/spring-example.md`
- For **Effect Path API basics**, suggest `/hkj-guide`
- For **architecture patterns** (functional core / imperative shell), suggest `/hkj-arch`

---

## Quick Start

### 1. Add the Starter

```gradle
// build.gradle.kts
dependencies {
    implementation("io.github.higher-kinded-j:hkj-spring-boot-starter:LATEST_VERSION")
}
```

```xml
<!-- Maven -->
<dependency>
    <groupId>io.github.higher-kinded-j</groupId>
    <artifactId>hkj-spring-boot-starter</artifactId>
    <version>LATEST_VERSION</version>
</dependency>
```

### 2. Return Functional Types from Controllers

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired private UserService userService;

    @GetMapping("/{id}")
    public Either<DomainError, User> getUser(@PathVariable String id) {
        return userService.findById(id);
        // Right(user) -> HTTP 200 with JSON body
        // Left(UserNotFoundError) -> HTTP 404 with error details
    }

    @PostMapping
    public Validated<List<ValidationError>, User> createUser(@RequestBody UserRequest request) {
        return userService.validateAndCreate(request);
        // Valid(user) -> HTTP 200 with user JSON
        // Invalid(errors) -> HTTP 400 with all validation errors
    }
}
```

### 3. That's It

The starter auto-configures everything: response conversion, JSON serialization, status code mapping.

---

## Auto-Configuration: What You Get

| Feature | Description |
|---------|-------------|
| `Either` response conversion | `Right(value)` -> 200, `Left(error)` -> mapped status code |
| `Validated` response conversion | `Valid(value)` -> 200, `Invalid(errors)` -> 400 |
| `CompletableFuturePath` async | Async operations with functional error handling |
| `VTaskPath` async | Virtual thread async via `DeferredResult` |
| `VStreamPath` SSE | Server-Sent Events streaming on virtual threads |
| Jackson serialization | JSON support for Either, Maybe, Try, Validated |
| Status code mapping | Customizable domain error -> HTTP status mapping |
| Actuator integration | Monitoring functional operations |

---

## HTTP Status Code Mapping

### Default Behaviour

By default, `Left` errors produce HTTP 500. Customize by implementing `ErrorStatusMapper`:

```java
@Component
public class DomainErrorMapper implements ErrorStatusMapper {

    @Override
    public int statusCodeFor(Object error) {
        return switch (error) {
            case DomainError.NotFound nf    -> 404;
            case DomainError.Forbidden f    -> 403;
            case DomainError.Validation v   -> 400;
            case DomainError.Conflict c     -> 409;
            default                         -> 500;
        };
    }
}
```

### Using Sealed Interfaces for Domain Errors

```java
public sealed interface DomainError {
    record NotFound(String resource, String id) implements DomainError {}
    record Validation(List<String> errors) implements DomainError {}
    record Forbidden(String reason) implements DomainError {}
    record Conflict(String message) implements DomainError {}
}
```

Sealed interfaces + pattern matching = exhaustive status code mapping at compile time.

---

## Controller Return Types

### Either: Success or Typed Error

```java
@GetMapping("/{id}")
public Either<DomainError, User> getUser(@PathVariable String id) {
    return userService.findById(id);
}
```

### Validated: Accumulating All Errors

```java
@PostMapping
public Validated<List<String>, User> createUser(@RequestBody UserRequest req) {
    return userService.validateAndCreate(req);
    // Accumulates ALL validation errors, not just the first
}
```

### CompletableFuturePath: Async

```java
@GetMapping("/{id}")
public CompletableFuturePath<Either<DomainError, User>> getUserAsync(@PathVariable String id) {
    return Path.futureAsync(() -> userService.findById(id));
}
```

### VTaskPath: Virtual Thread Async

```java
@GetMapping("/{id}")
public DeferredResult<Either<DomainError, User>> getUserVTask(@PathVariable String id) {
    VTaskPath<Either<DomainError, User>> task = Path.vtask(() -> userService.findById(id));
    // Framework converts VTaskPath to DeferredResult automatically
}
```

### VStreamPath: Server-Sent Events

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamUpdates() {
    VStreamPath<Update> stream = updateService.updates();
    // Framework converts VStreamPath to SSE automatically
}
```

---

## Service Layer Patterns

### Repository Access

```java
@Service
public class UserService {

    public Either<DomainError, User> findById(String id) {
        return Path.maybe(repository.findById(id))
            .toEitherPath(new DomainError.NotFound("user", id))
            .run();
    }

    public Validated<List<String>, User> validateAndCreate(UserRequest req) {
        return Path.valid(req.name(), Semigroup.listSemigroup())
            .via(name -> validateName(name))
            .zipWithAccum(
                validateEmail(req.email()),
                (name, email) -> new User(name, email))
            .run();
    }
}
```

### Composing Service Calls

```java
public Either<DomainError, OrderResult> processOrder(String userId, OrderRequest req) {
    return Path.<DomainError, User>right(null)
        .then(() -> findUser(userId))
        .via(user -> validateOrder(req)
            .mapError(DomainError.Validation::new))
        .via(order -> chargePayment(user, order)
            .mapError(DomainError.PaymentFailed::new))
        .map(payment -> new OrderResult(payment.id()))
        .run();
}
```

---

## Migration from Exception-Based Code

### Step 1: New Endpoints Use Either

Keep existing `@ExceptionHandler` endpoints working. All new endpoints return `Either`.

### Step 2: Convert Service Methods

```java
// Before: throws UserNotFoundException
public User findById(String id) {
    return repository.findById(id)
        .orElseThrow(() -> new UserNotFoundException(id));
}

// After: returns Either
public Either<DomainError, User> findById(String id) {
    return Path.maybe(repository.findById(id))
        .toEitherPath(new DomainError.NotFound("user", id))
        .run();
}
```

### Step 3: Convert Controllers

```java
// Before
@GetMapping("/{id}")
public User getUser(@PathVariable String id) {
    return userService.findById(id);  // What errors can this throw?
}

// After
@GetMapping("/{id}")
public Either<DomainError, User> getUser(@PathVariable String id) {
    return userService.findById(id);  // Errors explicit in return type
}
```

### Step 4: Remove `@ExceptionHandler` Methods

Once all endpoints are migrated, remove exception handlers.

---

## Testing

```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;

    @Test
    void getUser_returnsUser() throws Exception {
        when(userService.findById("1"))
            .thenReturn(Either.right(new User("Alice")));

        mockMvc.perform(get("/api/users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void getUser_returns404() throws Exception {
        when(userService.findById("99"))
            .thenReturn(Either.left(new DomainError.NotFound("user", "99")));

        mockMvc.perform(get("/api/users/99"))
            .andExpect(status().isNotFound());
    }
}
```

---

## EffectBoundary: Composable Effects in Spring

For services that compose multiple effect algebras (e.g., inventory + orders + notifications), `EffectBoundary` bridges Free monad programs into the existing *Path handler ecosystem.

### The Adoption Ladder

| Level | What You Write | Spring Analogy |
|-------|---------------|----------------|
| 0 | `Either<E,A>`, `IOPath<A>` from controllers | Today's patterns |
| 1 | `EffectBoundary` bean + `boundary.runIO()` | Any `@Bean` |
| 2 | Return `FreePath<F,A>` from controller | `CompletableFuture<T>` return |
| 3 | `@Interpreter(MyOp.class)` on interpreter classes | `@Repository`, `@Service` |
| 4 | `@EnableEffectBoundary({...})` on app class | `@EnableCaching` |
| 5 | `@EffectTest(effects={...})` on test class | `@WebMvcTest` |

### Level 1: Manual Bean

```java
@Configuration
public class OrderConfig {
    @Bean
    public EffectBoundary<OrderEffects> orderBoundary() {
        return EffectBoundary.of(Interpreters.combine(
            new ProductionOrderInterpreter(),
            new ProductionInventoryInterpreter(),
            new ProductionNotificationInterpreter()));
    }
}

@PostMapping("/orders")
public IOPath<OrderResult> createOrder(@RequestBody OrderRequest req) {
    return boundary.runIO(orderService.processOrder(req));
    // IOPath handled by existing IOPathReturnValueHandler -> HTTP 200 JSON
}
```

### Level 3: Interpreters as Spring Beans

```java
@Interpreter(OrderOp.class)
public class JpaOrderInterpreter extends OrderOpInterpreter<IOKind.Witness> {
    private final OrderRepository repo;  // Spring constructor injection

    public JpaOrderInterpreter(OrderRepository repo) { this.repo = repo; }

    @Override
    protected <A> Kind<IOKind.Witness, A> handleCreateOrder(OrderOp.CreateOrder<A> op) {
        return IO.of(() -> op.k().apply(repo.save(op.request())));
    }
}
```

Profile-based switching:
```java
@Interpreter(value = OrderOp.class, profile = "test")
public class StubOrderInterpreter extends OrderOpInterpreter<IOKind.Witness> { ... }
```

### Level 4: Full Auto-Wiring

```java
@SpringBootApplication
@EnableEffectBoundary({OrderOp.class, InventoryOp.class, NotifyOp.class})
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
```

This replaces manual wiring with one annotation: auto-discovers `@Interpreter` beans, combines them, registers `EffectBoundary` as a singleton.

### Level 5: Testing with @EffectTest

```java
@EffectTest(effects = {OrderOp.class, InventoryOp.class, NotifyOp.class})
class OrderServiceTest {
    @Autowired EffectBoundary<OrderEffects> boundary;

    @Test void processOrder_success() {
        var result = boundary.run(orderService.processOrder(validRequest));
        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
```

For pure tests without Spring context, use `TestBoundary` directly (see `/hkj-effects`).

### FreePath as Controller Return Type

```java
@GetMapping("/{id}/status")
public FreePath<OrderEffects, OrderStatus> getStatus(@PathVariable String id) {
    return orderService.getOrderStatus(id);
    // FreePathReturnValueHandler interprets via EffectBoundary bean -> HTTP 200 JSON
}
```

---

## Configuration

```yaml
# application.yml
hkj:
  web:
    either:
      default-error-status: 500       # Default HTTP status for Left values
    validated:
      error-status: 400               # HTTP status for Invalid values
    free-path-enabled: true           # Enable FreePath return type handling
    free-path-failure-status: 500     # HTTP status for FreePath execution failures
    json:
      format: standard                # JSON serialisation format
```

---

## Requirements

- Spring Boot 4.0.1+ (Jackson 3.x / `tools.jackson` namespace)
- Java 25 with `--enable-preview`
