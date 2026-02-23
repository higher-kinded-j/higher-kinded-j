# Context vs ConfigContext: Choosing the Right Tool

> *"If the rule you followed brought you to this, of what use was the rule?"*
>
> -- Anton Chigurh, *No Country for Old Men*

Both `Context<R, A>` and `ConfigContext<F, R, A>` solve the problem of accessing environment values without threading parameters through every function. Both enable clean, composable code. Both support testing through dependency injection. Yet they solve subtly different problems, and using the wrong one leads to subtle bugs or unnecessary complexity.

> *"A story has no beginning or end: arbitrarily one chooses that moment of experience from which to look back or from which to look ahead."*
>
> -- Graham Greene, *The End of the Affair*

The choice between them isn't about which is "better"; it's about where your story begins. Does your environment value exist at application startup, passed through a call chain? Or does it flow implicitly through thread scopes, inherited by forked virtual threads? The answer determines your tool.

~~~admonish info title="What You'll Learn"
- The fundamental difference between Context and ConfigContext
- When thread propagation semantics matter
- How to choose the right tool for common scenarios
- Using both together in the same application
- Migration patterns between the two approaches
~~~

---

## The Core Difference

### ConfigContext: Explicit Parameter Passing

`ConfigContext<F, R, A>` wraps `ReaderT<F, R, A>`: a monad transformer that threads an environment `R` through a computation. The environment is provided explicitly when you run the computation.

```java
// Define a computation that needs configuration
ConfigContext<IOKind.Witness, DatabaseConfig, User> fetchUser =
    ConfigContext.io(config ->
        userRepository.findById(userId, config.connectionString()));

// Provide configuration at runtime -- must pass explicitly
User user = fetchUser.runWithSync(productionConfig);
```

**Key characteristic:** The configuration flows through the call chain because you pass it at `runWithSync()`. Any code that needs the config must be part of the `ConfigContext` computation.

### Context: Implicit Thread-Scoped Propagation

`Context<R, A>` reads from a `ScopedValue<R>`: Java's thread-scoped value container. The value is bound to a scope and automatically available to all code in that scope, including forked virtual threads.

```java
// Define a scoped value
static final ScopedValue<DatabaseConfig> DB_CONFIG = ScopedValue.newInstance();

// Define a computation that reads from it
Context<DatabaseConfig, User> fetchUser =
    Context.asks(DB_CONFIG, config ->
        userRepository.findById(userId, config.connectionString()));

// Bind value to a scope -- implicitly available everywhere in scope
User user = ScopedValue
    .where(DB_CONFIG, productionConfig)
    .call(() -> fetchUser.run());
```

**Key characteristic:** The configuration is available implicitly within the scope. Code doesn't need to be part of a computation chain; any code executing in the scope can access the value.

---

## When Thread Propagation Matters

The critical difference emerges with virtual threads and structured concurrency.

### ConfigContext: Must Pass Explicitly to Forked Tasks

```java
ConfigContext<IOKind.Witness, RequestInfo, Result> process =
    ConfigContext.io(requestInfo -> {
        // requestInfo is available here...

        return Scope.<PartialResult>allSucceed()
            .fork(() -> {
                // ❌ requestInfo is NOT available here!
                // Forked virtual threads don't inherit ConfigContext
                return fetchData();
            })
            .fork(() -> {
                // ❌ Also not available here
                return fetchMoreData();
            })
            .join(Result::combine)
            .run();
    });
```

To propagate `ConfigContext` values to forked tasks, you must pass them explicitly:

```java
ConfigContext<IOKind.Witness, RequestInfo, Result> process =
    ConfigContext.io(requestInfo -> {
        // Must capture and pass explicitly
        return Scope.<PartialResult>allSucceed()
            .fork(() -> fetchData(requestInfo))      // Pass explicitly
            .fork(() -> fetchMoreData(requestInfo))  // Pass explicitly
            .join(Result::combine)
            .run();
    });
```

### Context: Automatic Inheritance in Forked Tasks

