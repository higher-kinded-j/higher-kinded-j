# RequestContext: Request-Scoped Patterns

> *"Never get out of the boat. Absolutely goddamn right. Unless you were goin' all the way."*
>
> -- Captain Willard, *Apocalypse Now*

A request enters your system and begins its journey through layers of services, queues, and databases. At each step, you need to know where it came from, where it's going, and how to find it again when something goes wrong. The trace ID is your boat; stay in it, and you can navigate any complexity. Lose it, and you're swimming blind in hostile waters.

> *"Keep a little fire burning; however small, however hidden."*
>
> -- Cormac McCarthy, *The Road*

That small fire is the context you propagate: trace IDs, correlation identifiers, locale preferences, timing information. It may seem like overhead, but when a production incident strikes at 3 AM, that carefully preserved context is the difference between a five-minute diagnosis and a five-hour nightmare.

`RequestContext` provides pre-built patterns for the most common request-scoped values, designed to integrate seamlessly with virtual threads and structured concurrency.

~~~admonish info title="What You'll Learn"
- Defining and using standard request context values
- Propagating trace IDs through concurrent operations
- Integrating with MDC for structured logging
- Building request pipelines with automatic context flow
- Timing and audit patterns with REQUEST_TIME
- Multi-tenant context with TENANT_ID
~~~

~~~admonish example title="Example Code"
- [RequestContextExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/context/RequestContextExample.java) -- Complete request tracing examples
~~~

---

## The RequestContext Utility Class

`RequestContext` is a utility class providing pre-defined `ScopedValue` instances for common request metadata:

```java
public final class RequestContext {
    private RequestContext() {}  // Utility class -- no instantiation

    /**
     * Unique identifier for distributed tracing.
     * Typically generated at the edge (API gateway, load balancer)
     * and propagated through all downstream services.
     */
    public static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();

    /**
     * Correlation ID linking related requests.
     * Used to group requests that are part of the same user action
     * or business transaction, even across separate trace trees.
     */
    public static final ScopedValue<String> CORRELATION_ID = ScopedValue.newInstance();

    /**
     * User's preferred locale for response formatting.
     * Influences date formats, number formats, and message translations.
     */
    public static final ScopedValue<Locale> LOCALE = ScopedValue.newInstance();

    /**
     * Tenant identifier for multi-tenant applications.
     * Determines which tenant's data and configuration to use.
     */
    public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();

    /**
     * Timestamp when the request was received.
     * Useful for timeout calculations, audit logging, and latency measurement.
     */
    public static final ScopedValue<Instant> REQUEST_TIME = ScopedValue.newInstance();

    /**
     * Request deadline for timeout propagation.
     * Operations should check this and fail fast if deadline has passed.
     */
    public static final ScopedValue<Instant> DEADLINE = ScopedValue.newInstance();
}
```

~~~admonish tip title="Design Rationale: Utility Class"
`RequestContext` uses static `ScopedValue` fields rather than bundling values in a record. This design provides:

- **Flexibility**: Bind only the values you need; others remain unbound
- **Granularity**: Different parts of your system can read different values
- **Composition**: Easily add custom scoped values alongside standard ones
- **Compatibility**: Works naturally with `ScopedValue.where().where()` chaining
~~~

---

## Trace ID: The Foundation of Observability

The trace ID is the most critical piece of request context. It's a unique identifier that follows a request through every service, database query, and message queue interaction.

### Generating Trace IDs

```java
public final class TraceIdGenerator {
    private TraceIdGenerator() {}

    /**
     * Generate a trace ID using UUID.
     * Simple and guaranteed unique, but verbose (36 characters).
     */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generate a compact trace ID.
     * 16 characters, URL-safe, suitable for headers.
     */
    public static String compact() {
        byte[] bytes = new byte[12];
        ThreadLocalRandom.current().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Generate a trace ID with timestamp prefix.
     * Format: {timestamp-hex}-{random-hex}
     * Enables rough time-based sorting of traces.
     */
    public static String timestamped() {
        long timestamp = System.currentTimeMillis();
        long random = ThreadLocalRandom.current().nextLong();
        return String.format("%012x-%08x", timestamp, random & 0xFFFFFFFFL);
    }
}
```

