# ConfigContext: Dependency Injection Without the Framework

> *"The onlyes power is no power."*
>
> -- Russell Hoban, *Riddley Walker*

Hoban's aphorism hints at a paradox: sometimes the most powerful abstractions are those that feel like nothing at all. `ConfigContext` provides dependency injection that doesn't feel like a framework: no annotations, no containers, no reflection. Dependencies flow through your code as naturally as function arguments, but without cluttering every signature.

~~~admonish info title="What You'll Learn"
- Threading configuration through effectful computations
- Accessing the environment with `ask()` and projecting parts with `map()`
- Chaining dependent operations that all read the same config
- Locally modifying configuration with `local()`
- Adapting contexts to different config types with `contramap()`
~~~

---

## The Problem

Consider a service that needs configuration:

```java
public class ReportService {
    public Report generate(ReportConfig config, UserId userId) {
        User user = userService.fetch(config.userServiceUrl(), config.timeout(), userId);
        List<Order> orders = orderService.fetch(config.orderServiceUrl(), config.timeout(), user);
        return buildReport(config.format(), user, orders);
    }
}
```

The `config` parameter appears everywhere. Every method in the call chain needs it, even if it only uses one field. Signatures become cluttered. Testing requires constructing complete config objects even when you only care about one aspect.

Dependency injection frameworks solve this with containers and annotations, but those come with their own complexity: lifecycle management, circular dependency detection, runtime magic.

---

## The Solution

`ConfigContext` threads configuration implicitly:

```java
public ConfigContext<IOKind.Witness, ReportConfig, Report> generate(UserId userId) {
    return ConfigContext.<ReportConfig>ask()
        .via(config -> fetchUser(userId))       // Config available inside
        .via(user -> fetchOrders(user))         // Config still available
        .via(orders -> buildReport(orders));    // And here
}

// Execute by providing config once at the edge
Report report = generate(userId).runWithSync(config);
```

Configuration is declared once. Every computation in the chain can access it. No parameters threaded through signatures.

---

## Creating ConfigContexts

### ask: Access the Entire Configuration

`ask()` yields the configuration itself:

```java
ConfigContext<IOKind.Witness, AppConfig, AppConfig> config = ConfigContext.ask();

// Then project what you need
ConfigContext<IOKind.Witness, AppConfig, String> apiUrl =
    ConfigContext.<AppConfig>ask()
        .map(AppConfig::apiUrl);
```

### io: Compute Using Configuration

When your computation depends on config:

```java
ConfigContext<IOKind.Witness, DbConfig, Connection> connection =
    ConfigContext.io(config -> DriverManager.getConnection(
        config.url(),
        config.username(),
        config.password()
    ));
```

The function receives the configuration and returns a value. Execution is immediate when the context runs.

### ioDeferred: Defer Computation

When you want the IO aspect to be truly deferred:

```java
ConfigContext<IOKind.Witness, ApiConfig, Response> response =
    ConfigContext.ioDeferred(config -> () -> {
        // This supplier is invoked later when runWith() is called
        return httpClient.get(config.endpoint());
    });
```

### pure: Ignore Configuration

For values that don't need the config:

```java
ConfigContext<IOKind.Witness, AnyConfig, Integer> fortyTwo =
    ConfigContext.pure(42);
```

---

## Transforming Values

### map: Transform the Result

```java
ConfigContext<IOKind.Witness, ServiceConfig, String> endpoint =
    ConfigContext.<ServiceConfig>ask()
        .map(ServiceConfig::baseUrl);

ConfigContext<IOKind.Witness, ServiceConfig, URI> uri =
    endpoint.map(URI::create);
```

---

## Chaining Computations

### via: Chain Dependent Operations

