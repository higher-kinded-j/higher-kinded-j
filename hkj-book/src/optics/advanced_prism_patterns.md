# Advanced Prism Patterns

## _Real-World Applications of Prisms in Production Systems_

![prism-advanced.jpeg](../images/prism.jpeg)

~~~admonish info title="What You'll Learn"
- Configuration management with layered prism composition
- API response handling with type-safe error recovery
- Data validation pipelines using prisms for conditional processing
- Event processing systems with prism-based routing
- State machine implementations using prisms for transitions
- Plugin architectures with type-safe variant handling
- Performance optimisation patterns for production systems
- Testing strategies for prism-heavy codebases
~~~

~~~admonish title="Hands On Practice"
[Tutorial10_AdvancedPrismPatterns.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial10_AdvancedPrismPatterns.java)
~~~

~~~admonish title="Example Code"
[ConfigurationManagementExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/ConfigurationManagementExample.java)
[ApiResponseHandlingExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/ApiResponseHandlingExample.java)
[DataValidationPipelineExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/DataValidationPipelineExample.java)
[EventProcessingExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/EventProcessingExample.java)
[StateMachineExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/StateMachineExample.java)
[PluginSystemExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/PluginSystemExample.java)
~~~

This guide explores sophisticated prism patterns encountered in production Java applications. We'll move beyond basic type matching to examine how prisms enable elegant solutions to complex architectural problems.

~~~admonish note title="Prerequisites"
This guide assumes familiarity with prism fundamentals including `getOptional()`, `build()`, convenience methods (`matches()`, `modify()`, `modifyWhen()`, etc.), and the `Prisms` utility class. If you're new to prisms, start with [Prisms: A Practical Guide](prisms.md) which covers:
- Core prism operations and type-safe variant handling
- The 7 convenience methods for streamlined operations
- The `Prisms` utility class for common patterns
- Composition with lenses and traversals
~~~

---

## Pattern 1: Configuration Management

### _Type-Safe, Layered Configuration Resolution_

Configuration systems often deal with multiple sources (environment variables, files, defaults) and various data types. Prisms provide a type-safe way to navigate this complexity.

### The Challenge

```java
// Traditional approach: brittle and verbose
Object rawValue = config.get("database.connection.pool.size");
if (rawValue instanceof Integer i) {
    return i > 0 ? i : DEFAULT_POOL_SIZE;
} else if (rawValue instanceof String s) {
    try {
        int parsed = Integer.parseInt(s);
        return parsed > 0 ? parsed : DEFAULT_POOL_SIZE;
    } catch (NumberFormatException e) {
        return DEFAULT_POOL_SIZE;
    }
}
return DEFAULT_POOL_SIZE;
```

### The Prism Solution

```java
@GeneratePrisms
sealed interface ConfigValue permits StringValue, IntValue, BoolValue, NestedConfig {}

record StringValue(String value) implements ConfigValue {}
record IntValue(int value) implements ConfigValue {}
record BoolValue(boolean value) implements ConfigValue {}
record NestedConfig(Map<String, ConfigValue> values) implements ConfigValue {}

public class ConfigResolver {
    private static final Prism<ConfigValue, IntValue> INT =
        ConfigValuePrisms.intValue();
    private static final Prism<ConfigValue, StringValue> STRING =
        ConfigValuePrisms.stringValue();

    public static int getPoolSize(ConfigValue value) {
        // Try integer first, fall back to parsing string
        return INT.mapOptional(IntValue::value, value)
            .filter(i -> i > 0)
            .or(() -> STRING.mapOptional(StringValue::value, value)
                .flatMap(ConfigResolver::safeParseInt)
                .filter(i -> i > 0))
            .orElse(DEFAULT_POOL_SIZE);
    }

    private static Optional<Integer> safeParseInt(String s) {
        try {
            return Optional.of(Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
```

### Nested Configuration Access

