# Spring Boot Integration: Functional Patterns for Enterprise Applications
## _Bringing Type-Safe Error Handling to Your REST APIs_

~~~admonish info title="What You'll Learn"
- How to use Either, Validated, Maybe, Try, IO, CompletableFuture, VTask, VStream, and Free as Spring controller return types (via their Path wrappers)
- Zero-configuration setup with the hkj-spring-boot-starter
- Virtual thread integration with VTaskPath (async) and VStreamPath (SSE streaming)
- Automatic JSON serialisation with customisable formats
- Success HTTP status control via `@ResponseStatus` on handler methods
- Monitoring functional operations with Spring Boot Actuator
- Securing endpoints with functional error handling patterns
- Building production-ready applications with explicit, typed errors
- Testing functional controllers with MockMvc, both full context and `@WebMvcTest` slices
~~~

~~~admonish example title="Example Application"
A complete working example is available in the [hkj-spring example module](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-spring/example). Run it with:
```bash
./gradlew :hkj-spring:example:bootRun
```
~~~

## Overview

Building REST APIs with Spring Boot is straightforward, but error handling often becomes a source of complexity and inconsistency. Traditional exception-based approaches scatter error handling logic across `@ExceptionHandler` methods, lose type safety, and make it difficult to reason about what errors a given endpoint can produce.

The **hkj-spring-boot-starter** solves these problems by bringing functional programming patterns seamlessly into Spring applications. Return `Either<Error, Data>`, `Validated<Errors, Data>`, or `CompletableFuturePath` from your controllers, and the framework automatically handles the rest: converting functional types to appropriate HTTP responses whilst preserving type safety and composability.

**The key insight:** Errors become explicit in your method signatures, not hidden in implementation details or exception hierarchies.

---

## Quick Start {#quick-start}

### Step 1: Add the Dependency

Add the starter to your Spring Boot project:

```gradle
// build.gradle.kts
dependencies {
    implementation("io.github.higher-kinded-j:hkj-spring-boot-starter:LATEST_VERSION")
}
```

Or with Maven:

```xml
<dependency>
    <groupId>io.github.higher-kinded-j</groupId>
    <artifactId>hkj-spring-boot-starter</artifactId>
    <version>LATEST_VERSION</version>
</dependency>
```

### Step 2: Return Functional Types from Controllers

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/{id}")
    public Either<DomainError, User> getUser(@PathVariable String id) {
        return userService.findById(id);
        // Right(user) → HTTP 200 with JSON body
        // Left(UserNotFoundError) → HTTP 404 with error details
    }

    @PostMapping
    public Validated<List<ValidationError>, User> createUser(@RequestBody UserRequest request) {
        return userService.validateAndCreate(request);
        // Valid(user) → HTTP 200 with user JSON
        // Invalid(errors) → HTTP 400 with all validation errors accumulated
    }
}
```

### Step 3: Run Your Application

That's it! The starter auto-configures everything:
- EitherPath to HTTP response conversion with automatic status code mapping
- ValidationPath to HTTP response with error accumulation
- MaybePath, TryPath, and IOPath handlers for optional, exception-capturing, and deferred computations
- CompletableFuturePath support for async operations
- VTaskPath support for virtual thread async via DeferredResult
- VStreamPath support for SSE streaming on virtual threads
- FreePath support for Free-monad programs interpreted through `EffectBoundary`
- `@ResponseStatus` honoured on handler methods to override the default success status
- JSON serialisation for functional types
- Customisable error type to HTTP status code mapping

---

## Why Use Functional Error Handling?

### The Problem with Exceptions

Traditional Spring Boot error handling relies on exceptions and `@ExceptionHandler` methods:

```java
@GetMapping("/{id}")
public User getUser(@PathVariable String id) {
    return userService.findById(id);  // What errors can this throw?
}

@ExceptionHandler(UserNotFoundException.class)
public ResponseEntity<ErrorResponse> handleNotFound(UserNotFoundException ex) {
    return ResponseEntity.status(404).body(new ErrorResponse(ex.getMessage()));
}

@ExceptionHandler(ValidationException.class)
public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
    return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
}
```

**Problems:**
- Errors are invisible in the method signature
- No compile-time guarantee that all errors are handled
- Exception handlers become a catch-all for unrelated errors
- Difficult to compose operations whilst maintaining error information
- Testing requires catching exceptions or using `@ExceptionHandler` integration

### The Functional Solution

With functional error handling, errors become explicit and composable:

```java
@GetMapping("/{id}")
public Either<DomainError, User> getUser(@PathVariable String id) {
    return userService.findById(id);  // Clear: returns User or DomainError
}

