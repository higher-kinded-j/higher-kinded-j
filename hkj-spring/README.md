# Higher-Kinded-J Spring Boot Integration

This module provides Spring Boot integration for higher-kinded-j, enabling type-safe functional programming patterns in Spring applications using the Effect Path API.

## Version Compatibility

| hkj-spring Version | Spring Boot Version | Jackson Version | Notes |
|--------------------|---------------------|-----------------|-------|
| 0.2.7 | 3.5.7 | 2.x | Legacy support |
| 0.2.8+ | 4.0.1+ | 3.x | Effect Path API |
| 0.3.7+ | 4.0.3+ | 3.x | + VTaskPath/VStreamPath virtual thread handlers |

> **Important**: Version 0.2.8+ introduces breaking changes. Use 0.2.7 for Spring Boot 3.5.7 compatibility.

## Quick Start

### 1. Add Dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.higher-kinded-j:hkj-spring-boot-starter:VERSION")
}
```

### 2. Use Effect Path API in Controllers

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/{id}")
    public EitherPath<UserError, User> getUser(@PathVariable String id) {
        return userService.findById(id);
        // Right(user) → HTTP 200 with JSON
        // Left(UserNotFoundError) → HTTP 404 with error JSON
    }

    @GetMapping("/{id}/optional")
    public MaybePath<User> getUserOptional(@PathVariable String id) {
        return Path.maybe(userRepository.findById(id));
        // Just(user) → HTTP 200 with JSON
        // Nothing → HTTP 404
    }

    @PostMapping
    public ValidationPath<List<ValidationError>, User> createUser(@RequestBody CreateUserRequest req) {
        return validateAndCreateUser(req);
        // Valid(user) → HTTP 200 with JSON
        // Invalid(errors) → HTTP 400 with all accumulated errors
    }
}
```

That's it! The starter auto-configures everything.

## Features

### Effect Path API Support (8 Handler Types)

| Path Type | Success | Failure | Use Case |
|-----------|---------|---------|----------|
| `EitherPath<E, A>` | HTTP 200 | HTTP 4xx/5xx (based on error type) | Railway-oriented programming |
| `MaybePath<A>` | HTTP 200 | HTTP 404 | Optional values |
| `TryPath<A>` | HTTP 200 | HTTP 500 | Exception handling |
| `ValidationPath<E, A>` | HTTP 200 | HTTP 400 (with all errors) | Input validation |
| `IOPath<A>` | HTTP 200 | HTTP 500 | Deferred side effects |
| `CompletableFuturePath<A>` | HTTP 200 | HTTP 500 | Async operations |
| `VTaskPath<A>` | HTTP 200 (via DeferredResult) | HTTP 500 | Virtual thread async |
| `VStreamPath<A>` | SSE stream | SSE error event | Virtual thread streaming |

### Automatic Path → HTTP Response Conversion

Return Effect Path types from controllers and the framework automatically:
- Converts success cases → HTTP 200 with JSON body
- Converts failure cases → Appropriate HTTP error status with JSON body

Error → Status Code mapping for EitherPath:
- `*NotFoundError` → 404
- `*ValidationError` / `*InvalidError` → 400
- `*AuthorizationError` / `*ForbiddenError` → 403
- `*AuthenticationError` / `*UnauthorizedError` → 401
- Default → 400 (configurable)

### Zero Configuration

Auto-configuration activates when:
- higher-kinded-j is on the classpath
- Spring Web MVC is present
- Application is a servlet web app

### Configurable via Properties

```yaml
hkj:
  web:
    # Enable/disable individual handlers
    either-path-enabled: true
    maybe-path-enabled: true
    try-path-enabled: true
    validation-path-enabled: true
    io-path-enabled: true
    completable-future-path-enabled: true
    vtask-path-enabled: true
    vstream-path-enabled: true

    # Customize status codes
    default-error-status: 400
    maybe-nothing-status: 404
    try-failure-status: 500
    validation-invalid-status: 400
    vtask-failure-status: 500
    vstream-failure-status: 500

    # Exception details (disable in production!)
    try-include-exception-details: false
    io-include-exception-details: false
    async-include-exception-details: false
    vtask-include-exception-details: false
    vstream-include-exception-details: false

  # Virtual thread configuration (VTaskPath and VStreamPath)
  virtual-threads:
    default-timeout-ms: 30000     # VTask timeout (30s)
    stream-timeout-ms: 60000      # VStream timeout (60s, 0 = no timeout)
```