```java
// Build a type-safe path through nested configuration
Prism<ConfigValue, NestedConfig> nested = ConfigValuePrisms.nestedConfig();
Lens<NestedConfig, Map<String, ConfigValue>> values = NestedConfigLenses.values();

Traversal<ConfigValue, ConfigValue> databaseConfig =
    nested.asTraversal()
        .andThen(values.asTraversal())
        .andThen(Traversals.forMap("database"))
        .andThen(nested.asTraversal())
        .andThen(values.asTraversal())
        .andThen(Traversals.forMap("connection"));

// Extract with fallback
ConfigValue rootConfig = loadConfiguration(); // Top-level configuration object
Optional<ConfigValue> connConfig = Traversals.getAll(databaseConfig, rootConfig)
    .stream().findFirst();
```

~~~admonish tip title="Configuration Best Practices"
- **Cache composed prisms**: Configuration paths don't change at runtime
- **Use `orElse()` chains**: Handle type coercion gracefully
- **Validate at load time**: Use `modifyWhen()` to enforce constraints
- **Provide clear defaults**: Always have fallback values
~~~

---

## Pattern 2: API Response Handling

### _Type-Safe HTTP Response Processing_

Modern APIs return varying response types based on status codes. Prisms provide elegant error handling and recovery strategies.

### The Challenge

```java
// Traditional approach: error-prone branching
if (response.status() == 200) {
    return processSuccess((SuccessResponse) response);
} else if (response.status() == 400) {
    ValidationError err = (ValidationError) response;
    return handleValidation(err);
} else if (response.status() == 500) {
    return handleServerError((ServerError) response);
} else if (response.status() == 429) {
    return retryWithBackoff((RateLimitError) response);
}
// What about 401, 403, 404, ...?
```

### The Prism Solution

```java
@GeneratePrisms
@GenerateLenses
sealed interface ApiResponse permits Success, ValidationError, ServerError,
                                     RateLimitError, AuthError, NotFoundError {}

record Success(JsonValue data, int statusCode) implements ApiResponse {}
record ValidationError(List<String> errors, String field) implements ApiResponse {}
record ServerError(String message, String traceId) implements ApiResponse {}
record RateLimitError(long retryAfterMs) implements ApiResponse {}
record AuthError(String realm) implements ApiResponse {}
record NotFoundError(String resource) implements ApiResponse {}

public class ApiHandler {
    // Reusable prisms for each response type
    private static final Prism<ApiResponse, Success> SUCCESS =
        ApiResponsePrisms.success();
    private static final Prism<ApiResponse, ValidationError> VALIDATION =
        ApiResponsePrisms.validationError();
    private static final Prism<ApiResponse, RateLimitError> RATE_LIMIT =
        ApiResponsePrisms.rateLimitError();
    private static final Prism<ApiResponse, ServerError> SERVER_ERROR =
        ApiResponsePrisms.serverError();

    public Either<String, JsonValue> handleResponse(ApiResponse response) {
        // Try success first
        return SUCCESS.mapOptional(Success::data, response)
            .map(Either::<String, JsonValue>right)
            // Then validation errors
            .or(() -> VALIDATION.mapOptional(
                err -> Either.<String, JsonValue>left(
                    "Validation failed: " + String.join(", ", err.errors())
                ),
                response
            ))
            // Then server errors
            .or(() -> SERVER_ERROR.mapOptional(
                err -> Either.<String, JsonValue>left(
                    "Server error: " + err.message() + " [" + err.traceId() + "]"
                ),
                response
            ))
            .orElse(Either.left("Unknown error type"));
    }

    public boolean isRetryable(ApiResponse response) {
        return RATE_LIMIT.matches(response) || SERVER_ERROR.matches(response);
    }

    public Optional<Long> getRetryDelay(ApiResponse response) {
        return RATE_LIMIT.mapOptional(RateLimitError::retryAfterMs, response);
    }
}
```

### Advanced: Response Pipeline with Fallbacks

