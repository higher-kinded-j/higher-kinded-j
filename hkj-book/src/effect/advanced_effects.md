# Advanced Effects

> *"We are surrounded by huge institutions we can never penetrate... They've
> made themselves user-friendly, but they define the tastes to which we conform.
> They're rather subtle, subservient tyrannies, but no less sinister for that."*
>
> -- J.G. Ballard

Ballard was describing the modern landscape of invisible systems: banks,
networks, bureaucracies that shape our choices while remaining opaque. Software
faces the same challenge. Configuration systems, database connections, logging
infrastructure. These are the "institutions" your code must navigate. They're
everywhere, they're necessary, and handling them explicitly at every call site
creates clutter that obscures your actual logic.

This chapter introduces three effect types that model these pervasive concerns:
**Reader** for environment access, **State** for threaded computation state, and
**Writer** for accumulated output. Each represents a different kind of
computational context that you'd otherwise pass explicitly through every
function signature.

~~~admonish info title="What You'll Learn"
- `ReaderPath` for dependency injection and environment access
- `WithStatePath` for computations with mutable state
- `WriterPath` for logging and accumulated output
- How to compose these effects with other Path types
- Patterns for real-world use: configuration, audit trails, and state machines
~~~

~~~admonish example title="See Example Code"
- [AdvancedEffectsExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/AdvancedEffectsExample.java) - ReaderPath, WithStatePath, and WriterPath demonstrations
~~~

~~~admonish warning title="Advanced Feature"
The Reader, State, and Writer Path types are an advanced part of the Effect
Path API. They build on the core Path types covered earlier and require
familiarity with those foundations.
~~~

---

## ReaderPath: The Environment You Inherit

`ReaderPath<R, A>` wraps `Reader<R, A>`, representing a computation that
needs access to an environment of type `R` to produce a value of type `A`.

Think of it as **implicit parameter passing**. Instead of threading a
`Config` or `DatabaseConnection` through every method signature, you
describe computations that *assume* the environment exists, then provide
it once at the edge of your system.

### Why Reader?

Consider a typical service method:

```java
// Without Reader: environment threaded explicitly
public User getUser(String id, DbConnection db, Config config, Logger log) {
    log.debug("Fetching user: " + id);
    int timeout = config.getTimeout();
    return db.query("SELECT * FROM users WHERE id = ?", id);
}
```

Every function in the call chain needs these parameters. The signatures
become cluttered; the actual logic is buried.

With Reader:

```java
// With Reader: environment is implicit
public ReaderPath<AppEnv, User> getUser(String id) {
    return ReaderPath.ask()
        .via(env -> {
            env.logger().debug("Fetching user: " + id);
            return ReaderPath.pure(
                env.db().query("SELECT * FROM users WHERE id = ?", id)
            );
        });
}
```

The environment is accessed when needed but not passed explicitly. The
method signature shows what it *computes*, not what it *requires*.

### Creation

```java
// Pure value (ignores environment)
ReaderPath<Config, String> pure = ReaderPath.pure("hello");

// Access the environment
ReaderPath<Config, Config> askAll = ReaderPath.ask();

// Project part of the environment
ReaderPath<Config, String> dbUrl = ReaderPath.asks(Config::databaseUrl);

// From a Reader function
ReaderPath<Config, Integer> timeout = ReaderPath.of(config -> config.timeout());
```

### Core Operations

```java
ReaderPath<Config, String> dbUrl = ReaderPath.asks(Config::databaseUrl);

// Transform
ReaderPath<Config, Integer> urlLength = dbUrl.map(String::length);

// Chain dependent computations
ReaderPath<Config, Connection> connection =
    dbUrl.via(url -> ReaderPath.of(config ->
        DriverManager.getConnection(url, config.username(), config.password())
    ));
```

### Running a Reader

Eventually you must provide the environment:

```java
Config config = loadConfig();

ReaderPath<Config, User> userPath = getUser("123");
User user = userPath.run(config);  // Provide environment here
```

The Reader executes with the given environment. All `ask` and `asks` calls
within the computation receive this environment.

### Local Environment Modification

Sometimes a sub-computation needs a modified environment:

```java
ReaderPath<Config, Result> withTestMode =
    computation.local(config -> config.withTestMode(true));
```