### Establishing Trace Context at the Edge

At your application's entry point (HTTP handler, message consumer, scheduled job), establish the trace context:

```java
public class RequestHandler {

    public Response handle(HttpRequest request) {
        // Extract or generate trace ID
        String traceId = request.header("X-Trace-ID")
            .orElseGet(TraceIdGenerator::compact);

        String correlationId = request.header("X-Correlation-ID")
            .orElse(traceId);  // Default correlation to trace if not provided

        Locale locale = request.header("Accept-Language")
            .map(Locale::forLanguageTag)
            .orElse(Locale.UK);

        // Bind context and process
        return ScopedValue
            .where(RequestContext.TRACE_ID, traceId)
            .where(RequestContext.CORRELATION_ID, correlationId)
            .where(RequestContext.LOCALE, locale)
            .where(RequestContext.REQUEST_TIME, Instant.now())
            .call(() -> processRequest(request));
    }

    private Response processRequest(HttpRequest request) {
        // All code in this scope has access to request context
        // including any virtual threads forked from here
        return router.route(request);
    }
}
```

### Reading Trace ID Deep in the Stack

Anywhere in your call stack, read the trace ID without parameter passing:

```java
public class OrderService {
    private static final ContextLogger log = new ContextLogger(OrderService.class);

    public Order createOrder(OrderRequest request) {
        // Trace ID is available implicitly
        String traceId = RequestContext.TRACE_ID.get();
        log.info("Creating order for customer: {}", request.customerId());

        Order order = buildOrder(request);
        orderRepository.save(order);

        log.info("Order created: {}", order.id());
        return order;
    }
}
```

### Propagating to Downstream Services

When calling external services, include the trace ID in headers:

```java
public class ExternalServiceClient {

    public <T> T call(String url, Class<T> responseType) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET();

        // Propagate trace context to downstream service
        if (RequestContext.TRACE_ID.isBound()) {
            builder.header("X-Trace-ID", RequestContext.TRACE_ID.get());
        }
        if (RequestContext.CORRELATION_ID.isBound()) {
            builder.header("X-Correlation-ID", RequestContext.CORRELATION_ID.get());
        }

        HttpResponse<String> response = httpClient.send(
            builder.build(),
            HttpResponse.BodyHandlers.ofString());

        return parseResponse(response, responseType);
    }
}
```

```
┌──────────────────────────────────────────────────────────────────────┐
│                     Trace Propagation Flow                           │
│                                                                      │
│   API Gateway                                                        │
│   ┌─────────────────────────────────────────────────────────────┐    │
│   │ X-Trace-ID: abc123                                          │    │
│   │ Generates or extracts trace ID from incoming request        │    │
│   └─────────────────────┬───────────────────────────────────────┘    │
│                         │                                            │
│                         ▼                                            │
│   Order Service         │                                            │
│   ┌─────────────────────┴───────────────────────────────────────┐    │
│   │ ScopedValue.where(TRACE_ID, "abc123")                       │    │
│   │                                                             │    │
│   │   ┌──────────────┐    ┌──────────────┐                      │    │
│   │   │ Validate     │    │ Calculate    │   (forked tasks      │    │
│   │   │ TRACE_ID=abc │    │ TRACE_ID=abc │    inherit context)  │    │
│   │   └──────────────┘    └──────────────┘                      │    │
│   │                                                             │    │
│   └─────────────────────┬───────────────────────────────────────┘    │
│                         │ HTTP call with X-Trace-ID: abc123          │
│                         ▼                                            │
│   Payment Service                                                    │
│   ┌─────────────────────────────────────────────────────────────┐    │
│   │ Extracts X-Trace-ID header                                  │    │
│   │ ScopedValue.where(TRACE_ID, "abc123")                       │    │
│   │ → Same trace ID continues through downstream service        │    │
│   └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Correlation ID: Linking Related Requests

While trace ID identifies a single request, correlation ID groups related requests that form a logical unit of work.

### Use Cases for Correlation ID

```java
// Scenario: User clicks "Submit Order" which triggers multiple API calls
// All share the same correlation ID but have different trace IDs