```java
public class ResilientApiClient {
    public CompletableFuture<JsonValue> fetchWithFallbacks(String endpoint) {
        return primaryApi.call(endpoint)
            .thenCompose(response ->
                SUCCESS.mapOptional(Success::data, response)
                    .map(CompletableFuture::completedFuture)
                    .or(() -> RATE_LIMIT.mapOptional(
                        err -> CompletableFuture.supplyAsync(
                            () -> callSecondaryApi(endpoint),
                            delayedExecutor(err.retryAfterMs(), TimeUnit.MILLISECONDS)
                        ),
                        response
                    ))
                    .orElseGet(() -> CompletableFuture.failedFuture(
                        new ApiException("Unrecoverable error")
                    ))
            );
    }
}
```

~~~admonish warning title="Production Considerations"
When using prisms for API handling:
- **Log unmatched cases**: Track responses that don't match any prism
- **Metrics**: Count matches per prism type for monitoring
- **Circuit breakers**: Integrate retry logic with circuit breaker patterns
- **Structured logging**: Use `mapOptional()` to extract error details
~~~

---

## Pattern 3: Data Validation Pipelines

### _Composable, Type-Safe Validation Logic_

Validation often requires checking different data types and applying conditional rules. Prisms make validation logic declarative and reusable.

### The Challenge

ETL pipelines process heterogeneous data where validation rules depend on data types:

```java
// Traditional approach: imperative branching
List<ValidationError> errors = new ArrayList<>();
for (Object value : row.values()) {
    if (value instanceof String s) {
        if (s.length() > MAX_STRING_LENGTH) {
            errors.add(new ValidationError("String too long: " + s));
        }
    } else if (value instanceof Integer i) {
        if (i < 0) {
            errors.add(new ValidationError("Negative integer: " + i));
        }
    }
    // ... more type checks
}
```

### The Prism Solution

```java
@GeneratePrisms
sealed interface DataValue permits StringData, IntData, DoubleData, NullData {}

record StringData(String value) implements DataValue {}
record IntData(int value) implements DataValue {}
record DoubleData(double value) implements DataValue {}
record NullData() implements DataValue {}

public class ValidationPipeline {
    // Validation rules as prism transformations
    private static final Prism<DataValue, StringData> STRING =
        DataValuePrisms.stringData();
    private static final Prism<DataValue, IntData> INT =
        DataValuePrisms.intData();

    public static List<String> validate(List<DataValue> row) {
        return row.stream()
            .flatMap(value -> Stream.concat(
                // Validate strings
                STRING.mapOptional(
                    s -> s.value().length() > MAX_STRING_LENGTH
                        ? Optional.of("String too long: " + s.value())
                        : Optional.empty(),
                    value
                ).stream(),
                // Validate integers
                INT.mapOptional(
                    i -> i.value() < 0
                        ? Optional.of("Negative integer: " + i.value())
                        : Optional.empty(),
                    value
                ).stream()
            ))
            .collect(Collectors.toList());
    }

    // Sanitise data by modifying only invalid values
    public static List<DataValue> sanitise(List<DataValue> row) {
        return row.stream()
            .map(value ->
                // Truncate long strings
                STRING.modifyWhen(
                    s -> s.value().length() > MAX_STRING_LENGTH,
                    s -> new StringData(s.value().substring(0, MAX_STRING_LENGTH)),
                    value
                )
            )
            .map(value ->
                // Clamp negative integers to zero
                INT.modifyWhen(
                    i -> i.value() < 0,
                    i -> new IntData(0),
                    value
                )
            )
            .collect(Collectors.toList());
    }
}
```

### Advanced: Validation with Accumulation

Using `Either` and prisms for validation that accumulates errors:

```java
public class AccumulatingValidator {
    public static Either<List<String>, List<DataValue>> validateAll(List<DataValue> row) {
        List<String> errors = new ArrayList<>();
        List<DataValue> sanitised = new ArrayList<>();

        for (DataValue value : row) {
            // Validate and potentially sanitise each value
            DataValue processed = value;

            // Check strings
            processed = STRING.modifyWhen(
                s -> s.value().length() > MAX_STRING_LENGTH,
                s -> {
                    errors.add("Truncated: " + s.value());
                    return new StringData(s.value().substring(0, MAX_STRING_LENGTH));
                },
                processed
            );

            // Check integers
            processed = INT.modifyWhen(
                i -> i.value() < 0,
                i -> {
                    errors.add("Clamped negative: " + i.value());
                    return new IntData(0);
                },
                processed
            );

            sanitised.add(processed);
        }

        return errors.isEmpty()
            ? Either.right(sanitised)
            : Either.left(errors);
    }
}
```

~~~admonish tip title="Validation Pipeline Best Practices"
- **Compose validators**: Build complex validation from simple prism rules
- **Use `modifyWhen()` for sanitisation**: Fix values whilst tracking changes
- **Accumulate errors**: Don't fail-fast; collect all validation issues
- **Type-specific rules**: Let prisms dispatch to appropriate validators
~~~

---

## Pattern 4: Event Processing

### _Type-Safe Event Routing and Handling_

Event-driven systems receive heterogeneous event types that require different processing logic. Prisms provide type-safe routing without `instanceof` cascades.

### The Challenge

```java
// Traditional approach: brittle event dispatching
public void handleEvent(Event event) {
    if (event instanceof UserCreated uc) {
        sendWelcomeEmail(uc.userId(), uc.email());
        provisionResources(uc.userId());
    } else if (event instanceof UserDeleted ud) {
        cleanupResources(ud.userId());
        archiveData(ud.userId());
    } else if (event instanceof OrderPlaced op) {
        processPayment(op.orderId());
        updateInventory(op.items());
    }
    // Grows with each new event type
}
```

### The Prism Solution

```java
@GeneratePrisms
@GenerateLenses
sealed interface DomainEvent permits UserCreated, UserDeleted, UserUpdated,
                                     OrderPlaced, OrderCancelled, PaymentProcessed {}

record UserCreated(String userId, String email, Instant timestamp) implements DomainEvent {}
record UserDeleted(String userId, Instant timestamp) implements DomainEvent {}
record UserUpdated(String userId, Map<String, String> changes, Instant timestamp) implements DomainEvent {}
record OrderPlaced(String orderId, List<LineItem> items, Instant timestamp) implements DomainEvent {}
record OrderCancelled(String orderId, String reason, Instant timestamp) implements DomainEvent {}
record PaymentProcessed(String orderId, double amount, Instant timestamp) implements DomainEvent {}

public class EventRouter {
    private static final Prism<DomainEvent, UserCreated> USER_CREATED =
        DomainEventPrisms.userCreated();
    private static final Prism<DomainEvent, UserDeleted> USER_DELETED =
        DomainEventPrisms.userDeleted();
    private static final Prism<DomainEvent, OrderPlaced> ORDER_PLACED =
        DomainEventPrisms.orderPlaced();

    // Declarative event handler registry
    private final Map<Prism<DomainEvent, ?>, Consumer<DomainEvent>> handlers = Map.of(
        USER_CREATED, event -> USER_CREATED.mapOptional(
            uc -> {
                sendWelcomeEmail(uc.userId(), uc.email());
                provisionResources(uc.userId());
                return uc;
            },
            event
        ),
        USER_DELETED, event -> USER_DELETED.mapOptional(
            ud -> {
                cleanupResources(ud.userId());
                archiveData(ud.userId());
                return ud;
            },
            event
        ),
        ORDER_PLACED, event -> ORDER_PLACED.mapOptional(
            op -> {
                processPayment(op.orderId());
                updateInventory(op.items());
                return op;
            },
            event
        )
    );

    public void route(DomainEvent event) {
        handlers.entrySet().stream()
            .filter(entry -> entry.getKey().matches(event))
            .findFirst()
            .ifPresentOrElse(
                entry -> entry.getValue().accept(event),
                () -> log.warn("Unhandled event type: {}", event.getClass())
            );
    }
}
```

