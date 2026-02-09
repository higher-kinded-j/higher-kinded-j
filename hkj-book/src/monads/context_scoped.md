# The Context Effect: Virtual Thread-Scoped Context Propagation

> *"This was no boat accident."*
>
> -- Hooper, *Jaws*

When code behaves unexpectedly in concurrent systems, it's rarely random. There's something beneath the surface: context that was set elsewhere, state that travelled invisibly, assumptions that held on one thread but failed on another. The experienced developer learns to suspect these hidden currents.

> *"A paranoid is someone who knows a little of what's going on."*
>
> -- William S. Burroughs

Java's `ScopedValue` API makes those currents visible. Where `ThreadLocal` let context drift silently through your application (sometimes appearing where it shouldn't, sometimes vanishing where it should) `ScopedValue` enforces explicit boundaries. You declare what flows into a scope. You control what child threads inherit. The paranoid developer becomes the informed one.

`Context<R, A>` brings this power into Higher-Kinded-J's functional vocabulary, providing a composable effect type for reading scoped values with full integration into the VTask and Scope ecosystem.

~~~admonish info title="What You'll Learn"
- Why `ThreadLocal` breaks down with virtual threads
- How `ScopedValue` provides thread-safe context propagation
- Using `Context<R, A>` to read and compose scoped computations
- Integrating Context with VTask for concurrent context propagation
- Building request tracing and security patterns
- MDC-style logging with ScopedValues
~~~

~~~admonish warning title="Java 25+ Feature"
`Context<R, A>` uses Java's `ScopedValue` API (JEP 506), finalised in Java 25. This API provides:
- **Immutability**: Values cannot be changed once bound to a scope
- **Inheritance**: Child virtual threads automatically inherit parent bindings
- **Bounded lifetime**: Values exist only within their declared scope
- **Performance**: Optimised for virtual thread access patterns

Ensure your project targets Java 25 or later to use these features.
~~~

~~~admonish example title="Example Code"
- [ContextBasicExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/context/ContextBasicExample.java) -- Core Context usage patterns
- [ContextScopeExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/context/ContextScopeExample.java) -- Integration with structured concurrency
~~~

---

## The Problem: ThreadLocal in the Virtual Thread Era

`ThreadLocal` served Java well for two decades. It provided thread-confined storage for request context, security principals, and transaction state. But virtual threads expose its fundamental weakness.

Consider a typical web application:

```java
// Traditional ThreadLocal pattern
public class RequestContext {
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    public static void setTraceId(String id) { TRACE_ID.set(id); }
    public static String getTraceId() { return TRACE_ID.get(); }
}

// In a request handler
public Response handleRequest(Request request) {
    RequestContext.setTraceId(request.traceId());
    try {
        return processRequest(request);  // Deep call stack reads TRACE_ID
    } finally {
        RequestContext.setTraceId(null);  // Must clean up!
    }
}
```

This pattern has three problems with virtual threads:

**1. Unbounded Accumulation**

Virtual threads are cheap; you might have millions. Each `ThreadLocal` allocates storage per thread. With platform threads, this was manageable (hundreds of threads). With virtual threads, memory usage explodes.

**2. Inheritance Confusion**

When you fork a virtual thread, does it inherit the parent's `ThreadLocal` values? With `InheritableThreadLocal`, yes, but the child gets a *copy*. Changes in the parent don't propagate. Changes in the child don't propagate back. This silent divergence causes subtle bugs.

**3. Cleanup Burden**

Forgetting to clear a `ThreadLocal` causes memory leaks and context pollution. Virtual threads make this worse: their lightweight nature encourages creating many short-lived threads, each requiring cleanup.

---

## The Solution: ScopedValue-Backed Context

Java 25's `ScopedValue` addresses these problems through different semantics:

```java
// ScopedValue pattern
public class RequestContext {
    public static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();
}

// In a request handler
public Response handleRequest(Request request) {
    return ScopedValue
        .where(RequestContext.TRACE_ID, request.traceId())
        .call(() -> processRequest(request));  // TRACE_ID visible in entire scope
    // No cleanup needed -- scope ends, binding disappears
}
```

**Key differences:**

| Aspect | ThreadLocal | ScopedValue |
|--------|-------------|-------------|
| Mutability | Mutable (`set()` anytime) | Immutable within scope |
| Inheritance | Copies value (diverges) | Shares binding (consistent) |
| Cleanup | Manual (error-prone) | Automatic (scope-based) |
| Memory | Per-thread allocation | Optimised for virtual threads |