// Frontend generates correlation ID for the user action
String correlationId = "user-action-" + UUID.randomUUID();

// Request 1: Validate cart
// Trace: t1, Correlation: user-action-xyz
validateCart(cartId);

// Request 2: Process payment
// Trace: t2, Correlation: user-action-xyz
processPayment(paymentDetails);

// Request 3: Create order
// Trace: t3, Correlation: user-action-xyz
createOrder(orderDetails);

// All three requests can be found by searching for correlation ID
```

### Correlation in Async Workflows

```java
public class OrderWorkflow {

    public void submitOrder(OrderRequest request, String correlationId) {
        ScopedValue
            .where(RequestContext.CORRELATION_ID, correlationId)
            .where(RequestContext.TRACE_ID, TraceIdGenerator.compact())
            .run(() -> {
                // Publish event with correlation ID
                OrderSubmittedEvent event = new OrderSubmittedEvent(
                    request,
                    RequestContext.TRACE_ID.get(),
                    RequestContext.CORRELATION_ID.get()
                );
                eventPublisher.publish(event);
            });
    }

    // Later, when processing the event (possibly in a different service)
    public void handleOrderSubmitted(OrderSubmittedEvent event) {
        ScopedValue
            .where(RequestContext.CORRELATION_ID, event.correlationId())
            .where(RequestContext.TRACE_ID, TraceIdGenerator.compact())  // New trace
            .run(() -> {
                // New trace ID, but same correlation links it to original action
                processOrder(event.orderRequest());
            });
    }
}
```

---

## Locale: Internationalisation Context

The locale determines how dates, numbers, and messages are formatted in responses.

### Reading Locale for Formatting

```java
public class ResponseFormatter {

    public String formatCurrency(BigDecimal amount) {
        Locale locale = RequestContext.LOCALE.orElse(Locale.UK);
        NumberFormat formatter = NumberFormat.getCurrencyInstance(locale);
        return formatter.format(amount);
    }

    public String formatDate(LocalDate date) {
        Locale locale = RequestContext.LOCALE.orElse(Locale.UK);
        DateTimeFormatter formatter = DateTimeFormatter
            .ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale);
        return date.format(formatter);
    }

    public String getMessage(String key, Object... args) {
        Locale locale = RequestContext.LOCALE.orElse(Locale.UK);
        ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);
        String pattern = bundle.getString(key);
        return MessageFormat.format(pattern, args);
    }
}
```

### Locale-Aware Response Building

```java
public class OrderResponseBuilder {

    private final ResponseFormatter formatter = new ResponseFormatter();

    public OrderResponse build(Order order) {
        return new OrderResponse(
            order.id(),
            formatter.formatCurrency(order.total()),
            formatter.formatDate(order.createdDate()),
            formatter.getMessage("order.status." + order.status().name())
        );
    }
}
```

---

## Request Timing and Deadlines

### REQUEST_TIME: When Did This Start?

Track when the request was received for latency measurement and audit:

```java
public class LatencyTracker {

    public void logOperationLatency(String operation) {
        if (RequestContext.REQUEST_TIME.isBound()) {
            Instant start = RequestContext.REQUEST_TIME.get();
            Duration elapsed = Duration.between(start, Instant.now());
            log.info("Operation '{}' at {}ms into request",
                operation, elapsed.toMillis());
        }
    }