The inner computation sees the modified environment; the outer computation
is unaffected.

### When to Use ReaderPath

`ReaderPath` is right when:
- Multiple functions need the same "context" (config, connection, logger)
- You want dependency injection without frameworks
- Computations should be testable with different environments
- You're building a DSL where environment is implicit

`ReaderPath` is wrong when:
- The environment changes during computation: use `StatePath`
- You need to accumulate results: use `WriterPath`
- The environment is only needed in one place: just pass it directly

### ReaderPath vs Spring Dependency Injection

If you use Spring Boot, you already have a dependency injection mechanism.
`ReaderPath` solves a similar problem in a different way. Understanding
where each approach shines helps you choose the right tool.

| Aspect | Spring DI (`@Autowired` / constructor) | `ReaderPath<R, A>` |
|--------|----------------------------------------|---------------------|
| **Provide a dependency** | Container wires it at startup | `.run(environment)` at the call-site edge |
| **Access a dependency** | Field or constructor parameter | `ReaderPath.ask()` / `ReaderPath.asks(R::field)` |
| **Scope** | Container-managed (singleton, request, etc.) | Explicit; the caller decides what to pass |
| **Swapping for tests** | `@MockBean`, `@TestConfiguration`, or a test profile | Pass a different environment value |
| **Composition** | Inject service A into service B | `readerA.via(a -> readerB)` chains readers |
| **Runtime variation** | Profiles, `@ConditionalOnProperty` | `reader.local(env -> env.withFeatureFlag(true))` |

**When Spring DI is the better fit:**

- Application-scoped singletons (database pools, HTTP clients, caches)
- Framework-managed lifecycle (startup, shutdown hooks)
- Wiring that is fixed for the lifetime of the application

**When ReaderPath adds value:**

- Per-request or per-tenant context that varies at runtime (tenant ID,
  correlation ID, feature flags, auth principal)
- Pure computation pipelines where you want to defer the environment
  until the last moment
- Testing without a Spring context; just pass a record

**Example: per-request context**

With Spring DI alone, per-request context typically requires a
`@RequestScope` bean or `ThreadLocal`. With `ReaderPath`, the context
flows through the computation explicitly:

```java
// Define the request-scoped environment
record RequestEnv(String tenantId, String correlationId, DataSource ds) {}

// Service method: no framework annotations needed
public ReaderPath<RequestEnv, List<Order>> ordersForTenant() {
    return ReaderPath.asks(RequestEnv::tenantId)
        .zipWith(ReaderPath.asks(RequestEnv::ds), (tenantId, ds) -> queryOrders(ds, tenantId));
}

// At the controller edge, provide the environment once
@GetMapping("/orders")
public List<Order> getOrders(HttpServletRequest request) {
    RequestEnv env = new RequestEnv(
        request.getHeader("X-Tenant-Id"),
        request.getHeader("X-Correlation-Id"),
        dataSource
    );
    return ordersForTenant().run(env);
}
```

The computation is pure and testable; the environment is assembled once
at the boundary.

~~~admonish tip title="See Also"
- [Spring Boot Integration](../spring/spring_boot_integration.md) - Using Effect Path types as controller return values
- [ReaderT Transformer](../transformers/readert_transformer.md) - The raw transformer behind ReaderPath
~~~

---

## StatePath: Computation with Memory

`StatePath<S, A>` wraps `State<S, A>`, representing a computation that
threads state through a sequence of operations. Each step can read the
current state, produce a value, and update the state for subsequent steps.

Unlike mutable state, `StatePath` keeps everything pure: the "mutation"
is actually a transformation that produces new state values.

### Why State?

Consider tracking statistics through a pipeline:

```java
// Without State: manual state threading
Stats stats1 = new Stats();
ResultA a = processA(input, stats1);
Stats stats2 = stats1.incrementProcessed();
ResultB b = processB(a, stats2);
Stats stats3 = stats2.incrementProcessed();
// ... and so on
```

With State:

```java
// With State: automatic threading
StatePath<Stats, ResultC> pipeline =
    StatePath.of(processA(input))
        .via(a -> StatePath.modify(Stats::incrementProcessed)
            .then(() -> StatePath.of(processB(a))))
        .via(b -> StatePath.modify(Stats::incrementProcessed)
            .then(() -> StatePath.of(processC(b))));

Tuple2<Stats, ResultC> result = pipeline.run(Stats.initial());
```

