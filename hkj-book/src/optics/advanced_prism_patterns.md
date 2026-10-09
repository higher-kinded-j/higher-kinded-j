# Advanced Prism Patterns

_Route configuration values, API responses, events, states and plugins by case, with prisms you compose once._

![prism.jpeg](../images/prism.jpeg)

~~~admonish info title="What You'll Learn"
- Resolve a configuration value through a chain of prism fallbacks with `mapOptional` and `Optional.or`
- Route API responses, domain events and plugins to their handlers by case, with no `instanceof` cascade
- Sanitise and validate mixed values with `modifyWhen`, collecting every error into an `Either`
- Guard a state transition by matching both the current state and the event with `matches`
- Match a category of values with `Prisms.nearly`, and name the negative case with `doesNotMatch`
~~~

~~~admonish example title="See Example Code"
- [ConfigurationManagementExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/ConfigurationManagementExample.java)
- [ApiResponseHandlingExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/ApiResponseHandlingExample.java)
- [DataValidationPipelineExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/DataValidationPipelineExample.java)
- [EventProcessingExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/EventProcessingExample.java)
- [StateMachineExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/StateMachineExample.java)
- [PluginSystemExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/PluginSystemExample.java)
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

Configuration systems often deal with multiple sources (environment variables, files, defaults) and various data types. Prisms provide a type-safe way to navigate this complexity.

### The Challenge

<!-- verify -->
```java
// Traditional approach: brittle and verbose
int poolSize(Map<String, Object> config) {
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
}
```

### The Prism Solution

<!-- verify -->
```java
@GeneratePrisms
sealed interface ConfigValue permits StringValue, IntValue, BoolValue, NestedConfig {}

record StringValue(String value) implements ConfigValue {}
record IntValue(int value) implements ConfigValue {}
record BoolValue(boolean value) implements ConfigValue {}
@GenerateLenses
record NestedConfig(Map<String, ConfigValue> values) implements ConfigValue {}

public class ConfigResolver {
    private static final int DEFAULT_POOL_SIZE = 10;

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

<!-- verify -->
```java
// Build a type-safe path through nested configuration
Prism<ConfigValue, NestedConfig> nested = ConfigValuePrisms.nestedConfig();
Lens<NestedConfig, Map<String, ConfigValue>> values = NestedConfigLenses.values();

Traversal<ConfigValue, ConfigValue> databaseConfig =
    nested
        .andThen(values)
        .andThen(Traversals.forMap("database"))
        .andThen(nested)
        .andThen(values)
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

Modern APIs return varying response types based on status codes. Prisms provide elegant error handling and recovery strategies.

### The Challenge

<!-- verify -->
```java
// Traditional approach: error-prone branching
Result handle(HttpResponse response) {
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
    throw new IllegalStateException("Unhandled status: " + response.status());
}
```

### The Prism Solution

