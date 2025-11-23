# Higher-Kinded-J Spring Boot Integration - Implementation Roadmap

This document provides a detailed plan for implementing the remaining features for the Spring Boot integration.

---

## Current Status (✅ Complete)

- ✅ Core auto-configuration (`HkjAutoConfiguration`)
- ✅ Either return value handler (`EitherReturnValueHandler`)
- ✅ Basic example application with REST endpoints
- ✅ Module structure (autoconfigure, starter, example)
- ✅ Gradle build configuration
- ✅ Documentation (README)

---

## Phase 2: Core Enhancement Features

### 1. Validated Return Value Handler (Accumulating Validation)

**Goal:** Support `Validated<List<E>, A>` return types in controllers to show ALL validation errors at once.

#### Implementation Location

**autoconfigure/src/main/java/org/higherkindedj/spring/web/returnvalue/**
- `ValidatedReturnValueHandler.java` - New handler for Validated types

**autoconfigure/src/main/java/org/higherkindedj/spring/autoconfigure/**
- `HkjWebMvcAutoConfiguration.java` - Register the new handler

#### Implementation Details

```java
// ValidatedReturnValueHandler.java
public class ValidatedReturnValueHandler implements HandlerMethodReturnValueHandler {

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        return Validated.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public void handleReturnValue(Object returnValue, ...) {
        if (returnValue instanceof Validated<?, ?> validated) {
            validated.fold(
                errors -> {
                    // HTTP 400 Bad Request
                    response.setStatus(HttpStatus.BAD_REQUEST.value());
                    writeJson(response, Map.of(
                        "valid", false,
                        "errors", errors  // All errors returned!
                    ));
                },
                value -> {
                    // HTTP 200 OK
                    response.setStatus(HttpStatus.OK.value());
                    writeJson(response, value);
                }
            );
        }
    }
}
```

**Register in HkjWebMvcAutoConfiguration:**
```java
newHandlers.add(new ValidatedReturnValueHandler());  // Add after EitherReturnValueHandler
```

#### Example Usage

**example/src/main/java/org/higherkindedj/spring/example/controller/**
- `ValidationController.java` - New controller demonstrating Validated

```java
@RestController
@RequestMapping("/api/validation")
public class ValidationController {

    @PostMapping("/user")
    public Validated<List<ValidationError>, User> validateUser(@RequestBody UserRequest req) {
        return userService.validateAndCreate(req);
        // Returns ALL validation errors at once
    }
}
```

**example/src/main/java/org/higherkindedj/spring/example/service/**
- Update `UserService.java` to add validated methods

```java
public Validated<List<ValidationError>, User> validateAndCreate(UserRequest req) {
    Applicative<Validated.Witness<List<ValidationError>>> A =
        ValidatedInstances.applicative(Semigroup.list());

    return Applicative.map3(
        A,
        validateEmail(req.email()),
        validateFirstName(req.firstName()),
        validateLastName(req.lastName()),
        (email, first, last) -> new User(generateId(), email, first, last)
    );
    // All three validators run, errors accumulated
}

private Validated<List<ValidationError>, String> validateEmail(String email) {
    List<ValidationError> errors = new ArrayList<>();
    if (email == null || email.isBlank()) {
        errors.add(new ValidationError("email", "Email is required"));
    }
    if (email != null && !email.contains("@")) {
        errors.add(new ValidationError("email", "Invalid email format"));
    }
    return errors.isEmpty()
        ? Validated.valid(email)
        : Validated.invalid(errors);
}
```

#### Dependencies
- None (Validated is in hkj-core)

#### Testing
- Test valid input → HTTP 200
- Test single validation error → HTTP 400 with 1 error
- Test multiple validation errors → HTTP 400 with ALL errors
- Test that errors are accumulated (not fail-fast)

---

### 2. EitherT Async Support

**Goal:** Support `EitherT<CompletableFuture.Witness, E, A>` return types for async operations with error handling.

#### Implementation Location

**autoconfigure/src/main/java/org/higherkindedj/spring/web/returnvalue/**
- `EitherTReturnValueHandler.java` - New async handler

#### Implementation Details

```java
public class EitherTReturnValueHandler implements AsyncHandlerMethodReturnValueHandler {

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        return EitherT.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public boolean isAsyncReturnValue(Object returnValue, MethodParameter returnType) {
        return true;  // EitherT is async
    }

    @Override
    public void handleReturnValue(Object returnValue, ...) {
        if (returnValue instanceof EitherT<?, ?, ?> eitherT) {
            // Extract the CompletableFuture<Either<E, A>>
            CompletableFuture<Either<?, ?>> future =
                unwrapEitherT(eitherT);

            // Create DeferredResult for Spring async handling
            DeferredResult<ResponseEntity<?>> deferredResult = new DeferredResult<>();

            future.whenComplete((either, throwable) -> {
                if (throwable != null) {
                    deferredResult.setErrorResult(throwable);
                } else {
                    either.fold(
                        error -> deferredResult.setResult(
                            ResponseEntity.status(determineStatusCode(error))
                                .body(Map.of("success", false, "error", error))
                        ),
                        value -> deferredResult.setResult(
                            ResponseEntity.ok(value)
                        )
                    );
                }
            });

            WebAsyncUtils.getAsyncManager(webRequest)
                .startDeferredResultProcessing(deferredResult, mavContainer);
        }
    }

    @SuppressWarnings("unchecked")
    private <F, E, A> CompletableFuture<Either<E, A>> unwrapEitherT(EitherT<?, ?, ?> eitherT) {
        // Unwrap EitherT to get CompletableFuture<Either<E, A>>
        Kind<CompletableFutureKind.Witness, Either<E, A>> kind =
            (Kind<CompletableFutureKind.Witness, Either<E, A>>) eitherT.value();
        return CompletableFutureKind.narrow(kind);
    }
}
```

#### Example Usage

**example/src/main/java/org/higherkindedj/spring/example/controller/**
- `AsyncController.java` - New controller for async operations

```java
@RestController
@RequestMapping("/api/async")
public class AsyncController {

    @Autowired
    private AsyncUserService asyncUserService;

    @GetMapping("/users/{id}")
    public EitherT<CompletableFuture.Witness, DomainError, User> getUserAsync(
            @PathVariable String id) {
        return asyncUserService.findByIdAsync(id);
        // Framework handles async + error handling automatically
    }

    @GetMapping("/users/{id}/enriched")
    public EitherT<CompletableFuture.Witness, DomainError, EnrichedUser> getEnrichedUser(
            @PathVariable String id) {
        // Chain multiple async operations
        return asyncUserService.enrichUserData(id);
    }
}
```

**example/src/main/java/org/higherkindedj/spring/example/service/**
- `AsyncUserService.java` - Service with async methods

```java
@Service
public class AsyncUserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Executor asyncExecutor;

    public EitherT<CompletableFuture.Witness, DomainError, User> findByIdAsync(String id) {
        CompletableFuture<Either<DomainError, User>> future =
            CompletableFuture.supplyAsync(
                () -> userRepository.findById(id),
                asyncExecutor
            );

        return EitherT.liftF(CompletableFutureKind.witness(), future);
    }

    public EitherT<CompletableFuture.Witness, DomainError, EnrichedUser> enrichUserData(String id) {
        MonadError<EitherT.Witness<CompletableFuture.Witness, DomainError>> M =
            EitherT.monadError(CompletableFutureKind.monad());

        return M.flatMap(
            findByIdAsync(id),
            user -> M.flatMap(
                loadProfileAsync(user),
                profile -> M.map(
                    loadOrdersAsync(user),
                    orders -> new EnrichedUser(user, profile, orders)
                )
            )
        );
    }
}
```

#### Dependencies
- None (EitherT and CompletableFutureKind are in hkj-core)

#### Configuration
Add async executor configuration:

```java
@Configuration
public class AsyncConfig {

    @Bean
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("hkj-async-");
        executor.initialize();
        return executor;
    }
}
```

#### Testing
- Test async success case → HTTP 200 (after delay)
- Test async error case → HTTP 4xx/5xx (after delay)
- Test async timeout
- Test multiple concurrent async requests
- Test chained async operations (flatMap)

---

### 3. Jackson Custom Serializers

**Goal:** Provide clean JSON serialization for Either, Validated, and Option types.

#### Implementation Location

**autoconfigure/src/main/java/org/higherkindedj/spring/json/**
- `HkjJacksonModule.java` - Custom Jackson module
- `EitherSerializer.java` - Serializer for Either
- `EitherDeserializer.java` - Deserializer for Either
- `ValidatedSerializer.java` - Serializer for Validated
- `ValidatedDeserializer.java` - Deserializer for Validated
- `OptionSerializer.java` - Serializer for Option
- `OptionDeserializer.java` - Deserializer for Option

**autoconfigure/src/main/java/org/higherkindedj/spring/autoconfigure/**
- `HkjJacksonAutoConfiguration.java` - New auto-config

#### Implementation Details

```java
// HkjJacksonModule.java
public class HkjJacksonModule extends SimpleModule {

    public HkjJacksonModule() {
        super("HkjJacksonModule");

        // Either serialization
        addSerializer(Either.class, new EitherSerializer());
        addDeserializer(Either.class, new EitherDeserializer());

        // Validated serialization
        addSerializer(Validated.class, new ValidatedSerializer());
        addDeserializer(Validated.class, new ValidatedDeserializer());

        // Option serialization
        addSerializer(Option.class, new OptionSerializer());
        addDeserializer(Option.class, new OptionDeserializer());
    }
}

// EitherSerializer.java
public class EitherSerializer extends JsonSerializer<Either<?, ?>> {

    @Override
    public void serialize(Either<?, ?> value, JsonGenerator gen,
                         SerializerProvider serializers) throws IOException {
        value.fold(
            left -> {
                gen.writeStartObject();
                gen.writeBooleanField("isRight", false);
                gen.writeObjectField("left", left);
                gen.writeEndObject();
                return null;
            },
            right -> {
                gen.writeStartObject();
                gen.writeBooleanField("isRight", true);
                gen.writeObjectField("right", right);
                gen.writeEndObject();
                return null;
            }
        );
    }
}

// EitherDeserializer.java
public class EitherDeserializer extends JsonDeserializer<Either<?, ?>> {

    @Override
    public Either<?, ?> deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        boolean isRight = node.get("isRight").asBoolean();

        if (isRight) {
            Object value = ctxt.readTreeAsValue(node.get("right"), Object.class);
            return Either.right(value);
        } else {
            Object error = ctxt.readTreeAsValue(node.get("left"), Object.class);
            return Either.left(error);
        }
    }
}

// ValidatedSerializer.java
public class ValidatedSerializer extends JsonSerializer<Validated<?, ?>> {

    @Override
    public void serialize(Validated<?, ?> value, JsonGenerator gen,
                         SerializerProvider serializers) throws IOException {
        value.fold(
            errors -> {
                gen.writeStartObject();
                gen.writeBooleanField("valid", false);
                gen.writeObjectField("errors", errors);
                gen.writeEndObject();
                return null;
            },
            valid -> {
                gen.writeStartObject();
                gen.writeBooleanField("valid", true);
                gen.writeObjectField("value", valid);
                gen.writeEndObject();
                return null;
            }
        );
    }
}

// OptionSerializer.java
public class OptionSerializer extends JsonSerializer<Option<?>> {

    @Override
    public void serialize(Option<?> value, JsonGenerator gen,
                         SerializerProvider serializers) throws IOException {
        value.fold(
            () -> gen.writeNull(),
            some -> {
                serializers.defaultSerializeValue(some, gen);
                return null;
            }
        );
    }
}
```

**Auto-configuration:**

```java
// HkjJacksonAutoConfiguration.java
@AutoConfiguration
@ConditionalOnClass({ObjectMapper.class, Either.class})
@AutoConfigureAfter(JacksonAutoConfiguration.class)
public class HkjJacksonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "hkjJacksonModule")
    public Module hkjJacksonModule() {
        return new HkjJacksonModule();
    }
}
```

**Register in AutoConfiguration.imports:**
```
org.higherkindedj.spring.autoconfigure.HkjJacksonAutoConfiguration
```

#### Example JSON Output

**Either:**
```json
// Right value
{
  "isRight": true,
  "right": {"id": "1", "email": "user@example.com"}
}

// Left value
{
  "isRight": false,
  "left": {"message": "User not found"}
}
```

**Validated:**
```json
// Valid
{
  "valid": true,
  "value": {"id": "1", "email": "user@example.com"}
}

// Invalid
{
  "valid": false,
  "errors": [
    {"field": "email", "message": "Invalid format"},
    {"field": "firstName", "message": "Required"}
  ]
}
```

**Option:**
```json
// Some
{"id": "1", "email": "user@example.com"}

// None
null
```

#### Testing
- Test Either.right serialization
- Test Either.left serialization
- Test Either deserialization
- Test Validated.valid serialization
- Test Validated.invalid serialization
- Test Option.some serialization
- Test Option.none serialization

---

### 4. Configuration Properties

**Goal:** Provide configurable properties for customizing the integration behavior.

#### Implementation Location

**autoconfigure/src/main/java/org/higherkindedj/spring/autoconfigure/**
- `HkjProperties.java` - Already exists, enhance it

#### Implementation Details

```java
@ConfigurationProperties(prefix = "hkj")
public class HkjProperties {

    private Web web = new Web();
    private Validation validation = new Validation();
    private Json json = new Json();
    private Async async = new Async();

    public static class Web {
        /**
         * Enable automatic Either to ResponseEntity conversion
         */
        private boolean eitherResponseEnabled = true;

        /**
         * Enable automatic Validated to ResponseEntity conversion
         */
        private boolean validatedResponseEnabled = true;

        /**
         * Default HTTP status for Left values when error type is unknown
         */
        private int defaultErrorStatus = 400;

        /**
         * Enable EitherT async support
         */
        private boolean asyncEitherTEnabled = true;

        /**
         * Custom error status code mappings
         * Format: "ErrorClassName=StatusCode"
         */
        private Map<String, Integer> errorStatusMappings = new HashMap<>();

        // getters/setters
    }

    public static class Validation {
        /**
         * Enable Validated-based validation
         */
        private boolean enabled = true;

        /**
         * Accumulate all validation errors (true) or fail-fast (false)
         */
        private boolean accumulateErrors = true;

        /**
         * Maximum number of errors to accumulate (0 = unlimited)
         */
        private int maxErrors = 0;

        // getters/setters
    }

    public static class Json {
        /**
         * Enable custom Jackson serializers for Either, Validated, Option
         */
        private boolean customSerializersEnabled = true;

        /**
         * Format for Either JSON output: SIMPLE or TAGGED
         * SIMPLE: {"value": ...} or {"error": ...}
         * TAGGED: {"isRight": true/false, "right/left": ...}
         */
        private EitherFormat eitherFormat = EitherFormat.TAGGED;

        /**
         * Format for Option JSON output: NULL or OBJECT
         * NULL: none -> null, some -> value
         * OBJECT: {"present": true/false, "value": ...}
         */
        private OptionFormat optionFormat = OptionFormat.NULL;

        public enum EitherFormat { SIMPLE, TAGGED }
        public enum OptionFormat { NULL, OBJECT }

        // getters/setters
    }

    public static class Async {
        /**
         * Thread pool size for async IO execution
         */
        private int executorCorePoolSize = 10;

        /**
         * Thread pool max size for async IO execution
         */
        private int executorMaxPoolSize = 20;

        /**
         * Queue capacity for async IO executor
         */
        private int executorQueueCapacity = 100;

        /**
         * Thread name prefix for async IO executor
         */
        private String executorThreadNamePrefix = "hkj-async-";

        /**
         * Default timeout for async operations (milliseconds)
         */
        private long defaultTimeoutMs = 30000;

        // getters/setters
    }

    // getters/setters for nested classes
}
```

**Enable configuration properties:**
```java
@AutoConfiguration
@ConditionalOnClass(Kind.class)
@EnableConfigurationProperties(HkjProperties.class)  // Add this
public class HkjAutoConfiguration {
    // ...
}
```

**Use properties in handlers:**
```java
@Bean
public WebMvcRegistrations hkjWebMvcRegistrations(HkjProperties properties) {
    return new WebMvcRegistrations() {
        @Override
        public RequestMappingHandlerAdapter getRequestMappingHandlerAdapter() {
            return new RequestMappingHandlerAdapter() {
                @Override
                public void afterPropertiesSet() {
                    super.afterPropertiesSet();

                    List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>();

                    if (properties.getWeb().isEitherResponseEnabled()) {
                        handlers.add(new EitherReturnValueHandler(properties));
                    }

                    if (properties.getWeb().isValidatedResponseEnabled()) {
                        handlers.add(new ValidatedReturnValueHandler());
                    }

                    if (properties.getWeb().isAsyncEitherTEnabled()) {
                        handlers.add(new EitherTReturnValueHandler());
                    }

                    handlers.addAll(new ArrayList<>(getReturnValueHandlers()));
                    setReturnValueHandlers(handlers);
                }
            };
        }
    };
}
```

#### Example Configuration

**example/src/main/resources/application.yml:**
```yaml
hkj:
  web:
    either-response-enabled: true
    validated-response-enabled: true
    default-error-status: 400
    async-either-t-enabled: true
    error-status-mappings:
      UserNotFoundError: 404
      ValidationError: 400
      AuthorizationError: 403

  validation:
    enabled: true
    accumulate-errors: true
    max-errors: 10

  json:
    custom-serializers-enabled: true
    either-format: TAGGED
    option-format: NULL

  async:
    executor-core-pool-size: 10
    executor-max-pool-size: 20
    executor-queue-capacity: 100
    executor-thread-name-prefix: "hkj-async-"
    default-timeout-ms: 30000