@GetMapping("/{id}/orders")
public Either<DomainError, List<Order>> getUserOrders(@PathVariable String id) {
    return userService.findById(id)
        .flatMap(orderService::getOrdersForUser);  // Compose operations naturally
}
```

**Benefits:**
- Errors are explicit in the type signature
- Compiler ensures error handling at call sites
- Functional composition with `map`, `flatMap`, `fold`
- Automatic HTTP response conversion
- Easy to test, no exception catching required

---

## Core Features

### 1. Either: Success or Typed Error

`Either<L, R>` represents a computation that can succeed with a `Right(value)` or fail with a `Left(error)`.

#### Basic Usage

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/{id}")
    public Either<DomainError, User> getUser(@PathVariable String id) {
        return userService.findById(id);
    }
}
```

**Response Mapping:**
- `Right(user)` → HTTP 200 with JSON: `{"id": "1", "email": "alice@example.com", ...}`
- `Left(UserNotFoundError)` → HTTP 404 with JSON: `{"success": false, "error": {"type": "UserNotFoundError", ...}}`

#### Error Type → HTTP Status Mapping {#error-type-http-status-mapping}

The framework automatically maps error types to HTTP status codes by examining class names:

```java
public sealed interface DomainError permits
    UserNotFoundError,      // Contains "NotFound" → 404
    ValidationError,        // Contains "Validation" → 400
    AuthorizationError,     // Contains "Authorization" → 403
    AuthenticationError {}  // Contains "Authentication" → 401

// Custom errors default to 400 Bad Request
```

#### Composing Operations

```java
@GetMapping("/{userId}/orders/{orderId}")
public Either<DomainError, Order> getUserOrder(
        @PathVariable String userId,
        @PathVariable String orderId) {

    return userService.findById(userId)
        .flatMap(user -> orderService.findById(orderId))
        .flatMap(order -> orderService.verifyOwnership(order, userId));

    // Short-circuits on first Left
}
```

See the [Either Monad documentation](../monads/either_monad.md) for comprehensive usage patterns.

---

### 2. Validated: Accumulating Multiple Errors {#validated-accumulating-multiple-errors}

`Validated<E, A>` is designed for validation scenarios where you want to accumulate **all** errors, not just the first one.

#### Basic Usage

```java
@PostMapping
public Validated<List<ValidationError>, User> createUser(@RequestBody UserRequest request) {
    return userService.validateAndCreate(request);
}
```

**Response Mapping:**
- `Valid(user)` → HTTP 200 with JSON: `{"id": "1", "email": "alice@example.com", ...}`
- `Invalid(errors)` → HTTP 400 with JSON: `{"success": false, "errors": [{"field": "email", "message": "Invalid format"}, ...]}`

#### Validation Example

```java
@Service
public class UserService {

    public Validated<List<ValidationError>, User> validateAndCreate(UserRequest request) {
        return Validated.validateAll(
            validateEmail(request.email()),
            validateName(request.firstName()),
            validateName(request.lastName())
        ).map(tuple -> new User(
            UUID.randomUUID().toString(),
            tuple._1(),  // email
            tuple._2(),  // firstName
            tuple._3()   // lastName
        ));
    }

    private Validated<ValidationError, String> validateEmail(String email) {
        if (email == null || !email.contains("@")) {
            return Validated.invalid(new ValidationError("email", "Invalid email format"));
        }
        return Validated.valid(email);
    }

    private Validated<ValidationError, String> validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Validated.invalid(new ValidationError("name", "Name cannot be empty"));
        }
        return Validated.valid(name);
    }
}
```

**Key Difference from Either:**
- `Either` short-circuits on first error (fail-fast)
- `Validated` accumulates all errors (fail-slow)

See the [Validated Monad documentation](../monads/validated_monad.md) for detailed usage.

---

### 3. CompletableFuturePath: Async Operations with Typed Errors {#completablefuturepath-async-operations-with-typed-errors}

`CompletableFuturePath<A>` wraps asynchronous computation in the Effect Path API, allowing you to compose async operations with `map`, `via`, and `recover`.

#### Basic Usage

