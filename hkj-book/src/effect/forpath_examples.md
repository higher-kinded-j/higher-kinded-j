# ForPath Examples

This chapter provides worked examples of ForPath comprehensions for each major Path type, showing how different effect semantics shape the comprehension behaviour.

## EitherPath: Railway-Oriented Error Handling

Real-world workflows often chain operations where any step can fail. Writing explicit error checks after every call obscures the happy path. EitherPath comprehensions give you *railway-oriented programming*: the happy path reads top-to-bottom, and any failure automatically short-circuits to a `Left` without additional boilerplate.

```java
record User(String id, String name) {}
record Order(String orderId, User user) {}

Function<String, EitherPath<String, User>> findUser = id ->
    id.equals("user-1")
        ? Path.right(new User("user-1", "Alice"))
        : Path.left("User not found: " + id);

Function<User, EitherPath<String, Order>> createOrder = user ->
    Path.right(new Order("order-123", user));

EitherPath<String, String> result = ForPath.from(findUser.apply("user-1"))
    .from(user -> createOrder.apply(user))
    .yield((user, order) -> "Created " + order.orderId() + " for " + user.name());
// Right("Created order-123 for Alice")

EitherPath<String, String> failed = ForPath.from(findUser.apply("unknown"))
    .from(user -> createOrder.apply(user))
    .yield((user, order) -> "Created " + order.orderId());
// Left("User not found: unknown")
```

The second comprehension never reaches `createOrder` -- the `Left` from `findUser` propagates immediately.

---

## IOPath: Deferred Side Effects

Not every computation should run immediately. IOPath wraps side-effectful operations (reading files, calling APIs, writing logs) as *descriptions* of work rather than executing them eagerly. The comprehension builds a pipeline that only runs when you explicitly ask for the result, giving you full control over when effects happen.

```java
IOPath<String> readConfig = Path.io(() -> "production");
IOPath<Integer> readPort = Path.io(() -> 8080);

IOPath<String> serverInfo = ForPath.from(readConfig)
    .from(env -> readPort)
    .let(t -> t._1().toUpperCase())
    .yield((env, port, upperEnv) -> upperEnv + " server on port " + port);

// Nothing executes until:
String result = serverInfo.unsafeRun();  // "PRODUCTION server on port 8080"
```

This separation of *description* from *execution* makes IO pipelines easy to test, compose, and reason about.

---

## VTaskPath: Virtual Thread Concurrency

When your workflow involves I/O-bound service calls, you want those calls running on virtual threads so the JVM can handle thousands of them concurrently. VTaskPath wraps each step as a virtual-thread task, and the comprehension orchestrates them sequentially (each step can depend on the previous result) or in parallel with [`par()`](forpath_par.md).

```java
VTaskPath<User> fetchUser = Path.vtask(() -> userService.fetch(userId));
VTaskPath<Profile> fetchProfile = Path.vtask(() -> profileService.fetch(profileId));

VTaskPath<String> greeting = ForPath.from(fetchUser)
    .from(user -> fetchProfile)
    .let(t -> t._1().name().toUpperCase())
    .yield((user, profile, upperName) ->
        "Hello " + upperName + " from " + profile.city());

// Nothing executes until:
String result = greeting.unsafeRun();  // "Hello ALICE from London"
```

VTaskPath comprehensions are particularly well-suited for orchestrating multi-step service workflows where each step depends on the previous:

```java
VTaskPath<OrderSummary> orderWorkflow = ForPath.from(Path.vtask(() -> validateOrder(order)))
    .from(validated -> Path.vtask(() -> reserveInventory(validated)))
    .from(t -> Path.vtask(() -> processPayment(t._2())))
    .from(t -> Path.vtask(() -> sendConfirmation(t._3())))
    .yield((validated, reserved, payment, confirmation) ->
        new OrderSummary(validated.id(), payment.transactionId(), confirmation.sentAt()));

// Execute the entire workflow
Try<OrderSummary> result = orderWorkflow.runSafe();
```

---

## NonDetPath: Nondeterministic Choice

The name *NonDet* comes from *nondeterministic computation* -- a model where a computation can produce multiple results simultaneously, as if it were exploring every possible choice in parallel. This concept originates from nondeterministic automata in theoretical computer science, where a machine can be "in multiple states at once."

In practice, NonDetPath is backed by `List`, and a for-comprehension over it generates the *cartesian product* of all choices. Combined with `when()` guards, this makes it a natural fit for search problems, constraint satisfaction, and combinatorial generation.

```java
NonDetPath<String> combinations = ForPath.from(Path.list("red", "blue"))
    .from(c -> Path.list("S", "M", "L"))
    .when(t -> !t._1().equals("blue") || !t._2().equals("S"))  // filter out blue-S
    .yield((colour, size) -> colour + "-" + size);

List<String> result = combinations.run();
// ["red-S", "red-M", "red-L", "blue-M", "blue-L"]
```

Each `.from()` introduces a new "dimension" of choice, and `when()` prunes branches that don't satisfy constraints -- much like a `SELECT ... WHERE` over multiple tables.

---

## Extended Arity (6+ Bindings)

All ForPath types support up to 12 chained bindings. Step 1 is hand-written; steps 2-12 are generated by the `hkj-processor` annotation processor:

```java
// A 6-step MaybePath comprehension
MaybePath<String> profile = ForPath.from(Path.just("user-42"))
    .from(id -> findUser(id))                    // b = User
    .focus(user -> user.address())               // c = Address
    .focus(addr -> addr.city())                  // d = city name
    .let(t -> t._4().toUpperCase())              // e = uppercased city
    .let(t -> t._2().name() + " from " + t._5()) // f = summary
    .yield((id, user, addr, city, upper, summary) -> summary);

// Result: Just("Alice from LONDON")
```

At higher arities, the tuple-style `yield` can be more readable:

```java
.yield(t -> t._6())  // access the summary directly by position
```

---

**Previous:** [ForPath Comprehension](forpath_comprehension.md) | **Next:** [ForPath Parallel Composition](forpath_par.md)