```java
record AppConfig(String userServiceUrl, String orderServiceUrl, Duration timeout) {}

ConfigContext<IOKind.Witness, AppConfig, Invoice> invoice =
    ConfigContext.<AppConfig>ask()
        .via(config -> fetchUser(config.userServiceUrl()))
        .via(user -> fetchOrders(user.id()))
        .via(orders -> createInvoice(orders));

private ConfigContext<IOKind.Witness, AppConfig, User> fetchUser(String url) {
    return ConfigContext.io(config -> userClient.fetch(url, config.timeout()));
}

private ConfigContext<IOKind.Witness, AppConfig, List<Order>> fetchOrders(UserId id) {
    return ConfigContext.io(config -> orderClient.fetch(config.orderServiceUrl(), id));
}
```

Each step has access to the same configuration. The config flows through without explicit passing.

### flatMap: Type-Preserving Chain

```java
ConfigContext<IOKind.Witness, AppConfig, Report> report =
    getUser()
        .flatMap(user -> getOrders(user))
        .flatMap(orders -> generateReport(orders));
```

### then: Sequence Ignoring Values

```java
ConfigContext<IOKind.Witness, Config, String> workflow =
    logStart()
        .then(() -> doWork())
        .then(() -> logComplete())
        .then(() -> ConfigContext.pure("done"));
```

---

## Modifying Configuration Locally

### local: Temporary Config Override

Sometimes a sub-computation needs modified settings:

```java
ConfigContext<IOKind.Witness, ApiConfig, Data> withLongerTimeout =
    fetchData()
        .local(config -> config.withTimeout(Duration.ofMinutes(5)));
```

The modified config applies only to this computation. Other computations in the same chain see the original.

### Pattern: Feature Flags

```java
record AppConfig(boolean debugMode, int maxRetries) {}

ConfigContext<IOKind.Witness, AppConfig, Result> withDebugMode =
    processData()
        .local(config -> new AppConfig(true, config.maxRetries()));
```

### Pattern: Environment-Specific Settings

```java
ConfigContext<IOKind.Witness, ServiceConfig, Response> callService =
    ConfigContext.io(config -> httpClient.call(config.endpoint()));

// In tests, use a test environment
ConfigContext<IOKind.Witness, ServiceConfig, Response> testCall =
    callService.local(config -> config.withEndpoint("http://localhost:8080"));
```

---

## Adapting to Different Config Types

### contramap: Transform the Required Config

When composing code with different config types:

```java
record GlobalConfig(DatabaseConfig db, ApiConfig api) {}

// This needs DatabaseConfig
ConfigContext<IOKind.Witness, DatabaseConfig, Connection> dbConnection = ...;

// Adapt to work with GlobalConfig
ConfigContext<IOKind.Witness, GlobalConfig, Connection> adapted =
    dbConnection.contramap(GlobalConfig::db);
```

`contramap` transforms the *input* (the config), allowing you to compose contexts that expect different configuration types.

### Pattern: Modular Configuration

```java
// User module expects UserConfig
ConfigContext<IOKind.Witness, UserConfig, User> fetchUser = ...;

// Order module expects OrderConfig
ConfigContext<IOKind.Witness, OrderConfig, Order> fetchOrder = ...;

// Application config combines both
record AppConfig(UserConfig userConfig, OrderConfig orderConfig) {}

// Adapt both to AppConfig
ConfigContext<IOKind.Witness, AppConfig, User> appUser =
    fetchUser.contramap(AppConfig::userConfig);

ConfigContext<IOKind.Witness, AppConfig, Order> appOrder =
    fetchOrder.contramap(AppConfig::orderConfig);

// Now they can be chained
ConfigContext<IOKind.Witness, AppConfig, Invoice> invoice =
    appUser.via(user -> appOrder.map(order -> createInvoice(user, order)));
```

---

## Execution

### runWith: Get an IOPath

```java
ConfigContext<IOKind.Witness, AppConfig, Report> reportCtx = generateReport();

// Provide config, get IOPath
AppConfig config = loadConfig();
IOPath<Report> ioPath = reportCtx.runWith(config);

// Execute when ready
Report report = ioPath.unsafeRun();
```

### runWithSync: Immediate Execution

For synchronous code:

```java
Report report = reportCtx.runWithSync(config);
```

