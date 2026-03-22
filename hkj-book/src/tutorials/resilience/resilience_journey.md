# Resilience Patterns Tutorials

These tutorials guide you through building fault-tolerant applications with higher-kinded-j's resilience patterns.

## Prerequisites

Before starting, you should be comfortable with:
- `VTask` basics (creating, running, composing)
- The Effect Path API (`VTaskPath`, `VStreamPath`)
- Basic error handling (`handleError`, `recover`)

## Tutorial Track

### Tutorial 1: Circuit Breaker (~10 minutes)

Learn to protect services from cascading failures with the circuit breaker pattern.

**Exercises:**
1. Create a circuit breaker with custom configuration
2. Protect a VTask with the circuit breaker
3. Observe the circuit opening after threshold failures
4. Handle `CircuitOpenException`
5. Use `protectWithFallback` for graceful degradation
6. Monitor circuit breaker metrics

**File:** `Tutorial01_CircuitBreaker.java`

### Tutorial 2: Saga (~10 minutes)

Learn to coordinate multi-step operations with automatic compensation on failure.

**Exercises:**
1. Create a simple saga with compensation
2. Chain saga steps with dependent data
3. Verify compensation on failure
4. Use `SagaBuilder` for complex workflows
5. Handle saga errors with `runSafe()`

**File:** `Tutorial02_Saga.java`

### Tutorial 3: Retry, Bulkhead & Combined Resilience (~10 minutes)

Learn VTask-native retry, concurrency limiting, and combining multiple patterns.

**Exercises:**
1. Use `Retry.retryTask` with a `RetryPolicy`
2. Monitor retries with `RetryEvent`
3. Create a `Bulkhead` to limit concurrency
4. Compose patterns with `ResilienceBuilder`
5. Use convenience methods from `Resilience`

**File:** `Tutorial03_RetryBulkheadResilience.java`

### Tutorial 4: Path API Resilience (~10 minutes)

Learn to use resilience patterns through the fluent Path API.

**Exercises:**
1. `VTaskPath.withRetry()` and `.retry()` convenience
2. `VTaskPath.catching()`, `.asMaybe()`, `.asTry()` typed error wrapping
3. `VTaskPath.withCircuitBreaker()` in a fluent chain
4. `VStreamPath.recover()` and `.onError()` stream error handling
5. `VStreamPath.mapTask()` with per-element retry
6. `VTaskContext` Layer 2 resilience: `.retry()`, `.withCircuitBreaker()`

**File:** `Tutorial04_PathResilience.java`

## Running Tutorials

```bash
# Run tutorial exercises (expected to fail until completed)
./gradlew :hkj-examples:tutorialTest

# Run solutions to verify they work
./gradlew :hkj-examples:test
```

## Solutions

Each tutorial has a corresponding solution file in the `solutions/` subdirectory. Try to complete the exercises on your own before checking the solutions.
