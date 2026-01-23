# Phase 4: Context & Scoped Values — Implementation Plan

> **Target Version**: v2.0.0-M4
> **Prerequisites**: Phase 3 complete (Structured Concurrency)
> **Estimated Duration**: To be determined by team velocity
> **Note**: Spring Integration deferred to Phase 5

## Overview

Phase 4 integrates Java 25's `ScopedValue` (JEP 506, finalized) with higher-kinded-j, providing `Context<R, A>` for functional context propagation that automatically inherits across structured concurrency boundaries.

---

## 1. Prerequisites Check

### 1.1 Java 25 Scoped Values Status

- [ ] Verify JEP 506 finalized status in Java 25
- [ ] Confirm `ScopedValue` API stability
- [ ] Document carrier semantics for virtual threads

### 1.2 Key Feature: Automatic Inheritance

Scoped values are automatically inherited by child virtual threads created within structured concurrency scopes. This is the killer feature that solves context propagation problems in reactive systems.

```java
// Context automatically visible in all forked tasks
ScopedValue.runWhere(CURRENT_USER, user, () -> {
    try (var scope = StructuredTaskScope.open()) {
        scope.fork(() -> service1.process()); // Sees CURRENT_USER
        scope.fork(() -> service2.process()); // Also sees CURRENT_USER
        scope.join();
    }
});
```

---

## 2. Context Implementation

### 2.1 ContextKind Interface

**Location**: `hkj-api/src/main/java/org/higherkindedj/hkt/context/ContextKind.java`

- [ ] Create `ContextKind<R, A>` interface
- [ ] Use `Kind2` for two type parameters (environment R, value A)
- [ ] Define `Witness` class

```java
@NullMarked
public interface ContextKind<R, A> extends Kind<ContextKind.Witness<R>, A> {

    final class Witness<TYPE_R> implements WitnessArity<TypeArity.Unary> {
        private Witness() {}
    }
}
```

### 2.2 Context Sealed Interface

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/context/Context.java`

- [ ] Create sealed `Context<R, A>` interface
- [ ] Integrate with `ScopedValue<R>`
- [ ] Support functional composition

```java
public sealed interface Context<R, A> extends ContextKind<R, A>, Chainable<A>
    permits ContextImpl {

    // ScopedValue declaration
    static <R> ScopedValue<R> declare();

    // Factory methods - access context
    static <R, A> Context<R, A> ask(ScopedValue<R> key);
    static <R, A> Context<R, A> asks(ScopedValue<R> key, Function<R, A> f);

    // Factory methods - pure values (ignore context)
    static <R, A> Context<R, A> pure(A value);
    static <R, A> Context<R, A> of(A value);

    // Factory methods - failure
    static <R, A> Context<R, A> fail(Throwable error);

    // Run with context
    A runWith(R context, ScopedValue<R> key);
    A runWith(R context, ScopedValue<R> key, Duration timeout);
    Try<A> runWithSafe(R context, ScopedValue<R> key);

    // Lift to VTask with context binding
    VTask<A> provide(R context, ScopedValue<R> key);
    VTaskPath<A> providePath(R context, ScopedValue<R> key);

    // Composition
    <B> Context<R, B> map(Function<A, B> f);
    <B> Context<R, B> flatMap(Function<A, Context<R, B>> f);
    <B> Context<R, B> via(Function<A, Context<R, B>> f);

    // Local context modification
    Context<R, A> local(UnaryOperator<R> f);

    // Context transformation
    <R2> Context<R2, A> contramap(Function<R2, R> f);
    <R2> Context<R2, A> provide(R context);
}
```

### 2.3 Context Implementation

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/context/ContextImpl.java`

- [ ] Package-private implementation
- [ ] Store computation as `Function<ScopedValue<R>, A>`
- [ ] Lazy evaluation until `runWith` called