```

#### Documentation
Add configuration reference to README with all available properties and defaults.

---

## Phase 3: Advanced Integration Features

### 5. Spring Security Integration

**Goal:** Integrate Either/Validated with Spring Security for authentication and authorization.

#### Implementation Location

**New module:** `hkj-spring/starter-security/`

**autoconfigure/src/main/java/org/higherkindedj/spring/security/**
- `EitherAuthenticationProvider.java`
- `EitherUserDetailsService.java`
- `SecurityEitherConverter.java`

**autoconfigure/src/main/java/org/higherkindedj/spring/autoconfigure/**
- `HkjSecurityAutoConfiguration.java`

#### Implementation Details

```java
// EitherAuthenticationProvider.java
public class EitherAuthenticationProvider implements AuthenticationProvider {

    private final EitherUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        Either<SecurityError, UserDetails> result =
            userDetailsService.loadUserByUsername(username)
                .flatMap(user -> validatePassword(user, password));

        return result.fold(
            error -> throw new BadCredentialsException(error.message()),
            user -> new UsernamePasswordAuthenticationToken(
                user, password, user.getAuthorities()
            )
        );
    }
}

// EitherUserDetailsService.java
public interface EitherUserDetailsService {
    Either<SecurityError, UserDetails> loadUserByUsername(String username);
}

