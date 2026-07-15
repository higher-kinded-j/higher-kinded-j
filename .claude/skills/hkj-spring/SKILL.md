---
name: hkj-spring
description: "Higher-Kinded-J Spring Boot integration (hkj-spring-boot-starter). Use PROACTIVELY whenever the working file or task involves: (1) a Spring controller method (@RestController / @GetMapping / @PostMapping / @PutMapping / @DeleteMapping) that returns Either, Validated, EitherOrBoth, EitherOrBothPath, EitherPath, MaybePath, ValidationPath, TryPath, IOPath, CompletableFuturePath, VTaskPath, VStreamPath, or FreePath; (2) a sealed interface or sealed hierarchy named DomainError / *Error / *Failure used as the Left of an Either; (3) Jackson 3.x / tools.jackson serialization of HKJ types, including EitherOrBoth and NonEmptyList; (4) any application.yml / application.properties key under hkj.web.* (including hkj.web.either-or-both-path-enabled), hkj.json.*, hkj.validation.*, hkj.async.*, hkj.virtual-threads.*, hkj.actuator.*, hkj.security.*, or hkj.effect-boundary.*; (5) mapping a domain error to an HTTP status code, including 4xx/5xx codes outside the heuristic table (409 Conflict, 422 Unprocessable Entity, 429 Too Many Requests, 503 Service Unavailable); (6) ErrorStatusCodeStrategy, ErrorStatusCodeMapper, DefaultErrorStatusCodeStrategy, or HttpHeaderCarrier (Retry-After, WWW-Authenticate, Location); (7) @WebMvcTest slices that need HkjAutoConfiguration / HkjJacksonAutoConfiguration / HkjWebMvcAutoConfiguration imported; (8) EffectBoundary, @Interpreter, @EnableEffectBoundary, @EffectTest, or returning FreePath from a controller; (9) auto-configuration questions about the starter; (10) calling another service over HTTP and keeping the typed error: @HkjHttpClient on a @HttpExchange interface whose methods return EitherPath / VTaskPath<Either> / MaybePath; spring.http.serviceclient.* configuration (base-url, timeouts) for the client; hkj.client.status-error-mappings; @OnStatus(value=, error=); ResponseErrorDecoder / ResponseErrorDecoderFactory / HkjClientExchange / ClientErrorResponse.retryAfter; decoding the {\"success\":false,\"error\":…} envelope back into a typed error; or a generated <Name>HttpExchange / <Name>Client / <Name>ClientConfiguration."
---

# Higher-Kinded-J Spring Boot Integration

You are helping a developer integrate HKJ with Spring Boot. The `hkj-spring-boot-starter` covers both edges of an HTTP boundary: **inbound**, returning `Either`, `Validated`, `CompletableFuturePath`, `VTaskPath`, and `VStreamPath` directly from Spring controllers with automatic HTTP response conversion; and **outbound**, generating declarative HTTP clients (`@HkjHttpClient`) whose methods return Effect Paths and decode a typed error back from the response, so the typed error channel survives a service-to-service call.

## When to load supporting files

- If the user wants a **complete working server example**, load `reference/spring-example.md`
- If the user is **calling another service** (`@HkjHttpClient`), load `reference/http-client-example.md`
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
| `EitherOrBoth` / `EitherOrBothPath` conversion | `Right` -> 200, `Both` -> 200 + `X-Hkj-Warnings` header, `Left` -> mapped status code |
| `CompletableFuturePath` async | Async operations with functional error handling |
| `VTaskPath` async | Virtual thread async via `DeferredResult` |
| `VStreamPath` SSE | Server-Sent Events streaming on virtual threads |
| Jackson serialization | JSON support for Either, Maybe, Try, Validated, EitherOrBoth, NonEmptyList |
| Status code mapping | Customisable domain error -> HTTP status mapping |
| `@HkjHttpClient` clients | Declarative HTTP clients returning Effect Paths, decoding a typed error from the response |
| Actuator integration | Monitoring functional operations |

---

## HTTP Status Code Mapping

The starter resolves the status for a `Left` error value in this order:

1. **Explicit mapping** by simple (or fully-qualified) class name from `hkj.web.error-status-mappings`
2. **Token heuristic** on the simple class name (CamelCase split, lower-cased, whole-token match)
3. **Configured default** (`hkj.web.either.default-error-status`, alias `hkj.web.default-error-status`)