### 2.4 ContextMonad

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/context/ContextMonad.java`

- [ ] Implement `MonadError<ContextKind.Witness<R>, Throwable>`
- [ ] Parameterized by environment type R
- [ ] Factory method pattern (not singleton)

```java
public final class ContextMonad<R> implements
    MonadError<ContextKind.Witness<R>, Throwable> {

    private final ScopedValue<R> scopedValue;

    public static <R> ContextMonad<R> instance(ScopedValue<R> scopedValue) {
        return new ContextMonad<>(scopedValue);
    }

    // Type class methods...
}
```

### 2.5 Design Decision Required

> **Consult**:
> 1. Should Context be Reader-like (pure function from R to A) or effectful (function from R to VTask<A>)?
> 2. How should we handle missing scoped values (exception vs Option)?
> 3. Should `ask` require the ScopedValue parameter or use a registry?

---

## 3. Common Context Patterns

### 3.1 Request Context

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/context/RequestContext.java`

- [ ] Pre-defined scoped values for common patterns
- [ ] Type-safe context accessors

```java
public final class RequestContext {

    // Common request-scoped values
    public static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> CORRELATION_ID = ScopedValue.newInstance();
    public static final ScopedValue<Locale> LOCALE = ScopedValue.newInstance();

    // Access helpers
    public static Context<String, String> traceId() {
        return Context.ask(TRACE_ID);
    }

    public static Context<String, String> correlationId() {
        return Context.ask(CORRELATION_ID);
    }

    public static Context<Locale, Locale> locale() {
        return Context.ask(LOCALE);
    }

    // Execution with full request context
    public static <A> A runWithRequestContext(
        String traceId,
        String correlationId,
        Locale locale,
        Callable<A> computation) throws Exception {

        return ScopedValue
            .where(TRACE_ID, traceId)
            .where(CORRELATION_ID, correlationId)
            .where(LOCALE, locale)
            .call(computation);
    }
}
```

### 3.2 Security Context

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/context/SecurityContext.java`

- [ ] Principal and roles
- [ ] Permission checking

```java
public final class SecurityContext {

    public static final ScopedValue<Principal> PRINCIPAL = ScopedValue.newInstance();
    public static final ScopedValue<Set<String>> ROLES = ScopedValue.newInstance();

    public static Context<Principal, Principal> currentPrincipal() {
        return Context.ask(PRINCIPAL);
    }

    public static Context<Set<String>, Boolean> hasRole(String role) {
        return Context.asks(ROLES, roles -> roles.contains(role));
    }

    public static Context<Set<String>, Boolean> hasAnyRole(String... roles) {
        return Context.asks(ROLES, r ->
            Arrays.stream(roles).anyMatch(r::contains));
    }
}
```

---

## 4. Context Combinators

### 4.1 ContextOps Utility Class

**Location**: `hkj-core/src/main/java/org/higherkindedj/hkt/context/ContextOps.java`

- [ ] Sequence and traverse for Context
- [ ] Parallel execution with shared context

```java
public final class ContextOps {

    // Sequence contexts
    public static <R, A> Context<R, List<A>> sequence(
        List<Context<R, A>> contexts);

    // Traverse with context
    public static <R, A, B> Context<R, List<B>> traverse(
        List<A> items,
        Function<A, Context<R, B>> f);

    // Parallel execution (context automatically inherited)
    public static <R, A, B> Context<R, Pair<A, B>> parZip(
        Context<R, A> ca,
        Context<R, B> cb);

    // Run multiple contexts with same environment
    public static <R, A, B, C> Context<R, C> map2(
        Context<R, A> ca,
        Context<R, B> cb,
        BiFunction<A, B, C> f);

    // Combine with VTask
    public static <R, A> VTask<A> bindAndRun(
        Context<R, A> context,
        R environment,
        ScopedValue<R> key);
}
```

---

## 5. Integration with Scope and VTask

### 5.1 Scope Context Integration

- [ ] Scoped values automatically propagate to forked tasks
- [ ] Provide helper methods for context-aware scopes

```java
// In Scope class
public static <R, A> ScopeBuilder<A> openWithContext(
    R environment,
    ScopedValue<R> key);