`Context<R, A>` wraps `ScopedValue` access in a functional effect type, enabling composition with `map`, `flatMap`, and integration with VTask.

---

## Purpose

`Context<R, A>` represents a computation that reads a value of type `R` from a `ScopedValue<R>` and produces a result of type `A`. It's conceptually similar to `Reader<R, A>`, but with crucial differences:

- **Reader**: Requires explicit parameter passing at `run(r)`
- **Context**: Reads from thread-scoped `ScopedValue`, inherits automatically across virtual thread boundaries

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Context<R, A>                               │
│                                                                     │
│   A computation that:                                               │
│   1. Reads from a ScopedValue<R>                                    │
│   2. Produces a value of type A                                     │
│   3. Can be composed with map/flatMap                               │
│   4. Integrates with VTask for concurrent execution                 │
│                                                                     │
│   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐        │
│   │    define    │ ──► │   compose    │ ──► │   provide    │        │
│   │   Context    │     │   with map/  │     │  ScopedValue │        │
│   │  .ask(KEY)   │     │   flatMap    │     │   binding    │        │
│   └──────────────┘     └──────────────┘     └──────────────┘        │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Core Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                      hkj-api module                                 │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  ContextKind<R, A>                                            │  │
│  │  ─────────────────                                            │  │
│  │  HKT witness interface for Context                            │  │
│  │  Enables integration with type class hierarchy                │  │
│  └───────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                      hkj-core module                                │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  Context<R, A>  (sealed interface)                            │  │
│  │  ─────────────────────────────────                            │  │
│  │  • ask(ScopedValue<R>) -- read the scoped value               │  │
│  │  • asks(ScopedValue<R>, Function<R,A>) -- read and transform  │  │
│  │  • succeed(A) -- lift a pure value                            │  │
│  │  • fail(Throwable) -- represent failure                       │  │
│  │  • map(Function<A,B>) -- transform the result                 │  │
│  │  • flatMap(Function<A, Context<R,B>>) -- chain contexts       │  │
│  │  • toVTask(ScopedValue<R>) -- convert to VTask                │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                              │                                      │
│                              ▼                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  ContextMonad<R>                                              │  │
│  │  ─────────────────                                            │  │
│  │  Monad instance for Context<R, _>                             │  │
│  │  Provides pure, map, flatMap, ap for HKT composition          │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  ContextOps                                                   │  │
│  │  ───────────                                                  │  │
│  │  Static utilities for common patterns:                        │  │
│  │  • withContext(VTask<A>, ScopedValue<R>, R) -- run with value │  │
│  │  • propagate(ScopedValue<R>...) -- propagate to forked tasks  │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌─────────────────────────┐  ┌─────────────────────────────────┐   │
│  │  RequestContext         │  │  SecurityContext                │   │
│  │  ──────────────         │  │  ───────────────                │   │
│  │  TRACE_ID               │  │  PRINCIPAL                      │   │
│  │  CORRELATION_ID         │  │  ROLES                          │   │
│  │  LOCALE                 │  │  AUTH_TOKEN                     │   │
│  │  REQUEST_TIME           │  │  hasRole(), requireRole()       │   │
│  └─────────────────────────┘  └─────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## How to Use Context&lt;R, A&gt;

### Creating a ScopedValue

First, define your scoped values as static finals:

```java
public final class AppContext {
    private AppContext() {}  // Utility class

    /** Current user's preferred locale. */
    public static final ScopedValue<Locale> LOCALE = ScopedValue.newInstance();

    /** Trace ID for distributed tracing. */
    public static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();

    /** Current tenant in multi-tenant application. */
    public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();
}
```

~~~admonish tip title="ScopedValue Naming Convention"
Define `ScopedValue` instances as `public static final` fields in a dedicated utility class. Use SCREAMING_SNAKE_CASE for the field name, matching the convention for constants. This makes the scoped values easy to discover and reference.
~~~

### Reading with Context.ask

The simplest way to read a scoped value:

```java
// Read the LOCALE value
Context<Locale, Locale> getLocale = Context.ask(AppContext.LOCALE);

// Read TRACE_ID
Context<String, String> getTraceId = Context.ask(AppContext.TRACE_ID);
```

`Context.ask(key)` returns a `Context<R, R>`; it reads the value and returns it unchanged.

### Reading and Transforming with Context.asks

Often you want to read and immediately transform:

```java
// Read locale and get the language tag
Context<Locale, String> getLanguage =
    Context.asks(AppContext.LOCALE, Locale::toLanguageTag);

// Read trace ID and format for logging
Context<String, String> getLogPrefix =
    Context.asks(AppContext.TRACE_ID, id -> "[" + id + "] ");

// Read tenant and build connection string
Context<String, String> getConnectionString =
    Context.asks(AppContext.TENANT_ID, tenant ->
        "jdbc:postgresql://db/" + tenant + "_database");
```

### Transforming Results with map

Transform the output of a context computation:

```java
Context<Locale, String> getLocale = Context.ask(AppContext.LOCALE);

// Transform to language tag
Context<Locale, String> getLanguageTag = getLocale.map(Locale::toLanguageTag);

// Transform to display name
Context<Locale, String> getDisplayName = getLocale.map(Locale::getDisplayName);

// Chain multiple transformations
Context<Locale, String> getUpperCaseLanguage = getLocale
    .map(Locale::toLanguageTag)
    .map(String::toUpperCase);
```

### Chaining with flatMap

Compose contexts that depend on previous results:

```java
// First context: get the tenant ID
Context<String, String> getTenant = Context.ask(AppContext.TENANT_ID);

// Second context: use tenant to look up configuration
// (Assume TENANT_CONFIGS is a Map<String, TenantConfig>)
Context<String, TenantConfig> getTenantConfig = getTenant.flatMap(tenantId ->
    Context.succeed(TENANT_CONFIGS.get(tenantId)));

// Chain multiple dependent operations
Context<String, DatabaseConnection> getConnection = getTenant
    .flatMap(tenantId -> Context.succeed(buildConnectionString(tenantId)))
    .flatMap(connStr -> Context.succeed(openConnection(connStr)));
```

### Combining Multiple Scoped Values

Since `Context<R, A>` is parameterised by the scoped value type `R`, you cannot directly chain contexts with different `R` types using `flatMap`. Instead, convert to `VTask` first:

```java
// Convert each Context to VTask, then combine
VTask<RequestInfo> gatherRequestInfo =
    Context.ask(AppContext.TRACE_ID).toVTask().flatMap(traceId ->
        Context.ask(AppContext.LOCALE).toVTask().flatMap(locale ->
            Context.ask(AppContext.TENANT_ID).toVTask().map(tenant ->
                new RequestInfo(traceId, locale, tenant))));

// Or use Context.map2/map3 for same-type contexts
// These work when all contexts read from the SAME ScopedValue type
Context<String, String> combined = Context.map2(
    Context.asks(AppContext.TRACE_ID, id -> id),
    Context.asks(AppContext.TRACE_ID, id -> id.toUpperCase()),
    (lower, upper) -> lower + " -> " + upper);
```

~~~admonish tip title="Why toVTask()?"
`Context<R, A>.flatMap` requires the continuation to return `Context<R, B>` with the **same** `R` type. When reading from different `ScopedValue` types (e.g., `ScopedValue<String>` and `ScopedValue<Locale>`), convert to `VTask` first. `VTask` isn't parameterised by context type, so it can freely combine values from different sources.
~~~

---

## Providing Context Values

### Basic Binding with ScopedValue.where

Bind a value and execute within the scope:

```java
ScopedValue<String> TRACE_ID = ScopedValue.newInstance();

Context<String, String> logMessage =
    Context.asks(TRACE_ID, id -> "[" + id + "] Processing request");

// Provide the value and run
String result = ScopedValue
    .where(TRACE_ID, "trace-abc-123")
    .call(() -> logMessage.run());

System.out.println(result);  // [trace-abc-123] Processing request
```

### Multiple Bindings

Chain multiple bindings for several scoped values:

```java
String result = ScopedValue
    .where(AppContext.TRACE_ID, "trace-123")
    .where(AppContext.LOCALE, Locale.UK)
    .where(AppContext.TENANT_ID, "acme-corp")
    .call(() -> {
        // All three values available in this scope
        return processRequest().run();
    });
```

### Converting to VTask

For integration with the VTask ecosystem:

```java
Context<String, ProcessedData> processWithTrace =
    Context.ask(AppContext.TRACE_ID)
           .map(traceId -> processor.process(traceId));

// Convert to VTask that reads from current scope
VTask<ProcessedData> task = processWithTrace.toVTask();

// Execute within a scope
Try<ProcessedData> result = ScopedValue
    .where(AppContext.TRACE_ID, "trace-xyz")
    .call(() -> task.runSafe());
```