    public Duration requestAge() {
        if (RequestContext.REQUEST_TIME.isBound()) {
            return Duration.between(RequestContext.REQUEST_TIME.get(), Instant.now());
        }
        return Duration.ZERO;
    }
}
```

### DEADLINE: Timeout Propagation

Propagate deadlines to ensure operations fail fast when time runs out:

```java
public class DeadlineAwareService {

    public <T> T executeWithDeadline(Callable<T> operation) throws Exception {
        // Check if we've already exceeded the deadline
        if (RequestContext.DEADLINE.isBound()) {
            Instant deadline = RequestContext.DEADLINE.get();
            if (Instant.now().isAfter(deadline)) {
                throw new DeadlineExceededException(
                    "Request deadline exceeded before operation started");
            }
        }

        return operation.call();
    }

    public Duration remainingTime() {
        if (RequestContext.DEADLINE.isBound()) {
            Duration remaining = Duration.between(
                Instant.now(),
                RequestContext.DEADLINE.get());
            return remaining.isNegative() ? Duration.ZERO : remaining;
        }
        return Duration.ofDays(1);  // No deadline = effectively infinite
    }
}

// Usage at edge
public Response handle(HttpRequest request) {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(30));

    return ScopedValue
        .where(RequestContext.REQUEST_TIME, Instant.now())
        .where(RequestContext.DEADLINE, deadline)
        .call(() -> processWithTimeout(request));
}
```

---

## Tenant ID: Multi-Tenant Applications

For SaaS applications serving multiple tenants from a single deployment:

### Tenant Resolution

```java
public class TenantResolver {

    /**
     * Resolve tenant from various sources in priority order.
     */
    public String resolve(HttpRequest request) {
        // 1. Explicit header
        Optional<String> headerTenant = request.header("X-Tenant-ID");
        if (headerTenant.isPresent()) {
            return headerTenant.get();
        }

        // 2. Subdomain (tenant.example.com)
        String host = request.host();
        if (host.contains(".")) {
            String subdomain = host.substring(0, host.indexOf('.'));
            if (!subdomain.equals("www") && !subdomain.equals("api")) {
                return subdomain;
            }
        }

        // 3. Path prefix (/tenant/{tenantId}/...)
        String path = request.path();
        if (path.startsWith("/tenant/")) {
            String[] parts = path.split("/");
            if (parts.length >= 3) {
                return parts[2];
            }
        }

        // 4. Default tenant
        return "default";
    }
}
```

### Tenant-Aware Data Access

```java
public class TenantAwareRepository<T> {

    private final DataSource dataSource;

    public T findById(String id) {
        String tenantId = RequestContext.TENANT_ID.get();
        String sql = "SELECT * FROM " + tableName() +
            " WHERE id = ? AND tenant_id = ?";

        return jdbcTemplate.queryForObject(sql, rowMapper(), id, tenantId);
    }

    public void save(T entity) {
        String tenantId = RequestContext.TENANT_ID.get();
        // Automatically tag entity with current tenant
        setTenantId(entity, tenantId);
        jdbcTemplate.save(entity);
    }
}
```

### Tenant-Specific Configuration

```java
public class TenantConfigService {

    private final Map<String, TenantConfig> configCache;

    public TenantConfig currentConfig() {
        String tenantId = RequestContext.TENANT_ID.get();
        return configCache.computeIfAbsent(tenantId, this::loadConfig);
    }

    public String getConnectionString() {
        TenantConfig config = currentConfig();
        return config.databaseUrl();
    }

    public Set<String> getEnabledFeatures() {
        TenantConfig config = currentConfig();
        return config.featureFlags();
    }
}
```

---

## MDC Integration for Structured Logging

### The ContextLogger Pattern

Build a logger that automatically includes request context:

```java
public class ContextLogger {
    private final Logger delegate;

    public ContextLogger(Class<?> clazz) {
        this.delegate = LoggerFactory.getLogger(clazz);
    }

    public void info(String message, Object... args) {
        delegate.info(formatWithContext(message), args);
    }

    public void warn(String message, Object... args) {
        delegate.warn(formatWithContext(message), args);
    }