// Usage
Scope.<User, Result>openWithContext(currentUser, CURRENT_USER)
    .fork(serviceA.process())  // Sees CURRENT_USER
    .fork(serviceB.process())  // Also sees CURRENT_USER
    .join();
```

### 5.2 VTask Context Integration

- [ ] Convenience methods for running VTask with context

```java
// In VTask class
public static <R, A> VTask<A> withContext(
    R environment,
    ScopedValue<R> key,
    VTask<A> task);

// Usage
VTask<Result> contextualTask = VTask.withContext(
    currentUser,
    CURRENT_USER,
    VTask.of(() -> processWithUser())
);
```

---

## 6. Testing Requirements

### 6.1 Context Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/context/ContextTest.java`

- [ ] `@Nested FactoryMethodsTests`
  - [ ] `Context.ask()` accesses scoped value
  - [ ] `Context.asks()` transforms scoped value
  - [ ] `Context.pure()` ignores context
  - [ ] `Context.fail()` represents failure
- [ ] `@Nested RunWithTests`
  - [ ] `runWith()` binds value and executes
  - [ ] `runWithSafe()` returns Try
  - [ ] Missing scoped value behaviour
- [ ] `@Nested CompositionTests`
  - [ ] `map()` transforms result
  - [ ] `flatMap()` chains context access
  - [ ] `via()` alias works correctly
- [ ] `@Nested LocalTests`
  - [ ] `local()` modifies context for subtree
  - [ ] Original context unchanged
- [ ] `@Nested ProvideTests`
  - [ ] `provide()` returns VTask with bound context
  - [ ] Context visible in VTask execution
- [ ] `@Nested InheritanceTests`
  - [ ] Context inherited by virtual threads
  - [ ] Context inherited in Scope.fork()

### 6.2 Context Property Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/context/ContextPropertyTest.java`

- [ ] Functor laws
- [ ] Monad laws
- [ ] Reader monad laws (ask/local coherence)
- [ ] Arbitrary providers for Context

### 6.3 Context Laws Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/context/ContextLawsTest.java`

- [ ] `@TestFactory` for Functor laws
- [ ] `@TestFactory` for Monad laws
- [ ] `@TestFactory` for Reader-specific laws

### 6.4 ContextOps Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/context/ContextOpsTest.java`

- [ ] `sequence` tests
- [ ] `traverse` tests
- [ ] `parZip` tests
- [ ] `map2` tests

### 6.5 Integration Tests

**Location**: `hkj-core/src/test/java/org/higherkindedj/hkt/context/ContextIntegrationTest.java`

- [ ] Context with VTask integration
- [ ] Context with Scope integration
- [ ] Context with Resource integration
- [ ] Multi-level context nesting
- [ ] Context in parallel execution

### 6.6 Common Context Tests

- [ ] RequestContext tests
- [ ] SecurityContext tests
- [ ] Permission checking tests

### 6.7 Coverage Requirements

- [ ] 100% coverage for Context
- [ ] 100% coverage for ContextMonad
- [ ] 100% coverage for ContextOps
- [ ] 100% coverage for RequestContext
- [ ] 100% coverage for SecurityContext

---

## 7. ArchUnit Tests

### 7.1 ContextArchitectureRules.java

**Location**: `hkj-core/src/test/java/org/higherkindedj/architecture/`

- [ ] ContextKind.Witness must be final with private constructor
- [ ] Context must be sealed interface
- [ ] ContextMonad must follow parameterized pattern
- [ ] RequestContext and SecurityContext must be utility classes

### 7.2 ScopedValue Usage Rules

- [ ] ScopedValue fields must be `static final`
- [ ] ScopedValue must be accessed through Context abstraction in effect code
- [ ] Direct ScopedValue.get() discouraged in business logic

---

## 8. Custom AssertJ Assertions

### 8.1 ContextAssert.java

**Location**: `hkj-core/src/test/java/org/higherkindedj/test/assertions/`

