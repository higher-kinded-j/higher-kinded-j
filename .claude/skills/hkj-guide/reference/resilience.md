# Higher-Kinded-J Resilience Patterns Reference

Package: `org.higherkindedj.hkt.resilience`

## 1. Retry

Retries a failing operation with configurable backoff. Assumes transient failures will resolve.

### RetryPolicy Factory Methods

```java
RetryPolicy.fixed(3, Duration.ofMillis(100));                    // Same delay every attempt
RetryPolicy.exponentialBackoff(5, Duration.ofSeconds(1));         // Doubling: 1s, 2s, 4s, 8s, 16s
RetryPolicy.exponentialBackoffWithJitter(5, Duration.ofSeconds(1)); // Randomized exponential (prevents thundering herd)
RetryPolicy.linear(5, Duration.ofMillis(200));                    // Linear: 200ms, 400ms, 600ms...
RetryPolicy.noRetry();                                            // Fail immediately
```

### Configuration (immutable -- methods return new instances)

```java
RetryPolicy policy = RetryPolicy.exponentialBackoff(5, Duration.ofMillis(100))
    .withMaxDelay(Duration.ofSeconds(30))   // Cap maximum wait
    .retryOn(IOException.class)             // Only retry specific exceptions
    .retryIf(ex -> ex instanceof IOException || ex instanceof TimeoutException)  // Custom predicate
    .onRetry(event -> log.warn("Retry #{}: {}", event.attemptNumber(), event.lastException().getMessage()));
```

Builder for full control:
```java
RetryPolicy policy = RetryPolicy.builder()
    .maxAttempts(5).initialDelay(Duration.ofMillis(100))
    .backoffMultiplier(2.0).maxDelay(Duration.ofSeconds(30))
    .useJitter(true).retryOn(IOException.class)
    .onRetry(event -> log.warn("Attempt {} failed", event.attemptNumber()))
    .build();
```

### Usage

```java
// Direct execution
String result = Retry.execute(policy, () -> httpClient.get(url));

// Wrap VTask (lazy -- nothing runs until run()/runSafe()/runAsync())
VTask<String> resilient = Retry.retryTask(VTask.of(() -> httpClient.get(url)), policy);

// With fallback on exhaustion
VTask<String> withFallback = Retry.retryTaskWithFallback(task, policy, lastError -> "default");

// With recovery task
VTask<String> withRecovery = Retry.retryTaskWithRecovery(task, policy, err -> VTask.of(() -> backup.get(url)));

// IOPath / VTaskPath integration
IOPath<Response> resilient = IOPath.delay(() -> httpClient.get(url))
    .withRetry(RetryPolicy.exponentialBackoff(3, Duration.ofSeconds(1)));
```

All retries exhausted throws `RetryExhaustedException` (cause = last failure).

## 2. Circuit Breaker

Stops calling a failing service to let it recover. Tracks consecutive failures and trips open when threshold reached.

### States

| State | Behavior | Transitions to |
|-------|----------|----------------|
| CLOSED | All calls allowed; failures counted | OPEN (failures reach threshold) |
| OPEN | All calls rejected with `CircuitOpenException` | HALF_OPEN (after openDuration expires) |
| HALF_OPEN | One probe call allowed | CLOSED (probe succeeds) or OPEN (probe fails) |

### Configuration

```java
CircuitBreakerConfig config = CircuitBreakerConfig.builder()
    .failureThreshold(5)                      // Consecutive failures before opening (default: 5)
    .successThreshold(3)                      // Probes needed in HALF_OPEN to close (default: 1)
    .openDuration(Duration.ofSeconds(30))     // How long circuit stays open (default: 60s)
    .callTimeout(Duration.ofSeconds(5))       // Timeout per protected call (default: 10s)
    .recordFailure(ex -> !(ex instanceof BusinessValidationException))  // Which exceptions count
    .build();
```

### Usage

```java
CircuitBreaker breaker = CircuitBreaker.create(config);  // or CircuitBreaker.withDefaults()

// Protect a VTask (generic -- one breaker can protect different return types)
VTask<String> protected = breaker.protect(VTask.of(() -> service.call()));

// With fallback
VTask<String> withFallback = breaker.protectWithFallback(
    VTask.of(() -> service.call()), ex -> "fallback-value");

// Metrics
CircuitBreakerMetrics m = breaker.metrics();
// m.totalCalls(), m.successfulCalls(), m.failedCalls(), m.rejectedCalls(), m.stateTransitions()

// Manual control
breaker.reset();       // Reset to CLOSED
breaker.tripOpen();    // Force OPEN (e.g., maintenance)
breaker.currentStatus();
```

## 3. Saga

Coordinates multi-step distributed operations with compensating transactions. On failure, compensations run in reverse order.

### Key Concepts

- Each forward step registers a compensation action (undo)
- On failure, compensations execute in reverse order of completion
- The failed step's compensation does NOT run (nothing to undo)
- All compensations are attempted even if some fail (best-effort)

### Creating a Saga