// Security error types
public sealed interface SecurityError permits
    AuthenticationError, AuthorizationError {
    String message();
}

public record AuthenticationError(String message) implements SecurityError {}
public record AuthorizationError(String message) implements SecurityError {}
```

**Controller integration:**
```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/login")
    public Either<SecurityError, AuthToken> login(@RequestBody LoginRequest request) {
        return authService.authenticate(request.username(), request.password());
        // Left -> HTTP 401
        // Right -> HTTP 200 with token
    }

    @GetMapping("/user")
    @PreAuthorize("hasRole('USER')")
    public Either<SecurityError, UserInfo> getCurrentUser(Principal principal) {
        return userService.findByUsername(principal.getName());
    }
}
```

#### Dependencies
**starter-security/build.gradle.kts:**
```kotlin
dependencies {
    api(project(":hkj-spring:autoconfigure"))
    api("org.springframework.boot:spring-boot-starter-security")
}
```

#### Testing
- Test successful authentication → HTTP 200
- Test failed authentication → HTTP 401
- Test authorization failure → HTTP 403
- Test protected endpoints with Either
- Test JWT token generation with Either

---

### 6. WebFlux Support (Reactive)

**Goal:** Support reactive programming with Reactor types (Mono, Flux) combined with Either.

#### Implementation Location

**New module:** `hkj-spring/starter-webflux/`

**autoconfigure/src/main/java/org/higherkindedj/spring/webflux/**
- `EitherMonoHandler.java`
- `EitherFluxHandler.java`
- `ReactiveEitherConverter.java`

**autoconfigure/src/main/java/org/higherkindedj/spring/autoconfigure/**
- `HkjWebFluxAutoConfiguration.java`

#### Implementation Details

```java
// EitherMonoHandler.java
public class EitherMonoHandler implements HandlerResultHandler {