```java
@GetMapping("/{id}/async")
public CompletableFuturePath<User> getUserAsync(@PathVariable String id) {
    return asyncUserService.findByIdAsync(id);
    // Automatically handles async to sync HTTP response conversion
}
```

#### Async Composition

```java
@Service
public class AsyncOrderService {

    public CompletableFuturePath<OrderSummary> processOrderAsync(
            String userId, OrderRequest request) {

        return asyncUserService.findByIdAsync(userId)
            .via(user -> asyncInventoryService.checkAvailability(request.items()))
            .via(availability -> asyncPaymentService.processPayment(request.payment()))
            .map(payment -> new OrderSummary(userId, request, payment));

        // Each step runs asynchronously
        // Composes naturally with other Path types
    }
}
```

**Response Handling:**
The framework uses Spring's async request processing:
1. Controller returns `CompletableFuturePath`
2. Framework extracts the underlying `CompletableFuture`
3. Spring's async mechanism handles the future
4. When complete, result is converted to HTTP response

See the [Effect Path API documentation](../effect/path_types.md) for comprehensive examples.

---

### 4. VTaskPath: Virtual Thread Async Operations {#vtaskpath-virtual-thread-async}

`VTaskPath<A>` runs deferred computations on virtual threads, providing async responses without thread pool configuration.

#### Basic Usage

```java
@GetMapping("/users/{id}")
public VTaskPath<User> getUser(@PathVariable String id) {
    return vtUserService.findById(id);
    // Executes on a virtual thread via DeferredResult
    // No thread pool configuration needed
}
```

**Response Handling:**
The framework uses Spring's `DeferredResult`:
1. Controller returns `VTaskPath`
2. Framework calls `VTask.runAsync()` on a virtual thread
3. Result is wrapped in `DeferredResult` with configurable timeout
4. When complete, result is converted to HTTP 200 with JSON body
5. On failure, returns configured error status (default: 500)

#### Structured Concurrency

Use `Scope` for parallel fan-out with automatic cancellation:

```java
@GetMapping("/users/{id}/enriched")
public VTaskPath<EnrichedUser> getEnrichedUser(@PathVariable String id) {
    VTask<User> userTask = VTask.of(() -> findUser(id));
    VTask<Profile> profileTask = VTask.of(() -> fetchProfile(id));
    VTask<OrderSummary> ordersTask = VTask.of(() -> fetchOrders(id));

    return Path.vtask(
        Scope.<Object>allSucceed()
            .fork(userTask)
            .fork(profileTask)
            .fork(ordersTask)
            .join())
        .map(results -> new EnrichedUser(
            (User) results.get(0),
            (Profile) results.get(1),
            (OrderSummary) results.get(2)
        ));
}
```

**Key advantages over CompletableFuturePath:**
- No thread pool sizing or tuning — virtual threads scale automatically with the JVM
- Structured concurrency via `Scope` replaces `CompletableFuture.allOf()` with cancellation awareness
- Simpler imperative code style — blocking is free on virtual threads

---

### 5. VStreamPath: SSE Streaming on Virtual Threads {#vstreampath-sse-streaming}

`VStreamPath<A>` enables Server-Sent Events (SSE) streaming backed by virtual threads. No WebFlux or Reactor dependency required.

#### Basic Usage

```java
@GetMapping(value = "/users/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public VStreamPath<User> streamUsers() {
    return vtUserService.streamAllUsers();
    // Each element emitted as an SSE data: event with JSON payload
}
```

**Response Handling:**
1. Controller returns `VStreamPath`
2. Framework starts a virtual thread to consume the stream
3. Each element is written as an SSE `data:` event with JSON payload
4. A completion event is sent when the stream ends
5. On error, an SSE error event is sent

#### Parameterised Streams

```java
@GetMapping(value = "/ticks", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public VStreamPath<TickEvent> streamTicks(@RequestParam(defaultValue = "10") int count) {
    return vtUserService.streamTicks(count);
    // Infinite stream limited by take(count)
}
```

**Key advantages:**
- No WebFlux, no Reactor, no Flux — just imperative code that streams
- Pull-based with natural backpressure via virtual thread blocking
- Configurable stream timeout via `hkj.virtual-threads.stream-timeout-ms`

---

## HTTP Status Codes {#http-status-codes}

Every Effect Path return-value handler resolves the success and error status codes independently, so the same controller method can map typed successes and typed failures to different HTTP responses.