### Advanced: Event Filtering and Transformation

```java
public class EventProcessor {
    // Process only recent user events
    public List<DomainEvent> getRecentUserEvents(
        List<DomainEvent> events,
        Instant since
    ) {
        Prism<DomainEvent, UserCreated> userCreated = USER_CREATED;
        Prism<DomainEvent, UserDeleted> userDeleted = USER_DELETED;

        return events.stream()
            .filter(e ->
                // Match user events with timestamp filter
                userCreated.mapOptional(
                    uc -> uc.timestamp().isAfter(since) ? uc : null,
                    e
                ).isPresent()
                ||
                userDeleted.mapOptional(
                    ud -> ud.timestamp().isAfter(since) ? ud : null,
                    e
                ).isPresent()
            )
            .collect(Collectors.toList());
    }

    // Transform events for audit log
    public List<AuditEntry> toAuditLog(List<DomainEvent> events) {
        return events.stream()
            .flatMap(event ->
                // Extract audit entries from different event types
                USER_CREATED.mapOptional(
                    uc -> new AuditEntry("USER_CREATED", uc.userId(), uc.timestamp()),
                    event
                ).or(() ->
                    ORDER_PLACED.mapOptional(
                        op -> new AuditEntry("ORDER_PLACED", op.orderId(), op.timestamp()),
                        event
                    )
                ).stream()
            )
            .collect(Collectors.toList());
    }
}
```

~~~admonish tip title="Event Processing Best Practices"
- **Registry pattern**: Map prisms to handlers for extensibility
- **Metrics**: Track event types processed using `matches()`
- **Dead letter queue**: Log events that match no prism
- **Event sourcing**: Use prisms to replay specific event types
~~~

---

## Pattern 5: State Machines

### _Type-Safe State Transitions_

State machines with complex transition rules benefit from prisms' ability to safely match states and transform between them.

### The Challenge

```java
// Traditional approach: verbose state management
public Order transition(Order order, OrderEvent event) {
    if (order.state() instanceof Pending && event instanceof PaymentReceived) {
        return order.withState(new Processing(((PaymentReceived) event).transactionId()));
    } else if (order.state() instanceof Processing && event instanceof ShippingCompleted) {
        return order.withState(new Shipped(((ShippingCompleted) event).trackingNumber()));
    }
    // Many more transitions...
    throw new IllegalStateException("Invalid transition");
}
```

### The Prism Solution

```java
@GeneratePrisms
sealed interface OrderState permits Pending, Processing, Shipped, Delivered, Cancelled {}

record Pending(Instant createdAt) implements OrderState {}
record Processing(String transactionId, Instant startedAt) implements OrderState {}
record Shipped(String trackingNumber, Instant shippedAt) implements OrderState {}
record Delivered(Instant deliveredAt) implements OrderState {}
record Cancelled(String reason, Instant cancelledAt) implements OrderState {}

@GeneratePrisms
sealed interface OrderEvent permits PaymentReceived, ShippingCompleted,
                                    DeliveryConfirmed, CancellationRequested {}

record PaymentReceived(String transactionId) implements OrderEvent {}
record ShippingCompleted(String trackingNumber) implements OrderEvent {}
record DeliveryConfirmed() implements OrderEvent {}
record CancellationRequested(String reason) implements OrderEvent {}

public class OrderStateMachine {
    private static final Prism<OrderState, Pending> PENDING =
        OrderStatePrisms.pending();
    private static final Prism<OrderState, Processing> PROCESSING =
        OrderStatePrisms.processing();
    private static final Prism<OrderState, Shipped> SHIPPED =
        OrderStatePrisms.shipped();

    private static final Prism<OrderEvent, PaymentReceived> PAYMENT =
        OrderEventPrisms.paymentReceived();
    private static final Prism<OrderEvent, ShippingCompleted> SHIPPING =
        OrderEventPrisms.shippingCompleted();
    private static final Prism<OrderEvent, DeliveryConfirmed> DELIVERY =
        OrderEventPrisms.deliveryConfirmed();

    // Define valid transitions as prism combinations
    public Optional<OrderState> transition(OrderState currentState, OrderEvent event) {
        // Pending -> Processing (on payment)
        if (PENDING.matches(currentState) && PAYMENT.matches(event)) {
            return PAYMENT.mapOptional(
                payment -> new Processing(payment.transactionId(), Instant.now()),
                event
            );
        }

        // Processing -> Shipped (on shipping)
        if (PROCESSING.matches(currentState) && SHIPPING.matches(event)) {
            return SHIPPING.mapOptional(
                shipping -> new Shipped(shipping.trackingNumber(), Instant.now()),
                event
            );
        }

        // Shipped -> Delivered (on confirmation)
        if (SHIPPED.matches(currentState) && DELIVERY.matches(event)) {
            return Optional.of(new Delivered(Instant.now()));
        }

        return Optional.empty(); // Invalid transition
    }

    // Guard conditions using prisms
    public boolean canCancel(OrderState state) {
        // Can cancel if Pending or Processing
        return PENDING.matches(state) || PROCESSING.matches(state);
    }

    // Extract state-specific data
    public Optional<String> getTrackingNumber(OrderState state) {
        return SHIPPED.mapOptional(Shipped::trackingNumber, state);
    }
}
```

