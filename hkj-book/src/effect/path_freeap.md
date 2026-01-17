# FreeApPath

`FreeApPath<F, A>` wraps `FreeAp<F, A>` for building **applicative DSLs**.
Unlike `FreePath`, operations in `FreeApPath` can be analyzed and potentially
executed in parallel because they don't depend on each other's results.

~~~admonish info title="What You'll Learn"
- Creating FreeApPath instances
- Difference from FreePath
- Static analysis of programs
- Parallel execution
- When to use (and when not to)
~~~

---

## The Key Difference

`FreePath` (monadic): Each operation can depend on previous results.
`FreeApPath` (applicative): Operations are independent; results combine at the end.

```java
// FreePath: second operation depends on first
FreePath<F, String> monadic = getUser(id).via(user ->
    getOrders(user.id()));  // Sequential: must wait for user

// FreeApPath: operations are independent
FreeApPath<F, Summary> applicative =
    getUser(id).zipWith(getOrders(id), Summary::new);  // Parallel-safe!
```

---

## Creation

```java
// Pure value
FreeApPath<ConfigOp.Witness, String> pure = Path.freeApPure("default");

// Lift an operation
FreeApPath<ConfigOp.Witness, String> dbUrl =
    Path.freeApLift(new GetConfig("database.url"));
```

---

## Core Operations

```java
FreeApPath<ConfigOp.Witness, String> host = getConfig("host");
FreeApPath<ConfigOp.Witness, Integer> port = getConfig("port").map(Integer::parseInt);

// Combine independent operations
FreeApPath<ConfigOp.Witness, String> url =
    host.zipWith(port, (h, p) -> "http://" + h + ":" + p);

// Map over results
FreeApPath<ConfigOp.Witness, String> upper = host.map(String::toUpperCase);
```

---

## Static Analysis

Because operations are independent, you can analyze programs before running them:

```java
// Collect all config keys that will be requested
Set<String> getRequestedKeys(FreeAp<ConfigOp.Witness, ?> program) {
    return program.analyze(op -> {
        GetConfig config = ConfigOpHelper.narrow(op);
        return Set.of(config.key());
    }, Monoids.set());
}

FreeApPath<ConfigOp.Witness, DbConfig> program =
    getConfig("db.host")
        .zipWith(getConfig("db.port"), DbConfig::new);

Set<String> keys = getRequestedKeys(program.run());
// Set.of("db.host", "db.port")
```

This enables:
- Validation before execution
- Optimization (batching, caching)
- Documentation generation
- Dependency analysis

---

## Parallel Execution

Interpreters can exploit independence for parallelism:

```java
// Sequential interpreter
NaturalTransformation<ConfigOp.Witness, IO.Witness> sequential =
    op -> IO.of(() -> loadConfig(op.key()));

// Parallel interpreter (batch all requests)
Kind<IO.Witness, Config> parallel = program.run().foldMap(
    batchingInterpreter,
    ioApplicative
);
```

---

## Running Programs

```java
FreeApPath<ConfigOp.Witness, DbConfig> program =
    getConfig("host").zipWith(getConfig("port"), DbConfig::new);

// Get the FreeAp structure
FreeAp<ConfigOp.Witness, DbConfig> freeAp = program.run();

// Interpret
Kind<IO.Witness, DbConfig> io = freeAp.foldMap(interpreter, ioApplicative);

// Execute
DbConfig config = IOKindHelper.narrow(io).unsafeRunSync();
```

---

## When to Use

`FreeApPath` is right when:
- Operations are **independent** (don't depend on each other's results)
- You want to analyze programs before running (static analysis)
- Parallel/batched execution is beneficial
- Building configuration loaders, query builders, validation pipelines

`FreeApPath` is wrong when:
- Operations depend on previous results → use [FreePath](path_free.md)
- You don't need static analysis or parallelism
- Simpler direct effects suffice → use [IOPath](path_io.md)

~~~admonish example title="Configuration Loading"
```java
// Define config operations
FreeApPath<ConfigOp.Witness, String> dbHost = getConfig("db.host");
FreeApPath<ConfigOp.Witness, Integer> dbPort = getConfig("db.port").map(Integer::parseInt);
FreeApPath<ConfigOp.Witness, String> dbName = getConfig("db.name");

// Combine into complete config (all three fetched independently)
FreeApPath<ConfigOp.Witness, DbConfig> dbConfig =
    dbHost.zipWith3(dbPort, dbName, DbConfig::new);

// Analyze: what keys are needed?
Set<String> keys = analyze(dbConfig);  // {db.host, db.port, db.name}

// Execute: fetch all in parallel/batch
DbConfig config = run(dbConfig, parallelInterpreter);
```
~~~

~~~admonish tip title="Applicative vs Monad"
Applicative is less powerful than Monad (you can't use previous results
to decide the next operation), but this limitation is a feature: it enables
static analysis and parallelism that monads cannot provide.
~~~

~~~admonish tip title="See Also"
- [Free Applicative](../monads/free_applicative.md) - Underlying type for FreeApPath
- [FreePath](path_free.md) - Monadic variant for dependent operations
- [Applicative](../typeclasses/applicative.md) - The Applicative typeclass
~~~

---

**Previous:** [FreePath](path_free.md)
**Next:** [VTaskPath](path_vtask.md)