### Success Status with `@ResponseStatus`

Handlers delegate to `SuccessStatusResolver` to pick the success status, in this order:

1. `@ResponseStatus` on the handler method
2. `@ResponseStatus` on the controller class
3. Meta-annotations (e.g. custom `@Created`, `@NoContent`)
4. The handler's default (typically `200`)

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EitherPath<DomainError, Order> create(@RequestBody OrderRequest req) {
        return orderService.create(req);
        // Right(order)           → HTTP 201 Created
        // Left(ValidationError)  → HTTP 400 (via ErrorStatusCodeMapper)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public MaybePath<Void> cancel(@PathVariable String id) {
        return orderService.cancel(id);
        // Just(_)   → HTTP 204 No Content (body suppressed)
        // Nothing() → HTTP 404
    }
}
```

All nine Effect Path handlers honour `@ResponseStatus` consistently.

### Error Status Mapping

Error responses continue to flow through `ErrorStatusCodeMapper`. Register mappings for each error type, with a configurable fallback:

```yaml
hkj:
  web:
    either:
      default-error-status: 400  # Fallback for unmapped Left error types
```

Tagged errors (those implementing the library's status-bearing error interface, or registered by type) produce their mapped status; everything else falls back to the default.

---

## JSON Serialisation {#json-serialisation}

The starter provides flexible JSON serialisation for functional types.

### Configuration

Configure serialisation format in `application.yml`:

```yaml
hkj:
  jackson:
    custom-serializers-enabled: true  # Enable custom serialisers (default: true)
    either-format: TAGGED             # TAGGED, UNWRAPPED, or DIRECT
    validated-format: TAGGED          # TAGGED, UNWRAPPED, or DIRECT
    maybe-format: TAGGED              # TAGGED, UNWRAPPED, or DIRECT
```

### Serialisation Formats

#### TAGGED (Default)

Wraps the value with metadata indicating success/failure:

```json
// Right(user)
{
  "success": true,
  "value": {
    "id": "1",
    "email": "alice@example.com"
  }
}

// Left(error)
{
  "success": false,
  "error": {
    "type": "UserNotFoundError",
    "userId": "999"
  }
}
```

#### UNWRAPPED

Returns just the value or error without wrapper:

```json
// Right(user)
{
  "id": "1",
  "email": "alice@example.com"
}

// Left(error)
{
  "type": "UserNotFoundError",
  "userId": "999"
}
```

#### DIRECT

Uses Either's default `toString()` representation (useful for debugging):

```json
"Right(value=User[id=1, email=alice@example.com])"
```

For complete serialisation details, see [hkj-spring/JACKSON_SERIALIZATION.md](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-spring/JACKSON_SERIALIZATION.md).

---

## Configuration

### Web Configuration

```yaml
hkj:
  web:
    either-path-enabled: true               # Enable EitherPath handler (default: true)
    validated-path-enabled: true            # Enable ValidationPath handler (default: true)
    maybe-path-enabled: true                # Enable MaybePath handler (default: true)
    try-path-enabled: true                  # Enable TryPath handler (default: true)
    io-path-enabled: true                   # Enable IOPath handler (default: true)
    completable-future-path-enabled: true   # Enable CompletableFuturePath handler (default: true)
    vtask-path-enabled: true                # Enable VTaskPath handler (default: true)
    vstream-path-enabled: true              # Enable VStreamPath handler (default: true)
    free-path-enabled: true                 # Enable FreePath handler (default: true)
    either:
      default-error-status: 400             # Default HTTP status for unmapped Left errors
```

The canonical key for the EitherPath default error status is `hkj.web.either.default-error-status`. The legacy flat path `hkj.web.default-error-status` is preserved as a backward-compatible alias and binds to the same value.

### Async Executor Configuration

For CompletableFuturePath operations, configure the async thread pool:

```yaml
hkj:
  async:
    core-pool-size: 10                 # Minimum threads
    max-pool-size: 20                  # Maximum threads
    queue-capacity: 100                # Queue size before rejection
    thread-name-prefix: "hkj-async-"   # Thread naming pattern
```

### Virtual Thread Configuration

For VTaskPath and VStreamPath operations (no pool sizing needed):

```yaml
hkj:
  virtual-threads:
    default-timeout-ms: 30000          # VTask timeout (default: 30s)
    stream-timeout-ms: 60000           # VStream timeout (default: 60s, 0 = no timeout)