---

## Integration with VTask and Scope

### Context in VTask Pipelines

Combine Context with VTask for effectful computations:

```java
public VTask<Response> handleRequest(Request request) {
    // Build the computation
    VTask<Response> pipeline = VTask
        .delay(() -> validateRequest(request))
        .flatMap(valid -> processRequest(valid))
        .flatMap(result -> formatResponse(result));

    // Execute with context
    return ScopedValue
        .where(RequestContext.TRACE_ID, request.traceId())
        .where(RequestContext.LOCALE, request.locale())
        .call(() -> pipeline);
}

// Deep in the call stack, context is available
private VTask<ProcessedData> processRequest(ValidatedRequest request) {
    return VTask.delay(() -> {
        String traceId = RequestContext.TRACE_ID.get();  // Available!
        logger.info("[{}] Processing: {}", traceId, request);
        return doProcess(request);
    });
}
```

### Context Propagation in Scope

Virtual threads forked within a scope inherit scoped values:

```java
public VTask<AggregatedResult> fetchAllData(String userId) {
    return ScopedValue
        .where(RequestContext.TRACE_ID, generateTraceId())
        .call(() ->
            Scope.<PartialResult>allSucceed()
                .fork(fetchUserProfile(userId))   // Inherits TRACE_ID
                .fork(fetchUserOrders(userId))    // Inherits TRACE_ID
                .fork(fetchUserPreferences(userId)) // Inherits TRACE_ID
                .join((profile, orders, prefs) ->
                    new AggregatedResult(profile, orders, prefs))
        );
}

// Each forked task sees the same TRACE_ID
private VTask<UserProfile> fetchUserProfile(String userId) {
    return VTask.delay(() -> {
        String traceId = RequestContext.TRACE_ID.get();  // Same as parent!
        logger.info("[{}] Fetching profile for {}", traceId, userId);
        return profileService.fetch(userId);
    });
}
```

```
          Main Thread
               │
               │ ScopedValue.where(TRACE_ID, "abc-123")
               │
               ▼
        ┌──────────────┐
        │   Scope      │
        │  .allSucceed │
        └──────┬───────┘
               │
       ┌───────┼───────┐
       │       │       │
       ▼       ▼       ▼
   ┌──────┐ ┌──────┐ ┌──────┐
   │ fork │ │ fork │ │ fork │
   │  #1  │ │  #2  │ │  #3  │
   └──────┘ └──────┘ └──────┘
       │       │       │
       │ TRACE_ID = "abc-123" (inherited)
       │       │       │
       ▼       ▼       ▼
   [profile] [orders] [prefs]
```

### Overriding Context in Forked Tasks

You can override context for specific forked tasks:

```java
Scope.<Result>allSucceed()
    .fork(normalTask())  // Uses parent TRACE_ID
    .fork(() -> ScopedValue
        .where(RequestContext.TRACE_ID, "override-456")
        .call(() -> specialTask().run()))  // Uses overridden TRACE_ID
    .join((normal, special) -> combine(normal, special));
```

---

## Context vs Reader

Both `Context<R, A>` and `Reader<R, A>` represent computations that read from an environment. When should you use each?

### Reader: Explicit Parameter Passing

```java
Reader<Config, String> getHost = Reader.asks(Config::hostname);

// Must explicitly provide Config at run time
String host = getHost.run(productionConfig);
```

**Use Reader when:**
- Configuration is passed at application startup
- You want explicit, visible dependency injection
- Thread propagation is not a concern
- You're in single-threaded or carefully managed contexts

### Context: Implicit Thread-Scoped Propagation

```java
Context<Config, String> getHost = Context.asks(CONFIG, Config::hostname);

// Value comes from ScopedValue binding
String host = ScopedValue
    .where(CONFIG, productionConfig)
    .call(() -> getHost.run());
```

**Use Context when:**
- Values must propagate through virtual thread forks
- Deep call stacks need access without parameter drilling
- Per-request data (trace IDs, user identity) needs implicit flow
- You're building concurrent applications with structured concurrency

### Comparison Table

| Aspect | Reader | Context |
|--------|--------|---------|
| Environment source | Explicit `run(r)` parameter | `ScopedValue` binding |
| Thread inheritance | None (must pass explicitly) | Automatic in virtual threads |
| Typical use | App configuration | Request-scoped data |
| Visibility | Explicit in signatures | Implicit (can be hidden) |
| Testing | Pass mock config to `run()` | Bind mock in `ScopedValue.where()` |
| Java version | Any | Java 25+ |