### Advanced: Transition Table

```java
import org.higherkindedj.optics.util.Pair; // Pair utility from hkj-optics

public class AdvancedStateMachine {
    // Define transitions as a declarative table
    private static final Map<
        Pair<Prism<OrderState, ?>, Prism<OrderEvent, ?>>,
        BiFunction<OrderState, OrderEvent, OrderState>
    > TRANSITIONS = Map.of(
        Pair.of(PENDING, PAYMENT),
        (state, event) -> PAYMENT.mapOptional(
            p -> new Processing(p.transactionId(), Instant.now()),
            event
        ).orElse(state),

        Pair.of(PROCESSING, SHIPPING),
        (state, event) -> SHIPPING.mapOptional(
            s -> new Shipped(s.trackingNumber(), Instant.now()),
            event
        ).orElse(state)
    );

    public OrderState process(OrderState state, OrderEvent event) {
        return TRANSITIONS.entrySet().stream()
            .filter(entry ->
                entry.getKey().first().matches(state) &&
                entry.getKey().second().matches(event)
            )
            .findFirst()
            .map(entry -> entry.getValue().apply(state, event))
            .orElseThrow(() -> new IllegalStateException(
                "Invalid transition: " + state + " -> " + event
            ));
    }
}
```

~~~admonish tip title="State Machine Best Practices"
- **Exhaustive matching**: Ensure all valid transitions are covered
- **Guard conditions**: Use `matches()` for pre-condition checks
- **Immutability**: States are immutable; transitions create new instances
- **Audit trail**: Log state transitions using prism metadata
~~~

---

## Pattern 6: Plugin Systems

### _Type-Safe Plugin Discovery and Execution_

Plugin architectures require dynamic dispatch to various plugin types whilst maintaining type safety.

### The Challenge

```java
// Traditional approach: reflection and casting
public void executePlugin(Plugin plugin, Object context) {
    if (plugin.getClass().getName().equals("DatabasePlugin")) {
        ((DatabasePlugin) plugin).execute((DatabaseContext) context);
    } else if (plugin.getClass().getName().equals("FileSystemPlugin")) {
        ((FileSystemPlugin) plugin).execute((FileSystemContext) context);
    }
    // Fragile and unsafe
}
```

### The Prism Solution