| Token(s) present in the simple class name | HTTP status |
|-------------------------------------------|-------------|
| `not` adjacent to `found`                 | 404 |
| `validation` or `invalid`                 | 400 |
| `authorization` or `forbidden`            | 403 |
| `authentication` or `unauthorized`        | 401 |
| (no match)                                | configured default |

Resolution is purely **by class name**. Nothing inspects the error's fields: a core
`ErrorEnvelope` / `@GenerateErrorEnvelope` error (its `code`, its typed context) is **not** consulted
by `DefaultErrorStatusCodeStrategy` / `ErrorStatusCodeMapper`; there is no Spring integration for it.
To key a status off an envelope `code()`, write your own `ErrorStatusCodeStrategy` bean (below).

### Explicit Mappings (covers 409, 422, 429, 503, …)

For status codes the heuristics do not produce, add entries to `hkj.web.error-status-mappings`:

```yaml
hkj:
  web:
    either:
      default-error-status: 500
    error-status-mappings:
      MfaAlreadyEnrolledError: 409   # Conflict
      PaymentDeclinedError: 422      # Unprocessable Entity
      MfaThrottledError: 429         # Too Many Requests
      ScheduledMaintenanceError: 503 # Service Unavailable
```

Mapping by fully-qualified class name is also supported, useful when two error classes share a
simple name across packages.

### Custom Strategy Bean (field-dependent status)

When the status depends on the error's field values, register an `ErrorStatusCodeStrategy`
bean. It replaces the default via `@ConditionalOnMissingBean`:

```java
@Bean
ErrorStatusCodeStrategy errorStatusCodeStrategy() {
    return (error, defaultStatus) -> switch (error) {
        case MfaThrottledError t when t.retryAfter() > 60 -> 503;
        case MfaThrottledError ignored -> 429;
        default -> ErrorStatusCodeMapper.determineStatusCode(error, defaultStatus);
    };
}
```

### Designing Error Class Names

If you can pick the names, choose ones that trip the heuristics so you avoid configuration:

```java
public sealed interface DomainError {
    record UserNotFound(String id) implements DomainError {}              // -> 404
    record ValidationFailed(List<String> errors) implements DomainError {} // -> 400
    record ForbiddenAction(String reason) implements DomainError {}      // -> 403
    record UnauthorizedAccess(String reason) implements DomainError {}   // -> 401
    record DuplicateResource(String id) implements DomainError {}        // -> default
}
```

If the desired status is 409, 422, 429, 503, or anything else outside the heuristics, prefer
an explicit mapping over renaming the variant: names should describe the domain, not the HTTP
table.

### Response Header Injection (Retry-After, WWW-Authenticate, …)

Error payloads can surface response headers by implementing `HttpHeaderCarrier`:

```java
public record MfaThrottledError(int retryAfterSeconds) implements DomainError, HttpHeaderCarrier {
    @Override public Map<String, String> headers() {
        return Map.of("Retry-After", Integer.toString(retryAfterSeconds));
    }
}
```

Honoured by `EitherPath`, `TryPath`, `ValidationPath`, `IOPath`, `CompletableFuturePath`,
`VTaskPath`, `FreePath`, and `EitherOrBothPath` (on its `Left`) handlers. Headers are added (not set), so multi-valued headers
like `WWW-Authenticate` and `Set-Cookie` accumulate as separate header lines, matching the
HTTP grammar; upstream headers from filters or interceptors are preserved. For single-valued
headers like `Retry-After`, the carrier should ensure the value appears at most once across
all payload elements. **Not** honoured by `VStreamPath` (SSE: headers commit before the
first event, set them via a filter / controller advice instead).

### Known Limits (so you don't fight the framework)

- The strategy bean is process-wide. For per-request behaviour, inspect request context inside
  the implementation.
- `MaybePath`'s `Nothing` carries no payload, so it cannot implement `HttpHeaderCarrier`. Wrap
  the result in `EitherPath<DomainError, T>` if you need headers or per-class status.
- `@ResponseStatus` on the handler method governs success values only; error status always
  comes from the strategy.

For the canonical end-to-end fixture (one endpoint per heuristic + every override + a header
case), see `hkj-spring/example/.../ErrorStatusFixtureController.java` and the matching
`ErrorStatusFixtureSliceTest`.

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

### EitherOrBothPath: Success That Still Carries Warnings