    public void error(String message, Throwable t) {
        delegate.error(formatWithContext(message), t);
    }

    public void error(String message, Object... args) {
        delegate.error(formatWithContext(message), args);
    }

    private String formatWithContext(String message) {
        StringBuilder prefix = new StringBuilder();

        if (RequestContext.TRACE_ID.isBound()) {
            prefix.append("[trace=").append(RequestContext.TRACE_ID.get()).append("] ");
        }
        if (RequestContext.TENANT_ID.isBound()) {
            prefix.append("[tenant=").append(RequestContext.TENANT_ID.get()).append("] ");
        }

        return prefix.toString() + message;
    }
}
```

### JSON Structured Logging

For log aggregation systems (ELK, Splunk, CloudWatch):

```java
public class StructuredLogger {
    private final Logger delegate;
    private final ObjectMapper mapper;

    public void info(String message, Object... args) {
        LogEntry entry = buildEntry("INFO", message, args);
        delegate.info(toJson(entry));
    }

    private LogEntry buildEntry(String level, String message, Object[] args) {
        return new LogEntry(
            level,
            String.format(message.replace("{}", "%s"), args),
            Instant.now(),
            contextMap()
        );
    }

    private Map<String, String> contextMap() {
        Map<String, String> context = new HashMap<>();

        if (RequestContext.TRACE_ID.isBound()) {
            context.put("traceId", RequestContext.TRACE_ID.get());
        }
        if (RequestContext.CORRELATION_ID.isBound()) {
            context.put("correlationId", RequestContext.CORRELATION_ID.get());
        }
        if (RequestContext.TENANT_ID.isBound()) {
            context.put("tenantId", RequestContext.TENANT_ID.get());
        }
        if (RequestContext.REQUEST_TIME.isBound()) {
            context.put("requestAge",
                Duration.between(RequestContext.REQUEST_TIME.get(), Instant.now())
                    .toMillis() + "ms");
        }

        return context;
    }

    record LogEntry(
        String level,
        String message,
        Instant timestamp,
        Map<String, String> context
    ) {}
}
```

Sample output:
```json
{
  "level": "INFO",
  "message": "Order created: ORD-123",
  "timestamp": "2025-01-15T10:30:45.123Z",
  "context": {
    "traceId": "abc-123-def",
    "correlationId": "user-action-xyz",
    "tenantId": "acme-corp",
    "requestAge": "45ms"
  }
}
```

### Bridging to SLF4J MDC

If you have existing code using SLF4J MDC, bridge at scope boundaries:

```java
public class MdcBridge {

    /**
     * Execute a task with ScopedValue context copied to MDC.
     * Use this when calling legacy code that reads from MDC.
     */
    public static <T> T withMdc(Callable<T> task) throws Exception {
        try {
            // Copy ScopedValues to MDC
            if (RequestContext.TRACE_ID.isBound()) {
                MDC.put("traceId", RequestContext.TRACE_ID.get());
            }
            if (RequestContext.CORRELATION_ID.isBound()) {
                MDC.put("correlationId", RequestContext.CORRELATION_ID.get());
            }
            if (RequestContext.TENANT_ID.isBound()) {
                MDC.put("tenantId", RequestContext.TENANT_ID.get());
            }

            return task.call();
        } finally {
            MDC.clear();
        }
    }
}

// Usage with legacy logging code
public void callLegacyService() {
    MdcBridge.withMdc(() -> {
        // Legacy code that uses MDC.get("traceId") will work here
        legacyService.process();
        return null;
    });
}
```

~~~admonish warning title="MDC Bridge Limitations"
The MDC bridge only works within the thread where it's called. Virtual threads forked after the bridge call won't have MDC values; they'll need their own bridge call. For new code, prefer `ContextLogger` which reads directly from `ScopedValue`.
~~~

---

## Complete Request Pipeline Example

Putting it all together:

```java
public class OrderController {
    private static final ContextLogger log = new ContextLogger(OrderController.class);

