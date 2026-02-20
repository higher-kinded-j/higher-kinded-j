# Resource Management with Bracket Pattern
## _Safe Acquisition and Release for VTask_

~~~admonish info title="What You'll Learn"
- Using `Resource` for safe resource management in concurrent computations
- Creating resources from `AutoCloseable`, explicit acquire/release, and pure values
- Composing multiple resources with `flatMap` and `and`
- Adding finalizers for cleanup actions
- Integrating resources with `Scope` for concurrent resource management
~~~

> *"Resource acquisition is initialization... the point is to tie the lifecycle of a resource to the lifetime of a local object."*
> -- **Bjarne Stroustrup**, creator of C++, on the RAII pattern that inspired functional bracket semantics

~~~admonish example title="See Example Code"
[VTaskResourceExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/VTaskResourceExample.java)
~~~

The `Resource` type provides safe resource management for VTask computations, implementing the bracket pattern (acquire-use-release). Resources are always released, even when exceptions occur or tasks are cancelled.

```
┌──────────────────────────────────────────────────────────────────┐
│                    Resource Lifecycle                            │
│                                                                  │
│  ┌─────────┐    ┌───────────┐    ┌─────────┐                     │
│  │ Acquire │ →  │    Use    │ →  │ Release │  (guaranteed)       │
│  │ resource│    │ resource  │    │ resource│                     │
│  └─────────┘    └───────────┘    └─────────┘                     │
│                       │                ↑                         │
│                       └── on success ──┘                         │
│                       └── on failure ──┘                         │
│                       └── on cancel  ──┘                         │
└──────────────────────────────────────────────────────────────────┘
```

---

## Creating Resources

~~~admonish example title="Basic Resource Creation"

```java
import org.higherkindedj.hkt.vtask.Resource;
import org.higherkindedj.hkt.vtask.VTask;

// Create a Resource from AutoCloseable (most common pattern)
Resource<Connection> connResource = Resource.fromAutoCloseable(
    () -> dataSource.getConnection()
);

// Use the resource - automatically closed after use
VTask<List<User>> users = connResource.use(conn ->
    VTask.of(() -> userDao.findAll(conn))
);

// Run the task - resource is managed automatically
List<User> result = users.run();

// Create a Resource with explicit acquire/release
Resource<FileChannel> fileResource = Resource.make(
    () -> FileChannel.open(path, StandardOpenOption.READ),
    channel -> {
        try { channel.close(); }
        catch (Exception e) { /* log and ignore */ }
    }
);

// Use a pure value (no resource management needed)
Resource<Config> configResource = Resource.pure(loadedConfig);
```
~~~

### Factory Methods

| Method | Description | Use Case |
|--------|-------------|----------|
| `fromAutoCloseable(supplier)` | Wraps an `AutoCloseable` | Database connections, streams, channels |
| `make(acquire, release)` | Explicit acquire and release functions | Custom resources, locks, external handles |
| `pure(value)` | Wraps a value with no cleanup | Configuration, constants, pre-initialized values |

---

## Using Resources

The `use` method runs a computation with the acquired resource and guarantees release:

```java
Resource<Connection> connResource = Resource.fromAutoCloseable(
    () -> dataSource.getConnection()
);

// The function receives the acquired resource
// Release happens automatically when the VTask completes
VTask<Integer> count = connResource.use(conn ->
    VTask.of(() -> {
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            return rs.getInt(1);
        }
    })
);

// Resource is acquired when run() is called
// Resource is released when the computation completes (success or failure)
int userCount = count.run();
```

### Exception Safety

If the use function throws, the resource is still released:

```java
VTask<String> riskyOperation = connResource.use(conn ->
    VTask.of(() -> {
        if (someCondition) {
            throw new RuntimeException("Something went wrong");
        }
        return "Success";
    })
);

// Even though the computation throws, the connection is closed
Try<String> result = riskyOperation.runSafe();
// result.isFailure() == true
// connection is closed
```

---

## Composing Resources

Resources compose naturally, acquiring in order and releasing in reverse (LIFO):

```java
// Chain resource acquisition with flatMap
Resource<PreparedStatement> stmtResource = connResource.flatMap(conn ->
    Resource.make(
        () -> conn.prepareStatement(sql),
        PreparedStatement::close
    )
);

// Combine two independent resources with and()
Resource<Par.Tuple2<Connection, FileChannel>> combined =
    connResource.and(fileResource);

combined.use(tuple -> {
    Connection conn = tuple.first();
    FileChannel file = tuple.second();
    return VTask.of(() -> processData(conn, file));
}).run();
// fileResource released first, then connResource

// Combine three resources
Resource<Par.Tuple3<Connection, Statement, ResultSet>> triple =
    connResource.and(stmtResource, resultSetResource);

// Transform resource value with map
Resource<String> connectionInfo = connResource.map(conn ->
    conn.getMetaData().getURL()
);
```

### Composition Methods

| Method | Signature | Description |
|--------|-----------|-------------|
| `map(f)` | `Resource<A> → (A → B) → Resource<B>` | Transform the resource value |
| `flatMap(f)` | `Resource<A> → (A → Resource<B>) → Resource<B>` | Chain dependent resources |
| `and(other)` | `Resource<A> → Resource<B> → Resource<Tuple2<A,B>>` | Combine two resources |
| `and(r2, r3)` | `Resource<A> → Resource<B> → Resource<C> → Resource<Tuple3<A,B,C>>` | Combine three resources |