```

For complete configuration options, see [hkj-spring/CONFIGURATION.md](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-spring/CONFIGURATION.md).

---

## Real-World Examples

### Example 1: User Management API

A typical CRUD API with validation and error handling:

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    // Get all users (always succeeds)
    @GetMapping
    public List<User> getAllUsers() {
        return userService.findAll();
    }

    // Get single user (may not exist)
    @GetMapping("/{id}")
    public Either<DomainError, User> getUser(@PathVariable String id) {
        return userService.findById(id);
        // Right(user) → 200 OK
        // Left(UserNotFoundError) → 404 Not Found
    }

    // Create user (validate all fields)
    @PostMapping
    public Validated<List<ValidationError>, User> createUser(@RequestBody UserRequest request) {
        return userService.validateAndCreate(request);
        // Valid(user) → 200 OK
        // Invalid([errors...]) → 400 Bad Request with all validation errors
    }

    // Update user (may not exist + validation)
    @PutMapping("/{id}")
    public Either<DomainError, User> updateUser(
            @PathVariable String id,
            @RequestBody UserRequest request) {

        return userService.findById(id)
            .flatMap(existingUser ->
                userService.validateUpdate(request)
                    .toEither()  // Convert Validated to Either
                    .map(validRequest -> userService.update(id, validRequest)));

        // Combines existence check + validation
    }

    // Delete user (may not exist)
    @DeleteMapping("/{id}")
    public Either<DomainError, Void> deleteUser(@PathVariable String id) {
        return userService.delete(id);
        // Right(null) → 200 OK
        // Left(UserNotFoundError) → 404 Not Found
    }

    // Get user's email (composition example)
    @GetMapping("/{id}/email")
    public Either<DomainError, String> getUserEmail(@PathVariable String id) {
        return userService.findById(id)
            .map(User::email);

        // Automatic error propagation
    }
}
```

### Example 2: Async Order Processing

Processing orders asynchronously with multiple external services:

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private AsyncOrderService orderService;

    @PostMapping
    public CompletableFuturePath<Order> createOrder(@RequestBody OrderRequest request) {
        return orderService.processOrder(request);
        // Each step runs asynchronously:
        // 1. Validate user
        // 2. Check inventory
        // 3. Process payment
        // 4. Create order record

        // Composes naturally with other Path types
        // Returns 200 on success, appropriate error code on failure
    }

    @GetMapping("/{id}")
    public CompletableFuturePath<Order> getOrder(@PathVariable String id) {
        return orderService.findByIdAsync(id);
    }
}

@Service
public class AsyncOrderService {

    @Autowired
    private AsyncUserService userService;
    @Autowired
    private AsyncInventoryService inventoryService;
    @Autowired
    private AsyncPaymentService paymentService;
    @Autowired
    private OrderRepository orderRepository;

    public CompletableFuturePath<Order> processOrder(OrderRequest request) {
        return userService.findByIdAsync(request.userId())
            .via(user -> inventoryService.checkAvailabilityAsync(request.items()))
            .via(availability -> {
                if (!availability.allAvailable()) {
                    return Path.completableFuture(
                        CompletableFuture.failedFuture(
                            new OutOfStockException(availability.unavailableItems())
                        )
                    );
                }
                return Path.completableFuture(
                    CompletableFuture.completedFuture(availability)
                );
            })
            .via(availability -> paymentService.processPaymentAsync(request.payment()))
            .map(payment -> createOrderRecord(request, payment));
    }
}
```

For complete working examples, see the [hkj-spring example module](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-spring/example).

---

## Spring Security Integration {#spring-security-integration}

The hkj-spring-boot-starter provides optional Spring Security integration with functional error handling patterns.

### Enabling Security Integration

```yaml
hkj:
  security:
    enabled: true                       # Enable functional security (default: false)
    validated-user-details: true        # Use Validated for user loading
    either-authentication: true         # Use Either for authentication
    either-authorization: true          # Use Either for authorisation
```

### Functional User Details Service

Use `Validated` to accumulate authentication errors:

```java
@Service
public class CustomUserDetailsService implements ValidatedUserDetailsService {