    @Override
    public boolean supports(HandlerResult result) {
        return result.getReturnType().resolve() == Mono.class &&
               hasEitherTypeParameter(result.getReturnType());
    }

    @Override
    public Mono<Void> handleResult(ServerWebExchange exchange, HandlerResult result) {
        Mono<Either<?, ?>> monoEither = (Mono<Either<?, ?>>) result.getReturnValue();

        return monoEither.flatMap(either ->
            either.fold(
                error -> {
                    exchange.getResponse().setStatusCode(determineStatusCode(error));
                    return writeJson(exchange, Map.of("success", false, "error", error));
                },
                value -> {
                    exchange.getResponse().setStatusCode(HttpStatus.OK);
                    return writeJson(exchange, value);
                }
            )
        );
    }
}
```

**Reactive controller:**
```java
@RestController
@RequestMapping("/api/reactive")
public class ReactiveUserController {

    @Autowired
    private ReactiveUserService userService;

    @GetMapping("/users/{id}")
    public Mono<Either<DomainError, User>> getUserReactive(@PathVariable String id) {
        return userService.findByIdReactive(id);
        // Reactive + Either combined!
    }

    @GetMapping("/users")
    public Flux<Either<DomainError, User>> getAllUsersReactive() {
        return userService.findAllReactive();
        // Stream of Either values
    }
}
```

**Service with R2DBC:**
```java
@Service
public class ReactiveUserService {

