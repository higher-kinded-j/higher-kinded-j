# Strategic Vision: higher-kinded-j 2.0 — Virtual Threads & Structured Concurrency

> **Document Purpose**: Strategic analysis and roadmap for enhancing higher-kinded-j to leverage Java 25's virtual threads and structured concurrency features.

> **Last Updated**: January 2026 — Aligned with ForPath, Effect Contexts, Each, WitnessArity, and ListPrisms features.

## Executive Summary

Java 25 brings three transformative features that create a unique opportunity for higher-kinded-j:

- **Virtual Threads** (finalized) - lightweight threads managed by JVM
- **Scoped Values** (JEP 506, finalized) - modern ThreadLocal replacement
- **Structured Concurrency** (JEP 505/525, preview) - hierarchical task management

This combination allows higher-kinded-j to offer something **neither Reactor/RxJava nor Cats/ZIO can match**: a **direct-style functional effect system** with native JVM virtual thread execution, full type safety, and seamless Spring integration.

---

## Table of Contents

1. [Core Effect Types for Virtual Threads](#1-core-effect-types-for-virtual-threads)
   - VTask, VTaskKind with WitnessArity, VTaskPath, ForPath Integration, VTaskContext
2. [Structured Concurrency Integration](#2-structured-concurrency-integration)
   - Scope, Par Combinators, Custom Joiners
3. [Scoped Values as Functional Context](#3-scoped-values-as-functional-context)
4. [Effect Transformation and Interop](#4-effect-transformation-and-interop)
5. [Resource Management](#5-resource-management)
6. [Spring Integration Enhancements](#6-spring-integration-enhancements)
7. [Competitive Advantages Over Scala Libraries](#7-competitive-advantages-over-scala-libraries)
8. [Advanced Patterns](#8-advanced-patterns)
   - Hedged Requests, Circuit Breaker, Saga, VStream with Each/ListPrisms
9. [Migration Path](#9-migration-path)
10. [Project Roadmap](#10-project-roadmap)
11. [Leveraging Existing HKJ Features](#11-leveraging-existing-hkj-features)

---

## 1. Core Effect Types for Virtual Threads

### 1.1 `VTask<A>` — The Virtual Thread Effect

A new core effect type that represents a computation executed on a virtual thread:

```java
// Core virtual thread effect
public sealed interface VTask<A> extends VTaskKind<A>,
    Chainable<A>, Effectful<A> {

    // Factory methods
    static <A> VTask<A> of(Callable<A> computation);
    static <A> VTask<A> delay(Supplier<A> thunk);
    static <A> VTask<A> blocking(Callable<A> blockingOp);  // Explicit blocking marker
    static <A> VTask<A> succeed(A value);
    static <A> VTask<Void> exec(Runnable action);

    // Execution
    A run() throws InterruptedException;              // Execute on virtual thread
    Try<A> runSafe();                                 // Wrapped execution
    CompletableFuture<A> runAsync();                  // Non-blocking handle

    // Composition (Monad operations)
    <B> VTask<B> map(Function<A, B> f);
    <B> VTask<B> flatMap(Function<A, VTask<B>> f);    // Sequential
    <B> VTask<B> via(Function<A, VTask<B>> f);        // Alias for flatMap

    // Timeouts & Cancellation
    VTask<A> timeout(Duration duration);
    VTask<A> interruptible();
    VTask<A> uninterruptible();
}
```

**Competitive Edge**: Unlike ZIO/Cats Effect fibers that are library-managed, `VTask` uses native JVM virtual threads with:

- **Zero-overhead scheduling** — JVM handles all scheduling
- **No colored function problem** — Everything is just code
- **Full debugging support** — Standard Java debuggers work perfectly
- **Pinning fixed in Java 25** — synchronized blocks no longer pin virtual threads (JEP 491)

### 1.2 `VTaskKind` with WitnessArity

Following the established pattern with `WitnessArity` bounds for compile-time arity enforcement:

```java
public interface VTaskKind<A> extends Kind<VTaskKind.Witness, A> {

    // Witness with arity bound ensures compile-time type safety
    final class Witness implements WitnessArity<TypeArity.Unary> {
        private Witness() {}
    }
}

// Helper for zero-cost unwrapping
public final class VTaskKindHelper {
    public static <A> VTask<A> unwrap(Kind<VTaskKind.Witness, A> kind) {
        return (VTask<A>) kind;
    }

    public static <A> Kind<VTaskKind.Witness, A> wrap(VTask<A> task) {
        return task;
    }
}
```

### 1.3 `VTaskMonad` Implementation

```java
public enum VTaskMonad implements MonadError<VTaskKind.Witness, Throwable> {
    INSTANCE;

    @Override
    public <A> VTask<A> of(A value) {
        return VTask.succeed(value);
    }

    @Override
    public <A, B> VTask<B> flatMap(
        Function<A, Kind<VTaskKind.Witness, B>> f,
        Kind<VTaskKind.Witness, A> fa) {
        return VTaskKindHelper.unwrap(fa)
            .flatMap(a -> VTaskKindHelper.unwrap(f.apply(a)));
    }

    @Override
    public <A> VTask<A> raiseError(Throwable error) {
        return VTask.of(() -> { throw error; });
    }

    @Override
    public <A> VTask<A> handleErrorWith(
        Function<Throwable, Kind<VTaskKind.Witness, A>> handler,
        Kind<VTaskKind.Witness, A> fa) {
        return VTask.of(() -> {
            try {
                return VTaskKindHelper.unwrap(fa).run();
            } catch (Throwable t) {
                return VTaskKindHelper.unwrap(handler.apply(t)).run();
            }
        });
    }
}
```

### 1.4 `VTaskPath` — Effect Path API Integration

Integrate VTask with the Effect Path API for seamless composition:

```java
public sealed interface VTaskPath<A> extends VTaskKind<A>,
    Chainable<A>, Effectful<A> permits VTaskPathImpl {

    // Factory methods via Path class
    // Path.vtask(() -> computation)
    // Path.vtaskPure(value)

    // Standard Effect Path operations
    <B> VTaskPath<B> map(Function<A, B> f);
    <B> VTaskPath<B> via(Function<A, VTaskPath<B>> f);

    // Execution
    A run() throws InterruptedException;
    Try<A> runSafe();
    CompletableFuture<A> runAsync();
}
```

### 1.5 `ForPath` Integration — For-Comprehension Style

Leverage the existing `ForPath` builder for VTask composition:

```java
// ForPath provides familiar for-comprehension syntax
VTaskPath<OrderConfirmation> workflow = ForPath
    .from(Path.vtask(() -> userService.getUser(userId)))
    .from(user -> Path.vtask(() -> inventoryService.checkStock(user.cart())))
    .from((user, stock) -> Path.vtask(() -> paymentService.charge(user, stock)))
    .let((user, stock, payment) -> createConfirmation(user, stock, payment))
    .yield((user, stock, payment, confirmation) -> confirmation);

// Execute the composed workflow
OrderConfirmation result = workflow.run();
```

**Integration Points:**
- Add `ForPath.fromVTask(VTaskPath<A>)` entry point
- Support `Steps1` through `Steps5` builders for VTask
- Integrate with `FocusPath` for optics composition

### 1.6 `VTaskContext` — Effect Context Layer 2 Wrapper

Following the Effect Contexts pattern, provide a user-friendly Layer 2 abstraction:

```java
public sealed interface VTaskContext<A> extends EffectContext<VTaskKind.Witness, A> {

    // Factory methods
    static <A> VTaskContext<A> of(Callable<A> computation);
    static <A> VTaskContext<A> pure(A value);
    static <A> VTaskContext<A> fromPath(VTaskPath<A> path);

    // Composition (hides HKT complexity)
    <B> VTaskContext<B> map(Function<A, B> f);
    <B> VTaskContext<B> via(Function<A, VTaskContext<B>> f);

    // Error handling
    VTaskContext<A> recover(Function<Throwable, A> handler);
    VTaskContext<A> recoverWith(Function<Throwable, VTaskContext<A>> handler);

    // Execution
    A run() throws InterruptedException;
    Try<A> runSafe();

    // Escape hatch to Layer 3
    VTask<A> underlying();
}
```

**Benefits:**
- Familiar monadic interface without HKT exposure
- Easy migration from IOPath or CompletableFuturePath
- Consistent with existing Effect Contexts (ErrorContext, ConfigContext, etc.)

---

## 2. Structured Concurrency Integration

### 2.1 `Scope<A>` — Structured Task Scope Effect

The killer feature leveraging Java 25's `StructuredTaskScope`:

```java
public sealed interface Scope<A> extends ScopeKind<A>, Chainable<A> {

    // Factory methods using Java 25's new static factory pattern
    static <A> Scope<A> open(ScopeJoiner<A> joiner);
    static <A> Scope<A> allSucceed();    // Wait for all, fail on any failure
    static <A> Scope<A> anySucceed();    // Return first success
    static <A> Scope<A> race();          // Return first completion (success or failure)

    // Fork subtasks (returns handle for result)
    <B> Subtask<B> fork(VTask<B> task);
    <B> Subtask<B> fork(Callable<B> computation);

    // Join and get result
    A join() throws InterruptedException;
    Try<A> joinSafe();

    // Configuration (immutable builders)
    Scope<A> named(String name);
    Scope<A> withTimeout(Duration timeout);
    Scope<A> withThreadFactory(ThreadFactory factory);
}
```

### 2.2 `Par<A>` — Parallel Composition Combinator

A high-level parallel combinator that wraps structured concurrency:

```java
public final class Par {

    // Parallel zip (all must succeed)
    static <A, B> VTask<Pair<A, B>> zip(VTask<A> a, VTask<B> b);
    static <A, B, C> VTask<Tuple3<A, B, C>> zip3(VTask<A> a, VTask<B> b, VTask<C> c);

    // Parallel map (applicative-style)
    static <A, B, C> VTask<C> map2(
        VTask<A> a, VTask<B> b,
        BiFunction<A, B, C> f);

    // Race (first to complete wins, others cancelled)
    static <A> VTask<A> race(VTask<A>... tasks);
    static <A> VTask<A> race(List<VTask<A>> tasks);

    // All must succeed (parallel traverse)
    static <A> VTask<List<A>> all(List<VTask<A>> tasks);
    static <A, B> VTask<List<B>> traverse(
        List<A> items, Function<A, VTask<B>> f);

    // Any can succeed (returns first success)
    static <A> VTask<A> any(List<VTask<A>> tasks);

    // N of M (wait for N successes from M tasks)
    static <A> VTask<List<A>> nOf(int n, List<VTask<A>> tasks);

    // Timeout on parallel group
    static <A> VTask<List<A>> allWithTimeout(
        List<VTask<A>> tasks, Duration timeout);
}
```

**Example Usage:**

```java
// Fetch user profile and orders in parallel, fail if either fails
VTask<UserDashboard> dashboard = Par.map2(
    userService.fetchProfile(userId),
    orderService.fetchOrders(userId),
    UserDashboard::new
);

// Race multiple service endpoints, use first responder
VTask<Response> response = Par.race(
    primaryService.fetch(id),
    fallbackService.fetch(id).delay(Duration.ofMillis(100))  // Hedged request
);

// Fetch all products in parallel with timeout
VTask<List<Product>> products = Par.allWithTimeout(
    productIds.stream().map(productService::fetch).toList(),
    Duration.ofSeconds(5)
);
```

### 2.3 Custom Joiner Support

Leverage Java 25's new `Joiner<T>` interface:

```java
public interface ScopeJoiner<T> {
    // Called when subtask completes
    boolean onComplete(Subtask<? extends T> subtask);

    // Called after join() completes
    T result() throws Throwable;

    // Built-in joiners
    static <T> ScopeJoiner<List<T>> allSucceed();
    static <T> ScopeJoiner<T> anySucceed();
    static <T> ScopeJoiner<T> firstComplete();

    // Error accumulation joiner (like Validated)
    static <E, T> ScopeJoiner<Validated<List<E>, List<T>>>
        accumulating(Function<Throwable, E> errorMapper);

    // Custom aggregation
    static <T, R> ScopeJoiner<R> collecting(
        Collector<T, ?, R> collector);
}
```

---

## 3. Scoped Values as Functional Context

### 3.1 `Context<R, A>` — Reader with Scoped Values

Replace traditional Reader monad with scoped value integration:

```java
public sealed interface Context<R, A> extends ContextKind<R, A>,
    Chainable<A> {

    // Declare context requirement
    static <R> ScopedValue<R> declare();

    // Access context
    static <R, A> Context<R, A> ask(ScopedValue<R> key);
    static <R, A> Context<R, A> asks(ScopedValue<R> key, Function<R, A> f);

    // Pure value (ignores context)
    static <R, A> Context<R, A> of(A value);

    // Run with context
    A runWith(R context, ScopedValue<R> key);
    VTask<A> provide(R context, ScopedValue<R> key);  // Lift to VTask

    // Composition
    <B> Context<R, B> map(Function<A, B> f);
    <B> Context<R, B> flatMap(Function<A, Context<R, B>> f);

    // Local modification
    Context<R, A> local(UnaryOperator<R> f);
}
```

**Example — Request Context:**

```java
// Declare scoped values (replaces ThreadLocal)
public class RequestContext {
    public static final ScopedValue<UserId> CURRENT_USER = ScopedValue.newInstance();
    public static final ScopedValue<TraceId> TRACE_ID = ScopedValue.newInstance();
    public static final ScopedValue<Locale> LOCALE = ScopedValue.newInstance();
}

// Service using context
public class OrderService {
    public Context<UserId, List<Order>> getMyOrders() {
        return Context.ask(CURRENT_USER)
            .flatMap(userId -> Context.of(orderRepo.findByUser(userId)));
    }
}

// Automatic propagation to virtual threads!
VTask<List<Order>> task = orderService.getMyOrders()
    .provide(currentUser, CURRENT_USER);

// In Spring controller - context automatically inherited by all forked tasks
@GetMapping("/orders")
public VTask<List<Order>> getOrders() {
    return Par.zip(
        orderService.getMyOrders().provide(getCurrentUser(), CURRENT_USER),
        recommendationService.getRecommendations()  // Also sees CURRENT_USER!
    ).map(this::combine);
}
```

**Key Advantage**: Scoped values are automatically inherited by child virtual threads in structured concurrency, solving the context propagation problem that plagues Reactor/RxJava.

---

## 4. Effect Transformation and Interop

### 4.1 `VTaskT<F, A>` — VTask Transformer

Stack VTask with other effects:

```java
public sealed interface VTaskT<F extends WitnessArity<?>, A>
    extends VTaskTKind<F, A> {

    // F<VTask<A>> → VTaskT<F, A>
    static <F extends WitnessArity<?>, A> VTaskT<F, A>
        fromKind(Kind<F, VTask<A>> fva, Monad<F> monadF);

    // Composition
    <B> VTaskT<F, B> map(Function<A, B> f);
    <B> VTaskT<F, B> flatMap(Function<A, VTaskT<F, B>> f);

    // Run the outer effect
    Kind<F, VTask<A>> run();

    // Common stacks
    static <E, A> VTaskT<EitherKind.Witness<E>, A>
        eitherT(Either<E, VTask<A>> eva);

    static <A> VTaskT<MaybeKind.Witness, A>
        maybeT(Maybe<VTask<A>> mva);
}
```

### 4.2 Natural Transformations

```java
public class VTaskTransformations {

    // IO → VTask (execute IO on virtual thread)
    public static <A> VTask<A> fromIO(IO<A> io) {
        return VTask.of(io::unsafeRun);
    }

    // VTask → CompletableFuture
    public static <A> CompletableFuture<A> toFuture(VTask<A> task) {
        return task.runAsync();
    }

    // CompletableFuture → VTask
    public static <A> VTask<A> fromFuture(CompletableFuture<A> future) {
        return VTask.of(future::join);  // Virtual thread can block!
    }

    // Try → VTask
    public static <A> VTask<A> fromTry(Try<A> t) {
        return t.fold(
            err -> VTask.of(() -> { throw err; }),
            VTask::succeed
        );
    }

    // Either → VTask
    public static <E extends Throwable, A> VTask<A>
        fromEither(Either<E, A> e) {
        return e.fold(
            err -> VTask.of(() -> { throw err; }),
            VTask::succeed
        );
    }
}
```

---

## 5. Resource Management

### 5.1 `Resource<A>` — Bracket Pattern with Virtual Threads

```java
public sealed interface Resource<A> extends ResourceKind<A>, Chainable<A> {

    // Create resource with automatic cleanup
    static <A> Resource<A> make(
        VTask<A> acquire,
        Consumer<A> release);

    static <A extends AutoCloseable> Resource<A> fromAutoCloseable(
        VTask<A> acquire);

    // Use the resource
    <B> VTask<B> use(Function<A, VTask<B>> f);

    // Composition
    <B> Resource<B> map(Function<A, B> f);
    <B> Resource<B> flatMap(Function<A, Resource<B>> f);

    // Combine resources (both acquired, both released)
    static <A, B> Resource<Pair<A, B>> both(Resource<A> a, Resource<B> b);

    // Sequential resource acquisition
    <B> Resource<Pair<A, B>> and(Resource<B> other);
}
```

**Example:**

```java
Resource<Connection> connection = Resource.fromAutoCloseable(
    VTask.of(() -> dataSource.getConnection())
);

Resource<PreparedStatement> statement = connection.flatMap(conn ->
    Resource.fromAutoCloseable(
        VTask.of(() -> conn.prepareStatement(sql))
    )
);

// Resources automatically released even on exception
VTask<List<User>> users = statement.use(stmt ->
    VTask.of(() -> executeQuery(stmt))
);
```

### 5.2 Structured Concurrency Resource Safety

Resources acquired in a scope are automatically released when the scope closes:

```java
VTask<Report> generateReport = Scope.<Report>allSucceed()
    .fork(Resource.fromAutoCloseable(dbPool.acquire())
        .use(conn -> fetchData(conn)))
    .fork(Resource.fromAutoCloseable(cacheClient.acquire())
        .use(cache -> getCachedMetrics(cache)))
    .join()
    .map(results -> buildReport(results));

// All connections automatically released when scope closes!
```

---

## 6. Spring Integration Enhancements

### 6.1 `VTaskReturnValueHandler`

```java
@Component
public class VTaskReturnValueHandler implements HandlerMethodReturnValueHandler {

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        return VTask.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public void handleReturnValue(
        Object returnValue,
        MethodParameter returnType,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest) {

        VTask<?> task = (VTask<?>) returnValue;
        DeferredResult<Object> deferredResult = new DeferredResult<>();

        // Execute on virtual thread, non-blocking for Tomcat
        Thread.ofVirtual().start(() -> {
            try {
                Object result = task.run();
                deferredResult.setResult(result);
            } catch (Throwable e) {
                deferredResult.setErrorResult(e);
            }
        });

        mavContainer.setRequestHandled(true);
        webRequest.setAttribute(
            DeferredResult.class.getName(),
            deferredResult,
            RequestAttributes.SCOPE_REQUEST);
    }
}
```

### 6.2 Scoped Value Integration with Spring

```java
@Configuration
public class ScopedValueConfiguration {

    public static final ScopedValue<SecurityContext> SECURITY_CONTEXT =
        ScopedValue.newInstance();

    @Bean
    public FilterRegistrationBean<ScopedValueFilter> scopedValueFilter() {
        return new FilterRegistrationBean<>(new ScopedValueFilter());
    }
}

public class ScopedValueFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse res,
                         FilterChain chain) throws IOException, ServletException {
        SecurityContext ctx = SecurityContextHolder.getContext();

        // Bind scoped value for entire request, auto-inherited by VTasks
        ScopedValue.runWhere(SECURITY_CONTEXT, ctx, () -> {
            chain.doFilter(req, res);
        });
    }
}

// In service - context automatically available in all virtual threads
@Service
public class SecureService {
    public VTask<SecretData> getSecretData() {
        return Context.ask(SECURITY_CONTEXT)
            .flatMap(ctx -> {
                if (ctx.hasRole("ADMIN")) {
                    return Context.of(fetchSecretData());
                }
                return Context.raiseError(new AccessDeniedException());
            })
            .provide(SECURITY_CONTEXT);
    }
}
```

### 6.3 Actuator Metrics for Virtual Threads

```java
@Endpoint(id = "vtask-metrics")
public class VTaskMetricsEndpoint {

    @ReadOperation
    public Map<String, Object> metrics() {
        return Map.of(
            "virtualThreads.active", getActiveVirtualThreadCount(),
            "virtualThreads.pinned", getPinnedCount(),
            "scopes.active", getActiveScopeCount(),
            "scopes.completed", getCompletedScopeCount(),
            "scopes.failed", getFailedScopeCount()
        );
    }
}
```

---

## 7. Competitive Advantages Over Scala Libraries

### 7.1 vs. ZIO

| Aspect | ZIO | higher-kinded-j 2.0 |
|--------|-----|---------------------|
| **Runtime** | Custom fiber scheduler | Native JVM virtual threads |
| **Debugging** | Complex fiber traces | Standard Java debuggers |
| **Memory** | ~3x larger fiber objects | JVM-optimized virtual threads |
| **Pinning** | N/A | Fixed in Java 25 (JEP 491) |
| **Learning Curve** | Steep (ZIO layers, etc.) | Familiar Java patterns |
| **Spring Integration** | Minimal | First-class support |
| **Tooling** | Scala-specific | Standard Java tooling |

### 7.2 vs. Cats Effect

| Aspect | Cats Effect | higher-kinded-j 2.0 |
|--------|-------------|---------------------|
| **Scheduling** | Cooperative (yields required) | Preemptive (JVM handles it) |
| **IO Type** | Library-based IO | Direct VTask execution |
| **Cross-platform** | JVM, JS, Native | JVM-focused (full power) |
| **Type Inference** | Scala's excellent inference | Java's improving inference |
| **Enterprise Adoption** | Limited in Java shops | Natural fit for Java teams |

### 7.3 vs. Reactor/RxJava

| Aspect | Reactor/RxJava | higher-kinded-j 2.0 |
|--------|----------------|---------------------|
| **Programming Model** | Reactive streams (callback-based) | Direct style (blocking is OK) |
| **Backpressure** | Manual propagation | Not needed (virtual threads) |
| **Debugging** | Complex chain traces | Normal stack traces |
| **Blocking** | Never (anti-pattern) | Encouraged (virtual threads) |
| **Cognitive Load** | High (operators, schedulers) | Low (familiar patterns) |
| **Composability** | Limited type safety | Full HKT type safety |
| **Error Handling** | onError callbacks | Typed MonadError |

---

## 8. Advanced Patterns

### 8.1 Hedged Requests

```java
public class Hedge {
    // Send second request if first is slow, use first responder
    public static <A> VTask<A> hedged(
        VTask<A> primary,
        VTask<A> backup,
        Duration hedgeDelay) {

        return Scope.<A>anySucceed()
            .fork(primary)
            .fork(backup.delay(hedgeDelay))
            .join();
    }
}

// Usage
VTask<Response> resilient = Hedge.hedged(
    serviceA.fetch(id),
    serviceB.fetch(id),
    Duration.ofMillis(50)
);
```

### 8.2 Circuit Breaker with VTask

```java
public class CircuitBreaker<A> {
    private final AtomicReference<State> state;

    public VTask<A> protect(VTask<A> task) {
        return VTask.of(() -> {
            State current = state.get();
            return switch (current) {
                case CLOSED -> executeWithTracking(task);
                case OPEN -> {
                    if (shouldAttemptReset()) {
                        yield executeWithTracking(task);
                    }
                    throw new CircuitOpenException();
                }
                case HALF_OPEN -> executeWithTracking(task);
            };
        });
    }
}
```

### 8.3 Saga Pattern

```java
public class Saga<A> {
    private final VTask<A> action;
    private final Consumer<A> compensate;

    public static <A> Saga<A> of(VTask<A> action, Consumer<A> compensate) {
        return new Saga<>(action, compensate);
    }

    public static <A> VTask<A> run(List<Saga<?>> steps, Function<List<?>, A> combine) {
        return VTask.of(() -> {
            List<Object> results = new ArrayList<>();
            List<Runnable> compensations = new ArrayList<>();

            try {
                for (Saga<?> step : steps) {
                    Object result = step.action.run();
                    results.add(result);
                    compensations.add(() -> step.compensate.accept(result));
                }
                return combine.apply(results);
            } catch (Throwable e) {
                // Compensate in reverse order
                Collections.reverse(compensations);
                for (Runnable comp : compensations) {
                    try { comp.run(); } catch (Exception ignored) {}
                }
                throw e;
            }
        });
    }
}
```

### 8.4 Streaming with Virtual Threads

Leverage the `Each` typeclass and `ListPrisms` patterns for streaming:

```java
public sealed interface VStream<A> {
    // Lazy, pull-based stream executed on virtual threads

    static <A> VStream<A> fromIterable(Iterable<A> items);
    static <A> VStream<A> generate(Supplier<A> generator);
    static <A> VStream<A> unfold(A seed, Function<A, Maybe<Pair<A, A>>> f);

    // Transformations
    <B> VStream<B> map(Function<A, B> f);
    <B> VStream<B> flatMap(Function<A, VStream<B>> f);
    VStream<A> filter(Predicate<A> p);
    VStream<A> take(int n);
    VStream<A> takeWhile(Predicate<A> p);

    // Parallel processing (each element on its own virtual thread)
    <B> VStream<B> mapPar(int parallelism, Function<A, B> f);
    <B> VStream<B> flatMapPar(int parallelism, Function<A, VStream<B>> f);

    // Terminal operations
    VTask<List<A>> toList();
    VTask<Void> forEach(Consumer<A> action);
    <B> VTask<B> fold(B zero, BiFunction<B, A, B> f);
}
```

### 8.5 `Each` Typeclass Integration for VStream

Integrate with the `Each` typeclass for canonical traversal:

```java
// VStream Each instance for element-wise operations
public class VStreamEach {
    public static <A> Each<VStream<A>, A> instance() {
        return Each.fromTraversal(vStreamTraversal());
    }

    // Indexed traversal with position
    public static <A> Each<VStream<A>, A> indexedInstance() {
        return Each.fromIndexedTraversal(vStreamIndexedTraversal());
    }
}

// Usage with optics
Each<VStream<User>, User> userStreamEach = VStreamEach.instance();
VStream<User> transformed = Traversals.modify(
    userStreamEach.each(),
    user -> user.withName(user.name().toUpperCase()),
    userStream
);
```

### 8.6 Stack-Safe Recursion with ListPrisms Patterns

Apply ListPrisms-style stack-safe operations for VStream processing:

```java
public class VStreamOps {
    // Stack-safe fold using trampolines (following ListPrisms pattern)
    public static <A, B> VTask<B> foldRightSafe(
        VStream<A> stream,
        B initial,
        BiFunction<A, Supplier<B>, B> combiner) {
        return VTask.of(() -> foldRightTrampoline(stream, initial, combiner).run());
    }

    // Cons/snoc decomposition for VStream
    public static <A> Prism<VStream<A>, Pair<A, VStream<A>>> cons() {
        return Prism.of(
            stream -> stream.uncons(),  // Maybe<Pair<A, VStream<A>>>
            pair -> VStream.cons(pair.first(), pair.second())
        );
    }

    // Chunked parallel processing
    public static <A, B> VStream<B> mapParChunked(
        VStream<A> stream,
        int chunkSize,
        int parallelism,
        Function<A, B> f) {
        return stream
            .chunked(chunkSize)
            .flatMap(chunk -> VStream.fromVTask(
                Par.traverse(chunk.toList(), a -> VTask.of(() -> f.apply(a)))
            ));
    }
}
```

---

## 9. Migration Path

### 9.1 From IOPath

```java
// Before (IOPath)
IOPath<String> io = Path.io(() -> readFile(path))
    .map(String::trim)
    .via(s -> Path.io(() -> validate(s)));
String result = io.unsafeRun();

// After (VTask) - nearly identical API!
VTask<String> task = VTask.of(() -> readFile(path))
    .map(String::trim)
    .via(s -> VTask.of(() -> validate(s)));
String result = task.run();
```

### 9.2 From CompletableFuturePath

```java
// Before
CompletableFuturePath<User> path = userService.fetchAsync(id);
User user = path.join();

// After - simpler, no need to think about async
VTask<User> task = VTask.of(() -> userService.fetch(id));
User user = task.run();
```

---

## 10. Project Roadmap

### Phase 1: Foundation (v2.0.0-M1)

- `VTask<A>` core implementation with `VTaskKind.Witness` using `WitnessArity<TypeArity.Unary>`
- `VTaskMonad` and `VTaskMonadError` type class instances
- `VTaskPath` for Effect Path API integration
- Basic `Par` combinators (zip, race, all)
- `VTaskKindHelper` for zero-cost unwrapping

### Phase 2: Effect Path Integration (v2.0.0-M2)

- `ForPath.fromVTask()` entry point for for-comprehension style
- `VTaskContext` Layer 2 wrapper (following Effect Contexts pattern)
- Integration with existing `PathOps` for sequence/traverse
- Add `Path.vtask()` and `Path.vtaskPure()` factory methods

### Phase 3: Structured Concurrency (v2.0.0-M3)

- `Scope<A>` with `StructuredTaskScope` integration
- Custom `ScopeJoiner` support with `Validated` accumulation
- `Resource<A>` with bracket pattern
- Timeout and cancellation support

### Phase 4: Context & Spring Integration (v2.0.0-M4)

- `Context<R, A>` with `ScopedValue` integration
- Spring Boot auto-configuration enhancements
- `VTaskReturnValueHandler` and `VTaskContextReturnValueHandler`
- Actuator metrics for virtual thread monitoring
- `ScopedValueFilter` for request context propagation

### Phase 5: Advanced Patterns (v2.0.0-RC1)

- `VStream<A>` for streaming with `Each` typeclass integration
- Stack-safe operations following `ListPrisms` patterns
- Circuit breaker, retry, and bulkhead patterns (extend existing `RetryPolicy`)
- Saga pattern support
- Performance benchmarks vs. Reactor/RxJava/ZIO

### Phase 6: Release (v2.0.0)

- Documentation and migration guides
- Reference implementations (update Order Workflow, Draughts examples)
- Performance tuning
- mdbook chapter on virtual threads

---

## 11. Leveraging Existing HKJ Features

The virtual threads strategy builds upon recent higher-kinded-j enhancements:

### 11.1 WitnessArity Integration

All new witness types use `WitnessArity` bounds for compile-time safety:

```java
// VTask witness
final class Witness implements WitnessArity<TypeArity.Unary> {}

// Scope witness (for structured concurrency results)
final class ScopeWitness implements WitnessArity<TypeArity.Unary> {}
```

### 11.2 Effect Contexts Pattern

`VTaskContext` follows the established pattern from `ErrorContext`, `ConfigContext`, etc.:

| Existing Context | VTask Equivalent |
|-----------------|------------------|
| `ErrorContext<F, E, A>` | `VTaskContext<A>` with `Throwable` errors |
| `ConfigContext<F, R, A>` | `Context<R, A>` with ScopedValue |
| `MutableContext<F, S, A>` | Not needed (virtual threads are stateless) |

### 11.3 ForPath Builder Integration

VTask integrates with the multi-step ForPath builder:

```java
// Steps progression for VTask
ForPath.Steps1<VTaskPath.Witness, A>
    → ForPath.Steps2<VTaskPath.Witness, A, B>
    → ForPath.Steps3<VTaskPath.Witness, A, B, C>
    → ... up to Steps5
```

### 11.4 Each Typeclass for Collections

VStream provides an `Each` instance for canonical traversal:

```java
Each<VStream<A>, A> vstreamEach = VStreamEach.instance();

// With indexed access
vstreamEach.eachWithIndex().ifPresent(indexed -> {
    // Process with position information
});
```

### 11.5 ListPrisms-Style Operations

Stack-safe recursive operations following the ListPrisms pattern:

- `VStreamOps.foldRightSafe()` — trampolined fold
- `VStreamOps.cons()` / `snoc()` — prism-based decomposition
- `VStreamOps.mapParChunked()` — chunked parallel processing

---

## Summary: The Unique Value Proposition

higher-kinded-j 2.0 offers a compelling combination that no other library provides:

1. **Direct-Style FP**: Write blocking code that executes on virtual threads — no callbacks, no colored functions
2. **Full Type Safety**: HKT-based abstractions with proper Functor/Applicative/Monad type classes
3. **Native JVM Integration**: Leverage virtual threads, structured concurrency, and scoped values directly
4. **Spring-First**: Seamless integration with Spring Boot 4's virtual thread support
5. **Simplified Mental Model**: No reactive streams complexity, no manual backpressure, no scheduler configuration
6. **Standard Tooling**: Regular Java debuggers, profilers, and monitoring work perfectly

The key insight is that **Java 25's virtual threads make reactive programming unnecessary for most use cases**. higher-kinded-j 2.0 can provide the composability and type safety of functional effect systems while embracing the simplicity of direct-style programming that virtual threads enable.

---

## References

- [JEP 505: Structured Concurrency (Fifth Preview)](https://openjdk.org/jeps/505)
- [JEP 525: Structured Concurrency (Sixth Preview)](https://openjdk.org/jeps/525)
- [JEP 506: Scoped Values](https://openjdk.org/jeps/506)
- [Project Loom: Structured Concurrency in JDK 25](https://rockthejvm.com/articles/structured-concurrency-jdk-25)
- [Structured Concurrency Revamp in Java 25 - Inside Java Newscast #91](https://nipafx.dev/inside-java-newscast-91/)
- [Spring Boot 4 and Virtual Threads](https://medium.com/@oleksandr.dendeberia/spring-boot-4-and-virtual-threads-a-practical-high-impact-upgrade-for-modern-java-development-10631f4f427b)
- [Virtual Threads, Structured Concurrency and Scoped Values - Spring I/O 2025](https://2025.springio.net/sessions/virtual-threads-structured-concurrency-and-scoped-values-putting-it-all-together/)
- [Cats Effect vs ZIO](https://softwaremill.com/cats-effect-vs-zio/)
- [The Ultimate Guide to Java Virtual Threads](https://rockthejvm.com/articles/the-ultimate-guide-to-java-virtual-threads)
- [Scala's Gamble with Direct Style](https://alexn.org/blog/2025/08/29/scala-gamble-with-direct-style/)