```java
ScopedValue<RequestInfo> REQUEST = ScopedValue.newInstance();

VTask<Result> process = VTask.delay(() -> {
    return Scope.<PartialResult>allSucceed()
        .fork(() -> {
            // ✓ REQUEST is automatically available!
            RequestInfo info = REQUEST.get();
            return fetchData(info);
        })
        .fork(() -> {
            // ✓ Also available here
            RequestInfo info = REQUEST.get();
            return fetchMoreData(info);
        })
        .join(Result::combine)
        .run();
});

// Bind once, propagates to all forked tasks
Result result = ScopedValue
    .where(REQUEST, requestInfo)
    .call(() -> process.run());
```

```
┌─────────────────────────────────────────────────────────────────────┐
│                   Thread Propagation Comparison                     │
│                                                                     │
│   ConfigContext (ReaderT)              Context (ScopedValue)        │
│   ─────────────────────                ─────────────────────        │
│                                                                     │
│   runWithSync(config)                  ScopedValue.where(KEY, val)  │
│         │                                      │                    │
│         ▼                                      ▼                    │
│   ┌───────────┐                        ┌───────────┐                │
│   │  Parent   │                        │  Parent   │                │
│   │config = ✓ │                        │ KEY = ✓   │                │
│   └─────┬─────┘                        └─────┬─────┘                │
│         │                                    │                      │
│    fork │ fork                          fork │ fork                 │
│         │                                    │                      │
│   ┌─────┴─────┐                        ┌─────┴─────┐                │
│   │           │                        │           │                │
│   ▼           ▼                        ▼           ▼                │
│ ┌─────┐   ┌─────┐                  ┌─────┐   ┌─────┐                │
│ │Child│   │Child│                  │Child│   │Child│                │
│ │ ❌   │   │ ❌   │                  │ ✓   │   │ ✓   │                │
│ │     │   │     │                  │KEY  │   │KEY  │                │
│ │Must │   │Must │                  │auto │   │auto │                │
│ │pass │   │pass │                  │     │   │     │                │
│ └─────┘   └─────┘                  └─────┘   └─────┘                │
│                                                                     │
│   Children don't inherit            Children inherit automatically  │
│   Must pass explicitly              via ScopedValue binding         │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Decision Guide

### The Key Question

> **"Does this value need to automatically propagate to child virtual threads?"**

If **yes** → Use `Context` with `ScopedValue`
If **no** → Use `ConfigContext` (or plain parameter passing)

### Scenario-Based Recommendations

| Scenario | Recommended | Reasoning |
|----------|-------------|-----------|
| **Request trace ID** | Context | Must follow forked tasks for distributed tracing |
| **User authentication** | Context | Security context must propagate to all operations |
| **Request locale** | Context | Formatting should be consistent across parallel ops |
| **Database config** | ConfigContext | Set once at startup, no thread propagation needed |
| **Feature flags** | ConfigContext | App-level config, doesn't need per-thread inheritance |
| **API base URLs** | ConfigContext | Static configuration, passed explicitly |
| **Tenant ID (multi-tenant)** | Context | Must propagate to all data access operations |
| **Transaction context** | Context | All operations must participate in same transaction |
| **Logging configuration** | ConfigContext | Static, doesn't vary per request |
| **Request deadline/timeout** | Context | Must be visible to all forked operations |

### Decision Flowchart

```
                    What kind of value is it?
                              │
              ┌───────────────┴───────────────┐
              │                               │
       Per-Request                    Application-Level
       (varies per call)              (same for all calls)
              │                               │
              │                               ▼
              │                        ConfigContext
              │                     (or plain parameters)
              │
              ▼
    Will you fork virtual threads?
              │
       ┌──────┴──────┐
       │             │
      Yes            No
       │             │
       ▼             ▼
    Context     Either works
                (Context for consistency,
                 ConfigContext if already using)