    private final OrderService orderService;
    private final TenantResolver tenantResolver;

    public Response createOrder(HttpRequest request) {
        // Resolve all context values
        String traceId = request.header("X-Trace-ID")
            .orElseGet(TraceIdGenerator::compact);
        String correlationId = request.header("X-Correlation-ID")
            .orElse(traceId);
        Locale locale = request.header("Accept-Language")
            .map(Locale::forLanguageTag)
            .orElse(Locale.UK);
        String tenantId = tenantResolver.resolve(request);
        Instant now = Instant.now();
        Instant deadline = now.plus(Duration.ofSeconds(30));

        // Bind context and process
        return ScopedValue
            .where(RequestContext.TRACE_ID, traceId)
            .where(RequestContext.CORRELATION_ID, correlationId)
            .where(RequestContext.LOCALE, locale)
            .where(RequestContext.TENANT_ID, tenantId)
            .where(RequestContext.REQUEST_TIME, now)
            .where(RequestContext.DEADLINE, deadline)
            .call(() -> {
                log.info("Processing order request");

                try {
                    OrderRequest orderRequest = parseBody(request, OrderRequest.class);
                    Order order = orderService.create(orderRequest);

                    log.info("Order created successfully: {}", order.id());
                    return Response.ok(order);

                } catch (ValidationException e) {
                    log.warn("Validation failed: {}", e.getMessage());
                    return Response.badRequest(e.getMessage());

                } catch (DeadlineExceededException e) {
                    log.error("Request deadline exceeded", e);
                    return Response.timeout();

                } catch (Exception e) {
                    log.error("Order creation failed", e);
                    return Response.serverError();
                }
            });
    }
}
```

---

## Integration with VTask and Scope

Request context propagates automatically to forked virtual threads:

```java
public class OrderService {
    private static final ContextLogger log = new ContextLogger(OrderService.class);

    public Order create(OrderRequest request) {
        log.info("Starting order creation");

        // Context propagates to all forked tasks
        return Scope.<OrderComponent>allSucceed()
            .fork(() -> {
                log.info("Validating inventory");  // Has trace ID
                return validateInventory(request);
            })
            .fork(() -> {
                log.info("Calculating pricing");   // Has trace ID
                return calculatePricing(request);
            })
            .fork(() -> {
                log.info("Checking fraud score");  // Has trace ID
                return checkFraudScore(request);
            })
            .join((inventory, pricing, fraud) -> {
                log.info("Assembling order");      // Has trace ID
                return assembleOrder(request, inventory, pricing, fraud);
            })
            .run();
    }
}
```

---

## Summary

| ScopedValue | Purpose | Typical Source |
|-------------|---------|----------------|
| `TRACE_ID` | Distributed tracing | Generated or from `X-Trace-ID` header |
| `CORRELATION_ID` | Link related requests | From `X-Correlation-ID` or same as trace |
| `LOCALE` | Response formatting | From `Accept-Language` header |
| `TENANT_ID` | Multi-tenant isolation | From header, subdomain, or path |
| `REQUEST_TIME` | Latency tracking | `Instant.now()` at request start |
| `DEADLINE` | Timeout propagation | Calculated from timeout policy |

~~~admonish info title="Hands-On Learning"
Practice request tracing patterns in [Tutorial 03: Request Tracing Patterns](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/context/Tutorial03_RequestTracingPatterns.java) (6 exercises, ~25 minutes).
~~~

~~~admonish tip title="See Also"
- [Context Effect](../monads/context_scoped.md) -- Core Context documentation
- [SecurityContext Patterns](context_security.md) -- Authentication and authorisation
- [VTaskPath](path_vtask.md) -- Virtual thread effect paths
- [Scope](../monads/vtask_scope.md) -- Structured concurrency
~~~

---

**Previous:** [Effect Contexts Overview](effect_contexts.md)
**Next:** [SecurityContext Patterns](context_security.md)
