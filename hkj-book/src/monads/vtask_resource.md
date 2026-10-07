# Resource Management with Bracket Pattern
## _Safe Acquisition and Release for VTask_

~~~admonish info title="What You'll Learn"
- Using `Resource` for safe resource management in concurrent computations
- Creating resources from `AutoCloseable`, explicit acquire/release, and pure values
- Composing multiple resources with `flatMap` and `and`
- Using one `Resource` many times, nested or at once
- Adding finalisers, and cleanup that runs only when the use fails
- Integrating resources with `Scope` for concurrent resource management
~~~

> *"Resource acquisition is initialization... the point is to tie the lifecycle of a resource to the lifetime of a local object."*
> — **Bjarne Stroustrup**, creator of C++, on the RAII pattern that inspired functional bracket semantics

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

<!-- verify -->
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
| `pure(value)` | Wraps a value with no cleanup | Configuration, constants, pre-initialised values |

---

## Using Resources

The `use` method runs a computation with the acquired resource and guarantees release:

<!-- verify -->
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

<!-- verify -->
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

### Using One Resource Many Times {#using-one-resource-many-times}

A `Resource` holds nothing until it is used. Each `use` acquires its own resource and releases exactly that one. So build a `Resource` once and use it wherever it is needed: once per request, on several threads at once, or one use nested inside another. This holds however the `Resource` was composed.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/monads/resource/ResourceBook.java:many_uses}}
```

The nested use opens a second connection, and each use closes the one it opened, innermost first.

---

## Composing Resources

Resources compose naturally, acquiring in order and releasing in reverse (LIFO):

<!-- verify -->
```java
// Chain resource acquisition with flatMap
Resource<PreparedStatement> stmtResource = connResource.flatMap(conn ->
    Resource.fromAutoCloseable(() -> conn.prepareStatement(sql))
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
Resource<Par.Tuple3<Connection, PreparedStatement, ResultSet>> triple =
    connResource.and(stmtResource, resultSetResource);

// Transform resource value with map. `map` takes a plain Function, so a checked
// exception has to be dealt with here rather than propagated.
Resource<String> connectionInfo = connResource.map(conn -> {
    try {
        return conn.getMetaData().getURL();
    } catch (SQLException e) {
        throw new IllegalStateException("Cannot read connection metadata", e);
    }
});
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

<!-- verify -->
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

## Resource Finalisers {#resource-finalizers}

Add cleanup actions that run after the primary release:

<!-- verify -->
```java
Resource<Connection> withLogging = connResource
    .withFinalizer(() -> logger.info("Connection released"));

// Cleanup runs even if release throws
Resource<Lock> lockResource = Resource.make(
    () -> { lock.lock(); return lock; },
    Lock::unlock
).withFinalizer(() -> metrics.recordLockRelease());
```

### Finaliser Behaviour {#finalizer-behaviour}

- **Finalisers run after the primary release.**
- **Finalisers run in the order they were added.**
- **A finaliser runs even when the release throws.**
- **A finaliser runs even when an earlier finaliser throws.**
- **The last exception thrown is the one the use fails with.** An earlier one from the release or a finaliser is not kept.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/monads/resource/ResourceBook.java:finalisers}}
```

---

## Resource + Scope Integration

Resources work seamlessly with Scope for structured concurrent resource management:

<!-- verify -->
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

<!-- verify -->
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

### onFailure Callback {#onfailure-callback}

`onFailure` adds an action that runs when the use fails, before the release. It receives the acquired resource, so it can undo partial work, such as rolling back a transaction. When the use succeeds, only the release runs.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/monads/resource/ResourceBook.java:on_failure}}
```

The first use succeeds, so it only closes `tx-1`. The second fails, so `tx-2` is rolled back and then closed. The use fails when the function given to `use`, or the task it returns, throws. In a composed `Resource`, three more failures count, since each happens while the resource is held:

- **`map`'s function throws.**
- **A resource that `flatMap` acquires next fails to acquire.**
- **A resource that `and` acquires next fails to acquire.**

The release runs even when the action throws.

### Combining with VTask Error Handling

<!-- verify -->
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
* **Each use acquires its own resource**, so one `Resource` can be used many times, nested or at once
* **onFailure** runs an action before the release when the use fails
* **Finalisers** add cleanup actions that run even if release throws
* **Scope integration** enables concurrent computations with safe resource management
* **Exception safety** ensures resources are released even when computations fail
~~~

~~~admonish info title="Hands-On Learning"
Practise Resource patterns in [TutorialResource](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/concurrency/TutorialResource.java) (10 exercises), part of the [Scope & Resource journey](../tutorials/concurrency/scope_resource_journey.md).
~~~

~~~admonish tip title="See Also"
- [VTask Monad](vtask_monad.md) - Core VTask type and basic operations
- [Structured Concurrency](vtask_scope.md) - Scope and ScopeJoiner for task coordination
- [IO Monad](io_monad.md) - Platform thread-based alternative with similar patterns
~~~

---

**Previous:** [Structured Concurrency](vtask_scope.md)
**Next:** [VStream](vstream.md)