The state threads through automatically. Each step can read it, modify it,
or ignore it.

### Creation

```java
// Pure value (state unchanged)
StatePath<Counter, String> pure = StatePath.pure("hello");

// Get current state
StatePath<Counter, Counter> current = StatePath.get();

// Set new state (discards old)
StatePath<Counter, Unit> reset = StatePath.set(Counter.zero());

// Modify state
StatePath<Counter, Unit> increment = StatePath.modify(Counter::increment);

// Get and modify in one step
StatePath<Counter, Integer> getAndIncrement =
    StatePath.getAndModify(counter -> {
        int value = counter.value();
        return Tuple.of(counter.increment(), value);
    });
```

### Core Operations

```java
StatePath<Counter, Integer> current = StatePath.get().map(Counter::value);

// Chain with state threading
StatePath<Counter, String> counted =
    StatePath.modify(Counter::increment)
        .then(() -> StatePath.get())
        .map(c -> "Count: " + c.value());

// Combine independent state operations
StatePath<Counter, Result> combined =
    operationA.zipWith(operationB, Result::new);
```

### Running State

```java
Counter initial = Counter.zero();

StatePath<Counter, String> computation = ...;

// Get both final state and result
Tuple2<Counter, String> both = computation.run(initial);

// Get just the result
String result = computation.eval(initial);

// Get just the final state
Counter finalState = computation.exec(initial);
```

### When to Use StatePath

`StatePath` is right when:
- You need to accumulate or track information through a computation
- Multiple operations must coordinate through shared state
- You're implementing state machines or interpreters
- You want mutable-like semantics with immutable guarantees

`StatePath` is wrong when:
- State never changes: use `ReaderPath`
- You're accumulating a log rather than replacing state: use `WriterPath`
- The state is external (database, file): use `IOPath`

---

## WriterPath: Accumulating Output

`WriterPath<W, A>` wraps `Writer<W, A>`, representing a computation that
produces both a value and accumulated output. The output (type `W`) is
combined using a `Monoid`, allowing automatic aggregation of logs, metrics,
or any combinable data.

### Why Writer?

Consider building an audit trail:

```java
// Without Writer: manual log passing
public Tuple2<List<String>, User> createUser(UserInput input, List<String> log) {
    List<String> log2 = append(log, "Validating input");
    Validated validated = validate(input);
    List<String> log3 = append(log2, "Creating user record");
    User user = repository.save(validated);
    List<String> log4 = append(log3, "User created: " + user.id());
    return Tuple.of(log4, user);
}
```

With Writer:

```java
// With Writer: automatic log accumulation
public WriterPath<List<String>, User> createUser(UserInput input) {
    return WriterPath.tell(List.of("Validating input"))
        .then(() -> WriterPath.pure(validate(input)))
        .via(validated -> WriterPath.tell(List.of("Creating user record"))
            .then(() -> WriterPath.pure(repository.save(validated))))
        .via(user -> WriterPath.tell(List.of("User created: " + user.id()))
            .map(unit -> user));
}
```

The log accumulates automatically. No explicit threading required.

### Creation

```java
// Pure value (empty log)
WriterPath<List<String>, Integer> pure = WriterPath.pure(42, Monoids.list());

// Write to log (no value)
WriterPath<List<String>, Unit> logged =
    WriterPath.tell(List.of("Something happened"), Monoids.list());

// Create with both value and log
WriterPath<List<String>, User> withLog =
    WriterPath.of(user, List.of("Created user"), Monoids.list());
```

The `Monoid<W>` parameter defines how log entries combine:
- `Monoids.list()`: concatenate lists
- `Monoids.string()`: concatenate strings
- Custom monoids for metrics, events, etc.

### Core Operations

```java
WriterPath<List<String>, Integer> computation = ...;

// Transform value (log unchanged)
WriterPath<List<String>, String> formatted = computation.map(n -> "Value: " + n);

// Add to log
WriterPath<List<String>, Integer> withExtra =
    computation.tell(List.of("Extra info"));

// Chain with log accumulation
WriterPath<List<String>, Result> pipeline =
    stepOne()
        .via(a -> stepTwo(a))
        .via(b -> stepThree(b));
// Logs from all three steps combine automatically
```