    @Autowired
    private R2dbcRepository userRepository;

    public Mono<Either<DomainError, User>> findByIdReactive(String id) {
        return userRepository.findById(id)
            .map(Either::<DomainError, User>right)
            .defaultIfEmpty(Either.left(new UserNotFoundError(id)));
    }
}
```

#### Dependencies
**starter-webflux/build.gradle.kts:**
```kotlin
dependencies {
    api(project(":hkj-spring:autoconfigure"))
    api("org.springframework.boot:spring-boot-starter-webflux")
}
```

#### Testing
- Test Mono<Either<E, A>> endpoints
- Test Flux<Either<E, A>> streams
- Test backpressure handling
- Test error propagation in reactive chains

---

### 7. Spring Boot Actuator Metrics

**Goal:** Expose metrics for Either/Validated usage to monitor error rates and performance.

#### Implementation Location

**autoconfigure/src/main/java/org/higherkindedj/spring/actuator/**
- `EitherMetrics.java`
- `ValidatedMetrics.java`
- `HkjHealthIndicator.java`

**autoconfigure/src/main/java/org/higherkindedj/spring/autoconfigure/**
- `HkjActuatorAutoConfiguration.java`

#### Implementation Details

```java
// EitherMetrics.java
@Component
@ConditionalOnClass(MeterRegistry.class)
public class EitherMetrics {