---

## MDC-Style Logging Integration

A common use case for scoped context is structured logging. Traditional MDC (Mapped Diagnostic Context) uses `ThreadLocal` internally, which has the problems described earlier. Here's how to build MDC-style logging with `ScopedValue`:

### Defining Logging Context

```java
public final class LogContext {
    private LogContext() {}

    public static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> SPAN_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> USER_ID = ScopedValue.newInstance();

    /**
     * Formats the current context as a log prefix.
     * Returns empty string if no context is bound.
     */
    public static String prefix() {
        StringBuilder sb = new StringBuilder();
        if (TRACE_ID.isBound()) {
            sb.append("[trace=").append(TRACE_ID.get()).append("] ");
        }
        if (SPAN_ID.isBound()) {
            sb.append("[span=").append(SPAN_ID.get()).append("] ");
        }
        if (USER_ID.isBound()) {
            sb.append("[user=").append(USER_ID.get()).append("] ");
        }
        return sb.toString();
    }
}
```

### Context-Aware Logging

```java
public final class ContextLogger {
    private final Logger delegate;

    public ContextLogger(Class<?> clazz) {
        this.delegate = LoggerFactory.getLogger(clazz);
    }

    public void info(String message, Object... args) {
        delegate.info(LogContext.prefix() + message, args);
    }

    public void error(String message, Throwable t) {
        delegate.error(LogContext.prefix() + message, t);
    }

    // ... other log levels
}

// Usage
public class OrderService {
    private static final ContextLogger log = new ContextLogger(OrderService.class);

    public Order processOrder(OrderRequest request) {
        log.info("Processing order: {}", request.id());
        // ... processing
        log.info("Order completed successfully");
        return order;
    }
}
```

### Automatic Context Propagation in Logs

When using `Scope`, all forked tasks inherit the logging context:

```java
public VTask<OrderResult> processOrderConcurrently(OrderRequest request) {
    return ScopedValue
        .where(LogContext.TRACE_ID, request.traceId())
        .where(LogContext.USER_ID, request.userId())
        .call(() ->
            Scope.<PartialResult>allSucceed()
                .fork(validateInventory(request))   // Logs with same trace/user
                .fork(calculateShipping(request))   // Logs with same trace/user
                .fork(processPayment(request))      // Logs with same trace/user
                .join(OrderResult::new)
        );
}
```

Sample log output:
```
[trace=ord-789] [user=alice] Processing order: ORD-001
[trace=ord-789] [user=alice] Validating inventory for 3 items
[trace=ord-789] [user=alice] Calculating shipping to postcode SW1A 1AA
[trace=ord-789] [user=alice] Processing payment of £49.99
[trace=ord-789] [user=alice] Order completed successfully
```

### Integration with Existing Logging Frameworks

For integration with SLF4J MDC (which uses `ThreadLocal`), you can bridge at scope boundaries:

```java
public <T> T withMdcBridge(Callable<T> task) throws Exception {
    // Copy ScopedValues to MDC at scope entry
    if (LogContext.TRACE_ID.isBound()) {
        MDC.put("traceId", LogContext.TRACE_ID.get());
    }
    if (LogContext.USER_ID.isBound()) {
        MDC.put("userId", LogContext.USER_ID.get());
    }
    try {
        return task.call();
    } finally {
        MDC.clear();
    }
}
```

~~~admonish warning title="MDC Bridge Limitations"
The MDC bridge only works within a single thread. Forked virtual threads won't automatically get MDC values; they'll get `ScopedValue` bindings. For consistent logging in concurrent code, use the `ContextLogger` pattern shown above rather than relying on MDC.
~~~

---

## Handling Unbound ScopedValues

What happens when code tries to read a `ScopedValue` that hasn't been bound?

### Default Behaviour: Exception

```java
Context<String, String> getTraceId = Context.ask(TRACE_ID);

// If TRACE_ID is not bound, this throws NoSuchElementException
String traceId = getTraceId.run();
```

This fail-fast behaviour is intentional; it surfaces configuration errors immediately rather than allowing silent failures.

### Checking Binding Status

```java
// Check if bound before reading
if (TRACE_ID.isBound()) {
    String traceId = TRACE_ID.get();
    // ... use traceId
} else {
    // Handle missing context
}

// Or with Context
Context<String, Maybe<String>> safeGetTraceId =
    Context.succeed(TRACE_ID.isBound()
        ? Maybe.just(TRACE_ID.get())
        : Maybe.nothing());
```