    @Override
    public Validated<List<SecurityError>, UserDetails> loadUserByUsername(String username) {
        return Validated.validateAll(
            validateUsername(username),
            validateAccountStatus(username),
            validateCredentials(username)
        ).map(tuple -> new User(
            tuple._1(),  // username
            tuple._2(),  // password
            tuple._3()   // authorities
        ));

        // Returns ALL validation errors at once
        // e.g., "Username too short" + "Account locked"
    }
}
```

### Functional Authentication

Use `Either` for JWT authentication with typed errors:

```java
@Component
public class JwtAuthenticationConverter {

    public Either<AuthenticationError, Authentication> convert(Jwt jwt) {
        return extractUsername(jwt)
            .flatMap(this::validateToken)
            .flatMap(this::extractAuthorities)
            .map(authorities -> new JwtAuthenticationToken(jwt, authorities));

        // Short-circuits on first error
    }
}
```

For complete security integration details, see [hkj-spring/SECURITY.md](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-spring/SECURITY.md).

---

## Monitoring with Spring Boot Actuator {#monitoring-with-spring-boot-actuator}

Track functional programming patterns in production with built-in Actuator integration.

### Enabling Actuator Integration

```yaml
hkj:
  actuator:
    metrics:
      enabled: true                     # Enable metrics tracking
    health:
      async-executor:
        enabled: true                   # Monitor CompletableFuturePath thread pool

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,hkj
```

### Available Metrics

The starter automatically tracks:

- **EitherPath metrics:** Success/error counts and rates
- **ValidationPath metrics:** Valid/invalid counts and error distributions
- **CompletableFuturePath metrics:** Async operation durations and success rates
- **VTaskPath metrics:** Virtual thread execution durations and success rates
- **VStreamPath metrics:** Stream completion counts and element distribution
- **Thread pool health:** Active threads, queue size, saturation

### Custom HKJ Endpoint

Access functional programming metrics via the custom actuator endpoint:

```bash
curl http://localhost:8080/actuator/hkj
```

Response:
```json
{
  "configuration": {
    "web": {
      "eitherPathEnabled": true,
      "validatedPathEnabled": true,
      "completableFuturePathEnabled": true
    },
    "jackson": {
      "eitherFormat": "TAGGED",
      "validatedFormat": "TAGGED"
    }
  },
  "metrics": {
    "eitherPath": {
      "successCount": 1547,
      "errorCount": 123,
      "totalCount": 1670,
      "successRate": 0.926
    },
    "validationPath": {
      "validCount": 892,
      "invalidCount": 45,
      "totalCount": 937,
      "validRate": 0.952
    },
    "completableFuturePath": {
      "successCount": 234,
      "errorCount": 12,
      "totalCount": 246,
      "successRate": 0.951
    }
  }
}
```

### Prometheus Integration

Export metrics to Prometheus for monitoring and alerting:

```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
```

Example Prometheus queries:
```promql
# EitherPath error rate
rate(hkj_either_path_invocations_total{result="error"}[5m])

# CompletableFuturePath p95 latency
histogram_quantile(0.95,
  rate(hkj_completable_future_path_async_duration_seconds_bucket[5m]))

# ValidationPath success rate
sum(rate(hkj_validation_path_invocations_total{result="valid"}[5m]))
  / sum(rate(hkj_validation_path_invocations_total[5m]))