## Module Structure

```
hkj-spring/
├── autoconfigure/     # Auto-configuration classes
│   ├── HkjAutoConfiguration
│   ├── HkjWebMvcAutoConfiguration
│   ├── HkjProperties
│   └── web/returnvalue/
│       ├── EitherPathReturnValueHandler
│       ├── MaybePathReturnValueHandler
│       ├── TryPathReturnValueHandler
│       ├── ValidationPathReturnValueHandler
│       ├── IOPathReturnValueHandler
│       ├── CompletableFuturePathReturnValueHandler
│       ├── VTaskPathReturnValueHandler     # Virtual thread async
│       ├── VStreamPathReturnValueHandler   # Virtual thread SSE streaming
│       └── ErrorStatusCodeMapper
├── starter/           # Dependency aggregator
└── example/           # Working example application
```

## Example Application

See `hkj-spring/example/` for a complete working example.

### Running the Example

```bash
./gradlew :hkj-spring:example:bootRun
```

### Try These Endpoints

```bash
# Get user by ID - EitherPath (200 OK)
curl http://localhost:8080/api/users/1

# Get non-existent user - EitherPath (404 Not Found)
curl http://localhost:8080/api/users/999

# Get user optional - MaybePath (200 OK or 404)
curl http://localhost:8080/api/users/1/optional

# Create user with validation - ValidationPath
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","firstName":"Test","lastName":"User"}'

# Config with deferred IO - IOPath
curl http://localhost:8080/api/config

# Async user fetch - CompletableFuturePath
curl http://localhost:8080/api/users/1/async

# Virtual thread user fetch - VTaskPath
curl http://localhost:8080/api/vt/users/1

# Structured concurrency enrichment - VTaskPath with Scope
curl http://localhost:8080/api/vt/users/1/enriched

# Stream users as SSE - VStreamPath
curl -N http://localhost:8080/api/vt/users/stream

# Stream tick events - VStreamPath
curl -N http://localhost:8080/api/vt/ticks?count=5
```

## How It Works

### Path Return Value Handlers

Each Effect Path type has a dedicated handler:

```java
@GetMapping("/{id}")
public EitherPath<DomainError, User> getUser(@PathVariable String id) {
    return Path.right(userId)
        .via(userService::findById)  // Returns EitherPath<Error, User>
        .peek(user -> log.info("Found user: {}", user.id()));
}
```

The handler:
1. Checks if return type is `EitherPath`
2. Calls `path.run()` to get the underlying `Either`
3. Uses `either.fold()` to handle both cases
4. For `Left`: Writes error JSON with appropriate HTTP status
5. For `Right`: Writes success JSON with HTTP 200

### Validation with Error Accumulation

```java
@PostMapping
public ValidationPath<List<ValidationError>, User> createUser(@RequestBody CreateUserRequest req) {
    return Path.<List<ValidationError>, String>valid(req.email(), Semigroups.list())
        .zipWith3Accum(
            validateName(req.firstName()),
            validateAge(req.age()),
            User::new
        );
    // All validation errors are accumulated, not fail-fast!
}
```

Response for invalid input:
```json
{
  "valid": false,
  "errors": [
    {"field": "email", "message": "Invalid email format"},
    {"field": "firstName", "message": "Name is required"},
    {"field": "age", "message": "Age must be positive"}
  ],
  "errorCount": 3
}
```

### Deferred IO Execution

```java
@GetMapping("/report")
public IOPath<Report> generateReport() {
    return Path.io(() -> reportService.generate())
        .peek(r -> log.info("Generated report: {}", r.id()))
        .ensuring(() -> cleanupTempFiles());
}
// IO is executed at the edge (when response is written)
```

### Async with CompletableFuturePath

```java
@GetMapping("/users/{id}/async")
public CompletableFuturePath<User> getUserAsync(@PathVariable String id) {
    return Path.futureAsync(() -> remoteService.fetchUser(id))
        .map(user -> enrichWithProfile(user));
}
// Non-blocking - request thread is released while waiting
```

### Virtual Thread Async with VTaskPath