<!-- verify -->
```java
@GeneratePrisms
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

<!-- verify -->
```java
public class ResilientApiClient {
    private static final Prism<ApiResponse, Success> SUCCESS = ApiResponsePrisms.success();
    private static final Prism<ApiResponse, RateLimitError> RATE_LIMIT = ApiResponsePrisms.rateLimitError();

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

Validation often requires checking different data types and applying conditional rules. Prisms make validation logic declarative and reusable.

### The Challenge

ETL pipelines process heterogeneous data where validation rules depend on data types:

<!-- verify -->
```java
// Traditional approach: imperative branching
List<String> errors = new ArrayList<>();
for (Object value : row.values()) {
    if (value instanceof String s) {
        if (s.length() > MAX_STRING_LENGTH) {
            errors.add("String too long: " + s);
        }
    } else if (value instanceof Integer i) {
        if (i < 0) {
            errors.add("Negative integer: " + i);
        }
    }
    // ... more type checks
}
```

### The Prism Solution

<!-- verify -->
```java
@GeneratePrisms
sealed interface DataValue permits StringData, IntData, DoubleData, NullData {}

record StringData(String value) implements DataValue {}
record IntData(int value) implements DataValue {}
record DoubleData(double value) implements DataValue {}
record NullData() implements DataValue {}

public class ValidationPipeline {
    private static final int MAX_STRING_LENGTH = 255;

    // Validation rules as prism transformations
    private static final Prism<DataValue, StringData> STRING =
        DataValuePrisms.stringData();
    private static final Prism<DataValue, IntData> INT =
        DataValuePrisms.intData();

    public static List<String> validate(List<DataValue> row) {
        return row.stream()
            .flatMap(value -> Stream.concat(
                // Validate strings
                STRING.getOptional(value)
                    .filter(s -> s.value().length() > MAX_STRING_LENGTH)
                    .map(s -> "String too long: " + s.value())
                    .stream(),
                // Validate integers
                INT.getOptional(value)
                    .filter(i -> i.value() < 0)
                    .map(i -> "Negative integer: " + i.value())
                    .stream()
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

<!-- verify -->
```java
public class AccumulatingValidator {
    private static final Prism<DataValue, StringData> STRING = DataValuePrisms.stringData();
    private static final Prism<DataValue, IntData> INT = DataValuePrisms.intData();

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

Event-driven systems receive heterogeneous event types that require different processing logic. Prisms provide type-safe routing without `instanceof` cascades.

### The Challenge

<!-- verify -->
```java
// Traditional approach: brittle event dispatching
public void handleEvent(DomainEvent event) {
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

<!-- verify -->
```java
@GeneratePrisms
sealed interface DomainEvent permits UserCreated, UserDeleted, UserUpdated,
                                     OrderPlaced, OrderCancelled, PaymentProcessed {}

record UserCreated(String userId, String email, Instant timestamp) implements DomainEvent {}
record UserDeleted(String userId, Instant timestamp) implements DomainEvent {}
record UserUpdated(String userId, Map<String, String> changes, Instant timestamp) implements DomainEvent {}
record OrderPlaced(String orderId, List<LineItem> items, Instant timestamp) implements DomainEvent {}
record OrderCancelled(String orderId, String reason, Instant timestamp) implements DomainEvent {}
record PaymentProcessed(String orderId, BigDecimal amount, Instant timestamp) implements DomainEvent {}

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

<!-- verify -->
```java
public class EventProcessor {
    private static final Prism<DomainEvent, UserCreated> USER_CREATED = DomainEventPrisms.userCreated();
    private static final Prism<DomainEvent, UserDeleted> USER_DELETED = DomainEventPrisms.userDeleted();
    private static final Prism<DomainEvent, OrderPlaced> ORDER_PLACED = DomainEventPrisms.orderPlaced();

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
                userCreated.getOptional(e)
                    .filter(uc -> uc.timestamp().isAfter(since))
                    .isPresent()
                ||
                userDeleted.getOptional(e)
                    .filter(ud -> ud.timestamp().isAfter(since))
                    .isPresent()
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

State machines with complex transition rules benefit from prisms' ability to safely match states and transform between them. A consignment's journey is one: the courier's events move its `ConsignmentState` on.

### The Challenge

<!-- verify -->
```java
// Traditional approach: verbose state management
public Consignment transition(Consignment consignment, ConsignmentEvent event) {
    if (consignment.state() instanceof Pending && event instanceof PickedUp) {
        return new Consignment(
            consignment.orderId(), consignment.to(), new Dispatched(((PickedUp) event).at()));
    } else if (consignment.state() instanceof Dispatched && event instanceof ReturnReceived) {
        return new Consignment(
            consignment.orderId(), consignment.to(),
            new Returned(((ReturnReceived) event).reason()));
    } else if (consignment.state() instanceof Returned && event instanceof Rebooked) {
        return new Consignment(consignment.orderId(), consignment.to(), new Pending());
    }
    throw new IllegalStateException("Invalid transition");
}
```

### The Prism Solution

The states are the chapter's sealed `ConsignmentState`, so `@GeneratePrisms` already gives each one a prism:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/ConsignmentState.java:consignment_state}}
```

The events are a sealed type of their own, and the machine matches a state and an event together:

<!-- verify -->
```java
@GeneratePrisms
sealed interface ConsignmentEvent permits PickedUp, ReturnReceived, Rebooked {}

record PickedUp(Instant at) implements ConsignmentEvent {}
record ReturnReceived(String reason) implements ConsignmentEvent {}
record Rebooked() implements ConsignmentEvent {}

public class ConsignmentStateMachine {
    private static final Prism<ConsignmentState, Pending> PENDING =
        ConsignmentStatePrisms.pending();
    private static final Prism<ConsignmentState, Dispatched> DISPATCHED =
        ConsignmentStatePrisms.dispatched();
    private static final Prism<ConsignmentState, Returned> RETURNED =
        ConsignmentStatePrisms.returned();

    private static final Prism<ConsignmentEvent, PickedUp> PICKED_UP =
        ConsignmentEventPrisms.pickedUp();
    private static final Prism<ConsignmentEvent, ReturnReceived> RETURN_RECEIVED =
        ConsignmentEventPrisms.returnReceived();
    private static final Prism<ConsignmentEvent, Rebooked> REBOOKED =
        ConsignmentEventPrisms.rebooked();

    // Define valid transitions as prism combinations
    public Optional<ConsignmentState> transition(
        ConsignmentState currentState, ConsignmentEvent event) {
        // Pending -> Dispatched (on pickup)
        if (PENDING.matches(currentState) && PICKED_UP.matches(event)) {
            return PICKED_UP.mapOptional(
                pickedUp -> new Dispatched(pickedUp.at()),
                event
            );
        }

        // Dispatched -> Returned (on a return)
        if (DISPATCHED.matches(currentState) && RETURN_RECEIVED.matches(event)) {
            return RETURN_RECEIVED.mapOptional(
                received -> new Returned(received.reason()),
                event
            );
        }

        // Returned -> Pending (on rebooking)
        if (RETURNED.matches(currentState) && REBOOKED.matches(event)) {
            return Optional.of(new Pending());
        }

        return Optional.empty(); // Invalid transition
    }

    // Guard conditions using prisms
    public boolean canRedirect(ConsignmentState state) {
        // Can redirect while no courier has it: Pending or Returned
        return PENDING.matches(state) || RETURNED.matches(state);
    }

    // Extract state-specific data
    public Optional<String> getReturnReason(ConsignmentState state) {
        return RETURNED.mapOptional(Returned::reason, state);
    }
}
```

### Advanced: Transition Table

<!-- verify -->
```java
import org.higherkindedj.optics.indexed.Pair; // Pair record from hkj-api

public class AdvancedStateMachine {
    private static final Prism<ConsignmentState, Pending> PENDING =
        ConsignmentStatePrisms.pending();
    private static final Prism<ConsignmentState, Dispatched> DISPATCHED =
        ConsignmentStatePrisms.dispatched();
    private static final Prism<ConsignmentState, Returned> RETURNED =
        ConsignmentStatePrisms.returned();
    private static final Prism<ConsignmentEvent, PickedUp> PICKED_UP =
        ConsignmentEventPrisms.pickedUp();
    private static final Prism<ConsignmentEvent, ReturnReceived> RETURN_RECEIVED =
        ConsignmentEventPrisms.returnReceived();
    private static final Prism<ConsignmentEvent, Rebooked> REBOOKED =
        ConsignmentEventPrisms.rebooked();

    // Define transitions as a declarative table
    private static final Map<
        Pair<Prism<ConsignmentState, ?>, Prism<ConsignmentEvent, ?>>,
        BiFunction<ConsignmentState, ConsignmentEvent, ConsignmentState>
    > TRANSITIONS = Map.of(
        Pair.of(PENDING, PICKED_UP),
        (state, event) -> PICKED_UP.<ConsignmentState>mapOptional(
            c -> new Dispatched(c.at()),
            event
        ).orElse(state),

        Pair.of(DISPATCHED, RETURN_RECEIVED),
        (state, event) -> RETURN_RECEIVED.<ConsignmentState>mapOptional(
            r -> new Returned(r.reason()),
            event
        ).orElse(state),

        Pair.of(RETURNED, REBOOKED),
        (state, event) -> new Pending()
    );

    public ConsignmentState process(ConsignmentState state, ConsignmentEvent event) {
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

Plugin architectures require dynamic dispatch to various plugin types whilst maintaining type safety.

### The Challenge

<!-- verify -->
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

<!-- verify -->
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

### Advanced: Plugin Batches {#advanced-plugin-batches}

<!-- verify -->
```java
public class PluginBatches {
    private static final Prism<Plugin, DatabasePlugin> DB = PluginPrisms.databasePlugin();

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

## Predicate Matching with `Prisms.nearly`

Every prism so far has matched on *type*: is this `JsonString`, is this `ShippingError`. Two utilities extend that to matching on a *condition*, and to expressing the negative case.

### `Prisms.nearly`: match a category of values

`Prisms.only(expected)` matches one exact value. `Prisms.nearly(defaultValue, predicate)` is its predicate-based complement: it matches any value satisfying the predicate, and because a matched value carries no extra information, the focus is `Unit`. The default value is what `build` produces when you run the prism backwards.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/prisms/AdvancedPrismPatternsBook.java:nearly}}
```

The point is not the `Unit` itself but composition: a `nearly` prism drops into any chain where you would otherwise break out into an `if`. Routing a value only when it looks like an email address, for instance:

<!-- verify -->
```java
Prism<String, Unit> emailish =
    Prisms.nearly("user@example.com", s -> s.contains("@") && s.contains("."));

boolean looksLikeEmail = emailish.matches(candidate);
```

~~~admonish note title="`nearly` matches a predicate, not proximity"
Despite the name, `nearly` has nothing to do with numeric closeness. It reads as "nearly `only`": where `only` demands an exact value, `nearly` accepts anything the predicate admits.
~~~

### `doesNotMatch`: the negative case, named

`doesNotMatch(source)` is exactly `!matches(source)`, but naming it keeps filter pipelines readable and stops the reader parsing a negation inside a lambda:

<!-- verify -->
```java
// Collect everything the prism does NOT match
List<JsonValue> nonStrings = values.stream()
    .filter(JsonValuePrisms.jsonString()::doesNotMatch)
    .toList();
```

Use it for exclusion filtering and for guard clauses; use `matches` everywhere else, so the positive case stays the default reading.

---

~~~admonish info title="Key Takeaways"
* **A prism is a routing primitive**: configuration layers, API responses, events, states, and plugins are all "which variant is this?" questions
* **Compose the prism once, reuse it everywhere**: layered fallbacks and deep extractions are values, not call-site logic
* **`matches`/`modifyWhen` keep intent visible**: conditional processing reads as the rule, not as an `instanceof` cascade
* **Sealed hierarchies make the variants explicit**: the compiler knows the whole set, so an exhaustive pattern `switch` fails to compile when one is added. Prism-based routing does not get that check for free, so pair it with a `switch` where coverage has to be guaranteed
* **Production patterns are compositions of the basics**: `getOptional`, `matches`, `mapOptional`, and `modifyWhen` carry every pattern here
~~~

~~~admonish tip title="See Also"
- [Advanced Prism Patterns: Recipes](advanced_prism_patterns_recipes.md): copy-paste recipes for caching composed prisms, bulk-processing utilities, and test strategies
- [Prisms](prisms.md): the fundamentals these patterns build on
- [Prism Toolkit](prism_toolkit.md): the ready-made utility prisms
~~~

~~~admonish info title="Hands-On Learning"
Practise advanced prism patterns in [Tutorial 10: Advanced Prism Patterns](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial10_AdvancedPrismPatterns.java) (8 exercises).
~~~

---

**Previous:** [Prism Toolkit](prism_toolkit.md)
**Next:** [Advanced Prism Patterns: Recipes](advanced_prism_patterns_recipes.md)