```

For complete Actuator integration details, see [hkj-spring/ACTUATOR.md](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-spring/ACTUATOR.md).

---

## Testing

Testing functional controllers is straightforward with MockMvc.

### Testing Either Responses

```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturn200ForExistingUser() throws Exception {
        mockMvc.perform(get("/api/users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.value.id").value("1"))
            .andExpect(jsonPath("$.value.email").value("alice@example.com"));
    }

    @Test
    void shouldReturn404ForMissingUser() throws Exception {
        mockMvc.perform(get("/api/users/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.type").value("UserNotFoundError"));
    }
}
```

### Testing Validated Responses

```java
@Test
void shouldAccumulateValidationErrors() throws Exception {
    String invalidRequest = """
        {
          "email": "invalid",
          "firstName": "",
          "lastName": "x"
        }
        """;

    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidRequest))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(jsonPath("$.errors.length()").value(3));  // All errors returned
}
```

### Testing CompletableFuturePath Async Responses

```java
@Test
void shouldHandleAsyncCompletableFuturePathResponse() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/users/1/async"))
        .andExpect(request().asyncStarted())  // Verify async started
        .andReturn();

    mockMvc.perform(asyncDispatch(result))   // Dispatch async result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("1"));
}
```

### Slice Testing with `@WebMvcTest`

For fast controller-only tests without the full application context, use Spring's `@WebMvcTest` slice. The slice excludes auto-configurations by default, so the HKJ auto-configurations must be imported explicitly:

```java
@WebMvcTest(UserController.class)
@ImportAutoConfiguration({
    HkjAutoConfiguration.class,
    HkjJacksonAutoConfiguration.class,
    HkjWebMvcAutoConfiguration.class
})
class UserControllerWebMvcSliceTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserService userService;

    @Test
    void shouldReturn200ForExistingUser() throws Exception {
        when(userService.findById("1"))
            .thenReturn(Either.right(new User("1", "alice@example.com")));

        mockMvc.perform(get("/api/users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.value.id").value("1"));
    }

    @Test
    void shouldReturn404ForTaggedError() throws Exception {
        when(userService.findById("999"))
            .thenReturn(Either.left(new UserNotFoundError("999")));

        mockMvc.perform(get("/api/users/999"))
            .andExpect(status().isNotFound());
    }
}
```

The three imports activate EitherPath/ValidationPath/Maybe/Try/IO/CompletableFuture/VTask/VStream/Free return-value handling, Jackson modules for functional types, and the error-status mapper; everything a controller slice needs. For integration-style tests covering the full stack, prefer `@SpringBootTest @AutoConfigureMockMvc` as shown above.

### Unit Testing Services

Services returning functional types are easy to test without mocking frameworks:

```java
class UserServiceTest {

    private UserService service;

    @Test
    void shouldReturnRightWhenUserExists() {
        Either<DomainError, User> result = service.findById("1");

        assertThat(result.isRight()).isTrue();
        User user = result.getRight();
        assertThat(user.id()).isEqualTo("1");
    }

    @Test
    void shouldReturnLeftWhenUserNotFound() {
        Either<DomainError, User> result = service.findById("999");

        assertThat(result.isLeft()).isTrue();
        DomainError error = result.getLeft();
        assertThat(error).isInstanceOf(UserNotFoundError.class);
    }

    @Test
    void shouldAccumulateValidationErrors() {
        UserRequest invalid = new UserRequest("bad-email", "", "x");

        Validated<List<ValidationError>, User> result =
            service.validateAndCreate(invalid);

        assertThat(result.isInvalid()).isTrue();
        List<ValidationError> errors = result.getErrors();
        assertThat(errors).hasSize(3);
    }
}
```

For comprehensive testing examples, see [hkj-spring/example/TESTING.md](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-spring/example/TESTING.md).

---

## Migration Guide

Migrating from exception-based error handling to functional patterns is straightforward and can be done incrementally.

See the [Migration Guide](./migrating_to_functional_errors.md) for a complete step-by-step walkthrough of:
- Converting exception-throwing methods to Either
- Replacing `@ExceptionHandler` methods with functional patterns
- Migrating validation logic to Validated
- Converting async operations to CompletableFuturePath
- Maintaining backwards compatibility during migration

---

## Architecture and Design

### Auto-Configuration

The starter uses Spring Boot 4.x auto-configuration:

```java
@AutoConfiguration
@ConditionalOnClass({DispatcherServlet.class, Kind.class})
@ConditionalOnWebApplication(type = SERVLET)
public class HkjWebMvcAutoConfiguration implements WebMvcConfigurer {