    private final MeterRegistry registry;
    private final Counter successCounter;
    private final Counter errorCounter;
    private final Map<String, Counter> errorTypeCounters = new ConcurrentHashMap<>();

    public EitherMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.successCounter = Counter.builder("hkj.either.success")
            .description("Number of successful Either (Right) values")
            .register(registry);
        this.errorCounter = Counter.builder("hkj.either.error")
            .description("Number of error Either (Left) values")
            .register(registry);
    }

    public void recordSuccess() {
        successCounter.increment();
    }

    public void recordError(String errorType) {
        errorCounter.increment();
        errorTypeCounters.computeIfAbsent(errorType, type ->
            Counter.builder("hkj.either.error")
                .tag("type", type)
                .register(registry)
        ).increment();
    }
}

// Instrumented EitherReturnValueHandler
public class EitherReturnValueHandler implements HandlerMethodReturnValueHandler {

    private final EitherMetrics metrics;

    @Override
    public void handleReturnValue(Object returnValue, ...) {
        if (returnValue instanceof Either<?, ?> either) {
            either.fold(
                error -> {
                    metrics.recordError(error.getClass().getSimpleName());
                    handleError(error, response);
                    return null;
                },
                value -> {
                    metrics.recordSuccess();
                    handleSuccess(value, response);
                    return null;
                }
            );
        }
    }
}