```

---

## Detailed Comparison

| Aspect | ConfigContext | Context |
|--------|---------------|---------|
| **Underlying mechanism** | `ReaderT` monad transformer | `ScopedValue` API |
| **Java version required** | Any | Java 21+ (preview), Java 25+ (final) |
| **Thread inheritance** | No (must pass explicitly) | Yes (automatic) |
| **Scope definition** | `runWithSync(value)` call | `ScopedValue.where().run()` block |
| **Multiple values** | Single `R` type (use record for multiple) | Multiple `ScopedValue`s, each independent |
| **Type safety** | Compile-time via generics | Runtime via `ScopedValue.get()` |
| **Effect integration** | Built-in (`IOKind.Witness`) | Convert with `toVTask()` |
| **Composability** | Via `flatMap`, `map` | Via `flatMap`, `map` |
| **Testing** | Pass mock at `runWithSync()` | Bind mock in `ScopedValue.where()` |
| **Layer** | Layer 2 (Effect Context) | Core effect type |

---

## Common Patterns

### Pattern 1: Application Config with ConfigContext

```java
// Define configuration record
record AppConfig(
    String databaseUrl,
    String apiBaseUrl,
    int maxConnections,
    Duration timeout
) {}

// Use ConfigContext for app-level config
public class UserService {
    public ConfigContext<IOKind.Witness, AppConfig, User> getUser(String id) {
        return ConfigContext.io(config -> {
            var connection = connect(config.databaseUrl());
            return connection.query("SELECT * FROM users WHERE id = ?", id);
        });
    }
}

// At application startup
AppConfig config = loadConfig();
User user = userService.getUser("123").runWithSync(config);
```

### Pattern 2: Request Context with Context

```java
// Define scoped values for request data
public final class RequestContext {
    public static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> USER_ID = ScopedValue.newInstance();
}

// Use Context for request-scoped data
public class OrderService {
    public VTask<Order> createOrder(OrderRequest request) {
        return VTask.delay(() -> {
            String traceId = RequestContext.TRACE_ID.get();
            String userId = RequestContext.USER_ID.get();

            log.info("[{}] Creating order for user {}", traceId, userId);
            return orderRepository.create(request, userId);
        });
    }
}

// At request handling
public Response handleRequest(HttpRequest request) {
    return ScopedValue
        .where(RequestContext.TRACE_ID, request.traceId())
        .where(RequestContext.USER_ID, request.userId())
        .call(() -> orderService.createOrder(parseBody(request)).runSafe());
}
```

### Pattern 3: Using Both Together

Most applications need both: application-level configuration and request-scoped context.

```java
public class OrderController {

    // App config via ConfigContext
    private final ConfigContext<IOKind.Witness, AppConfig, OrderService> serviceFactory =
        ConfigContext.io(config -> new OrderService(config.databaseUrl()));

    // Request handler combines both
    public Response createOrder(HttpRequest request, AppConfig appConfig) {
        // 1. Create service with app config
        OrderService service = serviceFactory.runWithSync(appConfig);

        // 2. Bind request context and process
        return ScopedValue
            .where(RequestContext.TRACE_ID, request.traceId())
            .where(RequestContext.USER_ID, extractUserId(request))
            .where(SecurityContext.PRINCIPAL, authenticate(request))
            .call(() -> {
                // Service methods can read from RequestContext
                Order order = service.createOrder(parseBody(request)).run();
                return Response.ok(order);
            });
    }
}
```

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Combined Usage Pattern                           │
│                                                                     │
│   Application Startup                                               │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │  AppConfig loaded from environment/files                    │   │
│   │  Services created with ConfigContext.runWithSync(config)    │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│                              ▼                                      │
│   Per Request                                                       │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │  ScopedValue.where(TRACE_ID, ...)                           │   │
│   │             .where(USER_ID, ...)                            │   │
│   │             .where(PRINCIPAL, ...)                          │   │
│   │             .call(() -> {                                   │   │
│   │                 // Services use app config (injected)       │   │
│   │                 // Operations read request context          │   │
│   │                 // Forked tasks inherit request context     │   │
│   │             });                                             │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│   ConfigContext: Application-level, passed at startup               │
│   Context: Request-level, bound per request, inherits in forks      │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Migration Patterns

### From ConfigContext to Context

If you find yourself manually passing context to every forked task, consider migrating to `Context`:

```java
// Before: Manual propagation
ConfigContext<IOKind.Witness, RequestInfo, Result> process =
    ConfigContext.io(info -> {
        return Scope.<Data>allSucceed()
            .fork(() -> fetch1(info))  // Must pass
            .fork(() -> fetch2(info))  // Must pass
            .fork(() -> fetch3(info))  // Must pass
            .join(Result::combine)
            .run();
    });