    @Override
    public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
        handlers.add(new EitherPathReturnValueHandler(properties));
        handlers.add(new ValidationPathReturnValueHandler(properties));
        handlers.add(new CompletableFuturePathReturnValueHandler(properties));
    }
}
```

Auto-configuration activates when:
- `higher-kinded-j-core` is on the classpath
- Spring Web MVC is present
- Application is a servlet web app

### Return Value Handlers

Each functional type has a dedicated handler:

1. **EitherPathReturnValueHandler:** Converts `EitherPath<L, R>` to HTTP responses
2. **ValidationPathReturnValueHandler:** Converts `ValidationPath<E, A>` to HTTP responses
3. **MaybePathReturnValueHandler:** Converts `MaybePath<A>`, mapping `Just` to `200` and `Nothing` to `404`
4. **TryPathReturnValueHandler:** Converts `TryPath<A>`, mapping `Success` to `200` and `Failure` to an error response
5. **IOPathReturnValueHandler:** Executes `IOPath<A>` and writes the captured result
6. **CompletableFuturePathReturnValueHandler:** Unwraps `CompletableFuturePath<A>` for async processing
7. **VTaskPathReturnValueHandler:** Executes `VTaskPath<A>` on virtual threads via `DeferredResult`
8. **VStreamPathReturnValueHandler:** Streams `VStreamPath<A>` as SSE events on virtual threads
9. **FreePathReturnValueHandler:** Interprets `FreePath<F, A>` via the registered `EffectBoundary` bean

Supporting collaborators:

- **`SuccessStatusResolver`:** Reads `@ResponseStatus` on the handler method (with controller-class fallback and meta-annotation support) so each handler can override its default success status.
- **`ErrorStatusCodeMapper`:** Configurable error-type to HTTP status mapping, falling back to `hkj.web.either.default-error-status`.

Handlers are registered automatically and integrated seamlessly with Spring's request processing lifecycle.

### Non-Invasive Design

The integration doesn't modify existing Spring Boot behaviour:
- Standard Spring MVC types work unchanged
- Exception handling still functions normally
- Can be disabled via configuration
- Coexists with traditional `ResponseEntity` endpoints

---

## Frequently Asked Questions

### Can I mix functional and traditional exception handling?

Yes! The integration is non-invasive. You can use `EitherPath`, `ValidationPath`, and `CompletableFuturePath` alongside traditional `ResponseEntity` and exception-based endpoints in the same application.

### Does this work with Spring WebFlux?

Currently, the starter supports Spring Web MVC (servlet-based). For reactive-style streaming, use `VStreamPath` which provides SSE streaming on virtual threads without requiring WebFlux or Reactor dependencies.

### Can I customise the error → HTTP status mapping?

Yes. Implement a custom return value handler or use the configuration properties to set default status codes. See [CONFIGURATION.md](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-spring/CONFIGURATION.md) for details.

### How does performance compare to exceptions?

Functional error handling is generally faster than exception-throwing for expected error cases, as it avoids stack trace generation and exception propagation overhead. For success cases, performance is equivalent.

### Can I use this with Spring Data repositories?

Yes. Wrap repository calls in your service layer:

```java
@Service
public class UserService {

    @Autowired
    private UserRepository repository;

    public Either<DomainError, User> findById(String id) {
        return repository.findById(id)
            .map(Either::<DomainError, User>right)
            .orElseGet(() -> Either.left(new UserNotFoundError(id)));
    }
}
```

### Does this work with validation annotations (`@Valid`)?

The integration focuses on functional validation patterns. For Spring's `@Valid` integration, you can convert `BindingResult` to `Validated` in your controllers.

---

## Related Documentation

- [Either Monad](../monads/either_monad.md) - Comprehensive Either usage
- [Validated Monad](../monads/validated_monad.md) - Validation patterns
- [Effect Path API](../effect/path_types.md) - Path types and async composition
- [Migration Guide](./migrating_to_functional_errors.md) - Step-by-step migration
- [Configuration Guide](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-spring/CONFIGURATION.md) - Complete configuration options
- [Jackson Serialisation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-spring/JACKSON_SERIALIZATION.md) - JSON format details
- [Security Integration](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-spring/SECURITY.md) - Spring Security patterns
- [Actuator Monitoring](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-spring/ACTUATOR.md) - Metrics and health checks
- [Testing Guide](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-spring/example/TESTING.md) - Testing patterns

---

## Summary

The hkj-spring-boot-starter brings functional programming patterns seamlessly into Spring Boot applications:

- **Return functional types from controllers:** EitherPath, ValidationPath, CompletableFuturePath, VTaskPath, VStreamPath
- **Virtual thread integration:** VTaskPath for async, VStreamPath for SSE streaming — no thread pools or WebFlux needed
- **Automatic HTTP response conversion:** No boilerplate required
- **Explicit, type-safe error handling:** Errors in method signatures
- **Composable operations:** Functional composition with map/via/flatMap
- **Zero configuration:** Auto-configuration handles everything
- **Production-ready:** Actuator metrics, security integration
- **Easy to test:** No exception mocking required

Get started today by adding the dependency and returning functional types from your controllers. The framework handles the rest!

---

**Previous:** [Introduction](ch_intro.md)
**Next:** [Migrating to Functional Errors](migrating_to_functional_errors.md)