- [ ] `ContextAssert<R, A>` for Context assertions
- [ ] `runsWith(R, ScopedValue<R>)` — fluent execution
- [ ] `yieldsValue(A)` — verify result
- [ ] `yieldsError(Class)` — verify failure
- [ ] `accessesKey(ScopedValue<R>)` — verify context access

---

## 9. Documentation Plan

> **Note**: Review this plan before writing documentation.

### 9.1 hkj-book Documentation

#### New Pages

- [ ] `context/context.md` — Context: Functional Context Propagation
  - What You'll Learn
  - ScopedValue integration
  - ask/asks patterns
  - Composition with map/flatMap
  - local for context modification
  - Integration with VTask and Scope
  - Key Takeaways

- [ ] `context/scoped_values.md` — Understanding Scoped Values
  - What are scoped values
  - Comparison with ThreadLocal
  - Automatic inheritance in virtual threads
  - Best practices

- [ ] `context/common_contexts.md` — Common Context Patterns
  - RequestContext for request-scoped data
  - SecurityContext for principals and roles
  - Custom context patterns

- [ ] `context/ch_intro.md` — Chapter: Context Management
  - Chapter introduction
  - In This Chapter
  - Chapter Contents

#### Updates

- [ ] Update `concurrency/scope.md` with context integration
- [ ] Update `effects/vtask.md` with context helpers
- [ ] Add Context chapter to SUMMARY.md

### 9.2 Javadoc Requirements

- [ ] Context fully documented
- [ ] ContextMonad fully documented
- [ ] ContextOps fully documented
- [ ] RequestContext fully documented
- [ ] SecurityContext fully documented

---

## 10. Tutorial Plan

> **Note**: Review this plan before writing tutorials.

**Location**: `hkj-examples/src/test/java/org/higherkindedj/tutorial/context/`

### 10.1 New Tutorial Track: Context

#### Tutorial01_ContextBasics.java (~10 minutes)

- [ ] Exercise 1: Declare a ScopedValue
- [ ] Exercise 2: Access context with ask
- [ ] Exercise 3: Transform context with asks
- [ ] Exercise 4: Run context with environment
- [ ] Exercise 5: Chain contexts with flatMap
- [ ] Exercise 6: Modify context locally

#### Tutorial02_ContextWithVTask.java (~10 minutes)

- [ ] Exercise 1: Provide context to VTask
- [ ] Exercise 2: Context in Par combinators
- [ ] Exercise 3: Context inheritance in Scope
- [ ] Exercise 4: Combining Context with other effects
- [ ] Exercise 5: Error handling in context

#### Tutorial03_CommonContextPatterns.java (~12 minutes)

- [ ] Exercise 1: RequestContext for tracing
- [ ] Exercise 2: SecurityContext for authorization
- [ ] Exercise 3: Custom context for configuration
- [ ] Exercise 4: Multi-context scenarios
- [ ] Exercise 5: Testing with mock contexts

### 10.2 Solution Files

- [ ] Tutorial01_ContextBasics_Solution.java
- [ ] Tutorial02_ContextWithVTask_Solution.java
- [ ] Tutorial03_CommonContextPatterns_Solution.java

---

## 11. Performance Benchmarks

**Location**: `hkj-benchmarks/src/jmh/java/org/higherkindedj/benchmarks/`

### 11.1 ContextBenchmark.java

- [ ] Context creation overhead
- [ ] `ask` access latency
- [ ] `asks` transformation overhead
- [ ] `runWith` binding overhead
- [ ] Context vs ThreadLocal comparison
- [ ] Context vs Reader monad comparison

### 11.2 ScopedValueBenchmark.java

- [ ] Raw ScopedValue access
- [ ] ScopedValue.where binding
- [ ] Nested scoped value bindings
- [ ] Virtual thread inheritance overhead

### 11.3 ContextInheritanceBenchmark.java

- [ ] Context propagation in Scope.fork
- [ ] Context propagation in Par combinators
- [ ] Deep nesting context access
- [ ] Many concurrent accesses

### 11.4 Comparison Benchmarks