### Running Writer

```java
WriterPath<List<String>, User> computation = createUser(input);

// Get both log and result
Tuple2<List<String>, User> both = computation.run();

// Get just the result
User user = computation.value();

// Get just the log
List<String> log = computation.written();
```

### When to Use WriterPath

`WriterPath` is right when:
- You're building audit trails or structured logs
- Accumulating metrics or statistics
- Collecting warnings or diagnostics alongside computation
- Any scenario where output should aggregate, not replace

`WriterPath` is wrong when:
- Output should replace previous output: use `StatePath`
- You need to read accumulated output mid-computation: use `StatePath`
- Output goes to external systems: use `IOPath`

---

## Combining Advanced Effects

These effect types compose with each other and with the core Path types.

### Reader + Either: Environment with Errors

```java
// A computation that needs config and might fail
ReaderPath<Config, EitherPath<Error, User>> getUser(String id) {
    return ReaderPath.asks(Config::database)
        .map(db -> Path.either(db.findUser(id))
            .toEitherPath(() -> new Error.NotFound(id)));
}
```

### State + Writer: State with Logging

```java
// Track state and log what happens
public StatePath<GameState, WriterPath<List<Event>, Move>> makeMove(Position pos) {
    return StatePath.get()
        .via(state -> {
            Move move = calculateMove(state, pos);
            GameState newState = state.apply(move);
            return StatePath.set(newState)
                .map(unit -> WriterPath.of(
                    move,
                    List.of(new Event.MoveMade(pos, move)),
                    Monoids.list()
                ));
        });
}
```

### Patterns: Configuration Service

```java
public class ConfigurableService {
    public ReaderPath<ServiceConfig, EitherPath<Error, Result>> process(Request req) {
        return ReaderPath.ask()
            .via(config -> {
                if (!config.isEnabled()) {
                    return ReaderPath.pure(Path.left(new Error.ServiceDisabled()));
                }
                return ReaderPath.pure(
                    Path.tryOf(() -> doProcess(req, config))
                        .toEitherPath(Error.ProcessingFailed::new)
                );
            });
    }
}

// Usage
ServiceConfig config = loadConfig();
EitherPath<Error, Result> result = service.process(request).run(config);
```

### Patterns: Audit Trail

```java
public class AuditedRepository {
    public WriterPath<List<AuditEvent>, EitherPath<Error, User>> saveUser(User user) {
        return WriterPath.tell(List.of(new AuditEvent.AttemptSave(user.id())))
            .then(() -> {
                Either<Error, User> result = repository.save(user);
                if (result.isRight()) {
                    return WriterPath.of(
                        Path.right(result.getRight()),
                        List.of(new AuditEvent.SaveSucceeded(user.id())),
                        Monoids.list()
                    );
                } else {
                    return WriterPath.of(
                        Path.left(result.getLeft()),
                        List.of(new AuditEvent.SaveFailed(user.id(), result.getLeft())),
                        Monoids.list()
                    );
                }
            });
    }
}
```

---

## Summary

| Effect Type | Models | Key Operations | Use Case |
|-------------|--------|----------------|----------|
| `ReaderPath<R, A>` | Environment access | `ask`, `asks`, `local` | Config, DI |
| `StatePath<S, A>` | Threaded state | `get`, `set`, `modify` | Counters, state machines |
| `WriterPath<W, A>` | Accumulated output | `tell`, `written` | Logging, audit trails |

These effects handle the "invisible institutions" of software: the
configuration that's everywhere, the state that threads through, the
logs that accumulate. By making them explicit in the type system, you
gain the same composability and predictability that the core Path types
provide for error handling.

The systems remain subtle and pervasive, but no longer tyrannical.

~~~admonish tip title="See Also"
- [Reader Monad](../monads/reader_monad.md) - The underlying type for ReaderPath
- [State Monad](../monads/state_monad.md) - The underlying type for StatePath
- [Writer Monad](../monads/writer_monad.md) - The underlying type for WriterPath
- [Monad Transformers](../transformers/transformers.md) - Combining multiple effects
~~~

---

**Previous:** [Common Compiler Errors](compiler_errors.md)
**Next:** [Effect Contexts](effect_contexts.md)