```java
// Direct chaining
Saga<String> saga = Saga.of(
        VTask.of(() -> paymentService.charge(order)),
        paymentId -> paymentService.refund(paymentId))
    .andThen(paymentId -> Saga.of(
        VTask.of(() -> inventoryService.reserve(order)),
        reservationId -> inventoryService.release(reservationId)))
    .andThen(reservationId -> Saga.of(
        VTask.of(() -> shippingService.schedule(order)),
        trackingId -> shippingService.cancel(trackingId)));

// SagaBuilder (named steps for error reporting)
Saga<String> saga = SagaBuilder.<Unit>start()
    .step("charge-payment",
        VTask.of(() -> paymentService.charge(order)),
        paymentId -> paymentService.refund(paymentId))
    .step("reserve-inventory",
        paymentId -> VTask.of(() -> inventoryService.reserve(order, paymentId)),
        reservationId -> inventoryService.release(reservationId))
    .stepNoCompensation("send-confirmation",  // Idempotent step, no undo needed
        reservationId -> VTask.of(() -> emailService.sendConfirmation(order, reservationId)))
    .build();
```

### Running

```java
// run() -- throws on failure
VTask<String> execution = saga.run();
Try<String> result = execution.runSafe();

// runSafe() -- returns Either<SagaError, String> with full details
VTask<Either<SagaError, String>> safe = saga.runSafe();
Either<SagaError, String> result = safe.run();
// SagaError: .failedStep(), .originalError(), .allCompensationsSucceeded(), .compensationFailures()
```

### Factory Methods

| Method | Description |
|--------|-------------|
| `Saga.of(action, consumer)` | Step with synchronous compensation |
| `Saga.of(action, function)` | Step with async compensation (VTask) |
| `Saga.noCompensation(action)` | Step without compensation |
| `.andThen(fn)` | Chain next step |
| `.map(fn)` / `.flatMap(fn)` | Transform result / chain sagas |

## 4. Bulkhead

Limits concurrent access to a resource to prevent cascading failures from one slow service exhausting all threads.

### Configuration

```java
Bulkhead simple = Bulkhead.withMaxConcurrent(10);

Bulkhead configured = Bulkhead.create(BulkheadConfig.builder()
    .maxConcurrent(5)                         // Max simultaneous executions (default: 10)
    .maxWait(10)                              // Max callers in wait queue (default: 0 = no limit)
    .waitTimeout(Duration.ofSeconds(2))       // Wait timeout for permit (default: 5s)
    .fairness(true)                           // FIFO ordering (default: false)
    .build());
```

### Usage

```java
VTask<Result> protected = bulkhead.protect(VTask.of(() -> database.query(sql)));
// Throws BulkheadFullException when full

// Inspect state
bulkhead.availablePermits();
bulkhead.activeCount();
```

Bulkhead vs VStreamPar: Bulkhead is per-service (shared across callers). VStreamPar is per-stream (within a pipeline). They compose naturally.

## 5. Combining Patterns

### Correct Ordering (outermost to innermost)

1. **Timeout** -- bounds total elapsed time including retries
2. **Bulkhead** -- limits concurrent access
3. **Retry** -- re-attempts on transient failure
4. **Circuit Breaker** -- each attempt checks circuit state (innermost)

Circuit breaker MUST be inside retry so each attempt is recorded individually.

### ResilienceBuilder (applies correct order regardless of call order)

```java
VTask<Response> resilient = Resilience.<Response>builder(
        VTask.of(() -> httpClient.get(url)))
    .withTimeout(Duration.ofSeconds(30))
    .withBulkhead(serviceBulkhead)
    .withRetry(retryPolicy)
    .withCircuitBreaker(serviceBreaker)
    .withFallback(ex -> Response.fallback())
    .build();
```

### Convenience Methods

```java
// Circuit breaker + retry
VTask<String> p = Resilience.withCircuitBreakerAndRetry(task, breaker, retryPolicy);

// All three core patterns (bulkhead -> retry -> circuit breaker)
VTask<String> p = Resilience.protect(task, breaker, retryPolicy, bulkhead);
```

### Stream Integration

```java
// Per-element resilience in parallel streams
List<UserProfile> profiles = Path.vstreamFromList(userIds)
    .parEvalMap(4, id ->
        serviceBreaker.protect(
            Retry.retryTask(VTask.of(() -> profileService.fetch(id)), retryPolicy)))
    .recover(ex -> UserProfile.unknown())
    .toList()
    .run();

// Using per-element helpers
Function<String, VTask<UserProfile>> resilientFetch =
    Resilience.withCircuitBreakerPerElement(
        Resilience.withRetryPerElement(id -> VTask.of(() -> profileService.fetch(id)), retryPolicy),
        serviceBreaker);
```

### Pattern Selection

| Failure Mode | Pattern |
|-------------|---------|
| Transient errors (network blips) | Retry |
| Service outage | Retry + Circuit Breaker |
| Limited capacity | Retry + Bulkhead |
| All failure modes | Retry + Circuit Breaker + Bulkhead + Timeout |
| Multi-step distributed ops | Saga |