### Providing Defaults

```java
// Get value or use default
Context<String, String> getTraceIdOrDefault =
    Context.succeed(TRACE_ID.isBound()
        ? TRACE_ID.get()
        : "no-trace");

// Helper method pattern
public static <T> T getOrDefault(ScopedValue<T> key, T defaultValue) {
    return key.isBound() ? key.get() : defaultValue;
}
```

---

## Pre-Built Context Patterns

Higher-Kinded-J provides two pre-built context utilities for common patterns:

### RequestContext

For HTTP request tracing and metadata propagation:

```java
public final class RequestContext {
    public static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> CORRELATION_ID = ScopedValue.newInstance();
    public static final ScopedValue<Locale> LOCALE = ScopedValue.newInstance();
    public static final ScopedValue<Instant> REQUEST_TIME = ScopedValue.newInstance();
    // ... helper methods
}
```

See [RequestContext Patterns](../effect/context_request.md) for detailed documentation.

### SecurityContext

For authentication and authorisation patterns:

```java
public final class SecurityContext {
    public static final ScopedValue<Principal> PRINCIPAL = ScopedValue.newInstance();
    public static final ScopedValue<Set<String>> ROLES = ScopedValue.newInstance();
    public static final ScopedValue<String> AUTH_TOKEN = ScopedValue.newInstance();

    public static Context<Set<String>, Boolean> hasRole(String role) { ... }
    public static Context<Set<String>, Unit> requireRole(String role) { ... }
    // ... more helpers
}
```

See [SecurityContext Patterns](../effect/context_security.md) for detailed documentation.

---

## Testing with Context

### Unit Testing

Bind test values directly:

```java
@Test
void shouldReadTraceId() {
    Context<String, String> ctx = Context.ask(TRACE_ID);

    String result = ScopedValue
        .where(TRACE_ID, "test-trace-123")
        .call(() -> ctx.run());

    assertThat(result).isEqualTo("test-trace-123");
}
```

### Testing Context Composition

When combining contexts with different scoped value types, use `toVTask()`:

```java
@Test
void shouldComposeMultipleContexts() throws Exception {
    // Convert to VTask to combine different ScopedValue types
    VTask<RequestInfo> task =
        Context.ask(TRACE_ID).toVTask().flatMap(traceId ->
            Context.ask(LOCALE).toVTask().map(locale ->
                new RequestInfo(traceId, locale)));

    RequestInfo result = ScopedValue
        .where(TRACE_ID, "test-123")
        .where(LOCALE, Locale.FRANCE)
        .call(() -> task.run());

    assertThat(result.traceId()).isEqualTo("test-123");
    assertThat(result.locale()).isEqualTo(Locale.FRANCE);
}
```

### Testing Unbound Behaviour

```java
@Test
void shouldThrowWhenUnbound() {
    Context<String, String> ctx = Context.ask(TRACE_ID);

    assertThatThrownBy(() -> ctx.run())
        .isInstanceOf(NoSuchElementException.class);
}
```

---

## Summary

| Concept | Description |
|---------|-------------|
| `ScopedValue<R>` | Java 25 API for thread-scoped immutable values |
| `Context<R, A>` | Effect type for reading scoped values functionally |
| `Context.ask(key)` | Read a scoped value |
| `Context.asks(key, f)` | Read and transform in one step |
| `map`, `flatMap` | Transform and compose contexts |
| `toVTask()` | Convert to VTask for execution |
| `ScopedValue.where().call()` | Bind values and execute |
| Inheritance | Child virtual threads inherit bindings |

~~~admonish info title="Hands-On Learning"
Practice Context patterns in [Tutorial 01: Context Fundamentals](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/context/Tutorial01_ContextFundamentals.java) (7 exercises, ~25 minutes).
~~~

~~~admonish tip title="See Also"
- [RequestContext Patterns](../effect/context_request.md) -- Request tracing and metadata
- [SecurityContext Patterns](../effect/context_security.md) -- Authentication and authorisation
- [Context vs ConfigContext](../effect/context_vs_config.md) -- When to use each
- [VTask](vtask_monad.md) -- Virtual thread effect type
- [Scope](vtask_scope.md) -- Structured concurrency
- [Reader](reader_monad.md) -- Explicit environment passing
~~~

---

**Previous:** [Reader Monad](reader_monad.md)
**Next:** [State Monad](state_monad.md)
