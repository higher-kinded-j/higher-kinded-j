# FreeApPath

`FreeApPath<F, A>` wraps `FreeAp<F, A>` for building **applicative DSLs**.
Unlike `FreePath`, operations in `FreeApPath` can be analysed and potentially
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

<!-- verify -->
```java
// FreePath: the second operation depends on the first
FreePath<ConfigOp.Witness, String> monadic =
    readConfig("config.location").via(location ->
        readConfig(location));  // Sequential: must wait for the location

// FreeApPath: the operations are independent
FreeApPath<ConfigOp.Witness, DbConfig> applicative =
    getConfig("db.host").zipWith(
        getConfig("db.port").map(Integer::parseInt),
        DbConfig::new);  // Parallel-safe!
```

---

## Creation

<!-- verify -->
```java
// Pure value
FreeApPath<ConfigOp.Witness, String> pure =
    Path.freeApPure("default", configFunctor);

// Lift an operation
FreeApPath<ConfigOp.Witness, String> dbUrl =
    Path.freeApLift(new GetConfig<>("database.url", Function.identity()), configFunctor);
```

---

## Core Operations

<!-- verify -->
```java
FreeApPath<ConfigOp.Witness, String> host = getConfig("host");
FreeApPath<ConfigOp.Witness, Integer> port = getConfig("db.port").map(Integer::parseInt);

// Combine independent operations
FreeApPath<ConfigOp.Witness, String> url =
    host.zipWith(port, (h, p) -> "http://" + h + ":" + p);

// Map over results
FreeApPath<ConfigOp.Witness, String> upper = host.map(String::toUpperCase);
```

---

## Static Analysis

Because operations are independent, you can analyse programs before running them:

<!-- verify -->
```java
// Every key the program will request, without running any of it
Set<String> getRequestedKeys(FreeAp<ConfigOp.Witness, ?> program) {
    return FreeApAnalyzer.collectOperations(program).stream()
        .map(op -> ((GetConfig<?>) op).key())
        .collect(Collectors.toSet());
}

FreeApPath<ConfigOp.Witness, DbConfig> program =
    getConfig("db.host")
        .zipWith(getConfig("db.port").map(Integer::parseInt), DbConfig::new);

Set<String> keys = getRequestedKeys(program.toFreeAp());
// Set.of("db.host", "db.port")
```

This enables:
- Validation before execution
- Optimisation (batching, caching)
- Documentation generation
- Dependency analysis

---

## Parallel Execution

Interpreters can exploit independence for parallelism:

<!-- verify -->
```java
// Sequential interpreter. `apply` is generic over the operation's result type,
// so an interpreter is an anonymous class rather than a lambda.
Natural<ConfigOp.Witness, IO.Witness> sequential =
    new Natural<>() {
        @Override
        public <A> Kind<IO.Witness, A> apply(Kind<ConfigOp.Witness, A> fa) {
            return switch ((ConfigOp<A>) fa) {
                case GetConfig<A>(String key, Function<String, A> next) ->
                    IO.delay(() -> next.apply(loadConfig(key)));
            };
        }
    };

// Parallel interpreter (batch all requests)
FreeApPath<ConfigOp.Witness, DbConfig> program =
    getConfig("db.host").zipWith(getConfig("db.port").map(Integer::parseInt), DbConfig::new);

Kind<IO.Witness, DbConfig> parallel = program.toFreeAp().foldMap(
    batchingInterpreter,
    ioApplicative
);
```

---

## Running Programs

<!-- verify -->
```java
FreeApPath<ConfigOp.Witness, DbConfig> program =
    getConfig("host").zipWith(getConfig("db.port").map(Integer::parseInt), DbConfig::new);

// Get the FreeAp structure
FreeAp<ConfigOp.Witness, DbConfig> freeAp = program.toFreeAp();

// Interpret
Kind<IO.Witness, DbConfig> io = freeAp.foldMap(interpreter, ioApplicative);

// Execute
DbConfig config = IOKindHelper.IO_OP.narrow(io).unsafeRunSync();
```

---

## When to Use

`FreeApPath` is right when:
- Operations are **independent** (don't depend on each other's results)
- You want to analyse programs before running (static analysis)
- Parallel/batched execution is beneficial
- Building configuration loaders, query builders, validation pipelines

`FreeApPath` is wrong when:
- Operations depend on previous results → use [FreePath](path_free.md)
- You don't need static analysis or parallelism
- Simpler direct effects suffice → use [IOPath](path_io.md)

~~~admonish example title="Configuration Loading"
<!-- verify -->
```java
// Define config operations
FreeApPath<ConfigOp.Witness, String> dbHost = getConfig("db.host");
FreeApPath<ConfigOp.Witness, Integer> dbPort = getConfig("db.port").map(Integer::parseInt);
FreeApPath<ConfigOp.Witness, String> dbName = getConfig("db.name");

// Combine into complete settings (all three fetched independently)
FreeApPath<ConfigOp.Witness, DbSettings> settings =
    dbHost.zipWith3(dbPort, dbName, DbSettings::new);

// Analyse: what keys are needed?  {db.host, db.port, db.name}
Set<String> keys =
    FreeApAnalyzer.collectOperations(settings.toFreeAp()).stream()
        .map(op -> ((GetConfig<?>) op).key())
        .collect(Collectors.toSet());

// Execute: fetch all in parallel/batch
Kind<IO.Witness, DbSettings> loaded =
    settings.toFreeAp().foldMap(batchingInterpreter, ioApplicative);
DbSettings config = IOKindHelper.IO_OP.narrow(loaded).unsafeRunSync();
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
- [Applicative](../functional/applicative.md) - The Applicative typeclass
~~~

---

**Previous:** [FreePath](path_free.md)
**Next:** [VStreamPath](path_vstream.md)