- [ ] HKJ Context vs raw ScopedValue
- [ ] HKJ Context vs ThreadLocal
- [ ] HKJ Context vs ReaderT
- [ ] HKJ Context vs Spring request scope

---

## 12. Completion Criteria

### 12.1 Code Complete

- [ ] Context implementation complete
- [ ] ContextMonad implementation complete
- [ ] ContextOps implementation complete
- [ ] Common context patterns complete
- [ ] Integration with VTask, Scope, Resource complete
- [ ] All tests passing at 100% coverage
- [ ] ArchUnit tests passing

### 12.2 Documentation Complete

- [ ] Documentation plan reviewed and approved
- [ ] All documentation written
- [ ] Tutorials complete

### 12.3 Performance Validated

- [ ] Benchmarks implemented
- [ ] Minimal overhead vs raw ScopedValue
- [ ] Better ergonomics than ThreadLocal

### 12.4 Integration Verified

- [ ] Works with VTask from Phase 1
- [ ] Works with VTaskPath from Phase 2
- [ ] Works with Scope and Resource from Phase 3
- [ ] Ready for Phase 5 (Spring Integration)

---

## 13. Design Decisions Summary

1. **Context Semantics**: Pure Reader vs effectful?
2. **Missing Value Handling**: Exception vs Option vs provide default?
3. **ScopedValue Parameter**: Explicit in API vs registry lookup?
4. **Multiple Contexts**: Support multiple simultaneous scoped values elegantly?
5. **Context Nesting**: How to handle nested runWith calls?
6. **Security Integration**: Build-in permission checking or leave to user?

---

## Appendix: File Checklist

### New Files to Create

```
hkj-api/src/main/java/org/higherkindedj/hkt/context/
├── ContextKind.java
└── package-info.java

hkj-core/src/main/java/org/higherkindedj/hkt/context/
├── Context.java
├── ContextImpl.java
├── ContextMonad.java
├── ContextOps.java
├── RequestContext.java
├── SecurityContext.java
└── package-info.java

hkj-core/src/test/java/org/higherkindedj/hkt/context/
├── ContextTest.java
├── ContextPropertyTest.java
├── ContextLawsTest.java
├── ContextOpsTest.java
├── ContextIntegrationTest.java
├── RequestContextTest.java
└── SecurityContextTest.java

hkj-core/src/test/java/org/higherkindedj/architecture/
└── ContextArchitectureRules.java

hkj-core/src/test/java/org/higherkindedj/test/assertions/
└── ContextAssert.java

hkj-benchmarks/src/jmh/java/org/higherkindedj/benchmarks/
├── ContextBenchmark.java
├── ScopedValueBenchmark.java
└── ContextInheritanceBenchmark.java

hkj-examples/src/test/java/org/higherkindedj/tutorial/context/
├── README.md
├── Tutorial01_ContextBasics.java
├── Tutorial02_ContextWithVTask.java
├── Tutorial03_CommonContextPatterns.java
└── solutions/
    ├── Tutorial01_ContextBasics_Solution.java
    ├── Tutorial02_ContextWithVTask_Solution.java
    └── Tutorial03_CommonContextPatterns_Solution.java

hkj-book/src/context/
├── ch_intro.md
├── context.md
├── scoped_values.md
└── common_contexts.md
```

### Files to Update

```
hkj-core/src/main/java/org/higherkindedj/hkt/scope/Scope.java
hkj-core/src/main/java/org/higherkindedj/hkt/vtask/VTask.java
hkj-book/src/concurrency/scope.md
hkj-book/src/effects/vtask.md
hkj-book/src/SUMMARY.md
```

---

## 14. Future: Phase 5 Preview

Phase 5 (Spring Integration) will build on Phase 4 by:

- Creating `VTaskReturnValueHandler` for Spring MVC
- Implementing `ScopedValueFilter` for request context
- Adding Spring Security integration with SecurityContext
- Providing Spring Boot auto-configuration
- Adding Actuator metrics for virtual threads

This phase focuses on the core Context abstraction; Spring-specific integration is deferred.