### Release Order

When composing resources, release order is the reverse of acquisition (LIFO):

```java
Resource<A> ra = Resource.make(acquireA, releaseA);
Resource<B> rb = Resource.make(acquireB, releaseB);
Resource<C> rc = Resource.make(acquireC, releaseC);

// Acquisition order: A, then B, then C
// Release order: C, then B, then A
Resource<Tuple3<A, B, C>> combined = ra.and(rb, rc);
```

This ensures that resources depending on other resources are released first.

---

## Resource Finalizers

Add cleanup actions that run after the primary release:

```java
Resource<Connection> withLogging = connResource
    .withFinalizer(() -> logger.info("Connection released"));

// Cleanup runs even if release throws
Resource<Lock> lockResource = Resource.make(
    () -> { lock.lock(); return lock; },
    Lock::unlock
).withFinalizer(() -> metrics.recordLockRelease());
```

### Finalizer Behaviour

- Finalizers run after the primary release function
- Multiple finalizers can be added (they run in reverse order of addition)
- If the primary release throws, finalizers still run
- If a finalizer throws, subsequent finalizers still run
- All exceptions are collected and suppressed on the original exception

```java
Resource<Handle> robust = Resource.make(acquire, release)
    .withFinalizer(() -> cleanupStep1())
    .withFinalizer(() -> cleanupStep2())
    .withFinalizer(() -> cleanupStep3());

// Execution order:
// 1. release()
// 2. cleanupStep3()  (most recently added)
// 3. cleanupStep2()
// 4. cleanupStep1()  (first added)
```

---

## Resource + Scope Integration

Resources work seamlessly with Scope for structured concurrent resource management:

```java
Resource<Connection> conn1 = Resource.fromAutoCloseable(() -> pool.getConnection());
Resource<Connection> conn2 = Resource.fromAutoCloseable(() -> pool.getConnection());

// Use resources within a scope
VTask<List<String>> parallelQueries = conn1.and(conn2).use(conns ->
    Scope.<String>allSucceed()
        .fork(VTask.of(() -> query(conns.first(), sql1)))
        .fork(VTask.of(() -> query(conns.second(), sql2)))
        .join()
);

// Both connections released after scope completes
List<String> results = parallelQueries.run();
```

### Real-World Example: Transaction with Multiple Resources

```java
Resource<Connection> connResource = Resource.make(
    () -> {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);
        return conn;
    },
    conn -> {
        try { conn.rollback(); } catch (Exception e) { /* ignore */ }
        try { conn.close(); } catch (Exception e) { /* ignore */ }
    }
);

VTask<OrderResult> processOrder = connResource.use(conn ->
    Scope.<Void>allSucceed()
        .fork(VTask.of(() -> { updateInventory(conn, order); return null; }))
        .fork(VTask.of(() -> { chargePayment(conn, order); return null; }))
        .fork(VTask.of(() -> { sendNotification(conn, order); return null; }))
        .join()
        .flatMap(_ -> VTask.of(() -> {
            conn.commit();
            return new OrderResult(order.id(), "SUCCESS");
        }))
);

// If any step fails:
// 1. Scope cancels remaining tasks
// 2. Connection release triggers rollback
// 3. Connection is closed
Try<OrderResult> result = processOrder.runSafe();
```

---

## Error Handling in Resources

### onFailure Callback

Execute a callback when the use computation fails:

```java
Resource<Connection> connWithCleanup = connResource
    .onFailure(conn -> {
        // Clean up any partial state on the connection
        try { conn.rollback(); } catch (Exception e) { /* ignore */ }
    });
```

### Combining with VTask Error Handling

```java
VTask<Data> robust = connResource.use(conn ->
    VTask.of(() -> fetchData(conn))
        .recover(error -> {
            logger.warn("Fetch failed, using cache", error);
            return cachedData;
        })
);
// Connection is released regardless of whether recover was invoked
```

---

~~~admonish info title="Key Takeaways"
* **Resource** implements the bracket pattern: acquire-use-release with guaranteed cleanup
* **fromAutoCloseable** wraps standard Java resources; **make** handles custom acquire/release
* **Composition** with `flatMap` and `and` maintains proper release ordering (LIFO)
* **Finalizers** add cleanup actions that run even if release throws
* **Scope integration** enables concurrent computations with safe resource management
* **Exception safety** ensures resources are released even when computations fail
~~~

~~~admonish info title="Hands-On Learning"
Practice Resource patterns in [Tutorial: Scope & Resource](../tutorials/concurrency/scope_resource_journey.md) (6 exercises, ~15 minutes).
~~~

~~~admonish tip title="See Also"
- [VTask Monad](vtask_monad.md) - Core VTask type and basic operations
- [Structured Concurrency](vtask_scope.md) - Scope and ScopeJoiner for task coordination
- [IO Monad](io_monad.md) - Platform thread-based alternative with similar patterns
~~~

---

**Previous:** [Structured Concurrency](vtask_scope.md)
**Next:** [VStream](vstream.md)