// HkjHealthIndicator.java
@Component
public class HkjHealthIndicator implements HealthIndicator {

    private final EitherMetrics metrics;

    @Override
    public Health health() {
        long successCount = getSuccessCount();
        long errorCount = getErrorCount();
        double errorRate = (double) errorCount / (successCount + errorCount);

        Health.Builder builder = errorRate > 0.5
            ? Health.down()
            : Health.up();

        return builder
            .withDetail("successCount", successCount)
            .withDetail("errorCount", errorCount)
            .withDetail("errorRate", String.format("%.2f%%", errorRate * 100))
            .build();
    }
}
```

#### Exposed Metrics

**Counters:**
- `hkj.either.success` - Total successful Either values
- `hkj.either.error` - Total error Either values
- `hkj.either.error{type=UserNotFoundError}` - Errors by type
- `hkj.validated.valid` - Total valid Validated values
- `hkj.validated.invalid` - Total invalid Validated values

**Gauges:**
- `hkj.either.error.rate` - Current error rate (0.0 - 1.0)

**Timers:**
- `hkj.either.processing.time` - Time to process Either handlers

**Health Indicators:**
- `hkj` - Overall health based on error rates

#### Access Metrics
```bash
# Prometheus format
curl http://localhost:8080/actuator/prometheus

# JSON format
curl http://localhost:8080/actuator/metrics/hkj.either.success
```

#### Dependencies
**autoconfigure/build.gradle.kts:**
```kotlin
dependencies {
    compileOnly("org.springframework.boot:spring-boot-starter-actuator")
    compileOnly("io.micrometer:micrometer-core")
}
```

#### Testing
- Test metrics are recorded on success
- Test metrics are recorded on error
- Test error type tags
- Test health indicator status
- Test metrics export to Prometheus

---

## Phase 4: Quality & Testing

### 8. Comprehensive Test Suite

**Goal:** Ensure all features are thoroughly tested with high coverage.

#### Test Module Structure

```
hkj-spring/
├── autoconfigure/src/test/java/
│   └── org/higherkindedj/spring/
│       ├── autoconfigure/
│       │   ├── HkjAutoConfigurationTest.java
│       │   ├── HkjWebMvcAutoConfigurationTest.java
│       │   └── HkjJacksonAutoConfigurationTest.java
│       ├── web/returnvalue/
│       │   ├── EitherReturnValueHandlerTest.java
│       │   ├── ValidatedReturnValueHandlerTest.java
│       │   └── EitherTReturnValueHandlerTest.java
│       └── json/
│           ├── EitherSerializerTest.java
│           └── ValidatedSerializerTest.java
│
└── example/src/test/java/
    └── org/higherkindedj/spring/example/
        ├── controller/
        │   ├── UserControllerTest.java
        │   ├── ValidationControllerTest.java
        │   └── AsyncControllerTest.java
        └── integration/
            ├── EndToEndIntegrationTest.java
            ├── AsyncIntegrationTest.java
            └── SecurityIntegrationTest.java
```

#### Test Categories

**1. Unit Tests**
```java
@ExtendWith(MockitoExtension.class)
class EitherReturnValueHandlerTest {

    @Mock
    private HttpServletResponse response;

    @Mock
    private NativeWebRequest webRequest;

    private EitherReturnValueHandler handler;

    @BeforeEach
    void setUp() {
        handler = new EitherReturnValueHandler();
        when(webRequest.getNativeResponse(HttpServletResponse.class))
            .thenReturn(response);
    }