```java
@GeneratePrisms
sealed interface Plugin permits DatabasePlugin, FileSystemPlugin,
                                 NetworkPlugin, ComputePlugin {}

record DatabasePlugin(String query, DatabaseConfig config) implements Plugin {
    public Result execute(DatabaseContext ctx) {
        return ctx.executeQuery(query, config);
    }
}

record FileSystemPlugin(Path path, FileOperation operation) implements Plugin {
    public Result execute(FileSystemContext ctx) {
        return ctx.performOperation(path, operation);
    }
}

record NetworkPlugin(URL endpoint, HttpMethod method) implements Plugin {
    public Result execute(NetworkContext ctx) {
        return ctx.makeRequest(endpoint, method);
    }
}

record ComputePlugin(String script, Runtime runtime) implements Plugin {
    public Result execute(ComputeContext ctx) {
        return ctx.runScript(script, runtime);
    }
}

public class PluginExecutor {
    private static final Prism<Plugin, DatabasePlugin> DB =
        PluginPrisms.databasePlugin();
    private static final Prism<Plugin, FileSystemPlugin> FS =
        PluginPrisms.fileSystemPlugin();
    private static final Prism<Plugin, NetworkPlugin> NET =
        PluginPrisms.networkPlugin();
    private static final Prism<Plugin, ComputePlugin> COMPUTE =
        PluginPrisms.computePlugin();

    public Either<String, Result> execute(
        Plugin plugin,
        ExecutionContext context
    ) {
        // Type-safe dispatch to appropriate handler
        return DB.mapOptional(
            dbPlugin -> context.getDatabaseContext()
                .map(dbPlugin::execute)
                .map(Either::<String, Result>right)
                .orElse(Either.left("Database context not available")),
            plugin
        ).or(() ->
            FS.mapOptional(
                fsPlugin -> context.getFileSystemContext()
                    .map(fsPlugin::execute)
                    .map(Either::<String, Result>right)
                    .orElse(Either.left("FileSystem context not available")),
                plugin
            )
        ).or(() ->
            NET.mapOptional(
                netPlugin -> context.getNetworkContext()
                    .map(netPlugin::execute)
                    .map(Either::<String, Result>right)
                    .orElse(Either.left("Network context not available")),
                plugin
            )
        ).or(() ->
            COMPUTE.mapOptional(
                computePlugin -> context.getComputeContext()
                    .map(computePlugin::execute)
                    .map(Either::<String, Result>right)
                    .orElse(Either.left("Compute context not available")),
                plugin
            )
        ).orElse(Either.left("Unknown plugin type"));
    }

    // Validate plugin before execution
    public List<String> validate(Plugin plugin) {
        List<String> errors = new ArrayList<>();

        DB.mapOptional(p -> {
            if (p.query().isEmpty()) {
                errors.add("Database query cannot be empty");
            }
            return p;
        }, plugin);

        FS.mapOptional(p -> {
            if (!Files.exists(p.path())) {
                errors.add("File path does not exist: " + p.path());
            }
            return p;
        }, plugin);

        return errors;
    }
}
```

### Advanced: Plugin Composition

```java
public class CompositePlugin {
    // Combine multiple plugins into a pipeline
    public static Plugin pipeline(List<Plugin> plugins) {
        return new CompositePluginImpl(plugins);
    }

    // Filter plugins by type for batch operations
    public static List<DatabasePlugin> getAllDatabasePlugins(List<Plugin> plugins) {
        Prism<Plugin, DatabasePlugin> dbPrism = DB;
        return plugins.stream()
            .flatMap(p -> dbPrism.getOptional(p).stream())
            .collect(Collectors.toList());
    }

    // Transform plugins based on environment
    public static List<Plugin> adaptForEnvironment(
        List<Plugin> plugins,
        Environment env
    ) {
        return plugins.stream()
            .map(plugin ->
                // Modify database plugins for different environments
                DB.modifyWhen(
                    db -> env == Environment.PRODUCTION,
                    db -> new DatabasePlugin(
                        db.query(),
                        db.config().withReadReplica()
                    ),
                    plugin
                )
            )
            .collect(Collectors.toList());
    }
}
```

~~~admonish tip title="Plugin Architecture Best Practices"
- **Capability detection**: Use `matches()` to check plugin capabilities
- **Fail-safe execution**: Always handle unmatched plugin types
- **Plugin validation**: Use prisms to validate configuration before execution
- **Metrics**: Track plugin execution by type using prism-based routing
~~~