// After: Automatic propagation
static final ScopedValue<RequestInfo> REQUEST_INFO = ScopedValue.newInstance();

VTask<Result> process = Scope.<Data>allSucceed()
    .fork(() -> {
        RequestInfo info = REQUEST_INFO.get();  // Available automatically
        return fetch1(info);
    })
    .fork(() -> {
        RequestInfo info = REQUEST_INFO.get();  // Available automatically
        return fetch2(info);
    })
    .fork(() -> {
        RequestInfo info = REQUEST_INFO.get();  // Available automatically
        return fetch3(info);
    })
    .join(Result::combine);

// Usage
Result result = ScopedValue
    .where(REQUEST_INFO, requestInfo)
    .call(() -> process.run());
```

### From Context to ConfigContext

If you're using `Context` for static configuration that doesn't need thread propagation, `ConfigContext` may be simpler:

```java
// Before: ScopedValue for static config (overkill)
static final ScopedValue<DatabaseConfig> DB_CONFIG = ScopedValue.newInstance();

VTask<User> getUser = VTask.delay(() -> {
    DatabaseConfig config = DB_CONFIG.get();
    return userRepo.find(config);
});

// Must bind everywhere
ScopedValue.where(DB_CONFIG, config).call(() -> getUser.run());

// After: ConfigContext for static config (appropriate)
ConfigContext<IOKind.Witness, DatabaseConfig, User> getUser =
    ConfigContext.io(config -> userRepo.find(config));

// Pass once
User user = getUser.runWithSync(config);
```

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Using ConfigContext for Request Data

```java
// ❌ Bad: Request data via ConfigContext
ConfigContext<IOKind.Witness, RequestInfo, Response> handler = ...;

// Problem: Every forked task needs explicit passing
// Problem: Easy to forget, causing bugs
```

### Anti-Pattern 2: Using Context for Static Config

```java
// ❌ Bad: Static config via ScopedValue
static final ScopedValue<DatabaseUrl> DB_URL = ScopedValue.newInstance();

// Problem: Must bind at every entry point
// Problem: No benefit from thread inheritance for static data
// Problem: More boilerplate than necessary
```

### Anti-Pattern 3: Mixing Indiscriminately

```java
// ❌ Bad: Some request data in ConfigContext, some in Context
// Inconsistent, confusing, easy to make mistakes

// ✓ Good: Clear separation
// - All request-scoped data: Context (ScopedValue)
// - All app-level config: ConfigContext (or constructor injection)
```

---

## Summary

| Use Case | Tool | Why |
|----------|------|-----|
| Request trace/correlation IDs | Context | Must propagate to forked tasks |
| User authentication/security | Context | Must propagate to forked tasks |
| Request locale/timezone | Context | Must propagate to forked tasks |
| Tenant ID (multi-tenant) | Context | Must propagate to forked tasks |
| Database connection config | ConfigContext | Static, no propagation needed |
| API endpoints/URLs | ConfigContext | Static, no propagation needed |
| Feature flags | ConfigContext | Static, no propagation needed |
| Logging configuration | ConfigContext | Static, no propagation needed |

**The rule of thumb:**
- **Changes per-request** and **needs thread inheritance** → `Context`
- **Set at startup** and **passed through call chain** → `ConfigContext`

~~~admonish tip title="See Also"
- [Context Effect](../monads/context_scoped.md) -- Core Context documentation
- [RequestContext Patterns](context_request.md) -- Request tracing and metadata
- [SecurityContext Patterns](context_security.md) -- Authentication and authorisation
- [ConfigContext](effect_contexts_config.md) -- Configuration dependency injection
- [Effect Contexts Overview](effect_contexts.md) -- Layer 2 effect wrappers
~~~

---

**Previous:** [SecurityContext Patterns](context_security.md)
**Next:** [Advanced Topics](advanced_topics.md)