    @Test
    void shouldHandleRightValue() throws Exception {
        Either<String, User> either = Either.right(
            new User("1", "test@example.com", "Test", "User")
        );

        handler.handleReturnValue(either, null, null, webRequest);

        verify(response).setStatus(HttpStatus.OK.value());
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    void shouldHandleLeftValueWithNotFoundError() throws Exception {
        Either<DomainError, User> either = Either.left(
            new UserNotFoundError("123")
        );

        handler.handleReturnValue(either, null, null, webRequest);

        verify(response).setStatus(HttpStatus.NOT_FOUND.value());
    }
}
```

**2. Integration Tests**
```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturn200ForExistingUser() throws Exception {
        mockMvc.perform(get("/api/users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("1"))
            .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void shouldReturn404ForMissingUser() throws Exception {
        mockMvc.perform(get("/api/users/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.message").value("User not found: 999"));
    }

    @Test
    void shouldReturnAllValidationErrors() throws Exception {
        String invalidRequest = """
            {
                "email": "invalid",
                "firstName": "",
                "lastName": ""
            }
            """;

        mockMvc.perform(post("/api/validation/user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.errors.length()").value(3));
    }
}
```

**3. Auto-Configuration Tests**
```java
@SpringBootTest
@TestPropertySource(properties = {
    "hkj.web.either-response-enabled=false",
    "hkj.web.validated-response-enabled=true"
})
class HkjAutoConfigurationPropertyTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldRespectConfigurationProperties() {
        // Verify that configuration can be disabled
        assertThat(context.getBeansOfType(EitherReturnValueHandler.class))
            .isEmpty();

        assertThat(context.getBeansOfType(ValidatedReturnValueHandler.class))
            .isNotEmpty();
    }
}
```

**4. Async Tests**
```java
@SpringBootTest
@AutoConfigureMockMvc
class AsyncControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldHandleAsyncEitherT() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/async/users/1"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("1"));
    }
}
```

**5. Performance Tests**
```java
@SpringBootTest
class EitherPerformanceTest {

    @Test
    void shouldHandleHighThroughput() {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<ResponseEntity<?>>> futures = new ArrayList<>();

        for (int i = 0; i < 1000; i++) {
            futures.add(executor.submit(() ->
                restTemplate.getForEntity("/api/users/1", String.class)
            ));
        }

        // Verify all requests complete successfully
        futures.forEach(future -> {
            assertThat(future.get().getStatusCode()).isEqualTo(HttpStatus.OK);
        });
    }
}
```

#### Coverage Goals
- Overall: > 90%
- Core handlers: > 95%
- Auto-configuration: > 85%
- Example controllers: > 80%

#### Testing Tools
```kotlin
// autoconfigure/build.gradle.kts
dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
```

---

## Implementation Priority

### Recommended Order

1. **Validated Return Value Handler** (Week 1)
   - High value, relatively simple
   - Complements existing Either handler
   - Example: Form validation with error accumulation

2. **Jackson Custom Serializers** (Week 2)
   - Needed for clean JSON output
   - Improves API usability
   - Small, focused scope

3. **Configuration Properties** (Week 3)
   - Enables customization
   - Required for production use
   - Foundation for other features

4. **Comprehensive Test Suite** (Week 4)
   - Ensures quality
   - Prevents regressions
   - Builds confidence

5. **EitherT Async Support** (Week 5-6)
   - More complex, requires async expertise
   - High value for async workflows
   - Example: Microservice orchestration

6. **Actuator Metrics** (Week 7)
   - Production monitoring
   - Performance insights
   - Relatively simple addition

7. **Spring Security Integration** (Week 8-9)
   - New module, larger scope
   - Requires security expertise
   - High value for secured applications

8. **WebFlux Support** (Week 10-12)
   - New module, largest scope
   - Reactive expertise required
   - Targets reactive applications

---

## Success Criteria

### Phase 2 Complete When:
- ✅ Validated handler working with examples
- ✅ EitherT async support functional
- ✅ Jackson serializers producing clean JSON
- ✅ Configuration properties documented
- ✅ Test coverage > 90%

### Phase 3 Complete When:
- ✅ Spring Security integration with examples
- ✅ WebFlux support with reactive examples
- ✅ Actuator metrics exposed and documented
- ✅ All modules tested and documented

### Production Ready When:
- ✅ All features implemented
- ✅ All tests passing
- ✅ Documentation complete
- ✅ Performance benchmarks acceptable
- ✅ Example applications comprehensive
- ✅ Community feedback incorporated

---

## Documentation Updates Needed

For each feature, update:
1. **hkj-spring/README.md** - Feature overview
2. **hkj-spring/autoconfigure/README.md** - Technical details
3. **hkj-spring/example/README.md** - Usage examples
4. **Javadoc** - All public APIs
5. **Integration guide** - Migration from traditional Spring

---

## Community Engagement

- Blog post for each major feature
- Stack Overflow answers demonstrating usage
- Conference talk proposal
- GitHub Discussions for feedback
- Regular releases with changelogs