`EitherOrBoth` is the inclusive-or: `Left` (fatal), `Right` (clean success), `Both` (success **plus**
non-fatal warnings). `Either` cannot express `Both` (exclusive) and `Validated` cannot (no partial
value). Both `EitherOrBothPath<W, T>` and a bare `EitherOrBoth<W, T>` are accepted return types.

| Branch | Response |
|--------|----------|
| `Right(value)` | success status (200, or the method's `@ResponseStatus`) + value as the JSON body |
| `Both(warnings, value)` | the **same success status** + value as the body, warnings JSON-encoded into the `X-Hkj-Warnings` response header |
| `Left(error)` | the fatal case (no value): status from `ErrorStatusCodeStrategy`, body `{"success":false,"error":…}` (same shape as `EitherPath`) |

The `Both` case is the interesting one: a partial success still returns 200, but the caller is told
what was degraded rather than the warnings being silently dropped.

```java
@GetMapping("/{id}/report")
public EitherOrBothPath<NonEmptyList<String>, Report> getReport(@PathVariable String id) {
    return reportService.buildReport(id);
    // Right(report)                              -> 200, body = report
    // Both(["stale cache","partial index"], rep) -> 200, body = rep,
    //                                               X-Hkj-Warnings: ["stale cache","partial index"]
    // Left(["report unavailable"])               -> status via ErrorStatusCodeStrategy
}
```

```java
// Service side: Path.bothNel / rightNel / leftNel bake in NonEmptyList.semigroup()
public EitherOrBothPath<NonEmptyList<String>, Report> buildReport(String id) {
    return cache.isStale()
        ? Path.bothNel("stale cache", renderer.render(id))
        : Path.rightNel(renderer.render(id));
}
```

Toggle with `hkj.web.either-or-both-path-enabled` (default `true`). Handler:
`EitherOrBothPathReturnValueHandler`, wired by `HkjWebMvcAutoConfiguration`, so a `@WebMvcTest`
slice needs the usual `@ImportAutoConfiguration` (see [Testing](#testing)).

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
public VTaskPath<Either<DomainError, User>> getUserVTask(@PathVariable String id) {
    return Path.vtask(() -> userService.findById(id));
    // Framework converts VTaskPath to DeferredResult automatically
}
```

### VStreamPath: Server-Sent Events

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public VStreamPath<Update> streamUpdates() {
    return updateService.updates();
    // Framework converts VStreamPath to SSE automatically
}
```

---

## Jackson (`HkjJacksonModule`)

Registered by `HkjJacksonAutoConfiguration`; no code needed. Beyond `Either` / `Validated` / `Maybe`
/ `Try`, two more types bind in **both directions** (request bodies as well as responses):

| Type | JSON shape |
|------|------------|
| `EitherOrBoth<L, R>` | tag-based: `{"kind":"left","left":…}` / `{"kind":"right","right":…}` / `{"kind":"both","left":…,"right":…}` |
| `NonEmptyList<A>` | a plain JSON array: `["a","b"]`. Deserialising `[]`, a non-array, or an array containing `null` is an error (the type's non-emptiness is enforced on the wire) |

Neither adds a config key; both follow `hkj.json.custom-serializers-enabled`. `EitherOrBoth` has no
`*-format` toggle; it is always tagged, because three branches cannot be told apart untagged.

The `EitherOrBoth`, `NonEmptyList`, `Either`, and `Validated` deserialisers all do Jackson 3.x
**contextual generic-type resolution**: a declared `Either<DomainError, User>` on a field or
parameter binds its branches to those concrete types rather than to `Object`/`LinkedHashMap`. A raw
(un-parameterised) declaration still falls back to `Object`.

---

## Calling Other Services with `@HkjHttpClient`

The outbound inverse of the controller handlers. Annotate a Spring `@HttpExchange` interface with `@HkjHttpClient`; each method returns an Effect Path, and the generated client decodes the server's `{"success":false,"error":…}` envelope back into a typed error.

### Declare, configure, autowire

```java
@HttpExchange("/users")
@HkjHttpClient
public interface UserClientApi {

    @GetExchange("/{id}")
    EitherPath<ApiError, UserDto> getUser(@PathVariable String id);   // 2xx -> Right, 4xx/5xx -> Left(decoded)

    @PostExchange
    VTaskPath<Either<ApiError, UserDto>> create(@RequestBody UserDto body);  // deferred: withRetry/timeout/...
}
```

```yaml
spring:
  http:
    serviceclient:
      userClientApi:               # group = decapitalised interface name (or @HkjHttpClient(group="..."))
        base-url: http://users.internal
        read-timeout: 2s
```

```java
@Autowired UserClientApi userClientApi;   // autowire YOUR interface; the generated …Client is the bean
EitherPath<ApiError, UserDto> path = userClientApi.getUser("42");
path.run().fold(this::handleError, this::renderUser);
```

The starter bundles `spring-boot-restclient`, which binds `spring.http.serviceclient.*` and applies the base URL (it is **not** pulled in by `spring-boot-starter-web` alone). The generated `…ClientConfiguration` declares the `@ImportHttpServices` group and is component-scanned with your app (it sits in your interface's package); add an explicit `@ImportHttpServices(basePackages = "...")` only if your client interfaces live outside the scan.

### Return types

| Return type | Evaluation | non-2xx | Use when |
|-------------|-----------|---------|----------|
| `EitherPath<E, T>` | eager, blocks the caller | `Left(decoded)` | a plain request/response call |
| `VTaskPath<Either<E, T>>` | deferred on a virtual thread | `Left(decoded)` | you want `withRetry` / `withCircuitBreaker` / `timeout` |
| `MaybePath<T>` | eager, blocks the caller | 404 -> `Nothing` (others propagate) | absence is normal and untyped |

Empty 2xx body: `EitherPath`/`VTaskPath` yield `Right(null)`, `MaybePath` yields `Nothing`. Transport failures (connection refused, timeout) and undecodable bodies are **not** typed errors: they propagate synchronously from the eager variants, and as a failed task from the deferred ones. The generated client is a stateless, thread-safe singleton.

### Decoding errors: concrete vs sealed

A **concrete** error type binds with no extra annotations. A **sealed** `DomainError` needs `@JsonTypeInfo(use = Id.NAME, property = "type")` + `@JsonSubTypes` so Jackson can pick the subtype. **Never use `Id.CLASS` / `Id.MINIMAL_CLASS` or `ObjectMapper` default typing** on a mapper that decodes responses from a server you do not fully trust: those put a class name on the wire that Jackson loads and instantiates (the classic deserialisation-gadget vector). `Id.NAME` only resolves your registered subtype names.

### Overriding status -> error type

Precedence: **per-method `@OnStatus` > global `hkj.client.status-error-mappings` > the declared type.**

```java
@GetExchange("/{id}")
@OnStatus(value = 404, error = UserNotFoundError.class)   // each error() must be assignable to the
@OnStatus(value = 409, error = ConflictError.class)       // declared error type (compile-checked)
EitherPath<DomainError, UserDto> getUser(@PathVariable String id);
```

```yaml
hkj:                                       # the client analogue of hkj.web.error-status-mappings
  client:
    status-error-mappings:
      404: com.example.UserNotFoundError
      429: com.example.RateLimitError       # applies to a method only if assignable to its declared E
```

Wholesale: define a `ResponseErrorDecoder` / `ResponseErrorDecoderFactory` bean (e.g. for a non-HKJ server that does not emit the envelope; an undecodable body raises `ResponseErrorDecodeException`).

### Resilience, Retry-After, SSE

```java
Either<ApiError, UserDto> result =
    userClientApi.create(body)
        .withRetry(RetryPolicy.exponentialBackoffWithJitter(3, Duration.ofMillis(100)))
        .withCircuitBreaker(breaker)
        .timeout(Duration.ofSeconds(2))
        .unsafeRun();
```

`Retry-After` is a **hook**, not automatic: a custom decoder reads `ClientErrorResponse.retryAfter()` and feeds it into the retry policy. Consume an SSE endpoint with `HkjClientExchange.vstream(source, ElementType.class, jsonMapper)` -> `VStreamPath<T>` (drain it with `toList()` or bound it with `take(n)`; `headOption()`/`find(...)` short-circuit and may leave the response open).

### Gotchas

- A sealed error type with no `@JsonTypeInfo` cannot be decoded.
- Client interfaces outside the component scan: the generated config is not picked up, so no proxy is created. Add `@ImportHttpServices(basePackages = "...")`.
- A generic `@HkjHttpClient` interface is **codegen-only** (no bean, since a generic client cannot be a singleton): instantiate the facade for a concrete type yourself.
- `VStreamPath` is not generated automatically; consume SSE via `HkjClientExchange.vstream(...)`.
- A super-interface from a precompiled dependency jar must be built with `-parameters`, or its `@PathVariable`/`@RequestParam` arguments bind to `arg0`-style names.

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
        return Path.valid(req.name(), Semigroups.list())
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

> **Heads up: `@WebMvcTest` does not load third-party auto-configurations.**
> The hkj-spring-boot-starter registers `HkjJacksonAutoConfiguration` (for the
> `Either` / `Validated` JSON shape) and `HkjWebMvcAutoConfiguration` (for the
> `*ReturnValueHandler`s and HTTP status-code mapping) via
> `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
> `@WebMvcTest` slices those out, so without explicit imports MockMvc sees a
> raw serialized `Either` and no status mapping. Add the three auto-configs
> below to restore production behaviour inside the slice.

```java
@WebMvcTest(UserController.class)
@ImportAutoConfiguration({
    HkjAutoConfiguration.class,           // binds HkjProperties
    HkjJacksonAutoConfiguration.class,    // HkjJacksonModule → Either/Validated JSON shape
    HkjWebMvcAutoConfiguration.class      // return-value handlers + status mapping
})
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

For full-fidelity tests (all auto-configs, properties, filters, etc.), use
`@SpringBootTest + @AutoConfigureMockMvc` instead. A working slice-test recipe
lives at `hkj-spring/example/src/test/java/.../UserControllerWebMvcSliceTest.java`.

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

The full reference lives in `hkj-spring/CONFIGURATION.md`. The headline keys:

```yaml
# application.yml
hkj:
  web:
    # ---- Status codes ----
    either:
      default-error-status: 500             # alias: hkj.web.default-error-status
    maybe-nothing-status: 404               # MaybePath Nothing → 404
    try-failure-status: 500                 # TryPath Failure → 500
    validation-invalid-status: 400          # ValidationPath Invalid → 400
    io-failure-status: 500                  # IOPath failures → 500
    async-failure-status: 500               # CompletableFuturePath failures → 500
    vtask-failure-status: 500               # VTaskPath failures → 500
    vstream-failure-status: 500             # VStreamPath failures → 500
    free-path-failure-status: 500           # FreePath interpretation failures → 500

    # ---- Per-class overrides (this is the §1 fix; takes precedence over heuristics) ----
    error-status-mappings:
      MfaAlreadyEnrolledError: 409
      PaymentDeclinedError: 422
      MfaThrottledError: 429
      ScheduledMaintenanceError: 503

    # ---- Handler toggles (all default true) ----
    either-path-enabled: true
    either-or-both-path-enabled: true       # EitherOrBoth / EitherOrBothPath (Both -> X-Hkj-Warnings)
    maybe-path-enabled: true
    try-path-enabled: true
    validation-path-enabled: true
    io-path-enabled: true
    completable-future-path-enabled: true
    vtask-path-enabled: true
    vstream-path-enabled: true
    free-path-enabled: true

    # ---- Production vs. dev: include exception details? ----
    try-include-exception-details: false
    io-include-exception-details: false
    async-include-exception-details: false
    vtask-include-exception-details: false
    vstream-include-exception-details: false
    free-path-include-exception-details: false

  json:
    custom-serializers-enabled: true
    either-format: TAGGED                   # TAGGED | SIMPLE
    validated-format: TAGGED                # TAGGED | SIMPLE
    maybe-format: TAGGED                    # TAGGED | SIMPLE

  validation:
    enabled: true
    accumulate-errors: true                 # true = collect all; false = fail-fast

  virtual-threads:
    default-timeout-ms: 30000
    stream-timeout-ms: 60000

  # ---- @HkjHttpClient (outbound): global status -> error type; see "Calling Other Services" ----
  client:
    status-error-mappings:
      404: com.example.UserNotFoundError
      429: com.example.RateLimitError

# Per-client base URL / timeouts are Spring-native keys (not hkj.*), one block per group:
spring:
  http:
    serviceclient:
      userClientApi:
        base-url: http://users.internal
        read-timeout: 2s
```

For field-aware status decisions (e.g. `MfaThrottledError.retryAfter() > 60 → 503`),
register an `ErrorStatusCodeStrategy` bean (see the [HTTP Status Code Mapping](#http-status-code-mapping)
section above).

---

## Requirements

- Spring Boot 4.0.1+ for the server side (Jackson 3.x / `tools.jackson` namespace)
- Spring Boot 4.1+ / Spring Framework 7 for the `@HkjHttpClient` client side (it uses `@ImportHttpServices` and the `spring.http.serviceclient.*` binding from `spring-boot-restclient`)
- Java 25 with `--enable-preview`