```java
@GetMapping("/users/{id}")
public VTaskPath<User> getUser(@PathVariable String id) {
    return vtUserService.findById(id);
}
// Runs on a virtual thread via DeferredResult — no thread pool needed
```

The handler:
1. Checks if return type is `VTaskPath`
2. Calls `VTask.runAsync()` to execute on a virtual thread
3. Wraps result in a Spring `DeferredResult` with configurable timeout
4. On success: writes JSON with HTTP 200
5. On failure: writes error JSON with configured status code

### Virtual Thread SSE Streaming with VStreamPath

```java
@GetMapping(value = "/users/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public VStreamPath<User> streamUsers() {
    return vtUserService.streamAllUsers();
}
// Pull-based streaming on virtual threads — no WebFlux/Reactor needed
```

The handler:
1. Checks if return type is `VStreamPath`
2. Runs the stream on a virtual thread
3. Emits each element as an SSE `data:` event with JSON payload
4. Sends a completion or error event when the stream ends

### Structured Concurrency with Scope

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

## Benefits

### Type-Safe Error Handling

```java
// Before: Exceptions hidden in implementation
public User getUser(String id) throws UserNotFoundException {
    // ...
}

// After: Errors explicit in type signature
public EitherPath<DomainError, User> getUser(String id) {
    // Compiler enforces error handling
}
```

### Railway-Oriented Programming

```java
@GetMapping("/{id}/orders")
public EitherPath<DomainError, List<Order>> getUserOrders(@PathVariable String id) {
    return Path.right(id)
        .via(userService::findById)           // EitherPath<Error, User>
        .via(orderService::getOrdersForUser); // EitherPath<Error, List<Order>>
    // Errors short-circuit automatically
}
```

### Composable Operations

```java
@GetMapping("/{id}/profile")
public EitherPath<DomainError, UserProfile> getUserProfile(@PathVariable String id) {
    return userService.findById(id)
        .map(User::toProfile)           // Functor composition
        .peek(p -> metrics.record(p))   // Side effects
        .mapError(e -> e.withContext("profile")); // Error transformation
}
```

## Architecture

### Spring Boot 4.x Compatibility

- Uses `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` for auto-configuration discovery
- Jackson 3.x for JSON serialization (`tools.jackson` package)
- Jakarta EE 11 / Servlet 6.1

### Conditional Configuration

Only activates when required dependencies are present:
- `@ConditionalOnClass(Kind.class)` - higher-kinded-j present
- `@ConditionalOnClass(DispatcherServlet.class)` - Spring MVC present
- `@ConditionalOnWebApplication(type = SERVLET)` - Servlet app

### Non-Invasive

Doesn't modify existing Spring Boot behavior. Only adds support for Effect Path return types.

## Testing

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
            .andExpect(jsonPath("$.id").value("1"));
    }

    @Test
    void shouldReturn404ForMissingUser() throws Exception {
        mockMvc.perform(get("/api/users/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void shouldReturn400WithAccumulatedValidationErrors() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\",\"firstName\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.errorCount").value(greaterThan(1)));
    }
}
```

## Requirements

- Java 21+
- Spring Boot 4.0.3+
- higher-kinded-j core library

## Related Documentation

- [Configuration Reference](CONFIGURATION.md)
- [Jackson Serialization](JACKSON_SERIALIZATION.md)
- [Security Integration](SECURITY.md)
- [Actuator Support](ACTUATOR.md)

## License

MIT (same as higher-kinded-j)

## Migration from 0.2.7

If upgrading from 0.2.7 (Spring Boot 3.5.7):

1. **Update Spring Boot**: Upgrade to Spring Boot 4.0.1+
2. **Replace raw types with Path types**:
   - `Either<E, A>` → `EitherPath<E, A>` (use `Path.right()`, `Path.left()`, `Path.either()`)
   - `Validated<E, A>` → `ValidationPath<E, A>` (use `Path.valid()`, `Path.invalid()`)
   - `EitherT<CompletableFuture.Witness, E, A>` → `CompletableFuturePath<A>` (use `Path.completableFuture()`)
3. **Update Jackson imports**: `com.fasterxml.jackson` → `tools.jackson`
4. **Update configuration properties**: See [Configuration Reference](CONFIGURATION.md)

## Contributing

See main [higher-kinded-j CONTRIBUTING.md](../CONTRIBUTING.md)