This is equivalent to `runWith(config).unsafeRun()`.

---

## Real-World Patterns

### Service Layer with Configuration

```java
public class OrderService {
    public ConfigContext<IOKind.Witness, ServiceConfig, Order> createOrder(OrderRequest request) {
        return validateRequest(request)
            .via(valid -> checkInventory(valid))
            .via(checked -> processPayment(checked))
            .via(paid -> saveOrder(paid));
    }

    private ConfigContext<IOKind.Witness, ServiceConfig, ValidatedRequest> validateRequest(
            OrderRequest request) {
        return ConfigContext.io(config ->
            validator.validate(request, config.validationRules()));
    }

    private ConfigContext<IOKind.Witness, ServiceConfig, CheckedRequest> checkInventory(
            ValidatedRequest request) {
        return ConfigContext.io(config ->
            inventoryClient.check(config.inventoryServiceUrl(), request.items()));
    }

    // ... similar for other methods
}

// At the application edge
ServiceConfig config = loadConfig();
Order order = orderService.createOrder(request).runWithSync(config);
```

### Database Access

```java
record DbConfig(String url, String user, String password, int poolSize) {}

public class UserRepository {
    public ConfigContext<IOKind.Witness, DbConfig, User> findById(UserId id) {
        return ConfigContext.io(config -> {
            try (Connection conn = getConnection(config)) {
                return queryUser(conn, id);
            }
        });
    }

    private Connection getConnection(DbConfig config) {
        return DriverManager.getConnection(config.url(), config.user(), config.password());
    }
}
```

### Testability

```java
// Production config
DbConfig prodConfig = new DbConfig(
    "jdbc:postgresql://prod-db:5432/app",
    "app_user",
    prodPassword,
    20
);

// Test config
DbConfig testConfig = new DbConfig(
    "jdbc:h2:mem:test",
    "sa",
    "",
    1
);

// Same code, different config
User prodUser = repository.findById(id).runWithSync(prodConfig);
User testUser = repository.findById(id).runWithSync(testConfig);
```

### Combining with ErrorContext

```java
public ConfigContext<IOKind.Witness, ApiConfig, ErrorContext<IOKind.Witness, ApiError, User>>
        fetchUser(UserId id) {
    return ConfigContext.io(config -> {
        return ErrorContext.<ApiError, User>io(
            () -> httpClient.get(config.userEndpoint() + "/" + id),
            ApiError::fromException);
    });
}
```

---

## Escape Hatch

When you need the raw transformer:

```java
ConfigContext<IOKind.Witness, AppConfig, String> ctx = ConfigContext.ask().map(AppConfig::name);

ReaderT<IOKind.Witness, AppConfig, String> transformer = ctx.toReaderT();
```

---

## Summary

| Operation | Purpose |
|-----------|---------|
| `ask()` | Access the entire configuration |
| `io(config -> value)` | Compute using configuration |
| `ioDeferred(config -> supplier)` | Deferred computation using config |
| `pure(value)` | Value ignoring configuration |
| `map(f)` | Transform the result |
| `via(f)` / `flatMap(f)` | Chain dependent computation |
| `local(f)` | Temporarily modify configuration |
| `contramap(f)` | Adapt to different config type |
| `runWith(config)` | Execute with provided configuration |
| `runWithSync(config)` | Execute synchronously |

`ConfigContext` embodies the paradox of invisible power. Dependencies flow through your code without ceremony, without frameworks, without the infrastructure that usually accompanies "enterprise" patterns. The only power is no power: the power that feels like nothing at all.

~~~admonish tip title="See Also"
- [ReaderT Transformer](../transformers/readert_transformer.md) - The underlying transformer
- [Reader Monad](../monads/reader_monad.md) - The Reader type
- [Advanced Effects](advanced_effects.md) - ReaderPath for simpler Reader usage
~~~

---

**Previous:** [Optional Contexts](effect_contexts_optional.md)
**Next:** [MutableContext](effect_contexts_mutable.md)