---

## Performance Optimisation Patterns

### Caching Composed Prisms

```java
public class OptimisedPrismCache {
    // Cache expensive optic compositions
    private static final Map<String, Object> OPTIC_CACHE =
        new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> T getCached(
        String key,
        Supplier<T> factory
    ) {
        return (T) OPTIC_CACHE.computeIfAbsent(key, k -> factory.get());
    }

    // Example usage: caching a composed traversal
    private static final Traversal<Config, String> DATABASE_HOST =
        getCached("config.database.host", () ->
            ConfigLenses.database()
                .asTraversal()
                .andThen(Prisms.some().asTraversal())
                .andThen(Prisms.right().asTraversal())
                .andThen(DatabaseSettingsLenses.host().asTraversal())
        );
}
```

### Bulk Operations with Prisms

```java
public class BulkProcessor {
    // Process multiple items efficiently
    public static <S, A> List<A> extractAll(
        Prism<S, A> prism,
        List<S> items
    ) {
        return items.stream()
            .flatMap(item -> prism.getOptional(item).stream())
            .collect(Collectors.toList());
    }

    // Partition items by prism match
    public static <S, A> Map<Boolean, List<S>> partitionByMatch(
        Prism<S, A> prism,
        List<S> items
    ) {
        return items.stream()
            .collect(Collectors.partitioningBy(prism::matches));
    }
}
```

---

## Testing Strategies

### Testing Prism-Based Logic

```java
public class PrismTestPatterns {
    @Test
    void testPrismMatching() {
        Prism<ApiResponse, Success> success = ApiResponsePrisms.success();

        ApiResponse successResponse = new Success(jsonData, 200);
        ApiResponse errorResponse = new ServerError("Error", "trace123");

        // Verify matching behaviour
        assertTrue(success.matches(successResponse));
        assertFalse(success.matches(errorResponse));

        // Verify extraction
        assertThat(success.getOptional(successResponse))
            .isPresent()
            .get()
            .extracting(Success::statusCode)
            .isEqualTo(200);
    }

    @Test
    void testComposedPrisms() {
        // Test deep prism compositions
        Prism<Config, String> hostPrism = buildHostPrism();

        Config validConfig = createValidConfig();
        Config invalidConfig = createInvalidConfig();

        assertThat(hostPrism.getOptional(validConfig)).isPresent();
        assertThat(hostPrism.getOptional(invalidConfig)).isEmpty();
    }

    @Test
    void testConditionalOperations() {
        Prism<ConfigValue, IntValue> intPrism = ConfigValuePrisms.intValue();

        ConfigValue value = new IntValue(42);

        // Test modifyWhen
        ConfigValue result = intPrism.modifyWhen(
            i -> i.value() > 0,
            i -> new IntValue(i.value() * 2),
            value
        );

        assertThat(intPrism.getOptional(result))
            .isPresent()
            .get()
            .extracting(IntValue::value)
            .isEqualTo(84);
    }
}
```

---

~~~admonish tip title="Further Reading"
- **Monocle**: [Scala Optics Library](https://www.optics.dev/Monocle/) - Production-ready Scala optics with extensive patterns
- **Haskell Lens**: [Canonical Reference](https://hackage.haskell.org/package/lens) - The original comprehensive optics library
- **Lens Tutorial**: [A Little Lens Starter Tutorial](https://www.schoolofhaskell.com/school/to-infinity-and-beyond/pick-of-the-week/a-little-lens-starter-tutorial) - Beginner-friendly introduction
~~~

~~~admonish info title="Hands-On Learning"
Practice advanced prism patterns in [Tutorial 10: Advanced Prism Patterns](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial10_AdvancedPrismPatterns.java) (8 exercises, ~12 minutes).
~~~

---

**Previous:** [Indexed Access](indexed_access.md)
**Next:** [Profunctor Optics](profunctor_optics.md)
